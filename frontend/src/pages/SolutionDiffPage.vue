<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rejectsCursor } from '../api/cursors'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import {
  fetchSolution,
  fetchSolutionDiff,
  type Solution,
  type SolutionDeltaCounts,
  type SolutionDeltaValue,
  type SolutionDiffEntry,
  type SolutionDiffField,
} from '../api/solutions'
import SolutionConflicts from '../components/solutions/SolutionConflicts.vue'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// SCR-062 "Solution comparison" (US-043, REQ-041; design D3 of us-043-diff-view): one card per
// delta with the current state of the entity in the catalog next to its target state after the
// solution, two columns, stacked on a narrow screen. New, modified and retiring entries, changed
// fields and drift since capture carry a label or an icon, never color alone. The diff loads on
// each visit; "Refresh" loads the first page again, "Load more" appends the next one. A card names
// the other active solutions that change the same entity (US-044; design D4 of
// us-044-conflict-warning).
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const PAGE_SIZE = 50

const solution = ref<Solution>()
const entries = ref<SolutionDiffEntry[]>([])
const counts = ref<SolutionDeltaCounts>({ new: 0, modified: 0, retiring: 0 })
const total = ref(0)
const page = ref(0)
const nextCursor = ref<string | null>(null)
const loaded = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const notFound = ref(false)
const failure = ref<string>()

const canRead = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:read'))
const solutionPath = computed(() => `/solutions/${encodeURIComponent(props.id)}`)

function fail(error: unknown) {
  const rejection = error instanceof RequestRejected ? error.rejection : undefined
  if (rejection?.status === 404) {
    notFound.value = true
  } else {
    failure.value = rateLimitMessage(rejection) ?? t('solutions.diff.loadFailed')
  }
}

async function refresh() {
  loading.value = true
  try {
    const first = await fetchSolutionDiff(props.id, { pageSize: PAGE_SIZE }, props.fetchFn)
    entries.value = first.entries
    counts.value = first.counts
    total.value = first.page.totalElements
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
      result = await fetchSolutionDiff(props.id, { page: next, pageSize: PAGE_SIZE, cursor: nextCursor.value }, props.fetchFn)
    } catch (error) {
      // the cursor is no longer valid, e.g. after a restart of the backend: the page number applies
      if (!(error instanceof RequestRejected && rejectsCursor(error.rejection))) throw error
      result = await fetchSolutionDiff(props.id, { page: next, pageSize: PAGE_SIZE }, props.fetchFn)
    }
    const known = new Set(entries.value.map((entry) => entry.deltaId))
    entries.value = [...entries.value, ...result.entries.filter((entry) => !known.has(entry.deltaId))]
    counts.value = result.counts
    total.value = result.page.totalElements
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
  if (!canRead.value) {
    loaded.value = true
    return
  }
  try {
    solution.value = await fetchSolution(props.id, props.fetchFn)
    await refresh()
  } catch (error) {
    fail(error)
  } finally {
    loaded.value = true
  }
})

function fieldLabel(field: SolutionDiffField): string {
  if (field.field === 'name') return t('solutions.deltas.name')
  if (field.field === 'description') return t('solutions.deltas.description')
  return field.propertyDefId ? label(labelKeys.property(field.propertyDefId), field.propertyName ?? '') : (field.propertyName ?? '')
}

function shown(value: SolutionDeltaValue | null | undefined): string {
  if (!value) return t('solutions.diff.noValue')
  const content = value.valueRef?.name ?? value.valueText ?? value.valueEnum ?? value.valueDate ?? value.valueInt ?? value.valueBool
  return content === undefined || content === null ? t('solutions.diff.noValue') : String(content)
}

function badge(entry: SolutionDiffEntry): { color: 'success' | 'info' | 'error'; icon: string } {
  if (entry.deltaType === 'new') return { color: 'success', icon: 'i-lucide-plus' }
  if (entry.deltaType === 'modified') return { color: 'info', icon: 'i-lucide-pencil' }
  return { color: 'error', icon: 'i-lucide-minus' }
}

function accent(entry: SolutionDiffEntry): string {
  if (entry.deltaType === 'new') return 'border-s-4 border-s-success'
  if (entry.deltaType === 'modified') return 'border-s-4 border-s-info'
  return 'border-s-4 border-s-error'
}

/** The notice of a column without a state: the entity does not exist yet, or no longer. */
function noState(entry: SolutionDiffEntry, side: 'current' | 'target'): string | undefined {
  if (side === 'current' && entry.deltaType === 'new') return t('solutions.diff.notYet')
  if (side === 'current' && !entry.inCatalog) return t('solutions.diff.noLongerInCatalog')
  return undefined
}

const sides = ['current', 'target'] as const
</script>

<template>
  <main class="mx-auto flex max-w-5xl flex-col gap-6 p-4 sm:p-8">
    <RouterLink :to="solutionPath" data-testid="diff-back" class="text-sm text-muted hover:text-default">
      ← {{ solution?.name ?? t('solutions.diff.back') }}
    </RouterLink>

    <UAlert
      v-if="session.me && !canRead"
      data-testid="solutions-no-access"
      color="neutral"
      variant="subtle"
      :description="t('solutions.noAccess')"
    />
    <UEmpty
      v-if="notFound"
      data-testid="diff-not-found"
      icon="i-lucide-search-x"
      :title="t('solutions.notFound')"
      :description="t('solutions.notFoundHint')"
    />

    <template v-if="solution">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex flex-col gap-1">
          <h1 data-testid="diff-title" class="text-2xl font-semibold text-highlighted">{{ t('solutions.diff.title') }}: {{ solution.name }}</h1>
          <p class="text-sm text-muted">{{ t('solutions.diff.intro') }}</p>
          <p data-testid="diff-counts" class="text-sm">{{ t('solutions.diff.counts', counts) }}</p>
        </div>
        <UButton
          data-testid="diff-refresh"
          color="neutral"
          variant="outline"
          icon="i-lucide-refresh-cw"
          :loading="loading"
          :label="t('solutions.diff.refresh')"
          @click="refresh"
        />
      </div>

      <UAlert v-if="failure" data-testid="diff-failure" color="error" variant="subtle" :description="failure" />

      <UEmpty
        v-if="loaded && !failure && total === 0"
        data-testid="diff-empty"
        icon="i-lucide-git-compare"
        :title="t('solutions.diff.empty')"
        :description="t('solutions.diff.emptyHint')"
      />

      <ol v-else class="flex flex-col gap-4" data-testid="diff-entries">
        <li v-for="entry in entries" :key="entry.deltaId">
          <article
            :data-testid="`diff-entry-${entry.deltaType}`"
            :aria-labelledby="`diff-${entry.deltaId}`"
            :class="['flex flex-col gap-3 rounded-lg border border-default bg-default p-4', accent(entry)]"
          >
            <header class="flex flex-wrap items-center gap-2">
              <UBadge
                data-testid="diff-type"
                :color="badge(entry).color"
                variant="subtle"
                :icon="badge(entry).icon"
                :label="t(`solutions.deltas.types.${entry.deltaType}`)"
              />
              <h2 :id="`diff-${entry.deltaId}`" class="font-semibold text-highlighted">{{ entry.name }}</h2>
              <span class="text-sm text-muted">{{ label(labelKeys.metatype(entry.metatypeId), entry.metatypeName) }}</span>
            </header>
            <SolutionConflicts :conflicts="entry.conflicts ?? []" />
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <section v-for="side in sides" :key="side" :data-testid="`diff-${side}`" class="flex flex-col gap-2">
                <h3 class="text-sm font-medium text-muted">{{ t(`solutions.diff.${side}`) }}</h3>
                <p v-if="noState(entry, side)" data-testid="diff-no-state" class="text-sm text-muted italic">
                  {{ noState(entry, side) }}
                </p>
                <template v-else>
                  <p
                    v-if="side === 'target' && entry.deltaType === 'retiring'"
                    data-testid="diff-out-of-service"
                    class="flex items-center gap-1 text-sm font-medium text-highlighted"
                  >
                    <UIcon name="i-lucide-archive-x" class="text-error" aria-hidden="true" />
                    {{ t('solutions.diff.outOfService') }}
                  </p>
                  <dl class="grid grid-cols-[minmax(0,10rem)_1fr] gap-x-3 gap-y-1 text-sm">
                    <template v-for="field in entry.fields" :key="`${side}-${field.field}-${field.propertyDefId}`">
                      <dt
                        :class="[
                          'break-words',
                          'text-muted',
                        ]"
                      >
                        {{ fieldLabel(field) }}
                      </dt>
                      <dd
                        :data-testid="`diff-field-${field.field === 'property' ? field.propertyName : field.field}`"
                        :data-changed="entry.deltaType === 'modified' && field.changed ? 'true' : undefined"
                        :class="[
                          'flex flex-col break-words',
                          entry.deltaType === 'modified' && field.changed ? 'rounded bg-info/10 px-1 font-semibold text-highlighted' : '',
                          entry.deltaType === 'modified' && !field.changed ? 'text-muted' : '',
                          side === 'target' && entry.deltaType === 'retiring' ? 'text-muted line-through' : '',
                        ]"
                      >
                        <span v-if="field.redacted" data-testid="diff-hidden" class="flex items-center gap-1 text-muted">
                          <UIcon name="i-lucide-eye-off" aria-hidden="true" />{{ t('solutions.diff.hidden') }}
                        </span>
                        <span v-else>
                          {{ shown(side === 'current' || entry.deltaType === 'retiring' ? field.current : field.target) }}
                          <span v-if="entry.deltaType === 'modified' && field.changed" class="sr-only">({{ t('solutions.diff.changed') }})</span>
                        </span>
                        <span
                          v-if="side === 'current' && field.catalogChanged"
                          data-testid="diff-drift"
                          class="flex items-center gap-1 text-xs font-normal text-default"
                        >
                          <UIcon name="i-lucide-triangle-alert" class="text-warning" aria-hidden="true" />{{ t('solutions.diff.drift') }}
                        </span>
                      </dd>
                    </template>
                  </dl>
                </template>
              </section>
            </div>
          </article>
        </li>
      </ol>

      <div v-if="total > 0" class="flex flex-wrap items-center justify-between gap-3">
        <p data-testid="diff-shown" class="text-sm text-muted">{{ t('solutions.diff.shown', { shown: entries.length, total }) }}</p>
        <UButton
          v-if="nextCursor"
          data-testid="diff-more"
          color="neutral"
          variant="outline"
          icon="i-lucide-chevrons-down"
          :loading="loadingMore"
          :label="t('solutions.diff.more')"
          @click="loadMore"
        />
      </div>
    </template>
  </main>
</template>
