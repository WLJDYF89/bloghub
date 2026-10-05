/** 后端返回的时间格式统一是 'yyyy-MM-dd HH:mm:ss' 字符串 */

/** '2026-09-20 14:03:00' -> '09-20 14:03' */
export function formatDateTime(value) {
  if (!value) return '—'
  const s = String(value)
  return s.length >= 16 ? s.slice(5, 16) : s
}

/** '2026-09-20 14:03:00' -> '2026-09-20' */
export function formatDate(value) {
  if (!value) return '—'
  return String(value).slice(0, 10)
}

/**
 * 距今多久。只用于「最近编辑」这类列表，超过 30 天就直接给日期，
 * 不写「3 个月前」那种估摸出来的说法。
 */
export function formatAgo(value) {
  if (!value) return '—'
  const t = parseTime(value)
  if (!t) return String(value)
  const diff = Date.now() - t
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d === 1) return '昨天'
  if (d < 30) return `${d} 天前`
  return formatDate(value)
}

/** 距今多少整天。管理员列表接口不返回天数字段，只能前端按 updateTime 算。 */
export function daysSince(value) {
  const t = parseTime(value)
  if (t === null) return null
  return Math.max(0, Math.floor((Date.now() - t) / 86400000))
}

function parseTime(value) {
  const s = String(value).replace(' ', 'T')
  const t = new Date(s).getTime()
  return Number.isNaN(t) ? null : t
}

/** 头像里那个字：昵称优先，取第一个字符 */
export function initial(name) {
  const n = String(name || '').trim()
  return n ? n[0].toUpperCase() : '?'
}

/** 正文纯文本预览用（去掉 markdown 记号，压缩空白） */
export function plainText(text) {
  return String(text || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/^[>#\-*\s]+/gm, ' ')
    .replace(/[*_~]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

export function countWords(text) {
  return plainText(text).length
}