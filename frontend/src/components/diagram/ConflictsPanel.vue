<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// The conflicts of a diagram (SCR-021, US-074; design D5 of us-074-diagram-viewpoint-change): the shapes and
// edges whose type the viewpoint does not allow. The save is possible only without them; each can be removed
// from the diagram, or all at once. A reader sees the list without the actions.
export interface ConflictItem {
  id: string
  kind: 'shape' | 'edge'
  name: string
}

defineProps<{ items: ConflictItem[]; editable: boolean; selectedId?: string }>()
const emit = defineEmits<{ select: [item: ConflictItem]; remove: [item: ConflictItem]; removeAll: [] }>()
const { t } = useI18n()
</script>

<template>
  <section v-if="items.length > 0" class="rounded-md border border-warning p-3" data-testid="conflicts-panel" :aria-label="t('diagrams.viewpointChange.panelLabel')">
    <div class="flex flex-wrap items-center gap-2">
      <h2 class="text-sm font-semibold text-highlighted" data-testid="conflicts-title">{{ t('diagrams.viewpointChange.panelTitle', { count: items.length }) }}</h2>
      <UButton
        v-if="editable"
        class="ms-auto"
        data-testid="conflicts-remove-all"
        size="xs"
        color="neutral"
        variant="outline"
        icon="i-lucide-square-minus"
        :label="t('diagrams.viewpointChange.removeAll')"
        @click="emit('removeAll')"
      />
    </div>
    <p class="mt-1 text-xs text-muted">{{ t('diagrams.viewpointChange.panelHint') }}</p>
    <ul class="mt-2 flex flex-col gap-1">
      <li v-for="item in items" :key="`${item.kind}-${item.id}`" class="flex flex-wrap items-center gap-2 text-sm" :data-testid="`conflict-item-${item.id}`">
        <UButton
          size="xs"
          color="neutral"
          :variant="item.id === selectedId ? 'subtle' : 'ghost'"
          :aria-pressed="item.id === selectedId"
          :label="item.name"
          :aria-label="t(item.kind === 'shape' ? 'diagrams.viewpointChange.selectShape' : 'diagrams.viewpointChange.selectEdge', { name: item.name })"
          :data-testid="`conflict-select-${item.id}`"
          @click="emit('select', item)"
        />
        <span class="text-xs text-muted">{{ t(item.kind === 'shape' ? 'diagrams.viewpointChange.kindShape' : 'diagrams.viewpointChange.kindEdge') }}</span>
        <UButton
          v-if="editable"
          class="ms-auto"
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-x"
          :label="t('diagrams.viewpointChange.remove')"
          :aria-label="t('diagrams.viewpointChange.removeNamed', { name: item.name })"
          :data-testid="`conflict-remove-${item.id}`"
          @click="emit('remove', item)"
        />
      </li>
    </ul>
  </section>
</template>
