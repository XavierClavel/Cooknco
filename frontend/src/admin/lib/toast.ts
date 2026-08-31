import {ref} from 'vue'

export type Toast = {id: number; text: string; tone: 'ok' | 'danger' | 'info'}

export const toasts = ref<Toast[]>([])
let seq = 0

function push(text: string, tone: Toast['tone']) {
  const id = ++seq
  toasts.value.push({id, text, tone})
  setTimeout(() => dismiss(id), 4500)
}

export function dismiss(id: number) {
  toasts.value = toasts.value.filter(t => t.id !== id)
}

export const notifyOk = (text: string) => push(text, 'ok')
export const notifyError = (text: string) => push(text, 'danger')
export const notifyInfo = (text: string) => push(text, 'info')
