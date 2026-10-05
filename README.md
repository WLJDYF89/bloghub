# BlogHub

一个前后端分离的个人博客系统：游客可浏览已发布文章，登录后进入内容工作台写作、管理与分类，并内置基于大模型的 AI 写作助手（SSE 流式输出）。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端 | Vue 3 · Vite · Vue Router · Pinia · 原生 CSS（设计令牌集中在 `src/style.css`） |
| 后端 | Spring Boot 4 · Spring MVC · MyBatis · MySQL · Redis · JWT（jjwt）· BCrypt |
| AI 服务 | FastAPI · Uvicorn · OpenAI 兼容 SDK（DeepSeek / 通义千问 / Ollama 均可） |
| 数据库 | MySQL 8（utf8mb4 / utf8mb4_0900_ai_ci） |

## 目录结构

```
BlogHub/
├── front_end/              前端 SPA（Vue 3 + Vite）
│   └── src/
│       ├── api/            统一请求层（/api 前缀，开发期由 Vite 代理到后端）
│       ├── views/          public 公开站 · auth 认证页 · console 内容工作台
│       ├── layouts/        PublicLayout / ConsoleLayout
│       ├── components/     图标、分页、状态标签、确认弹窗等通用组件
│       └── assets/         共享样式（admin.css 等）
├── server/                 后端（Spring Boot）
│   └── src/main/
│       ├── java/guat/lxy/
│       │   ├── controller/ article · user · category · admin · ai
│       │   ├── service/    业务实现
│       │   ├── mapper/     MyBatis 接口（SQL 在 resources/mapper/*.xml）
│       │   └── interceptor/ JwtInterceptor 统一鉴权
│       └── resources/      application.yml · mapper/*.xml
├── ai/                     AI 写作助手（FastAPI）
│   ├── main.py             /ai/chat 流式接口 + 心跳 + 异常翻译
│   └── app/                prompts · llm · schemas · sse · config
├── sql/                    schema.sql（建库建表） · seed_*.sql（示例数据）
└── README.md
```

## 功能

**公开站（免登录）**

- 文章列表：关键词搜索、按分类筛选、分页
- 文章详情：已发布文章对所有人开放；草稿仅作者本人可见

**认证**

- 注册 / 登录 / 找回密码（重置密码）
- 登录态使用 JWT；登出时把 token 写入 Redis 黑名单

**内容工作台（登录后）**

- 概览：篇数统计、最久未更新的草稿、最近编辑
- 我的文章：草稿与已发布统一管理，支持关键词 / 状态 / 分类筛选与排序（默认按更新时间倒序）
- 编辑器：新建 / 编辑文章，配合 AI 写作助手（续写、润色、改写、起标题、列大纲、翻译、自定义指令）
- 分类：查看分类下的文章；分类的增删改仅管理员可操作
- 设置：个人资料

**管理员专属**

- 全站文章：跨作者检索与管理
- 用户管理

## 环境要求

- JDK 24
- Maven 3.9+
- Node.js `^22.18` 或 `>=24.12`
- MySQL 8.0+
- Redis
- Python 3.10+（仅 AI 服务需要）

## 快速开始

### 1. 初始化数据库

初始化顺序不能颠倒：分类的示例数据依赖文章 id。

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql
mysql -uroot -p --default-character-set=utf8mb4 bloghub < sql/seed_articles.sql     # 可选
mysql -uroot -p --default-character-set=utf8mb4 bloghub < sql/seed_categories.sql   # 可选
```

`schema.sql` 会建库 `bloghub`、建 6 张表，并写入 `role` 基础数据。注意它会 **DROP 并重建所有表**，已有数据会丢失，升级结构请改用 `ALTER TABLE`。

数据库地址、账号密码在 `server/src/main/resources/application.yml` 的 `spring.datasource` 中配置，与本机不一致时请修改。

### 2. 启动后端（端口 1127）

```bash
cd server
mvn spring-boot:run
```

需要本机 Redis 已启动（地址、密码同样在 `application.yml` 中配置，用于登录态黑名单）。用 IDEA 直接运行 `BlogHubServerApplication` 也可以。

### 3. 启动 AI 服务（端口 8000）

```bash
cd ai
conda create -n blog-hub python=3.11 -y
conda activate blog-hub
pip install -r requirements.txt

copy .env.example .env        # Windows；把 LLM_API_KEY 换成你自己的密钥
python main.py
```

`.env` 里没配 `LLM_API_KEY` 时服务会直接启动失败，这是有意设计，避免运行期第一次生成才报错。DeepSeek / 通义千问 / Ollama 都是 OpenAI 兼容协议，只需替换 `LLM_BASE_URL` 与 `LLM_MODEL`。

### 4. 启动前端（端口 5173）

```bash
cd front_end
npm install
npm run dev
```

打开 `http://localhost:5173`。开发期 `/api` 由 Vite 代理到 `http://localhost:1127`，前端代码里只出现 `/api` 前缀，不涉及跨域配置。

### 5. 创建第一个管理员

项目没有「注册成管理员」的入口（注册接口固定写入 `role_id = 2`），首次部署需要手工提升：

```sql
-- 1) 先在页面上注册一个账号，假设用户名是 alice
UPDATE `user` SET role_id = 1 WHERE username = 'alice';
-- 2) 重新登录（JWT 不含角色，后端每次请求查库取最新角色）
```

## 端口约定

| 服务 | 端口 |
| --- | --- |
| 前端（Vite dev） | 5173 |
| 后端（Spring Boot） | 1127 |
| AI 服务（FastAPI） | 8000 |
| MySQL | 3306 |
| Redis | 6379 |

## 主要接口

后端统一返回 `{ code, msg, data }`：`code = 1` 成功、`0` 失败（失败也返回 HTTP 200），前端在 `src/api/request.js` 中统一解包。

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 用户 | `POST /user/register` · `POST /user/login` · `GET /user/info` · `POST /user/logout` · `POST /user/reset-password` | 注册 / 登录 / 当前用户 / 登出 / 找回密码 |
| 文章 | `GET /article/list` · `GET /article/{id}` | 公开列表 / 详情 |
| 文章 | `POST /article/add` · `PUT /article/update` · `DELETE /article/delete/{id}` | 增 / 改 / 删（仅作者本人） |
| 文章 | `GET /article/my` · `GET /article/overview` | 我的文章（支持 keyword / status / categoryId / sortBy / sortOrder）/ 个人总览 |
| 分类 | `GET /category/public` · `GET /category/list` · `POST /category/add` · `PUT /category/update` · `DELETE /category/delete/{id}` · `GET /category/{id}/articles` | 公开分类 / 分类管理 / 分类下文章 |
| 管理 | `GET /admin/article/list` · `PUT /admin/article/update` · `DELETE /admin/article/delete/{id}` · `GET /admin/user/list` | 仅管理员 |
| AI | `POST /ai/chat` | 需登录，SSE 流式生成 |

鉴权由 `JwtInterceptor` 统一处理，白名单为：`POST /user/login`、`POST /user/register`、`POST /user/reset-password`、`GET /article/list`、`GET /category/public`，以及纯数字 id 的文章详情（可选登录，认不出身份即按游客处理）。

## 数据库

`sql/schema.sql` 包含 6 张表：

| 表 | 说明 |
| --- | --- |
| `role` | 系统角色权限表（1 = 超级管理员，2 = 普通用户，id 被后端硬编码引用，勿改） |
| `user` | 用户信息表 |
| `category` | 文章分类表 |
| `article` | 博客文章数据表 |
| `tag` / `article_tag` | 标签与文章-标签关联表（预留，当前业务未使用） |

## 安全与配置说明

- `ai/.env` 与各类密钥一律不提交，已在 `.gitignore` 中排除，仓库内只保留 `.env.example` 模板。
- 密码使用 BCrypt 存储；库里的旧 MD5 记录会在用户登录校验通过时自动升级为 BCrypt。
- 登录态校验、token 黑名单在 `JwtInterceptor` / `TokenService` 中统一完成，Python 侧不解析 JWT。
- 已知缺口：`POST /user/reset-password` 不做身份核验（知道用户名即可重置密码），仅适用于本地或演示环境，对外部署前必须补上校验。