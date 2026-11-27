<script setup lang="ts">
import { computed, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { labelPoint, type Point } from './edgeModel'
import { ptToPx } from './canvasModel'

// The edges of the canvas (SCR-021, US-074 AC6; design D5 of us-074-diagram-connections): straight
// lines through the bend points with an arrow at the target and the name of the connection, drawn into
// the viewport of the canvas in px. An edge is reached with Tab, selected with a click, Enter or
// Space; a double click on it adds a bend point, a bend point is moved by dragging and removed by a
// double click. The lines are computed from the shapes by the canvas (edgeModel.ts), in pt.
export interface EdgeDraw {
  id: string
  label: string
  hint?: string
  /** the line in pt, from the border of the source through the bend points to the border of the target */
  line: Point[]
  /** the bend points of the edge in pt */
  bends: Point[]
  selected: boolean
  unsaved: boolean
  incomplete: string[]
  error?: string
  /** the viewpoint does not allow the type of the connection (design D2 of us-074-diagram-viewpoint-change) */
  conflict?: boolean
  /** a hierarchy line (design D4 of us-074-diagram-nesting): dashed, without an arrowhead and without a name */
  hierarchy?: boolean
}

const props = defineProps<{
  edges: EdgeDraw[]
  editable: boolean
  /** screen position to the position in the viewport of the canvas, in px */
  toFlow: (x: number, y: number) => Point
}>()
const emit = defineEmits<{
  select: [id: string]
  bendAdded: [id: string, point: Point]
  bendMoved: [id: string, index: number, point: Point]
  bendRemoved: [id: string, index: number]
  contextMenu: [id: string, event: MouseEvent]
}>()
const { t } = useI18n()

interface Drawn extends EdgeDraw {
  d: string
  arrow: string
  label_: { x: number; y: number }
  bendsPx: Point[]
}

function px(point: Point): Point {
  return { x: ptToPx(point.x), y: ptToPx(point.y) }
}

/** The triangle at the end of the line, pointing along its last segment. */
function arrowhead(line: Point[]): string {
  const end = line.at(-1)!
  const before = line.at(-2) ?? end
  const angle = Math.atan2(end.y - before.y, end.x - before.x)
  const size = 9
  const wing = (turn: number) => `${end.x - size * Math.cos(angle + turn)},${end.y - size * Math.sin(angle + turn)}`
  return `${end.x},${end.y} ${wing(Math.PI / 7)} ${wing(-Math.PI / 7)}`
}

const drawn = computed<Drawn[]>(() =>
  props.edges.map((edge) => {
    const line = edge.line.map(px)
    return {
      ...edge,
      d: line.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`).join(' '),
      arrow: arrowhead(line),
      label_: px(labelPoint(edge.line)),
      bendsPx: edge.bends.map(px),
    }
  }),
)

function color(edge: Drawn): string {
  return edge.error ? 'var(--ui-error)' : edge.conflict ? 'var(--ui-warning)' : (edge.hint ?? 'var(--ui-text-muted)')
}

function toPt(clientX: number, clientY: number): Point {
  const point = props.toFlow(clientX, clientY)
  return { x: point.x / ptToPx(1), y: point.y / ptToPx(1) }
}

function keydown(event: KeyboardEvent, id: string) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    emit('select', id)
  }
}

let stop: (() => void) | undefined

function dragBend(event: PointerEvent | MouseEvent, id: string, index: number) {
  if (!props.editable) {
    return
  }
  event.preventDefault()
  const move = (next: Event) => {
    const pointer = next as MouseEvent
    emit('bendMoved', id, index, toPt(pointer.clientX, pointer.clientY))
  }
  const end = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', end)
    stop = undefined
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', end)
  stop = end
}

onBeforeUnmount(() => stop?.())
</script>

<template>
  <svg class="pointer-events-none absolute left-0 top-0 h-px w-px overflow-visible" data-testid="canvas-edges">
    <g v-for="edge in drawn" :key="edge.id" :data-testid="`edge-${edge.id}`" :data-unsaved="edge.unsaved ? 'true' : undefined" :data-selected="edge.selected ? 'true' : undefined" :data-error="edge.error ? 'true' : undefined" :data-conflict="edge.conflict ? 'true' : undefined">
      <title v-if="edge.error || edge.conflict || edge.incomplete.length > 0">{{ edge.error ?? (edge.conflict ? t('diagrams.viewpointChange.conflictText') : t('diagrams.canvas.incomplete', { properties: edge.incomplete.join(', ') })) }}</title>
      <path
        :d="edge.d"
        fill="none"
        :stroke="color(edge)"
        :stroke-width="edge.selected ? 3 : 1.5"
        :stroke-dasharray="edge.hierarchy ? '2 4' : edge.unsaved || edge.conflict ? '6 4' : undefined"
        stroke-linejoin="round"
      />
      <polygon v-if="!edge.hierarchy" :points="edge.arrow" :fill="color(edge)" />
      <path
        :d="edge.d"
        fill="none"
        stroke="transparent"
        stroke-width="14"
        class="nopan nodrag pointer-events-auto cursor-pointer outline-none focus-visible:stroke-primary/30"
        style="pointer-events: stroke"
        tabindex="0"
        role="button"
        :aria-label="edge.hierarchy ? t('diagrams.nesting.lineLabel', { name: edge.label }) : edge.conflict ? `${t('diagrams.canvas.edgeLabel', { name: edge.label })}. ${t('diagrams.viewpointChange.conflictText')}` : t('diagrams.canvas.edgeLabel', { name: edge.label })"
        :aria-pressed="edge.selected"
        :data-testid="`edge-hit-${edge.id}`"
        @click.stop="emit('select', edge.id)"
        @focus="emit('select', edge.id)"
        @keydown="keydown($event, edge.id)"
        @contextmenu.prevent.stop="emit('contextMenu', edge.id, $event)"
        @dblclick.stop="editable && !edge.hierarchy && emit('bendAdded', edge.id, toPt($event.clientX, $event.clientY))"
      />
      <text
        v-if="!edge.hierarchy"
        :x="edge.label_.x"
        :y="edge.label_.y - 4"
        text-anchor="middle"
        class="fill-default text-[10px]"
        paint-order="stroke"
        stroke="var(--ui-bg)"
        stroke-width="3"
        aria-hidden="true"
        data-testid="edge-label"
      >
        {{ edge.conflict ? `${edge.label} – ${t('diagrams.viewpointChange.conflictShort')}` : edge.label }}
      </text>
      <template v-if="edge.selected && editable">
        <circle
          v-for="(point, index) in edge.bendsPx"
          :key="index"
          :cx="point.x"
          :cy="point.y"
          r="5"
          class="nopan nodrag pointer-events-auto cursor-move fill-default stroke-primary"
          style="pointer-events: all"
          stroke-width="2"
          :data-testid="`edge-bend-${edge.id}-${index}`"
          @pointerdown.stop="dragBend($event, edge.id, index)"
          @dblclick.stop="emit('bendRemoved', edge.id, index)"
        />
      </template>
    </g>
  </svg>
</template>
