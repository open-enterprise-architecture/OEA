<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteDiagram, type Diagram } from '../../api/diagrams'

// Confirmation of the deletion of a diagram (US-074; design D8 of us-074-diagrams): the diagram can
// be restored with entity:restore, and the entities of the repository stay.
const props = defineProps<{ diagram?: Diagram; fetchFn?: typeof fetch }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ done: []; stale: [] }>()
const { t } = useI18n()

const failure = ref<string>()
const submitting = ref(false)

watch(open, (value) => {
  if (value) {
    failure.value = undefined
  }
})

async function confirm() {
  if (!props.diagram) {
    return
  }
  submitting.value = true
  failure.value = undefined
  try {
    const outcome = await deleteDiagram(props.diagram.id, props.diagram.version, props.fetchFn)
    if (outcome.ok) {
      open.value = false
      emit('done')
    } else if (outcome.rejection.status === 412) {
      failure.value = t('diagrams.stale')
      emit('stale')
    } else if (outcome.rejection.status === 404) {
      open.value = false
      emit('done')
    } else {
      failure.value = outcome.rejection.status === 403 ? t('diagrams.forbidden') : t('diagrams.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.delete')" :description="diagram ? t('diagrams.deleteHint', { name: diagram.name }) : ''">
    <template #body>
      <div class="flex flex-col gap-4" data-testid="diagram-delete-dialog">
        <UAlert v-if="failure" data-testid="diagram-delete-failure" color="error" variant="subtle" :description="failure" />
        <div class="flex justify-end gap-2">
          <UButton data-testid="diagram-delete-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton data-testid="diagram-delete-confirm" color="error" :loading="submitting" :label="t('diagrams.delete')" @click="confirm" />
        </div>
      </div>
    </template>
  </UModal>
</template>
