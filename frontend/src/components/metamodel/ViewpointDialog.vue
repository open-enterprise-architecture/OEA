<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  countAffectedDiagrams,
  createViewpoint,
  elementFits,
  INSTANCE_CONFIGURATION,
  DEFAULT_HEIGHT,
  DEFAULT_LENGTH,
  DEFAULT_WIDTH,
  MAPPING_SIZE_MAX,
  MAPPING_SIZE_MIN,
  updateViewpoint,
  VIEWPOINT_NOTATIONS,
  type NotationElement,
  type Viewpoint,
  type ViewpointMapping,
  type ViewpointNotation,
  type ViewpointSize,
} from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Creating and changing a viewpoint in SCR-031 (US-090, US-091; design D4 of us-090-viewpoints, D6
// of us-091-viewpoint-notations): name, description, the notation, the element and connection
// metatypes of the instance as checkboxes, and per selected type the optional notation mapping
// (element of the notation or a generic one, color) and the optional default size in pt (design D5 of
// s17-viewpoint-default-sizes): width and height for an element type, a length for a connection
// type. Changing the notation clears the elements that no longer fit, not the sizes. A stale version shows the collision warning with the current state.
const props = defineProps<{ viewpoint?: Viewpoint; current?: Viewpoint; catalog: NotationElement[]; catalogFailed?: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: []; cancel: []; stale: [] }>()
const { t } = useI18n()

const NAME_MAX = 255
const DESCRIPTION_MAX = 1000

const form = reactive({
  name: props.viewpoint?.name ?? '',
  description: props.viewpoint?.description ?? '',
  metatypeIds: (props.viewpoint?.allowedMetatypes ?? []).map((metatype) => metatype.id),
  connectionTypeIds: (props.viewpoint?.allowedRelations ?? []).map((metatype) => metatype.id),
  notation: (props.viewpoint?.notation ?? 'generic') as ViewpointNotation,
  /** the setting "Ask 'New parent?'": the global one, or an override (design D4 of us-074-diagram-nesting) */
  askNewParent: (props.viewpoint?.askNewParent === true ? 'ask' : props.viewpoint?.askNewParent === false ? 'no' : 'global') as 'global' | 'ask' | 'no',
})
const askChoices = computed(() => [
  { value: 'global', label: t('metamodel.viewpoint.askNewParentGlobal') },
  { value: 'ask', label: t('metamodel.viewpoint.askNewParentAsk') },
  { value: 'no', label: t('metamodel.viewpoint.askNewParentNo') },
])

/**
 * The warning "N diagrams affected" (US-090 AC2; design D4 of us-074-diagram-viewpoint-change): an allowed entity type
 * or connection type is removed from a viewpoint that diagrams use. The number is that of the diagrams with a shown
 * shape or edge of a removed type, asked of the backend for the types selected now (debounced); their shapes become
 * conflicts. The warning does not block saving.
 */
const COUNT_DEBOUNCE_MS = 250
const affectedDiagrams = ref(0)
const removesType = computed(() => {
  const viewpoint = props.viewpoint
  if (!viewpoint || viewpoint.diagramCount === 0) {
    return false
  }
  return (
    viewpoint.allowedMetatypes.some((type) => !form.metatypeIds.includes(type.id)) ||
    viewpoint.allowedRelations.some((type) => !form.connectionTypeIds.includes(type.id))
  )
})
let countTimer: ReturnType<typeof setTimeout> | undefined
let countTicket = 0
watch(
  () => [removesType.value, [...form.metatypeIds].sort().join(), [...form.connectionTypeIds].sort().join()],
  () => {
    clearTimeout(countTimer)
    const ticket = ++countTicket
    const viewpoint = props.viewpoint
    if (!viewpoint || !removesType.value) {
      affectedDiagrams.value = 0
      return
    }
    countTimer = setTimeout(async () => {
      try {
        const outcome = await countAffectedDiagrams(viewpoint.id, [...form.metatypeIds], [...form.connectionTypeIds], props.fetchFn)
        if (ticket === countTicket) {
          affectedDiagrams.value = outcome.ok ? outcome.value.count : 0
        }
      } catch {
        if (ticket === countTicket) {
          affectedDiagrams.value = 0
        }
      }
    }, COUNT_DEBOUNCE_MS)
  },
)
onBeforeUnmount(() => clearTimeout(countTimer))

/** The mapping of one type as typed: the element key and the color. */
interface MappingRow {
  element?: string
  hint?: string
}
const NONE = 'none'
const rows = reactive<Record<string, MappingRow>>(
  Object.fromEntries(
    (props.viewpoint?.mappings ?? []).map((mapping) => [
      mapping.metatypeId,
      { element: mapping.notationElement, hint: mapping.visualHint ?? undefined },
    ]),
  ),
)
const BLANK: MappingRow = {}

/** The default size of one type as typed, in pt: width and height of an element, the length of a connection. */
interface SizeRow {
  width: string
  height: string
  length: string
}
const sizes = reactive<Record<string, SizeRow>>(
  Object.fromEntries(
    (props.viewpoint?.sizes ?? []).map((size) => [size.metatypeId, { width: size.width?.toString() ?? '', height: size.height?.toString() ?? '', length: size.length?.toString() ?? '' }]),
  ),
)
const EMPTY_SIZE: SizeRow = { width: '', height: '', length: '' }
const sizeOf = (id: string): SizeRow => sizes[id] ?? EMPTY_SIZE
function patchSize(id: string, change: Partial<SizeRow>) {
  sizes[id] = { ...sizeOf(id), ...change }
}
const rowOf = (id: string): MappingRow => rows[id] ?? BLANK
function patch(id: string, change: Partial<MappingRow>) {
  rows[id] = { ...rowOf(id), ...change }
}

// changing the notation clears the elements that no longer fit, with their color (US-091 AC3); sizes stay
watch(
  () => form.notation,
  (notation) => {
    for (const [id, mapping] of Object.entries(rows)) {
      if (mapping.element && !elementFits(notation, mapping.element)) {
        rows[id] = { ...BLANK }
      }
    }
  },
)

const notations = computed(() => VIEWPOINT_NOTATIONS.map((value) => ({ value, label: t(`metamodel.viewpoint.notations.${value}`) })))
const NOTATION_ORDER = ['archimate3', 'uml', 'bpmn2', 'generic']

/** The elements a type of this shape can be drawn with: the notation's own and the generic ones (US-091 AC1). */
function choices(connection: boolean) {
  const items = props.catalog
    .filter((element) => (element.kind === 'relationship') === connection && elementFits(form.notation, element.key))
    .sort((a, b) => NOTATION_ORDER.indexOf(a.notation) - NOTATION_ORDER.indexOf(b.notation) || a.label.localeCompare(b.label))
    .map((element) => ({ value: element.key, label: element.notation === 'generic' ? t('metamodel.viewpoint.genericElement', { label: element.label }) : element.label }))
  return [{ value: NONE, label: t('metamodel.viewpoint.noElement') }, ...items]
}
const elementChoices = computed(() => choices(false))
const connectionChoices = computed(() => choices(true))
const errors = ref<Record<string, string>>({})
const failure = ref<string>()
const submitting = ref(false)
const stale = ref(false)

// the metamodel store holds the instance metatypes; extensions name their own configuration
const instance = computed(() => metamodel.metatypes.filter((metatype) => !metatype.configId || metatype.configId === INSTANCE_CONFIGURATION))
const elements = computed(() =>
  instance.value.filter((metatype) => !metatype.isConnection).map((metatype) => ({ value: metatype.id, label: label(labelKeys.metatype(metatype.id), metatype.name) })),
)
const connections = computed(() =>
  instance.value.filter((metatype) => metatype.isConnection).map((metatype) => ({ value: metatype.id, label: label(labelKeys.metatype(metatype.id), metatype.name) })),
)
/** The selected types in the table, elements first. */
const selected = computed(() => [
  ...elements.value.filter((item) => form.metatypeIds.includes(item.value)).map((item) => ({ id: item.value, label: item.label, connection: false })),
  ...connections.value.filter((item) => form.connectionTypeIds.includes(item.value)).map((item) => ({ id: item.value, label: item.label, connection: true })),
])

// removing a type removes its size fields and their values
watch(selected, (types) => {
  const ids = new Set(types.map((type) => type.id))
  for (const id of Object.keys(sizes)) {
    if (!ids.has(id)) delete sizes[id]
  }
})

/** A size is empty (the default applies) or a whole number from 1 to 2000 pt. */
function sizeValid(text: string): boolean {
  if (text.trim() === '') return true
  const value = Number(text)
  return Number.isInteger(value) && value >= MAPPING_SIZE_MIN && value <= MAPPING_SIZE_MAX
}
const filled = (text: string) => text.trim() !== ''
/** Width and height are filled together or left empty together. */
const pairValid = (id: string) => filled(sizeOf(id).width) === filled(sizeOf(id).height)
const sizesValid = computed(() =>
  selected.value.every((type) => (type.connection ? sizeValid(sizeOf(type.id).length) : sizeValid(sizeOf(type.id).width) && sizeValid(sizeOf(type.id).height))),
)
const pairsValid = computed(() => selected.value.every((type) => type.connection || pairValid(type.id)))
const valid = computed(
  () =>
    form.name.trim().length > 0 &&
    form.name.trim().length <= NAME_MAX &&
    form.description.length <= DESCRIPTION_MAX &&
    form.metatypeIds.length > 0 &&
    sizesValid.value &&
    pairsValid.value,
)

/** The mappings of the selected types that have an element, as the request carries them. */
function mappings(): ViewpointMapping[] {
  return selected.value.flatMap((type) => {
    const mapping = rows[type.id]
    if (!mapping?.element) return []
    return [
      {
        metatypeId: type.id,
        notationElement: mapping.element,
        visualHint: mapping.hint ?? null,
      },
    ]
  })
}

/** The sizes of the selected types that have one, as the request carries them. */
function sizeList(): ViewpointSize[] {
  return selected.value.flatMap((type): ViewpointSize[] => {
    const size = sizeOf(type.id)
    if (type.connection) return filled(size.length) ? [{ metatypeId: type.id, length: Number(size.length) }] : []
    return filled(size.width) && filled(size.height) ? [{ metatypeId: type.id, width: Number(size.width), height: Number(size.height) }] : []
  })
}

async function submit(version = props.viewpoint?.version) {
  failure.value = undefined
  errors.value = {}
  if (!valid.value) {
    return
  }
  submitting.value = true
  const body = {
    name: form.name.trim(),
    description: form.description.trim() ? form.description : null,
    metatypeIds: form.metatypeIds,
    connectionTypeIds: form.connectionTypeIds,
    notation: form.notation,
    mappings: mappings(),
    sizes: sizeList(),
    askNewParent: form.askNewParent === 'global' ? null : form.askNewParent === 'ask',
  }
  try {
    const outcome = props.viewpoint
      ? await updateViewpoint(props.viewpoint.id, body, version, props.fetchFn)
      : await createViewpoint(body, props.fetchFn)
    if (outcome.ok) {
      stale.value = false
      emit('saved')
    } else if (outcome.rejection.problemType === 'stale-version') {
      stale.value = true
      emit('stale')
    } else if (outcome.rejection.problemType === 'read-only-viewpoint') {
      failure.value = t('metamodel.viewpoint.readOnly')
    } else if (outcome.rejection.status === 422 && outcome.rejection.invalidFields[0]) {
      errors.value = { [outcome.rejection.invalidFields[0]]: outcome.rejection.detail ?? t('metamodel.viewpoint.invalid') }
    } else {
      failure.value = rejectionMessage(outcome.rejection)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="viewpoint-form" @submit.prevent="submit()">
    <UAlert v-if="failure" data-testid="viewpoint-failure" color="error" variant="subtle" :description="failure" />
    <UAlert
      v-if="affectedDiagrams > 0"
      data-testid="viewpoint-diagrams-affected"
      color="warning"
      variant="subtle"
      :description="t('metamodel.viewpoint.diagramsAffected', { count: affectedDiagrams }, affectedDiagrams)"
    />
    <CollisionWarning v-if="stale" @overwrite="submit(props.current?.version)" @discard="emit('cancel')">
      <p v-if="props.current">{{ props.current.name }}<span v-if="props.current.description"> – {{ props.current.description }}</span></p>
      <p v-else>{{ t('metamodel.collision.deleted') }}</p>
    </CollisionWarning>
    <UFormField :label="t('metamodel.viewpoint.name')" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="viewpoint-name" class="w-full" :maxlength="NAME_MAX" autofocus />
    </UFormField>
    <UFormField
      :label="t('metamodel.viewpoint.description')"
      name="description"
      :error="form.description.length <= DESCRIPTION_MAX ? undefined : t('metamodel.viewpoint.descriptionTooLong', { max: DESCRIPTION_MAX })"
    >
      <UTextarea v-model="form.description" data-testid="viewpoint-description" :rows="2" class="w-full" />
    </UFormField>
    <UFormField :label="t('metamodel.viewpoint.metatypes')" :error="errors.metatypeIds" :help="t('metamodel.viewpoint.metatypesHint')" required>
      <UCheckboxGroup v-model="form.metatypeIds" data-testid="viewpoint-metatypes" :items="elements" class="max-h-56 overflow-y-auto" />
    </UFormField>
    <UFormField :label="t('metamodel.viewpoint.connections')" :error="errors.connectionTypeIds">
      <UCheckboxGroup v-if="connections.length > 0" v-model="form.connectionTypeIds" data-testid="viewpoint-connections" :items="connections" class="max-h-40 overflow-y-auto" />
      <p v-else class="text-sm text-muted">{{ t('metamodel.viewpoint.noConnections') }}</p>
    </UFormField>
    <UFormField :label="t('metamodel.viewpoint.notation')" name="notation" :error="errors.notation">
      <USelect v-model="form.notation" data-testid="viewpoint-notation" :items="notations" class="w-full" :aria-label="t('metamodel.viewpoint.notation')" />
    </UFormField>
    <UFormField :label="t('metamodel.viewpoint.askNewParentLabel')" name="askNewParent" :help="t('metamodel.viewpoint.askNewParentHint')">
      <USelect v-model="form.askNewParent" data-testid="viewpoint-ask-new-parent" :items="askChoices" class="w-full" :aria-label="t('metamodel.viewpoint.askNewParentLabel')" />
    </UFormField>
    <UFormField
      v-if="selected.length > 0"
      :label="t('metamodel.viewpoint.mapping')"
      name="mappings"
      :error="errors.mappings"
      :help="t('metamodel.viewpoint.mappingHint')"
    >
      <UAlert v-if="catalogFailed" data-testid="viewpoint-catalog-failed" color="warning" variant="subtle" :description="t('metamodel.viewpoint.catalogFailed')" class="mb-2" />
      <table class="w-full text-sm" data-testid="viewpoint-mappings">
        <thead class="text-start text-muted">
          <tr>
            <th class="py-1 pe-2 text-start font-medium">{{ t('metamodel.viewpoint.mappingType') }}</th>
            <th class="py-1 pe-2 text-start font-medium">{{ t('metamodel.viewpoint.element') }}</th>
            <th class="py-1 text-start font-medium">{{ t('metamodel.viewpoint.color') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="type in selected" :key="type.id" data-testid="mapping-row" :data-metatype-id="type.id">
            <td class="py-1 pe-2 align-top">{{ type.label }}</td>
            <td class="py-1 pe-2 align-top">
              <USelect
                :model-value="rowOf(type.id).element ?? NONE"
                :data-testid="`mapping-element-${type.id}`"
                :items="type.connection ? connectionChoices : elementChoices"
                :aria-label="t('metamodel.viewpoint.elementOf', { name: type.label })"
                class="w-52"
                @update:model-value="(value: string) => patch(type.id, { element: value === NONE ? undefined : value })"
              />
            </td>
            <td class="py-1 align-top">
              <span class="flex items-center gap-1">
                <input
                  type="color"
                  :data-testid="`mapping-color-${type.id}`"
                  :value="rowOf(type.id).hint ?? '#000000'"
                  :disabled="!rowOf(type.id).element"
                  :aria-label="`${t('metamodel.viewpoint.color')}: ${type.label}`"
                  class="h-8 w-10 cursor-pointer rounded border border-default bg-default disabled:cursor-not-allowed disabled:opacity-50"
                  @input="patch(type.id, { hint: ($event.target as HTMLInputElement).value })"
                />
                <UButton
                  v-if="rowOf(type.id).hint"
                  :data-testid="`mapping-color-clear-${type.id}`"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-x"
                  :aria-label="t('metamodel.viewpoint.clearColor', { name: type.label })"
                  @click="patch(type.id, { hint: undefined })"
                />
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </UFormField>
    <UFormField
      v-if="selected.length > 0"
      :label="t('metamodel.viewpoint.sizes')"
      name="sizes"
      :error="errors.sizes"
      :help="t('metamodel.viewpoint.sizesHint')"
    >
      <table class="w-full text-sm" data-testid="viewpoint-sizes">
        <thead class="text-start text-muted">
          <tr>
            <th class="py-1 pe-2 text-start font-medium">{{ t('metamodel.viewpoint.mappingType') }}</th>
            <th class="py-1 pe-2 text-start font-medium">{{ t('metamodel.viewpoint.width') }}</th>
            <th class="py-1 pe-2 text-start font-medium">{{ t('metamodel.viewpoint.height') }}</th>
            <th class="py-1 text-start font-medium">{{ t('metamodel.viewpoint.length') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="type in selected" :key="type.id" data-testid="size-row" :data-metatype-id="type.id">
            <td class="py-1 pe-2 align-top">{{ type.label }}</td>
            <template v-if="!type.connection">
              <td class="py-1 pe-2 align-top">
                <UInput
                  :model-value="sizeOf(type.id).width"
                  @update:model-value="(value: string | number) => patchSize(type.id, { width: String(value ?? '') })"
                  :data-testid="`size-width-${type.id}`"
                  type="number"
                  :min="MAPPING_SIZE_MIN"
                  :max="MAPPING_SIZE_MAX"
                  :placeholder="String(DEFAULT_WIDTH)"
                  :color="sizeValid(sizeOf(type.id).width) && pairValid(type.id) ? undefined : 'error'"
                  :aria-label="`${t('metamodel.viewpoint.width')}: ${type.label}`"
                  :aria-invalid="!sizeValid(sizeOf(type.id).width) || !pairValid(type.id)"
                  class="w-24"
                />
              </td>
              <td class="py-1 pe-2 align-top">
                <UInput
                  :model-value="sizeOf(type.id).height"
                  @update:model-value="(value: string | number) => patchSize(type.id, { height: String(value ?? '') })"
                  :data-testid="`size-height-${type.id}`"
                  type="number"
                  :min="MAPPING_SIZE_MIN"
                  :max="MAPPING_SIZE_MAX"
                  :placeholder="String(DEFAULT_HEIGHT)"
                  :color="sizeValid(sizeOf(type.id).height) && pairValid(type.id) ? undefined : 'error'"
                  :aria-label="`${t('metamodel.viewpoint.height')}: ${type.label}`"
                  :aria-invalid="!sizeValid(sizeOf(type.id).height) || !pairValid(type.id)"
                  class="w-24"
                />
              </td>
              <td class="py-1 align-top"></td>
            </template>
            <template v-else>
              <td class="py-1 pe-2 align-top"></td>
              <td class="py-1 pe-2 align-top"></td>
              <td class="py-1 align-top">
                <UInput
                  :model-value="sizeOf(type.id).length"
                  @update:model-value="(value: string | number) => patchSize(type.id, { length: String(value ?? '') })"
                  :data-testid="`size-length-${type.id}`"
                  type="number"
                  :min="MAPPING_SIZE_MIN"
                  :max="MAPPING_SIZE_MAX"
                  :placeholder="String(DEFAULT_LENGTH)"
                  :color="sizeValid(sizeOf(type.id).length) ? undefined : 'error'"
                  :aria-label="`${t('metamodel.viewpoint.length')}: ${type.label}`"
                  :aria-invalid="!sizeValid(sizeOf(type.id).length)"
                  class="w-24"
                />
              </td>
            </template>
          </tr>
        </tbody>
      </table>
      <p v-if="!sizesValid" data-testid="size-error" class="mt-1 text-sm text-error">
        {{ t('metamodel.viewpoint.sizeInvalid', { min: MAPPING_SIZE_MIN, max: MAPPING_SIZE_MAX }) }}
      </p>
      <p v-if="!pairsValid" data-testid="size-pair-error" class="mt-1 text-sm text-error">{{ t('metamodel.viewpoint.sizePairInvalid') }}</p>
    </UFormField>
    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="viewpoint-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="viewpoint-submit" :disabled="!valid" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
