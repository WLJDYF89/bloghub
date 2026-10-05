/**
 * AI 生成接口的流式请求层。
 *
 * 为什么不复用 request.js：那边强制 `res.json()` 并按 { code, msg, data } 解包，
 * 而 AI 接口是 SSE 事件流，形状完全不同。
 *
 * 事件协议（与 ai/app/main.py 一一对应）：
 *   event: delta  data:{"v":"片段"}            文本增量
 *   event: done   data:{"text":"全文",...}     权威文本，回填时用它
 *   event: error  data:{"code":"...","msg":"..."}
 *   以「:」开头的行是注释（ready / hb 心跳），忽略。
 */
import { getToken, notifyAuthLost } from './request'

const BASE = '/api'

export class AiStreamError extends Error {
  constructor(message, { code, kind = 'ai' } = {}) {
    super(message)
    this.name = 'AiStreamError'
    this.code = code
    /** 'ai' 生成失败 · 'network' 连不上 · 'auth' 登录态失效 */
    this.kind = kind
  }
}

/**
 * @param {string} path   形如 '/ai/chat'（BASE 前缀在函数内补）
 * @param {object} body   请求体
 * @param {{ onEvent?: (name: string, payload: object) => void, signal?: AbortSignal }} options
 */
export async function streamAi(path, body, { onEvent, signal } = {}) {
  const token = getToken()

  let res
  try {
    res = await fetch(BASE + path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal,
      // 不能用 EventSource：它不支持 POST，也带不上 Authorization
    })
  } catch (e) {
    if (e?.name === 'AbortError') throw e
    throw new AiStreamError('连接不上服务器，请确认后端已在 1127 端口启动', { kind: 'network' })
  }

  const contentType = res.headers.get('Content-Type') || ''

  // 预检失败：Spring 在开始写流之前把「Python 没启动 / 上游非 200」转成了 Result{code:0,msg}，
  // 那是个普通 JSON 响应（HTTP 200），不是事件流。
  if (contentType.includes('application/json')) {
    let payload = null
    try {
      payload = await res.json()
    } catch {
      /* 不是 JSON 就走下面的兜底文案 */
    }
    const msg = payload?.msg || `AI 请求失败（HTTP ${res.status}）`
    const kind = /登录|token|Token/.test(msg) ? 'auth' : 'ai'
    if (kind === 'auth' && token) notifyAuthLost()
    throw new AiStreamError(msg, { code: payload?.code, kind })
  }

  if (!res.ok) {
    throw new AiStreamError(`AI 服务返回 HTTP ${res.status}`, { kind: 'network' })
  }

  if (!res.body) {
    throw new AiStreamError('当前浏览器不支持流式响应', { kind: 'ai' })
  }

  const reader = res.body.getReader()
  // stream: true —— 一个中文字符可能被切在 chunk 边界上，不能按块直接 decode
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 一次 read 可能含多个事件块，必须循环分发
      let sep
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        dispatch(buffer.slice(0, sep), onEvent)
        buffer = buffer.slice(sep + 2)
      }
    }
    if (buffer.trim()) dispatch(buffer, onEvent)
  } finally {
    // 异常或提前返回时把底层流关掉，别让连接悬着
    reader.cancel().catch(() => {})
  }
}

function dispatch(block, onEvent) {
  let event = 'message'
  const dataLines = []

  for (const raw of block.split('\n')) {
    const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }

  if (!dataLines.length) return

  let payload
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch {
    return
  }
  onEvent?.(event, payload)
}
