<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// The question when an existing entity with children is placed (SCR-021, US-074, US-135; design D4 of
// us-074-diagram-nesting): are its direct children shown too, nested in it? "Only this entity" and closing
// the dialog place the entity alone.
defineProps<{ name: string; count: number }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ show: []; only: [] }>()
const { t } = useI18n()

function only() {
  open.value = false
  emit('only')
}
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.nesting.childrenTitle')" :description="t('diagrams.nesting.childrenHint', { name, count }, count)" @update:open="(value: boolean) => !value && emit('only')">
    <template #body>
      <div class="flex justify-end gap-2" data-testid="children-dialog">
        <UButton data-testid="children-only" color="neutral" variant="ghost" :label="t('diagrams.nesting.onlyThis')" @click="only" />
        <UButton data-testid="children-show" :label="t('diagrams.nesting.showChildren')" @click="emit('show')" />
      </div>
    </template>
  </UModal>
</template>
