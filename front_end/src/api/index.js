import { http } from './request'

/* ---------------- 用户 / 登录 ---------------- */

export const userApi = {
  /** 公开 */
  login: (userName, password) => http.post('/user/login', { userName, password }),
  register: (payload) => http.post('/user/register', payload),
  resetPassword: (userName, password) => http.post('/user/reset-password', { userName, password }),

  /** 需要登录 */
  info: () => http.get('/user/info'),
  logout: () => http.post('/user/logout'),
}

/* ---------------- 文章 ---------------- */

export const articleApi = {
  /** 公开：只返回已发布。params: pageNum/pageSize/keyword/author/sortBy/sortOrder */
  publicPage: (params) => http.get('/article/list', params),

  /** 公开（可选登录）：草稿仅作者本人可见，其他人按「文章不存在」处理 */
  detail: (id) => http.get(`/article/${id}`),

  /** 「我的文章」：不强制 status=1，params 额外支持 status/categoryId */
  myPage: (params) => http.get('/article/my', params),

  /** 个人总览统计：按当前登录用户聚合 */
  overview: () => http.get('/article/overview'),

  /** 新增后返回新文章 id */
  add: (article) => http.post('/article/add', article),
  update: (article) => http.put('/article/update', article),
  remove: (id) => http.del(`/article/delete/${id}`),
}

/* ---------------- 分类 ---------------- */

export const categoryApi = {
  /** 返回的每一项带 articleCount（当前登录用户名下的篇数） */
  list: () => http.get('/category/list'),
  /** 公开：给未登录首页用的分类列表，只有 id/name/sortOrder */
  publicList: () => http.get('/category/public'),
  add: (payload) => http.post('/category/add', payload),
  update: (payload) => http.put('/category/update', payload),
  remove: (id) => http.del(`/category/delete/${id}`),
  articles: (id, params) => http.get(`/category/${id}/articles`, params),
}

/* ---------------- 管理员 ---------------- */

export const adminApi = {
  /** 全站文章，含草稿。不支持 status 过滤 */
  articlePage: (params) => http.get('/admin/article/list', params),
  updateArticle: (article) => http.put('/admin/article/update', article),
  removeArticle: (id) => http.del(`/admin/article/delete/${id}`),
  /** 全量返回，无分页 */
  users: () => http.get('/admin/user/list'),
}

/* ---------------- AI 写作助手 ---------------- */

export const aiApi = {
  /** 探活：未配置模型密钥时 Python 返回 503 */
  health: () => http.get('/ai/health'),

  // 生成接口是 SSE 流，不走这里 —— 见 api/aiStream.js 的 streamAi()
}