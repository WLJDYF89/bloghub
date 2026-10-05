<script setup>
import Icon from '@/components/Icon.vue'

/**
 * 空态。每个列表都必须给一个能执行的下一步，不能只放一张灰图。
 */
defineProps({
  icon: { type: String, default: 'inbox' },
  title: { type: String, default: '这里还是空的' },
  desc: { type: String, default: '' },
})
</script>

<template>
  <div class="empty">
    <span class="empty__mark">
      <Icon :name="icon" :size="19" />
    </span>
    <p class="empty__title">{{ title }}</p>
    <p v-if="desc" class="empty__desc">{{ desc }}</p>
    <div v-if="$slots.action" class="empty__action">
      <slot name="action" />
    </div>
  </div>
</template>