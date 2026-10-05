<script setup>
import { computed } from 'vue'
import Icon from '@/components/Icon.vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  totalPages: { type: Number, default: 0 },
})

const emit = defineEmits(['go'])

const from = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.pageSize + 1))
const to = computed(() => Math.min(props.page * props.pageSize, props.total))

/** 最多显示 5 个页码，两端用省略号收口 */
const pages = computed(() => {
  const last = props.totalPages
  if (last <= 7) return Array.from({ length: last }, (_, i) => i + 1)
  const p = props.page
  const out = [1]
  const start = Math.max(2, Math.min(p - 1, last - 4))
  const end = Math.min(last - 1, Math.max(p + 1, 5))
  if (start > 2) out.push('…')
  for (let i = start; i <= end; i++) out.push(i)
  if (end < last - 1) out.push('…')
  out.push(last)
  return out
})
</script>

<template>
  <div class="pager">
    <span class="pager__info">
      共 {{ total }} 条<template v-if="total > 0">，当前 {{ from }}–{{ to }}</template>
    </span>
    <span class="grow" />
    <div v-if="totalPages >= 1" class="pager__ctrl">
      <button
        class="pager__btn"
        type="button"
        :disabled="page <= 1"
        aria-label="上一页"
        @click="emit('go', page - 1)"
      >
        <Icon name="chevronLeft" :size="13" />
      </button>
      <template v-for="(p, i) in pages" :key="`${p}-${i}`">
        <span v-if="p === '…'" class="pager__gap">…</span>
        <button
          v-else
          class="pager__btn"
          :class="{ 'is-on': p === page }"
          type="button"
          @click="emit('go', p)"
        >
          {{ p }}
        </button>
      </template>
      <button
        class="pager__btn"
        type="button"
        :disabled="page >= totalPages"
        aria-label="下一页"
        @click="emit('go', page + 1)"
      >
        <Icon name="chevronRight" :size="13" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.pager__gap {
  padding: 0 4px;
  color: var(--text-4);
  font-size: 13px;
}
</style>