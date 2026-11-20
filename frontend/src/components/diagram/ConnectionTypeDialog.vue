<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ConnectionOption } from './edgeModel'

// The type of a connection drawn between two shapes (SCR-021, US-074 AC6; design D5 of
// us-074-diagram-connections): the connection types that the viewpoint and the metamodel allow
// between the two metatypes, when there are several.
defineProps<{ options: ConnectionOption[] }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ pick: [option: ConnectionOption] }>()
const { t } = useI18n()
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.connections.typeTitle')" :description="t('diagrams.connections.typeHint')">
    <template #body>
      <div class="flex flex-col gap-3" data-testid="connection-type-dialog">
        <ul class="flex flex-col">
          <li v-for="option in options" :key="option.metatypeId">
            <button
              type="button"
              class="w-full rounded-md px-2 py-1.5 text-start text-sm hover:bg-elevated focus-visible:outline-2 focus-visible:outline-primary"
              :data-testid="`connection-type-${option.metatypeId}`"
              @click="emit('pick', option)"
            >
              {{ option.name }}
            </button>
          </li>
        </ul>
        <div class="flex justify-end">
          <UButton data-testid="connection-type-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
        </div>
      </div>
    </template>
  </UModal>
</template>
