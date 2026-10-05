# BlogHub · 编辑器内 AI 写作助手（一期）

## Context

BlogHub 是一个 Vue3 + Spring Boot(1127) + MySQL + Redis 的博客 CMS。它的核心价值是「让作者知道自己该写什么」——但目前作者坐下写东西时是完全裸写的：`EditorView.vue` 只有标题框、正文 textarea 和右侧属性栏，没有任何写作辅助。

本次要加一个 **Python 编写的 AI Agent**，形态是**编辑器内的写作 Copilot**：选中一段能润色/改写/翻译，光标处能续写，还能起标题、生成大纲。技术上新增一个 Python FastAPI 服务，由现有 Spring Boot 后端做流式转发，前端只调 Spring Boot。

**为什么不选其他形态**：公开站拿不到分类标签、列表不返回正文，所以站内 RAG/问答没有语料；后端没有评论/点赞/浏览量表，读者互动类 agent 没有数据基础。只有编辑器内 Copilot 的数据全在前端手里，不需要伪造任何东西 —— 这符合项目「不做假的」这条铁律。

**一期边界（已与用户确认）**：
- 模型走 **DeepSeek**（OpenAI 兼容接口，`https://api.deepseek.com/v1`）
- **只做纯生成版，不做工具调用循环**。7 个动作全部靠提示词实现；`search_my_articles` / `get_my_article` 工具、多轮对话、用量统计放到二期。
- 不做富文本/Markdown 渲染（后端存纯文本，所见即所存）。

## 架构

```
浏览器 :5173
  └─ fetch POST /api/ai/chat   (SSE, Authorization: Bearer <bh_token>)
       └─ vite proxy 去掉 /api
            └─ Spring Boot :1127  POST /ai/chat
                 ├─ JwtInterceptor 鉴权（已在 /** 上，无需改白名单）
                 ├─ 同步取 BaseContext 身份 → 清 ThreadLocal
                 ├─ 同步 send() 预检 Python 是否可用
                 └─ StreamingResponseBody 逐块透传
                      └─ Python FastAPI :8000  POST /ai/chat
                           └─ DeepSeek（OpenAI 兼容 /chat/completions, stream=true）
```

鉴权不落到 Python：JWT 由现有 `JwtInterceptor` 校验完，Spring 只转发身份与共享 token；Python 不解析 JWT，也不需要 Redis 黑名单。

## 一期范围

| 层 | 内容 |
|---|---|
| Python | `/ai/health`、`/ai/chat`（SSE 流式）、7 个动作提示词、输出清洗 |
| Spring | `AiProperties` 配置、`AiGateway` 转发、`AiController`、虚拟线程、异步超时 |
| 前端 | `aiStream.js` 流式封装、编辑器内联 AI 条、插入/替换选中/用作标题、停止、错误 banner |

7 个动作：`CONTINUE` 续写 · `POLISH` 润色 · `REWRITE` 改写 · `TITLE` 起标题 · `OUTLINE` 生成大纲 · `TRANSLATE` 翻译 · `CUSTOM` 自定义指令。

---

## 实施步骤

### 1. Python FastAPI 服务（新建 `e:\BlogHub\ai\`）

与 `server/`、`front_end/` 平级，独立部署单元，不污染 Maven 树。

```
ai/
  app/__init__.py
  app/config.py      # pydantic-settings，lifespan 启动即校验 key，缺失直接起不来
  app/schemas.py     # ChatRequest / Context / Action 枚举
  app/prompts.py     # ACTIONS 模板 + GUARD 守卫 + sanitize()
  app/llm.py         # AsyncOpenAI 客户端 + 流式调用
  app/sse.py         # sse_event(name, payload) / 心跳
  app/main.py        # FastAPI 实例 + /ai/chat + /ai/health
  requirements.txt
  .env.example
```

`requirements.txt`：`fastapi`、`uvicorn[standard]`、`openai>=1.54`、`httpx`、`pydantic-settings`

**请求体**（`schemas.py`）：
```python
class Context(BaseModel):
    title: str = ""
    content: str = ""
    selection: str = ""
    sel_start: int = 0
    sel_end: int = 0
    category_name: str = ""
    tag_names: list[str] = []

class ChatRequest(BaseModel):
    action: Action            # CONTINUE|POLISH|REWRITE|TITLE|OUTLINE|TRANSLATE|CUSTOM
    instruction: str = ""     # 仅 CUSTOM 用
    context: Context
```

**SSE 事件协议**（`data:` 必须是单行 JSON；注释行 `:` 前端忽略）：
```
: ready\n\n                                   ← 必须在调 LLM 之前 flush，见「关键细节 2」
event: delta\ndata:{"v":"片段"}\n\n
event: done\ndata:{"text":"<清洗后全文>","finish":"stop","truncated":false}\n\n
event: error\ndata:{"code":"UPSTREAM_FAILED","msg":"…","retryable":true}\n\n
: hb\n\n                                      ← 每 10s 一条，用于探测客户端断连
```
`done.text` 是**权威文本**，前端流式期间只预览，点回填时用 `done.text`。

**`GET /ai/health`** → `{"configured":true,"model":"deepseek-chat"}`；未配 key 返回 503。

**`prompts.py`**：`ACTIONS` 是 `{Action: (system, user_template)}` 的 dict，7 个动作共享同一段守卫后缀 —— 「只输出正文本身；不要解释、不要前言、不要 Markdown 代码围栏、不要 `#` 标题符号、不要自称 AI」。
`sanitize(text)` 做三件事：剥掉首尾 ``` 围栏、剥掉 `/^(好的|当然|以下是)[^\n]{0,20}[：:]\s*/`、把 3 个以上连续空行压成 2 个。`TITLE` 动作额外只取首行、去掉引号与 `#`。

**`.env.example`**：
```
LLM_BASE_URL=https://api.deepseek.com/v1
LLM_API_KEY=sk-xxx
LLM_MODEL=deepseek-chat
LLM_TEMPERATURE=0.7
LLM_MAX_TOKENS=2048
LLM_TIMEOUT_READ=180
SERVER_PORT=8000
SHARED_TOKEN=dev-ai-token
```

### 2. Spring Boot 转发层

**新增 `common/properties/AiProperties.java`** —— `@Data @Component @ConfigurationProperties(prefix = "bloghub.ai")`，字段 `baseUrl` / `sharedToken` / `connectTimeoutMs` / `firstByteTimeoutMs` / `maxDurationMs`。
（`common/properties/JwtProperties.java` 是空类，没有现成范式可抄，这里新建即可。）

**改动 `src/main/resources/application.yml`**：
```yaml
bloghub:
  ai:
    base-url: http://127.0.0.1:8000
    shared-token: dev-ai-token
    connect-timeout-ms: 3000
    max-duration-ms: 600000

spring:
  threads:
    virtual:
      enabled: true          # 每个 SSE 流不占平台线程
  mvc:
    async:
      request-timeout: 600000  # 不设会被 Tomcat 默认 ~30s 静默掐断长生成
```

**新增 `config/AiHttpClientConfig.java`** —— 单例 `java.net.http.HttpClient`，`version(HTTP_1_1)`、`connectTimeout(3s)`、`executor(Executors.newVirtualThreadPerTaskExecutor())`。用 JDK 内置客户端，**不引 WebClient/OkHttp**（webflux 会往 servlet 栈里拖 Reactor，收益不抵成本）。

**新增 `service/AiGatewayService.java` + `impl/AiGatewayServiceImpl.java`**：
- `HttpResponse<InputStream> open(AiChatCommand cmd)` —— 用**同步** `send()` + `BodyHandlers.ofInputStream()`。`ofInputStream` 在**响应头**到达时即完成，body 惰性读，于是「Python 没启动 / 返回非 200」变成同步可判定，能干净地抛 `BaseException` → `Result{code:0,msg}`。
- `void pump(HttpResponse<InputStream> up, HttpServletResponse response, OutputStream out)` —— `try (InputStream in = up.body())` 中 4KB 循环读写并**逐块 flush**（不能攒），`IOException` 只 log.debug（客户端断开走这里），try-with-resources 的 `close(in)` 即取消上游 exchange，不需要额外 cancel。

**新增 `controller/AiController.java`**：
```java
@RestController
@RequestMapping("/ai")
public class AiController {

    @PostMapping("/chat")   // 注意：不声明 produces，见「关键细节 3」
    public StreamingResponseBody chat(@RequestBody AiChatDTO dto, HttpServletResponse response) {
        // 异步线程读不到 ThreadLocal，身份必须在这里同步取完
        AiChatCommand cmd = AiChatCommand.of(dto, BaseContext.getCurrentUserName());
        BaseContext.clearAll();                 // 立即清，防线程复用串号
        HttpResponse<InputStream> up = gateway.open(cmd);   // 预检失败仍能返回 Result{code:0}
        return out -> gateway.pump(up, response, out);
    }
}
```
路径 **`/ai/chat`**（前端 `/api` 前缀被 vite rewrite 掉）。`/ai/**` 已被 `addPathPatterns("/**")` 覆盖且不在白名单里，**`WebConfig` 不用改**。

**新增 `pojo/dto/AiChatDTO.java`** —— 直接镜像 Python 的请求体（action / instruction / context 内嵌类）。

**改动 `interceptor/JwtInterceptor.java`** —— 改为 `implements AsyncHandlerInterceptor` 并覆写 `afterConcurrentHandlingStarted` 执行 `BaseContext.clearAll()`。异步请求下 `afterCompletion` 要等异步处理结束才回调，Tomcat 线程在 async 交接时不会被清，线程复用时会被下一个请求读到上一个用户的身份。

### 3. 前端接入

**新增 `src/api/aiStream.js`** —— 绕过 `request.js` 的 `{code,msg,data}` 解包（SSE 不是那个形状）。
```js
export async function streamAi(path, body, { onEvent, signal })
export class AiStreamError extends Error {}   // {code, kind}
```
用 `fetch('/api' + path, { method:'POST', headers:{ 'Content-Type':'application/json', Authorization:`Bearer ${getToken()}`, Accept:'text/event-stream' }, body, signal })`。
不用 `EventSource`（它不能 POST、不能带 Authorization）。
解析要点：`TextDecoder('utf-8', { stream:true })` 处理跨 chunk 截断的多字节字符；按 `\n\n` 切事件块，**一次 read 可能含多个事件**必须循环分发；逐行识别 `event:` / `data:`，**跳过 `:` 开头的注释行**。非 200 时 `res.json()` 解 envelope，按 `code===0 && /登录|token/.test(msg)` 抛 `kind:'auth'`。

**改动 `src/api/index.js`** —— 追加 `export const aiApi = { health: () => http.get('/ai/health') }`。

**改动 `src/views/console/EditorView.vue`** —— 新增内联 AI 条，**放在左栏正文 textarea 下方、`.panel__body--flush` 之内**，触发器放在 `panel__head` 的 `seg` 旁边。

```html
<section v-if="ai.open" class="ed__ai"> … </section>
```
- 内容：动作 chip 行（7 个，复用 `.chip` / `.chip.is-on`）、CUSTOM 时的指令输入框（复用 `.input`）、结果区（只读 `<div class="prose">`）、底部动作（插入 / 替换选中 / 用作标题 / 停止 / 重试）。
- 样式用 `scoped`，只加 `.ed__ai` 与结果区两三条规则，颜色/圆角/阴影全部取 `style.css` 的 `:root` token。

**关键约束（决定了位置选择）**：`mode==='write'` 是 `v-if` 切换，把 AI 做成第三个 tab 会**卸载 textarea 从而丢失 `selectionStart/End`**，让「改写选中」失效。内联条永不卸载 textarea，选中区间天然存活。

**选中处理**：`contentEl = ref(null)`、`sel = reactive({start:0,end:0,text:''})`，在 textarea 上监听 `select` / `keyup` / `mouseup`，加一个 `@blur="snapSel"` 兜底（点按钮会 blur，但 DOM 索引通常保留）。

**三种回填**（都只改 `form`，不碰 `saved`）：
```js
// 插入 / 替换选中
form.content = c.slice(0, sel.start) + text + c.slice(sel.end)
// 用作标题
form.title = text.split('\n')[0].trim(); nextTick(autosizeTitle)
```
替换后 `await nextTick()` → `contentEl.value.setSelectionRange(newEnd, newEnd)` → `focus()` 恢复光标。
`dirty` 是 `computed(snapshot() !== saved())` 派生值，回填后自动变脏，**不手动置位**；`Ctrl+S` 读的就是 `form` 现值，无需改 `onKey`。

**流式渲染**：累积到独立 `ai.text` ref，**绝不能写进 `form.content`**（`v-model` 每个 token 都会重置光标并抖动 dirty）。渲染成一个只读单文本节点（`white-space: pre-wrap`），不是 token 数组。用 `requestAnimationFrame` 合并一帧内的多个 delta。`AbortController` 挂到「停止」。

**错误与空态**：统一用 `.banner.banner--error` + 一个 `.btn--ghost.btn--sm` 重试。三种情形分别是「AI 服务未启动」（Spring 预检抛 code:0）、「AI 服务未配置模型密钥」（health 503）、「生成中途失败」（`event: error`，**保留已生成的部分**并提示可插入）。未运行时只显示动作 chip 行 + 一行 `.t-xs.c-3`，不做假占位。

### 4. 启动方式

- Python：`cd ai && uvicorn app.main:app --port 8000`（`.env` 从 `.env.example` 复制后填 key）
- Spring：照旧 1127
- 前端：照旧 `npm run dev`（5173）

---

## 关键细节（最容易踩的三个坑）

1. **异步 + ThreadLocal 的安全问题**：`JwtInterceptor.afterCompletion` 在异步请求中要等异步处理完成才回调，Tomcat 线程在 async 交接时不会被清理，线程复用会把上一个用户的 `BaseContext` 带给下一个请求。规避 = Controller 同步取完身份后**立即 `BaseContext.clearAll()`** + `JwtInterceptor` 改实现 `AsyncHandlerInterceptor`。同时这也解释了为什么身份必须**在返回流对象之前**取 —— 异步线程读不到 ThreadLocal。

2. **Python 必须先 flush 一条 `: ready`**：Spring 用同步 `send()` 做预检，若 Python 收到请求就闷头调 LLM（首 token 可能几十秒），`send()` 会阻塞住 Tomcat 线程。先 flush 一条注释行，`send()` 立刻返回响应头，预检语义才成立。

3. **不能声明 `produces = TEXT_EVENT_STREAM_VALUE`**：一旦声明，异常路径上 `GlobalExceptionHandler` 返回的 `Result` 找不到能写 `text/event-stream` 的 converter，会变成 406 而不是干净的 `code:0`。规避 = 方法上不写 `produces`，在 `StreamingResponseBody` lambda 内首行手动 `response.setContentType("text/event-stream;charset=UTF-8")`，并靠同步 `send()` 把上游错误提前变成 `Result`。**已开始写字节后**的失败只能发 `event: error`，HTTP 状态仍是 200 —— 前端按 `event:` 名区分，不看状态码。

---

## 验证

**前置**：MySQL、Redis 起好；Spring 1127 起来；`ai/.env` 填好 DeepSeek key；`uvicorn app.main:app --port 8000` 起来。

**端到端手工验收**：
1. `curl http://127.0.0.1:8000/ai/health` → `{"configured":true,"model":"deepseek-chat"}`
2. 登录 → 进「我的文章」→ 打开编辑器，输入标题和正文
3. 点 AI 条里的「润色」→ 文字**逐字**出现（不是等完才一次性显示）
4. 选中正文一段 → 点「改写」→ 点「替换选中」→ 正文对应位置被替换，且左侧出现「有未保存的改动」
5. `Ctrl+S` → 保存成功，脏标记消失（证明 `dirty` 派生逻辑没被破坏）
6. 生成中点「停止」→ 1 秒内停住，uvicorn 访问日志里该请求已结束（证明取消链路通）
7. **停掉 Python** 再点「续写」→ 出现错误 banner，浏览器 Network 里是 HTTP 200 + `code:0`，**不是 5xx、不是断流**
8. 把 key 改错重启 Python → 报「未配置模型密钥」类错误，而不是吐半截流
9. 生成过程中直接关掉标签页 → Spring 侧日志出现 `stream closed`，无线程泄漏、无上游残留请求

**回归检查**（改到了公共文件，必须过一遍）：
- `JwtInterceptor` 改动后，未登录访问任意受保护接口仍返回 `code:0 + 登录已过期`
- 编辑器原有功能：新建文章、存草稿、发布、删除、离开时未保存确认、`Ctrl+S` 全部正常
- 窄屏 ≤1000px 下编辑器单列布局正常，AI 条 `max-height` 内滚动不撑破

---

## 二期（本期不做）

`search_my_articles` / `get_my_article` 工具调用循环（复用转发来的用户 JWT 回调 `/article/my`、`/article/{id}`，最多 1 轮工具 + 2 步上限，工具失败降级不中断生成）；多轮对话历史；基于 Redis 的速率限制；首个 delta 超时提示；服务间窄权限短时 token；用量统计。
