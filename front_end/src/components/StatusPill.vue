<script setup>
/**
 * 文章状态。status 只有 0 / 1：
 * 0=草稿（空心中性点）· 1=已发布（实心墨色点）。
 * 刻意不用绿色/红色 —— 草稿不是错误，颜色留给真正需要警觉的地方。
 */
defineProps({
  status: { type: Number, default: null },
  size: { type: String, default: 'md' },
})

const LABEL = { 0: '草稿', 1: '已发布' }
</script>

<template>
  <span
    class="status"
    :class="[status === 1 ? 'status--live' : 'status--draft', size === 'sm' && 'status--sm']"
  >
    <i class="status__dot" />
    {{ LABEL[status] || '未知' }}
  </span>
</template>

<style scoped>
.status--sm {
  font-size: 12px;
}
.status--sm .status__dot {
  width: 6px;
  height: 6px;
}
</style>