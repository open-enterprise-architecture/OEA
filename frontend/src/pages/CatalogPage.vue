<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import {
  fetchCatalog,
  queryCatalog,
  type Catalog,
  type CatalogColumn,
  type CatalogColumnConfig,
  type CatalogQueryResult,
  type CatalogSort,
  type JoinMode,
} from '../api/catalogs'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { fetchPropertyDefinitions, type PropertyDefinitionDetail } from '../api/metamodel'
import CatalogColumnsDialog from '../components/CatalogColumnsDialog.vue'
import CatalogJoinsDialog from '../components/CatalogJoinsDialog.vue'
import CatalogTable from '../components/CatalogTable.vue'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// A single catalog with its rows (UC-06, US-049, SCR-020). Sort and page are part of the URL
// (ADR-044, design D8 of us-049-show-catalog-data), so back navigation and reload restore the view;
// so is the mode of every join that differs from its default (design D5 of us-048-catalog-joins).
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
// an instance catalog needs catalog:manage; a visible personal catalog is one's own (US-047)
const canConfigure = computed(
  () =>
    catalog.value?.scope === 'personal' ||
    (session.me?.permissions.some((entry) => entry.permission === 'catalog:manage') ?? false),
)
// the path the page is shown at; query changes while navigating elsewhere are ignored
const path = route.path
let latestQuery = 0

// column keys are system fields (camel case) or storage names of properties (US-047), join
// columns `{joinId}.{key}` (US-048)
const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
const SORT = new RegExp(`^((?:${UUID}\\.)?[a-zA-Z][a-zA-Z0-9_]*):(asc|desc)$`)
const MODE = new RegExp(`^(${UUID}):(rows|aggregate)$`)
const PAGE = /^\d+$/

interface View {
  sort?: CatalogSort
  page: number
  modes: Record<string, JoinMode>
  invalid: string[]
}

/** The view state of the URL; `invalid` lists the parameters that do not parse. */
function viewOf(query: LocationQuery): View {
  const invalid: string[] = []
  let sort: CatalogSort | undefined
  let page = 0
  const modes: Record<string, JoinMode> = {}
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
  return { sort, page, modes, invalid }
}

/** Writes the view to the URL without a history entry; defaults are omitted (ADR-044). */
async function showView(sort: CatalogSort | undefined, page: number, modes: Record<string, JoinMode> = view.value.modes) {
  const query: LocationQuery = { ...route.query }
  delete query.sort
  delete query.page
  delete query.mode
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

const byColumn = computed(() => new Map(definitions.value.map((definition) => [definition.sqlColumnName, definition])))

/** Headers in the user's language; a configured header is shown as written (US-047, US-140). */
function translatedColumns(columns: CatalogColumn[], configured: CatalogColumnConfig[]): CatalogColumn[] {
  const ownLabels = new Set(
    configured
      .filter((column) => column.displayLabel)
      .map((column) => column.field ?? definitions.value.find((definition) => definition.id === column.propertyDefId)?.sqlColumnName),
  )
  return columns.map((column) => {
    const definition = byColumn.value.get(column.key)
    if (ownLabels.has(column.key)) {
      return column
    }
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
      const enumValue = byColumn.value.get(key)?.enumValues?.find((candidate) => candidate.value === value)
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
  try {
    catalog.value = await fetchCatalog(props.id, props.fetchFn)
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
  const { sort, page, modes, invalid } = view.value
  if (invalid.length > 0) {
    await showView(undefined, 0, {})
    return
  }
  const current = ++latestQuery
  querying.value = true
  try {
    const outcome = await queryCatalog(props.id, { page: page > 0 ? page : undefined, sort, joinModes: modes }, props.fetchFn)
    if (current !== latestQuery) {
      return
    }
    if (outcome.ok) {
      const pageMeta = outcome.value.page
      if (pageMeta.page > 0 && outcome.value.rows.length === 0 && pageMeta.totalElements > 0) {
        // a page beyond the last one, e.g. from an old link
        await showView(sort, 0)
        return
      }
      result.value = outcome.value
    } else if (outcome.rejection.status === 400 && (sort || page > 0 || Object.keys(modes).length > 0)) {
      // e.g. a sort column the catalog no longer returns or a removed join
      await showView(undefined, 0, {})
    } else if (outcome.rejection.status === 404) {
      state.value = 'not-found'
    } else {
      failure.value = rateLimitMessage(outcome.rejection)
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
  if (view.value.sort || view.value.page > 0 || Object.keys(view.value.modes).length > 0) {
    await showView(undefined, 0, {})
  } else {
    await loadRows()
  }
}

onMounted(loadCatalog)
watch(() => props.id, loadCatalog)
watch(
  () => [route.query.sort, route.query.page, route.query.mode],
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
          <div v-if="canConfigure" class="ms-auto flex gap-2">
            <UButton
              data-testid="configure-columns"
              icon="i-lucide-columns-3"
              color="neutral"
              variant="outline"
              :label="t('catalog.columns.configure')"
              @click="configuring = true"
            />
            <UButton
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
