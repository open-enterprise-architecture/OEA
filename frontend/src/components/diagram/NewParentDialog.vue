<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// "New parent?" when a shape is moved into a container or out of it (SCR-021, US-135; design D4 of
// us-074-diagram-nesting). "Yes" changes the parent of the entity with the next save (versioned as
// `setEntityParent` does); "No" and closing the dialog change only the picture.
defineProps<{ name: string; parent?: string }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ yes: []; no: [] }>()
const { t } = useI18n()
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.nesting.parentTitle')" :description="parent ? t('diagrams.nesting.parentHint', { name, parent }) : t('diagrams.nesting.parentNoneHint', { name })" @update:open="(value: boolean) => !value && emit('no')">
    <template #body>
      <div class="flex justify-end gap-2" data-testid="new-parent-dialog">
        <UButton data-testid="new-parent-no" color="neutral" variant="ghost" :label="t('diagrams.nesting.no')" @click="emit('no')" />
        <UButton data-testid="new-parent-yes" :label="t('diagrams.nesting.yes')" @click="emit('yes')" />
      </div>
    </template>
  </UModal>
</template>
