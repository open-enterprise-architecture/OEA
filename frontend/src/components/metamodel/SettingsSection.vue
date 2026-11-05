<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { updateConfiguration, updateGlobalReimportSetting, type GlobalReimportSetting } from '../../api/metamodel'
import { metamodel, metamodelLocked, reloadMetamodel } from '../../metamodel/store'
import { rejectionMessage } from './messages'

// Settings of SCR-030: the lock mode of the instance metamodel (US-035; design D6 of
// us-037-solution-extensions-and-lock-mode) and the global re-import setting for organization
// mappings (US-032, ADR-049; design D3 of scr-030-metamodel-configuration). Both stay
// changeable while the metamodel is locked.
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const failure = ref<string>()
const notice = ref<string>()
const lockFailure = ref<string>()
const lockNotice = ref<string>()
const saving = ref(false)
const setting = computed({
  get: () => metamodel.globalReimportSetting,
  set: (value) => void change(value as GlobalReimportSetting),
})
const items = computed(() => (['keep', 'remove'] as const).map((value) => ({ label: t(`metamodel.reimport.${value}`), value })))
const locked = computed({
  get: () => metamodelLocked(),
  set: (value) => void lock(value),
})

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

/** Switches the edit mode with the version read (If-Match); a collision shows the current state. */
async function lock(value: boolean) {
  const configuration = metamodel.instanceConfiguration
  if (!configuration) {
    return
  }
  lockFailure.value = undefined
  lockNotice.value = undefined
  saving.value = true
  const outcome = await updateConfiguration(
    configuration.id,
    { name: configuration.name, editMode: value ? 'import-only' : 'gui-and-import' },
    configuration.version,
    props.fetchFn,
  )
  if (outcome.ok) {
    lockNotice.value = t(value ? 'metamodel.lock.locked' : 'metamodel.lock.unlocked')
  } else if (outcome.rejection.problemType === 'stale-version') {
    lockFailure.value = t('metamodel.collision.reloaded')
  } else {
    lockFailure.value = rejectionMessage(outcome.rejection)
  }
  await reloadMetamodel(props.fetchFn)
  saving.value = false
}
</script>

<template>
  <div class="flex max-w-2xl flex-col gap-8">
    <section v-if="canWrite && metamodel.instanceConfiguration" class="flex flex-col gap-3">
      <h2 class="font-semibold text-highlighted">{{ t('metamodel.lock.title') }}</h2>
      <UAlert v-if="lockFailure" data-testid="lock-failure" color="error" variant="subtle" :description="lockFailure" />
      <UAlert v-if="lockNotice" data-testid="lock-notice" color="success" variant="subtle" :description="lockNotice" />
      <USwitch
        v-model="locked"
        data-testid="metamodel-lock"
        :disabled="saving"
        :label="t('metamodel.lock.label')"
        :description="t('metamodel.lock.explanation')"
      />
    </section>

    <section class="flex flex-col gap-3">
      <h2 class="font-semibold text-highlighted">{{ t('metamodel.reimport.title') }}</h2>
      <p class="text-sm text-muted">{{ t('metamodel.reimport.globalHint') }}</p>
      <UAlert v-if="failure" data-testid="settings-failure" color="error" variant="subtle" :description="failure" />
      <UAlert v-if="notice" data-testid="settings-notice" color="success" variant="subtle" :description="notice" />
      <USelect v-if="canWrite && setting" v-model="setting" data-testid="global-reimport" :aria-label="t('metamodel.reimport.title')" :items="items" class="w-56" />
      <p v-else-if="!canWrite" class="text-sm text-muted" data-testid="settings-read-only">{{ t('metamodel.reimport.needsWrite') }}</p>
    </section>
  </div>
</template>
