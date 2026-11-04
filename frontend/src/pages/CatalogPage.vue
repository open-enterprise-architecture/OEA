<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import { fetchCatalog, queryCatalog, type Catalog, type CatalogQueryResult, type CatalogSort } from '../api/catalogs'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { fetchPropertyDefinitions, type PropertyDefinitionDetail } from '../api/metamodel'
import CatalogTable from '../components/CatalogTable.vue'
import { label, labelKeys } from '../i18n/labels'

// A single catalog with its rows (UC-06, US-049, SCR-020). Sort and page are part of the URL
// (ADR-044, design D8 of us-049-show-catalog-data), so back navigation and reload restore the view.
// Metatype names, column headers and enum values are shown in the language of the user (US-140).
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const catalog = ref<Catalog>()
const state = ref<'loading' | 'loaded' | 'not-found' | 'failed'>('loading')
const result = ref<CatalogQueryResult>()
// the waiting time of a request above the rate limit (REQ-186), shown instead of the generic failure
const failure = ref<string>()
const querying = ref(false)
const definitions = ref<PropertyDefinitionDetail[]>([])
// the path the page is shown at; query changes while navigating elsewhere are ignored
const path = route.path
let latestQuery = 0

const SORT = /^([a-z][a-z0-9_]*):(asc|desc)$/
const PAGE = /^\d+$/

/** The view state of the URL; `invalid` lists the parameters that do not parse. */
function viewOf(query: LocationQuery): { sort?: CatalogSort; page: number; invalid: string[] } {
  const invalid: string[] = []
  let sort: CatalogSort | undefined
  let page = 0
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
  return { sort, page, invalid }
}

/** Writes the view to the URL without a history entry; defaults are omitted (ADR-044). */
async function showView(sort: CatalogSort | undefined, page: number) {
  const query: LocationQuery = { ...route.query }
  delete query.sort
  delete query.page
  if (sort) {
    query.sort = `${sort.column}:${sort.direction}`
  }
  if (page > 0) {
    query.page = String(page)
  }
  await router.replace({ query })
}

const view = computed(() => viewOf(route.query))

/** The rows with translated column headers and enum values (design D7 of us-140-dynamic-labels). */
const shown = computed<CatalogQueryResult | undefined>(() => {
  if (!result.value) {
    return undefined
  }
  const byColumn = new Map(definitions.value.map((definition) => [definition.sqlColumnName, definition]))
  return {
    ...result.value,
    columns: result.value.columns.map((column) => {
      const definition = byColumn.get(column.key)
      if (column.key === 'name') {
        return { ...column, label: t('catalog.nameColumn') }
      }
      return definition ? { ...column, label: label(labelKeys.property(definition.id), column.label) } : column
    }),
    rows: result.value.rows.map((row) => ({
      ...row,
      values: Object.fromEntries(
        Object.entries(row.values).map(([key, value]) => {
          const enumValue = byColumn.get(key)?.enumValues?.find((candidate) => candidate.value === value)
          return [key, enumValue && value ? label(labelKeys.enumValue(enumValue.id), value) : value]
        }),
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
  const { sort, page, invalid } = view.value
  if (invalid.length > 0) {
    await showView(undefined, 0)
    return
  }
  const current = ++latestQuery
  querying.value = true
  try {
    const outcome = await queryCatalog(props.id, { page: page > 0 ? page : undefined, sort }, props.fetchFn)
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
    } else if (outcome.rejection.status === 400 && (sort || page > 0)) {
      // e.g. a sort column the catalog no longer returns
      await showView(undefined, 0)
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

onMounted(loadCatalog)
watch(() => props.id, loadCatalog)
watch(
  () => [route.query.sort, route.query.page],
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
        <div class="flex items-center gap-3">
          <h1 data-testid="catalog-title" class="text-2xl font-semibold text-highlighted">{{ catalog.name }}</h1>
          <UBadge
            data-testid="catalog-scope"
            :color="catalog.scope === 'personal' ? 'neutral' : 'primary'"
            variant="subtle"
            :label="catalog.scope === 'personal' ? t('catalogs.personal') : t('catalogs.shared')"
          />
        </div>
        <p data-testid="catalog-type" class="text-muted">{{ metatypeName }}</p>
        <p v-if="catalog.description" class="text-default">{{ catalog.description }}</p>
      </header>

      <CatalogTable
        v-if="shown"
        :result="shown"
        :loading="querying"
        @sort="(sort) => showView(sort, 0)"
        @page="(page) => showView(view.sort, page)"
      />
    </template>
  </main>
</template>
