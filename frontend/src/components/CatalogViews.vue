<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  addCatalogView,
  expressionOf,
  filterText,
  removeCatalogView,
  updateCatalogView,
  type Catalog,
  type CatalogFilter,
  type CatalogFilterMode,
  type CatalogSavedView,
  type CatalogSavedViewInput,
  type CatalogSort,
  type JoinMode,
} from '../api/catalogs'
import { rateLimitMessage } from '../api/problem'

// The views of SCR-020 (US-053; design D5 of us-053-saved-views): the selection of a saved view,
// "Save view" with the columns of the current table, and the dialog "Views" with "Make default"
// and "Remove". A view is a snapshot of the view state; the page keeps the state in the URL.

/** The view state of the page. */
export interface ViewState {
  viewId?: string
  filters: CatalogFilter[]
  filterMode: CatalogFilterMode
  modes: Record<string, JoinMode>
  sort?: CatalogSort
}

const props = defineProps<{
  catalogId: string
  views: CatalogSavedView[]
  state: ViewState
  /** The primary columns of the current table, with their shown headers. */
  columns: { key: string; label: string }[]
  canConfigure: boolean
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{ apply: [view: CatalogSavedView]; leave: []; changed: [catalog: Catalog] }>()
const { t } = useI18n()

const ALL = '__all__'

const items = computed(() => [
  { label: t('catalog.views.all'), value: ALL },
  ...props.views.map((view) => ({
    label: !view.available
      ? t('catalog.views.unavailable', { name: view.name })
      : view.isDefault
        ? t('catalog.views.defaultNamed', { name: view.name })
        : view.name,
    value: view.id,
    disabled: !view.available,
  })),
])

const active = computed(() => props.views.find((view) => view.id === props.state.viewId))

/** Whether the page shows something else than the active view: other filters, modes or sort. */
const changed = computed(() => {
  const view = active.value
  if (!view) {
    return false
  }
  const filters = props.state.filters.map(filterText)
  const saved = (view.filters ?? []).map((expression) => filterText({ key: expression.attributePath, operator: expression.operator, values: values(expression.value) }))
  const modes = Object.fromEntries((view.joinModeOverrides ?? []).map((override) => [override.joinDefinitionId, override.joinMode]))
  const sameModes =
    Object.keys(props.state.modes).every((joinId) => props.state.modes[joinId] === modes[joinId]) &&
    Object.keys(modes).every((joinId) => props.state.modes[joinId] === modes[joinId] || props.state.modes[joinId] === undefined)
  const sort = props.state.sort
  return (
    filters.join('\n') !== saved.join('\n') ||
    (filters.length > 1 && props.state.filterMode !== view.filterMode) ||
    !sameModes ||
    (sort?.column ?? null) !== (view.sort?.column ?? null) ||
    (sort?.direction ?? null) !== (view.sort?.direction ?? null)
  )
})

function values(value: unknown): string[] {
  return Array.isArray(value) ? value.map(String) : value === undefined || value === null ? [] : [String(value)]
}

function choose(value: unknown) {
  if (value === ALL) {
    emit('leave')
    return
  }
  const view = props.views.find((candidate) => candidate.id === value)
  if (view?.available) {
    emit('apply', view)
  }
}

/** The input of a view: its values with another default flag. */
function inputOf(view: CatalogSavedView, isDefault: boolean): CatalogSavedViewInput {
  return {
    name: view.name,
    description: view.description ?? null,
    columnOrder: view.columnOrder,
    filters: view.filters,
    filterMode: view.filterMode,
    joinModeOverrides: view.joinModeOverrides,
    sort: view.sort ?? null,
    isDefault,
  }
}

// saving a view
interface ColumnRow {
  key: string
  label: string
  shown: boolean
}

const saving = ref(false)
const name = ref('')
const description = ref('')
const makeDefault = ref(false)
const rows = ref<ColumnRow[]>([])
const saveError = ref<string>()
const busy = ref(false)

function openSave() {
  name.value = ''
  description.value = ''
  makeDefault.value = false
  saveError.value = undefined
  rows.value = props.columns.map((column) => ({ key: column.key, label: column.label, shown: true }))
  saving.value = true
}

function move(index: number, by: number) {
  const list = [...rows.value]
  const [row] = list.splice(index, 1)
  list.splice(index + by, 0, row!)
  rows.value = list
}

async function save() {
  const columnOrder = rows.value.filter((row) => row.shown).map((row) => row.key)
  if (!name.value.trim()) {
    saveError.value = t('catalog.views.nameRequired')
    return
  }
  if (columnOrder.length === 0) {
    saveError.value = t('catalog.views.columnRequired')
    return
  }
  const shown = new Set(columnOrder)
  // filters and sort on hidden columns are left out; join columns stay
  const onShown = (key: string) => shown.has(key) || /^[0-9a-f-]{36}\./.test(key)
  busy.value = true
  saveError.value = undefined
  try {
    const outcome = await addCatalogView(
      props.catalogId,
      {
        name: name.value.trim(),
        description: description.value.trim() || null,
        columnOrder,
        filters: props.state.filters.filter((filter) => onShown(filter.key)).map(expressionOf),
        filterMode: props.state.filterMode,
        joinModeOverrides: Object.entries(props.state.modes).map(([joinDefinitionId, joinMode]) => ({ joinDefinitionId, joinMode })),
        sort: props.state.sort && onShown(props.state.sort.column) ? props.state.sort : null,
        isDefault: makeDefault.value,
      },
      props.fetchFn,
    )
    if (outcome.ok) {
      saving.value = false
      emit('changed', outcome.value)
    } else if (outcome.rejection.status === 422 && outcome.rejection.invalidFields?.includes('name')) {
      saveError.value = t('catalog.views.nameTaken')
    } else {
      saveError.value = rateLimitMessage(outcome.rejection) ?? t('catalog.views.saveFailed')
    }
  } catch {
    saveError.value = t('catalog.views.saveFailed')
  } finally {
    busy.value = false
  }
}

// managing views
const managing = ref(false)
const manageError = ref<string>()

async function changeView(action: () => ReturnType<typeof removeCatalogView>) {
  manageError.value = undefined
  try {
    const outcome = await action()
    if (outcome.ok) {
      emit('changed', outcome.value)
    } else {
      manageError.value = rateLimitMessage(outcome.rejection) ?? t('catalog.views.changeFailed')
    }
  } catch {
    manageError.value = t('catalog.views.changeFailed')
  }
}

function makeDefaultView(view: CatalogSavedView) {
  return changeView(() => updateCatalogView(props.catalogId, view.id, inputOf(view, true), props.fetchFn))
}

function remove(view: CatalogSavedView) {
  return changeView(() => removeCatalogView(props.catalogId, view.id, props.fetchFn))
}
</script>

<template>
  <div data-testid="catalog-views" class="flex flex-wrap items-center gap-2">
    <USelect
      v-if="views.length > 0"
      data-testid="view-select"
      :model-value="state.viewId ?? ALL"
      :items="items"
      :aria-label="t('catalog.views.title')"
      icon="i-lucide-layout-list"
      class="w-56"
      @update:model-value="choose"
    />
    <span v-if="changed" data-testid="view-changed" class="text-sm text-muted">{{ t('catalog.views.changed') }}</span>
    <UButton
      v-if="canConfigure"
      data-testid="view-save"
      icon="i-lucide-save"
      color="neutral"
      variant="ghost"
      size="sm"
      :label="t('catalog.views.save')"
      @click="openSave"
    />
    <UButton
      v-if="canConfigure && views.length > 0"
      data-testid="view-manage"
      icon="i-lucide-settings-2"
      color="neutral"
      variant="ghost"
      size="sm"
      :label="t('catalog.views.title')"
      @click="managing = true"
    />

    <UModal v-model:open="saving" :title="t('catalog.views.saveTitle')">
      <template #body>
        <form data-testid="view-save-form" class="flex flex-col gap-4" @submit.prevent="save">
          <UFormField :label="t('catalog.views.name')" required>
            <UInput v-model="name" data-testid="view-save-name" maxlength="100" class="w-full" />
          </UFormField>
          <UFormField :label="t('catalog.views.description')">
            <UTextarea v-model="description" data-testid="view-save-description" maxlength="1000" class="w-full" />
          </UFormField>
          <fieldset class="flex flex-col gap-1">
            <legend class="mb-1 text-sm font-medium text-highlighted">{{ t('catalog.views.columns') }}</legend>
            <div v-for="(row, index) in rows" :key="row.key" data-testid="view-column" class="flex items-center gap-2">
              <UCheckbox v-model="row.shown" data-testid="view-column-shown" :label="row.label" class="flex-1" />
              <UButton
                icon="i-lucide-arrow-up"
                size="xs"
                color="neutral"
                variant="ghost"
                :disabled="index === 0"
                :aria-label="t('catalog.views.moveUp', { name: row.label })"
                @click="move(index, -1)"
              />
              <UButton
                icon="i-lucide-arrow-down"
                size="xs"
                color="neutral"
                variant="ghost"
                :disabled="index === rows.length - 1"
                :aria-label="t('catalog.views.moveDown', { name: row.label })"
                @click="move(index, 1)"
              />
            </div>
          </fieldset>
          <p class="text-sm text-muted">{{ t('catalog.views.snapshotHint') }}</p>
          <UCheckbox v-model="makeDefault" data-testid="view-save-default" :label="t('catalog.views.useAsDefault')" />
          <p v-if="saveError" data-testid="view-save-error" class="text-sm text-error">{{ saveError }}</p>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" :label="t('catalog.views.cancel')" @click="saving = false" />
            <UButton data-testid="view-save-submit" type="submit" :loading="busy" :label="t('catalog.views.saveSubmit')" />
          </div>
        </form>
      </template>
    </UModal>

    <UModal v-model:open="managing" :title="t('catalog.views.title')">
      <template #body>
        <ul class="flex flex-col divide-y divide-default">
          <li v-for="view in views" :key="view.id" data-testid="view-row" class="flex flex-wrap items-center gap-2 py-2">
            <span data-testid="view-row-name" class="flex-1 text-default">{{ view.name }}</span>
            <UBadge v-if="view.isDefault" data-testid="view-row-default" color="primary" variant="subtle" :label="t('catalog.views.default')" />
            <UBadge v-if="!view.available" color="neutral" variant="subtle" :label="t('catalog.views.notAvailable')" />
            <UButton
              v-if="!view.isDefault && view.available"
              data-testid="view-make-default"
              size="xs"
              color="neutral"
              variant="outline"
              :label="t('catalog.views.makeDefault')"
              @click="makeDefaultView(view)"
            />
            <UButton
              data-testid="view-remove"
              size="xs"
              color="error"
              variant="ghost"
              icon="i-lucide-trash-2"
              :aria-label="t('catalog.views.removeNamed', { name: view.name })"
              @click="remove(view)"
            />
          </li>
        </ul>
        <p v-if="manageError" class="mt-2 text-sm text-error">{{ manageError }}</p>
      </template>
    </UModal>
  </div>
</template>
