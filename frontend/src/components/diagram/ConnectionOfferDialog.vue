<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

// "Choose…" of the offer "N connections available" (SCR-021, US-074 AC6; design D5 of
// us-074-diagram-connections): the connections between the placed shape and the shapes already
// shown, to add some of them as edges.
export interface OfferedConnection {
  /** the connection ID */
  id: number
  label: string
  metatypeName: string
}
const props = defineProps<{ offers: OfferedConnection[] }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ add: [ids: number[]] }>()
const { t } = useI18n()

const chosen = ref<number[]>([])

watch(
  () => [open.value, props.offers] as const,
  () => {
    chosen.value = props.offers.map((offer) => offer.id)
  },
  { immediate: true },
)

function toggle(id: number, checked: boolean | 'indeterminate') {
  chosen.value = checked === true ? [...chosen.value, id] : chosen.value.filter((entry) => entry !== id)
}
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.connections.chooseTitle')" :description="t('diagrams.connections.chooseHint')">
    <template #body>
      <div class="flex flex-col gap-3" data-testid="offer-dialog">
        <ul class="flex max-h-72 flex-col gap-1 overflow-y-auto">
          <li v-for="offer in offers" :key="offer.id" class="flex items-center gap-2 text-sm">
            <UCheckbox :model-value="chosen.includes(offer.id)" :label="offer.label" :data-testid="`offer-${offer.id}`" @update:model-value="toggle(offer.id, $event)" />
            <span class="text-xs text-muted">{{ offer.metatypeName }}</span>
          </li>
        </ul>
        <div class="flex justify-end gap-2">
          <UButton data-testid="offer-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton data-testid="offer-add" :disabled="chosen.length === 0" :label="t('diagrams.connections.addSelected')" @click="emit('add', chosen)" />
        </div>
      </div>
    </template>
  </UModal>
</template>
