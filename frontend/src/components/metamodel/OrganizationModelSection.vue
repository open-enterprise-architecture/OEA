<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchMetatypes, type MetatypeDetail } from '../../api/metamodel'
import { ORGANIZATION_PARTS, fetchOrganizationModel, updateOrganizationModel, type OrganizationModel, type OrganizationPart } from '../../api/organization'
import { fetchPersonMetatype } from '../../api/visibility'
import { label, labelKeys } from '../../i18n/labels'
import { rejectionMessage } from './messages'

// "Organization model" of SCR-030 (US-075, US-076, REQ-077; design D2 of us-075-lane-assignments): which entity types
// play organizational unit, role and person, and which connection types play belongs-to, performs-role and
// responsible. Everyone who reads the metamodel sees it; holders of metamodel:write change it with the version they
// read (a locked metamodel offers no change). A package import fills the parts that are still unset.
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
// USelect does not accept an empty value, so an unset part has its own value
const NONE = 'none'
const { t } = useI18n()

const model = ref<OrganizationModel>()
const metatypes = ref<MetatypeDetail[]>([])
const chosen = ref<Record<OrganizationPart, string>>(Object.fromEntries(ORGANIZATION_PARTS.map((part) => [part, NONE])) as Record<OrganizationPart, string>)
const visibilityPerson = ref<string | null>(null)
const failure = ref<string>()
const notice = ref<string>()
const invalid = ref<OrganizationPart[]>([])
const saving = ref(false)

const CONNECTION: readonly OrganizationPart[] = ['belongsTo', 'performsRole', 'responsible']

function items(part: OrganizationPart) {
  const connection = CONNECTION.includes(part)
  return [
    { value: NONE, label: t('metamodel.organization.unset') },
    ...metatypes.value
      .filter((type) => (type.isConnection === true) === connection)
      .map((type) => ({ value: type.id, label: label(labelKeys.metatype(type.id), type.name) })),
  ]
}

function adopt(next: OrganizationModel) {
  model.value = next
  for (const part of ORGANIZATION_PARTS) {
    chosen.value[part] = next[part].metatypeId ?? NONE
  }
}

async function load() {
  try {
    const [read, types] = await Promise.all([fetchOrganizationModel(props.fetchFn), fetchMetatypes(props.fetchFn)])
    metatypes.value = types
    adopt(read)
    failure.value = undefined
  } catch {
    failure.value = t('metamodel.organization.loadFailed')
  }
  // the person metatype of connection-scoped visibility is read by security administration only: without it no hint
  try {
    visibilityPerson.value = (await fetchPersonMetatype(props.fetchFn)).metatypeId
  } catch {
    visibilityPerson.value = null
  }
}

/** The person part differs from the person metatype of connection-scoped visibility: a hint (SoD-2 keeps them apart). */
const differs = computed(() => !!chosen.value.person && chosen.value.person !== NONE && !!visibilityPerson.value && chosen.value.person !== visibilityPerson.value)

const unchanged = computed(() => !model.value || ORGANIZATION_PARTS.every((part) => (model.value![part].metatypeId ?? NONE) === chosen.value[part]))

async function save() {
  if (!model.value) return
  failure.value = undefined
  notice.value = undefined
  invalid.value = []
  saving.value = true
  try {
    const body = Object.fromEntries(ORGANIZATION_PARTS.map((part) => [part, chosen.value[part] === NONE ? null : chosen.value[part]]))
    const outcome = await updateOrganizationModel(body, model.value.version, props.fetchFn)
    if (outcome.ok) {
      adopt(outcome.value)
      notice.value = t('metamodel.organization.saved')
    } else if (outcome.rejection.problemType === 'stale-version') {
      await load()
      failure.value = t('metamodel.collision.reloaded')
    } else if (outcome.rejection.status === 422) {
      invalid.value = ORGANIZATION_PARTS.filter((part) => outcome.rejection.invalidFields.includes(part))
      failure.value = t('metamodel.organization.invalid')
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
  <section class="flex flex-col gap-3" data-testid="organization-model" aria-labelledby="organization-model-title">
    <div class="flex flex-col gap-1">
      <h2 id="organization-model-title" class="font-semibold text-highlighted">{{ t('metamodel.organization.title') }}</h2>
      <p class="text-sm text-muted">{{ t('metamodel.organization.hint') }}</p>
    </div>
    <UAlert v-if="failure" data-testid="organization-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="organization-notice" color="success" variant="subtle" :description="notice" />
    <UAlert v-if="differs" data-testid="organization-person-hint" color="info" variant="subtle" icon="i-lucide-info" :description="t('metamodel.organization.personDiffers')" />
    <div v-if="model" class="grid gap-3 sm:grid-cols-2">
      <UFormField
        v-for="part in ORGANIZATION_PARTS"
        :key="part"
        :label="t(`metamodel.organization.part.${part}`)"
        :error="invalid.includes(part) ? t('metamodel.organization.partInvalid') : undefined"
        :data-testid="`organization-part-${part}`"
      >
        <USelect v-if="canWrite" v-model="chosen[part]" :items="items(part)" class="w-full" :data-testid="`organization-select-${part}`" />
        <p v-else class="text-sm" :data-testid="`organization-value-${part}`">{{ model[part].metatypeName ? label(labelKeys.metatype(model[part].metatypeId!), model[part].metatypeName!) : t('metamodel.organization.unset') }}</p>
      </UFormField>
    </div>
    <div v-if="canWrite && model" class="flex justify-end">
      <UButton :loading="saving" :disabled="unchanged" :label="t('metamodel.organization.save')" data-testid="organization-save" @click="save" />
    </div>
  </section>
</template>
