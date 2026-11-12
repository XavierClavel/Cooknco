<!--
  The one renderer for the privacy policy and the deletion notice.

  Both are reachable signed out - the Play Console points at them and a reviewer opens them
  with no account - so nothing here reads the session or calls the API. The copy itself is
  in `@/locales/legal`, per language, and carries no markup: a document that has to exist
  twice is easier to keep honest when neither copy can drift in layout as well as in wording.
-->
<template>
  <article class="legal sticker">
    <h1 class="legal__title">{{ doc.title }}</h1>
    <p class="legal__updated">{{ doc.updated }}</p>

    <p v-for="(paragraph, index) in doc.intro" :key="`intro-${index}`">
      {{ paragraph }}
    </p>

    <section v-for="(section, index) in doc.sections" :key="`section-${index}`" class="legal__section">
      <h2 class="legal__heading">{{ section.heading }}</h2>

      <p v-for="(paragraph, i) in section.body ?? []" :key="`body-${index}-${i}`">
        {{ paragraph }}
      </p>

      <ul v-if="section.bullets?.length" class="legal__list">
        <li v-for="(bullet, i) in section.bullets" :key="`bullet-${index}-${i}`">
          <strong v-if="bullet.term">{{ bullet.term }}&nbsp;—&nbsp;</strong>{{ bullet.text }}
        </li>
      </ul>

      <p v-for="(paragraph, i) in section.after ?? []" :key="`after-${index}-${i}`">
        {{ paragraph }}
      </p>
    </section>
  </article>
</template>

<script lang="ts" setup>
import type { LegalDocument } from '@/scripts/legal'

defineProps<{ doc: LegalDocument }>()
</script>

<style scoped>
.legal {
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
  padding: 28px 32px 32px;
  text-align: left;
  font-size: 14px;
  line-height: 1.55;
}

.legal p {
  margin: 0 0 8px;
}

.legal__title {
  margin: 0;
  font-size: clamp(24px, 3.5vw, 30px);
  font-weight: 700;
  line-height: 1.15;
}

.legal__updated {
  margin: 4px 0 16px !important;
  font-size: 12px;
  color: var(--muted);
}

.legal__section {
  margin-top: 20px;
}

.legal__heading {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
}

.legal__list {
  margin: 0 0 8px;
  padding-left: 1.25rem;
}

.legal__list li {
  margin-bottom: 6px;
}

@media (max-width: 600px) {
  .legal { padding: 18px 16px 20px; }
}
</style>
