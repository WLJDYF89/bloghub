<script setup>
import { RouterLink, RouterView } from 'vue-router'
import Icon from '@/components/Icon.vue'
import BrandMark from '@/components/BrandMark.vue'
import { useAuth } from '@/stores/auth'
import { initial } from '@/utils/format'

const auth = useAuth()
</script>

<template>
  <div class="pub">
    <header class="pub__nav">
      <div class="pub__nav-inner">
        <RouterLink class="pub__brand" :to="{ name: 'home' }">
          <span class="pub__mark"><BrandMark :size="27" /></span>
          <span class="pub__brand-text">BlogHub</span>
        </RouterLink>

        <nav class="pub__links">
          <RouterLink class="pub__link" :to="{ name: 'home' }">文章</RouterLink>
        </nav>

        <span class="grow" />

        <div class="pub__actions">
          <template v-if="auth.isLoggedIn">
            <RouterLink class="btn btn--ghost btn--sm" :to="{ name: 'overview' }">
              工作台
              <Icon name="arrowUpRight" :size="13" />
            </RouterLink>
            <RouterLink class="pub__me" :to="{ name: 'settings' }" :title="auth.displayName">
              <span class="avatar avatar--sm avatar--accent">{{ initial(auth.displayName) }}</span>
              <span class="pub__me-name">{{ auth.displayName }}</span>
            </RouterLink>
          </template>
          <template v-else>
            <RouterLink class="btn btn--ghost btn--sm" :to="{ name: 'login' }">登录</RouterLink>
            <RouterLink class="btn btn--primary btn--sm" :to="{ name: 'register' }">注册</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <main class="pub__main">
      <RouterView />
    </main>

    <footer class="pub__foot">
      <div class="pub__foot-inner">
        <span>BlogHub</span>
        <span class="pub__foot-sep">·</span>
        <span>一个用来写和读的地方</span>
        <span class="grow" />
        <RouterLink class="pub__foot-link" :to="{ name: 'login' }">写作者入口</RouterLink>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.pub {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.pub__nav {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  height: var(--nav-h);
  background: rgba(253, 252, 249, 0.82);
  backdrop-filter: saturate(160%) blur(12px);
  border-bottom: 1px solid var(--line);
}

.pub__nav-inner {
  max-width: var(--content-max);
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.pub__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: none;
}

/* 底色/圆角由 BrandMark 里的 mark.svg 自带，这里只定尺寸 */
.pub__mark {
  width: 27px;
  height: 27px;
  display: grid;
  place-items: center;
  flex: none;
}

.pub__brand-text {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.pub__links {
  display: flex;
  align-items: center;
  gap: 2px;
}

.pub__link {
  padding: 6px 10px;
  border-radius: var(--r-sm);
  font-size: 15px;
  color: var(--text-2);
  transition:
    color var(--t-fast) var(--ease),
    background-color var(--t-fast) var(--ease);
}

.pub__link:hover {
  color: var(--text);
  background: var(--surface-2);
}

.pub__link.router-link-exact-active {
  color: var(--text);
  font-weight: 500;
}

.pub__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.pub__me {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 3px 9px 3px 3px;
  border-radius: var(--r-sm);
  transition: background-color var(--t-fast) var(--ease);
}

.pub__me:hover {
  background: var(--surface-2);
}

.pub__me-name {
  font-size: 15px;
  font-weight: 500;
  max-width: 10ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pub__main {
  flex: 1;
}

.pub__foot {
  border-top: 1px solid var(--line);
  background: var(--surface);
}

.pub__foot-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-3);
}

.pub__foot-sep {
  color: var(--text-4);
}

.pub__foot-link:hover {
  color: var(--accent);
}

@media (max-width: 560px) {
  .pub__nav-inner {
    padding: 0 16px;
    gap: 12px;
  }
  .pub__me-name {
    display: none;
  }
  .pub__foot-inner {
    padding: 18px 16px;
  }
}
</style>