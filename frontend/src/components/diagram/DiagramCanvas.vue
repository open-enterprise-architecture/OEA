<script setup lang="ts">
import { VueFlow, useVueFlow, type NodeDragEvent, type NodeMouseEvent } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/node-resizer/dist/style.css'
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CANVAS_LIMITS,
  saveDiagramView,
  type DiagramEntity,
  type DiagramEntityDetails,
  type DiagramView,
} from '../../api/diagramView'
import { rateLimitMessage, type BlockedEntity } from '../../api/problem'
import AddExistingDialog from './AddExistingDialog.vue'
import EntityDetailsPanel from './EntityDetailsPanel.vue'
import {
  abbreviation,
  applyEdit,
  applyNewEntityEdit,
  editBlocked,
  fromView,
  hasUnsavedChanges,
  isUnsaved,
  keyStep,
  limitPosition,
  limitSize,
  moved,
  placeExisting,
  placeNew,
  placementBlock,
  ptToPx,
  pxToPt,
  rejectedShapes,
  visualHint,
  writeRequest,
  type CanvasShape,
  type EntityEdits,
  type FieldChange,
  type ShapeData,
} from './canvasModel'
import EntityShape from './EntityShape.vue'

// The canvas editor of a diagram (SCR-021, US-074; design D6 of us-074-diagram-canvas): pan and
// zoom, the palette of the viewpoint, "Add existing", moving, resizing and removing shapes, and
// "Save" of the whole layout. A reader sees the canvas read-only. Positions and sizes are kept in
// pt (canvasModel.ts); the canvas draws them in px. The details panel (design D3 of
// us-074-diagram-entity-details) opens on the selected shape; its changes of the entity are kept
// here with the unsaved changes and saved with the layout.
const props = defineProps<{ view: DiagramView; version: number; editable: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [view: DiagramView]; reload: []; dirty: [dirty: boolean] }>()
const { t } = useI18n()

/** The type of the data a palette entry carries when it is dragged onto the canvas. */
const PALETTE_TYPE = 'application/x-oea-metatype'

const flowId = `diagram-canvas-${useId()}`
const { screenToFlowCoordinate, zoomIn, zoomOut, fitView } = useVueFlow(flowId)

const shapes = ref<CanvasShape[]>(props.view.shapes.map(fromView))
const saved = ref<CanvasShape[]>(props.view.shapes.map(fromView))
const selectedId = ref<string>()
const surface = ref<HTMLElement>()
const adding = ref(false)
const saving = ref(false)
const conflict = ref(false)
const failure = ref<string>()
const rejected = ref(false)
const notice = ref<string>()
const menu = ref<{ x: number; y: number }>()
// the details panel: the changes of the saved entities, the messages of a rejected save, and what a save answered 409
const edits = ref<EntityEdits>({})
const panelOpen = ref(false)
const reloadToken = ref(0)
const fieldErrors = ref(new Map<string, string>())
const staleEntities = ref<{ entityId: number; name: string; currentVersion: number }[]>([])
const blockedEntities = ref<BlockedEntity[]>([])

const selectedShape = computed(() => shapes.value.find((shape) => shape.id === selectedId.value))
const dirty = computed(() => hasUnsavedChanges(shapes.value, saved.value, edits.value))
watch(selectedId, (id) => {
  panelOpen.value = id !== undefined
})
watch(dirty, (value) => emit('dirty', value))

watch(
  () => props.view,
  (view) => adopt(view),
)

function adopt(view: DiagramView) {
  shapes.value = view.shapes.map(fromView)
  saved.value = view.shapes.map(fromView)
  selectedId.value = shapes.value.some((shape) => shape.id === selectedId.value) ? selectedId.value : undefined
  failure.value = undefined
  rejected.value = false
  edits.value = {}
  fieldErrors.value = new Map()
  staleEntities.value = []
  blockedEntities.value = []
  // an open panel reads its entity again: it has a new version after a save
  reloadToken.value += 1
}

const metatypeNames = computed(() => new Map(props.view.viewpoint.allowedMetatypes.map((type) => [type.id, type.name])))

function update(id: string, change: (shape: CanvasShape) => CanvasShape) {
  shapes.value = shapes.value.map((shape) => (shape.id === id ? change(shape) : shape))
}

function rename(id: string, name: string) {
  const shape = shapes.value.find((entry) => entry.id === id)
  if (!shape?.editing) {
    return
  }
  const trimmed = name.trim()
  if (trimmed.length === 0) {
    // a new shape without a name is not kept
    shapes.value = shapes.value.filter((entry) => entry.id !== id)
    return
  }
  update(id, (entry) => ({ ...entry, name: trimmed, editing: false, error: undefined }))
}

function resized(id: string, params: { x: number; y: number; width: number; height: number }) {
  update(id, (shape) => ({
    ...shape,
    x: limitPosition(pxToPt(params.x)),
    y: limitPosition(pxToPt(params.y)),
    width: limitSize(pxToPt(params.width)),
    height: limitSize(pxToPt(params.height)),
    error: undefined,
  }))
}

const nodes = computed(() =>
  shapes.value.map((shape, index) => {
    const data: ShapeData = {
      name: shape.name,
      abbreviation: abbreviation(props.view, shape.metatypeId),
      hint: visualHint(props.view, shape.metatypeId),
      editable: props.editable,
      editing: shape.editing === true,
      unsaved: isUnsaved(shape, saved.value, edits.value),
      missingMandatory: shape.missingMandatory,
      error: shape.error,
      metatypeName: metatypeNames.value.get(shape.metatypeId) ?? '',
      rename: (name) => rename(shape.id, name),
      resized: (params) => resized(shape.id, params),
    }
    return {
      id: shape.id,
      type: 'entity',
      position: { x: ptToPx(shape.x), y: ptToPx(shape.y) },
      style: { width: `${ptToPx(shape.width)}px`, height: `${ptToPx(shape.height)}px` },
      draggable: props.editable,
      selected: shape.id === selectedId.value,
      zIndex: index,
      data,
    }
  }),
)

/** The center of the visible canvas in pt, moved a little for each shape that already has it. */
function visibleCenter(): { x: number; y: number } {
  const rect = surface.value?.getBoundingClientRect()
  const point = screenToFlowCoordinate({ x: (rect?.left ?? 0) + (rect?.width ?? 0) / 2, y: (rect?.top ?? 0) + (rect?.height ?? 0) / 2 })
  return freePlace({ x: pxToPt(point.x), y: pxToPt(point.y) })
}

function freePlace(center: { x: number; y: number }): { x: number; y: number } {
  let place = center
  while (shapes.value.some((shape) => Math.abs(shape.x + shape.width / 2 - place.x) < 1 && Math.abs(shape.y + shape.height / 2 - place.y) < 1)) {
    place = { x: place.x + 10, y: place.y + 10 }
  }
  return place
}

function room(forNew: boolean): boolean {
  const block = placementBlock(shapes.value, forNew)
  notice.value =
    block === 'shapes'
      ? t('diagrams.canvas.tooManyShapes', { count: CANVAS_LIMITS.shapes })
      : block === 'new'
        ? t('diagrams.canvas.tooManyNew', { count: CANVAS_LIMITS.newEntities })
        : undefined
  return block === undefined
}

function addNew(metatypeId: string, at?: { x: number; y: number }) {
  if (!props.editable || !room(true)) {
    return
  }
  const shape = placeNew(props.view, metatypeId, freePlace(at ?? visibleCenter()))
  shapes.value = [...shapes.value, shape]
  selectedId.value = shape.id
}

function addExisting(entity: DiagramEntity) {
  if (!props.editable || !room(false)) {
    return
  }
  const shape = placeExisting(props.view, entity, visibleCenter())
  shapes.value = [...shapes.value, shape]
  selectedId.value = shape.id
}

function removeSelected() {
  if (!props.editable || !selectedId.value) {
    return
  }
  const id = selectedId.value
  const removed = shapes.value.find((shape) => shape.id === id)
  shapes.value = shapes.value.filter((shape) => shape.id !== id)
  if (removed?.entityId !== undefined && !shapes.value.some((shape) => shape.entityId === removed.entityId)) {
    // the changes of an entity that is no longer shown are not saved
    const { [removed.entityId]: _dropped, ...rest } = edits.value
    edits.value = rest
  }
  selectedId.value = undefined
  menu.value = undefined
  notice.value = undefined
}

function dragStop(event: NodeDragEvent) {
  const shape = shapes.value.find((entry) => entry.id === event.node.id)
  if (!shape) {
    return
  }
  const x = limitPosition(pxToPt(event.node.position.x))
  const y = limitPosition(pxToPt(event.node.position.y))
  if (x !== shape.x || y !== shape.y) {
    update(shape.id, (entry) => ({ ...entry, x, y, error: undefined }))
  }
}

function select(event: NodeMouseEvent) {
  selectedId.value = event.node.id
  menu.value = undefined
}

function contextMenu(event: NodeMouseEvent) {
  if (!props.editable || !(event.event instanceof MouseEvent)) {
    return
  }
  event.event.preventDefault()
  selectedId.value = event.node.id
  menu.value = { x: event.event.clientX, y: event.event.clientY }
}

function focusIn(event: FocusEvent) {
  const id = (event.target as HTMLElement | null)?.closest<HTMLElement>('.vue-flow__node')?.dataset.id
  if (id) {
    selectedId.value = id
  }
}

function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    menu.value = undefined
    panelOpen.value = false
  }
  if (!props.editable || !selectedId.value || event.target instanceof HTMLInputElement) {
    return
  }
  const step = keyStep(event.shiftKey)
  const direction: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }
  const delta = direction[event.key]
  if (delta) {
    event.preventDefault()
    update(selectedId.value, (shape) => moved(shape, delta[0], delta[1]))
  } else if (event.key === 'Delete') {
    event.preventDefault()
    removeSelected()
  }
}

function dragOver(event: DragEvent) {
  if (props.editable && event.dataTransfer?.types.includes(PALETTE_TYPE)) {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }
}

function drop(event: DragEvent) {
  const metatypeId = event.dataTransfer?.getData(PALETTE_TYPE)
  if (!props.editable || !metatypeId) {
    return
  }
  event.preventDefault()
  const point = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  addNew(metatypeId, { x: pxToPt(point.x), y: pxToPt(point.y) })
}

function dragPalette(event: DragEvent, metatypeId: string) {
  event.dataTransfer?.setData(PALETTE_TYPE, metatypeId)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

/** A field of the entity of the selected shape was changed in the details panel. */
function changed(change: FieldChange, version: number) {
  const shape = selectedShape.value
  if (!props.editable || !shape) {
    return
  }
  fieldErrors.value = new Map([...fieldErrors.value].filter(([key]) => !key.startsWith(`${shape.entityId ?? shape.newEntity?.key}|`)))
  if (shape.newEntity) {
    update(shape.id, (entry) => applyNewEntityEdit(entry, change))
    return
  }
  const entityId = shape.entityId!
  if (change.value !== undefined && editBlocked(edits.value, entityId)) {
    notice.value = t('diagrams.details.tooManyChanges', { count: CANVAS_LIMITS.entityUpdates })
    return
  }
  notice.value = undefined
  edits.value = applyEdit(edits.value, entityId, version, change)
  if (change.field === 'name') {
    // the shapes of the entity show the name entered, and the saved one again when it is back
    const name = change.value ?? saved.value.find((entry) => entry.entityId === entityId)?.name ?? shape.name
    shapes.value = shapes.value.map((entry) => (entry.entityId === entityId ? { ...entry, name } : entry))
  }
}

/** The panel read an entity: the shapes show its name and which mandatory values it misses. */
function loaded(details: DiagramEntityDetails | undefined) {
  if (!details) {
    return
  }
  const refresh = (entry: CanvasShape): CanvasShape =>
    entry.entityId === details.id && !edits.value[details.id]
      ? { ...entry, name: details.name, missingMandatory: [...details.missingMandatory] }
      : entry
  shapes.value = shapes.value.map(refresh)
  saved.value = saved.value.map(refresh)
}

/** "Reload entity" after a version conflict: the changes of this entity are discarded, the others stay. */
function reloadEntity(entityId: number) {
  const { [entityId]: _discarded, ...rest } = edits.value
  edits.value = rest
  staleEntities.value = staleEntities.value.filter((entry) => entry.entityId !== entityId)
  fieldErrors.value = new Map([...fieldErrors.value].filter(([key]) => !key.startsWith(`${entityId}|`)))
  const shape = shapes.value.find((entry) => entry.entityId === entityId)
  if (shape) {
    selectedId.value = shape.id
    panelOpen.value = true
  }
  reloadToken.value += 1
}

async function save() {
  const request = writeRequest(shapes.value, edits.value)
  saving.value = true
  failure.value = undefined
  rejected.value = false
  staleEntities.value = []
  blockedEntities.value = []
  try {
    const outcome = await saveDiagramView(props.view.diagram.id, request, props.version, props.fetchFn)
    if (outcome.ok) {
      adopt(outcome.value.view)
      emit('saved', outcome.value.view)
      return
    }
    const rejection = outcome.rejection
    if (rejection.status === 412) {
      conflict.value = true
    } else if (rejection.status === 409 && rejection.problemType === 'entity-version-conflict') {
      staleEntities.value = (rejection.conflicts ?? []).map((entry) => ({
        entityId: entry.entityId,
        name: shapes.value.find((shape) => shape.entityId === entry.entityId)?.name ?? String(entry.entityId),
        currentVersion: entry.currentVersion,
      }))
    } else if (rejection.status === 409 && rejection.problemType === 'entity-blocked') {
      blockedEntities.value = rejection.entities ?? []
    } else if (rejection.status === 422) {
      const marks = rejectedShapes(rejection.errors ?? [], request, shapes.value)
      shapes.value = shapes.value.map((shape) => (marks.shapes.has(shape.id) ? { ...shape, error: marks.shapes.get(shape.id) } : shape))
      fieldErrors.value = marks.fields
      rejected.value = true
    } else if (rejection.status === 403) {
      failure.value = t('diagrams.canvas.forbidden')
    } else if (rejection.status === 413) {
      failure.value = t('diagrams.canvas.tooLarge')
    } else if (rejection.status === 404) {
      failure.value = t('diagrams.gone')
    } else {
      failure.value = rateLimitMessage(rejection) ?? t('diagrams.canvas.saveFailed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    saving.value = false
  }
}

function reload() {
  conflict.value = false
  emit('reload')
}

function closeMenu() {
  menu.value = undefined
}

function zoom(action: () => Promise<unknown>) {
  void action().catch(() => undefined)
}

onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>

<template>
  <div class="flex flex-col gap-3" data-testid="diagram-canvas">
    <div class="flex flex-wrap items-center gap-2" role="toolbar" :aria-label="t('diagrams.canvas.toolbar')">
      <template v-if="editable">
        <div class="flex flex-wrap items-center gap-1" role="group" :aria-label="t('diagrams.canvas.palette')" data-testid="canvas-palette">
          <UButton
            v-for="type in view.viewpoint.allowedMetatypes"
            :key="type.id"
            size="xs"
            color="neutral"
            variant="outline"
            draggable="true"
            :label="type.name"
            :aria-label="t('diagrams.canvas.addNew', { name: type.name })"
            :title="t('diagrams.canvas.dragHint')"
            :data-testid="`palette-${type.id}`"
            @dragstart="dragPalette($event, type.id)"
            @click="addNew(type.id)"
          />
        </div>
        <UButton data-testid="canvas-add-existing" size="xs" color="neutral" variant="outline" icon="i-lucide-search" :label="t('diagrams.canvas.addExisting')" @click="adding = true" />
        <UButton data-testid="canvas-remove" size="xs" color="neutral" variant="outline" icon="i-lucide-square-minus" :disabled="!selectedId" :label="t('diagrams.canvas.remove')" @click="removeSelected" />
      </template>
      <div class="ms-auto flex flex-wrap items-center gap-2">
        <UBadge v-if="dirty" data-testid="canvas-unsaved" color="warning" variant="subtle" :label="t('diagrams.canvas.unsavedChanges')" />
        <UBadge v-else-if="!editable" data-testid="canvas-read-only" color="neutral" variant="subtle" :label="t('diagrams.canvas.readOnly')" />
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-zoom-in" :aria-label="t('diagrams.canvas.zoomIn')" data-testid="canvas-zoom-in" @click="zoom(zoomIn)" />
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-zoom-out" :aria-label="t('diagrams.canvas.zoomOut')" data-testid="canvas-zoom-out" @click="zoom(zoomOut)" />
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-maximize" :aria-label="t('diagrams.canvas.fitView')" data-testid="canvas-fit-view" @click="zoom(fitView)" />
        <UButton v-if="editable" data-testid="canvas-save" size="sm" icon="i-lucide-save" :loading="saving" :disabled="!dirty" :label="t('diagrams.canvas.save')" @click="save" />
      </div>
    </div>

    <UAlert v-if="notice" data-testid="canvas-notice" color="warning" variant="subtle" :description="notice" />
    <UAlert v-if="failure" data-testid="canvas-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="rejected" data-testid="canvas-rejected" color="error" variant="subtle" :description="t('diagrams.canvas.rejected')" />
    <UAlert v-if="staleEntities.length > 0" data-testid="canvas-entity-conflict" color="error" variant="subtle" :title="t('diagrams.canvas.entityConflictTitle')">
      <template #description>
        <p>{{ t('diagrams.canvas.entityConflict', { names: staleEntities.map((entry) => entry.name).join(', ') }) }}</p>
        <div class="mt-2 flex flex-wrap gap-2">
          <UButton
            v-for="entry in staleEntities"
            :key="entry.entityId"
            size="xs"
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            :label="t('diagrams.canvas.reloadEntity', { name: entry.name })"
            :data-testid="`canvas-entity-reload-${entry.entityId}`"
            @click="reloadEntity(entry.entityId)"
          />
        </div>
      </template>
    </UAlert>
    <UAlert v-if="blockedEntities.length > 0" data-testid="canvas-entity-blocked" color="error" variant="subtle" :title="t('diagrams.canvas.blockedTitle')">
      <template #description>
        <p v-for="entry in blockedEntities" :key="entry.entityId">
          {{ t('diagrams.canvas.blocked', { name: entry.entityName, issues: entry.issues.map((issue) => `${issue.label} (${issue.solutionName})`).join(', ') }) }}
        </p>
      </template>
    </UAlert>

    <p :id="`${flowId}-hint`" class="sr-only">{{ editable ? t('diagrams.canvas.hint') : t('diagrams.canvas.hintReadOnly') }}</p>
    <div class="flex flex-col gap-3 lg:flex-row">
      <div class="min-w-0 flex-1">
        <div
          ref="surface"
          class="h-[32rem] overflow-hidden rounded-md border border-accented"
          role="region"
          :aria-label="t('diagrams.canvas.label')"
          :aria-describedby="`${flowId}-hint`"
          data-testid="canvas-surface"
          @keydown="keydown"
          @focusin="focusIn"
        >
          <VueFlow
            :id="flowId"
            :nodes="nodes"
            :nodes-draggable="editable"
            :nodes-connectable="false"
            :delete-key-code="null"
            disable-keyboard-a11y
            :zoom-on-double-click="false"
            :min-zoom="0.1"
            :max-zoom="4"
            :fit-view-on-init="view.shapes.length > 0"
            @node-drag-stop="dragStop"
            @node-click="select"
            @node-context-menu="contextMenu"
            @pane-click="selectedId = undefined"
            @dragover="dragOver"
            @drop="drop"
          >
            <template #node-entity="nodeProps">
              <EntityShape :id="nodeProps.id" :data="nodeProps.data" :selected="nodeProps.selected" />
            </template>
          </VueFlow>
        </div>

      </div>
      <EntityDetailsPanel
        v-if="panelOpen && selectedShape && !selectedShape.editing"
        :diagram-id="view.diagram.id"
        :shape="selectedShape"
        :editable="editable"
        :edit="selectedShape.entityId !== undefined ? edits[selectedShape.entityId] : undefined"
        :errors="fieldErrors"
        :reload-token="reloadToken"
        :fetch-fn="fetchFn"
        @change="changed"
        @close="panelOpen = false"
        @loaded="loaded"
      />
    </div>

    <div v-if="menu" role="menu" class="fixed z-50 min-w-48 rounded-md border border-accented bg-default p-1 shadow-lg" :style="{ left: `${menu.x}px`, top: `${menu.y}px` }" data-testid="canvas-menu">
      <button type="button" role="menuitem" class="w-full rounded-sm px-2 py-1.5 text-start text-sm hover:bg-elevated" data-testid="canvas-menu-remove" @click="removeSelected">
        {{ t('diagrams.canvas.remove') }}
      </button>
    </div>

    <AddExistingDialog v-if="editable" v-model:open="adding" :diagram-id="view.diagram.id" :fetch-fn="fetchFn" @pick="addExisting" />

    <UModal v-model:open="conflict" :title="t('diagrams.canvas.conflictTitle')" :description="t('diagrams.canvas.conflict')">
      <template #body>
        <div class="flex justify-end gap-2" data-testid="canvas-conflict">
          <UButton data-testid="canvas-conflict-keep" color="neutral" variant="ghost" :label="t('diagrams.canvas.keepEditing')" @click="conflict = false" />
          <UButton data-testid="canvas-conflict-reload" color="error" :label="t('diagrams.canvas.reload')" @click="reload" />
        </div>
      </template>
    </UModal>
  </div>
</template>
