<script setup>
import { RouterLink } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
</script>

<template>
  <div class="page">
    <div class="page__inner page--narrow">
      <EmptyState
        icon="search"
        title="页面不存在"
        desc="你访问的地址不在当前站点里，或者这篇文章已经不在这里了。"
      >
        <template #action>
          <RouterLink class="btn btn--primary" :to="{ name: 'home' }">回到首页</RouterLink>
        </template>
      </EmptyState>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: calc(100dvh - var(--nav-h));
  display: grid;
  place-items: center;
}

.page__inner {
  width: min(100%, 620px);
}
</style>
