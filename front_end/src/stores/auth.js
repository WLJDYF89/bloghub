import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { userApi } from '@/api'
import { getToken, setToken } from '@/api/request'

/**
 * 登录态。
 *
 * 注意：/user/login 只回 userId / userName / nickName / token，**不带角色**。
 * 角色只能靠 /user/info 拿 roleId（1=管理员，2=普通用户），
 * 所以登录成功后必须补一次 info —— 否则 404 页和导航都没法按角色分流。
 */
export const useAuth = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref(null)
  /** 是否已经尝试过恢复登录态（路由守卫靠它决定要不要等） */
  const ready = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value && user.value))
  const isAdmin = computed(() => user.value?.roleId === 1)
  const displayName = computed(() => user.value?.nickname || user.value?.userName || '')
  const roleLabel = computed(() => (isAdmin.value ? '管理员' : '作者'))

  function clear() {
    setToken('')
    token.value = ''
    user.value = null
  }

  /** 从后端刷新用户信息；失败（含 token 失效）就清空登录态 */
  async function fetchInfo() {
    if (!token.value) return null
    try {
      user.value = await userApi.info()
      return user.value
    } catch {
      clear()
      return null
    }
  }

  /** 应用启动时调一次：有 token 就去换身份 */
  async function restore() {
    if (ready.value) return
    if (token.value) {
      await fetchInfo()
    }
    ready.value = true
  }

  async function login(userName, password) {
    const data = await userApi.login(userName, password)
    setToken(data.token)
    token.value = data.token
    // 登录接口不回角色，必须补一次
    await fetchInfo()
    // 极端情况：info 失败但 token 拿到了，用登录返回值兜底，角色按普通用户处理
    if (!user.value) {
      user.value = { userName: data.userName, nickname: data.nickName, roleId: 2 }
    }
    return user.value
  }

  async function logout() {
    try {
      // 后端把 token 拉黑（Redis 挂了会抛错）——失败也必须清本地，否则用户卡在假登录里
      await userApi.logout()
    } catch {
      /* 忽略：本地登出是无条件的 */
    }
    clear()
  }

  return {
    token,
    user,
    ready,
    isLoggedIn,
    isAdmin,
    displayName,
    roleLabel,
    restore,
    fetchInfo,
    login,
    logout,
    clear,
  }
})