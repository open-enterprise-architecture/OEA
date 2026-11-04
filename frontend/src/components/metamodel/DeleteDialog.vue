<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WriteOutcome } from '../../api/metamodel'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Confirmation of a deletion in SCR-030 with the version read (design D4 of
// scr-030-metamodel-configuration): on 412 the dialog shows the current state and asks again.
const props = defineProps<{
  title: string
  description: string
  warning?: string
  version?: number
  run: (version: number | undefined) => Promise<WriteOutcome<void>>
}>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ done: []; stale: [] }>()
const { t } = useI18n()

const failure = ref<string>()
const staleVersion = ref<number>()
const submitting = ref(false)

watch(open, (value) => {
  if (value) {
    failure.value = undefined
    staleVersion.value = undefined
  }
})

async function confirm(version: number | undefined) {
  failure.value = undefined
  submitting.value = true
  try {
    const outcome = await props.run(version)
    if (outcome.ok) {
      open.value = false
      emit('done')
    } else if (outcome.rejection.problemType === 'stale-version') {
      staleVersion.value = outcome.rejection.currentVersion
      // the caller reloads and shows the current state in the slot
      emit('stale')
    } else {
      failure.value = rejectionMessage(outcome.rejection)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="title" :description="description">
    <template #body>
      <div class="flex flex-col gap-4" data-testid="delete-dialog">
        <UAlert v-if="warning" data-testid="delete-warning" color="warning" variant="subtle" :description="warning" />
        <UAlert v-if="failure" data-testid="delete-failure" color="error" variant="subtle" :description="failure" />
        <CollisionWarning
          v-if="staleVersion !== undefined"
          :overwrite-label="t('metamodel.collision.deleteAnyway')"
          @overwrite="confirm(staleVersion)"
          @discard="open = false"
        >
          <slot name="current" />
        </CollisionWarning>
        <div v-else class="flex justify-end gap-2">
          <UButton data-testid="delete-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton
            data-testid="delete-confirm"
            color="error"
            :loading="submitting"
            :label="t('metamodel.delete')"
            @click="confirm(version)"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
