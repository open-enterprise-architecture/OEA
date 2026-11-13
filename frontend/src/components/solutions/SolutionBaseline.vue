<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { PageCursors, rejectsCursor } from '../../api/cursors'
import { fetchMetatypes } from '../../api/metamodel'
import { rateLimitMessage } from '../../api/problem'
import { fetchSolutionLandscape, type SolutionLandscapeEntry, type SolutionLandscapePage } from '../../api/solutions'
import { label, labelKeys } from '../../i18n/labels'

// The area "Baseline" of SCR-061 (US-039; design D5 of us-039-solution-baseline): the current
// landscape the solution builds on, with a search, a metatype filter and paging. The next page
// follows by the cursor of the loaded one; a jump or a changed filter uses the page number. With
// solution:write, each row offers "Modify" and "Retire" (US-041, US-042), or shows "Modified" or
// "Retiring" when it has a delta.
const props = defineProps<{
  solutionId: string
  canWrite?: boolean
  deltaTypes?: Record<number, 'modified' | 'retiring'>
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{ retire: [entry: SolutionLandscapeEntry]; modify: [entry: SolutionLandscapeEntry] }>()
const { t } = useI18n()

// USelect does not accept an empty value, so "all metatypes" has its own value
const ALL = 'all'
const PAGE_SIZE = 50
const SEARCH_DELAY_MS = 300

const search = ref('')
const appliedSearch = ref('')
const metatype = ref(ALL)
const page = ref(0)
const result = ref<SolutionLandscapePage>()
const failure = ref<string>()
const loading = ref(false)
const metatypes = ref<{ label: string; value: string }[]>([])
const cursors = new PageCursors()
let latestQuery = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined

const filtered = computed(() => appliedSearch.value.trim().length > 0 || metatype.value !== ALL)
const metatypeItems = computed(() => [{ label: t('solutions.baseline.allMetatypes'), value: ALL }, ...metatypes.value])
const columns = computed(() => [
  { accessorKey: 'name', header: t('solutions.baseline.name') },
  { accessorKey: 'metatypeName', header: t('solutions.baseline.metatype') },
  ...(props.canWrite || Object.keys(props.deltaTypes ?? {}).length > 0 ? [{ id: 'delta', header: t('solutions.deltas.type') }] : []),
])
const rows = computed(() =>
  (result.value?.items ?? []).map((entry) => ({ ...entry, metatypeName: label(labelKeys.metatype(entry.metatypeId), entry.metatypeName) })),
)

async function load() {
  const current = ++latestQuery
  const query = { q: appliedSearch.value, metatypeId: metatype.value === ALL ? undefined : metatype.value, pageSize: PAGE_SIZE }
  const key = JSON.stringify(query)
  const cursor = cursors.cursorFor(key, page.value)
  loading.value = true
  try {
    let outcome = await fetchSolutionLandscape(props.solutionId, { ...query, page: page.value, cursor }, props.fetchFn)
    if (cursor && !outcome.ok && rejectsCursor(outcome.rejection) && current === latestQuery) {
      // the cursor is no longer valid, e.g. after a restart of the backend: the page number applies
      outcome = await fetchSolutionLandscape(props.solutionId, { ...query, page: page.value }, props.fetchFn)
    }
    if (current !== latestQuery) {
      return
    }
    if (outcome.ok) {
      cursors.remember(key, outcome.value)
      result.value = outcome.value
      failure.value = undefined
    } else {
      failure.value = rateLimitMessage(outcome.rejection) ?? t('solutions.baseline.loadFailed')
    }
  } finally {
    if (current === latestQuery) {
      loading.value = false
    }
  }
}

function showPage(next: number) {
  page.value = next
  void load()
}

watch(search, (value) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    appliedSearch.value = value
  }, SEARCH_DELAY_MS)
})

watch([appliedSearch, metatype], () => {
  cursors.forget()
  showPage(0)
})

onMounted(async () => {
  void load()
  try {
    metatypes.value = (await fetchMetatypes(props.fetchFn))
      .filter((candidate) => !candidate.isConnection)
      .map((candidate) => ({ label: label(labelKeys.metatype(candidate.id), candidate.name), value: candidate.id }))
      .sort((a, b) => a.label.localeCompare(b.label))
  } catch {
    // without the metatypes, the filter offers "all metatypes" only
  }
})

onBeforeUnmount(() => clearTimeout(searchTimer))
</script>

<template>
  <section class="flex flex-col gap-3" data-testid="solution-baseline">
    <div class="flex flex-col gap-1">
      <h2 class="text-lg font-semibold text-highlighted">{{ t('solutions.baseline.title') }}</h2>
      <p class="text-sm text-muted">{{ t('solutions.baseline.intro') }}</p>
    </div>
    <div class="flex flex-wrap gap-3">
      <UInput
        v-model="search"
        data-testid="baseline-search"
        icon="i-lucide-search"
        :placeholder="t('solutions.baseline.search')"
        :aria-label="t('solutions.baseline.search')"
        maxlength="255"
        class="min-w-56 flex-1"
      />
      <USelect
        v-model="metatype"
        data-testid="baseline-metatype"
        :items="metatypeItems"
        :aria-label="t('solutions.baseline.metatype')"
        class="min-w-48"
      />
    </div>
    <UAlert v-if="failure" data-testid="baseline-failure" color="error" variant="subtle" :description="failure" />
    <template v-else-if="result">
      <UAlert
        v-if="result.page.totalElements === 0 && !filtered"
        data-testid="baseline-empty-landscape"
        color="neutral"
        variant="subtle"
        icon="i-lucide-info"
        :description="t('solutions.baseline.emptyLandscape')"
      />
      <p v-else-if="result.page.totalElements === 0" data-testid="baseline-no-match" class="text-sm text-muted">
        {{ t('solutions.baseline.noMatch') }}
      </p>
      <template v-else>
        <UTable data-testid="baseline-table" :data="rows" :columns="columns" :loading="loading" class="rounded-lg border border-default">
          <template #delta-cell="{ row }">
            <div class="flex justify-end">
              <UBadge
                v-if="props.deltaTypes?.[row.original.id]"
                :data-testid="`baseline-${props.deltaTypes[row.original.id]}`"
                :color="props.deltaTypes[row.original.id] === 'modified' ? 'info' : 'warning'"
                variant="subtle"
                :label="t(`solutions.deltas.types.${props.deltaTypes[row.original.id]}`)"
              />
              <UButton
                v-if="!props.deltaTypes?.[row.original.id] && props.canWrite"
                data-testid="baseline-modify"
                color="neutral"
                variant="ghost"
                size="sm"
                icon="i-lucide-pencil"
                :label="t('solutions.baseline.modify')"
                @click="emit('modify', row.original)"
              />
              <UButton
                v-if="!props.deltaTypes?.[row.original.id] && props.canWrite"
                data-testid="baseline-retire"
                color="neutral"
                variant="ghost"
                size="sm"
                icon="i-lucide-archive-x"
                :label="t('solutions.baseline.retire')"
                @click="emit('retire', row.original)"
              />
            </div>
          </template>
        </UTable>
        <div class="flex items-center justify-between gap-4">
          <p data-testid="baseline-page-info" class="text-sm text-muted">
            {{ t('solutions.baseline.pageInfo', { page: result.page.page + 1, pages: result.page.totalPages, entries: result.page.totalElements }) }}
          </p>
          <UPagination
            v-if="result.page.totalPages > 1"
            data-testid="baseline-pagination"
            :page="result.page.page + 1"
            :items-per-page="result.page.pageSize"
            :total="result.page.totalElements"
            @update:page="(next: number) => showPage(next - 1)"
          />
        </div>
      </template>
    </template>
  </section>
</template>
