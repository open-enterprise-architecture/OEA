<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchMetatype, type PropertyMapping } from '../../api/metamodel'
import { rateLimitMessage, type Rejection } from '../../api/problem'
import {
  createSolutionDelta,
  fetchSolutionLandscapeEntity,
  updateSolutionDelta,
  type SolutionDelta,
  type SolutionDeltaValue,
  type SolutionLandscapeEntity,
} from '../../api/solutions'
import PropertyInput from './PropertyInput.vue'

// The dialog "Modify entity" of SCR-061 (US-041 AC1, AC2; design D6 of us-041-modified-delta):
// per field the current value of the catalog, read-only, and an input for the new value. The
// server records the current values as the before values. Only changed fields are sent; a cleared
// property input removes the value. Opened with a delta, it edits that delta (updateSolutionDelta)
// and starts from its after values.
const props = defineProps<{ solutionId: string; entityId: number; delta?: SolutionDelta; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [delta: SolutionDelta]; completed: []; cancel: [] }>()
const { t } = useI18n()

const NAME_MAX = 255
const DESCRIPTION_MAX = 2000

const entity = ref<SolutionLandscapeEntity>()
const mappings = ref<PropertyMapping[]>([])
const name = ref<string>()
const description = ref<string>()
// undefined: not part of the change; a number field gives a number at run time
const values = ref<Record<string, string | undefined>>({})
const fieldErrors = ref<Record<string, string>>({})
const failure = ref<string>()
const saving = ref(false)
const loaded = ref(false)

const canSave = computed(() => loaded.value && !saving.value)

function display(value: SolutionDeltaValue | null | undefined): string | undefined {
  if (!value) return undefined
  const shown = value.valueRef?.name ?? value.valueText ?? value.valueEnum ?? value.valueDate ?? value.valueInt ?? value.valueBool
  return shown === undefined || shown === null ? undefined : String(shown)
}

/** The value as the input holds it: text, a reference as its entity ID. */
function asInput(value: SolutionDeltaValue | null | undefined): string {
  if (!value) return ''
  if (value.valueRef) return String(value.valueRef.id)
  return display(value) ?? ''
}

function current(propertyDefId: string): string {
  const value = entity.value?.properties.find((candidate) => candidate.propertyDefId === propertyDefId)
  if (!value) return t('solutions.deltas.modify.noValue')
  if (value.redacted) return t('solutions.deltas.modify.hidden')
  return display(value) ?? t('solutions.deltas.modify.noValue')
}

onMounted(async () => {
  try {
    entity.value = await fetchSolutionLandscapeEntity(props.solutionId, props.entityId, props.fetchFn)
    const detail = await fetchMetatype(entity.value.metatypeId, props.fetchFn)
    mappings.value = (detail.propertyMappings ?? [])
      .filter((mapping) => mapping.state === undefined || mapping.state === 'active')
      .sort((a, b) => a.sortOrder - b.sortOrder)
    for (const change of props.delta?.changes ?? []) {
      if (change.field === 'name') name.value = display(change.after) ?? ''
      else if (change.field === 'description') description.value = display(change.after) ?? ''
      else if (change.propertyDefId && !change.redacted) values.value[change.propertyDefId] = asInput(change.after)
    }
    loaded.value = true
  } catch {
    failure.value = t('solutions.deltas.modify.loadFailed')
  }
})

function written(): { propertyDefId: string; value: string }[] {
  return Object.entries(values.value).flatMap(([propertyDefId, value]) => {
    const raw: unknown = value
    return raw === undefined ? [] : [{ propertyDefId, value: raw === null ? '' : String(raw) }]
  })
}

function showRejection(rejection: Rejection, sent: { propertyDefId: string }[]) {
  if (rejection.status === 409) {
    emit('completed')
    failure.value = t('solutions.deltas.completed')
    return
  }
  if (rejection.status === 403) {
    failure.value = t('solutions.deltas.restricted')
    return
  }
  if (rejection.status === 422) {
    const errors = rejection.errors?.length
      ? rejection.errors
      : rejection.invalidFields.map((field) => ({ field, message: rejection.detail ?? t('solutions.deltas.invalid') }))
    const general: string[] = []
    const byField: Record<string, string> = {}
    for (const error of errors) {
      const property = /^properties\[(\d+)\]/.exec(error.field)
      const id = property ? sent[Number(property[1])]?.propertyDefId : undefined
      if (id) byField[id] = error.message
      else if (error.field === 'name' || error.field === 'description') byField[error.field] = error.message
      else general.push(error.message)
    }
    fieldErrors.value = byField
    failure.value = general.length > 0 ? general.join(' ') : undefined
    return
  }
  failure.value = rateLimitMessage(rejection) ?? t('solutions.deltas.createFailed')
}

async function save() {
  failure.value = undefined
  fieldErrors.value = {}
  if (!canSave.value) return
  saving.value = true
  const properties = written()
  const changes = {
    ...(name.value !== undefined && name.value !== entity.value?.name ? { name: name.value } : {}),
    ...(description.value !== undefined && description.value !== entity.value?.description ? { description: description.value } : {}),
    properties,
  }
  try {
    const outcome = props.delta
      ? await updateSolutionDelta(props.solutionId, props.delta.id, changes, props.fetchFn)
      : await createSolutionDelta(props.solutionId, { deltaType: 'modified', entityId: props.entityId, ...changes }, props.fetchFn)
    if (outcome.ok) {
      emit('saved', outcome.value)
      return
    }
    showRejection(outcome.rejection, properties)
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="modify-dialog" @submit.prevent="save">
    <p v-if="entity" class="text-sm text-muted">{{ t('solutions.deltas.modify.hint') }}</p>

    <template v-if="entity">
      <UFormField :label="t('solutions.deltas.name')" :error="fieldErrors.name" data-testid="modify-field-name">
        <p class="text-sm text-muted" data-testid="modify-current">{{ t('solutions.deltas.modify.current', { value: entity.name }) }}</p>
        <UInput v-model="name" data-testid="modify-name" class="w-full" :maxlength="NAME_MAX" :placeholder="entity.name" />
      </UFormField>
      <UFormField :label="t('solutions.deltas.description')" :error="fieldErrors.description" data-testid="modify-field-description">
        <p class="text-sm text-muted" data-testid="modify-current">
          {{ t('solutions.deltas.modify.current', { value: entity.description || t('solutions.deltas.modify.noValue') }) }}
        </p>
        <UTextarea v-model="description" data-testid="modify-description" class="w-full" :rows="2" :maxlength="DESCRIPTION_MAX" autoresize />
      </UFormField>
      <UFormField
        v-for="mapping in mappings"
        :key="mapping.propertyDefinition.id"
        :label="mapping.propertyDefinition.label ?? mapping.propertyDefinition.name"
        :required="mapping.necessity === 'mandatory'"
        :error="fieldErrors[mapping.propertyDefinition.id]"
        :data-testid="`modify-property-${mapping.propertyDefinition.name}`"
      >
        <p class="text-sm text-muted" data-testid="modify-current">
          {{ t('solutions.deltas.modify.current', { value: current(mapping.propertyDefinition.id) }) }}
        </p>
        <PropertyInput
          v-model="values[mapping.propertyDefinition.id]"
          :mapping="mapping"
          :solution-id="props.solutionId"
          :fetch-fn="props.fetchFn"
        />
      </UFormField>
    </template>

    <UAlert v-if="failure" data-testid="modify-failure" color="error" variant="subtle" :description="failure" />

    <div class="flex justify-end gap-2">
      <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="modify-save" :disabled="!canSave" :loading="saving" :label="t('solutions.deltas.modify.save')" />
    </div>
  </form>
</template>
