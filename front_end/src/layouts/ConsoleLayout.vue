<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import BrandMark from '@/components/BrandMark.vue'
import { useAuth } from '@/stores/auth'
import { initial } from '@/utils/format'

const auth = useAuth()
const route = useRoute()
const router = useRouter()

const collapsed = ref(localStorage.getItem('bh_rail') === '1')
const drawerOpen = ref(false)
const userMenu = ref(false)

function toggleRail() {
  collapsed.value = !collapsed.value
  localStorage.setItem('bh_rail', collapsed.value ? '1' : '0')
}

const nav = computed(() => {
  const base = [
    { name: 'overview', label: '概览', icon: 'gauge' },
    { name: 'my-articles', label: '我的文章', icon: 'docs' },
    { name: 'categories', label: '分类', icon: 'folder' },
  ]
  if (auth.isAdmin) {
    base.push(
      { group: '全站' },
      { name: 'all-articles', label: '全站文章', icon: 'layers' },
      { name: 'users', label: '用户', icon: 'users' },
    )
  }
  base.push({ group: '账号' }, { name: 'settings', label: '设置', icon: 'gear' })
  return base
})

const pageTitle = computed(() => route.meta.title || 'BlogHub')

async function doLogout() {
  userMenu.value = false
  await auth.logout()
  router.push({ name: 'home' })
}

function onDocClick(e) {
  if (!e.target.closest('.rail__user-wrap')) userMenu.value = false
}
onMounted(() => document.addEventListener('click', onDocClick))
onUnmounted(() => document.removeEventListener('click', onDocClick))

router.afterEach(() => {
  drawerOpen.value = false
})
</script>

<template>
  <div class="shell" :class="{ 'is-collapsed': collapsed, 'is-drawer-open': drawerOpen }">
    <!-- ---------------- 玻璃侧栏 ---------------- -->
    <aside class="rail">
      <div class="rail__head">
        <RouterLink to="/" class="rail__mark" aria-label="回到博客首页">
          <BrandMark :size="30" />
        </RouterLink>
        <div class="rail__wordmark">
          <div class="rail__name">BlogHub</div>
          <div class="rail__sub">内容工作台</div>
        </div>
        <button
          class="rail__collapse"
          type="button"
          :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="toggleRail"
        >
          <Icon name="collapse" :size="15" />
        </button>
      </div>

      <nav class="rail__nav">
        <template v-for="(item, i) in nav" :key="item.name || item.group || i">
          <div v-if="item.group" class="rail__group">{{ item.group }}</div>
          <RouterLink
            v-else
            class="rail__item"
            :class="{ 'is-active': route.name === item.name }"
            :to="{ name: item.name }"
            :title="item.label"
          >
            <Icon :name="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </template>
      </nav>

      <div class="rail__foot">
        <div class="rail__user-wrap" style="position: relative">
          <button class="rail__user" type="button" @click.stop="userMenu = !userMenu">
            <span class="avatar avatar--sm avatar--ink">{{ initial(auth.displayName) }}</span>
            <span class="rail__user-meta">
              <span class="rail__user-name">{{ auth.displayName }}</span>
              <span class="rail__user-role">{{ auth.roleLabel }}</span>
            </span>
            <Icon name="more" :size="15" style="margin-left: auto; opacity: 0.5" />
          </button>

          <div v-if="userMenu" class="menu rail__menu">
            <RouterLink class="menu__item" :to="{ name: 'settings' }" @click="userMenu = false">
              <Icon name="gear" :size="14" />
              账号设置
            </RouterLink>
            <RouterLink class="menu__item" :to="{ name: 'home' }" @click="userMenu = false">
              <Icon name="external" :size="14" />
              查看博客
            </RouterLink>
            <div class="divider" style="margin: 5px 0" />
            <button class="menu__item menu__item--danger" type="button" @click="doLogout">
              <Icon name="logout" :size="14" />
              退出登录
            </button>
          </div>
        </div>
      </div>
    </aside>

    <!-- ---------------- 主区 ---------------- -->
    <div class="main">
      <header class="topbar">
        <button
          class="btn btn--ghost btn--icon topbar__burger"
          type="button"
          aria-label="打开导航"
          @click="drawerOpen = !drawerOpen"
        >
          <Icon name="collapse" :size="16" />
        </button>

        <span class="topbar__title">{{ pageTitle }}</span>
        <span class="topbar__spacer" />

        <RouterLink class="btn btn--ghost btn--sm topbar__blog" :to="{ name: 'home' }">
          查看博客
          <Icon name="arrowUpRight" :size="13" />
        </RouterLink>
        <RouterLink class="btn btn--primary btn--sm" :to="{ name: 'article-new' }">
          <Icon name="pen" :size="14" />
          写文章
        </RouterLink>
      </header>

      <RouterView />
    </div>
  </div>
</template>

<style scoped>
.rail__collapse {
  margin-left: auto;
  width: 26px;
  height: 26px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--r-xs);
  color: var(--text-3);
  transition: color var(--t-fast) var(--ease);
}

.rail__collapse:hover {
  color: var(--text);
}

.rail__menu {
  bottom: calc(100% + 6px);
  left: 8px;
  right: 8px;
}

.topbar__burger {
  display: none;
}

.topbar__blog {
  display: inline-flex;
}

@media (max-width: 860px) {
  .topbar__burger {
    display: inline-flex;
  }
  .topbar__blog {
    display: none;
  }
  .rail__collapse {
    display: none;
  }
}

@media (max-width: 560px) {
  .topbar__title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>