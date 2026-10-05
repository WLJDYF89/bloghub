/**
 * 统一请求层。
 *
 * 后端所有接口都返回 { code: 1 成功 / 0 失败, msg, data }，
 * 失败也是 HTTP 200，所以不能靠 response.ok 判成败，必须看 code。
 *
 * 鉴权：请求头 Authorization: Bearer <token>。
 * 登录态失效时后端返回 code=0 + 中文 msg（「登录已过期」/「您已退出登录，请重新登录」），
 * 这里统一识别成 401 语义，交给 auth store 清登录态并跳登录页。
 */

const BASE = '/api'

const TOKEN_KEY = 'bh_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  constructor(message, { code, kind = 'api' } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    /** 'api' 业务失败 · 'network' 连不上 · 'auth' 登录态失效 · 'service' 依赖不可用 */
    this.kind = kind
  }
}

/** 登录态失效时由 auth store 注册进来的回调 */
let onAuthLost = null
export function setAuthLostHandler(fn) {
  onAuthLost = fn
}

/** 供流式请求（aiStream.js）复用：它不走本文件的解包逻辑，但失效处理要一致 */
export function notifyAuthLost() {
  onAuthLost?.()
}

/** 后端依赖（Redis / DB）不可用时的文案特征 —— 这类失败要能重试，不是用户操作错了 */
function classify(msg, code) {
  if (code !== 0) return 'api'
  if (/登录|token|Token/.test(msg)) return 'auth'
  if (/服务暂不可用|Redis/i.test(msg)) return 'service'
  return 'api'
}

function buildUrl(path, params) {
  const url = new URL(BASE + path, window.location.origin)
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v === undefined || v === null || v === '') continue
      url.searchParams.set(k, String(v))
    }
  }
  return url.pathname + url.search
}

export async function request(path, { method = 'GET', params, body } = {}) {
  const token = getToken()

  let res
  try {
    res = await fetch(buildUrl(path, params), {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError('连接不上服务器，请确认后端已在 1127 端口启动', { kind: 'network' })
  }

  // 网关级 401/403（正常业务失败不会走到这）
  if (res.status === 401 || res.status === 403) {
    if (token) onAuthLost?.()
    throw new ApiError('登录已过期，请重新登录', { kind: 'auth' })
  }

  let payload
  try {
    payload = await res.json()
  } catch {
    throw new ApiError(`服务器返回了无法解析的内容（HTTP ${res.status}）`, { kind: 'network' })
  }

  if (payload && payload.code === 1) {
    return payload.data
  }

  const msg = payload?.msg || `请求失败（HTTP ${res.status}）`
  const kind = classify(msg, payload?.code)
  if (kind === 'auth' && token) onAuthLost?.()
  throw new ApiError(msg, { code: payload?.code, kind })
}

export const http = {
  get: (path, params) => request(path, { params }),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  del: (path) => request(path, { method: 'DELETE' }),
}