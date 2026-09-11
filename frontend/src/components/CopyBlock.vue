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
    `border` is the theme's ink token, so the 3px border global.scss puts on every card merges
    into the fill and the block is shaped by its own colour — and the copy button is pale, which
    is the only way it stays visible on top.
  -->
  <v-card
    color="border"
    class="d-flex align-center ga-2 pa-2 pl-4 my-3"
  >
    <pre class="code flex-grow-1">{{ text }}</pre>
    <v-btn
      :icon="copied ? 'mdi-check' : 'mdi-content-copy'"
      :aria-label="copied ? $t('copied') : $t('copy')"
      :color="copied ? 'primary' : 'background'"
      size="small"
      flat
      rounded="lg"
      @click="copy"
    ></v-btn>
  </v-card>
</template>

<style scoped>
.code {
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
</style>
