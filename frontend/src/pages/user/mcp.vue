<template>
  <v-container class="mx-auto" align="center">
    <v-card
      class="pa-5 ma-5"
      max-width="1000px"
      min-width="300px"
    >
      <v-card-title>
        {{ $t("mcp_title") }}
      </v-card-title>

      <v-card-text class="text-left">
        {{ $t("mcp_intro") }}
      </v-card-text>

      <v-card-text class="text-left pb-0">
        <ol class="steps">
          <li>{{ $t("mcp_step_add") }}</li>
          <li>{{ $t("mcp_step_authorize") }}</li>
          <li>{{ $t("mcp_step_done") }}</li>
        </ol>
      </v-card-text>

      <v-card-title class="text-h6 text-sm-h6 text-left">
        {{ $t("mcp_endpoint") }}
      </v-card-title>
      <v-card-text class="text-left pt-0">
        <copy-block :text="mcpUrl"></copy-block>
        <div class="text-body-2">{{ $t("mcp_endpoint_hint") }}</div>
      </v-card-text>

      <v-card-title class="text-h6 text-sm-h6 text-left">
        {{ $t("mcp_clients") }}
      </v-card-title>
      <!--
        One button per client rather than all five laid out at once: a person setting this up
        uses exactly one of them, and five open code blocks make the page read like a reference
        instead of an instruction. Coral for the chosen one — with the app's ink border, which
        is what lets it sit on the green surface.
      -->
      <v-card-text class="text-left pt-0">
        <div class="d-flex flex-wrap ga-3 mb-4">
          <v-btn
            v-for="client in mcpClients"
            :key="client.key"
            :color="client.key === selected ? 'primary' : 'background'"
            :aria-pressed="client.key === selected"
            height="48"
            rounded="lg"
            flat
            class="text-none text-body-1"
            @click="selected = client.key"
          >
            <template v-slot:prepend>
              <v-img
                v-if="client.logo"
                :src="client.logo"
                width="20"
                height="20"
              ></v-img>
              <v-icon v-else :icon="client.icon"></v-icon>
            </template>
            {{ $t(`mcp_client_${client.key}`) }}
          </v-btn>
        </div>

        <v-card color="background" class="pa-4">
          <div class="text-body-2">{{ $t(`mcp_client_${selectedClient.key}_hint`) }}</div>
          <copy-block :text="selectedClient.snippet"></copy-block>
        </v-card>
      </v-card-text>

      <v-card-title class="text-h6 text-sm-h6 text-left">
        {{ $t("mcp_permissions") }}
      </v-card-title>
      <v-card-text class="text-left pt-0">
        <!--
          The same three lines the consent screen lists, in the same order: a person reading
          this page and a person reading `backend/src/main/resources/oauth/consent.html` must
          not come away with two different ideas of what they approved. Change both together.
        -->
        <ul class="grants">
          <li>{{ $t("mcp_grant_read") }}</li>
          <li>{{ $t("mcp_grant_write") }}</li>
          <li>{{ $t("mcp_grant_collect") }}</li>
        </ul>
        <div class="text-body-2 mt-3">{{ $t("mcp_permissions_note") }}</div>
        <div class="text-body-2 mt-2">{{ $t("mcp_expiry_note") }}</div>
      </v-card-text>

      <v-container>
        <v-row class="d-flex align-center justify-center mb-2" dense>
          <action-button
            icon="mdi-cog"
            :text="`${$t('settings')}`"
            :action="toSettings"
          ></action-button>
        </v-row>
      </v-container>
    </v-card>
  </v-container>
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
/*
 * Numbered and bulleted lists, marked with the app's palette rather than the browser's glyphs.
 *
 * Each row is a flex line so the marker centres against its text whatever the two measure:
 * the badge is taller than a line box of card text, so anchoring it to the top of the line
 * (which is what an absolutely positioned marker does) leaves it sitting low.
 */
.steps,
.grants {
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: step;
}

.steps li,
.grants li {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  margin-bottom: 8px;
}

.steps li {
  counter-increment: step;
}

.steps li::before {
  content: counter(step);
  flex: 0 0 auto;
  width: 1.5rem;
  height: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  font-weight: 700;
  color: white;
  background: #ff6f59;
  border: 2px solid #0d1821;
  border-radius: 6px;
}

/*
 * Solid, and the only marker here without the app's ink outline: outlined, a small square in a
 * list of what an assistant may do reads as an unticked checkbox, and these are statements
 * rather than choices. Ink rather than the coral accent, because without a border there is
 * nothing between it and the green card. The consent screen marks the same three lines the
 * same way.
 */
.grants li::before {
  content: '';
  flex: 0 0 auto;
  width: 0.6rem;
  height: 0.6rem;
  margin: 0 0.45rem;
  background: #0d1821;
  border-radius: 2px;
}
</style>
