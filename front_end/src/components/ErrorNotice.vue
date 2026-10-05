<script setup>
import Icon from '@/components/Icon.vue'

/**
 * 请求失败的统一呈现。区分「业务失败」「连不上后端」「后端依赖（Redis/DB）挂了」
 * —— 后两种要能重试，不能只甩一句话。
 */
const props = defineProps({
  error: { type: Object, default: null },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['retry'])

const MESSAGE = {
  network: '连接不上后端服务',
  service: '后端依赖不可用',
}

function titleOf(e) {
  return MESSAGE[e?.kind] || '加载失败'
}
</script>

<template>
  <div v-if="error" class="banner banner--error" :class="{ 'banner--compact': compact }">
    <Icon name="alert" :size="15" />
    <span class="grow">
      <b v-if="!compact">{{ titleOf(error) }}</b>
      <template v-if="!compact"> · </template>{{ error.message }}
      <template v-if="error.kind === 'service'">
        · 后端连不上 Redis 时会主动拒绝请求，请确认 Redis 已启动
      </template>
    </span>
    <button class="btn btn--sm btn--ghost banner__action" type="button" @click="emit('retry')">
      <Icon name="refresh" :size="13" />
      重试
    </button>
  </div>
</template>

<style scoped>
.banner--compact {
  padding: 8px 11px;
}
</style>