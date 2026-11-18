<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  createViewpoint,
  elementFits,
  INSTANCE_CONFIGURATION,
  MAPPING_SIZE_MAX,
  MAPPING_SIZE_MIN,
  updateViewpoint,
  VIEWPOINT_NOTATIONS,
  type NotationElement,
  type Viewpoint,
  type ViewpointMapping,
  type ViewpointNotation,
} from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Creating and changing a viewpoint in SCR-031 (US-090, US-091; design D4 of us-090-viewpoints, D6
// of us-091-viewpoint-notations): name, description, the notation, the element and connection
// metatypes of the instance as checkboxes, and per selected type the optional notation mapping
// (element of the notation or a generic one, size, color). Changing the notation clears the
// elements that no longer fit. A stale version shows the collision warning with the current state.
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
})

/** The mapping of one type as typed: the element key, the sizes as text and the color. */
interface MappingRow {
  element?: string
  width: string
  height: string
  hint?: string
}
const NONE = 'none'
const rows = reactive<Record<string, MappingRow>>(
  Object.fromEntries(
    (props.viewpoint?.mappings ?? []).map((mapping) => [
      mapping.metatypeId,
      { element: mapping.notationElement, width: mapping.defaultWidth?.toString() ?? '', height: mapping.defaultHeight?.toString() ?? '', hint: mapping.visualHint ?? undefined },
    ]),
  ),
)
const BLANK: MappingRow = { width: '', height: '' }
const rowOf = (id: string): MappingRow => rows[id] ?? BLANK
function patch(id: string, change: Partial<MappingRow>) {
  rows[id] = { ...rowOf(id), ...change }
}

// changing the notation clears the elements that no longer fit, with their size and color (US-091 AC3)
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

/** A size is empty (the catalog's) or a whole number from 1 to 2000. */
function sizeValid(text: string): boolean {
  if (text.trim() === '') return true
  const value = Number(text)
  return Number.isInteger(value) && value >= MAPPING_SIZE_MIN && value <= MAPPING_SIZE_MAX
}
const sizesValid = computed(() => selected.value.every((type) => !rows[type.id]?.element || (sizeValid(rows[type.id]!.width) && sizeValid(rows[type.id]!.height))))
const valid = computed(
  () =>
    form.name.trim().length > 0 &&
    form.name.trim().length <= NAME_MAX &&
    form.description.length <= DESCRIPTION_MAX &&
    form.metatypeIds.length > 0 &&
    sizesValid.value,
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
        defaultWidth: mapping.width.trim() === '' ? null : Number(mapping.width),
        defaultHeight: mapping.height.trim() === '' ? null : Number(mapping.height),
        visualHint: mapping.hint ?? null,
      },
    ]
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
            <th class="py-1 pe-2 text-start font-medium">{{ t('metamodel.viewpoint.width') }}</th>
            <th class="py-1 pe-2 text-start font-medium">{{ t('metamodel.viewpoint.height') }}</th>
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
            <td class="py-1 pe-2 align-top">
              <UInput
                :model-value="rowOf(type.id).width"
                @update:model-value="(value: string | number) => patch(type.id, { width: String(value ?? '') })"
                :data-testid="`mapping-width-${type.id}`"
                type="number"
                :min="MAPPING_SIZE_MIN"
                :max="MAPPING_SIZE_MAX"
                :disabled="!rowOf(type.id).element"
                :color="sizeValid(rowOf(type.id).width) ? undefined : 'error'"
                :aria-label="`${t('metamodel.viewpoint.width')}: ${type.label}`"
                :aria-invalid="!sizeValid(rowOf(type.id).width)"
                class="w-24"
              />
            </td>
            <td class="py-1 pe-2 align-top">
              <UInput
                :model-value="rowOf(type.id).height"
                @update:model-value="(value: string | number) => patch(type.id, { height: String(value ?? '') })"
                :data-testid="`mapping-height-${type.id}`"
                type="number"
                :min="MAPPING_SIZE_MIN"
                :max="MAPPING_SIZE_MAX"
                :disabled="!rowOf(type.id).element"
                :color="sizeValid(rowOf(type.id).height) ? undefined : 'error'"
                :aria-label="`${t('metamodel.viewpoint.height')}: ${type.label}`"
                :aria-invalid="!sizeValid(rowOf(type.id).height)"
                class="w-24"
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
      <p v-if="!sizesValid" data-testid="mapping-size-error" class="mt-1 text-sm text-error">
        {{ t('metamodel.viewpoint.sizeInvalid', { min: MAPPING_SIZE_MIN, max: MAPPING_SIZE_MAX }) }}
      </p>
    </UFormField>
    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="viewpoint-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="viewpoint-submit" :disabled="!valid" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
