<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { rejectsCursor } from '../api/cursors'
import {
  compareEntityVersions,
  fetchEntityHistory,
  fetchEntityVersion,
  type EntityHistory,
  type EntityVersion,
  type EntityVersionChange,
  type EntityVersionDiff,
  type EntityVersionState,
  type HistoryQuery,
} from '../api/history'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import VersionDiff from '../components/history/VersionDiff.vue'
import VersionState from '../components/history/VersionState.vue'
import { currentLanguage } from '../i18n'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// SCR-026 "Change history" (US-098, REQ-098; design D5 of us-098-entity-history): the versions of
// an entity newest first, each with its number, local time, person and the labels of the fields
// it changed, without values. A field the reader may not read carries the badge "Restricted"; the
// creation is "Created". Filters by period and person; "Load more" appends the next page. The
// back link returns to the catalog the page was opened from (query parameter `from`).
// Values (US-099, US-100; design D5 of us-099-version-diff): a version expands to its diff
// against its predecessor and offers its complete state; "Compare" selects two versions and shows
// their net diff in a panel. Versions are immutable, so diffs and states are loaded once.
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const { t } = useI18n()
const route = useRoute()

const PAGE_SIZE = 50
const ALL = '-'

const history = ref<EntityHistory>()
const versions = ref<EntityVersion[]>([])
const page = ref(0)
const nextCursor = ref<string | null>(null)
const loaded = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const notFound = ref(false)
const failure = ref<string>()
const filter = reactive({ actor: ALL, from: '', to: '' })
const expanded = reactive<Record<number, boolean>>({})
const diffs = reactive<Record<number, EntityVersionDiff>>({})
const states = reactive<Record<number, EntityVersionState>>({})
const shownStates = reactive<Record<number, boolean>>({})
const detailFailures = reactive<Record<number, string>>({})
const comparing = ref(false)
const selected = ref<number[]>([])
const comparison = ref<EntityVersionDiff>()
const compareFailure = ref<string>()

const entityId = computed(() => Number(props.id))
const canRead = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:read'))
const filtered = computed(() => filter.actor !== ALL || filter.from !== '' || filter.to !== '')
const periodInvalid = computed(() => filter.from !== '' && filter.to !== '' && filter.from > filter.to)
const total = computed(() => history.value?.page.totalElements ?? 0)
// only the creation exists: the empty state names it (US-098 AC3)
const unchanged = computed(() => !filtered.value && total.value === 1 && versions.value[0]?.version === 1)

/** The catalog the page was opened from; only paths of this application. */
const back = computed(() => {
  const from = route.query.from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/catalogs'
})

const actorItems = computed(() => [
  { label: t('history.allPersons'), value: ALL },
  ...(history.value?.actors ?? []).map((actor) => ({ label: actor.name, value: String(actor.id) })),
])

/** The start of the `from` day and the end of the `to` day in the local time zone, as instants. */
function query(): HistoryQuery {
  return {
    actorId: filter.actor !== ALL ? Number(filter.actor) : undefined,
    from: filter.from ? new Date(`${filter.from}T00:00:00`).toISOString() : undefined,
    to: filter.to ? new Date(`${filter.to}T23:59:59.999`).toISOString() : undefined,
    pageSize: PAGE_SIZE,
  }
}

function fail(error: unknown) {
  const rejection = error instanceof RequestRejected ? error.rejection : undefined
  if (rejection?.status === 404) {
    notFound.value = true
  } else {
    failure.value = rateLimitMessage(rejection) ?? t('history.loadFailed')
  }
}

async function load() {
  if (periodInvalid.value) return
  loading.value = true
  try {
    const first = await fetchEntityHistory(entityId.value, query(), props.fetchFn)
    history.value = first
    versions.value = first.versions
    page.value = 0
    nextCursor.value = first.nextCursor
    failure.value = undefined
  } catch (error) {
    fail(error)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextCursor.value) return
  loadingMore.value = true
  const next = page.value + 1
  try {
    let result
    try {
      result = await fetchEntityHistory(entityId.value, { ...query(), page: next, cursor: nextCursor.value }, props.fetchFn)
    } catch (error) {
      // the cursor is no longer valid, e.g. after a restart of the backend: the page number applies
      if (!(error instanceof RequestRejected && rejectsCursor(error.rejection))) throw error
      result = await fetchEntityHistory(entityId.value, { ...query(), page: next }, props.fetchFn)
    }
    const known = new Set(versions.value.map((version) => version.version))
    versions.value = [...versions.value, ...result.versions.filter((version) => !known.has(version.version))]
    history.value = result
    page.value = next
    nextCursor.value = result.nextCursor
    failure.value = undefined
  } catch (error) {
    fail(error)
  } finally {
    loadingMore.value = false
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (canRead.value) {
    await load()
  }
  loaded.value = true
})

watch(filter, () => load())

function detailMessage(error: unknown): string {
  return rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('history.detailFailed')
}

/** Expands or collapses a version; its diff against the predecessor is loaded once. */
async function toggle(version: number) {
  expanded[version] = !expanded[version]
  if (!expanded[version] || version < 2 || diffs[version]) return
  delete detailFailures[version]
  try {
    diffs[version] = await compareEntityVersions(entityId.value, version - 1, version, props.fetchFn)
  } catch (error) {
    detailFailures[version] = detailMessage(error)
  }
}

/** Shows or hides the complete state of a version; it is loaded once. */
async function toggleState(version: number) {
  shownStates[version] = !shownStates[version]
  if (!shownStates[version] || states[version]) return
  delete detailFailures[version]
  try {
    states[version] = await fetchEntityVersion(entityId.value, version, props.fetchFn)
  } catch (error) {
    detailFailures[version] = detailMessage(error)
  }
}

/** Selects a version for the comparison; a third one replaces the version selected first. */
function select(version: number, checked: boolean) {
  const next = selected.value.filter((candidate) => candidate !== version)
  if (checked) next.push(version)
  if (next.length > 2) next.shift()
  selected.value = next
}

const range = computed(() => {
  if (selected.value.length < 2) return undefined
  const [from, to] = [...selected.value].sort((a, b) => a - b)
  return { from: from!, to: to! }
})

watch(range, async (current) => {
  comparison.value = undefined
  compareFailure.value = undefined
  if (!current) return
  try {
    const result = await compareEntityVersions(entityId.value, current.from, current.to, props.fetchFn)
    // a later selection may have replaced this one meanwhile
    if (range.value?.from === current.from && range.value?.to === current.to) comparison.value = result
  } catch (error) {
    compareFailure.value = detailMessage(error)
  }
})

watch(comparing, (active) => {
  if (!active) selected.value = []
})

function fieldLabel(change: EntityVersionChange): string {
  if (change.isSystemField) return t(`history.fields.${change.field}`)
  return label(labelKeys.property(change.field), change.label)
}

function time(value: string): string {
  return new Intl.DateTimeFormat(currentLanguage(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

/** The local date as YYYY-MM-DD. */
function day(value: string): string {
  const date = new Date(value)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
</script>

<template>
  <main class="mx-auto flex max-w-4xl flex-col gap-6 p-4 sm:p-8">
    <RouterLink :to="back" data-testid="history-back" class="text-sm text-muted hover:text-default">
      ← {{ t('history.back') }}
    </RouterLink>

    <UAlert
      v-if="session.me && !canRead"
      data-testid="history-no-access"
      color="neutral"
      variant="subtle"
      :description="t('history.noAccess')"
    />
    <UEmpty
      v-if="notFound"
      data-testid="history-not-found"
      icon="i-lucide-search-x"
      :title="t('history.notFound')"
      :description="t('history.notFoundHint')"
    />
    <UAlert v-if="failure && !history" data-testid="history-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="history">
      <div class="flex flex-col gap-1">
        <h1 data-testid="history-title" class="text-2xl font-semibold text-highlighted">{{ t('history.title') }}: {{ history.entityName }}</h1>
        <p class="text-sm text-muted">{{ t('history.intro') }}</p>
      </div>

      <div class="flex flex-wrap items-end gap-3" data-testid="history-filters">
        <UFormField :label="t('history.person')">
          <USelect v-model="filter.actor" data-testid="history-person" :aria-label="t('history.person')" :items="actorItems" class="w-56" />
        </UFormField>
        <UFormField :label="t('history.from')">
          <UInput v-model="filter.from" data-testid="history-from" type="date" />
        </UFormField>
        <UFormField :label="t('history.to')" :error="periodInvalid ? t('history.periodInvalid') : undefined">
          <UInput v-model="filter.to" data-testid="history-to" type="date" />
        </UFormField>
        <UButton
          v-if="!unchanged"
          data-testid="history-compare"
          class="ms-auto"
          color="neutral"
          :variant="comparing ? 'solid' : 'outline'"
          icon="i-lucide-git-compare"
          :aria-pressed="comparing"
          :label="comparing ? t('history.compareDone') : t('history.compare')"
          @click="comparing = !comparing"
        />
      </div>

      <UCard v-if="comparing" data-testid="history-compare-panel">
        <template #header>
          <h2 class="font-semibold text-highlighted">
            {{ range ? t('history.compareTitle', { from: range.from, to: range.to }) : t('history.compare') }}
          </h2>
        </template>
        <p v-if="!range" data-testid="history-compare-hint" class="text-sm text-muted">{{ t('history.compareHint') }}</p>
        <UAlert v-else-if="compareFailure" data-testid="history-compare-failure" color="error" variant="subtle" :description="compareFailure" />
        <VersionDiff v-else-if="comparison" :changes="comparison.changes" />
      </UCard>

      <UAlert v-if="failure" data-testid="history-failure" color="error" variant="subtle" :description="failure" />

      <UEmpty
        v-if="unchanged && history.created"
        data-testid="history-unchanged"
        icon="i-lucide-history"
        :title="t('history.unchanged', { date: day(history.created.changedAt), person: history.created.changedBy.name })"
      />
      <UEmpty
        v-else-if="loaded && total === 0"
        data-testid="history-empty"
        icon="i-lucide-filter-x"
        :title="t('history.noMatch')"
      />

      <ol v-else class="flex flex-col gap-3 border-s-2 border-default ps-4" data-testid="history-versions">
        <li v-for="version in versions" :key="version.version">
          <article :data-testid="`history-version-${version.version}`" class="flex flex-col gap-2 rounded-lg border border-default bg-default p-4">
            <header class="flex flex-wrap items-center gap-x-3 gap-y-1">
              <UCheckbox
                v-if="comparing"
                :data-testid="`history-select-${version.version}`"
                :model-value="selected.includes(version.version)"
                :aria-label="t('history.select', { version: version.version })"
                @update:model-value="(checked: boolean | 'indeterminate') => select(version.version, checked === true)"
              />
              <h2 class="font-semibold text-highlighted">
                {{ version.version === 1 ? t('history.created') : t('history.version', { version: version.version }) }}
              </h2>
              <time data-testid="history-time" :datetime="version.changedAt" class="text-sm text-muted">{{ time(version.changedAt) }}</time>
              <span data-testid="history-actor" class="text-sm">{{ version.changedBy.name }}</span>
              <UButton
                :data-testid="`history-toggle-${version.version}`"
                class="ms-auto"
                color="neutral"
                variant="ghost"
                size="xs"
                :icon="expanded[version.version] ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                :aria-expanded="expanded[version.version] === true"
                :aria-controls="`history-details-${version.version}`"
                :label="expanded[version.version] ? t('history.hideDetails') : t('history.details')"
                @click="toggle(version.version)"
              />
            </header>
            <ul v-if="version.changes.length > 0" class="flex flex-wrap gap-2" :aria-label="t('history.changedFields')">
              <li v-for="change in version.changes" :key="change.field" :data-testid="`history-change-${change.field}`">
                <UBadge
                  v-if="change.restricted"
                  data-testid="history-restricted"
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-lock"
                  :label="`${fieldLabel(change)} · ${t('history.restricted')}`"
                />
                <UBadge v-else color="neutral" variant="subtle" :label="fieldLabel(change)" />
              </li>
            </ul>
            <p v-else-if="version.version > 1" data-testid="history-no-fields" class="text-sm text-muted italic">{{ t('history.noFields') }}</p>
            <div
              v-if="expanded[version.version]"
              :id="`history-details-${version.version}`"
              :data-testid="`history-details-${version.version}`"
              class="flex flex-col gap-3 border-t border-default pt-3"
            >
              <UAlert
                v-if="detailFailures[version.version]"
                data-testid="history-detail-failure"
                color="error"
                variant="subtle"
                :description="detailFailures[version.version]"
              />
              <VersionDiff v-if="version.version > 1 && diffs[version.version]" :changes="diffs[version.version]!.changes" />
              <div>
                <UButton
                  :data-testid="`history-show-state-${version.version}`"
                  color="neutral"
                  variant="link"
                  icon="i-lucide-file-text"
                  :label="shownStates[version.version] ? t('history.hideState') : t('history.showState', { version: version.version })"
                  @click="toggleState(version.version)"
                />
              </div>
              <VersionState v-if="shownStates[version.version] && states[version.version]" :state="states[version.version]!" />
            </div>
          </article>
        </li>
      </ol>

      <div v-if="total > 0 && !unchanged" class="flex flex-wrap items-center justify-between gap-3">
        <p data-testid="history-shown" class="text-sm text-muted">{{ t('history.shown', { shown: versions.length, total }) }}</p>
        <UButton
          v-if="nextCursor"
          data-testid="history-more"
          color="neutral"
          variant="outline"
          icon="i-lucide-chevrons-down"
          :loading="loadingMore"
          :label="t('history.more')"
          @click="loadMore"
        />
      </div>
    </template>
  </main>
</template>
