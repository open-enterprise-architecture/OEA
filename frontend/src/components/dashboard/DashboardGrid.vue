<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  GRID_COLUMNS,
  overlappingIds,
  positionProblem,
  type GridPosition,
  type LayoutPosition,
  type PositionProblem,
  type WidgetConfig,
} from '../../api/dashboards'

// The 12-column grid of a dashboard (US-060, REQ-057; design D4 of us-055-dashboards). The view mode
// places the widgets at their positions. The edit mode moves a widget by its handle and resizes it at
// the corner (pointer), or by the arrangement buttons and the arrow keys on the handle (keyboard);
// every change leaves as one `layout` event. A position that leaves the grid is not sent, `invalid`
// names why. Overlapping widgets get a red border (US-060 AC4); the server accepts overlaps.
const props = defineProps<{ widgets: WidgetConfig[]; editing: boolean }>()
const emit = defineEmits<{
  layout: [positions: LayoutPosition[]]
  invalid: [problem: PositionProblem]
  edit: [widget: WidgetConfig]
  remove: [widget: WidgetConfig]
}>()
const { t } = useI18n()

/** The height of a grid row and the gap between cells, in pixels. */
const ROW_HEIGHT = 112
const GAP = 12

const grid = ref<HTMLElement>()
const selected = ref<string>()

interface Drag {
  id: string
  mode: 'move' | 'resize'
  startX: number
  startY: number
  origin: GridPosition
  cellWidth: number
  cellHeight: number
  current: GridPosition
}
const drag = ref<Drag>()

const overlapping = computed(() => overlappingIds(props.widgets.map((widget) => ({ id: widget.id, position: displayed(widget) }))))

/** The position shown: the one being dragged, kept inside the grid for the display. */
function displayed(widget: WidgetConfig): GridPosition {
  if (drag.value?.id !== widget.id) {
    return widget.position
  }
  const { col, row, width, height } = drag.value.current
  const fitWidth = Math.min(Math.max(width, 1), GRID_COLUMNS)
  return { col: Math.min(Math.max(col, 1), GRID_COLUMNS + 1 - fitWidth), row: Math.max(row, 1), width: fitWidth, height: Math.max(height, 1) }
}

function dragProblem(widget: WidgetConfig): PositionProblem | undefined {
  return drag.value?.id === widget.id ? positionProblem(drag.value.current) : undefined
}

function style(widget: WidgetConfig) {
  const position = displayed(widget)
  return { gridColumn: `${position.col} / span ${position.width}`, gridRow: `${position.row} / span ${position.height}` }
}

/** Sends the position of one widget, unless it is unchanged or outside the grid. */
function commit(widget: WidgetConfig, position: GridPosition) {
  const old = widget.position
  if (old.col === position.col && old.row === position.row && old.width === position.width && old.height === position.height) {
    return
  }
  const problem = positionProblem(position)
  if (problem) {
    emit('invalid', problem)
    return
  }
  emit('layout', [{ widgetId: widget.id, ...position }])
}

function nudge(widget: WidgetConfig, dCol: number, dRow: number, dWidth: number, dHeight: number) {
  const { col, row, width, height } = widget.position
  commit(widget, { col: col + dCol, row: row + dRow, width: width + dWidth, height: height + dHeight })
}

function arrow(widget: WidgetConfig, event: KeyboardEvent) {
  const moves: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }
  const step = moves[event.key]
  if (!step) {
    return
  }
  event.preventDefault()
  if (event.shiftKey) {
    nudge(widget, 0, 0, step[0], step[1])
  } else {
    nudge(widget, step[0], step[1], 0, 0)
  }
}

function begin(event: PointerEvent, widget: WidgetConfig, mode: Drag['mode']) {
  if (!props.editing || (event.button ?? 0) !== 0 || !grid.value) {
    return
  }
  const rect = grid.value.getBoundingClientRect()
  drag.value = {
    id: widget.id,
    mode,
    startX: event.clientX,
    startY: event.clientY,
    origin: { ...widget.position },
    cellWidth: (rect.width + GAP) / GRID_COLUMNS,
    cellHeight: ROW_HEIGHT + GAP,
    current: { ...widget.position },
  }
  selected.value = widget.id
  event.preventDefault()
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', end)
  window.addEventListener('pointercancel', cancel)
}

function move(event: PointerEvent) {
  const state = drag.value
  if (!state) {
    return
  }
  const dCols = Math.round((event.clientX - state.startX) / state.cellWidth)
  const dRows = Math.round((event.clientY - state.startY) / state.cellHeight)
  const { col, row, width, height } = state.origin
  state.current =
    state.mode === 'move'
      ? { col: Math.max(col + dCols, 1), row: Math.max(row + dRows, 1), width, height }
      : { col, row, width: Math.max(width + dCols, 1), height: Math.max(height + dRows, 1) }
}

function stop() {
  window.removeEventListener('pointermove', move)
  window.removeEventListener('pointerup', end)
  window.removeEventListener('pointercancel', cancel)
}

function end() {
  const state = drag.value
  drag.value = undefined
  stop()
  const widget = props.widgets.find((candidate) => candidate.id === state?.id)
  if (state && widget) {
    commit(widget, state.current)
  }
}

function cancel() {
  drag.value = undefined
  stop()
}

onBeforeUnmount(stop)

const arrangements = [
  { key: 'left', icon: 'i-lucide-arrow-left', delta: [-1, 0, 0, 0] },
  { key: 'right', icon: 'i-lucide-arrow-right', delta: [1, 0, 0, 0] },
  { key: 'up', icon: 'i-lucide-arrow-up', delta: [0, -1, 0, 0] },
  { key: 'down', icon: 'i-lucide-arrow-down', delta: [0, 1, 0, 0] },
  { key: 'narrower', icon: 'i-lucide-chevrons-right-left', delta: [0, 0, -1, 0] },
  { key: 'wider', icon: 'i-lucide-chevrons-left-right', delta: [0, 0, 1, 0] },
  { key: 'shorter', icon: 'i-lucide-chevrons-down-up', delta: [0, 0, 0, -1] },
  { key: 'taller', icon: 'i-lucide-chevrons-up-down', delta: [0, 0, 0, 1] },
] as const
</script>

<template>
  <div
    ref="grid"
    data-testid="dashboard-grid"
    class="grid grid-cols-12 gap-3"
    :style="{ gridAutoRows: `${ROW_HEIGHT}px` }"
  >
    <section
      v-for="widget in props.widgets"
      :key="widget.id"
      :data-testid="`widget-${widget.id}`"
      :data-widget-title="widget.title"
      :data-overlap="overlapping.has(widget.id) ? 'true' : undefined"
      :aria-labelledby="`widget-title-${widget.id}`"
      class="relative flex min-h-0 flex-col overflow-hidden rounded-lg border bg-default"
      :class="overlapping.has(widget.id) || dragProblem(widget) ? 'border-error ring-1 ring-error' : 'border-default'"
      :style="style(widget)"
      @focusin="selected = widget.id"
    >
      <header class="flex items-center gap-1 border-b border-default px-2 py-1">
        <button
          v-if="props.editing"
          type="button"
          :data-testid="`move-${widget.id}`"
          :aria-label="t('dashboard.widget.move', { title: widget.title })"
          class="cursor-grab touch-none rounded p-1 text-muted hover:bg-elevated focus-visible:outline-2 focus-visible:outline-primary"
          @pointerdown="begin($event, widget, 'move')"
          @keydown="arrow(widget, $event)"
        >
          <UIcon name="i-lucide-grip-vertical" class="size-4" aria-hidden="true" />
        </button>
        <h2 :id="`widget-title-${widget.id}`" class="min-w-0 flex-1 truncate text-sm font-medium text-highlighted">{{ widget.title }}</h2>
        <span
          v-if="overlapping.has(widget.id)"
          :data-testid="`overlap-${widget.id}`"
          class="inline-flex items-center gap-1 text-xs text-error"
        >
          <UIcon name="i-lucide-layers" class="size-3.5" aria-hidden="true" />
          {{ t('dashboard.overlaps') }}
        </span>
        <template v-if="props.editing">
          <UButton
            :data-testid="`edit-${widget.id}`"
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-pencil"
            :aria-label="t('dashboard.widget.edit', { title: widget.title })"
            @click="emit('edit', widget)"
          />
          <UButton
            :data-testid="`remove-${widget.id}`"
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-trash-2"
            :aria-label="t('dashboard.widget.remove', { title: widget.title })"
            @click="emit('remove', widget)"
          />
        </template>
      </header>
      <div class="min-h-0 flex-1 overflow-auto p-2" tabindex="0">
        <slot name="widget" :widget="widget" />
      </div>
      <div
        v-if="props.editing && selected === widget.id"
        :data-testid="`arrange-${widget.id}`"
        role="group"
        :aria-label="t('dashboard.widget.arrange', { title: widget.title })"
        class="flex flex-wrap gap-1 border-t border-default bg-elevated px-2 py-1"
      >
        <UButton
          v-for="arrangement in arrangements"
          :key="arrangement.key"
          :data-testid="`arrange-${arrangement.key}-${widget.id}`"
          size="xs"
          color="neutral"
          variant="outline"
          :icon="arrangement.icon"
          :aria-label="t(`dashboard.widget.${arrangement.key}`)"
          :title="t(`dashboard.widget.${arrangement.key}`)"
          @click="nudge(widget, arrangement.delta[0], arrangement.delta[1], arrangement.delta[2], arrangement.delta[3])"
        />
      </div>
      <div
        v-if="props.editing"
        :data-testid="`resize-${widget.id}`"
        aria-hidden="true"
        class="absolute right-0 bottom-0 size-4 cursor-nwse-resize touch-none rounded-tl bg-primary/30"
        @pointerdown="begin($event, widget, 'resize')"
      />
    </section>
  </div>
</template>
