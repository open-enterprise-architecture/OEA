<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ConnectedOption } from './edgeModel'

// "Add connected element" (SCR-021, US-074 AC6; design D5 of us-074-diagram-connections): the choice
// of a connection metatype and a target metatype among the allowed ones; the canvas places the new
// element at the default length of the connection metatype.
const props = defineProps<{ options: ConnectedOption[]; loading: boolean; failed: boolean }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ add: [connectionId: string, targetId: string] }>()
const { t } = useI18n()

const connection = ref<string>()
const target = ref<string>()

const connections = computed(() => {
  const seen = new Map<string, string>()
  props.options.forEach((option) => seen.set(option.connection.id, option.connection.name))
  return [...seen].map(([value, label]) => ({ value, label }))
})
const targets = computed(() =>
  props.options.filter((option) => option.connection.id === connection.value).map((option) => ({ value: option.target.id, label: option.target.name })),
)

watch(
  () => [open.value, props.options] as const,
  () => {
    connection.value = connections.value[0]?.value
  },
  { immediate: true },
)
watch(connection, () => {
  target.value = targets.value[0]?.value
}, { immediate: true })
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.connections.connectedTitle')" :description="t('diagrams.connections.connectedHint')">
    <template #body>
      <div class="flex flex-col gap-3" data-testid="connected-dialog">
        <p v-if="loading" class="text-sm text-muted" data-testid="connected-loading">{{ t('diagrams.connections.loading') }}</p>
        <UAlert v-else-if="failed" color="error" variant="subtle" data-testid="connected-failure" :description="t('diagrams.connections.loadFailed')" />
        <p v-else-if="options.length === 0" class="text-sm text-muted" data-testid="connected-none">{{ t('diagrams.connections.noneAllowed') }}</p>
        <template v-else>
          <UFormField :label="t('diagrams.connections.connectionType')">
            <USelect v-model="connection" class="w-full" :items="connections" data-testid="connected-connection" />
          </UFormField>
          <UFormField :label="t('diagrams.connections.targetType')">
            <USelect v-model="target" class="w-full" :items="targets" data-testid="connected-target" />
          </UFormField>
        </template>
        <div class="flex justify-end gap-2">
          <UButton data-testid="connected-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton
            data-testid="connected-add"
            :disabled="loading || failed || !connection || !target"
            :label="t('diagrams.connections.addConnected')"
            @click="connection && target && emit('add', connection, target)"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
