<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchMetatypes, type MetatypeDetail } from '../../api/metamodel'
import { LINEAGE_PARTS, fetchLineageModel, updateLineageModel, type LineageModel, type LineagePart } from '../../api/lineageModel'
import { label, labelKeys } from '../../i18n/labels'
import { rejectionMessage } from './messages'

// "Lineage model" of SCR-030 (US-064, US-065, REQ-062; design D3 of us-065-lineage-model-and-query): which entity type
// plays the data object and which connection types play data flow, carries-data, data input and data output. Everyone
// who reads the metamodel sees it; holders of metamodel:write change it with the version they read (a locked
// metamodel offers no change). A package import fills the parts that are still unset.
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
// USelect does not accept an empty value, so an unset part has its own value
const NONE = 'none'
const { t } = useI18n()

const model = ref<LineageModel>()
const metatypes = ref<MetatypeDetail[]>([])
const chosen = ref<Record<LineagePart, string>>(Object.fromEntries(LINEAGE_PARTS.map((part) => [part, NONE])) as Record<LineagePart, string>)
const failure = ref<string>()
const notice = ref<string>()
const invalid = ref<LineagePart[]>([])
const saving = ref(false)

const CONNECTION: readonly LineagePart[] = ['dataFlow', 'carriesData', 'dataInput', 'dataOutput']

function items(part: LineagePart) {
  const connection = CONNECTION.includes(part)
  return [
    { value: NONE, label: t('metamodel.lineage.unset') },
    ...metatypes.value
      .filter((type) => (type.isConnection === true) === connection)
      .map((type) => ({ value: type.id, label: label(labelKeys.metatype(type.id), type.name) })),
  ]
}

function adopt(next: LineageModel) {
  model.value = next
  for (const part of LINEAGE_PARTS) {
    chosen.value[part] = next[part].metatypeId ?? NONE
  }
}

async function load() {
  try {
    const [read, types] = await Promise.all([fetchLineageModel(props.fetchFn), fetchMetatypes(props.fetchFn)])
    metatypes.value = types
    adopt(read)
    failure.value = undefined
  } catch {
    failure.value = t('metamodel.lineage.loadFailed')
  }
}

const unchanged = computed(() => !model.value || LINEAGE_PARTS.every((part) => (model.value![part].metatypeId ?? NONE) === chosen.value[part]))

async function save() {
  if (!model.value) return
  failure.value = undefined
  notice.value = undefined
  invalid.value = []
  saving.value = true
  try {
    const body = Object.fromEntries(LINEAGE_PARTS.map((part) => [part, chosen.value[part] === NONE ? null : chosen.value[part]]))
    const outcome = await updateLineageModel(body, model.value.version, props.fetchFn)
    if (outcome.ok) {
      adopt(outcome.value)
      notice.value = t('metamodel.lineage.saved')
    } else if (outcome.rejection.problemType === 'stale-version') {
      await load()
      failure.value = t('metamodel.collision.reloaded')
    } else if (outcome.rejection.status === 422) {
      invalid.value = LINEAGE_PARTS.filter((part) => outcome.rejection.invalidFields.includes(part))
      failure.value = t('metamodel.lineage.invalid')
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
  <section class="flex flex-col gap-3" data-testid="lineage-model" aria-labelledby="lineage-model-title">
    <div class="flex flex-col gap-1">
      <h2 id="lineage-model-title" class="font-semibold text-highlighted">{{ t('metamodel.lineage.title') }}</h2>
      <p class="text-sm text-muted">{{ t('metamodel.lineage.hint') }}</p>
    </div>
    <UAlert v-if="failure" data-testid="lineage-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="lineage-notice" color="success" variant="subtle" :description="notice" />
    <div v-if="model" class="grid gap-3 sm:grid-cols-2">
      <UFormField
        v-for="part in LINEAGE_PARTS"
        :key="part"
        :label="t(`metamodel.lineage.part.${part}`)"
        :error="invalid.includes(part) ? t('metamodel.lineage.partInvalid') : undefined"
        :data-testid="`lineage-part-${part}`"
      >
        <USelect v-if="canWrite" v-model="chosen[part]" :items="items(part)" class="w-full" :data-testid="`lineage-select-${part}`" />
        <p v-else class="text-sm" :data-testid="`lineage-value-${part}`">{{ model[part].metatypeName ? label(labelKeys.metatype(model[part].metatypeId!), model[part].metatypeName!) : t('metamodel.lineage.unset') }}</p>
      </UFormField>
    </div>
    <div v-if="canWrite && model" class="flex justify-end">
      <UButton :loading="saving" :disabled="unchanged" :label="t('metamodel.lineage.save')" data-testid="lineage-save" @click="save" />
    </div>
  </section>
</template>
