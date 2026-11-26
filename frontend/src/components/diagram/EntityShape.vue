<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import { NodeResizer, type OnResizeEnd } from '@vue-flow/node-resizer'
import { nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CANVAS_LIMITS } from '../../api/diagramView'
import type { ShapeData } from './canvasModel'

// A shape of the canvas (SCR-021; design D6 of us-074-diagram-canvas): a box with the name, the
// abbreviation of the metatype and the visual hint of its mapping as border color. A new shape
// shows an input for its name; an incomplete entity names its missing mandatory properties.
const props = defineProps<{ id: string; data: ShapeData; selected?: boolean }>()
const { t } = useI18n()

const input = ref<HTMLInputElement>()
const draft = ref('')

onMounted(async () => {
  if (props.data.editing) {
    await nextTick()
    input.value?.focus()
  }
})

function commit() {
  props.data.rename(draft.value)
}

function resizeEnd(event: OnResizeEnd) {
  props.data.resized(event.params)
}
</script>

<template>
  <div
    class="flex h-full w-full flex-col gap-0.5 overflow-hidden rounded-md border-2 bg-default px-2 text-xs text-highlighted shadow-sm"
    :class="[data.container ? 'justify-start pt-1' : 'justify-center', selected ? 'ring-2 ring-primary' : '', data.error ? 'border-error!' : 'border-accented']"
    :data-container="data.container ? 'true' : undefined"
    :style="data.hint && !data.error ? { borderColor: data.hint } : undefined"
    :data-testid="`shape-${id}`"
    :data-unsaved="data.unsaved ? 'true' : undefined"
    :data-incomplete="data.missingMandatory.length > 0 ? 'true' : undefined"
    :title="data.error ?? (data.missingMandatory.length > 0 ? t('diagrams.canvas.incomplete', { properties: data.missingMandatory.join(', ') }) : undefined)"
  >
    <template v-if="data.connectable && !data.editing">
      <Handle id="left" type="source" :position="Position.Left" class="size-2.5! border-default! bg-primary!" aria-hidden="true" :title="t('diagrams.canvas.connectFrom')" />
      <Handle id="right" type="source" :position="Position.Right" class="size-2.5! border-default! bg-primary!" aria-hidden="true" :title="t('diagrams.canvas.connectFrom')" />
    </template>
    <NodeResizer v-if="data.editable && !data.editing" :min-width="24" :min-height="16" :is-visible="selected" @resize-end="resizeEnd" />
    <div class="flex items-center gap-1">
      <span v-if="data.abbreviation" class="shrink-0 rounded-sm bg-elevated px-1 font-mono text-[10px] text-default" :title="data.metatypeName">{{ data.abbreviation }}</span>
      <input
        v-if="data.editing"
        ref="input"
        v-model="draft"
        :maxlength="CANVAS_LIMITS.entityName"
        :aria-label="t('diagrams.canvas.newName', { type: data.metatypeName })"
        :placeholder="t('diagrams.canvas.namePlaceholder')"
        data-testid="shape-name-input"
        class="nodrag min-w-0 flex-1 rounded-sm border border-accented bg-default px-1 text-xs"
        @keydown.enter.prevent="commit"
        @keydown.esc.prevent.stop="draft = ''; commit()"
        @keydown.stop
        @blur="commit"
      />
      <span v-else class="min-w-0 flex-1 truncate font-medium" data-testid="shape-name">{{ data.name }}</span>
      <span v-if="data.unsaved && !data.editing" class="size-1.5 shrink-0 rounded-full bg-warning" data-testid="shape-unsaved" role="img" :aria-label="t('diagrams.canvas.unsaved')" :title="t('diagrams.canvas.unsaved')" />
    </div>
    <div v-if="data.missingMandatory.length > 0" class="flex items-center gap-1 truncate text-[10px] text-default" data-testid="shape-incomplete">
      <UIcon name="i-lucide-triangle-alert" class="size-3 shrink-0 text-warning" />
      <span class="truncate">{{ t('diagrams.canvas.incomplete', { properties: data.missingMandatory.join(', ') }) }}</span>
    </div>
    <div v-if="data.error" class="truncate text-[10px] font-medium text-default" role="alert" data-testid="shape-error">{{ data.error }}</div>
  </div>
</template>
