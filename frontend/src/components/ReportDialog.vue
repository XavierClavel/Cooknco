<template>
  <v-dialog v-model="open" max-width="480">
    <template #activator="{props: activatorProps}">
      <slot name="activator" :props="activatorProps">
        <v-btn
          v-bind="activatorProps"
          :icon="ICON_REPORT"
          :title="`${$t('report')}`"
          variant="text"
          size="small"
        ></v-btn>
      </slot>
    </template>

    <v-card>
      <v-card-title class="text-h6">{{ $t("report_title") }}</v-card-title>
      <v-card-text>
        <div class="text-body-2 mb-3">{{ $t("report_description") }}</div>
        <v-select
          :label="`${$t('admin_reason')}`"
          :items="reasonOptions"
          item-title="label"
          item-value="value"
          v-model="reason"
        ></v-select>
        <v-textarea
          v-model="comment"
          :label="`${$t('report_comment')}`"
          :placeholder="`${$t('report_comment_placeholder')}`"
          rows="3"
          counter="1023"
          maxlength="1023"
          class="mt-3"
        ></v-textarea>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn :disabled="submitting" @click="open = false">{{ $t("cancel") }}</v-btn>
        <v-btn
          color="primary"
          variant="elevated"
          :loading="submitting"
          @click="submit"
        >{{ $t("report_submit") }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <v-snackbar v-model="snackbar" :timeout="4000">{{ snackbarText }}</v-snackbar>
</template>

<script lang="ts" setup>
import {useI18n} from "vue-i18n";
import {reportContent, reportReasons} from "@/scripts/reports";
import {ICON_REPORT} from "@/scripts/icons";

const props = defineProps<{
  /** RECIPE, USER or COOKBOOK. */
  targetType: string
  targetId: number
}>()

const {t} = useI18n()

const open = ref(false)
const reason = ref('INAPPROPRIATE_CONTENT')
const comment = ref('')
const submitting = ref(false)
const snackbar = ref(false)
const snackbarText = ref('')

const reasonOptions = computed(() =>
  reportReasons.map(value => ({label: t(`report_reason_${value.toLowerCase()}`), value})),
)

const submit = () => {
  submitting.value = true
  reportContent(props.targetType, props.targetId, reason.value, comment.value)
    .then(() => {
      open.value = false
      comment.value = ''
      snackbarText.value = t('report_sent')
      snackbar.value = true
    })
    .catch(error => {
      // The backend rejects self-reports and repeat reports with a translated cause key
      const cause = error?.response?.data
      snackbarText.value = cause ? t(cause) : t('report_failed')
      snackbar.value = true
    })
    .finally(() => { submitting.value = false })
}
</script>
