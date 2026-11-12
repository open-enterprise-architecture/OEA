<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchMetatype, fetchMetatypes, type PropertyMapping } from '../../api/metamodel'
import { rateLimitMessage, type Rejection } from '../../api/problem'
import { createSolutionDelta, type SolutionDelta } from '../../api/solutions'
import { label, labelKeys } from '../../i18n/labels'
import PropertyInput from './PropertyInput.vue'

// The dialog "New entity" of SCR-061 (US-040 AC1, AC4; design D9 of us-040-entity-deltas): an
// element metatype, name, description and one field per active property mapping of the
// metatype. The backend checks the delta like the write of a new entity; a rejection keeps the
// dialog open and shows each message at the field it names.
const props = defineProps<{ solutionId: string; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ created: [delta: SolutionDelta]; completed: []; cancel: [] }>()
const { t } = useI18n()

const NAME_MAX = 255
const DESCRIPTION_MAX = 2000

const metatypes = ref<{ label: string; value: string }[]>([])
const metatype = ref<string>()
const mappings = ref<PropertyMapping[]>([])
const name = ref('')
const description = ref('')
// a number field gives a number at run time, every other field text; the contract sends text
const values = ref<Record<string, string>>({})
const fieldErrors = ref<Record<string, string>>({})
const failure = ref<string>()
const saving = ref(false)

const nameValid = computed(() => name.value.trim().length > 0)
const canSave = computed(() => metatype.value !== undefined && nameValid.value && !saving.value)

function definitionLabel(mapping: PropertyMapping): string {
  return mapping.propertyDefinition.label ?? mapping.propertyDefinition.name
}

watch(metatype, async (id) => {
  mappings.value = []
  values.value = {}
  fieldErrors.value = {}
  if (!id) return
  try {
    const detail = await fetchMetatype(id, props.fetchFn)
    mappings.value = (detail.propertyMappings ?? [])
      .filter((mapping) => mapping.state === undefined || mapping.state === 'active')
      .sort((a, b) => a.sortOrder - b.sortOrder)
  } catch {
    failure.value = t('solutions.deltas.propertiesFailed')
  }
})

onMounted(async () => {
  try {
    metatypes.value = (await fetchMetatypes(props.fetchFn))
      .filter((candidate) => !candidate.isConnection)
      .map((candidate) => ({ label: label(labelKeys.metatype(candidate.id), candidate.name), value: candidate.id }))
      .sort((a, b) => a.label.localeCompare(b.label))
  } catch {
    failure.value = t('solutions.deltas.metatypesFailed')
  }
})

function written(): { propertyDefId: string; value: string }[] {
  return mappings.value
    .map((mapping) => {
      const value: unknown = values.value[mapping.propertyDefinition.id]
      return { propertyDefId: mapping.propertyDefinition.id, value: value === null || value === undefined ? '' : String(value) }
    })
    .filter((entry) => entry.value.length > 0)
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
      if (property) {
        const id = sent[Number(property[1])]?.propertyDefId
        if (id) byField[id] = error.message
        else general.push(error.message)
      } else if (['name', 'description', 'metatypeId'].includes(error.field)) {
        byField[error.field] = error.message
      } else {
        general.push(error.message)
      }
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
  if (!canSave.value || !metatype.value) return
  saving.value = true
  const properties = written()
  try {
    const outcome = await createSolutionDelta(
      props.solutionId,
      {
        deltaType: 'new',
        metatypeId: metatype.value,
        name: name.value,
        ...(description.value.length > 0 ? { description: description.value } : {}),
        properties,
      },
      props.fetchFn,
    )
    if (outcome.ok) {
      emit('created', outcome.value)
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
  <form class="flex flex-col gap-4" novalidate data-testid="delta-dialog" @submit.prevent="save">
    <UFormField :label="t('solutions.deltas.metatype')" :error="fieldErrors.metatypeId" required>
      <USelect
        v-model="metatype"
        data-testid="delta-metatype"
        :items="metatypes"
        class="w-full"
      />
    </UFormField>
    <UFormField :label="t('solutions.deltas.name')" :error="fieldErrors.name" required>
      <UInput v-model="name" data-testid="delta-name" class="w-full" :maxlength="NAME_MAX" />
    </UFormField>
    <UFormField :label="t('solutions.deltas.description')" :error="fieldErrors.description">
      <UTextarea v-model="description" data-testid="delta-description" class="w-full" :rows="3" :maxlength="DESCRIPTION_MAX" autoresize />
    </UFormField>

    <UFormField
      v-for="mapping in mappings"
      :key="mapping.propertyDefinition.id"
      :label="definitionLabel(mapping)"
      :required="mapping.necessity === 'mandatory'"
      :error="fieldErrors[mapping.propertyDefinition.id]"
      :data-testid="`delta-property-${mapping.propertyDefinition.name}`"
    >
      <PropertyInput
        v-model="values[mapping.propertyDefinition.id]"
        :mapping="mapping"
        :solution-id="props.solutionId"
        :fetch-fn="props.fetchFn"
      />
    </UFormField>

    <UAlert v-if="failure" data-testid="delta-failure" color="error" variant="subtle" :description="failure" />

    <div class="flex justify-end gap-2">
      <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="delta-save" :disabled="!canSave" :loading="saving" :label="t('solutions.deltas.save')" />
    </div>
  </form>
</template>
