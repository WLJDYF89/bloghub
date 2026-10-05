import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/stores/auth'

const routes = [
  /* ---------------- 公开站（读者视角，不需要登录） ---------------- */
  {
    path: '/',
    component: () => import('@/layouts/PublicLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/public/HomeView.vue'),
        meta: { title: '文章' },
      },
      {
        path: 'articles/:id',
        name: 'article',
        component: () => import('@/views/public/ArticleView.vue'),
        meta: { title: '阅读' },
      },
    ],
  },

  /* ---------------- 认证页（整屏，不带任何外壳） ---------------- */
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true },
  },
  {
    path: '/forgot',
    name: 'forgot',
    component: () => import('@/views/auth/ForgotPasswordView.vue'),
    meta: { title: '找回密码', guestOnly: true },
  },

  /* ---------------- 内容工作台（登录后） ---------------- */
  {
    path: '/console',
    component: () => import('@/layouts/ConsoleLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: { name: 'overview' } },
      {
        path: 'overview',
        name: 'overview',
        component: () => import('@/views/console/OverviewView.vue'),
        meta: { title: '概览' },
      },
      {
        path: 'articles',
        name: 'my-articles',
        component: () => import('@/views/console/ArticlesView.vue'),
        meta: { title: '我的文章' },
      },
      {
        path: 'articles/new',
        name: 'article-new',
        component: () => import('@/views/console/EditorView.vue'),
        meta: { title: '新建文章' },
      },
      {
        path: 'articles/:id/edit',
        name: 'article-edit',
        component: () => import('@/views/console/EditorView.vue'),
        meta: { title: '编辑文章' },
      },
      {
        path: 'categories',
        name: 'categories',
        component: () => import('@/views/console/CategoriesView.vue'),
        meta: { title: '分类' },
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/console/SettingsView.vue'),
        meta: { title: '设置' },
      },
      /* ---- 仅管理员 ---- */
      {
        path: 'all-articles',
        name: 'all-articles',
        component: () => import('@/views/console/AllArticlesView.vue'),
        meta: { title: '全站文章', requiresAdmin: true },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/console/UsersView.vue'),
        meta: { title: '用户', requiresAdmin: true },
      },
    ],
  },

  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    return { top: 0 }
  },
})

const BASE_TITLE = 'BlogHub'

router.beforeEach(async (to) => {
  const auth = useAuth()

  // 首次进入时用本地 token 换一次身份（拿 roleId）。只做一次。
  if (!auth.ready) {
    await auth.restore()
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }

  // 已登录就不再回认证页
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return { name: 'overview' }
  }

  // 管理员专属：非管理员直接送回概览，不给一个「你没权限」的死页面
  if (
    to.meta.requiresAdmin &&
    to.matched.some((r) => r.meta.requiresAdmin) &&
    auth.isLoggedIn &&
    !auth.isAdmin
  ) {
    return { name: 'overview' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${BASE_TITLE}` : BASE_TITLE
})

export default router