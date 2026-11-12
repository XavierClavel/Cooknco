<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps({
  text: {
    type: String,
    required: true,
  },
})

// Shown on the button itself rather than through the app's snackbar: this page carries one
// block per client, and a global message would not say which one was taken.
const copied = ref(false)
let reset: ReturnType<typeof setTimeout> | null = null

async function copy () {
  try {
    await navigator.clipboard.writeText(props.text)
  } catch (e) {
    // No clipboard (an insecure origin, or a browser that refused): the text is selectable,
    // so leave it to the user rather than claiming a copy that did not happen.
    console.log(e)
    return
  }
  copied.value = true
  if (reset) clearTimeout(reset)
  reset = setTimeout(() => { copied.value = false }, 2000)
}

onUnmounted(() => {
  if (reset) clearTimeout(reset)
})
</script>

<template>
  <!--
    Ink, for the one thing on the page that is a terminal or a config file rather than prose.
    The copy button is outlined in white, which is the only way it stays visible on top.
  -->
  <div class="copy-block">
    <pre class="copy-block__code">{{ text }}</pre>
    <button
      type="button"
      class="copy-block__btn"
      :class="{'is-copied': copied}"
      @click="copy"
    >
      <v-icon v-if="copied" size="16">mdi-check</v-icon>
      {{ copied ? $t('copied') : $t('copy') }}
    </button>
  </div>
</template>

<style scoped>
.copy-block {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 8px 14px;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius-sm);
  background: var(--ink);
}

.copy-block__code {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: #f0f4ef;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85rem;
  line-height: 1.5;
  /* Long URLs and the json blocks both have to survive a phone-width card */
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
}

.copy-block__btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 34px;
  padding: 0 12px;
  border: 1.5px solid var(--white);
  border-radius: 8px;
  background: transparent;
  color: var(--white);
  font-family: var(--font);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.copy-block__btn:hover {
  background: rgba(255, 255, 255, 0.12);
}

.copy-block__btn:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
}

.copy-block__btn.is-copied {
  background: var(--coral);
  border-color: var(--coral);
}
</style>
