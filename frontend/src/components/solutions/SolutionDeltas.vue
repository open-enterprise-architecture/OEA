<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage } from '../../api/problem'
import { deleteSolutionDelta, type SolutionDelta } from '../../api/solutions'
import { label, labelKeys } from '../../i18n/labels'
import SolutionDeltaDialog from './SolutionDeltaDialog.vue'

// The area "Changes" of SCR-061 (US-040, US-042; design D9 of us-040-entity-deltas): the deltas
// of the solution with type, name and metatype, a delta `modified` with its number of changes
// (US-041). With solution:write, while the solution is not completed, "New entity" opens the
// dialog, "Edit" opens a delta `modified` in the dialog "Modify entity" of the page, and "Remove"
// removes a delta after a confirmation.
const props = defineProps<{ solutionId: string; deltas: SolutionDelta[]; canWrite: boolean; failure?: string; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ changed: []; completed: []; edit: [delta: SolutionDelta] }>()
const { t } = useI18n()

const creating = ref(false)
const removing = ref<SolutionDelta>()
const removalFailure = ref<string>()
const submitting = ref(false)

const confirmOpen = computed({
  get: () => removing.value !== undefined,
  set: (open: boolean) => {
    if (!open) removing.value = undefined
  },
})

const columns = computed(() => [
  { accessorKey: 'deltaType', header: t('solutions.deltas.type') },
  { accessorKey: 'name', header: t('solutions.deltas.name') },
  { accessorKey: 'metatypeName', header: t('solutions.deltas.metatype') },
  ...(props.canWrite ? [{ id: 'actions', header: t('solutions.deltas.actions') }] : []),
])

const rows = computed(() =>
  props.deltas.map((delta) => ({ ...delta, metatypeName: label(labelKeys.metatype(delta.metatypeId), delta.metatypeName) })),
)

function onCreated() {
  creating.value = false
  emit('changed')
}

async function confirmRemoval() {
  if (!removing.value) return
  submitting.value = true
  removalFailure.value = undefined
  try {
    const outcome = await deleteSolutionDelta(props.solutionId, removing.value.id, props.fetchFn)
    if (outcome.ok) {
      removing.value = undefined
      emit('changed')
    } else if (outcome.rejection.status === 409) {
      removing.value = undefined
      emit('completed')
    } else {
      removalFailure.value = rateLimitMessage(outcome.rejection) ?? t('solutions.deltas.removeFailed')
    }
  } catch {
    removalFailure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="flex flex-col gap-3" data-testid="solution-deltas">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h2 class="text-lg font-semibold text-highlighted">{{ t('solutions.deltas.title') }}</h2>
        <p class="text-sm text-muted">{{ t('solutions.deltas.intro') }}</p>
      </div>
      <UButton
        v-if="props.canWrite"
        data-testid="delta-new"
        icon="i-lucide-plus"
        :label="t('solutions.deltas.new')"
        @click="creating = true"
      />
    </div>
    <UAlert v-if="props.failure" data-testid="deltas-failure" color="error" variant="subtle" :description="props.failure" />
    <p v-if="props.deltas.length === 0" data-testid="deltas-empty" class="text-sm text-muted">{{ t('solutions.deltas.empty') }}</p>
    <UTable v-else data-testid="deltas-table" :data="rows" :columns="columns" class="rounded-lg border border-default">
      <template #deltaType-cell="{ row }">
        <UBadge
          :color="row.original.deltaType === 'new' ? 'success' : row.original.deltaType === 'modified' ? 'info' : 'warning'"
          variant="subtle"
          :label="t(`solutions.deltas.types.${row.original.deltaType}`)"
        />
        <span v-if="row.original.deltaType === 'modified'" data-testid="delta-change-count" class="ms-2 text-sm text-muted">
          {{ t('solutions.deltas.modify.count', row.original.changes.length) }}
        </span>
      </template>
      <template #actions-cell="{ row }">
        <div class="flex justify-end gap-1">
          <UButton
            v-if="row.original.deltaType === 'modified'"
            data-testid="delta-edit"
            color="neutral"
            variant="ghost"
            size="sm"
            icon="i-lucide-pencil"
            :label="t('solutions.deltas.modify.edit')"
            @click="emit('edit', row.original)"
          />
          <UButton
            data-testid="delta-remove"
            color="neutral"
            variant="ghost"
            size="sm"
            icon="i-lucide-trash-2"
            :label="t('solutions.deltas.remove')"
            @click="removing = row.original"
          />
        </div>
      </template>
    </UTable>

    <UModal v-model:open="creating" :title="t('solutions.deltas.new')" :description="t('solutions.deltas.newHint')">
      <template #body>
        <SolutionDeltaDialog
          :solution-id="props.solutionId"
          :fetch-fn="props.fetchFn"
          @created="onCreated"
          @completed="emit('completed')"
          @cancel="creating = false"
        />
      </template>
    </UModal>

    <UModal
      v-model:open="confirmOpen"
      :title="t('solutions.deltas.removeTitle')"
      :description="t('solutions.deltas.removeConfirm', { name: removing?.name ?? '' })"
    >
      <template #body>
        <div class="flex flex-col gap-4" data-testid="delta-remove-dialog">
          <UAlert v-if="removalFailure" color="error" variant="subtle" :description="removalFailure" />
          <div class="flex justify-end gap-2">
            <UButton data-testid="delta-remove-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="removing = undefined" />
            <UButton
              data-testid="delta-remove-confirm"
              color="error"
              :loading="submitting"
              :label="t('solutions.deltas.remove')"
              @click="confirmRemoval"
            />
          </div>
        </div>
      </template>
    </UModal>
  </section>
</template>
