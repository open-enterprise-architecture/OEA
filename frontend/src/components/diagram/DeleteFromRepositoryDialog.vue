<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteEntityFromRepository, fetchDeletionPreview, type EntityDeletionPreview } from '../../api/entityDeletion'
import { rateLimitMessage } from '../../api/problem'

// "Delete from repository" (SCR-021, REQ-146, US-074; design D6 of s17-entity-delete): the
// confirmation shows what the deletion takes along - children, connections and the active solutions
// whose deltas become conflicts - and deletes at once with the version of the preview. A change of
// the entity since then (412) offers to read the preview again.
const props = defineProps<{ entityId: number | undefined; fetchFn?: typeof fetch }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ deleted: [ids: number[]] }>()
const { t } = useI18n()

const preview = ref<EntityDeletionPreview>()
const loading = ref(false)
const submitting = ref(false)
/** the message of a failed preview or deletion, with whether the preview can be read again */
const failure = ref<{ text: string; again: boolean }>()

async function load() {
  if (props.entityId === undefined) {
    return
  }
  loading.value = true
  failure.value = undefined
  preview.value = undefined
  try {
    const outcome = await fetchDeletionPreview(props.entityId, props.fetchFn)
    if (outcome.ok) {
      preview.value = outcome.value
    } else {
      const rejection = outcome.rejection
      failure.value = {
        text:
          rateLimitMessage(rejection) ??
          (rejection.status === 422
            ? t('diagrams.repositoryDelete.notDeletable')
            : rejection.status === 404
              ? t('diagrams.repositoryDelete.gone')
              : rejection.status === 403
                ? t('diagrams.repositoryDelete.forbidden')
                : t('diagrams.repositoryDelete.loadFailed')),
        again: false,
      }
    }
  } catch {
    failure.value = { text: t('common.backendUnreachable'), again: false }
  } finally {
    loading.value = false
  }
}

watch(
  () => [open.value, props.entityId] as const,
  ([isOpen]) => {
    if (isOpen) {
      void load()
    }
  },
  { immediate: true },
)

async function confirm() {
  if (!preview.value) {
    return
  }
  submitting.value = true
  failure.value = undefined
  try {
    const outcome = await deleteEntityFromRepository(preview.value.entity.id, preview.value.entity.version, props.fetchFn)
    if (outcome.ok) {
      open.value = false
      emit('deleted', outcome.value.deleted)
      return
    }
    const rejection = outcome.rejection
    if (rejection.status === 412) {
      failure.value = { text: t('diagrams.repositoryDelete.changed', { name: preview.value.entity.name }), again: true }
    } else if (rejection.status === 409 && rejection.problemType === 'entity-blocked') {
      const names = (rejection.entities ?? []).map((entry) => entry.entityName).join(', ')
      failure.value = { text: t('diagrams.repositoryDelete.blocked', { names }), again: false }
    } else if (rejection.status === 403) {
      failure.value = { text: t('diagrams.repositoryDelete.forbidden'), again: false }
    } else if (rejection.status === 404) {
      failure.value = { text: t('diagrams.repositoryDelete.gone'), again: false }
    } else if (rejection.status === 422) {
      failure.value = { text: t('diagrams.repositoryDelete.notDeletable'), again: false }
    } else {
      failure.value = { text: rateLimitMessage(rejection) ?? t('diagrams.repositoryDelete.failed'), again: false }
    }
  } catch {
    failure.value = { text: t('common.backendUnreachable'), again: false }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal
    v-model:open="open"
    :title="preview ? t('diagrams.repositoryDelete.title', { name: preview.entity.name }) : t('diagrams.repositoryDelete.action')"
    :description="t('diagrams.repositoryDelete.hint')"
  >
    <template #body>
      <div class="flex flex-col gap-4" data-testid="repository-delete-dialog">
        <p v-if="loading" data-testid="repository-delete-loading" class="text-sm text-muted" role="status">{{ t('diagrams.repositoryDelete.loading') }}</p>
        <template v-if="preview">
          <ul class="flex flex-col gap-1 text-sm" data-testid="repository-delete-counts">
            <li>{{ t('diagrams.repositoryDelete.children', { count: preview.childCount }, preview.childCount) }}</li>
            <li>{{ t('diagrams.repositoryDelete.connections', { count: preview.connectionCount }, preview.connectionCount) }}</li>
          </ul>
          <UAlert
            v-if="preview.solutions.length > 0"
            data-testid="repository-delete-solutions"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            :title="t('diagrams.repositoryDelete.solutionsTitle', { count: preview.solutions.length }, preview.solutions.length)"
          >
            <template #description>
              <p>{{ t('diagrams.repositoryDelete.solutionsHint') }}</p>
              <ul class="mt-1 list-disc ps-4">
                <li v-for="solution in preview.solutions" :key="solution.id">
                  {{ t('diagrams.repositoryDelete.solution', { name: solution.name, count: solution.deltaCount }, solution.deltaCount) }}
                </li>
              </ul>
            </template>
          </UAlert>
        </template>
        <UAlert v-if="failure" data-testid="repository-delete-failure" color="error" variant="subtle" :description="failure.text" />
        <div class="flex justify-end gap-2">
          <UButton v-if="failure?.again" data-testid="repository-delete-again" color="neutral" variant="outline" icon="i-lucide-refresh-cw" :label="t('diagrams.repositoryDelete.again')" @click="load" />
          <UButton data-testid="repository-delete-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton
            data-testid="repository-delete-confirm"
            color="error"
            icon="i-lucide-trash-2"
            :disabled="!preview || failure?.again"
            :loading="submitting"
            :label="t('diagrams.repositoryDelete.action')"
            @click="confirm"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
