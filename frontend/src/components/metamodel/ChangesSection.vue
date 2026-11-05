<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchChanges, type ChangeFilter, type MetamodelChange, type MetamodelChangeKind } from '../../api/metamodel'
import { currentLanguage } from '../../i18n'

// The section "Changes" of SCR-030: the metamodel changes from the audit trail with names,
// filters and import batches (US-034, REQ-034; design D6 of us-034-metamodel-changes).
const props = withDefaults(defineProps<{ fetchFn?: typeof fetch; pageSize?: number }>(), { fetchFn: undefined, pageSize: 50 })
const { t, te } = useI18n()

const ALL = '-'
const KINDS: MetamodelChangeKind[] = ['metatype', 'property', 'category', 'enum-value', 'mapping', 'labels', 'reimport', 'package', 'configuration']

const filter = reactive({ kind: ALL, actorRef: ALL, from: '', to: '', batchId: '' })
const items = ref<MetamodelChange[]>([])
const actors = ref<{ actorRef?: string | null; actorName?: string | null; actorType: string }[]>([])
const page = ref(0)
const total = ref(0)
const loading = ref(false)
const failed = ref(false)

const kindItems = computed(() => [
  { label: t('metamodel.changes.allKinds'), value: ALL },
  ...KINDS.map((kind) => ({ label: t(`metamodel.changes.kind.${kind}`), value: kind })),
])
const actorItems = computed(() => [
  { label: t('metamodel.changes.allPersons'), value: ALL },
  ...actors.value
    .filter((actor) => actor.actorRef)
    .map((actor) => ({ label: actor.actorName ?? actor.actorRef!, value: actor.actorRef! })),
])
const more = computed(() => items.value.length < total.value)

function query(): ChangeFilter {
  return {
    kind: filter.kind !== ALL ? (filter.kind as MetamodelChangeKind) : undefined,
    actorRef: filter.actorRef !== ALL ? filter.actorRef : undefined,
    from: filter.from || undefined,
    to: filter.to || undefined,
    batchId: filter.batchId || undefined,
  }
}

/** Loads the first page again, or appends the next one. */
async function load(append = false) {
  loading.value = true
  failed.value = false
  const next = append ? page.value + 1 : 0
  try {
    const result = await fetchChanges(query(), next, props.pageSize, props.fetchFn)
    items.value = append ? [...items.value, ...result.items] : result.items
    actors.value = result.actors
    total.value = result.page.totalElements
    page.value = next
  } catch {
    failed.value = true
  }
  loading.value = false
}

onMounted(() => load())
watch(filter, () => load())

function action(change: MetamodelChange): string {
  const key = `metamodel.changes.event.${change.eventType.replace(/^metamodel\./, '')}`
  return te(key) ? t(key) : change.eventType
}

function person(change: MetamodelChange): string {
  return change.actor.actorName ?? (change.actor.actorType === 'system' ? t('metamodel.changes.system') : (change.actor.actorRef ?? ''))
}

function element(change: MetamodelChange): string {
  return change.elementName ?? (change.targetId ? t('metamodel.changes.unknownElement', { id: change.targetId }) : '')
}

function details(change: MetamodelChange): string {
  const values = (change.details ?? {}) as Record<string, unknown>
  if (Array.isArray(values.changedFields) && values.changedFields.length > 0) {
    return t('metamodel.changes.changedFields', { fields: values.changedFields.join(', ') })
  }
  if (values.before !== undefined && values.after !== undefined) {
    return `${String(values.before)} → ${String(values.after)}`
  }
  if (typeof values.packageId === 'string' && values.version !== undefined) {
    return t('metamodel.changes.package', { package: values.packageId, version: String(values.version) })
  }
  return ''
}

function time(change: MetamodelChange): string {
  return new Date(change.occurredAt).toLocaleString(currentLanguage())
}
</script>

<template>
  <section class="flex flex-col gap-4">
    <div class="flex flex-col gap-1">
      <h2 class="font-semibold text-highlighted">{{ t('metamodel.changes.title') }}</h2>
      <p class="text-sm text-muted">{{ t('metamodel.changes.hint') }}</p>
    </div>

    <div class="flex flex-wrap items-end gap-3">
      <USelect v-model="filter.kind" data-testid="changes-kind" :aria-label="t('metamodel.changes.change')" :items="kindItems" class="w-56" />
      <USelect v-model="filter.actorRef" data-testid="changes-person" :aria-label="t('metamodel.changes.person')" :items="actorItems" class="w-56" />
      <UFormField :label="t('metamodel.changes.from')">
        <UInput v-model="filter.from" data-testid="changes-from" type="date" />
      </UFormField>
      <UFormField :label="t('metamodel.changes.to')">
        <UInput v-model="filter.to" data-testid="changes-to" type="date" />
      </UFormField>
      <UBadge
        v-if="filter.batchId"
        data-testid="changes-batch-filter"
        color="neutral"
        variant="subtle"
        size="lg"
        :label="t('metamodel.changes.batchFilter', { id: filter.batchId.slice(0, 8) })"
      >
        <template #trailing>
          <UButton
            data-testid="changes-batch-clear"
            icon="i-lucide-x"
            size="xs"
            color="neutral"
            variant="link"
            :aria-label="t('metamodel.changes.removeBatch')"
            @click="filter.batchId = ''"
          />
        </template>
      </UBadge>
    </div>

    <UAlert v-if="failed" data-testid="changes-failure" color="error" variant="subtle" :description="t('metamodel.changes.loadFailed')" />

    <div class="overflow-x-auto rounded-md border border-default">
      <table class="w-full text-left text-sm">
        <thead class="bg-elevated text-toned">
          <tr>
            <th class="px-3 py-2">{{ t('metamodel.changes.time') }}</th>
            <th class="px-3 py-2">{{ t('metamodel.changes.person') }}</th>
            <th class="px-3 py-2">{{ t('metamodel.changes.change') }}</th>
            <th class="px-3 py-2">{{ t('metamodel.changes.element') }}</th>
            <th class="px-3 py-2">{{ t('metamodel.changes.details') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="change in items" :key="change.id" data-testid="change-row" class="border-t border-default align-top">
            <td class="px-3 py-2 whitespace-nowrap">{{ time(change) }}</td>
            <td class="px-3 py-2" data-testid="change-person">{{ person(change) }}</td>
            <td class="px-3 py-2" data-testid="change-action">{{ action(change) }}</td>
            <td class="px-3 py-2" data-testid="change-element">{{ element(change) }}</td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap items-center gap-2">
                <span>{{ details(change) }}</span>
                <UButton
                  v-if="change.batchId"
                  data-testid="change-batch"
                  size="xs"
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-package"
                  :label="t('metamodel.changes.batch')"
                  @click="filter.batchId = change.batchId!"
                />
              </div>
            </td>
          </tr>
          <tr v-if="!loading && items.length === 0">
            <td colspan="5" class="px-3 py-4 text-center text-muted" data-testid="changes-empty">{{ t('metamodel.changes.none') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="more">
      <UButton data-testid="changes-more" color="neutral" variant="outline" :loading="loading" :label="t('metamodel.changes.loadMore')" @click="load(true)" />
    </div>
  </section>
</template>
