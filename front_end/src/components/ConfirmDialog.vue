<script setup>
import { watch } from 'vue'
import Icon from '@/components/Icon.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  body: { type: String, default: '' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  danger: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['confirm', 'cancel'])

function onKey(e) {
  if (e.key === 'Escape' && !props.busy) emit('cancel')
}

watch(
  () => props.open,
  (v) => {
    if (v) document.addEventListener('keydown', onKey)
    else document.removeEventListener('keydown', onKey)
  },
)
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" @click.self="!busy && emit('cancel')">
      <div class="dialog" role="dialog" aria-modal="true">
        <h2 class="dialog__head row gap-2">
          <Icon v-if="danger" name="alert" :size="16" style="color: var(--danger)" />
          {{ title }}
        </h2>
        <p class="dialog__body">{{ body }}</p>
        <div class="dialog__foot">
          <button class="btn btn--ghost" type="button" :disabled="busy" @click="emit('cancel')">
            {{ cancelText }}
          </button>
          <button
            class="btn"
            :class="danger ? 'btn--danger-solid' : 'btn--primary'"
            type="button"
            :disabled="busy"
            @click="emit('confirm')"
          >
            {{ busy ? '处理中…' : confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>