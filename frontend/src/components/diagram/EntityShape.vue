<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import { NodeResizeControl, NodeResizer, ResizeControlVariant, type OnResizeEnd } from '@vue-flow/node-resizer'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CANVAS_LIMITS } from '../../api/diagramView'
import { BAND_WIDTH, eventRings, eventStroke, MARKER_PATHS, type MarkerPath } from './bpmnSymbols'
import { initials, roleChips } from './assignments'
import { ptToPx, type ShapeData } from './canvasModel'

// A shape of the canvas (SCR-021; design D6 of us-074-diagram-canvas, design D3 of
// us-074-bpmn-process-diagrams): a box with the name, the abbreviation of the metatype and the visual hint of
// its mapping as border color, or, in a diagram of the notation `bpmn2`, the BPMN symbol of its notation
// element (task, event, gateway, pool, lane, annotation, group) with its marker. A new shape shows an input
// for its name; an incomplete entity names its missing mandatory properties.
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

const symbol = computed(() => props.data.symbol)
// the assignments (us-075-lane-assignments D6): the badge of the organizational unit and the chips of the roles on a lane or pool, the avatar of the person on a user task
const chips = computed(() => roleChips(props.data.assignments?.roles ?? []))
const person = computed(() => props.data.assignments?.persons[0])
const realizers = computed(() => props.data.assignments?.realizers ?? [])
const carriesBand = computed(() => symbol.value?.kind === 'lane' || symbol.value?.kind === 'pool')
function insertLabel(side: 'before' | 'after'): string {
  const vertical = props.data.insertLane?.vertical === true
  return t(vertical ? (side === 'before' ? 'diagrams.bpmn.insertLeft' : 'diagrams.bpmn.insertRight') : side === 'before' ? 'diagrams.bpmn.insertAbove' : 'diagrams.bpmn.insertBelow')
}
const stroke = computed(() =>
  props.data.error ? 'var(--ui-error)' : props.data.conflict ? 'var(--ui-warning)' : (props.data.hint ?? 'var(--ui-text-muted)'),
)
const markerPaths = computed<MarkerPath[]>(() => (symbol.value?.marker ? MARKER_PATHS[symbol.value.marker] : []))
const bandPx = ptToPx(BAND_WIDTH)
/** the text of an annotation is its description, else its name */
const shown = computed(() => (symbol.value?.kind === 'annotation' && props.data.description ? props.data.description : props.data.name))
const complaint = computed(
  () =>
    props.data.error ??
    (props.data.conflict ? t('diagrams.viewpointChange.conflictText') : undefined) ??
    (props.data.missingMandatory.length > 0 ? t('diagrams.canvas.incomplete', { properties: props.data.missingMandatory.join(', ') }) : undefined),
)
</script>

<template>
  <div
    :class="
      symbol
        ? ['relative h-full w-full text-xs text-highlighted', selected ? 'rounded-md ring-2 ring-primary' : '']
        : [
            'flex h-full w-full flex-col gap-0.5 overflow-hidden rounded-md border-2 bg-default px-2 text-xs text-highlighted shadow-sm',
            data.container ? 'justify-start pt-1' : 'justify-center',
            selected ? 'ring-2 ring-primary' : '',
            data.error ? 'border-error!' : data.conflict ? 'border-warning! border-dashed!' : 'border-accented',
          ]
    "
    :data-container="data.container ? 'true' : undefined"
    :style="!symbol && data.hint && !data.error && !data.conflict ? { borderColor: data.hint } : undefined"
    :data-testid="`shape-${id}`"
    :data-symbol="symbol?.kind"
    :data-unsaved="data.unsaved ? 'true' : undefined"
    :data-incomplete="data.missingMandatory.length > 0 ? 'true' : undefined"
    :data-conflict="data.conflict ? 'true' : undefined"
    :role="symbol ? 'group' : undefined"
    :aria-label="symbol ? `${data.metatypeName}: ${data.name}` : undefined"
    :title="complaint"
  >
    <template v-if="data.connectable && !data.editing">
      <Handle id="left" type="source" :position="Position.Left" class="size-2.5! border-default! bg-primary!" aria-hidden="true" :title="t('diagrams.canvas.connectFrom')" />
      <Handle id="right" type="source" :position="Position.Right" class="size-2.5! border-default! bg-primary!" aria-hidden="true" :title="t('diagrams.canvas.connectFrom')" />
    </template>
    <template v-if="data.editable && !data.editing">
      <NodeResizer v-if="data.resizeEdges === 'all'" :min-width="24" :min-height="16" :is-visible="selected" @resize-end="resizeEnd" />
      <template v-else-if="selected">
        <NodeResizeControl
          v-for="edge in data.resizeEdges"
          :key="edge"
          :position="edge"
          :variant="ResizeControlVariant.Line"
          :min-width="24"
          :min-height="16"
          :data-testid="`resize-${edge}`"
          @resize-end="resizeEnd"
        />
      </template>
    </template>

    <!-- a BPMN symbol (design D3 of us-074-bpmn-process-diagrams) -->
    <template v-if="symbol">
      <svg v-if="symbol.kind === 'event'" class="absolute inset-0 size-full overflow-visible" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true" data-testid="symbol-outline">
        <ellipse cx="50" cy="50" rx="49" ry="49" fill="var(--ui-bg)" :stroke="stroke" :stroke-width="eventStroke(symbol.position)" vector-effect="non-scaling-stroke" />
        <ellipse v-if="eventRings(symbol.position) === 2" cx="50" cy="50" rx="41" ry="41" fill="none" :stroke="stroke" stroke-width="1.5" vector-effect="non-scaling-stroke" />
      </svg>
      <svg v-else-if="symbol.kind === 'data-object'" class="absolute inset-0 size-full overflow-visible" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true" :data-collection="data.collection ? 'true' : undefined" data-testid="symbol-outline">
        <path d="M3 2H68L97 30V98H3Z" fill="var(--ui-bg)" :stroke="stroke" stroke-width="2" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        <path d="M68 2V30H97" fill="none" :stroke="stroke" stroke-width="1.5" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        <path v-if="data.collection" d="M35 72V92M50 72V92M65 72V92" fill="none" :stroke="stroke" stroke-width="2" vector-effect="non-scaling-stroke" data-testid="collection-marker" />
      </svg>
      <svg v-else-if="symbol.kind === 'data-store'" class="absolute inset-0 size-full overflow-visible" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true" data-testid="symbol-outline">
        <path d="M2 14V86A48 12 0 0 0 98 86V14" fill="var(--ui-bg)" :stroke="stroke" stroke-width="2" vector-effect="non-scaling-stroke" />
        <ellipse cx="50" cy="14" rx="48" ry="12" fill="var(--ui-bg)" :stroke="stroke" stroke-width="2" vector-effect="non-scaling-stroke" />
        <path d="M2 28A48 12 0 0 0 98 28" fill="none" :stroke="stroke" stroke-width="1.5" vector-effect="non-scaling-stroke" />
      </svg>
      <svg v-else-if="symbol.kind === 'gateway'" class="absolute inset-0 size-full overflow-visible" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true" data-testid="symbol-outline">
        <polygon points="50,1 99,50 50,99 1,50" fill="var(--ui-bg)" :stroke="stroke" stroke-width="2" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
      </svg>
      <div
        v-else
        class="absolute inset-0 bg-default"
        :class="{
          'rounded-lg border-2': symbol.kind === 'task' || symbol.kind === 'sub-process',
          'rounded-lg border-4': symbol.kind === 'call-activity',
          'border-2': symbol.kind === 'pool' || symbol.kind === 'lane',
          'border-y-2 border-s-2 bg-transparent!': symbol.kind === 'annotation',
          'rounded-lg border-2 border-dashed bg-transparent!': symbol.kind === 'group',
        }"
        :style="{ borderColor: stroke }"
        aria-hidden="true"
        data-testid="symbol-outline"
      />
      <div
        v-if="symbol.kind === 'pool' || symbol.kind === 'lane'"
        class="absolute flex items-center justify-center"
        :class="data.vertical ? 'inset-x-0 top-0 border-b-2 px-1' : 'inset-y-0 start-0 border-e-2'"
        :style="data.vertical ? { height: `${bandPx}px`, borderColor: stroke } : { width: `${bandPx}px`, borderColor: stroke }"
        :data-vertical="data.vertical ? 'true' : undefined"
        data-testid="symbol-band"
      >
        <span v-if="!data.editing" class="truncate font-medium" :class="data.vertical ? 'max-w-full' : 'max-h-full [writing-mode:vertical-rl] rotate-180'" data-testid="shape-name">{{ data.name }}</span>
      </div>
      <svg
        v-if="markerPaths.length > 0"
        class="absolute overflow-visible"
        :class="symbol.kind === 'task' ? 'start-1.5 top-1.5 size-4' : 'inset-[22%] size-[56%]'"
        viewBox="0 0 24 24"
        stroke-width="1.6"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        :data-marker="symbol.marker"
        data-testid="symbol-marker"
      >
        <path
          v-for="(path, index) in markerPaths"
          :key="index"
          :d="path.d"
          :fill="path.fill === 'solid' ? stroke : 'none'"
          :stroke="path.fill === 'background' ? 'var(--ui-bg)' : stroke"
        />
      </svg>
      <button
        v-if="data.collapsible"
        type="button"
        class="nodrag absolute bottom-1 left-1/2 flex size-4 -translate-x-1/2 items-center justify-center rounded-sm border border-accented bg-default text-xs leading-none hover:bg-elevated"
        :aria-label="data.collapsed ? t('diagrams.bpmn.expandShape', { name: data.name }) : t('diagrams.bpmn.collapseShape', { name: data.name })"
        :title="data.collapsed ? t('diagrams.bpmn.expand') : t('diagrams.bpmn.collapse')"
        :aria-expanded="!data.collapsed"
        data-testid="shape-toggle"
        @click.stop="data.toggle()"
      >
        {{ data.collapsed ? '+' : '−' }}
      </button>
      <span v-else-if="data.collapsed" class="absolute bottom-1 left-1/2 flex size-4 -translate-x-1/2 items-center justify-center rounded-sm border border-accented text-xs leading-none" aria-hidden="true" data-testid="shape-collapsed-marker">+</span>
      <template v-if="data.insertLane">
        <button
          v-for="side in (['before', 'after'] as const)"
          :key="side"
          type="button"
          class="nodrag absolute z-10 flex size-5 items-center justify-center rounded-full border border-accented bg-default text-muted shadow-sm hover:bg-elevated hover:text-highlighted"
          :class="
            data.insertLane.vertical
              ? ['top-1/2 -translate-y-1/2', side === 'before' ? '-start-2.5' : '-end-2.5']
              : ['left-1/2 -translate-x-1/2', side === 'before' ? '-top-2.5' : '-bottom-2.5']
          "
          :aria-label="insertLabel(side)"
          :title="insertLabel(side)"
          :data-testid="`lane-insert-${side}`"
          @click.stop="data.insertLane.insert(side)"
        >
          <UIcon :name="data.insertLane.vertical ? 'i-lucide-between-vertical-start' : 'i-lucide-between-horizontal-start'" class="size-3.5" />
        </button>
      </template>
      <div
        class="absolute flex flex-col items-center gap-0.5"
        :class="{
          'inset-0 justify-center px-2 text-center': symbol.label === 'inside' && symbol.kind !== 'annotation',
          'inset-0 justify-center ps-3 pe-1 text-start': symbol.kind === 'annotation',
          'inset-x-0 top-0 px-2 pt-1 text-center': symbol.label === 'top',
          'left-1/2 top-full mt-0.5 w-max max-w-40 -translate-x-1/2 text-center': symbol.label === 'below',
          'inset-x-12 top-2 text-center': symbol.label === 'band' && data.editing && !data.vertical,
          'inset-x-2 top-9 text-center': symbol.label === 'band' && data.editing && data.vertical,
          'end-1 top-1 max-w-60 items-end text-end': symbol.label === 'band' && !data.editing && !data.vertical,
          'end-1 top-9 max-w-60 items-end text-end': symbol.label === 'band' && !data.editing && data.vertical,
        }"
      >
        <input
          v-if="data.editing"
          ref="input"
          v-model="draft"
          :maxlength="CANVAS_LIMITS.entityName"
          :aria-label="t('diagrams.canvas.newName', { type: data.metatypeName })"
          :placeholder="t('diagrams.canvas.namePlaceholder')"
          data-testid="shape-name-input"
          class="nodrag min-w-0 rounded-sm border border-accented bg-default px-1 text-xs"
          @keydown.enter.prevent="commit"
          @keydown.esc.prevent.stop="draft = ''; commit()"
          @keydown.stop
          @blur="commit"
        />
        <span v-else-if="symbol.label !== 'band'" class="min-w-0 font-medium" :class="symbol.kind === 'annotation' ? 'line-clamp-4 whitespace-pre-line' : 'line-clamp-3 break-words'" data-testid="shape-name">{{ shown }}</span>
        <div v-if="realizers.length > 0 && !data.editing" class="flex max-w-full flex-wrap justify-center gap-1" data-testid="shape-realizers">
          <span
            v-for="realizer in realizers"
            :key="`${realizer.entityId ?? realizer.newEntityKey}`"
            class="nodrag inline-flex max-w-full items-center gap-0.5 rounded-full border border-accented bg-elevated px-1.5 text-[10px] text-default"
            :data-pending="realizer.pending ? 'true' : undefined"
            data-testid="realizer-badge"
            :title="t('diagrams.dataObjects.realizedBy')"
          >
            <UIcon name="i-lucide-server" class="size-3 shrink-0" />
            <span class="truncate">{{ realizer.name }}</span>
            <button
              v-if="data.assignments?.removable(realizer)"
              type="button"
              class="rounded-full leading-none hover:text-highlighted"
              :aria-label="t('diagrams.assignments.remove', { name: realizer.name })"
              data-testid="realizer-remove"
              @click.stop="data.assignments.remove(realizer)"
            >
              ×
            </button>
          </span>
        </div>
        <div v-if="carriesBand && data.assignments && !data.editing && (data.assignments.orgUnit || chips.shown.length > 0)" class="flex max-w-full flex-wrap justify-end gap-1" data-testid="shape-assignments">
          <span v-if="data.assignments.orgUnit" class="nodrag inline-flex max-w-full items-center gap-0.5 rounded-full border border-accented bg-elevated px-1.5 text-[10px] text-default" :data-pending="data.assignments.orgUnit.pending ? 'true' : undefined" data-testid="assignment-badge" :title="t('diagrams.assignments.orgUnit')">
            <UIcon name="i-lucide-building-2" class="size-3 shrink-0" />
            <span class="truncate">{{ data.assignments.orgUnit.name }}</span>
            <button
              v-if="data.assignments.removable(data.assignments.orgUnit)"
              type="button"
              class="rounded-full leading-none hover:text-highlighted"
              :aria-label="t('diagrams.assignments.remove', { name: data.assignments.orgUnit.name })"
              data-testid="assignment-remove"
              @click.stop="data.assignments.remove(data.assignments.orgUnit)"
            >
              ×
            </button>
          </span>
          <span
            v-for="role in chips.shown"
            :key="`${role.entityId ?? role.newEntityKey}`"
            class="nodrag inline-flex max-w-full items-center gap-0.5 rounded-full border border-default bg-default px-1.5 text-[10px] text-default"
            :data-pending="role.pending ? 'true' : undefined"
            data-testid="assignment-chip"
            :title="t('diagrams.assignments.role')"
          >
            <span class="truncate">{{ role.name }}</span>
            <button
              v-if="data.assignments.removable(role)"
              type="button"
              class="rounded-full leading-none hover:text-highlighted"
              :aria-label="t('diagrams.assignments.remove', { name: role.name })"
              data-testid="assignment-remove"
              @click.stop="data.assignments.remove(role)"
            >
              ×
            </button>
          </span>
          <span v-if="chips.more > 0" class="inline-flex items-center rounded-full border border-default px-1.5 text-[10px] text-muted" data-testid="assignment-more" :title="data.assignments.roles.slice(3).map((role) => role.name).join(', ')">+{{ chips.more }}</span>
        </div>
        <span
          v-if="person && !data.editing"
          class="absolute end-1 top-1 flex size-5 items-center justify-center rounded-full bg-primary text-[9px] font-semibold text-inverted"
          :title="person.name"
          :aria-label="t('diagrams.assignments.responsible', { name: person.name })"
          role="img"
          data-testid="assignment-avatar"
        >
          {{ initials(person.name) }}
        </span>
        <span v-if="data.unsaved && !data.editing" class="size-1.5 shrink-0 rounded-full bg-warning" data-testid="shape-unsaved" role="img" :aria-label="t('diagrams.canvas.unsaved')" :title="t('diagrams.canvas.unsaved')" />
        <div v-if="data.missingMandatory.length > 0" class="flex items-center gap-1 text-[10px] text-default" data-testid="shape-incomplete">
          <UIcon name="i-lucide-triangle-alert" class="size-3 shrink-0 text-warning" />
          <span class="truncate">{{ t('diagrams.canvas.incomplete', { properties: data.missingMandatory.join(', ') }) }}</span>
        </div>
        <div v-if="data.conflict" class="flex items-center gap-1 text-[10px] text-default" data-testid="shape-conflict">
          <UIcon name="i-lucide-triangle-alert" class="size-3 shrink-0 text-warning" />
          <span class="truncate">{{ t('diagrams.viewpointChange.conflictText') }}</span>
        </div>
        <div v-if="data.error" class="text-[10px] font-medium text-default" role="alert" data-testid="shape-error">{{ data.error }}</div>
      </div>
    </template>

    <!-- the box of every other notation and of an unmapped type -->
    <template v-else>
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
      <div v-if="data.conflict" class="flex items-center gap-1 truncate text-[10px] text-default" data-testid="shape-conflict">
        <UIcon name="i-lucide-triangle-alert" class="size-3 shrink-0 text-warning" />
        <span class="truncate">{{ t('diagrams.viewpointChange.conflictText') }}</span>
      </div>
      <div v-if="data.error" class="truncate text-[10px] font-medium text-default" role="alert" data-testid="shape-error">{{ data.error }}</div>
    </template>
  </div>
</template>
