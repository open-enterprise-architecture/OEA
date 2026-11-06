<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  addCatalogFilter,
  expressionOf,
  FILTER_OPERATORS,
  MAX_FILTERS,
  takesList,
  takesNoValue,
  type Catalog,
  type CatalogFilter,
  type CatalogFilterMode,
  type CatalogFilterOperator,
  type CatalogFilterType,
  type CatalogSavedFilter,
} from '../api/catalogs'
import { rateLimitMessage } from '../api/problem'

// The filter bar of SCR-020 (US-051, US-052; design D5 of us-051-catalog-filters): one chip per
// filter, "Add filter" in three steps – column, operator by the column's type, value –, the AND/OR
// switch, the quick selection of the saved filters and "Save filter". The page keeps the filters
// in the URL; the bar only reports changes.

/** A column a filter can name; `group` is the join name for a join column. */
export interface FilterColumn {
  key: string
  label: string
  group?: string
  filterType: CatalogFilterType
  enumValues?: string[]
}

const props = defineProps<{
  catalogId: string
  columns: FilterColumn[]
  filters: CatalogFilter[]
  mode: CatalogFilterMode
  savedFilters: CatalogSavedFilter[]
  canSave: boolean
  /** The shown label of a value, e.g. a translated enum value. */
  valueLabel: (key: string, value: string) => string
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{
  change: [filters: CatalogFilter[], mode: CatalogFilterMode]
  apply: [filter: CatalogSavedFilter]
  saved: [catalog: Catalog]
}>()
const { t } = useI18n()

const byKey = computed(() => new Map(props.columns.map((column) => [column.key, column])))

function columnLabel(column: FilterColumn | undefined, key: string): string {
  if (!column) {
    return key
  }
  return column.group ? t('catalog.filters.joinColumn', { join: column.group, column: column.label }) : column.label
}

/** "Status equals Active", with the labels of the column, the operator and the values. */
function chipLabel(filter: CatalogFilter): string {
  const column = byKey.value.get(filter.key)
  const parts = [columnLabel(column, filter.key), t(`catalog.filters.operators.${filter.operator}`)]
  if (filter.values.length > 0) {
    parts.push(filter.values.map((value) => shownValue(filter.key, column, value)).join(', '))
  }
  return parts.join(' ')
}

function shownValue(key: string, column: FilterColumn | undefined, value: string): string {
  if (column?.filterType === 'boolean') {
    return t(`catalog.filters.${value === 'true' ? 'yes' : 'no'}`)
  }
  return column?.filterType === 'enum' ? props.valueLabel(key, value) : value
}

function remove(index: number) {
  emit(
    'change',
    props.filters.filter((_, candidate) => candidate !== index),
    props.mode,
  )
}

// adding a filter
const adding = ref(false)
const columnKey = ref<string>()
const operator = ref<CatalogFilterOperator>()
const value = ref('')
const listValues = ref<string[]>([])

const chosen = computed(() => (columnKey.value ? byKey.value.get(columnKey.value) : undefined))

const columnItems = computed(() => {
  const primary = props.columns.filter((column) => !column.group).map((column) => ({ label: column.label, value: column.key }))
  const groups = [...new Set(props.columns.filter((column) => column.group).map((column) => column.group!))].map((group) => [
    { type: 'label' as const, label: group },
    ...props.columns.filter((column) => column.group === group).map((column) => ({ label: column.label, value: column.key })),
  ])
  return groups.length > 0 ? [primary, ...groups] : primary
})

const operatorItems = computed(() =>
  chosen.value
    ? FILTER_OPERATORS[chosen.value.filterType].map((candidate) => ({ label: t(`catalog.filters.operators.${candidate}`), value: candidate }))
    : [],
)

const valueItems = computed(() => {
  if (chosen.value?.filterType === 'boolean') {
    return [
      { label: t('catalog.filters.yes'), value: 'true' },
      { label: t('catalog.filters.no'), value: 'false' },
    ]
  }
  return (chosen.value?.enumValues ?? []).map((candidate) => ({ label: props.valueLabel(chosen.value!.key, candidate), value: candidate }))
})

const inputType = computed(() => (chosen.value?.filterType === 'number' ? 'number' : chosen.value?.filterType === 'date' ? 'date' : 'text'))

watch(columnKey, () => {
  operator.value = undefined
  value.value = ''
  listValues.value = []
})

/** The values of the form: a list for in/notIn, none for the empty tests. */
const formValues = computed<string[] | undefined>(() => {
  if (!chosen.value || !operator.value) {
    return undefined
  }
  if (takesNoValue(operator.value)) {
    return []
  }
  if (takesList(operator.value)) {
    const values =
      chosen.value.filterType === 'enum'
        ? listValues.value
        : value.value
            .split(',')
            .map((item) => item.trim())
            .filter(Boolean)
    return values.length > 0 ? values : undefined
  }
  const single = value.value.trim()
  return single ? [single] : undefined
})

function openAdd() {
  columnKey.value = undefined
  operator.value = undefined
  value.value = ''
  listValues.value = []
  adding.value = true
}

function add() {
  if (!columnKey.value || !operator.value || !formValues.value) {
    return
  }
  emit('change', [...props.filters, { key: columnKey.value, operator: operator.value, values: formValues.value }], props.mode)
  adding.value = false
}

const modeItems = computed(() => [
  { label: t('catalog.filters.and'), value: 'and' },
  { label: t('catalog.filters.or'), value: 'or' },
])

// saved filters
const savedItems = computed(() =>
  props.savedFilters.map((filter) => ({
    label: filter.available ? filter.name : t('catalog.filters.unavailable', { name: filter.name }),
    value: filter.id,
    disabled: !filter.available,
  })),
)

function choose(id: unknown) {
  const filter = props.savedFilters.find((candidate) => candidate.id === id)
  if (filter) {
    emit('apply', filter)
  }
}

const saving = ref(false)
const name = ref('')
const description = ref('')
const saveError = ref<string>()
const busy = ref(false)

function openSave() {
  name.value = ''
  description.value = ''
  saveError.value = undefined
  saving.value = true
}

async function save() {
  if (!name.value.trim()) {
    saveError.value = t('catalog.filters.nameRequired')
    return
  }
  busy.value = true
  saveError.value = undefined
  try {
    const outcome = await addCatalogFilter(
      props.catalogId,
      {
        name: name.value.trim(),
        description: description.value.trim() || null,
        logicalOperator: props.mode,
        expressions: props.filters.map(expressionOf),
      },
      props.fetchFn,
    )
    if (outcome.ok) {
      saving.value = false
      emit('saved', outcome.value)
    } else if (outcome.rejection.status === 422 && outcome.rejection.invalidFields?.includes('name')) {
      saveError.value = t('catalog.filters.nameTaken')
    } else {
      saveError.value = rateLimitMessage(outcome.rejection) ?? t('catalog.filters.saveFailed')
    }
  } catch {
    saveError.value = t('catalog.filters.saveFailed')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section data-testid="filter-bar" class="flex flex-wrap items-center gap-2" :aria-label="t('catalog.filters.title')">
    <USelect
      v-if="savedFilters.length > 0"
      data-testid="saved-filters"
      :items="savedItems"
      :placeholder="t('catalog.filters.savedPlaceholder')"
      :aria-label="t('catalog.filters.savedPlaceholder')"
      :ui="{ placeholder: 'text-muted' }"
      icon="i-lucide-bookmark"
      class="w-56"
      @update:model-value="choose"
    />

    <span
      v-for="(filter, index) in filters"
      :key="`${index}-${filter.key}`"
      data-testid="filter-chip"
      class="inline-flex items-center gap-1 rounded-md bg-elevated px-2 py-1 text-sm text-default"
    >
      <span data-testid="filter-chip-label">{{ chipLabel(filter) }}</span>
      <UButton
        data-testid="filter-chip-remove"
        icon="i-lucide-x"
        size="xs"
        color="neutral"
        variant="ghost"
        :aria-label="t('catalog.filters.removeNamed', { name: chipLabel(filter) })"
        @click="remove(index)"
      />
    </span>

    <USelect
      v-if="filters.length >= 2"
      data-testid="filter-mode"
      :model-value="mode"
      :items="modeItems"
      :aria-label="t('catalog.filters.modeLabel')"
      class="w-40"
      @update:model-value="(next: unknown) => emit('change', filters, next as CatalogFilterMode)"
    />

    <UPopover v-model:open="adding">
      <UButton
        data-testid="filter-add"
        icon="i-lucide-filter"
        color="neutral"
        variant="outline"
        size="sm"
        :disabled="filters.length >= MAX_FILTERS || columns.length === 0"
        :label="t('catalog.filters.add')"
        @click="openAdd"
      />
      <template #content>
        <form data-testid="filter-form" class="flex w-72 flex-col gap-3 p-3" @submit.prevent="add">
          <UFormField :label="t('catalog.filters.column')">
            <USelect v-model="columnKey" data-testid="filter-column" :items="columnItems" class="w-full" />
          </UFormField>
          <UFormField v-if="chosen" :label="t('catalog.filters.operator')">
            <USelect v-model="operator" data-testid="filter-operator" :items="operatorItems" class="w-full" />
          </UFormField>
          <template v-if="chosen && operator && !takesNoValue(operator)">
            <UFormField :label="t('catalog.filters.value')" :help="takesList(operator) && chosen.filterType !== 'enum' ? t('catalog.filters.listHelp') : undefined">
              <USelectMenu
                v-if="takesList(operator) && chosen.filterType === 'enum'"
                v-model="listValues"
                data-testid="filter-values"
                :items="valueItems"
                value-key="value"
                multiple
                class="w-full"
              />
              <USelect
                v-else-if="valueItems.length > 0 && !takesList(operator)"
                v-model="value"
                data-testid="filter-value-choice"
                :items="valueItems"
                class="w-full"
              />
              <UInput v-else v-model="value" data-testid="filter-value" :type="takesList(operator) ? 'text' : inputType" class="w-full" />
            </UFormField>
          </template>
          <UButton data-testid="filter-apply" type="submit" :disabled="!formValues" :label="t('catalog.filters.apply')" class="self-end" />
        </form>
      </template>
    </UPopover>

    <UButton
      v-if="canSave && filters.length > 0"
      data-testid="filter-save"
      icon="i-lucide-bookmark-plus"
      color="neutral"
      variant="ghost"
      size="sm"
      :label="t('catalog.filters.save')"
      @click="openSave"
    />

    <UModal v-model:open="saving" :title="t('catalog.filters.saveTitle')">
      <template #body>
        <form data-testid="filter-save-form" class="flex flex-col gap-4" @submit.prevent="save">
          <UFormField :label="t('catalog.filters.name')" required :error="saveError">
            <UInput v-model="name" data-testid="filter-save-name" maxlength="100" class="w-full" />
          </UFormField>
          <UFormField :label="t('catalog.filters.description')">
            <UTextarea v-model="description" data-testid="filter-save-description" maxlength="1000" class="w-full" />
          </UFormField>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" :label="t('catalog.filters.cancel')" @click="saving = false" />
            <UButton data-testid="filter-save-submit" type="submit" :loading="busy" :label="t('catalog.filters.saveSubmit')" />
          </div>
        </form>
      </template>
    </UModal>
  </section>
</template>
