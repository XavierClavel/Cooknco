<template>
  <section class="sticker sk-pad-lg sk-narrow mcp">
    <h1 class="sk-title">{{ $t("mcp_title") }}</h1>

    <p>{{ $t("mcp_intro") }}</p>

    <ol class="mcp__steps">
      <li>{{ $t("mcp_step_add") }}</li>
      <li>{{ $t("mcp_step_authorize") }}</li>
      <li>{{ $t("mcp_step_done") }}</li>
    </ol>

    <h2 class="sk-h3 mcp__heading">{{ $t("mcp_endpoint") }}</h2>
    <copy-block :text="mcpUrl"></copy-block>
    <p class="sk-hint">{{ $t("mcp_endpoint_hint") }}</p>

    <h2 class="sk-h3 mcp__heading">{{ $t("mcp_clients") }}</h2>
    <!--
      One button per client rather than all five laid out at once: a person setting this up
      uses exactly one of them, and five open code blocks make the page read like a reference
      instead of an instruction. Coral for the chosen one.
    -->
    <div class="sk-chip-row">
      <button
        v-for="client in mcpClients"
        :key="client.key"
        type="button"
        class="sk-chip sk-chip--coral"
        :class="{'is-active': client.key === selected}"
        :aria-pressed="client.key === selected"
        @click="selected = client.key"
      >
        <img v-if="client.logo" :src="client.logo" alt="" class="mcp__logo" />
        <v-icon v-else :icon="client.icon"></v-icon>
        {{ $t(`mcp_client_${client.key}`) }}
      </button>
    </div>

    <div class="mcp__client">
      <p>{{ $t(`mcp_client_${selectedClient.key}_hint`) }}</p>
      <copy-block :text="selectedClient.snippet"></copy-block>
    </div>

    <h2 class="sk-h3 mcp__heading">{{ $t("mcp_permissions") }}</h2>
    <!--
      The same three lines the consent screen lists, in the same order: a person reading
      this page and a person reading `backend/src/main/resources/oauth/consent.html` must
      not come away with two different ideas of what they approved. Change both together.
    -->
    <ul class="mcp__grants">
      <li>{{ $t("mcp_grant_read") }}</li>
      <li>{{ $t("mcp_grant_write") }}</li>
      <li>{{ $t("mcp_grant_collect") }}</li>
    </ul>
    <p class="mcp__note">{{ $t("mcp_permissions_note") }}</p>
    <p class="mcp__note">{{ $t("mcp_expiry_note") }}</p>

    <div class="mcp__footer">
      <button type="button" class="sk-btn" @click="toSettings">{{ $t('settings') }}</button>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { mcpClients, mcpUrl } from "@/scripts/mcp";
import { toSettings } from "@/scripts/common";

// Opens on the first client rather than on nothing: an empty panel below the row would read as
// a page still loading.
const selected = ref(mcpClients[0].key)
const selectedClient = computed(() => mcpClients.find((it) => it.key === selected.value) ?? mcpClients[0])
</script>

<style scoped>
.mcp {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 14px;
  line-height: 1.5;
}

.mcp p {
  margin: 0;
}

.mcp__heading {
  margin-top: 10px;
}

.mcp__steps,
.mcp__grants {
  margin: 0;
  padding-left: 1.4rem;
}

.mcp__steps li,
.mcp__grants li {
  margin-bottom: 4px;
}

.mcp__logo {
  width: 16px;
  height: 16px;
  object-fit: contain;
}

.mcp__client {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: var(--line) solid var(--ink);
  border-radius: var(--radius-sm);
  background: var(--white);
}

.mcp__note {
  font-size: 12px;
}

.mcp__footer {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
