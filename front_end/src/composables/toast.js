import { ref } from 'vue'

/**
 * 极小的 toast 队列。只做「操作成功/失败」的即时反馈，
 * 不做消息中心 —— 后端没有消息，前端也就不该长出一个。
 */
const items = ref([])
let seq = 0

export function useToast() {
  function push(message, type = 'ok') {
    const id = ++seq
    items.value.push({ id, message, type })
    setTimeout(() => {
      items.value = items.value.filter((t) => t.id !== id)
    }, type === 'error' ? 4200 : 2400)
  }

  return {
    items,
    ok: (msg) => push(msg, 'ok'),
    error: (msg) => push(msg, 'error'),
  }
}