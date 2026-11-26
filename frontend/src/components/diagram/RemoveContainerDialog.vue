<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// Removing a container (SCR-021, US-074; design D4 of us-074-diagram-nesting): the shapes nested in it
// leave the diagram with it, the entities stay. The confirmation names their number.
defineProps<{ name: string; count: number }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ confirm: [] }>()
const { t } = useI18n()
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.nesting.removeTitle')" :description="t('diagrams.nesting.removeHint', { name, count }, count)">
    <template #body>
      <div class="flex justify-end gap-2" data-testid="remove-container-dialog">
        <UButton data-testid="remove-container-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
        <UButton data-testid="remove-container-confirm" color="error" :label="t('diagrams.nesting.removeConfirm', { count }, count)" @click="emit('confirm')" />
      </div>
    </template>
  </UModal>
</template>
