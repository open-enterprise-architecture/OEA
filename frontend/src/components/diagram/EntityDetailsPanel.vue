<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  fetchDiagramEntityDetails,
  searchDiagramReferenceTargets,
  type DiagramEntityDetails,
} from '../../api/diagramView'
import { fetchMetatype, type PropertyMapping } from '../../api/metamodel'
import { CANVAS_LIMITS } from '../../api/diagramView'
import type { components } from '../../api/generated/schema'
import PropertyInput, { type ReferenceChoice } from '../PropertyInput.vue'
import type { EntityEdit, FieldChange } from './canvasModel'
import type { DetailsSubject } from './edgeModel'

// The details panel of the canvas (SCR-021, US-074 step 6; design D3 of
// us-074-diagram-entity-details): name, description, metatype, version and the values of the
// selected shape's entity in the form of its metatype, with the inputs of the dialogs of SCR-061
// (PropertyInput). Holders of `entity:write` change what the server marks as writable; the changes
// go to the canvas, which keeps them with the unsaved changes of the diagram. A reader sees the
// values read-only; a value the reader may not read shows as "Restricted".
type PropertyValue = components['schemas']['PropertyValue']

const props = defineProps<{
  diagramId: string
  /** the shape or the edge whose entity or connection is shown */
  shape: DetailsSubject
  editable: boolean
  /** the changes made to a saved entity */
  edit?: EntityEdit
  /** the messages of a rejected save, keyed `<entity ID or new entity key>|<field>` */
  errors?: Map<string, string>
  /** raised by the canvas to read the entity again (after a version conflict) */
  reloadToken?: number
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{
  change: [change: FieldChange, version: number]
  close: []
  loaded: [details: DiagramEntityDetails | undefined]
}>()
const { t } = useI18n()

const details = ref<DiagramEntityDetails>()
const mappings = ref<PropertyMapping[]>([])
const state = ref<'loading' | 'ready' | 'failed' | 'gone'>('loading')
let generation = 0

/** A saved entity is read from the server; a new one has only its metatype and what was entered. */
const saved = computed(() => props.shape.entityId !== undefined)
const owner = computed(() => String(props.shape.entityId ?? props.shape.newEntity?.key))
const canEdit = computed(() => props.editable)

async function load() {
  const current = ++generation
  state.value = 'loading'
  try {
    const metatype = await fetchMetatype(props.shape.metatypeId, props.fetchFn)
    const read = saved.value ? await fetchDiagramEntityDetails(props.diagramId, props.shape.entityId!, props.fetchFn) : undefined
    if (current !== generation) return
    mappings.value = (metatype.propertyMappings ?? [])
      .filter((mapping) => mapping.state === undefined || mapping.state === 'active')
      .sort((a, b) => a.sortOrder - b.sortOrder)
    details.value = read
    state.value = saved.value && !read ? 'gone' : 'ready'
    emit('loaded', read)
  } catch {
    if (current === generation) state.value = 'failed'
  }
}

watch(() => [owner.value, props.reloadToken], load, { immediate: true })

function definitionLabel(mapping: PropertyMapping): string {
  return mapping.propertyDefinition.label ?? mapping.propertyDefinition.name
}

function stored(defId: string): PropertyValue | undefined {
  return details.value?.properties.find((value) => value.propertyDefId === defId)
}

/** The text a value has as an input holds it: a reference as its entity ID. */
function asInput(value: PropertyValue | undefined): string {
  if (!value || value.redacted) return ''
  if (value.valueRef) return String(value.valueRef.id)
  const shown = value.valueText ?? value.valueEnum ?? value.valueDate ?? value.valueInt ?? value.valueBool
  return shown === undefined || shown === null ? '' : String(shown)
}

/** The value as shown to a reader: the name of a reference, else the text. */
function display(value: PropertyValue | undefined): string {
  if (!value) return ''
  if (value.valueRef) return value.valueRef.name ?? String(value.valueRef.id)
  return asInput(value)
}

function fieldOf(defId: string) {
  return details.value?.fields.find((field) => field.propertyDefId === defId)
}

function restricted(mapping: PropertyMapping): boolean {
  const defId = mapping.propertyDefinition.id
  return saved.value ? fieldOf(defId)?.readable === false || stored(defId)?.redacted === true : false
}

/** Where the server marks the property as writable; a new entity has no such mark, so everything but a connection-scoped property. */
function writable(mapping: PropertyMapping): boolean {
  if (!canEdit.value) return false
  if (saved.value) return fieldOf(mapping.propertyDefinition.id)?.writable === true
  return mapping.propertyDefinition.visibility?.visibilityMode !== 'connection-scoped'
}

function entered(defId: string): string | undefined {
  return saved.value ? props.edit?.properties[defId] : props.shape.newEntity?.properties?.[defId]
}

/** The value of the input: what was entered, else the value read. */
function valueOf(defId: string): string {
  return entered(defId) ?? (saved.value ? asInput(stored(defId)) : '')
}

function setValue(defId: string, text: string | undefined) {
  const value = text ?? ''
  const baseline = saved.value ? asInput(stored(defId)) : ''
  emit('change', { field: 'property', propertyDefId: defId, value: value === baseline ? undefined : value }, details.value?.version ?? 0)
}

const name = computed(() => (saved.value ? (props.edit?.name ?? details.value?.name ?? props.shape.name) : props.shape.name))
const description = computed(() => (saved.value ? (props.edit?.description ?? details.value?.description ?? '') : (props.shape.newEntity?.description ?? '')))

function setName(text: string | undefined) {
  const value = text ?? ''
  emit('change', { field: 'name', value: value === details.value?.name ? undefined : value }, details.value?.version ?? 0)
}

function setDescription(text: string | undefined) {
  const value = text ?? ''
  emit('change', { field: 'description', value: value === (saved.value ? (details.value?.description ?? '') : '') ? undefined : value }, details.value?.version ?? 0)
}

/** Whether a mandatory property has no value now: what was entered, else what the entity has. */
function missing(mapping: PropertyMapping): boolean {
  if (mapping.necessity !== 'mandatory') return false
  const defId = mapping.propertyDefinition.id
  const text = entered(defId)
  if (text !== undefined) return text === ''
  return saved.value ? (details.value?.missingMandatory ?? []).includes(mapping.propertyDefinition.name) : true
}

const missingNames = computed(() => mappings.value.filter(missing).map(definitionLabel))

function error(field: string): string | undefined {
  return props.errors?.get(`${owner.value}|${field}`)
}

function searchFor(mapping: PropertyMapping): (term: string) => Promise<ReferenceChoice[]> {
  return async (term) => {
    const outcome = await searchDiagramReferenceTargets(props.diagramId, mapping.propertyDefinition.id, term, props.fetchFn)
    return outcome.ok ? outcome.value.items.map((entry) => ({ label: entry.name, value: String(entry.id) })) : []
  }
}

const metatypeName = computed(() => details.value?.metatype.name ?? '')
</script>

<template>
  <aside
    class="flex w-full flex-col gap-3 rounded-md border border-accented bg-default p-3 text-sm lg:w-80 lg:shrink-0"
    role="complementary"
    :aria-label="t('diagrams.details.label')"
    data-testid="entity-details"
    @keydown.esc.stop="emit('close')"
  >
    <div class="flex items-start gap-2">
      <div class="min-w-0 flex-1">
        <h2 class="truncate text-base font-semibold text-highlighted" data-testid="details-title">{{ name }}</h2>
        <p class="text-xs text-muted" data-testid="details-meta">
          <span v-if="metatypeName">{{ metatypeName }}</span>
          <span v-else-if="shape.newEntity">{{ t('diagrams.details.newEntity') }}</span>
          <span v-if="details"> · {{ t('diagrams.details.version', { version: details.version }) }}</span>
        </p>
      </div>
      <UButton color="neutral" variant="ghost" size="xs" icon="i-lucide-x" :aria-label="t('diagrams.details.close')" data-testid="details-close" @click="emit('close')" />
    </div>

    <p v-if="state === 'loading'" class="text-muted" data-testid="details-loading">{{ t('diagrams.details.loading') }}</p>
    <UAlert v-else-if="state === 'failed'" color="error" variant="subtle" data-testid="details-failure" :description="t('diagrams.details.loadFailed')" />
    <UAlert v-else-if="state === 'gone'" color="neutral" variant="subtle" data-testid="details-gone" :description="t('diagrams.details.gone')" />

    <template v-else>
      <UAlert v-if="error('')" color="error" variant="subtle" data-testid="details-error" :description="error('')" />
      <p v-if="missingNames.length > 0" class="flex items-center gap-1 text-xs text-warning" data-testid="details-missing">
        <UIcon name="i-lucide-triangle-alert" class="size-4 shrink-0" />
        <span>{{ t('diagrams.details.missing', { properties: missingNames.join(', ') }) }}</span>
      </p>
      <p v-if="!canEdit" class="text-xs text-muted" data-testid="details-read-only">{{ t('diagrams.details.readOnly') }}</p>

      <UFormField v-if="saved" :label="t('diagrams.details.name')" :error="error('name')" data-testid="details-field-name">
        <UInput v-if="canEdit" :model-value="name" class="w-full" :maxlength="CANVAS_LIMITS.entityName" :highlight="!!error('name')" data-testid="details-name" @update:model-value="(value: string | number | undefined) => setName(String(value ?? ''))" />
        <p v-else data-testid="details-name-text">{{ name }}</p>
      </UFormField>
      <UFormField :label="t('diagrams.details.description')" :error="error('description')" data-testid="details-field-description">
        <UTextarea
          v-if="canEdit"
          :model-value="description"
          class="w-full"
          :rows="2"
          :maxlength="CANVAS_LIMITS.entityDescription"
          autoresize
          :highlight="!!error('description')"
          data-testid="details-description"
          @update:model-value="(value: string | undefined) => setDescription(String(value ?? ''))"
        />
        <p v-else data-testid="details-description-text">{{ description || t('diagrams.details.noValue') }}</p>
      </UFormField>

      <UFormField
        v-for="mapping in mappings"
        :key="mapping.propertyDefinition.id"
        :label="definitionLabel(mapping)"
        :required="mapping.necessity === 'mandatory'"
        :error="error(mapping.propertyDefinition.id)"
        :help="missing(mapping) && !error(mapping.propertyDefinition.id) ? t('diagrams.details.valueMissing') : undefined"
        :data-testid="`details-field-${mapping.propertyDefinition.name}`"
      >
        <PropertyInput
          v-if="writable(mapping)"
          :model-value="valueOf(mapping.propertyDefinition.id)"
          :mapping="mapping"
          :search-references="searchFor(mapping)"
          :selected-label="display(stored(mapping.propertyDefinition.id))"
          :invalid="!!error(mapping.propertyDefinition.id)"
          @update:model-value="(value: string | undefined) => setValue(mapping.propertyDefinition.id, value)"
        />
        <p v-else-if="restricted(mapping)" class="text-muted" data-testid="details-restricted">{{ t('diagrams.details.restricted') }}</p>
        <p v-else :data-testid="`details-value-${mapping.propertyDefinition.name}`">
          {{ display(stored(mapping.propertyDefinition.id)) || t('diagrams.details.noValue') }}
          <span v-if="canEdit && mapping.necessity === 'mandatory' && missing(mapping)" class="text-xs text-muted"> ({{ t('diagrams.details.notChangeable') }})</span>
        </p>
      </UFormField>
    </template>
  </aside>
</template>
