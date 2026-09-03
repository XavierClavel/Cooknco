<template>
  <teleport to="body">
    <div class="toasts">
      <transition-group name="toast">
        <div v-for="t in toasts" :key="t.id" class="toast" :class="t.tone">
          <ui-icon :name="t.tone === 'ok' ? 'checkCircle' : t.tone === 'danger' ? 'alert' : 'info'" />
          <span>{{ t.text }}</span>
          <button class="btn icon" @click="dismiss(t.id)"><ui-icon name="x" :size="14" /></button>
        </div>
      </transition-group>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import {toasts, dismiss} from '../lib/toast'
import UiIcon from './UiIcon.vue'
</script>

<style scoped>
.toasts {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}
.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 260px;
  max-width: 420px;
  padding: 9px 8px 9px 12px;
  border-radius: var(--radius);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  box-shadow: var(--shadow-lg);
  font-size: 13px;
}
.toast span { flex: 1; }
.toast.ok     { border-color: var(--c-ok-border);     color: var(--c-ok); }
.toast.danger { border-color: var(--c-danger-border); color: var(--c-danger); }
.toast.info   { border-color: var(--c-info-border);   color: var(--c-info); }
.toast-enter-active, .toast-leave-active { transition: all .18s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(6px); }
</style>
