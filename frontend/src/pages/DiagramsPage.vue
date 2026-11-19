<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  fetchDeletedDiagrams,
  fetchDiagrams,
  restoreDiagram,
  type DeletedDiagram,
  type Diagram,
  type DiagramQuery,
} from '../api/diagrams'
import { rejectsCursor } from '../api/cursors'
import { rateLimitMessage } from '../api/problem'
import { currentLanguage } from '../i18n'
import DiagramDelete from '../components/diagram/DiagramDelete.vue'
import DiagramForm from '../components/diagram/DiagramForm.vue'
import { refreshSession, session } from '../session/store'

// The page "Diagrams" (UC-10, US-074, SCR-067): the diagrams with their viewpoint and notation, a
// name filter and "Load more" with the cursor. Holders of entity:write create, rename and delete;
// holders of entity:restore switch on the deleted diagrams and restore them (design D6 and D8 of
// us-074-diagrams).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

const PAGE_SIZE = 50

function shown(at: string): string {
  return new Date(at).toLocaleString(currentLanguage(), { dateStyle: 'medium', timeStyle: 'short' })
}

const diagrams = ref<Diagram[]>([])
const deleted = ref<DeletedDiagram[]>([])
const total = ref(0)
const page = ref(0)
const nextCursor = ref<string>()
const loaded = ref(false)
const loading = ref(false)
const failure = ref<string>()
const filter = ref('')
const showDeleted = ref(false)
const creating = ref(false)
const renaming = ref<Diagram>()
const deleting = ref<Diagram>()
const restoreFailure = ref<string>()

const permissions = computed(() => new Set(session.me?.permissions.map((entry) => entry.permission) ?? []))
const canRead = computed(() => permissions.value.has('entity:read'))
const canWrite = computed(() => permissions.value.has('entity:write'))
const canRestore = computed(() => permissions.value.has('entity:restore'))

let timer: ReturnType<typeof setTimeout> | undefined

function query(next: number, cursor: string | undefined): DiagramQuery {
  return { q: filter.value, page: next, pageSize: PAGE_SIZE, cursor }
}

/** Loads the first page, or the page after the loaded one when `more` is set (with its cursor). */
async function load(more = false) {
  loading.value = true
  failure.value = undefined
  const next = more ? page.value + 1 : 0
  try {
    let outcome = showDeleted.value
      ? await fetchDeletedDiagrams(query(next, more ? nextCursor.value : undefined), props.fetchFn)
      : await fetchDiagrams(query(next, more ? nextCursor.value : undefined), props.fetchFn)
    if (!outcome.ok && more && rejectsCursor(outcome.rejection)) {
      // a cursor of another query or of a restarted backend: the page number alone applies
      outcome = showDeleted.value
        ? await fetchDeletedDiagrams(query(next, undefined), props.fetchFn)
        : await fetchDiagrams(query(next, undefined), props.fetchFn)
    }
    if (!outcome.ok) {
      failure.value = rateLimitMessage(outcome.rejection) ?? t('diagrams.loadFailed')
      return
    }
    const result = outcome.value
    if (showDeleted.value) {
      deleted.value = more ? [...deleted.value, ...(result.items as DeletedDiagram[])] : (result.items as DeletedDiagram[])
    } else {
      diagrams.value = more ? [...diagrams.value, ...(result.items as Diagram[])] : (result.items as Diagram[])
    }
    total.value = result.page.totalElements
    page.value = result.page.page
    nextCursor.value = result.nextCursor ?? undefined
  } catch {
    failure.value = t('diagrams.loadFailed')
  } finally {
    loading.value = false
    loaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  await load()
})

onBeforeUnmount(() => clearTimeout(timer))

watch(filter, () => {
  clearTimeout(timer)
  timer = setTimeout(() => void load(), 250)
})

watch(showDeleted, () => void load())

const count = computed(() => (showDeleted.value ? deleted.value.length : diagrams.value.length))
const hasMore = computed(() => count.value < total.value)

async function created(diagram: Diagram) {
  creating.value = false
  await router.push(`/diagrams/${diagram.id}`)
}

async function renamed() {
  renaming.value = undefined
  await load()
}

async function restore(diagram: DeletedDiagram) {
  restoreFailure.value = undefined
  const outcome = await restoreDiagram(diagram.id, diagram.version, props.fetchFn)
  if (outcome.ok) {
    await load()
    return
  }
  if (outcome.rejection.problemType === 'diagram-name-taken') {
    restoreFailure.value = t('diagrams.restoreNameTaken', { name: diagram.name })
  } else if (outcome.rejection.status === 412 || outcome.rejection.status === 409 || outcome.rejection.status === 404) {
    restoreFailure.value = t('diagrams.restoreStale')
    await load()
  } else {
    restoreFailure.value = t('diagrams.failed')
  }
}
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <div class="flex items-center justify-between gap-4">
      <h1 class="text-2xl font-semibold text-highlighted">{{ t('diagrams.title') }}</h1>
      <UButton v-if="canWrite" data-testid="new-diagram" icon="i-lucide-plus" :label="t('diagrams.new')" @click="creating = true" />
    </div>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert v-else-if="session.me && !canRead" data-testid="no-diagram-access" color="neutral" variant="subtle" :description="t('diagrams.noAccess')" />
    <UAlert v-if="failure" data-testid="diagrams-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="canRead">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <UInput
          v-model="filter"
          data-testid="diagram-filter"
          icon="i-lucide-search"
          :placeholder="t('diagrams.filter')"
          :aria-label="t('diagrams.filter')"
          maxlength="200"
          class="w-72"
        />
        <USwitch v-if="canRestore" v-model="showDeleted" data-testid="show-deleted" :label="t('diagrams.showDeleted')" />
      </div>
      <UAlert v-if="restoreFailure" data-testid="restore-failure" color="error" variant="subtle" :description="restoreFailure" />

      <template v-if="loaded && !failure">
        <ul v-if="!showDeleted && diagrams.length > 0" data-testid="diagram-list" class="divide-y divide-default rounded-lg border border-default">
          <li v-for="diagram in diagrams" :key="diagram.id" data-testid="diagram-item" class="flex items-center justify-between gap-3 px-4 py-3">
            <RouterLink :to="`/diagrams/${diagram.id}`" class="flex min-w-0 flex-1 flex-col hover:underline" data-testid="diagram-link">
              <span class="truncate font-medium text-highlighted">{{ diagram.name }}</span>
              <span class="text-sm text-muted">
                {{ diagram.viewpoint.name }} ·
                <span data-testid="diagram-updated">{{ t('diagrams.updated', { date: shown(diagram.updatedAt) }) }}</span>
              </span>
              <span v-if="diagram.description" class="truncate text-sm text-muted">{{ diagram.description }}</span>
            </RouterLink>
            <UBadge data-testid="diagram-notation" color="neutral" variant="outline" :label="diagram.viewpoint.notation" />
            <template v-if="canWrite">
              <UButton data-testid="diagram-rename" size="xs" color="neutral" variant="ghost" icon="i-lucide-pencil" :aria-label="t('diagrams.rename', { name: diagram.name })" @click="renaming = diagram" />
              <UButton data-testid="diagram-delete" size="xs" color="error" variant="ghost" icon="i-lucide-trash-2" :aria-label="t('diagrams.deleteAction', { name: diagram.name })" @click="deleting = diagram" />
            </template>
          </li>
        </ul>
        <ul v-else-if="showDeleted && deleted.length > 0" data-testid="deleted-list" class="divide-y divide-default rounded-lg border border-default">
          <li v-for="diagram in deleted" :key="diagram.id" data-testid="deleted-item" class="flex items-center justify-between gap-3 px-4 py-3">
            <span class="flex min-w-0 flex-1 flex-col">
              <span class="truncate font-medium text-highlighted">{{ diagram.name }}</span>
              <span class="text-sm text-muted" data-testid="deleted-by">
                {{ t('diagrams.deletedBy', { name: diagram.deletedBy.displayName, date: shown(diagram.deletedAt) }) }}
              </span>
            </span>
            <UBadge color="neutral" variant="outline" :label="diagram.viewpoint.notation" />
            <UButton data-testid="diagram-restore" size="xs" icon="i-lucide-undo-2" :label="t('diagrams.restore')" :aria-label="t('diagrams.restoreAction', { name: diagram.name })" @click="restore(diagram)" />
          </li>
        </ul>
        <UEmpty
          v-else
          data-testid="diagrams-empty"
          icon="i-lucide-workflow"
          :title="showDeleted ? t('diagrams.noneDeleted') : t('diagrams.empty')"
          :description="showDeleted ? '' : canWrite ? t('diagrams.emptyHint') : ''"
        />
        <div v-if="hasMore" class="flex justify-center">
          <UButton data-testid="diagrams-load-more" color="neutral" variant="outline" :loading="loading" :label="t('diagrams.loadMore')" @click="load(true)" />
        </div>
      </template>
    </template>

    <UModal v-model:open="creating" :title="t('diagrams.new')" :description="t('diagrams.newHint')">
      <template #body>
        <DiagramForm v-if="creating" :fetch-fn="props.fetchFn" @saved="created" @cancel="creating = false" />
      </template>
    </UModal>
    <UModal :open="renaming !== undefined" :title="t('diagrams.renameTitle')" @update:open="(open: boolean) => !open && (renaming = undefined)">
      <template #body>
        <DiagramForm v-if="renaming" :diagram="renaming" :fetch-fn="props.fetchFn" @saved="renamed" @cancel="renaming = undefined" />
      </template>
    </UModal>
    <DiagramDelete
      :open="deleting !== undefined"
      :diagram="deleting"
      :fetch-fn="props.fetchFn"
      @update:open="(open: boolean) => !open && (deleting = undefined)"
      @done="load()"
      @stale="load()"
    />
  </main>
</template>
