<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import {
  fetchCatalog,
  filterOf,
  filterText,
  MAX_FILTERS,
  parseFilter,
  queryCatalog,
  type Catalog,
  type CatalogColumn,
  type CatalogColumnConfig,
  type CatalogFilter,
  type CatalogFilterMode,
  type CatalogQueryResult,
  type CatalogSavedFilter,
  type CatalogSavedView,
  type CatalogSort,
  type JoinMode,
} from '../api/catalogs'
import { PageCursors, rejectsCursor } from '../api/cursors'
import { queryTimeoutMessage, rateLimitMessage, RequestRejected } from '../api/problem'
import { fetchPropertyDefinitions, type PropertyDefinitionDetail } from '../api/metamodel'
import CatalogColumnsDialog from '../components/CatalogColumnsDialog.vue'
import CatalogFilterBar, { type FilterColumn } from '../components/CatalogFilterBar.vue'
import CatalogJoinsDialog from '../components/CatalogJoinsDialog.vue'
import PlaceInNavigation from '../components/PlaceInNavigation.vue'
import CatalogTable from '../components/CatalogTable.vue'
import CatalogViews from '../components/CatalogViews.vue'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// A single catalog with its rows (UC-06, US-049, SCR-020). Sort and page are part of the URL
// (ADR-044, design D8 of us-049-show-catalog-data), so back navigation and reload restore the view;
// so is the mode of every join that differs from its default (design D5 of us-048-catalog-joins)
// and so are the filters with their combination (design D5 of us-051-catalog-filters) and the
// saved view whose columns the table shows (design D5 of us-053-saved-views).
// Metatype names, column headers and enum values are shown in the language of the user (US-140).
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const route = useRoute()
const router = useRouter()
const { t, te } = useI18n()

const catalog = ref<Catalog>()
const state = ref<'loading' | 'loaded' | 'not-found' | 'failed'>('loading')
const result = ref<CatalogQueryResult>()
// the waiting time of a request above the rate limit (REQ-186), shown instead of the generic failure
const failure = ref<string>()
const querying = ref(false)
const definitions = ref<PropertyDefinitionDetail[]>([])
const configuring = ref(false)
const joining = ref(false)
// a saved filter was loaded without expressions on columns the result no longer has (US-052)
const filterHint = ref<string>()
// an instance catalog needs catalog:manage; a visible personal catalog is one's own (US-047)
const canConfigure = computed(
  () =>
    catalog.value?.scope === 'personal' ||
    (session.me?.permissions.some((entry) => entry.permission === 'catalog:manage') ?? false),
)
// placing the catalog in the navigation tree needs viewpoint:manage (US-054)
const canPlace = computed(() => session.me?.permissions.some((entry) => entry.permission === 'viewpoint:manage') ?? false)
// the path the page is shown at; query changes while navigating elsewhere are ignored
const path = route.path
let latestQuery = 0

const cursors = new PageCursors()

// column keys are system fields (camel case) or storage names of properties (US-047), join
// columns `{joinId}.{key}` (US-048)
const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
const SORT = new RegExp(`^((?:${UUID}\\.|source\\.|target\\.)?[a-zA-Z][a-zA-Z0-9_]*):(asc|desc)$`)
const MODE = new RegExp(`^(${UUID}):(rows|aggregate)$`)
const PAGE = /^\d+$/

interface View {
  sort?: CatalogSort
  page: number
  modes: Record<string, JoinMode>
  filters: CatalogFilter[]
  filterMode: CatalogFilterMode
  viewId?: string
  invalid: string[]
}

/** The parameters of the view state; without any of them the default view applies (US-049 AC6). */
const VIEW_PARAMETERS = ['view', 'sort', 'page', 'mode', 'filter', 'filterMode']
const VIEW_ID = new RegExp(`^${UUID}$`)

/** The view state of the URL; `invalid` lists the parameters that do not parse. */
function viewOf(query: LocationQuery): View {
  const invalid: string[] = []
  let sort: CatalogSort | undefined
  let page = 0
  const modes: Record<string, JoinMode> = {}
  const filters: CatalogFilter[] = []
  if (query.filter !== undefined) {
    const entries = Array.isArray(query.filter) ? query.filter : [query.filter]
    for (const entry of entries) {
      // the column check is the server's; an unknown column answers 400
      const filter = typeof entry === 'string' ? parseFilter(entry) : undefined
      if (filter && entries.length <= MAX_FILTERS) {
        filters.push(filter)
      } else {
        invalid.push('filter')
      }
    }
  }
  let filterMode: CatalogFilterMode = 'and'
  if (query.filterMode !== undefined) {
    if (query.filterMode === 'or' || query.filterMode === 'and') {
      filterMode = query.filterMode
    } else {
      invalid.push('filterMode')
    }
  }
  let viewId: string | undefined
  if (query.view !== undefined) {
    if (typeof query.view === 'string' && VIEW_ID.test(query.view)) {
      viewId = query.view
    } else {
      invalid.push('view')
    }
  }
  if (query.mode !== undefined) {
    for (const entry of Array.isArray(query.mode) ? query.mode : [query.mode]) {
      const match = typeof entry === 'string' ? MODE.exec(entry) : null
      if (match) {
        modes[match[1]!] = match[2] as JoinMode
      } else {
        invalid.push('mode')
      }
    }
  }
  if (query.sort !== undefined) {
    const match = typeof query.sort === 'string' ? SORT.exec(query.sort) : null
    if (match) {
      sort = { column: match[1]!, direction: match[2] as CatalogSort['direction'] }
    } else {
      invalid.push('sort')
    }
  }
  if (query.page !== undefined) {
    if (typeof query.page === 'string' && PAGE.test(query.page) && Number.isSafeInteger(Number(query.page))) {
      page = Number(query.page)
    } else {
      invalid.push('page')
    }
  }
  return { sort, page, modes, filters, filterMode, viewId, invalid }
}

/** Writes the view to the URL without a history entry; defaults are omitted (ADR-044). */
async function showView(
  sort: CatalogSort | undefined,
  page: number,
  modes: Record<string, JoinMode> = view.value.modes,
  filters: CatalogFilter[] = view.value.filters,
  filterMode: CatalogFilterMode = view.value.filterMode,
  // `null` leaves the view; an omitted argument keeps it
  viewId: string | null = view.value.viewId ?? null,
) {
  const query: LocationQuery = { ...route.query }
  delete query.view
  if (viewId) {
    query.view = viewId
  }
  delete query.sort
  delete query.page
  delete query.mode
  delete query.filter
  delete query.filterMode
  if (filters.length > 0) {
    query.filter = filters.map(filterText)
    if (filterMode === 'or') {
      query.filterMode = 'or'
    }
  }
  if (sort) {
    query.sort = `${sort.column}:${sort.direction}`
  }
  if (page > 0) {
    query.page = String(page)
  }
  const entries = Object.entries(modes).map(([joinId, mode]) => `${joinId}:${mode}`)
  if (entries.length > 0) {
    query.mode = entries
  }
  await router.replace({ query })
}

/** The stored mode of a join: its default, else the catalog's. */
function defaultMode(joinId: string): JoinMode {
  const join = catalog.value?.joinDefinitions.find((candidate) => candidate.id === joinId)
  return join?.defaultJoinMode ?? catalog.value?.defaultJoinMode ?? 'aggregate'
}

/** Switches a join; a sort by one of its columns ends when it becomes compact (US-050). */
async function switchMode(joinId: string, mode: JoinMode) {
  const modes = { ...view.value.modes }
  if (mode === defaultMode(joinId)) {
    delete modes[joinId]
  } else {
    modes[joinId] = mode
  }
  const sort = view.value.sort
  const keepSort = !(mode === 'aggregate' && sort?.column.startsWith(`${joinId}.`))
  await showView(keepSort ? sort : undefined, view.value.page, modes)
}

const view = computed(() => viewOf(route.query))

/** New filters show the first page; the sort stays (design D5 of us-051-catalog-filters). */
async function changeFilters(filters: CatalogFilter[], filterMode: CatalogFilterMode) {
  filterHint.value = undefined
  await showView(view.value.sort, 0, view.value.modes, filters, filters.length > 1 ? filterMode : 'and')
}

/** A saved filter replaces the filters of the bar; expressions on columns the result lacks are left out. */
async function applySaved(saved: CatalogSavedFilter) {
  const known = new Set(filterColumns.value.map((column) => column.key))
  const filters = saved.expressions.map(filterOf)
  const kept = filters.filter((filter) => known.has(filter.key))
  filterHint.value = kept.length < filters.length ? t('catalog.filters.partlyApplied', { name: saved.name }) : undefined
  await showView(view.value.sort, 0, view.value.modes, kept, kept.length > 1 ? saved.logicalOperator : 'and')
}

/** The columns of the result a filter can name, with their translated headers (US-051). */
const filterColumns = computed<FilterColumn[]>(() => {
  if (!shown.value) {
    return []
  }
  return [
    ...shown.value.columns.map((column) => ({ key: column.key, label: column.label, filterType: column.filterType, enumValues: column.enumValues })),
    ...(shown.value.joins ?? []).flatMap((join) =>
      join.columns.map((column) => ({
        key: `${join.id}.${column.key}`,
        label: column.label,
        group: join.name,
        filterType: column.filterType,
        enumValues: column.enumValues,
      })),
    ),
  ]
})

/** An enum value in the user's language, by the storage name of its property. */
function valueLabel(key: string, value: string): string {
  const property = key.replace(/^(?:source|target|[0-9a-f-]{36})\./, '')
  const enumValue = byColumn.value.get(property)?.enumValues?.find((candidate) => candidate.value === value)
  return enumValue ? label(labelKeys.enumValue(enumValue.id), value) : value
}

/** A view replaces columns, filters, join modes and sort; overrides equal to the default are left out. */
async function applyView(saved: CatalogSavedView) {
  filterHint.value = undefined
  const modes = Object.fromEntries(
    (saved.joinModeOverrides ?? [])
      .filter((override) => override.joinMode !== defaultMode(override.joinDefinitionId))
      .map((override) => [override.joinDefinitionId, override.joinMode]),
  )
  const filters = (saved.filters ?? []).map(filterOf)
  await showView(saved.sort ?? undefined, 0, modes, filters, filters.length > 1 ? saved.filterMode : 'and', saved.id)
}

/** "All columns" leaves the view and keeps filters, join modes and sort. */
async function leaveView() {
  await showView(view.value.sort, 0, view.value.modes, view.value.filters, view.value.filterMode, null)
}

/** After a change of the views, the selection shows them. */
function viewsChanged(changed: Catalog) {
  catalog.value = changed
}

/** The primary columns of the table, for the column selection of a new view. */
const viewColumns = computed(() => (shown.value?.columns ?? []).map((column) => ({ key: column.key, label: column.label })))

/** After saving a filter, the quick selection offers it. */
function filterSaved(saved: Catalog) {
  catalog.value = saved
}

const byColumn = computed(() => new Map(definitions.value.map((definition) => [definition.sqlColumnName, definition])))

/** Headers in the user's language; a configured header is shown as written (US-047, US-140). */
function translatedColumns(columns: CatalogColumn[], configured: CatalogColumnConfig[]): CatalogColumn[] {
  const ownLabels = new Set(
    configured
      .filter((column) => column.displayLabel)
      .map((column) => {
        const key = column.field ?? definitions.value.find((definition) => definition.id === column.propertyDefId)?.sqlColumnName
        return column.endpoint ? `${column.endpoint}.${key}` : key
      }),
  )
  return columns.map((column) => {
    if (ownLabels.has(column.key)) {
      return column
    }
    const endpoint = /^(source|target)\.(.+)$/.exec(column.key)
    if (endpoint) {
      // the source or target of a connection catalog (US-080): "Source", else "Source: <attribute>"
      const side = t(`catalog.columns.endpoint.${endpoint[1]}`)
      if (endpoint[2] === 'name') {
        return { ...column, label: side }
      }
      const definition = byColumn.value.get(endpoint[2]!)
      const attribute = definition
        ? label(labelKeys.property(definition.id), definition.name)
        : te(`catalog.columns.field.${endpoint[2]}`)
          ? t(`catalog.columns.field.${endpoint[2]}`)
          : column.label
      return { ...column, label: t('catalog.columns.endpointAttribute', { side, name: attribute }) }
    }
    const definition = byColumn.value.get(column.key)
    if (column.key === 'name') {
      return { ...column, label: t('catalog.nameColumn') }
    }
    if (!definition && te(`catalog.columns.field.${column.key}`)) {
      return { ...column, label: t(`catalog.columns.field.${column.key}`) }
    }
    return definition ? { ...column, label: label(labelKeys.property(definition.id), column.label) } : column
  })
}

/** Values with enum values in the user's language. */
function translatedValues(values: Record<string, string | null>): Record<string, string | null> {
  return Object.fromEntries(
    Object.entries(values).map(([key, value]) => {
      const enumValue = byColumn.value.get(key.replace(/^(source|target)\./, ''))?.enumValues?.find((candidate) => candidate.value === value)
      return [key, enumValue && value ? label(labelKeys.enumValue(enumValue.id), value) : value]
    }),
  )
}

/** The rows with translated column headers and enum values (design D7 of us-140-dynamic-labels). */
const shown = computed<CatalogQueryResult | undefined>(() => {
  if (!result.value) {
    return undefined
  }
  return {
    ...result.value,
    columns: translatedColumns(result.value.columns, catalog.value?.columns ?? []),
    joins: (result.value.joins ?? []).map((join) => ({
      ...join,
      columns: translatedColumns(join.columns, catalog.value?.joinDefinitions.find((definition) => definition.id === join.id)?.targetColumns ?? []),
    })),
    rows: result.value.rows.map((row) => ({
      ...row,
      values: translatedValues(row.values),
      joinResults: Object.fromEntries(
        Object.entries(row.joinResults ?? {}).map(([joinId, results]) =>
          [joinId, results.map((joined) => ({ ...joined, values: translatedValues(joined.values) }))],
        ),
      ),
    })),
  }
})

const metatypeName = computed(() =>
  catalog.value ? label(labelKeys.metatype(catalog.value.primaryMetatypeId), catalog.value.primaryMetatypeName) : '',
)

async function loadDefinitions() {
  try {
    definitions.value = await fetchPropertyDefinitions(props.fetchFn)
  } catch {
    // without definitions the headers and values stay in the corporate language
    definitions.value = []
  }
}

async function loadCatalog() {
  await refreshSession(props.fetchFn)
  state.value = 'loading'
  result.value = undefined
  failure.value = undefined
  cursors.forget()
  try {
    catalog.value = await fetchCatalog(props.id, props.fetchFn)
    const preset = catalog.value?.savedViews.find((candidate) => candidate.isDefault && candidate.available)
    if (preset && !VIEW_PARAMETERS.some((name) => route.query[name] !== undefined)) {
      // the default view applies only without view state in the URL (design D5 of us-053-saved-views)
      await applyView(preset)
    }
    state.value = catalog.value ? 'loaded' : 'not-found'
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined)
    state.value = 'failed'
  }
  if (state.value === 'loaded') {
    await loadDefinitions()
    await loadRows()
  }
}

async function loadRows() {
  const { sort, page, modes, filters, filterMode, viewId, invalid } = view.value
  if (invalid.length > 0) {
    await showView(undefined, 0, {}, [], 'and', null)
    return
  }
  const current = ++latestQuery
  querying.value = true
  try {
    const query = { sort, joinModes: modes, filters: filters.map(filterText), filterMode, view: viewId }
    const key = JSON.stringify(query)
    const cursor = cursors.cursorFor(key, page)
    let outcome = await queryCatalog(props.id, { ...query, page: page > 0 ? page : undefined, cursor }, props.fetchFn)
    if (cursor && !outcome.ok && rejectsCursor(outcome.rejection) && current === latestQuery) {
      // the cursor is no longer valid, e.g. after a restart of the backend: the page number applies
      outcome = await queryCatalog(props.id, { ...query, page }, props.fetchFn)
    }
    if (current !== latestQuery) {
      return
    }
    if (outcome.ok) {
      cursors.remember(key, outcome.value)
      const pageMeta = outcome.value.page
      if (pageMeta.page > 0 && outcome.value.rows.length === 0 && pageMeta.totalElements > 0) {
        // a page beyond the last one, e.g. from an old link
        await showView(sort, 0)
        return
      }
      result.value = outcome.value
    } else if (outcome.rejection.status === 400 && viewId && outcome.rejection.invalidFields?.includes('view')) {
      // a view that was removed or no longer fits the catalog
      await showView(sort, 0, modes, filters, filterMode, null)
    } else if (outcome.rejection.status === 400 && (sort || page > 0 || Object.keys(modes).length > 0 || filters.length > 0 || viewId)) {
      // e.g. a sort or filter column the catalog no longer returns or a removed join
      await showView(undefined, 0, {}, [], 'and', null)
    } else if (outcome.rejection.status === 404) {
      state.value = 'not-found'
    } else {
      failure.value = rateLimitMessage(outcome.rejection) ?? queryTimeoutMessage(outcome.rejection)
      state.value = 'failed'
    }
  } catch {
    state.value = 'failed'
  } finally {
    if (current === latestQuery) {
      querying.value = false
    }
  }
}

/** After saving the columns, the table shows them in the default order (design D6). */
async function columnsSaved(saved: Catalog) {
  catalog.value = saved
  configuring.value = false
  if (view.value.sort || view.value.page > 0) {
    await showView(undefined, 0)
  } else {
    await loadRows()
  }
}

/** After changing the joins, the table shows them with their default modes (design D5). */
async function joinsChanged(changed: Catalog) {
  catalog.value = changed
  if (view.value.sort || view.value.page > 0 || Object.keys(view.value.modes).length > 0 || view.value.filters.length > 0) {
    await showView(undefined, 0, {}, [], 'and', null)
  } else {
    await loadRows()
  }
}

onMounted(loadCatalog)
watch(() => props.id, loadCatalog)
watch(
  () => [route.query.sort, route.query.page, route.query.mode, route.query.filter, route.query.filterMode, route.query.view],
  () => {
    if (state.value === 'loaded' && route.path === path) {
      void loadRows()
    }
  },
)
</script>

<template>
  <main class="mx-auto flex max-w-5xl flex-col gap-6 p-8">
    <UButton
      to="/catalogs"
      data-testid="back-to-catalogs"
      icon="i-lucide-arrow-left"
      color="neutral"
      variant="ghost"
      :label="t('catalogs.title')"
      class="self-start"
    />

    <UAlert
      v-if="state === 'not-found'"
      data-testid="catalog-not-found"
      color="neutral"
      variant="subtle"
      :title="t('catalog.notFound')"
      :description="t('catalog.notFoundHint')"
    />
    <UAlert
      v-if="state === 'failed'"
      data-testid="catalog-failure"
      color="error"
      variant="subtle"
      :description="failure ?? t('catalog.loadFailed')"
    />

    <template v-if="state === 'loaded' && catalog">
      <header class="flex flex-col gap-2">
        <div class="flex flex-wrap items-center gap-3">
          <h1 data-testid="catalog-title" class="text-2xl font-semibold text-highlighted">{{ catalog.name }}</h1>
          <UBadge
            data-testid="catalog-scope"
            :color="catalog.scope === 'personal' ? 'neutral' : 'primary'"
            variant="subtle"
            :label="catalog.scope === 'personal' ? t('catalogs.personal') : t('catalogs.shared')"
          />
          <div v-if="canConfigure || canPlace" class="ms-auto flex flex-wrap gap-2">
            <PlaceInNavigation v-if="canPlace" :catalog-id="catalog.id" :catalog-name="catalog.name" :fetch-fn="fetchFn" />
            <UButton
              v-if="canConfigure"
              data-testid="configure-columns"
              icon="i-lucide-columns-3"
              color="neutral"
              variant="outline"
              :label="t('catalog.columns.configure')"
              @click="configuring = true"
            />
            <UButton
              v-if="canConfigure"
              data-testid="configure-joins"
              icon="i-lucide-git-merge"
              color="neutral"
              variant="outline"
              :label="t('catalog.joins.title')"
              @click="joining = true"
            />
          </div>
        </div>
        <p data-testid="catalog-type" class="text-muted">{{ metatypeName }}</p>
        <p v-if="catalog.description" class="text-default">{{ catalog.description }}</p>
      </header>

      <div v-if="shown" class="flex flex-col gap-2">
        <CatalogViews
          :catalog-id="catalog.id"
          :views="catalog.savedViews"
          :state="{ viewId: view.viewId, filters: view.filters, filterMode: view.filterMode, modes: view.modes, sort: view.sort }"
          :columns="viewColumns"
          :can-configure="canConfigure"
          :fetch-fn="fetchFn"
          @apply="applyView"
          @leave="leaveView"
          @changed="viewsChanged"
        />
        <CatalogFilterBar
          :catalog-id="catalog.id"
          :columns="filterColumns"
          :filters="view.filters"
          :mode="view.filterMode"
          :saved-filters="catalog.savedFilters"
          :can-save="canConfigure"
          :value-label="valueLabel"
          :fetch-fn="fetchFn"
          @change="changeFilters"
          @apply="applySaved"
          @saved="filterSaved"
        />
        <p v-if="filterHint" data-testid="filter-hint" class="text-sm text-muted">{{ filterHint }}</p>
      </div>

      <CatalogTable
        v-if="shown"
        :result="shown"
        :loading="querying"
        :primary-label="metatypeName"
        @sort="(sort) => showView(sort, 0)"
        @page="(page) => showView(view.sort, page)"
        @mode="switchMode"
      />

      <UModal v-model:open="configuring" :title="t('catalog.columns.title')" :ui="{ content: 'sm:max-w-3xl' }">
        <template #body>
          <CatalogColumnsDialog
            :catalog="catalog"
            :definitions="definitions"
            :fetch-fn="fetchFn"
            @saved="columnsSaved"
            @cancel="configuring = false"
          />
        </template>
      </UModal>

      <UModal v-model:open="joining" :title="t('catalog.joins.title')" :ui="{ content: 'sm:max-w-3xl' }">
        <template #body>
          <CatalogJoinsDialog
            :catalog="catalog"
            :definitions="definitions"
            :fetch-fn="fetchFn"
            @changed="joinsChanged"
            @close="joining = false"
          />
        </template>
      </UModal>
    </template>
  </main>
</template>
