<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import type { CatalogQueryResult, CatalogSort } from '../api/catalogs'
import { PageCursors, rejectsCursor } from '../api/cursors'
import { fetchPortalCatalogs, queryPortalCatalog } from '../api/portal'
import { queryTimeoutMessage, rateLimitMessage, RequestRejected } from '../api/problem'
import CatalogTable from '../components/CatalogTable.vue'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession } from '../session/store'

// A shared catalog read-only in the portal (SCR-050; design D7 of s9-portal-catalogs): the
// configured columns with paging and sorting, and no action that changes the catalog. Sort and
// page are part of the URL as on the catalog page (ADR-044), so reload and back navigation keep them.
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const name = ref<string>()
const metatype = ref<string>()
const state = ref<'loading' | 'loaded' | 'not-found' | 'failed'>('loading')
const result = ref<CatalogQueryResult>()
const failure = ref<string>()
const querying = ref(false)
// the path the page is shown at; query changes while navigating elsewhere are ignored
const path = route.path
let latestQuery = 0

const cursors = new PageCursors()

const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
const SORT = new RegExp(`^((?:${UUID}\\.|source\\.|target\\.)?[a-zA-Z][a-zA-Z0-9_]*):(asc|desc)$`)
const PAGE = /^\d+$/

interface View {
  sort?: CatalogSort
  page: number
  invalid: boolean
}

function viewOf(query: LocationQuery): View {
  let sort: CatalogSort | undefined
  let page = 0
  let invalid = false
  if (query.sort !== undefined) {
    const match = typeof query.sort === 'string' ? SORT.exec(query.sort) : null
    if (match) {
      sort = { column: match[1]!, direction: match[2] as CatalogSort['direction'] }
    } else {
      invalid = true
    }
  }
  if (query.page !== undefined) {
    if (typeof query.page === 'string' && PAGE.test(query.page) && Number.isSafeInteger(Number(query.page))) {
      page = Number(query.page)
    } else {
      invalid = true
    }
  }
  return { sort, page, invalid }
}

const view = computed(() => viewOf(route.query))

/** Writes sort and page to the URL without a history entry; defaults are omitted. */
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

/** Corrects the URL; before the first rows the watcher does not load, so this does. */
async function reset(sort: CatalogSort | undefined, page: number) {
  await showView(sort, page)
  if (state.value !== 'loaded') {
    await loadRows()
  }
}

async function loadRows() {
  const { sort, page, invalid } = view.value
  if (invalid) {
    await reset(undefined, 0)
    return
  }
  const current = ++latestQuery
  querying.value = true
  try {
    const key = JSON.stringify({ sort })
    const cursor = cursors.cursorFor(key, page)
    let outcome = await queryPortalCatalog(props.id, { page: page > 0 ? page : undefined, sort, cursor }, props.fetchFn)
    if (cursor && !outcome.ok && rejectsCursor(outcome.rejection) && current === latestQuery) {
      // the cursor is no longer valid, e.g. after a restart of the backend: the page number applies
      outcome = await queryPortalCatalog(props.id, { page, sort }, props.fetchFn)
    }
    if (current !== latestQuery) {
      return
    }
    if (outcome.ok) {
      cursors.remember(key, outcome.value)
      const meta = outcome.value.page
      if (meta.page > 0 && outcome.value.rows.length === 0 && meta.totalElements > 0) {
        await reset(sort, 0)
        return
      }
      result.value = outcome.value
      state.value = 'loaded'
    } else if (outcome.rejection.status === 400 && (sort || page > 0)) {
      // e.g. a sort column the catalog no longer returns
      await reset(undefined, 0)
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

async function load() {
  await refreshSession(props.fetchFn)
  state.value = 'loading'
  result.value = undefined
  failure.value = undefined
  cursors.forget()
  try {
    // the name and type come from the list of shared catalogs; the query has no catalog header
    const summary = (await fetchPortalCatalogs(props.fetchFn)).find((catalog) => catalog.id === props.id)
    if (!summary) {
      state.value = 'not-found'
      return
    }
    name.value = summary.name
    metatype.value = label(labelKeys.metatype(summary.primaryMetatypeId), summary.primaryMetatypeName)
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined)
    state.value = 'failed'
    return
  }
  await loadRows()
}

onMounted(load)
watch(() => props.id, load)
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
  <main class="flex flex-col gap-6 p-8">
    <RouterLink to="/portal" data-testid="back-to-portal" class="text-sm text-muted hover:text-default">
      ← {{ t('portal.back') }}
    </RouterLink>

    <UEmpty
      v-if="state === 'not-found'"
      data-testid="portal-catalog-not-found"
      icon="i-lucide-search-x"
      :title="t('catalog.notFound')"
      :description="t('portal.notFoundHint')"
      :actions="[{ label: t('portal.back'), to: '/portal' }]"
    />
    <UAlert
      v-else-if="state === 'failed'"
      data-testid="portal-catalog-failure"
      color="error"
      variant="subtle"
      :description="failure ?? t('catalog.loadFailed')"
    />
    <template v-else-if="name">
      <header class="flex flex-col gap-1">
        <h1 data-testid="portal-catalog-name" class="text-2xl font-semibold text-highlighted">{{ name }}</h1>
        <p class="text-muted">{{ metatype }}</p>
      </header>
      <CatalogTable
        v-if="result"
        :result="result"
        :loading="querying"
        :primary-label="metatype"
        read-only
        @sort="(sort) => showView(sort, 0)"
        @page="(page) => showView(view.sort, page)"
      />
    </template>
  </main>
</template>
