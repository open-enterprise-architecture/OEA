<script setup lang="ts">
import { ConnectionMode, VueFlow, useVueFlow, type NodeDragEvent, type NodeMouseEvent } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/node-resizer/dist/style.css'
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CANVAS_LIMITS,
  fetchAllowedConnections,
  fetchDiagramEntityChildren,
  fetchDiagramEntityDetails,
  findDiagramConnections,
  saveDiagramView,
  type AllowedConnectionResult,
  type DiagramEntity,
  type DiagramEntityDetails,
  type DiagramView,
} from '../../api/diagramView'
import { rateLimitMessage, type BlockedEntity } from '../../api/problem'
import AddExistingDialog from './AddExistingDialog.vue'
import ConnectedElementDialog from './ConnectedElementDialog.vue'
import ConnectionEdges, { type EdgeDraw } from './ConnectionEdges.vue'
import ConnectionOfferDialog, { type OfferedConnection } from './ConnectionOfferDialog.vue'
import ChildrenQuestionDialog from './ChildrenQuestionDialog.vue'
import ConflictsPanel, { type ConflictItem } from './ConflictsPanel.vue'
import ConnectionTypeDialog from './ConnectionTypeDialog.vue'
import DeleteFromRepositoryDialog from './DeleteFromRepositoryDialog.vue'
import EntityDetailsPanel from './EntityDetailsPanel.vue'
import NewParentDialog from './NewParentDialog.vue'
import RemoveContainerDialog from './RemoveContainerDialog.vue'
import ViewpointChangeDialog from './ViewpointChangeDialog.vue'
import { bpmnSymbol, flowStyle, PALETTE_GROUPS, paletteGroup, type PaletteGroup } from './bpmnSymbols'
import {
  abbreviation,
  applyEdit,
  bpmnFlowWarnings,
  canContain,
  applyNewEntityEdit,
  containerAt,
  descendantsOf,
  editBlocked,
  effectiveParent,
  fromView,
  fromViewLine,
  growContainers,
  hasUnsavedChanges,
  isEdgeUnsaved,
  isBpmnView,
  isUnsaved,
  keyStep,
  layoutChildren,
  levelOf,
  limitPosition,
  limitSize,
  moveTree,
  nestBlock,
  notationElementOf,
  nestShape,
  parentOfShape,
  sameParent,
  withoutShapeLines,
  placeExisting,
  placeNew,
  placementBlock,
  ptToPx,
  pxToPt,
  defaultSize,
  rejectedShapes,
  visualHint,
  writeRequest,
  type CanvasLine,
  type CanvasShape,
  type EntityEdits,
  type FieldChange,
  type ParentChanges,
  type ParentTarget,
  type ShapeData,
} from './canvasModel'
import {
  addBendPoint,
  applyNewConnectionEdit,
  connectedCenter,
  connectedOptions,
  connectionLength,
  connectionOptions,
  edgeBlock,
  edgeLabel,
  edgeLine,
  edgeSubject,
  fromViewEdge,
  hierarchyPath,
  moveBendPoint,
  newEdge,
  removeBendPoint,
  withoutShapeEdges,
  type CanvasEdge,
  type ConnectedOption,
  type ConnectionOption,
  type Point,
} from './edgeModel'
import EntityShape from './EntityShape.vue'

// The canvas editor of a diagram (SCR-021, US-074; design D6 of us-074-diagram-canvas): pan and
// zoom, the palette of the viewpoint, "Add existing", moving, resizing and removing shapes, and
// "Save" of the whole layout. A reader sees the canvas read-only. Positions and sizes are kept in
// pt (canvasModel.ts); the canvas draws them in px. The details panel (design D3 of
// us-074-diagram-entity-details) opens on the selected shape; its changes of the entity are kept
// here with the unsaved changes and saved with the layout. The edges (design D5 of
// us-074-diagram-connections) are lines between shapes: drawn from a handle to another shape, shown on
// request when an existing entity is placed, or added with a connected new element. A holder of
// entity:delete may delete the entity of a shape or edge from the repository (design D6 of
// s17-entity-delete): after the confirmation it is deleted at once, outside the explicit save, and the
// shapes and edges of everything deleted leave the canvas.
const props = defineProps<{ view: DiagramView; version: number; editable: boolean; canDelete?: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [view: DiagramView]; reload: []; dirty: [dirty: boolean] }>()
const { t } = useI18n()

/** The type of the data a palette entry carries when it is dragged onto the canvas. */
const PALETTE_TYPE = 'application/x-oea-metatype'

const flowId = `diagram-canvas-${useId()}`
const { screenToFlowCoordinate, zoomIn, zoomOut, fitView } = useVueFlow(flowId)

const shapes = ref<CanvasShape[]>(props.view.shapes.map(fromView))
const saved = ref<CanvasShape[]>(props.view.shapes.map(fromView))
const edges = ref<CanvasEdge[]>(props.view.edges.map(fromViewEdge))
const savedEdges = ref<CanvasEdge[]>(props.view.edges.map(fromViewEdge))
// nesting (design D4 of us-074-diagram-nesting): the hierarchy lines, the unsaved parent changes of saved
// entities, and the questions of the editor
const lines = ref<CanvasLine[]>(props.view.hierarchyLines.map(fromViewLine))
const savedLines = ref<CanvasLine[]>(props.view.hierarchyLines.map(fromViewLine))
const parents = ref<ParentChanges>({})
const childrenAsk = ref<string>()
const childrenOpen = ref(false)
// BPMN (design D3 to D6 of us-074-bpmn-process-diagrams): the descriptions the details panel read (shown by text annotations)
const descriptions = ref<Record<number, string>>({})
const parentAsk = ref<{ shapeId: string; target: ParentTarget; parentName?: string }>()
const parentOpen = ref(false)
const removeAsk = ref<{ ids: string[]; name: string; count: number; all?: boolean }>()
const removeOpen = ref(false)
// viewpoint conflicts (design D5 of us-074-diagram-viewpoint-change): the dialog "Change viewpoint", the shapes and
// edges a save named as conflicts although the view did not mark them, and the notice that the save is blocked
const changeOpen = ref(false)
const flagged = ref(new Set<string>())
const blockedByConflicts = ref(false)
const selectedId = ref<string>()
const selectedEdgeId = ref<string>()
const surface = ref<HTMLElement>()
const adding = ref(false)
const saving = ref(false)
const conflict = ref(false)
const failure = ref<string>()
const rejected = ref(false)
const notice = ref<string>()
const menu = ref<{ x: number; y: number; edge: boolean }>()
// drawing connections: the type choice, "Add connected element" and the offer of existing connections
const typeChoice = ref<{ source: CanvasShape; target: CanvasShape; options: ConnectionOption[] }>()
const typeOpen = ref(false)
const connectedOpen = ref(false)
const connectedList = ref<ConnectedOption[]>([])
const connectedLoading = ref(false)
const connectedFailed = ref(false)
const offer = ref<{ source: CanvasShape; target: CanvasShape; id: number; name: string; metatypeId: string }[]>([])
const offerOpen = ref(false)
// "Delete from repository": the entity to delete and its confirmation
const deleting = ref<number>()
const deleteOpen = ref(false)
/** the answers of the metamodel for pairs of metatypes, for the session of the editor */
const allowedCache = new Map<string, AllowedConnectionResult>()
// the details panel: the changes of the saved entities, the messages of a rejected save, and what a save answered 409
const edits = ref<EntityEdits>({})
const panelOpen = ref(false)
const reloadToken = ref(0)
const fieldErrors = ref(new Map<string, string>())
const staleEntities = ref<{ entityId: number; name: string; currentVersion: number }[]>([])
const blockedEntities = ref<BlockedEntity[]>([])

const selectedShape = computed(() => shapes.value.find((shape) => shape.id === selectedId.value))
const selectedEdge = computed(() => edges.value.find((edge) => edge.id === selectedEdgeId.value))
const subject = computed(() => selectedShape.value ?? (selectedEdge.value ? edgeSubject(selectedEdge.value, shapes.value) : undefined))
const dirty = computed(() => hasUnsavedChanges(shapes.value, saved.value, edits.value, edges.value, savedEdges.value, lines.value, savedLines.value, parents.value))
watch([selectedId, selectedEdgeId], ([shape, edge]) => {
  panelOpen.value = shape !== undefined || edge !== undefined
})
watch(dirty, (value) => emit('dirty', value))

watch(
  () => props.view,
  (view) => adopt(view),
)

function adopt(view: DiagramView) {
  shapes.value = view.shapes.map(fromView)
  saved.value = view.shapes.map(fromView)
  edges.value = view.edges.map(fromViewEdge)
  savedEdges.value = view.edges.map(fromViewEdge)
  lines.value = view.hierarchyLines.map(fromViewLine)
  savedLines.value = view.hierarchyLines.map(fromViewLine)
  parents.value = {}
  descriptions.value = {}
  selectedId.value = shapes.value.some((shape) => shape.id === selectedId.value) ? selectedId.value : undefined
  selectedEdgeId.value = edges.value.some((edge) => edge.id === selectedEdgeId.value) ? selectedEdgeId.value : undefined
  offer.value = []
  failure.value = undefined
  rejected.value = false
  flagged.value = new Set()
  blockedByConflicts.value = false
  edits.value = {}
  fieldErrors.value = new Map()
  staleEntities.value = []
  blockedEntities.value = []
  // an open panel reads its entity again: it has a new version after a save
  reloadToken.value += 1
}

const metatypeNames = computed(() => new Map(props.view.viewpoint.allowedMetatypes.map((type) => [type.id, type.name])))
const bpmn = computed(() => isBpmnView(props.view))
/** Whether a shape takes nested shapes: every shape, but in a `bpmn2` diagram only pool, lane and sub-process (design D5). */
const mayContain = computed(() => canContain(props.view))
const notation = computed(() => props.view.viewpoint.notation)

/** The groups of the palette of a `bpmn2` diagram, by the notation element of the type; empty groups are left out (design D4). */
const paletteGroups = computed(() => {
  if (!bpmn.value) {
    return undefined
  }
  const grouped = new Map<PaletteGroup, typeof props.view.viewpoint.allowedMetatypes>()
  for (const type of props.view.viewpoint.allowedMetatypes) {
    const group = paletteGroup(notationElementOf(props.view, type.id))
    grouped.set(group, [...(grouped.get(group) ?? []), type])
  }
  return PALETTE_GROUPS.filter((group) => grouped.has(group)).map((group) => ({ id: group, types: grouped.get(group)! }))
})

/** The warnings of the pool rules of the flows for the layout as it is now (design D6); never stored, never sent. */
const flowWarnings = computed(() => (bpmn.value ? bpmnFlowWarnings(shapes.value, edges.value, props.view.viewpoint.effectiveMappings) : new Map()))
const warningText = (kind: 'sequence-crosses-pool' | 'message-within-pool') =>
  kind === 'sequence-crosses-pool' ? t('diagrams.bpmn.sequenceCrossesPool') : t('diagrams.bpmn.messageWithinPool')
function selectFirstWarning() {
  const first = edges.value.find((edge) => flowWarnings.value.has(edge.id))
  if (first) {
    selectEdge(first.id)
  }
}

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
    // a new shape without a name is not kept, nor are its edges
    dropShape(id)
    return
  }
  update(id, (entry) => ({ ...entry, name: trimmed, editing: false, error: undefined }))
}

function resized(id: string, params: { x: number; y: number; width: number; height: number }) {
  // the position of a nested shape is relative to its container: the model keeps it absolute
  const shape = shapes.value.find((entry) => entry.id === id)
  const container = shape?.parentShapeId ? shapes.value.find((entry) => entry.id === shape.parentShapeId) : undefined
  update(id, (entry) => ({
    ...entry,
    x: limitPosition(pxToPt(params.x) + (container?.x ?? 0)),
    y: limitPosition(pxToPt(params.y) + (container?.y ?? 0)),
    width: limitSize(pxToPt(params.width)),
    height: limitSize(pxToPt(params.height)),
    error: undefined,
  }))
  // a container keeps its children and grows with its own container
  shapes.value = growContainers(fitAround(shapes.value, id))
}

/** A container resized smaller than what is nested in it keeps the room of its nested shapes. */
function fitAround(list: CanvasShape[], id: string): CanvasShape[] {
  const inside = descendantsOf(list, id)
  const shape = list.find((entry) => entry.id === id)
  if (!shape || inside.length === 0) {
    return list
  }
  const left = Math.min(shape.x, ...inside.map((entry) => entry.x))
  const top = Math.min(shape.y, ...inside.map((entry) => entry.y))
  const right = Math.max(shape.x + shape.width, ...inside.map((entry) => entry.x + entry.width))
  const bottom = Math.max(shape.y + shape.height, ...inside.map((entry) => entry.y + entry.height))
  return list.map((entry) =>
    entry.id === id ? { ...entry, x: left, y: top, width: limitSize(right - left), height: limitSize(bottom - top) } : entry,
  )
}

/**
 * Removes a shape with what is nested in it and their edges and lines; the changes of an entity or
 * connection that nothing shows any more are discarded.
 */
function dropShape(id: string) {
  const gone = new Set([id, ...descendantsOf(shapes.value, id).map((shape) => shape.id)])
  shapes.value = shapes.value.filter((shape) => !gone.has(shape.id))
  edges.value = [...gone].reduce((rest, shapeId) => withoutShapeEdges(rest, shapeId), edges.value)
  lines.value = withoutShapeLines(lines.value, gone)
  pruneEdits()
}

function pruneEdits() {
  const shown = new Set<number>()
  shapes.value.forEach((shape) => shape.entityId !== undefined && shown.add(shape.entityId))
  edges.value.forEach((edge) => edge.connectionId !== undefined && shown.add(edge.connectionId))
  const kept = Object.fromEntries(Object.entries(edits.value).filter(([id]) => shown.has(Number(id))))
  if (Object.keys(kept).length !== Object.keys(edits.value).length) {
    edits.value = kept
  }
  const keptParents = Object.fromEntries(Object.entries(parents.value).filter(([id]) => shown.has(Number(id))))
  if (Object.keys(keptParents).length !== Object.keys(parents.value).length) {
    parents.value = keptParents
  }
}

function selectShape(id: string | undefined) {
  selectedId.value = id
  selectedEdgeId.value = undefined
}

function selectEdge(id: string | undefined) {
  selectedEdgeId.value = id
  selectedId.value = undefined
  menu.value = undefined
}

const nodes = computed(() => {
  const byId = new Map(shapes.value.map((shape) => [shape.id, shape]))
  return shapes.value.map((shape, index) => {
    // the canvas takes the position of a nested shape relative to its container
    const container = shape.parentShapeId ? byId.get(shape.parentShapeId) : undefined
    const data: ShapeData = {
      name: shape.name,
      abbreviation: abbreviation(props.view, shape.metatypeId),
      hint: visualHint(props.view, shape.metatypeId),
      editable: props.editable,
      connectable: props.editable,
      editing: shape.editing === true,
      unsaved: isUnsaved(shape, saved.value, edits.value, parents.value),
      missingMandatory: shape.missingMandatory,
      error: shape.error,
      conflict: isConflict(shape),
      metatypeName: metatypeNames.value.get(shape.metatypeId) ?? '',
      container: shapes.value.some((entry) => entry.parentShapeId === shape.id),
      symbol: bpmnSymbol(notation.value, notationElementOf(props.view, shape.metatypeId)),
      description:
        shape.newEntity?.description ?? (shape.entityId !== undefined ? (edits.value[shape.entityId]?.description ?? descriptions.value[shape.entityId]) : undefined),
      rename: (name) => rename(shape.id, name),
      resized: (params) => resized(shape.id, params),
    }
    return {
      id: shape.id,
      type: 'entity',
      position: { x: ptToPx(shape.x - (container?.x ?? 0)), y: ptToPx(shape.y - (container?.y ?? 0)) },
      ...(container ? { parentNode: container.id } : {}),
      style: { width: `${ptToPx(shape.width)}px`, height: `${ptToPx(shape.height)}px` },
      draggable: props.editable,
      selected: shape.id === selectedId.value,
      zIndex: index,
      data,
    }
  })
})

/** A shape or edge the viewpoint does not allow, as the view marked it or a rejected save named it. */
function isConflict(item: { id: string; conflict?: boolean }): boolean {
  return item.conflict === true || flagged.value.has(item.id)
}

const conflictItems = computed<ConflictItem[]>(() => [
  ...shapes.value.filter(isConflict).map((shape): ConflictItem => ({ id: shape.id, kind: 'shape', name: shape.name })),
  ...edges.value.filter(isConflict).map((edge): ConflictItem => ({ id: edge.id, kind: 'edge', name: edgeLabel(edge, shapes.value) })),
])

const lineDraws = computed<EdgeDraw[]>(() =>
  lines.value.flatMap((line) => {
    const path = hierarchyPath(line, shapes.value)
    const parent = shapes.value.find((shape) => shape.id === line.parentShapeId)
    const child = shapes.value.find((shape) => shape.id === line.childShapeId)
    return path
      ? [
          {
            id: line.id,
            label: `${parent?.name ?? ''} → ${child?.name ?? ''}`,
            line: path,
            bends: [],
            selected: line.id === selectedEdgeId.value,
            unsaved: !savedLines.value.some((entry) => entry.id === line.id),
            incomplete: [],
            hierarchy: true,
          },
        ]
      : []
  }),
)

const edgeDraws = computed<EdgeDraw[]>(() => [
  ...lineDraws.value,
  ...edges.value.flatMap((edge) => {
    const line = edgeLine(edge, shapes.value)
    return line
      ? [
          {
            id: edge.id,
            label: edgeLabel(edge, shapes.value),
            hint: visualHint(props.view, edge.metatypeId),
            line,
            bends: edge.points,
            selected: edge.id === selectedEdgeId.value,
            unsaved: isEdgeUnsaved(edge, savedEdges.value, edits.value),
            incomplete: edge.missingMandatory,
            error: edge.error,
            conflict: isConflict(edge),
            flow: flowStyle(notation.value, notationElementOf(props.view, edge.metatypeId)),
            ...(flowWarnings.value.has(edge.id) ? { warning: warningText(flowWarnings.value.get(edge.id)!) } : {}),
          },
        ]
      : []
  }),
])

function toFlow(x: number, y: number): Point {
  return screenToFlowCoordinate({ x, y })
}

function updateEdge(id: string, change: (edge: CanvasEdge) => CanvasEdge) {
  edges.value = edges.value.map((edge) => (edge.id === id ? change(edge) : edge))
}

function bendAdded(id: string, point: Point) {
  const edge = edges.value.find((entry) => entry.id === id)
  const line = edge ? edgeLine(edge, shapes.value) : undefined
  if (!props.editable || !edge || !line) {
    return
  }
  const added = addBendPoint(edge, line, point)
  notice.value = added ? undefined : t('diagrams.canvas.tooManyBendPoints', { count: CANVAS_LIMITS.bendPoints })
  if (added) {
    updateEdge(id, () => added)
  }
}

function bendMoved(id: string, index: number, point: Point) {
  updateEdge(id, (edge) => moveBendPoint(edge, index, point))
}

function bendRemoved(id: string, index: number) {
  updateEdge(id, (edge) => removeBendPoint(edge, index))
}

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
  if (at) {
    dropInto(shape, { x: shape.x + shape.width / 2, y: shape.y + shape.height / 2 })
  }
  selectShape(shape.id)
}

/**
 * A new element dropped onto a container is nested in it and created as a child of the container's entity,
 * whatever the setting says (design D4 of us-074-diagram-nesting).
 */
function dropInto(shape: CanvasShape, center: { x: number; y: number }) {
  const container = containerAt(shapes.value, center, new Set([shape.id]), mayContain.value)
  if (!container || nestBlock(shapes.value, shape.id, container.id)) {
    return
  }
  const target = parentOfShape(container)
  shapes.value = nestShape(shapes.value, shape.id, container.id).map((entry) =>
    entry.id === shape.id && entry.newEntity
      ? {
          ...entry,
          newEntity: {
            ...entry.newEntity,
            parentEntityId: target && 'entityId' in target ? target.entityId : undefined,
            parentNewEntityKey: target && 'key' in target ? target.key : undefined,
          },
        }
      : entry,
  )
}

function addExisting(entity: DiagramEntity) {
  if (!props.editable || !room(false)) {
    return
  }
  const shape = placeExisting(props.view, entity, visibleCenter())
  shapes.value = [...shapes.value, shape]
  selectShape(shape.id)
  void offerConnections(shape)
  if ((shape.childCount ?? 0) > 0 && mayContain.value(shape)) {
    childrenAsk.value = shape.id
    childrenOpen.value = true
  }
}

/**
 * "Show children" (design D4 of us-074-diagram-nesting): the direct children that the viewpoint allows and
 * the diagram does not show yet are nested in the shape in a grid and the shape grows; a child shown
 * elsewhere gets the composition connection of the pair as an edge where the viewpoint allows its type, else
 * a hierarchy line. A notice names the children of a type the viewpoint does not allow and says when the list
 * was cut off.
 */
async function showChildren(shapeId: string | undefined) {
  childrenOpen.value = false
  childrenAsk.value = undefined
  const container = shapes.value.find((shape) => shape.id === shapeId)
  if (!props.editable || !container || container.entityId === undefined) {
    return
  }
  try {
    const outcome = await fetchDiagramEntityChildren(props.view.diagram.id, container.entityId, props.fetchFn)
    if (!outcome.ok) {
      notice.value = t('diagrams.nesting.childrenFailed')
      return
    }
    const current = shapes.value.find((shape) => shape.id === container.id)
    if (!current) {
      return
    }
    const messages: string[] = []
    const shown = new Map<number, CanvasShape>()
    shapes.value.forEach((shape) => shape.entityId !== undefined && !shown.has(shape.entityId) && shown.set(shape.entityId, shape))
    const nestedHere = new Set(descendantsOf(shapes.value, current.id).map((shape) => shape.entityId))
    const fresh = outcome.value.items.filter((item) => item.allowed && !shown.has(item.entity.id))
    const refused = outcome.value.items.filter((item) => !item.allowed && !shown.has(item.entity.id))
    const room = Math.max(0, CANVAS_LIMITS.shapes - shapes.value.length)
    const placing = fresh.slice(0, room)
    if (levelOf(shapes.value, current.id) + 1 > CANVAS_LIMITS.nesting && placing.length > 0) {
      messages.push(t('diagrams.nesting.tooDeep', { count: CANVAS_LIMITS.nesting }))
    } else {
      if (placing.length < fresh.length) messages.push(t('diagrams.canvas.tooManyShapes', { count: CANVAS_LIMITS.shapes }))
      const layout = layoutChildren(props.view, shapes.value, current, placing.map((item) => item.entity))
      shapes.value = layout.shapes
    }
    if (refused.length > 0) {
      messages.push(t('diagrams.nesting.notAllowed', { names: refused.map((item) => item.entity.name).join(', ') }))
    }
    // children shown elsewhere: the composition connection as an edge, else a hierarchy line
    for (const item of outcome.value.items) {
      const there = shown.get(item.entity.id)
      if (!there || nestedHere.has(item.entity.id) || there.id === current.id) continue
      const connection = item.compositionConnection
      if (connection?.allowed) {
        const exists = edges.value.some((edge) => edge.connectionId === connection.connection.id && edge.sourceShapeId === current.id && edge.targetShapeId === there.id)
        if (!exists && !edgeBlock(edges.value, false)) {
          edges.value = [...edges.value, newEdge(current.id, there.id, { connectionId: connection.connection.id, name: connection.connection.name, metatypeId: connection.connection.metatypeId })]
        }
      } else if (!lines.value.some((line) => line.parentShapeId === current.id && line.childShapeId === there.id) && lines.value.length < CANVAS_LIMITS.hierarchyLines) {
        lines.value = [...lines.value, { id: crypto.randomUUID(), parentShapeId: current.id, childShapeId: there.id }]
      }
    }
    if (outcome.value.truncated) {
      messages.push(t('diagrams.nesting.truncated', { count: CANVAS_LIMITS.children }))
    }
    notice.value = messages.length > 0 ? messages.join(' ') : undefined
  } catch {
    notice.value = t('common.backendUnreachable')
  }
}

/**
 * The offer "N connections available" (design D5 of us-074-diagram-connections): when a shape of an
 * existing entity is placed, the connections of the viewpoint between it and the entities already
 * shown. Nothing is added without a choice; a connection that already has an edge between the shapes
 * is not offered again.
 */
async function offerConnections(placed: CanvasShape) {
  offer.value = []
  if (placed.entityId === undefined) {
    return
  }
  const others = [...new Set(shapes.value.map((shape) => shape.entityId).filter((id): id is number => id !== undefined && id !== placed.entityId))]
  try {
    const outcome = await findDiagramConnections(props.view.diagram.id, [placed.entityId], others, props.fetchFn)
    if (!outcome.ok) {
      return
    }
    const found: typeof offer.value = []
    for (const item of outcome.value.items) {
      const [sourceId, targetId] = [item.sourceEntityId, item.targetEntityId]
      const ends = (entityId: number) => (entityId === placed.entityId ? placed : shapes.value.find((shape) => shape.entityId === entityId))
      const source = ends(sourceId)
      const target = ends(targetId)
      const shown = edges.value.some((edge) => edge.connectionId === item.connection.id && edge.sourceShapeId === source?.id && edge.targetShapeId === target?.id)
      const twice = found.some((entry) => entry.id === item.connection.id && entry.source.id === source?.id && entry.target.id === target?.id)
      if (source && target && !shown && !twice) {
        found.push({ source, target, id: item.connection.id, name: item.connection.name, metatypeId: item.connection.metatypeId })
      }
    }
    offer.value = found
  } catch {
    // the offer is a convenience: without an answer nothing is offered
  }
}

/** The edges of the chosen offered connections are added, unsaved. */
function showOffered(ids?: number[]) {
  const chosen = offer.value.filter((entry) => ids === undefined || ids.includes(entry.id))
  const block = edgeBlock(edges.value, false, chosen.length)
  if (block) {
    notice.value = t('diagrams.canvas.tooManyEdges', { count: CANVAS_LIMITS.edges })
    return
  }
  notice.value = undefined
  edges.value = [...edges.value, ...chosen.map((entry) => newEdge(entry.source.id, entry.target.id, { connectionId: entry.id, name: entry.name, metatypeId: entry.metatypeId }))]
  offer.value = []
  offerOpen.value = false
}

const offered = computed<OfferedConnection[]>(() =>
  offer.value.map((entry) => ({
    id: entry.id,
    label: `${entry.name}`,
    metatypeName: props.view.viewpoint.allowedRelations.find((type) => type.id === entry.metatypeId)?.name ?? '',
  })),
)

/** Whether another edge may be added; says why not. */
function roomForEdge(forNew: boolean): boolean {
  const block = edgeBlock(edges.value, forNew)
  notice.value =
    block === 'edges'
      ? t('diagrams.canvas.tooManyEdges', { count: CANVAS_LIMITS.edges })
      : block === 'new'
        ? t('diagrams.canvas.tooManyNewConnections', { count: CANVAS_LIMITS.newConnections })
        : undefined
  return block === undefined
}

async function allowedBetween(source: string, target: string): Promise<AllowedConnectionResult | undefined> {
  const key = `${source}|${target}`
  const known = allowedCache.get(key)
  if (known) {
    return known
  }
  const outcome = await fetchAllowedConnections(source, target, props.fetchFn)
  if (!outcome.ok) {
    return undefined
  }
  allowedCache.set(key, outcome.value)
  return outcome.value
}

/** A line was drawn from the handle of one shape to another: the type of the connection is chosen among the allowed ones. */
async function connect(params: { source: string; target: string }) {
  const source = shapes.value.find((shape) => shape.id === params.source)
  const target = shapes.value.find((shape) => shape.id === params.target)
  if (!props.editable || !source || !target || source.editing || target.editing || !roomForEdge(true)) {
    return
  }
  try {
    const result = await allowedBetween(source.metatypeId, target.metatypeId)
    if (!result) {
      notice.value = t('diagrams.connections.loadFailed')
      return
    }
    const options = connectionOptions(props.view, result)
    if (options.length === 0) {
      notice.value = t('diagrams.canvas.noConnectionAllowed')
    } else if (options.length === 1) {
      drawConnection(source, target, options[0]!)
    } else {
      typeChoice.value = { source, target, options }
      typeOpen.value = true
    }
  } catch {
    notice.value = t('common.backendUnreachable')
  }
}

function drawConnection(source: CanvasShape, target: CanvasShape, option: ConnectionOption) {
  typeOpen.value = false
  typeChoice.value = undefined
  if (!roomForEdge(true)) {
    return
  }
  // a type that runs only from the target to the source is drawn from the shape that was dragged to
  const [from, to] = option.reversed ? [target, source] : [source, target]
  const edge = newEdge(from.id, to.id, { metatypeId: option.metatypeId, newKey: crypto.randomUUID() })
  edges.value = [...edges.value, edge]
  selectEdge(edge.id)
}

/** "Add connected element": the allowed connection and target types of the selected shape are read, then chosen. */
async function openConnected() {
  const source = selectedShape.value
  if (!props.editable || !source || source.editing) {
    return
  }
  connectedOpen.value = true
  connectedLoading.value = true
  connectedFailed.value = false
  connectedList.value = []
  try {
    const results = new Map<string, AllowedConnectionResult>()
    await Promise.all(
      props.view.viewpoint.allowedMetatypes.map(async (target) => {
        const result = await allowedBetween(source.metatypeId, target.id)
        if (!result) throw new Error('not loaded')
        results.set(target.id, result)
      }),
    )
    connectedList.value = connectedOptions(props.view, results)
  } catch {
    connectedFailed.value = true
  } finally {
    connectedLoading.value = false
  }
}

/** A new element at the default length of the connection type, to the right of the source, with its connection. */
function addConnected(connectionId: string, targetId: string) {
  const source = selectedShape.value
  connectedOpen.value = false
  if (!props.editable || !source || !room(true) || !roomForEdge(true)) {
    return
  }
  const center = connectedCenter(source, connectionLength(props.view, connectionId), defaultSize(props.view, targetId))
  const shape = placeNew(props.view, targetId, center)
  shapes.value = [...shapes.value, shape]
  edges.value = [...edges.value, newEdge(source.id, shape.id, { metatypeId: connectionId, newKey: crypto.randomUUID() })]
  selectShape(shape.id)
}

/** The stored entity or connection of the selected shape or edge; none for a new one that is not saved yet. */
const deletableEntityId = computed(() => (selectedShape.value ? selectedShape.value.entityId : selectedEdge.value?.connectionId))

function startRepositoryDelete() {
  const entityId = deletableEntityId.value
  if (!props.canDelete || entityId === undefined || selectedShape.value?.editing) {
    return
  }
  menu.value = undefined
  deleting.value = entityId
  deleteOpen.value = true
}

/**
 * The entity and its cascade were deleted: their shapes and edges leave the canvas, with the edges
 * and unsaved connections that end at them and the unsaved changes of those entities. The other
 * unsaved changes stay; the stored shapes stay hidden in the repository and show again after a restore.
 */
function repositoryDeleted(ids: number[]) {
  const gone = new Set(ids)
  const removed = new Set(shapes.value.filter((shape) => shape.entityId !== undefined && gone.has(shape.entityId)).map((shape) => shape.id))
  const keepShape = (shape: CanvasShape) => !removed.has(shape.id)
  // what was nested in a deleted shape stays on the canvas, without a container
  const free = (shape: CanvasShape): CanvasShape => (shape.parentShapeId && removed.has(shape.parentShapeId) ? { ...shape, parentShapeId: undefined } : shape)
  const keepEdge = (edge: CanvasEdge) =>
    !(edge.connectionId !== undefined && gone.has(edge.connectionId)) && !removed.has(edge.sourceShapeId) && !removed.has(edge.targetShapeId)
  shapes.value = shapes.value.filter(keepShape).map(free)
  saved.value = saved.value.filter(keepShape).map(free)
  lines.value = withoutShapeLines(lines.value, removed)
  savedLines.value = withoutShapeLines(savedLines.value, removed)
  edges.value = edges.value.filter(keepEdge)
  savedEdges.value = savedEdges.value.filter(keepEdge)
  edits.value = Object.fromEntries(Object.entries(edits.value).filter(([id]) => !gone.has(Number(id))))
  parents.value = Object.fromEntries(Object.entries(parents.value).filter(([id]) => !gone.has(Number(id))))
  pruneEdits()
  if (selectedId.value && removed.has(selectedId.value)) {
    selectedId.value = undefined
  }
  if (selectedEdgeId.value && !edges.value.some((edge) => edge.id === selectedEdgeId.value) && !lines.value.some((line) => line.id === selectedEdgeId.value)) {
    selectedEdgeId.value = undefined
  }
  deleting.value = undefined
  notice.value = undefined
}

function removeSelected() {
  if (!props.editable) {
    return
  }
  if (selectedEdgeId.value) {
    removeEdge(selectedEdgeId.value)
  } else if (selectedId.value) {
    if (!removeShape(selectedId.value)) {
      menu.value = undefined
      return
    }
  } else {
    return
  }
  selectedId.value = undefined
  selectedEdgeId.value = undefined
  menu.value = undefined
  notice.value = undefined
}

/** Only the edge or line goes: the connection stays in the repository, the hierarchy stays as it is. */
function removeEdge(id: string) {
  edges.value = edges.value.filter((edge) => edge.id !== id)
  lines.value = lines.value.filter((line) => line.id !== id)
  pruneEdits()
}

/** Removes a shape; the shapes nested in a container leave the diagram with it, so that is asked first. Returns whether it was removed. */
function removeShape(id: string): boolean {
  const nestedCount = descendantsOf(shapes.value, id).length
  if (nestedCount > 0) {
    removeAsk.value = { ids: [id], name: shapes.value.find((shape) => shape.id === id)?.name ?? '', count: nestedCount }
    removeOpen.value = true
    return false
  }
  dropShape(id)
  return true
}

/** "Remove from diagram" of a conflict (design D5 of us-074-diagram-viewpoint-change): the removal of the canvas. */
function removeConflict(item: ConflictItem) {
  if (!props.editable) {
    return
  }
  if (item.kind === 'edge') {
    removeEdge(item.id)
  } else if (!removeShape(item.id)) {
    return
  }
  selectedId.value = undefined
  selectedEdgeId.value = undefined
  notice.value = undefined
}

/** "Remove all conflicts": every conflicting shape and edge; the allowed shapes nested in a conflicting container are asked about. */
function removeAllConflicts() {
  if (!props.editable) {
    return
  }
  const conflicting = shapes.value.filter(isConflict).map((shape) => shape.id)
  const gone = new Set(conflicting)
  conflicting.forEach((id) => descendantsOf(shapes.value, id).forEach((shape) => gone.add(shape.id)))
  const nested = gone.size - conflicting.length
  if (nested > 0) {
    removeAsk.value = { ids: conflicting, name: t('diagrams.viewpointChange.allName'), count: nested, all: true }
    removeOpen.value = true
    return
  }
  conflicting.forEach(dropShape)
  edges.value.filter(isConflict).forEach((edge) => removeEdge(edge.id))
  selectedId.value = undefined
  selectedEdgeId.value = undefined
  notice.value = undefined
}

function selectConflict(item: ConflictItem) {
  if (item.kind === 'shape') {
    selectShape(item.id)
  } else {
    selectEdge(item.id)
  }
}

/** The viewpoint of the diagram was changed (and saved at once): the page reads the view of the new viewpoint. */
function viewpointChanged(next: DiagramView) {
  emit('saved', next)
}

function dragStop(event: NodeDragEvent) {
  const shape = shapes.value.find((entry) => entry.id === event.node.id)
  if (!shape) {
    return
  }
  // the canvas names the position of a nested shape relative to its container; the computed one is absolute
  const x = limitPosition(pxToPt(event.node.computedPosition.x))
  const y = limitPosition(pxToPt(event.node.computedPosition.y))
  if (x !== shape.x || y !== shape.y) {
    shapes.value = moveTree(shapes.value, shape.id, round(x - shape.x), round(y - shape.y))
  }
  settle(shape.id)
}

function round(value: number): number {
  return Math.round(value * 100) / 100
}

/**
 * A shape was dropped: the shape whose box holds its center is its container (none outside every shape). A
 * new container asks "New parent?" when the setting asks and the entity has another parent; where the center
 * stays in the container the shape had, that container grows to hold the shape.
 */
function settle(id: string) {
  const shape = shapes.value.find((entry) => entry.id === id)
  if (!shape || shape.editing) {
    return
  }
  const own = new Set([id, ...descendantsOf(shapes.value, id).map((entry) => entry.id)])
  const container = containerAt(shapes.value, { x: shape.x + shape.width / 2, y: shape.y + shape.height / 2 }, own, mayContain.value)
  if ((container?.id ?? undefined) === shape.parentShapeId) {
    shapes.value = growContainers(shapes.value)
    return
  }
  const block = container ? nestBlock(shapes.value, id, container.id) : undefined
  if (block === 'depth') {
    notice.value = t('diagrams.nesting.tooDeep', { count: CANVAS_LIMITS.nesting })
    shapes.value = growContainers(shapes.value)
    return
  }
  notice.value = undefined
  shapes.value = nestShape(shapes.value, id, container?.id)
  askParent(id, container)
}

/** The question "New parent?" (US-135): only when the setting asks and the new container's entity is not the parent. */
function askParent(id: string, container: CanvasShape | undefined) {
  const shape = shapes.value.find((entry) => entry.id === id)
  if (!shape || !props.view.askNewParent || (shape.entityId === undefined && !shape.newEntity)) {
    return
  }
  const target = parentOfShape(container)
  if (sameParent(effectiveParent(shape, parents.value), target)) {
    return
  }
  parentAsk.value = { shapeId: id, target, parentName: container?.name }
  parentOpen.value = true
}

/** "Yes": the parent of the entity changes with the next save; the version is the one the editor read. */
async function parentConfirmed() {
  const ask = parentAsk.value
  parentOpen.value = false
  parentAsk.value = undefined
  const shape = shapes.value.find((entry) => entry.id === ask?.shapeId)
  if (!ask || !shape) {
    return
  }
  if (shape.newEntity) {
    const target = ask.target
    update(shape.id, (entry) => ({
      ...entry,
      newEntity: {
        ...entry.newEntity!,
        parentEntityId: target && 'entityId' in target ? target.entityId : undefined,
        parentNewEntityKey: target && 'key' in target ? target.key : undefined,
      },
    }))
    return
  }
  const entityId = shape.entityId!
  let version: number | undefined = edits.value[entityId]?.version ?? parents.value[entityId]?.version
  if (version === undefined) {
    try {
      version = (await fetchDiagramEntityDetails(props.view.diagram.id, entityId, props.fetchFn))?.version
    } catch {
      version = undefined
    }
  }
  if (version === undefined) {
    notice.value = t('diagrams.nesting.parentFailed')
    return
  }
  const original: ParentTarget = shape.parentEntityId ? { entityId: shape.parentEntityId } : null
  const { [entityId]: _previous, ...rest } = parents.value
  // the parent of the view again: nothing to save
  parents.value = sameParent(ask.target, original) ? rest : { ...rest, [entityId]: { version, parent: ask.target } }
}

function parentDeclined() {
  parentOpen.value = false
  parentAsk.value = undefined
}

function select(event: NodeMouseEvent) {
  selectShape(event.node.id)
  menu.value = undefined
}

function contextMenu(event: NodeMouseEvent) {
  if (!(props.editable || props.canDelete) || !(event.event instanceof MouseEvent)) {
    return
  }
  event.event.preventDefault()
  selectShape(event.node.id)
  menu.value = { x: event.event.clientX, y: event.event.clientY, edge: false }
}

function edgeMenu(id: string, event: MouseEvent) {
  if (!(props.editable || props.canDelete)) {
    return
  }
  selectEdge(id)
  menu.value = { x: event.clientX, y: event.clientY, edge: true }
}

function focusIn(event: FocusEvent) {
  const id = (event.target as HTMLElement | null)?.closest<HTMLElement>('.vue-flow__node')?.dataset.id
  if (id) {
    selectShape(id)
  }
}

function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    menu.value = undefined
    panelOpen.value = false
  }
  if (event.key === 'Delete' && event.ctrlKey && props.canDelete && (selectedId.value || selectedEdgeId.value) && !(event.target instanceof HTMLInputElement)) {
    event.preventDefault()
    startRepositoryDelete()
    return
  }
  if (!props.editable || (!selectedId.value && !selectedEdgeId.value) || event.target instanceof HTMLInputElement) {
    return
  }
  const step = keyStep(event.shiftKey)
  const direction: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }
  const delta = direction[event.key]
  if (delta && selectedId.value) {
    event.preventDefault()
    shapes.value = growContainers(moveTree(shapes.value, selectedId.value, delta[0], delta[1]))
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

/** A field of the entity or connection of the selected shape or edge was changed in the details panel. */
function changed(change: FieldChange, version: number) {
  const shape = selectedShape.value
  const edge = selectedEdge.value
  if (!props.editable || (!shape && !edge)) {
    return
  }
  const entityId = shape ? shape.entityId : edge?.connectionId
  const key = shape ? shape.newEntity?.key : edge?.newConnection?.key
  fieldErrors.value = new Map([...fieldErrors.value].filter(([name]) => !name.startsWith(`${entityId ?? key}|`)))
  if (entityId === undefined) {
    if (shape) update(shape.id, (entry) => applyNewEntityEdit(entry, change))
    else if (edge) updateEdge(edge.id, (entry) => applyNewConnectionEdit(entry, change))
    return
  }
  if (change.value !== undefined && editBlocked(edits.value, entityId)) {
    notice.value = t('diagrams.details.tooManyChanges', { count: CANVAS_LIMITS.entityUpdates })
    return
  }
  notice.value = undefined
  edits.value = applyEdit(edits.value, entityId, version, change)
  if (change.field === 'name') {
    // the shapes and edges of the entity show the name entered, and the saved one again when it is back
    const name =
      change.value ?? saved.value.find((entry) => entry.entityId === entityId)?.name ?? savedEdges.value.find((entry) => entry.connectionId === entityId)?.name ?? shape?.name ?? edge?.name ?? ''
    shapes.value = shapes.value.map((entry) => (entry.entityId === entityId ? { ...entry, name } : entry))
    edges.value = edges.value.map((entry) => (entry.connectionId === entityId ? { ...entry, name } : entry))
  }
}

/** The panel read an entity or connection: the shapes and edges show its name and which mandatory values it misses. */
function loaded(details: DiagramEntityDetails | undefined) {
  if (!details) {
    return
  }
  const refresh = (entry: CanvasShape): CanvasShape =>
    entry.entityId === details.id && !edits.value[details.id]
      ? { ...entry, name: details.name, missingMandatory: [...details.missingMandatory] }
      : entry
  const refreshEdge = (entry: CanvasEdge): CanvasEdge =>
    entry.connectionId === details.id && !edits.value[details.id]
      ? { ...entry, name: details.name, missingMandatory: [...details.missingMandatory] }
      : entry
  descriptions.value = { ...descriptions.value, [details.id]: details.description }
  shapes.value = shapes.value.map(refresh)
  saved.value = saved.value.map(refresh)
  edges.value = edges.value.map(refreshEdge)
  savedEdges.value = savedEdges.value.map(refreshEdge)
}

/** "Reload entity" after a version conflict: the changes of this entity are discarded, the others stay. */
function reloadEntity(entityId: number) {
  const { [entityId]: _discarded, ...rest } = edits.value
  edits.value = rest
  const { [entityId]: _parent, ...otherParents } = parents.value
  parents.value = otherParents
  staleEntities.value = staleEntities.value.filter((entry) => entry.entityId !== entityId)
  fieldErrors.value = new Map([...fieldErrors.value].filter(([key]) => !key.startsWith(`${entityId}|`)))
  const shape = shapes.value.find((entry) => entry.entityId === entityId)
  const edge = edges.value.find((entry) => entry.connectionId === entityId)
  if (shape) {
    selectShape(shape.id)
    panelOpen.value = true
  } else if (edge) {
    selectEdge(edge.id)
    panelOpen.value = true
  }
  reloadToken.value += 1
}

async function save() {
  const request = writeRequest(shapes.value, edits.value, edges.value, lines.value, parents.value)
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
    } else if (rejection.status === 409 && rejection.problemType === 'diagram-conflicts') {
      // the layout still holds shapes or edges of a type the viewpoint does not allow: they are named, the list opens
      const named = new Set<string>()
      for (const field of rejection.invalidFields) {
        const shape = /^shapes\[(\d+)]/.exec(field)
        const edge = /^edges\[(\d+)]/.exec(field)
        const id = shape ? request.shapes[Number(shape[1])]?.id : edge ? request.edges?.[Number(edge[1])]?.id : undefined
        if (id) named.add(id)
      }
      flagged.value = named
      blockedByConflicts.value = true
    } else if (rejection.status === 409 && rejection.problemType === 'entity-version-conflict') {
      staleEntities.value = (rejection.conflicts ?? []).map((entry) => ({
        entityId: entry.entityId,
        name: shapes.value.find((shape) => shape.entityId === entry.entityId)?.name ?? String(entry.entityId),
        currentVersion: entry.currentVersion,
      }))
    } else if (rejection.status === 409 && rejection.problemType === 'entity-blocked') {
      blockedEntities.value = rejection.entities ?? []
    } else if (rejection.status === 422) {
      const marks = rejectedShapes(rejection.errors ?? [], request, shapes.value, edges.value)
      shapes.value = shapes.value.map((shape) => (marks.shapes.has(shape.id) ? { ...shape, error: marks.shapes.get(shape.id) } : shape))
      edges.value = edges.value.map((edge) => (marks.edges.has(edge.id) ? { ...edge, error: marks.edges.get(edge.id) } : edge))
      fieldErrors.value = marks.fields
      rejected.value = true
    } else if (rejection.status === 403) {
      failure.value = (request.newConnections?.length ?? 0) > 0 ? t('diagrams.canvas.forbiddenConnection') : t('diagrams.canvas.forbidden')
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

function removeConfirmed() {
  const ask = removeAsk.value
  removeOpen.value = false
  removeAsk.value = undefined
  if (!ask) {
    return
  }
  ask.ids.forEach(dropShape)
  if (ask.all) {
    edges.value.filter(isConflict).forEach((edge) => removeEdge(edge.id))
  }
  selectedId.value = undefined
  selectedEdgeId.value = undefined
  notice.value = undefined
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
          <template v-for="group in paletteGroups ?? [{ id: undefined, types: view.viewpoint.allowedMetatypes }]" :key="group.id ?? 'all'">
            <div
              class="flex flex-wrap items-center gap-1"
              :class="group.id ? 'rounded-md border border-default px-1.5 py-1' : ''"
              :role="group.id ? 'group' : undefined"
              :aria-label="group.id ? t(`diagrams.bpmn.group.${group.id}`) : undefined"
              :data-testid="group.id ? `palette-group-${group.id}` : undefined"
            >
              <span v-if="group.id" class="text-[10px] font-medium uppercase text-muted" aria-hidden="true">{{ t(`diagrams.bpmn.group.${group.id}`) }}</span>
              <UButton
                v-for="type in group.types"
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
          </template>
        </div>
        <UButton data-testid="canvas-add-existing" size="xs" color="neutral" variant="outline" icon="i-lucide-search" :label="t('diagrams.canvas.addExisting')" @click="adding = true" />
        <UButton data-testid="canvas-add-connected" size="xs" color="neutral" variant="outline" icon="i-lucide-git-commit-horizontal" :disabled="!selectedShape || selectedShape.editing" :label="t('diagrams.canvas.addConnected')" @click="openConnected" />
        <UButton data-testid="canvas-remove" size="xs" color="neutral" variant="outline" icon="i-lucide-square-minus" :disabled="!selectedId && !selectedEdgeId" :label="t('diagrams.canvas.remove')" @click="removeSelected" />
        <UButton
          data-testid="canvas-change-viewpoint"
          size="xs"
          color="neutral"
          variant="outline"
          icon="i-lucide-replace"
          :disabled="dirty"
          :aria-describedby="dirty ? `${flowId}-viewpoint-hint` : undefined"
          :label="t('diagrams.viewpointChange.action')"
          @click="changeOpen = true"
        />
        <span v-if="dirty" :id="`${flowId}-viewpoint-hint`" class="text-xs text-muted" data-testid="canvas-change-viewpoint-hint">{{ t('diagrams.viewpointChange.saveFirst') }}</span>
      </template>
      <div class="ms-auto flex flex-wrap items-center gap-2">
        <UButton
          v-if="flowWarnings.size > 0"
          data-testid="canvas-bpmn-warnings"
          size="xs"
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          :label="t('diagrams.bpmn.warnings', flowWarnings.size)"
          @click="selectFirstWarning"
        />
        <UBadge v-if="dirty" data-testid="canvas-unsaved" color="warning" variant="subtle" :label="t('diagrams.canvas.unsavedChanges')" />
        <UBadge v-else-if="!editable" data-testid="canvas-read-only" color="neutral" variant="subtle" :label="t('diagrams.canvas.readOnly')" />
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-zoom-in" :aria-label="t('diagrams.canvas.zoomIn')" data-testid="canvas-zoom-in" @click="zoom(zoomIn)" />
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-zoom-out" :aria-label="t('diagrams.canvas.zoomOut')" data-testid="canvas-zoom-out" @click="zoom(zoomOut)" />
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-maximize" :aria-label="t('diagrams.canvas.fitView')" data-testid="canvas-fit-view" @click="zoom(fitView)" />
        <UButton v-if="editable" data-testid="canvas-save" size="sm" icon="i-lucide-save" :loading="saving" :disabled="!dirty" :label="t('diagrams.canvas.save')" @click="save" />
      </div>
    </div>

    <UAlert v-if="notice" data-testid="canvas-notice" color="warning" variant="subtle" :description="notice" />
    <UAlert v-if="offer.length > 0" data-testid="canvas-offer" color="info" variant="subtle">
      <template #description>
        <div class="flex flex-wrap items-center gap-2">
          <span data-testid="canvas-offer-text" role="status">{{ t('diagrams.connections.offer', offer.length) }}</span>
          <UButton data-testid="offer-show-all" size="xs" color="neutral" variant="outline" :label="t('diagrams.connections.showAll')" @click="showOffered()" />
          <UButton data-testid="offer-choose" size="xs" color="neutral" variant="outline" :label="t('diagrams.connections.choose')" @click="offerOpen = true" />
          <UButton data-testid="offer-dismiss" size="xs" color="neutral" variant="ghost" :label="t('diagrams.connections.dismiss')" @click="offer = []" />
        </div>
      </template>
    </UAlert>
    <UAlert v-if="blockedByConflicts && conflictItems.length > 0" data-testid="canvas-conflicts-blocked" color="warning" variant="subtle" :description="t('diagrams.viewpointChange.blocked')" />
    <ConflictsPanel :items="conflictItems" :editable="editable" :selected-id="selectedId ?? selectedEdgeId" @select="selectConflict" @remove="removeConflict" @remove-all="removeAllConflicts" />
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

    <p :id="`${flowId}-hint`" class="sr-only">
      {{ editable ? `${t('diagrams.canvas.hint')} ${t('diagrams.canvas.hintEdges')}` : t('diagrams.canvas.hintReadOnly') }}
      {{ canDelete ? t('diagrams.canvas.hintDelete') : '' }}
    </p>
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
            :nodes-connectable="editable"
            :connection-mode="ConnectionMode.Loose"
            :delete-key-code="null"
            disable-keyboard-a11y
            :zoom-on-double-click="false"
            :min-zoom="0.1"
            :max-zoom="4"
            :fit-view-on-init="view.shapes.length > 0"
            @node-drag-stop="dragStop"
            @node-click="select"
            @node-context-menu="contextMenu"
            @pane-click="selectShape(undefined)"
            @connect="connect"
            @dragover="dragOver"
            @drop="drop"
          >
            <template #node-entity="nodeProps">
              <EntityShape :id="nodeProps.id" :data="nodeProps.data" :selected="nodeProps.selected" />
            </template>
            <template #zoom-pane>
              <ConnectionEdges :edges="edgeDraws" :editable="editable" :to-flow="toFlow" @select="selectEdge" @context-menu="edgeMenu" @bend-added="bendAdded" @bend-moved="bendMoved" @bend-removed="bendRemoved" />
            </template>
          </VueFlow>
        </div>

      </div>
      <EntityDetailsPanel
        v-if="panelOpen && subject && !selectedShape?.editing"
        :diagram-id="view.diagram.id"
        :shape="subject"
        :editable="editable"
        :edit="subject.entityId !== undefined ? edits[subject.entityId] : undefined"
        :errors="fieldErrors"
        :reload-token="reloadToken"
        :fetch-fn="fetchFn"
        @change="changed"
        @close="panelOpen = false"
        @loaded="loaded"
      />
    </div>

    <div v-if="menu" role="menu" class="fixed z-50 min-w-48 rounded-md border border-accented bg-default p-1 shadow-lg" :style="{ left: `${menu.x}px`, top: `${menu.y}px` }" data-testid="canvas-menu">
      <button v-if="editable" type="button" role="menuitem" class="w-full rounded-sm px-2 py-1.5 text-start text-sm hover:bg-elevated" data-testid="canvas-menu-remove" @click="removeSelected">
        {{ t('diagrams.canvas.remove') }}
      </button>
      <button type="button" role="menuitem" class="w-full rounded-sm px-2 py-1.5 text-start text-sm hover:bg-elevated" data-testid="canvas-menu-details" @click="panelOpen = true">
        {{ t('diagrams.canvas.details') }}
      </button>
      <button v-if="editable && !menu.edge" type="button" role="menuitem" class="w-full rounded-sm px-2 py-1.5 text-start text-sm hover:bg-elevated" data-testid="canvas-menu-connected" @click="openConnected">
        {{ t('diagrams.canvas.addConnected') }}
      </button>
      <button v-if="editable && !menu.edge && selectedShape?.entityId !== undefined && (selectedShape.childCount ?? 0) > 0 && mayContain(selectedShape)" type="button" role="menuitem" class="w-full rounded-sm px-2 py-1.5 text-start text-sm hover:bg-elevated" data-testid="canvas-menu-children" @click="showChildren(selectedId)">
        {{ t('diagrams.nesting.showChildren') }}
      </button>
      <button v-if="canDelete && deletableEntityId !== undefined" type="button" role="menuitem" class="w-full rounded-sm px-2 py-1.5 text-start text-sm text-error hover:bg-elevated" data-testid="canvas-menu-delete-repository" @click="startRepositoryDelete">
        {{ t('diagrams.repositoryDelete.action') }}
      </button>
    </div>

    <DeleteFromRepositoryDialog v-if="canDelete" v-model:open="deleteOpen" :entity-id="deleting" :fetch-fn="fetchFn" @deleted="repositoryDeleted" />
    <AddExistingDialog v-if="editable" v-model:open="adding" :diagram-id="view.diagram.id" :fetch-fn="fetchFn" @pick="addExisting" />
    <ConnectionTypeDialog v-if="editable" v-model:open="typeOpen" :options="typeChoice?.options ?? []" @pick="(option) => typeChoice && drawConnection(typeChoice.source, typeChoice.target, option)" />
    <ConnectedElementDialog v-if="editable" v-model:open="connectedOpen" :options="connectedList" :loading="connectedLoading" :failed="connectedFailed" @add="addConnected" />
    <ConnectionOfferDialog v-if="editable" v-model:open="offerOpen" :offers="offered" @add="showOffered" />
    <ChildrenQuestionDialog
      v-if="editable"
      v-model:open="childrenOpen"
      :name="shapes.find((shape) => shape.id === childrenAsk)?.name ?? ''"
      :count="shapes.find((shape) => shape.id === childrenAsk)?.childCount ?? 0"
      @show="showChildren(childrenAsk)"
      @only="childrenAsk = undefined"
    />
    <NewParentDialog
      v-if="editable"
      v-model:open="parentOpen"
      :name="shapes.find((shape) => shape.id === parentAsk?.shapeId)?.name ?? ''"
      :parent="parentAsk?.parentName"
      @yes="parentConfirmed"
      @no="parentDeclined"
    />
    <ViewpointChangeDialog v-if="editable" v-model:open="changeOpen" :view="view" :fetch-fn="fetchFn" @changed="viewpointChanged" @stale="reload" />
    <RemoveContainerDialog v-if="editable" v-model:open="removeOpen" :name="removeAsk?.name ?? ''" :count="removeAsk?.count ?? 0" @confirm="removeConfirmed" />

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
