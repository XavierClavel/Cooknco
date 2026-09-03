<template>
  <teleport to="body">
    <transition name="fade">
      <div v-if="modelValue" class="overlay" @mousedown.self="close">
        <div class="modal" :style="{maxWidth: width + 'px'}" role="dialog" aria-modal="true">
          <header>
            <h3>{{ title }}</h3>
            <button class="btn icon" :aria-label="$t('close')" @click="close">
              <ui-icon name="x" />
            </button>
          </header>
          <div class="body"><slot /></div>
          <footer v-if="$slots.actions">
            <slot name="actions" />
          </footer>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted} from 'vue'
import UiIcon from './UiIcon.vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  width?: number
}>(), {width: 520})

const emit = defineEmits<{(e: 'update:modelValue', v: boolean): void}>()
const close = () => emit('update:modelValue', false)

const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && props.modelValue) close() }
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(16, 24, 40, .45);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 8vh 16px 16px;
  overflow-y: auto;
}
.modal {
  width: 100%;
  background: var(--c-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 14px 13px 18px;
  border-bottom: 1px solid var(--c-border);
}
header h3 { flex: 1; font-size: 14.5px; }
.body { padding: 18px; }
footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid var(--c-border);
  background: var(--c-surface-alt);
}
.fade-enter-active, .fade-leave-active { transition: opacity .13s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
