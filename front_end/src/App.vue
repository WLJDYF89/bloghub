<script setup>
import { RouterView } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { useToast } from '@/composables/toast'

const { items } = useToast()
</script>

<template>
  <RouterView />

  <div class="toasts" role="status" aria-live="polite">
    <div v-for="t in items" :key="t.id" class="toast" :class="{ 'toast--error': t.type === 'error' }">
      <Icon :name="t.type === 'error' ? 'alert' : 'check'" :size="14" />
      <span>{{ t.message }}</span>
    </div>
  </div>
</template>