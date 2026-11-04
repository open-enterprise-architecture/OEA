<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { updateGlobalReimportSetting, type GlobalReimportSetting } from '../../api/metamodel'
import { metamodel, reloadMetamodel } from '../../metamodel/store'
import { rejectionMessage } from './messages'

// Global re-import setting for organization mappings in SCR-030 (US-032, ADR-049; design D3 of
// scr-030-metamodel-configuration).
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const failure = ref<string>()
const notice = ref<string>()
const setting = computed({
  get: () => metamodel.globalReimportSetting,
  set: (value) => void change(value as GlobalReimportSetting),
})
const items = computed(() => (['keep', 'remove'] as const).map((value) => ({ label: t(`metamodel.reimport.${value}`), value })))

async function change(value: GlobalReimportSetting) {
  failure.value = undefined
  notice.value = undefined
  const outcome = await updateGlobalReimportSetting(value, props.fetchFn)
  if (outcome.ok) {
    notice.value = t('metamodel.reimport.saved')
  } else {
    failure.value = rejectionMessage(outcome.rejection)
  }
  await reloadMetamodel(props.fetchFn)
}
</script>

<template>
  <section class="flex max-w-2xl flex-col gap-3">
    <h2 class="font-semibold text-highlighted">{{ t('metamodel.reimport.title') }}</h2>
    <p class="text-sm text-muted">{{ t('metamodel.reimport.globalHint') }}</p>
    <UAlert v-if="failure" data-testid="settings-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="settings-notice" color="success" variant="subtle" :description="notice" />
    <USelect v-if="canWrite && setting" v-model="setting" data-testid="global-reimport" :aria-label="t('metamodel.reimport.title')" :items="items" class="w-56" />
    <p v-else-if="!canWrite" class="text-sm text-muted" data-testid="settings-read-only">{{ t('metamodel.reimport.needsWrite') }}</p>
  </section>
</template>
