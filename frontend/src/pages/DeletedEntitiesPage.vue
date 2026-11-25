<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  DELETED_ENTITY_LIMITS,
  fetchDeletedEntities,
  summarize,
  type DeletedEntity,
  type DeletedEntityQuery,
  type EntityUndeleteResult,
  type RestoreNoticeData,
  type RestoreTarget,
} from '../api/deletedEntities'
import { rejectsCursor } from '../api/cursors'
import { fetchMetatypes, type MetatypeDetail } from '../api/metamodel'
import { rateLimitMessage } from '../api/problem'
import RestoreEntityDialog from '../components/deleted/RestoreEntityDialog.vue'
import RestoreNotice from '../components/deleted/RestoreNotice.vue'
import { currentLanguage } from '../i18n'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// The page "Deleted entities" (REQ-159, ADR-019, SCR-068; design D3 of s17-deleted-entities): the
// deleted entities and connections, newest deletion first, with who deleted them and with which
// entity ("Deleted with ERP"), a name filter, a type filter and "Load more" with the cursor. Holders
// of entity:restore restore an entry with a confirmation; a notice names what came back and what
// stayed deleted.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const PAGE_SIZE = 50
const ALL = '-'

function shown(at: string): string {
  return new Date(at).toLocaleString(currentLanguage(), { dateStyle: 'medium', timeStyle: 'short' })
}

const items = ref<DeletedEntity[]>([])
const total = ref(0)
const page = ref(0)
const nextCursor = ref<string>()
const loaded = ref(false)
const loading = ref(false)
const failure = ref<string>()
const filter = ref('')
const metatype = ref(ALL)
const metatypes = ref<MetatypeDetail[]>([])
const restoring = ref<RestoreTarget>()
const dialogOpen = ref(false)
const notice = ref<RestoreNoticeData>()
// the names the notice uses, kept from the list before the restore refreshes it
const names = new Map<number, string>()

const canRestore = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:restore'))

const metatypeItems = computed(() => [
  { label: t('deletedEntities.allMetatypes'), value: ALL },
  ...metatypes.value.map((entry) => ({ label: label(labelKeys.metatype(entry.id), entry.name), value: entry.id })),
])

let timer: ReturnType<typeof setTimeout> | undefined

function query(next: number, cursor: string | undefined): DeletedEntityQuery {
  return { q: filter.value, metatypeId: metatype.value !== ALL ? metatype.value : undefined, page: next, pageSize: PAGE_SIZE, cursor }
}

/** Loads the first page, or the page after the loaded one when `more` is set (with its cursor). */
async function load(more = false) {
  loading.value = true
  failure.value = undefined
  const next = more ? page.value + 1 : 0
  try {
    let outcome = await fetchDeletedEntities(query(next, more ? nextCursor.value : undefined), props.fetchFn)
    if (!outcome.ok && more && rejectsCursor(outcome.rejection)) {
      // a cursor of another query or of a restarted backend: the page number alone applies
      outcome = await fetchDeletedEntities(query(next, undefined), props.fetchFn)
    }
    if (!outcome.ok) {
      failure.value = rateLimitMessage(outcome.rejection) ?? t('deletedEntities.loadFailed')
      return
    }
    const result = outcome.value
    items.value = more ? [...items.value, ...result.items] : result.items
    for (const entry of result.items) {
      names.set(entry.id, entry.name)
    }
    total.value = result.page.totalElements
    page.value = result.page.page
    nextCursor.value = result.nextCursor ?? undefined
  } catch {
    failure.value = t('deletedEntities.loadFailed')
  } finally {
    loading.value = false
    loaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRestore.value) {
    loaded.value = true
    return
  }
  try {
    metatypes.value = await fetchMetatypes(props.fetchFn)
  } catch {
    // without the metatypes the type filter offers only "All types"
    metatypes.value = []
  }
  await load()
})

onBeforeUnmount(() => clearTimeout(timer))

watch(filter, () => {
  clearTimeout(timer)
  timer = setTimeout(() => void load(), 250)
})

watch(metatype, () => void load())

const hasMore = computed(() => items.value.length < total.value)

function ask(entry: DeletedEntity) {
  restoring.value = { id: entry.id, name: entry.name, version: entry.version, deletedWith: entry.deletedWith?.name }
  dialogOpen.value = true
}

async function restored(target: RestoreTarget, result: EntityUndeleteResult) {
  notice.value = { kind: 'done', name: target.name, summary: summarize(result, names) }
  await load()
}

async function conflicted(target: RestoreTarget, reason: 'stale' | 'endpoint-deleted') {
  notice.value = { kind: reason, name: target.name }
  await load()
}
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <h1 class="text-2xl font-semibold text-highlighted">{{ t('deletedEntities.title') }}</h1>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert v-else-if="session.me && !canRestore" data-testid="deleted-entities-no-permission" color="neutral" variant="subtle" :description="t('deletedEntities.noPermission')" />
    <UAlert v-if="failure" data-testid="deleted-entities-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="canRestore">
      <div class="flex flex-wrap items-end gap-3">
        <UInput
          v-model="filter"
          data-testid="deleted-entities-filter"
          icon="i-lucide-search"
          :placeholder="t('deletedEntities.filter')"
          :aria-label="t('deletedEntities.filter')"
          :maxlength="DELETED_ENTITY_LIMITS.filter"
          class="w-72"
        />
        <USelect v-model="metatype" data-testid="deleted-entities-metatype" :aria-label="t('deletedEntities.metatype')" :items="metatypeItems" class="w-56" />
      </div>
      <RestoreNotice v-if="notice" :notice="notice" />

      <template v-if="loaded && !failure">
        <ul v-if="items.length > 0" data-testid="deleted-entities-list" class="divide-y divide-default rounded-lg border border-default">
          <li v-for="entry in items" :key="entry.id" data-testid="deleted-entity" class="flex items-center justify-between gap-3 px-4 py-3">
            <span class="flex min-w-0 flex-1 flex-col">
              <span class="truncate font-medium text-highlighted">
                {{ entry.name }}
                <span v-if="entry.isConnection && entry.source && entry.target" class="text-sm font-normal text-muted" data-testid="deleted-entity-ends">
                  ({{ entry.source.name }} → {{ entry.target.name }})
                </span>
              </span>
              <span class="text-sm text-muted" data-testid="deleted-entity-by">
                {{ label(labelKeys.metatype(entry.metatype.id), entry.metatype.name) }} ·
                {{
                  entry.deletedBy
                    ? t('deletedEntities.deletedBy', { name: entry.deletedBy.displayName, date: shown(entry.deletedAt) })
                    : t('deletedEntities.deletedAt', { date: shown(entry.deletedAt) })
                }}
              </span>
              <span v-if="entry.deletedWith" class="text-sm text-muted" data-testid="deleted-entity-with">
                {{ t('deletedEntities.deletedWith', { name: entry.deletedWith.name }) }}
              </span>
            </span>
            <UBadge v-if="entry.isConnection" color="neutral" variant="outline" :label="t('deletedEntities.connection')" />
            <UButton
              data-testid="deleted-entity-restore"
              size="xs"
              icon="i-lucide-undo-2"
              :label="t('deletedEntities.restore')"
              :aria-label="t('deletedEntities.restoreAction', { name: entry.name })"
              @click="ask(entry)"
            />
          </li>
        </ul>
        <UEmpty v-else data-testid="deleted-entities-empty" icon="i-lucide-trash-2" :title="t('deletedEntities.empty')" />
        <div v-if="hasMore" class="flex justify-center">
          <UButton data-testid="deleted-entities-load-more" color="neutral" variant="outline" :loading="loading" :label="t('deletedEntities.loadMore')" @click="load(true)" />
        </div>
      </template>
    </template>

    <RestoreEntityDialog
      v-model:open="dialogOpen"
      :target="restoring"
      :fetch-fn="props.fetchFn"
      @restored="restored"
      @conflict="conflicted"
    />
  </main>
</template>
