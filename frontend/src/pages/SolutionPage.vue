<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rejectsCursor } from '../api/cursors'
import { fetchPlateaus, type Plateau } from '../api/plateaus'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import {
  createSolutionDelta,
  fetchGoLivePreview,
  fetchSolution,
  fetchSolutionDeltas,
  fetchSolutionOpenIssues,
  goLiveSolution,
  type Solution,
  type SolutionDelta,
  type SolutionGoLivePreview,
  type SolutionLandscapeEntry,
  type SolutionOpenIssue,
} from '../api/solutions'
import OpenIssuesDialog from '../components/solutions/OpenIssuesDialog.vue'
import DeltaMergeDialog from '../components/solutions/DeltaMergeDialog.vue'
import SolutionBaseline from '../components/solutions/SolutionBaseline.vue'
import SolutionDeltas from '../components/solutions/SolutionDeltas.vue'
import SolutionModifyDialog from '../components/solutions/SolutionModifyDialog.vue'
import { currentLanguage } from '../i18n'
import { refreshSession, session } from '../session/store'

// SCR-061 "Solution" (UC-05, US-038, US-039): name, status, owner, creation time and the
// architecture vision of one solution (design D4 of us-038-create-solution), and its baseline
// (design D5 of us-039-solution-baseline), and its entity deltas (US-040, US-042; design D9 of
// us-040-entity-deltas): the area "Changes" and "Retire" in the baseline, for solution:write while
// the solution is not completed; "Modify" in the baseline and "Edit" in the list open the dialog
// "Modify entity" (US-041; design D6 of us-041-modified-delta). The deltas load page by page
// ("Load more"); after a write, the first page and the baseline load again, whose entries carry
// the type of their delta (design D0 of us-043-diff-view). The header warns when changes concern
// other active solutions, and saving a delta that conflicts names them in a notice above the list
// until it is closed (US-044; design D4 of us-044-conflict-warning). "Go live" (US-152; design D6
// of us-152-solution-go-live) loads the preview: with field conflicts it opens the merge dialog
// SCR-063 first, then the confirmation with the counts; after the go-live the page shows the
// status `implemented` and the open issues.
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const solution = ref<Solution>()
// the plateaus of a solution in plateau mode, for the header and the basis of the baseline (us-084 D8)
const plateaus = ref<Plateau[]>([])
const fromPlateau = computed(() => plateaus.value.find((plateau) => plateau.id === solution.value?.fromPlateauId))
const toPlateau = computed(() => plateaus.value.find((plateau) => plateau.id === solution.value?.toPlateauId))
const basis = computed(() => {
  if (!solution.value?.toPlateauId) return undefined
  if (toPlateau.value?.basis === 'predecessor') return t('solutions.plateaus.basisPredecessor', { name: fromPlateau.value?.name ?? '' })
  return toPlateau.value ? t('solutions.plateaus.basisCatalog') : undefined
})
const loaded = ref(false)
const notFound = ref(false)
const failure = ref<string>()

const DELTA_PAGE_SIZE = 50

const deltas = ref<SolutionDelta[]>([])
const deltaTotal = ref(0)
const conflicted = ref(0)
// the delta just saved whose entity other active solutions change too
const savedConflict = ref<SolutionDelta>()
const deltaPage = ref(0)
const deltaCursor = ref<string | null>(null)
const loadingMore = ref(false)
const baselineKey = ref(0)
const deltasFailure = ref<string>()
const completed = ref(false)
const preview = ref<SolutionGoLivePreview>()
const merging = ref(false)
const confirming = ref(false)
const goingLive = ref(false)
const goLiveFailure = ref<string>()
const openIssues = ref<SolutionOpenIssue[]>([])
const wentLive = ref(false)

const canRead = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:read'))
// resolving open issues needs solution:write or entity:write (US-153; owner decision 2026-11-17)
const canResolve = computed(() =>
  (session.me?.permissions ?? []).some((entry) => entry.permission === 'solution:write' || entry.permission === 'entity:write'),
)
const resolving = ref(false)
const pendingIssues = computed(() => openIssues.value.filter((issue) => issue.status === 'open'))
// values only a snapshot keeps are shown once in the merge dialog; "Resolve later" goes live
const snapshotsReviewed = ref(false)
const canWrite = computed(
  () =>
    (session.me?.permissions ?? []).some((entry) => entry.permission === 'solution:write') &&
    !completed.value &&
    solution.value !== undefined &&
    solution.value.status !== 'implemented' &&
    solution.value.status !== 'archived',
)
// the entity the dialog "Modify entity" is open for, with the delta it edits
const modifying = ref<{ entityId: number; delta?: SolutionDelta }>()
const modifyOpen = computed({
  get: () => modifying.value !== undefined,
  set: (open: boolean) => {
    if (!open) modifying.value = undefined
  },
})

async function onModified(delta: SolutionDelta) {
  modifying.value = undefined
  deltasFailure.value = undefined
  await onChanged(delta)
}

/**
 * After a write: the first page of the deltas and the shown page of the baseline load again; a
 * saved delta with conflicts is named in the notice.
 */
async function onChanged(delta?: SolutionDelta) {
  savedConflict.value = delta && delta.conflicts.length > 0 ? delta : undefined
  baselineKey.value += 1
  await loadDeltas()
}

const savedConflictText = computed(() =>
  savedConflict.value
    ? t('solutions.conflicts.saved', {
        name: savedConflict.value.name,
        solutions: savedConflict.value.conflicts.map((conflict) => conflict.solutionName).join(', '),
      })
    : '',
)

async function loadDeltas() {
  try {
    const first = await fetchSolutionDeltas(props.id, { pageSize: DELTA_PAGE_SIZE }, props.fetchFn)
    deltas.value = first.items
    deltaTotal.value = first.page.totalElements
    conflicted.value = first.conflicted
    deltaPage.value = 0
    deltaCursor.value = first.nextCursor
    deltasFailure.value = undefined
  } catch (error) {
    const rejection = error instanceof RequestRejected ? error.rejection : undefined
    deltasFailure.value = rateLimitMessage(rejection) ?? t('solutions.deltas.loadFailed')
  }
}

async function loadMore() {
  if (!deltaCursor.value) return
  loadingMore.value = true
  const page = deltaPage.value + 1
  try {
    let next
    try {
      next = await fetchSolutionDeltas(props.id, { page, pageSize: DELTA_PAGE_SIZE, cursor: deltaCursor.value }, props.fetchFn)
    } catch (error) {
      // the cursor is no longer valid, e.g. after a restart of the backend: the page number applies
      if (!(error instanceof RequestRejected && rejectsCursor(error.rejection))) throw error
      next = await fetchSolutionDeltas(props.id, { page, pageSize: DELTA_PAGE_SIZE }, props.fetchFn)
    }
    const known = new Set(deltas.value.map((delta) => delta.id))
    deltas.value = [...deltas.value, ...next.items.filter((delta) => !known.has(delta.id))]
    deltaTotal.value = next.page.totalElements
    conflicted.value = next.conflicted
    deltaPage.value = page
    deltaCursor.value = next.nextCursor
    deltasFailure.value = undefined
  } catch (error) {
    const rejection = error instanceof RequestRejected ? error.rejection : undefined
    deltasFailure.value = rateLimitMessage(rejection) ?? t('solutions.deltas.loadFailed')
  } finally {
    loadingMore.value = false
  }
}

async function retire(entry: SolutionLandscapeEntry) {
  const outcome = await createSolutionDelta(props.id, { deltaType: 'retiring', entityId: entry.id! }, props.fetchFn)
  if (outcome.ok) {
    deltasFailure.value = undefined
    await onChanged(outcome.value)
  } else if (outcome.rejection.status === 409) {
    onCompleted()
  } else {
    deltasFailure.value = rateLimitMessage(outcome.rejection) ?? outcome.rejection.detail ?? t('solutions.deltas.createFailed')
  }
}

/** Loads the preview; field conflicts open the merge dialog, otherwise the confirmation. */
async function startGoLive() {
  goLiveFailure.value = undefined
  try {
    preview.value = await fetchGoLivePreview(props.id, props.fetchFn)
  } catch (error) {
    const rejection = error instanceof RequestRejected ? error.rejection : undefined
    if (rejection?.status === 409) {
      onCompleted()
    } else {
      goLiveFailure.value = rateLimitMessage(rejection) ?? t('solutions.goLive.failed')
    }
    return
  }
  const fieldConflicts = preview.value.conflicts.filter((conflict) => conflict.kind !== 'not-in-metamodel')
  const snapshotValues = preview.value.conflicts.length - fieldConflicts.length
  if (fieldConflicts.length > 0 || (snapshotValues > 0 && !snapshotsReviewed.value)) {
    merging.value = true
  } else {
    confirming.value = true
  }
}

async function onMerged() {
  merging.value = false
  snapshotsReviewed.value = true
  await loadDeltas()
  await startGoLive()
}

async function confirmGoLive() {
  goingLive.value = true
  goLiveFailure.value = undefined
  try {
    const outcome = await goLiveSolution(props.id, props.fetchFn)
    if (outcome.ok) {
      confirming.value = false
      wentLive.value = true
      openIssues.value = outcome.value.openIssues
      solution.value = await fetchSolution(props.id, props.fetchFn)
      baselineKey.value += 1
      await loadDeltas()
    } else if (outcome.rejection.problemType === 'merge-required') {
      confirming.value = false
      await startGoLive()
    } else if (outcome.rejection.problemType === 'solution-completed') {
      confirming.value = false
      onCompleted()
    } else {
      goLiveFailure.value =
        rateLimitMessage(outcome.rejection) ??
        (outcome.rejection.problemType === 'entity-deleted'
          ? t('solutions.goLive.entityDeleted')
          : outcome.rejection.problemType === 'entity-blocked'
            ? t('solutions.goLive.entityBlocked')
            : t('solutions.goLive.failed'))
    }
  } finally {
    goingLive.value = false
  }
}

async function onIssuesResolved(issues: SolutionOpenIssue[]) {
  resolving.value = false
  openIssues.value = issues
  solution.value = await fetchSolution(props.id, props.fetchFn)
}

function onCompleted() {
  completed.value = true
  deltasFailure.value = t('solutions.deltas.completed')
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  try {
    solution.value = await fetchSolution(props.id, props.fetchFn)
    if (solution.value.toPlateauId) {
      // without a permission to read plateaus, the header names the mode only
      plateaus.value = await fetchPlateaus(props.fetchFn).catch(() => [])
    }
    await loadDeltas()
    if (solution.value.status === 'implemented' || solution.value.openIssueCount > 0) {
      openIssues.value = await fetchSolutionOpenIssues(props.id, props.fetchFn)
    }
  } catch (error) {
    const rejection = error instanceof RequestRejected ? error.rejection : undefined
    if (rejection?.status === 404) {
      notFound.value = true
    } else {
      failure.value = rateLimitMessage(rejection) ?? t('solutions.loadOneFailed')
    }
  } finally {
    loaded.value = true
  }
})

const createdAt = computed(() =>
  solution.value ? new Date(solution.value.createdAt).toLocaleString(currentLanguage(), { dateStyle: 'medium', timeStyle: 'short' }) : '',
)
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <RouterLink to="/solutions" data-testid="solution-back" class="text-sm text-muted hover:text-default">
      ← {{ t('solutions.title') }}
    </RouterLink>

    <UAlert
      v-if="session.me && !canRead"
      data-testid="solutions-no-access"
      color="neutral"
      variant="subtle"
      :description="t('solutions.noAccess')"
    />
    <UAlert v-if="failure" data-testid="solution-failure" color="error" variant="subtle" :description="failure" />
    <UEmpty
      v-if="notFound"
      data-testid="solution-not-found"
      icon="i-lucide-search-x"
      :title="t('solutions.notFound')"
      :description="t('solutions.notFoundHint')"
    />

    <template v-if="solution">
      <div class="flex flex-wrap items-center gap-3">
        <h1 data-testid="solution-title" class="text-2xl font-semibold text-highlighted">{{ solution.name }}</h1>
        <UBadge
          data-testid="solution-status"
          :color="solution.status === 'draft' ? 'neutral' : 'primary'"
          variant="subtle"
          :label="t(`solutions.status.${solution.status}`)"
        />
        <UButton
          v-if="canWrite && !solution.toPlateauId"
          data-testid="solution-go-live"
          class="ms-auto"
          icon="i-lucide-rocket"
          :label="t('solutions.goLive.action')"
          @click="startGoLive"
        />
        <!-- a solution of a plateau goes live with its plateau (US-157 AC3) -->
        <UButton
          v-if="solution.toPlateauId && solution.status !== 'implemented' && solution.status !== 'archived'"
          data-testid="solution-goes-live-with-plateau"
          class="ms-auto"
          to="/plateaus"
          color="neutral"
          variant="link"
          icon="i-lucide-layers"
          :label="toPlateau ? t('solutions.plateaus.goesLiveWith', { name: toPlateau.name }) : t('solutions.plateaus.goesLiveWithPlateau')"
        />
      </div>
      <UAlert v-if="goLiveFailure" data-testid="go-live-failure" color="error" variant="subtle" :description="goLiveFailure" />
      <UAlert
        v-if="wentLive"
        data-testid="go-live-done"
        color="success"
        variant="subtle"
        icon="i-lucide-circle-check"
        :description="t('solutions.goLive.done')"
      />
      <section v-if="openIssues.length > 0" class="flex flex-col gap-2" data-testid="solution-open-issues">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h2 class="text-lg font-semibold text-highlighted">{{ t('solutions.goLive.openIssues.title', { n: pendingIssues.length }) }}</h2>
          <UButton
            v-if="canResolve && pendingIssues.length > 0"
            data-testid="open-issues-resolve"
            icon="i-lucide-wrench"
            :label="t('solutions.openIssues.action')"
            @click="resolving = true"
          />
        </div>
        <p class="text-sm text-muted">{{ t('solutions.goLive.openIssues.intro') }}</p>
        <ul class="flex flex-col gap-1 text-sm">
          <li v-for="issue in openIssues" :key="issue.id" :data-testid="`open-issue-${issue.id}`" class="flex flex-wrap items-center gap-2">
            <span class="font-medium text-highlighted">{{ issue.entityName }}</span>
            <span>{{ issue.label }}</span>
            <UBadge color="warning" variant="subtle" :label="t(`solutions.goLive.openIssues.kind.${issue.kind}`)" />
            <UBadge
              :data-testid="`open-issue-status-${issue.id}`"
              :color="issue.status === 'open' ? 'error' : 'success'"
              variant="outline"
              :label="
                issue.status === 'open' ? t('solutions.openIssues.status.open') : t(`solutions.openIssues.resolution.${issue.resolution}`)
              "
            />
          </li>
        </ul>
      </section>
      <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
        <dt class="text-muted">{{ t('solutions.owner') }}</dt>
        <dd data-testid="solution-owner">{{ solution.ownerName }}</dd>
        <dt class="text-muted">{{ t('solutions.createdAt') }}</dt>
        <dd data-testid="solution-created">{{ createdAt }}</dd>
        <dt class="text-muted">{{ t('solutions.mode') }}</dt>
        <dd v-if="solution.toPlateauId" data-testid="solution-plateaus">
          {{
            fromPlateau && toPlateau
              ? t('solutions.plateaus.mode', { from: fromPlateau.name, to: toPlateau.name })
              : t('solutions.plateaus.modeOnly')
          }}
        </dd>
        <dd v-else>{{ t('solutions.projectMode') }}</dd>
      </dl>
      <UAlert
        v-if="conflicted > 0"
        data-testid="solution-conflicts"
        color="warning"
        variant="subtle"
        icon="i-lucide-triangle-alert"
        :description="t('solutions.conflicts.notice', { n: conflicted }, conflicted)"
      />
      <section class="flex flex-col gap-2">
        <h2 class="text-lg font-semibold text-highlighted">{{ t('solutions.vision') }}</h2>
        <p v-if="solution.description" data-testid="solution-vision" class="whitespace-pre-line">{{ solution.description }}</p>
        <p v-else data-testid="solution-no-vision" class="text-muted">{{ t('solutions.noVision') }}</p>
      </section>
      <UAlert
        v-if="savedConflict"
        data-testid="delta-saved-conflict"
        color="warning"
        variant="subtle"
        icon="i-lucide-triangle-alert"
        :description="savedConflictText"
        close
        @update:open="(open: boolean) => !open && (savedConflict = undefined)"
      />
      <SolutionDeltas
        :solution-id="solution.id"
        :deltas="deltas"
        :total="deltaTotal"
        :has-more="deltaCursor !== null"
        :loading-more="loadingMore"
        :can-write="canWrite"
        :failure="deltasFailure"
        :fetch-fn="props.fetchFn"
        @changed="onChanged"
        @more="loadMore"
        @completed="onCompleted"
        @edit="(delta: SolutionDelta) => (modifying = { entityId: delta.entityId!, delta })"
      />
      <SolutionBaseline
        :solution-id="solution.id"
        :can-write="canWrite"
        :reload-key="baselineKey"
        :basis="basis"
        :fetch-fn="props.fetchFn"
        @retire="retire"
        @modify="(entry: SolutionLandscapeEntry) => (modifying = { entityId: entry.id! })"
      />
      <UModal
        :open="resolving"
        :title="t('solutions.openIssues.title')"
        @update:open="(open: boolean) => !open && (resolving = false)"
      >
        <template #body>
          <OpenIssuesDialog
            v-if="resolving"
            :solution-id="solution.id"
            :issues="pendingIssues"
            :fetch-fn="props.fetchFn"
            @resolved="onIssuesResolved"
            @cancel="resolving = false"
          />
        </template>
      </UModal>
      <UModal
        :open="merging"
        :title="t('solutions.goLive.merge.title')"
        @update:open="(open: boolean) => !open && (merging = false)"
      >
        <template #body>
          <DeltaMergeDialog
            v-if="merging && preview"
            :solution-id="solution.id"
            :conflicts="preview.conflicts"
            :fetch-fn="props.fetchFn"
            @resolved="onMerged"
            @cancel="merging = false"
          />
        </template>
      </UModal>
      <UModal
        :open="confirming"
        :title="t('solutions.goLive.confirmTitle', { name: solution.name })"
        @update:open="(open: boolean) => !open && (confirming = false)"
      >
        <template #body>
          <div v-if="preview" class="flex flex-col gap-4" data-testid="go-live-confirm">
            <ul class="flex flex-col gap-1 text-sm" data-testid="go-live-counts">
              <li>{{ t('solutions.goLive.created', { n: preview.counts.created }, preview.counts.created) }}</li>
              <li>{{ t('solutions.goLive.modified', { n: preview.counts.modified }, preview.counts.modified) }}</li>
              <li>{{ t('solutions.goLive.retired', { n: preview.counts.retired }, preview.counts.retired) }}</li>
              <li v-if="preview.counts.retiredConnections > 0">
                {{ t('solutions.goLive.retiredConnections', { n: preview.counts.retiredConnections }, preview.counts.retiredConnections) }}
              </li>
              <li v-if="preview.counts.notInMetamodel > 0" data-testid="go-live-not-in-metamodel">
                {{ t('solutions.goLive.notInMetamodel', { n: preview.counts.notInMetamodel }, preview.counts.notInMetamodel) }}
              </li>
            </ul>
            <UAlert
              v-if="preview.violations.length > 0"
              data-testid="go-live-violations"
              color="warning"
              variant="subtle"
              icon="i-lucide-triangle-alert"
              :description="t('solutions.goLive.violations', { n: preview.violations.length }, preview.violations.length)"
            />
            <p class="text-sm font-medium text-error">{{ t('solutions.goLive.irreversible') }}</p>
            <UAlert v-if="goLiveFailure" color="error" variant="subtle" :description="goLiveFailure" />
            <div class="flex justify-end gap-2">
              <UButton data-testid="go-live-cancel" color="neutral" variant="outline" :label="t('solutions.goLive.cancel')" @click="confirming = false" />
              <UButton data-testid="go-live-confirm-button" icon="i-lucide-rocket" :label="t('solutions.goLive.confirm')" :loading="goingLive" @click="confirmGoLive" />
            </div>
          </div>
        </template>
      </UModal>
      <UModal
        v-model:open="modifyOpen"
        :title="t(modifying?.delta ? 'solutions.deltas.modify.editTitle' : 'solutions.deltas.modify.title')"
        :description="t('solutions.deltas.modify.description')"
      >
        <template #body>
          <SolutionModifyDialog
            v-if="modifying"
            :solution-id="solution.id"
            :entity-id="modifying.entityId"
            :delta="modifying.delta"
            :fetch-fn="props.fetchFn"
            @saved="onModified"
            @completed="
              () => {
                modifying = undefined
                onCompleted()
              }
            "
            @cancel="modifying = undefined"
          />
        </template>
      </UModal>
    </template>
  </main>
</template>
