<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { restoreDeletedEntity, type EntityUndeleteResult, type RestoreTarget } from '../../api/deletedEntities'
import { rateLimitMessage } from '../../api/problem'

// "Restore" of a deleted entity (SCR-068 and SCR-020; REQ-159, ADR-019; design D3 of
// s17-deleted-entities): the confirmation, then `restoreDeletedEntity` with the version of the
// deleted entity. What the entity's deletion took along comes back with it. A restore that someone
// else did or a change meanwhile (404, 409, 412) closes the dialog and leaves the notice and the
// reload to the caller; so does a connection with a deleted endpoint.

const props = defineProps<{ target: RestoreTarget | undefined; fetchFn?: typeof fetch }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ restored: [target: RestoreTarget, result: EntityUndeleteResult]; conflict: [target: RestoreTarget, reason: 'stale' | 'endpoint-deleted'] }>()
const { t } = useI18n()

const submitting = ref(false)
const failure = ref<string>()

watch(open, (isOpen) => {
  if (isOpen) {
    failure.value = undefined
  }
})

async function confirm() {
  const target = props.target
  if (!target) {
    return
  }
  submitting.value = true
  failure.value = undefined
  try {
    const outcome = await restoreDeletedEntity(target.id, target.version, props.fetchFn)
    if (outcome.ok) {
      open.value = false
      emit('restored', target, outcome.value)
      return
    }
    const rejection = outcome.rejection
    if (rejection.status === 409 && rejection.problemType === 'connection-not-restorable') {
      open.value = false
      emit('conflict', target, 'endpoint-deleted')
    } else if (rejection.status === 412 || rejection.status === 409 || rejection.status === 404) {
      open.value = false
      emit('conflict', target, 'stale')
    } else if (rejection.status === 403) {
      failure.value = t('deletedEntities.forbidden')
    } else {
      failure.value = rateLimitMessage(rejection) ?? t('deletedEntities.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal
    v-model:open="open"
    :title="t('deletedEntities.restoreTitle', { name: target?.name ?? '' })"
    :description="t('deletedEntities.restoreHint')"
  >
    <template #body>
      <div class="flex flex-col gap-4" data-testid="restore-entity-dialog">
        <p v-if="target?.deletedWith" data-testid="restore-entity-with" class="text-sm text-muted">
          {{ t('deletedEntities.restoreWithHint', { name: target.deletedWith }) }}
        </p>
        <UAlert v-if="failure" data-testid="restore-entity-failure" color="error" variant="subtle" :description="failure" />
        <div class="flex justify-end gap-2">
          <UButton data-testid="restore-entity-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton
            data-testid="restore-entity-confirm"
            icon="i-lucide-undo-2"
            :loading="submitting"
            :disabled="!target"
            :label="t('deletedEntities.restore')"
            @click="confirm"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
