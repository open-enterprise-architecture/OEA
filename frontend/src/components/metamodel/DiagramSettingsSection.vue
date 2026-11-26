<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchDiagramSettings, updateDiagramSettings, type DiagramSettings } from '../../api/metamodel'
import { rejectionMessage } from './messages'

// "Diagram settings" above the viewpoint list of SCR-031 (US-074, US-135; design D4 of us-074-diagram-nesting):
// the global setting "Ask 'New parent?' when moving shapes", which a viewpoint may override. Holders of
// viewpoint:manage change it with the version they read; a change made meanwhile is read again.
const props = defineProps<{ canManage: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const settings = ref<DiagramSettings>()
const failure = ref<string>()
const notice = ref<string>()
const saving = ref(false)

async function load() {
  try {
    settings.value = await fetchDiagramSettings(props.fetchFn)
    failure.value = undefined
  } catch {
    failure.value = t('metamodel.diagramSettings.loadFailed')
  }
}

async function change(value: boolean | 'indeterminate') {
  if (!settings.value || !props.canManage || typeof value !== 'boolean') {
    return
  }
  failure.value = undefined
  notice.value = undefined
  saving.value = true
  try {
    const outcome = await updateDiagramSettings(value, settings.value.version, props.fetchFn)
    if (outcome.ok) {
      settings.value = outcome.value
      notice.value = t('metamodel.diagramSettings.saved')
    } else if (outcome.rejection.problemType === 'stale-version') {
      await load()
      failure.value = t('metamodel.collision.reloaded')
    } else {
      failure.value = rejectionMessage(outcome.rejection)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="flex flex-col gap-2" data-testid="diagram-settings" aria-labelledby="diagram-settings-title">
    <h2 id="diagram-settings-title" class="font-semibold text-highlighted">{{ t('metamodel.diagramSettings.title') }}</h2>
    <UAlert v-if="failure" data-testid="diagram-settings-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="diagram-settings-notice" color="success" variant="subtle" :description="notice" />
    <USwitch
      v-if="settings && canManage"
      data-testid="diagram-settings-ask"
      :model-value="settings.askNewParent"
      :disabled="saving"
      :label="t('metamodel.diagramSettings.askNewParent')"
      :description="t('metamodel.diagramSettings.askNewParentHint')"
      @update:model-value="change"
    />
    <p v-else-if="settings" class="text-sm text-default" data-testid="diagram-settings-value">
      {{ t('metamodel.diagramSettings.askNewParent') }}: {{ settings.askNewParent ? t('metamodel.diagramSettings.on') : t('metamodel.diagramSettings.off') }}
    </p>
  </section>
</template>
