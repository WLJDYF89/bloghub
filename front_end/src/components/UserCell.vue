<script setup>
import { computed } from 'vue'
import { initial } from '@/utils/format'

const props = defineProps({
  name: { type: String, default: '' },
  sub: { type: String, default: '' },
  admin: { type: Boolean, default: false },
})

const label = computed(() => initial(props.name))
</script>

<template>
  <span class="ucell">
    <span class="avatar avatar--sm" :class="admin ? 'avatar--accent' : ''">{{ label }}</span>
    <span class="ucell__meta">
      <span class="ucell__name">{{ name || '—' }}</span>
      <span v-if="sub" class="ucell__sub">{{ sub }}</span>
    </span>
  </span>
</template>

<style scoped>
.ucell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.ucell__meta {
  min-width: 0;
}
.ucell__name {
  display: block;
  font-size: 16px;
  color: var(--text);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ucell__sub {
  display: block;
  font-size: 13.5px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>