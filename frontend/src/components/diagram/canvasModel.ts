import type {
  DiagramEntity,
  DiagramEntityChild,
  DiagramEntityUpdate,
  DiagramHierarchyLine,
  DiagramParentChange,
  DiagramShape,
  DiagramView,
  DiagramViewWrite,
} from '../../api/diagramView'
import { CANVAS_LIMITS } from '../../api/diagramView'
import type { Viewpoint } from '../../api/metamodel'
import { BAND_WIDTH, isBpmnContainer, type BpmnSymbol } from './bpmnSymbols'
import { edgeFingerprint, type CanvasEdge } from './edgeModel'

// The state of the canvas editor without the canvas library (design D6 of us-074-diagram-canvas):
// shapes in pt, the conversion to the pixels of the canvas, default sizes, moving with the keyboard
// and the request of the save. The canvas draws and edits exactly this state.

/** 1 pt is 4/3 px (96 dpi on 72 pt per inch). */
export const PX_PER_PT = 4 / 3

/** The size of a shape of a metatype that the viewpoint stores no size for, in pt. */
export const DEFAULT_SIZE = { width: 90, height: 30 }

/** A shape on the canvas. Position and size are in pt with at most two decimals. */
export interface CanvasShape {
  id: string
  /** the existing entity; `undefined` for an entity that is created with the next save */
  entityId?: number
  /**
   * a new entity, created with the save; its shape shows it by `key`. The description and the values
   * (property definition ID to text) are entered in the details panel (US-074 step 6).
   */
  newEntity?: NewEntityState
  name: string
  metatypeId: string
  /** the container shape of the same diagram, drawn before this shape (design D1 of us-074-diagram-nesting); the position stays absolute */
  parentShapeId?: string
  /** the direct children of the entity that are not deleted, as the view read them: the question "Show children" */
  childCount?: number
  /** the parent of the entity as the view read it (the parent change of an unsaved move is in {@link ParentChanges}) */
  parentEntityId?: number | null
  x: number
  y: number
  width: number
  height: number
  /** the sub-process is shown collapsed in this diagram (design D3 of us-074-bpmn-pools-and-lanes); `width` and `height` stay the expanded size */
  collapsed?: boolean
  /** the lanes of the pool lie side by side (design D5 of us-074-bpmn-pools-and-lanes); absent for horizontal; only on the shape of a pool */
  orientation?: 'vertical'
  missingMandatory: string[]
  /** the reason a save rejected the shape; cleared by the next change of the shape */
  error?: string
  /** the viewpoint does not allow the type of the entity: a conflict that blocks the save until it is resolved (design D2 of us-074-diagram-viewpoint-change) */
  conflict?: boolean
  /** the name of a new shape is entered in the shape itself */
  editing?: boolean
}

/**
 * A new entity: created with the save; its shape shows it by `key`. The parent is the entity of the container
 * it was dropped into: an existing entity, or another new entity by its key.
 */
export interface NewEntityState {
  key: string
  metatypeId: string
  description?: string
  properties?: Record<string, string>
  parentEntityId?: number
  parentNewEntityKey?: string
}

/** A hierarchy line from a container shape to the shape of a child placed elsewhere (design D4 of us-074-diagram-nesting). */
export interface CanvasLine {
  id: string
  parentShapeId: string
  childShapeId: string
}

/** The new parent of an entity: an existing entity, a new entity by its key, or none. */
export type ParentTarget = { entityId: number } | { key: string } | null

/** An unsaved change of the parent of a saved entity, with the entity version read. */
export interface ParentChange {
  version: number
  parent: ParentTarget
}

/** The unsaved parent changes of saved entities, by entity ID. */
export type ParentChanges = Record<number, ParentChange>

/** What the canvas hands to the node of a shape. */
export interface ShapeData {
  name: string
  abbreviation?: string
  hint?: string
  editable: boolean
  /** the shape can be the end of a drawn connection */
  connectable: boolean
  editing: boolean
  unsaved: boolean
  missingMandatory: string[]
  error?: string
  /** the shape is a conflict: the viewpoint does not allow its type */
  conflict: boolean
  /** the name of the metatype, for the label of the input of a new shape */
  metatypeName: string
  /** the shape holds other shapes: it is a container */
  container: boolean
  /** the sub-process is drawn collapsed: 90 x 60 pt with the "+" marker */
  collapsed: boolean
  /** the toggle "Collapse" or "Expand" is offered: an editor, on the shape of a sub-process */
  collapsible: boolean
  toggle: () => void
  /** the edges a resize may drag: all, or in a pool or lane with lanes the edges across the lanes and the far edge along them, in a lane the far edge along the lanes only */
  resizeEdges: 'all' | ('top' | 'bottom' | 'left' | 'right')[]
  /** the label band of a pool or lane lies at the top (the lanes lie side by side) */
  vertical: boolean
  /** the icons "Insert lane" a selected lane offers: before and after it along the lanes; none for another shape */
  insertLane?: { vertical: boolean; insert: (side: 'before' | 'after') => void }
  /** the BPMN symbol of the notation element, in a diagram of the notation `bpmn2` (design D3 of us-074-bpmn-process-diagrams); none draws the box */
  symbol?: BpmnSymbol
  /** the description of the entity, shown by a text annotation */
  description?: string
  rename: (name: string) => void
  resized: (params: { x: number; y: number; width: number; height: number }) => void
}

/** pt to px of the canvas. */
export function ptToPx(pt: number): number {
  return pt * PX_PER_PT
}

/** px of the canvas to pt, rounded to two decimals. */
export function pxToPt(px: number): number {
  return round2(px / PX_PER_PT)
}

/** Rounds to two decimals. */
export function round2(value: number): number {
  return Math.round(value * 100) / 100
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

/** A position in pt within the limits of the contract, with two decimals. */
export function limitPosition(value: number): number {
  return clamp(round2(value), -CANVAS_LIMITS.position, CANVAS_LIMITS.position)
}

/** A size in pt within the limits of the contract, with two decimals. */
export function limitSize(value: number): number {
  return clamp(round2(value), CANVAS_LIMITS.sizeMin, CANVAS_LIMITS.sizeMax)
}

/** The default size in pt of a metatype in the viewpoint: its stored size, else 90 x 30. */
export function defaultSize(view: DiagramView, metatypeId: string): { width: number; height: number } {
  const mapping = view.viewpoint.effectiveMappings.find((entry) => entry.metatypeId === metatypeId)
  return {
    width: mapping?.width ?? DEFAULT_SIZE.width,
    height: mapping?.height ?? DEFAULT_SIZE.height,
  }
}

/** The color of the visual hint of a metatype in the viewpoint, or `undefined`. */
export function visualHint(view: DiagramView, metatypeId: string): string | undefined {
  return view.viewpoint.effectiveMappings.find((entry) => entry.metatypeId === metatypeId)?.visualHint ?? undefined
}

/** The abbreviation of an allowed metatype, or `undefined`. */
export function abbreviation(view: DiagramView, metatypeId: string): string | undefined {
  return view.viewpoint.allowedMetatypes.find((entry) => entry.id === metatypeId)?.abbreviation
}

/** The shape of a saved view. */
export function fromView(shape: DiagramShape): CanvasShape {
  return {
    id: shape.id,
    entityId: shape.entity.id,
    name: shape.entity.name,
    metatypeId: shape.entity.metatypeId,
    ...(shape.parentShapeId ? { parentShapeId: shape.parentShapeId } : {}),
    childCount: shape.entity.childCount,
    parentEntityId: shape.entity.parentEntityId,
    x: shape.x,
    y: shape.y,
    width: shape.width,
    height: shape.height,
    ...(shape.collapsed ? { collapsed: true } : {}),
    ...(shape.orientation === 'vertical' ? { orientation: 'vertical' as const } : {}),
    missingMandatory: [...shape.missingMandatory],
    ...(shape.conflict ? { conflict: true } : {}),
  }
}

/**
 * The names of the shapes and edges of the view that the viewpoint does not allow, so that they would be conflicts
 * (design D5 of us-074-diagram-viewpoint-change): the exact type is allowed or not, as the save checks it.
 */
export function previewConflicts(view: DiagramView, viewpoint: Viewpoint): { shapes: string[]; edges: string[] } {
  const types = new Set(viewpoint.allowedMetatypes.map((type) => type.id))
  const connections = new Set(viewpoint.allowedRelations.map((type) => type.id))
  return {
    shapes: view.shapes.filter((shape) => !types.has(shape.entity.metatypeId)).map((shape) => shape.entity.name),
    edges: view.edges.filter((edge) => !connections.has(edge.connection.metatypeId)).map((edge) => edge.connection.name),
  }
}

/** A new shape of an existing entity at the center `(x, y)` in pt, with the default size of its metatype. */
export function placeExisting(view: DiagramView, entity: DiagramEntity, center: { x: number; y: number }): CanvasShape {
  const size = defaultSize(view, entity.metatype.id)
  return {
    id: crypto.randomUUID(),
    entityId: entity.id,
    name: entity.name,
    metatypeId: entity.metatype.id,
    childCount: entity.childCount,
    parentEntityId: entity.parentEntityId,
    x: limitPosition(center.x - size.width / 2),
    y: limitPosition(center.y - size.height / 2),
    ...size,
    missingMandatory: [],
  }
}

/** A new shape of a new entity at the center, with the default size; its name is entered next. */
export function placeNew(view: DiagramView, metatypeId: string, center: { x: number; y: number }): CanvasShape {
  const size = defaultSize(view, metatypeId)
  return {
    id: crypto.randomUUID(),
    newEntity: { key: crypto.randomUUID(), metatypeId },
    name: '',
    metatypeId,
    x: limitPosition(center.x - size.width / 2),
    y: limitPosition(center.y - size.height / 2),
    ...size,
    missingMandatory: [],
    editing: true,
  }
}

/**
 * Why another shape cannot be added: a diagram holds at most 2000 shapes and a save creates at most
 * 500 new entities; `undefined` when there is room.
 */
export function placementBlock(shapes: CanvasShape[], forNew: boolean): 'shapes' | 'new' | undefined {
  if (shapes.length >= CANVAS_LIMITS.shapes) {
    return 'shapes'
  }
  if (forNew && shapes.filter((shape) => shape.newEntity).length >= CANVAS_LIMITS.newEntities) {
    return 'new'
  }
  return undefined
}

/** The shape moved by `(dx, dy)` pt, within the limits. */
export function moved(shape: CanvasShape, dx: number, dy: number): CanvasShape {
  return { ...shape, x: limitPosition(shape.x + dx), y: limitPosition(shape.y + dy), error: undefined }
}

/** The step of an arrow key in pt: 1, or 10 with Shift (accessibility). */
export function keyStep(shift: boolean): number {
  return shift ? 10 : 1
}

/**
 * The changes of one saved entity made in the details panel (design D3 of
 * us-074-diagram-entity-details): only the changed fields, with the entity version the panel read.
 * `properties` maps a property definition ID to the new text; an empty text removes the value.
 */
export interface EntityEdit {
  version: number
  name?: string
  description?: string
  properties: Record<string, string>
}

/** The edits of the saved entities, by entity ID. */
export type EntityEdits = Record<number, EntityEdit>

/** The change of one field: `value` is `undefined` when the field is back at the value read. */
export type FieldChange =
  | { field: 'name' | 'description'; value: string | undefined }
  | { field: 'property'; propertyDefId: string; value: string | undefined }

/** Whether an edit changes anything. */
export function isEdited(edit: EntityEdit | undefined): boolean {
  return !!edit && (edit.name !== undefined || edit.description !== undefined || Object.keys(edit.properties).length > 0)
}

/** The number of entities with changes. */
export function editedCount(edits: EntityEdits): number {
  return Object.values(edits).filter(isEdited).length
}

/**
 * Whether the change of another entity is refused: a save changes at most 500 entities
 * (`CANVAS_LIMITS.entityUpdates`); a further change of an entity that already has changes is fine.
 */
export function editBlocked(edits: EntityEdits, entityId: number): boolean {
  return !isEdited(edits[entityId]) && editedCount(edits) >= CANVAS_LIMITS.entityUpdates
}

/** The edits with one field changed; an entity without changes has no entry. */
export function applyEdit(edits: EntityEdits, entityId: number, version: number, change: FieldChange): EntityEdits {
  const before = edits[entityId]
  const next: EntityEdit = { version: before?.version ?? version, name: before?.name, description: before?.description, properties: { ...before?.properties } }
  if (change.field === 'property') {
    if (change.value === undefined) delete next.properties[change.propertyDefId]
    else next.properties[change.propertyDefId] = change.value
  } else {
    next[change.field] = change.value
  }
  const rest = { ...edits }
  if (isEdited(next)) rest[entityId] = next
  else delete rest[entityId]
  return rest
}

/** The shape of a new entity with one field changed. */
export function applyNewEntityEdit(shape: CanvasShape, change: FieldChange): CanvasShape {
  if (!shape.newEntity) {
    return shape
  }
  const newEntity = { ...shape.newEntity, properties: { ...shape.newEntity.properties } }
  if (change.field === 'property') {
    if (change.value === undefined) delete newEntity.properties[change.propertyDefId]
    else newEntity.properties[change.propertyDefId] = change.value
  } else if (change.field === 'description') {
    newEntity.description = change.value
  }
  return { ...shape, newEntity, error: undefined }
}

/**
 * The request of the save: the shapes in drawing order, the new entities (names trimmed, with the
 * description and values entered) and the changes of the shown saved entities with their versions.
 */
export function writeRequest(
  shapes: CanvasShape[],
  edits: EntityEdits = {},
  edges: CanvasEdge[] = [],
  lines: CanvasLine[] = [],
  parents: ParentChanges = {},
): DiagramViewWrite {
  const named = shapes.filter((shape) => shape.entityId !== undefined || (shape.newEntity && shape.name.trim().length > 0))
  const placed = new Set(named.map((shape) => shape.id))
  const drawnLines = lines.filter((line) => placed.has(line.parentShapeId) && placed.has(line.childShapeId))
  const newKeys = new Set(named.map((shape) => shape.newEntity?.key).filter((key): key is string => key !== undefined))
  const changes: DiagramParentChange[] = named
    .filter((shape) => shape.entityId !== undefined && parents[shape.entityId] !== undefined)
    .filter((shape, index, all) => all.findIndex((other) => other.entityId === shape.entityId) === index)
    .flatMap((shape) => {
      const change = parents[shape.entityId!]!
      const target = change.parent
      if (target !== null && 'key' in target && !newKeys.has(target.key)) return []
      return [
        {
          entityId: shape.entityId!,
          expectedVersion: change.version,
          ...(target === null ? {} : 'key' in target ? { parentNewEntityKey: target.key } : { parentEntityId: target.entityId }),
        },
      ]
    })
  const drawn = edges.filter((edge) => placed.has(edge.sourceShapeId) && placed.has(edge.targetShapeId))
  const shown = new Set(named.map((shape) => shape.entityId).filter((id): id is number => id !== undefined))
  drawn.forEach((edge) => edge.connectionId !== undefined && shown.add(edge.connectionId))
  const created = new Map(drawn.filter((edge) => edge.newConnection).map((edge) => [edge.newConnection!.key, edge.newConnection!]))
  const updates: DiagramEntityUpdate[] = Object.entries(edits)
    .filter(([id, edit]) => isEdited(edit) && shown.has(Number(id)))
    .map(([id, edit]) => ({
      entityId: Number(id),
      expectedVersion: edit.version,
      ...(edit.name !== undefined ? { name: edit.name.trim() } : {}),
      ...(edit.description !== undefined ? { description: edit.description } : {}),
      ...(Object.keys(edit.properties).length > 0
        ? { properties: Object.entries(edit.properties).map(([propertyDefId, value]) => ({ propertyDefId, value })) }
        : {}),
    }))
  return {
    shapes: named.map((shape) => ({
      id: shape.id,
      ...(shape.newEntity ? { newEntityKey: shape.newEntity.key } : { entityId: shape.entityId }),
      ...(shape.parentShapeId && placed.has(shape.parentShapeId) ? { parentShapeId: shape.parentShapeId } : {}),
      x: limitPosition(shape.x),
      y: limitPosition(shape.y),
      width: limitSize(shape.width),
      height: limitSize(shape.height),
      collapsed: shape.collapsed === true,
      orientation: shape.orientation === 'vertical' ? ('vertical' as const) : ('horizontal' as const),
    })),
    newEntities: named
      .filter((shape) => shape.newEntity)
      .map((shape) => {
        const entity = shape.newEntity!
        const values = Object.entries(entity.properties ?? {}).filter(([, value]) => value !== '')
        return {
          key: entity.key,
          metatypeId: entity.metatypeId,
          name: shape.name.trim(),
          ...((entity.description ?? '') !== '' ? { description: entity.description } : {}),
          ...(values.length > 0 ? { properties: values.map(([propertyDefId, value]) => ({ propertyDefId, value })) } : {}),
          ...(entity.parentEntityId !== undefined ? { parentEntityId: entity.parentEntityId } : {}),
          ...(entity.parentNewEntityKey !== undefined && newKeys.has(entity.parentNewEntityKey) ? { parentNewEntityKey: entity.parentNewEntityKey } : {}),
        }
      }),
    ...(updates.length > 0 ? { entityUpdates: updates } : {}),
    ...(drawnLines.length > 0 ? { hierarchyLines: drawnLines.map((line) => ({ id: line.id, parentShapeId: line.parentShapeId, childShapeId: line.childShapeId })) } : {}),
    ...(changes.length > 0 ? { parentChanges: changes } : {}),
    edges: drawn.map((edge) => ({
      id: edge.id,
      ...(edge.newConnection ? { newConnectionKey: edge.newConnection.key } : { connectionId: edge.connectionId }),
      sourceShapeId: edge.sourceShapeId,
      targetShapeId: edge.targetShapeId,
      points: edge.points.map((point) => ({ x: limitPosition(point.x), y: limitPosition(point.y) })),
    })),
    newConnections: [...created.values()].map((connection) => {
      const values = Object.entries(connection.properties ?? {}).filter(([, value]) => value !== '')
      return {
        key: connection.key,
        metatypeId: connection.metatypeId,
        ...((connection.description ?? '') !== '' ? { description: connection.description } : {}),
        ...(values.length > 0 ? { properties: values.map(([propertyDefId, value]) => ({ propertyDefId, value })) } : {}),
      }
    }),
  }
}

/** The part of a shape that a save stores; two shapes with the same fingerprint are the same. */
function fingerprint(shape: CanvasShape): string {
  return JSON.stringify([shape.id, shape.entityId ?? shape.newEntity?.key, shape.x, shape.y, shape.width, shape.height, shape.parentShapeId ?? null, shape.collapsed === true, shape.orientation === 'vertical'])
}

/**
 * Whether the shapes differ from the saved ones in shape, entity, position, size or order, or an
 * entity has changes of its fields.
 */
export function hasUnsavedChanges(
  shapes: CanvasShape[],
  saved: CanvasShape[],
  edits: EntityEdits = {},
  edges: CanvasEdge[] = [],
  savedEdges: CanvasEdge[] = [],
  lines: CanvasLine[] = [],
  savedLines: CanvasLine[] = [],
  parents: ParentChanges = {},
): boolean {
  const current = shapes.filter((shape) => !shape.editing || shape.name.trim().length > 0)
  const placed = new Set(current.map((shape) => shape.id))
  const drawn = edges.filter((edge) => placed.has(edge.sourceShapeId) && placed.has(edge.targetShapeId))
  const drawnLines = lines.filter((line) => placed.has(line.parentShapeId) && placed.has(line.childShapeId))
  return (
    current.length !== saved.length ||
    current.some((shape, index) => fingerprint(shape) !== fingerprint(saved[index])) ||
    drawn.length !== savedEdges.length ||
    drawn.some((edge) => isEdgeUnsaved(edge, savedEdges, edits)) ||
    drawnLines.length !== savedLines.length ||
    drawnLines.some((line) => !savedLines.some((entry) => entry.id === line.id && entry.parentShapeId === line.parentShapeId && entry.childShapeId === line.childShapeId)) ||
    Object.keys(parents).length > 0 ||
    editedCount(edits) > 0
  )
}

/** Whether an edge is new or differs from its saved version, or its connection has changes. */
export function isEdgeUnsaved(edge: CanvasEdge, saved: CanvasEdge[], edits: EntityEdits = {}): boolean {
  const before = saved.find((entry) => entry.id === edge.id)
  return !before || edgeFingerprint(before) !== edgeFingerprint(edge) || (edge.connectionId !== undefined && isEdited(edits[edge.connectionId]))
}

/** Whether a shape is new or differs from its saved version, or its entity has changes. */
export function isUnsaved(shape: CanvasShape, saved: CanvasShape[], edits: EntityEdits = {}, parents: ParentChanges = {}): boolean {
  const before = saved.find((entry) => entry.id === shape.id)
  return (
    !before ||
    fingerprint(before) !== fingerprint(shape) ||
    (shape.entityId !== undefined && (isEdited(edits[shape.entityId]) || parents[shape.entityId] !== undefined))
  )
}

/**
 * What a 422 names, from the paths of its errors: the shapes by ID (`shapes[i]`, `newEntities[j]`
 * and `entityUpdates[i]` name the shapes of that entity), and the messages of the fields, keyed
 * `<entity ID or new entity key>|<field>`, where the field is `name`, `description`, a property
 * definition ID or empty for a message of the entity as a whole (e.g. a mandatory value missing).
 */
export interface Rejected {
  shapes: Map<string, string>
  fields: Map<string, string>
  /** the edges the errors name, by edge ID: `edges[i]`, `newConnections[j]` and `entityUpdates[i]` of a connection */
  edges: Map<string, string>
  /** the hierarchy lines the errors name, by line ID */
  lines: Map<string, string>
}

export function rejectedShapes(
  errors: { field: string; message: string }[],
  request: DiagramViewWrite,
  shapes: CanvasShape[],
  edges: CanvasEdge[] = [],
): Rejected {
  const rejected = new Map<string, string>()
  const rejectedEdges = new Map<string, string>()
  const fields = new Map<string, string>()
  const lineErrors = new Map<string, string>()
  const markEdge = (edge: CanvasEdge, message: string) => {
    if (!rejectedEdges.has(edge.id)) rejectedEdges.set(edge.id, message)
  }
  const byId = new Map(shapes.map((shape) => [shape.id, shape]))
  const mark = (shape: CanvasShape, message: string) => {
    if (!rejected.has(shape.id)) rejected.set(shape.id, message)
  }
  for (const error of errors) {
    const shape = /^shapes\[(\d+)]/.exec(error.field)
    const entity = /^newEntities\[(\d+)](?:\.(.*))?$/.exec(error.field)
    const update = /^entityUpdates\[(\d+)](?:\.(.*))?$/.exec(error.field)
    const edge = /^edges\[(\d+)]/.exec(error.field)
    const connection = /^newConnections\[(\d+)](?:\.(.*))?$/.exec(error.field)
    const line = /^hierarchyLines\[(\d+)]/.exec(error.field)
    const change = /^parentChanges\[(\d+)]/.exec(error.field)
    if (line) {
      const id = request.hierarchyLines?.[Number(line[1])]
      for (const candidate of shapes) {
        if (id && candidate.id === id.childShapeId) mark(candidate, error.message)
      }
      lineErrors.set(id?.id ?? '', error.message)
    } else if (change) {
      const sent = request.parentChanges?.[Number(change[1])]
      for (const candidate of shapes) {
        if (sent && candidate.entityId === sent.entityId) mark(candidate, error.message)
      }
    } else if (edge) {
      const id = request.edges?.[Number(edge[1])]?.id
      const found = edges.find((candidate) => candidate.id === id)
      if (found) markEdge(found, error.message)
    } else if (connection) {
      const sent = request.newConnections?.[Number(connection[1])]
      for (const candidate of edges) {
        if (sent && candidate.newConnection?.key === sent.key) markEdge(candidate, error.message)
      }
      if (sent) fields.set(`${sent.key}|${fieldOf(connection[2], sent.properties)}`, error.message)
    } else if (shape) {
      const id = request.shapes[Number(shape[1])]?.id
      if (id && byId.has(id)) mark(byId.get(id)!, error.message)
    } else if (entity) {
      const sent = request.newEntities?.[Number(entity[1])]
      for (const candidate of shapes) {
        if (sent && candidate.newEntity?.key === sent.key) mark(candidate, error.message)
      }
      if (sent) fields.set(`${sent.key}|${fieldOf(entity[2], sent.properties)}`, error.message)
    } else if (update) {
      const sent = request.entityUpdates?.[Number(update[1])]
      for (const candidate of shapes) {
        if (sent && candidate.entityId === sent.entityId) mark(candidate, error.message)
      }
      for (const candidate of edges) {
        if (sent && candidate.connectionId === sent.entityId) markEdge(candidate, error.message)
      }
      if (sent) fields.set(`${sent.entityId}|${fieldOf(update[2], sent.properties)}`, error.message)
    }
  }
  return { shapes: rejected, fields, edges: rejectedEdges, lines: lineErrors }
}

/** The field a path below an entity names: `name`, `description`, the property of `properties[k]...`, or empty. */
function fieldOf(path: string | undefined, properties: { propertyDefId: string }[] | undefined): string {
  if (path === 'name' || path === 'description') {
    return path
  }
  const property = /^properties\[(\d+)]/.exec(path ?? '')
  return property ? (properties?.[Number(property[1])]?.propertyDefId ?? '') : ''
}

// ── Nested shapes (design D1, D4 of us-074-diagram-nesting) ─────────────────────────────────────────────

/** The space around the children of a container and above them (for the name of the container), in pt. */
export const CONTAINER_PADDING = 10
export const CONTAINER_HEADER = 24
/** The gap between two children laid out in a grid, in pt. */
export const CHILD_GAP = 10

/** The direct children of a container, in drawing order. */
export function nestedIn(shapes: CanvasShape[], id: string): CanvasShape[] {
  return shapes.filter((shape) => shape.parentShapeId === id)
}

/** All shapes nested in a container, at any depth, in drawing order. */
export function descendantsOf(shapes: CanvasShape[], id: string): CanvasShape[] {
  const found = new Set<string>([id])
  const result: CanvasShape[] = []
  // a nested shape always follows its container, so one pass in drawing order finds every level
  for (const shape of shapes) {
    if (shape.parentShapeId && found.has(shape.parentShapeId)) {
      found.add(shape.id)
      result.push(shape)
    }
  }
  return result
}

/** The level of a shape: 1 on the canvas, one more than its container. */
export function levelOf(shapes: CanvasShape[], id: string): number {
  let level = 1
  let current = shapes.find((shape) => shape.id === id)
  while (current?.parentShapeId) {
    level += 1
    const parent: string = current.parentShapeId
    current = shapes.find((shape) => shape.id === parent)
  }
  return level
}

/** The number of levels of the shape and what is nested in it: 1 for a shape without nested shapes. */
export function subtreeLevels(shapes: CanvasShape[], id: string): number {
  const base = levelOf(shapes, id)
  return Math.max(base, ...descendantsOf(shapes, id).map((shape) => levelOf(shapes, shape.id))) - base + 1
}

/** Whether `inner` lies completely within `outer`, the edges included. */
export function lies(outer: CanvasShape, inner: CanvasShape): boolean {
  return (
    inner.x >= outer.x &&
    inner.y >= outer.y &&
    round2(inner.x + inner.width) <= round2(outer.x + outer.width) &&
    round2(inner.y + inner.height) <= round2(outer.y + outer.height)
  )
}

/**
 * The shape whose box holds the point and that is drawn on top: the candidate for the container of a shape
 * that is dropped there. Shapes of `exclude` (the shape itself and what is nested in it) and shapes that are
 * still named are skipped; with `canContain` (design D5 of us-074-bpmn-process-diagrams) only shapes that
 * may take nested shapes are candidates, so a drop onto another shape falls through to the container below it.
 */
export function containerAt(
  shapes: CanvasShape[],
  point: { x: number; y: number },
  exclude: Set<string>,
  canContain: (shape: CanvasShape) => boolean = () => true,
): CanvasShape | undefined {
  return [...shapes]
    .reverse()
    .find(
      (shape) =>
        !exclude.has(shape.id) &&
        !shape.editing &&
        canContain(shape) &&
        point.x >= shape.x &&
        point.x <= shape.x + shape.width &&
        point.y >= shape.y &&
        point.y <= shape.y + shape.height,
    )
}

/**
 * The shapes with every container grown to hold what is nested in it: its box becomes the union of its own
 * and its children's (rounded to two decimals). A container never shrinks by itself (design D4).
 */
export function growContainers(shapes: CanvasShape[]): CanvasShape[] {
  const result = shapes.map((shape) => ({ ...shape }))
  const byId = new Map(result.map((shape) => [shape.id, shape]))
  // a nested shape follows its container: from the last to the first, so that growth reaches every level
  for (let index = result.length - 1; index >= 0; index -= 1) {
    const shape = result[index]!
    const parent = shape.parentShapeId ? byId.get(shape.parentShapeId) : undefined
    if (!parent) continue
    const left = Math.min(parent.x, shape.x)
    const top = Math.min(parent.y, shape.y)
    const right = Math.max(round2(parent.x + parent.width), round2(shape.x + shape.width))
    const bottom = Math.max(round2(parent.y + parent.height), round2(shape.y + shape.height))
    parent.x = left
    parent.y = top
    parent.width = limitSize(round2(right - left))
    parent.height = limitSize(round2(bottom - top))
  }
  return result
}

/** The shape and everything nested in it moved by `(dx, dy)` pt, within the limits. */
export function moveTree(shapes: CanvasShape[], id: string, dx: number, dy: number): CanvasShape[] {
  const ids = new Set([id, ...descendantsOf(shapes, id).map((shape) => shape.id)])
  return shapes.map((shape) => (ids.has(shape.id) ? moved(shape, dx, dy) : shape))
}

/**
 * Why a shape cannot be nested in a container: the levels of the shape with what is nested in it would be
 * more than 10 (`depth`), or the container lies within the shape (`cycle`); `undefined` when it can.
 */
export function nestBlock(shapes: CanvasShape[], id: string, containerId: string): 'depth' | 'cycle' | undefined {
  if (containerId === id || descendantsOf(shapes, id).some((shape) => shape.id === containerId)) {
    return 'cycle'
  }
  return levelOf(shapes, containerId) + subtreeLevels(shapes, id) > CANVAS_LIMITS.nesting ? 'depth' : undefined
}

/**
 * The shape nested in a container, or put on the canvas without one: the shape and what is nested in it
 * follow the container directly in the drawing order, and the container grows to hold the shape.
 */
export function nestShape(shapes: CanvasShape[], id: string, containerId: string | undefined): CanvasShape[] {
  if (containerId === undefined) {
    return growContainers(shapes.map((shape) => (shape.id === id ? { ...shape, parentShapeId: undefined, error: undefined } : shape)))
  }
  const ids = new Set([id, ...descendantsOf(shapes, id).map((shape) => shape.id)])
  const block = shapes.filter((shape) => ids.has(shape.id)).map((shape) => (shape.id === id ? { ...shape, parentShapeId: containerId, error: undefined } : shape))
  const rest = shapes.filter((shape) => !ids.has(shape.id))
  const position = rest.findIndex((shape) => shape.id === containerId)
  return growContainers([...rest.slice(0, position + 1), ...block, ...rest.slice(position + 1)])
}

/** A child to place in a container, as `listDiagramEntityChildren` names it. */
export type ChildEntity = DiagramEntityChild['entity']

/**
 * The shapes of the children laid out in a grid inside the container, with as many columns as make the
 * container about square, below what is nested in it already; each at the default size of its type
 * (design D4). The result also holds the container grown to hold them.
 */
export function layoutChildren(
  view: DiagramView,
  shapes: CanvasShape[],
  container: CanvasShape,
  children: ChildEntity[],
): { shapes: CanvasShape[]; added: CanvasShape[] } {
  if (children.length === 0) {
    return { shapes, added: [] }
  }
  const sizes = children.map((child) => defaultSize(view, child.metatypeId))
  const cellWidth = Math.max(...sizes.map((size) => size.width))
  const cellHeight = Math.max(...sizes.map((size) => size.height))
  const columns = Math.ceil(Math.sqrt(children.length))
  const below = nestedIn(shapes, container.id).reduce((bottom, shape) => Math.max(bottom, round2(shape.y + shape.height)), container.y + CONTAINER_HEADER - CHILD_GAP)
  const top = below + CHILD_GAP
  const left = container.x + CONTAINER_PADDING
  const added = children.map((child, index): CanvasShape => {
    const size = sizes[index]!
    return {
      id: crypto.randomUUID(),
      entityId: child.id,
      name: child.name,
      metatypeId: child.metatypeId,
      parentShapeId: container.id,
      childCount: child.childCount,
      parentEntityId: child.parentEntityId,
      x: limitPosition(left + (index % columns) * (cellWidth + CHILD_GAP)),
      y: limitPosition(top + Math.floor(index / columns) * (cellHeight + CHILD_GAP)),
      ...size,
      missingMandatory: [],
    }
  })
  const last = shapes.filter((shape) => shape.id === container.id || descendantsOf(shapes, container.id).some((entry) => entry.id === shape.id))
  const position = shapes.findIndex((shape) => shape.id === last.at(-1)?.id)
  const next = [...shapes.slice(0, position + 1), ...added, ...shapes.slice(position + 1)]
  return { shapes: growContainers(next), added }
}

/** The parent of an entity as it is now: the unsaved change, or the one the view read. */
export function effectiveParent(shape: CanvasShape, parents: ParentChanges): ParentTarget {
  if (shape.newEntity) {
    return shape.newEntity.parentNewEntityKey !== undefined
      ? { key: shape.newEntity.parentNewEntityKey }
      : shape.newEntity.parentEntityId !== undefined
        ? { entityId: shape.newEntity.parentEntityId }
        : null
  }
  const change = shape.entityId !== undefined ? parents[shape.entityId] : undefined
  if (change) {
    return change.parent
  }
  return shape.parentEntityId ? { entityId: shape.parentEntityId } : null
}

/** Whether two parents are the same. */
export function sameParent(a: ParentTarget, b: ParentTarget): boolean {
  if (a === null || b === null) return a === b
  if ('key' in a || 'key' in b) return 'key' in a && 'key' in b && a.key === b.key
  return a.entityId === b.entityId
}

/** The entity of a container shape as the parent of what is nested in it. */
export function parentOfShape(container: CanvasShape | undefined): ParentTarget {
  if (!container) return null
  if (container.newEntity) return { key: container.newEntity.key }
  return container.entityId !== undefined ? { entityId: container.entityId } : null
}

/** The lines without those of a removed shape. */
export function withoutShapeLines(lines: CanvasLine[], shapeIds: Set<string>): CanvasLine[] {
  return lines.filter((line) => !shapeIds.has(line.parentShapeId) && !shapeIds.has(line.childShapeId))
}

/** The line of a saved view. */
export function fromViewLine(line: DiagramHierarchyLine): CanvasLine {
  return { id: line.id, parentShapeId: line.parentShapeId, childShapeId: line.childShapeId }
}

// ── BPMN (design D3 to D6 of us-074-bpmn-process-diagrams) ──────────────────────────────────────────────────

/** Whether the viewpoint of the view has the notation `bpmn2`. */
export function isBpmnView(view: DiagramView): boolean {
  return view.viewpoint.notation === 'bpmn2'
}

/** The notation element of a metatype in the viewpoint of the view (`generic:Box` and `generic:Arrow` for an unmapped one). */
export function notationElementOf(view: DiagramView, metatypeId: string): string | undefined {
  return view.viewpoint.effectiveMappings.find((entry) => entry.metatypeId === metatypeId)?.notationElement
}

/**
 * Whether a shape takes nested shapes, "Show children" and the children question: every shape, except in a
 * `bpmn2` diagram, where only shapes drawn as pool, lane or sub-process do (design D5).
 */
export function canContain(view: DiagramView): (shape: CanvasShape) => boolean {
  if (!isBpmnView(view)) {
    return () => true
  }
  const keys = new Map(view.viewpoint.effectiveMappings.map((entry) => [entry.metatypeId, entry.notationElement]))
  return (shape) => isBpmnContainer(keys.get(shape.metatypeId))
}

/** The warnings of the pool rules for a flow (design D6): they are computed from the layout and never stored. */
export type FlowWarning = 'sequence-crosses-pool' | 'message-within-pool'

/**
 * The warnings of the sequence and message flows for the current layout, by edge ID. The pool of a shape is
 * its nearest ancestor shape drawn as a pool, or the shape itself when it is a pool. A sequence flow whose ends
 * have different pools (one end without a pool counts, both without do not) and a message flow whose ends have
 * the same pool are warned. Flows of conflicts and flows with an end that is not on the canvas are not checked.
 */
export function bpmnFlowWarnings(
  shapes: CanvasShape[],
  edges: CanvasEdge[],
  mappings: { metatypeId: string; notationElement: string }[],
): Map<string, FlowWarning> {
  const keys = new Map(mappings.map((entry) => [entry.metatypeId, entry.notationElement]))
  const byId = new Map(shapes.map((shape) => [shape.id, shape]))
  const poolOf = (id: string): string | undefined => {
    const seen = new Set<string>()
    for (let shape = byId.get(id); shape && !seen.has(shape.id); shape = shape.parentShapeId ? byId.get(shape.parentShapeId) : undefined) {
      seen.add(shape.id)
      if (keys.get(shape.metatypeId) === 'bpmn:Pool') return shape.id
    }
    return undefined
  }
  const warnings = new Map<string, FlowWarning>()
  for (const edge of edges) {
    const source = byId.get(edge.sourceShapeId)
    const target = byId.get(edge.targetShapeId)
    if (!source || !target || edge.conflict || source.conflict || target.conflict) continue
    const key = keys.get(edge.metatypeId)
    const [from, to] = [poolOf(source.id), poolOf(target.id)]
    if (key === 'bpmn:SequenceFlow' && from !== to) warnings.set(edge.id, 'sequence-crosses-pool')
    else if (key === 'bpmn:MessageFlow' && from !== undefined && from === to) warnings.set(edge.id, 'message-within-pool')
  }
  return warnings
}

// ── Stacked lanes and collapsed sub-processes (design D1, D3, D5 and D6 of us-074-bpmn-pools-and-lanes) ──────────

/** A lane is at least this high (wide in a vertical pool), in pt. */
export const LANE_MIN_HEIGHT = 30

/** The size of a collapsed sub-process in pt; its stored size stays the expanded one. */
export const COLLAPSED_SIZE = { width: 90, height: 60 }

/** The notation element a metatype is drawn as. */
export interface NotationMapping {
  metatypeId: string
  notationElement: string
}

/** The lane rules of one diagram: which shapes are pools and lanes, and the lanes of each of them in their order. */
interface LaneIndex {
  isLane: (shape: CanvasShape) => boolean
  isHost: (shape: CanvasShape) => boolean
  /** a lane in a pool or lane: stacked by it */
  isStacked: (shape: CanvasShape) => boolean
  /** the lanes of the pool or lane lie side by side: its outermost pool is vertical */
  isVertical: (shape: CanvasShape) => boolean
  lanesOf: (shape: CanvasShape) => CanvasShape[]
  childrenOf: (shape: CanvasShape) => CanvasShape[]
  /** everything nested in the shape, at any depth, in drawing order */
  tree: (shape: CanvasShape) => CanvasShape[]
  /** what is nested in the shape without being one of its lanes, with what is nested in that */
  directContent: (shape: CanvasShape) => CanvasShape[]
  byId: Map<string, CanvasShape>
}

/** `ignore` holds the shapes that are no pools or lanes for this index: the lanes of a vertical pool while the others are stacked. */
function laneIndex(shapes: CanvasShape[], mappings: NotationMapping[], ignore: Set<string> = new Set()): LaneIndex {
  const keys = new Map(mappings.map((mapping) => [mapping.metatypeId, mapping.notationElement]))
  const isPool = (shape: CanvasShape) => keys.get(shape.metatypeId) === 'bpmn:Pool'
  const isLane = (shape: CanvasShape) => keys.get(shape.metatypeId) === 'bpmn:Lane' && !ignore.has(shape.id)
  const isHost = (shape: CanvasShape) => !ignore.has(shape.id) && (isPool(shape) || isLane(shape))
  const byId = new Map(shapes.map((shape) => [shape.id, shape]))
  const order = new Map(shapes.map((shape, position) => [shape.id, position]))
  const children = new Map<string, CanvasShape[]>()
  for (const shape of shapes) {
    if (shape.parentShapeId && byId.has(shape.parentShapeId)) {
      children.set(shape.parentShapeId, [...(children.get(shape.parentShapeId) ?? []), shape])
    }
  }
  const childrenOf = (shape: CanvasShape) => children.get(shape.id) ?? []
  const isStacked = (shape: CanvasShape) => {
    const parent = shape.parentShapeId ? byId.get(shape.parentShapeId) : undefined
    return isLane(shape) && parent !== undefined && isHost(parent)
  }
  const outermost = (shape: CanvasShape) => {
    let current = shape
    while (isStacked(current)) current = byId.get(current.parentShapeId!)!
    return current
  }
  const isVertical = (shape: CanvasShape) => {
    const root = outermost(shape)
    return isPool(root) && root.orientation === 'vertical'
  }
  const lanes = new Map<string, CanvasShape[]>()
  for (const shape of shapes) {
    if (isHost(shape)) {
      const own = childrenOf(shape).filter(isLane)
      if (own.length > 0) {
        const along = (lane: CanvasShape) => (isVertical(shape) ? lane.x : lane.y)
        lanes.set(shape.id, own.sort((a, b) => along(a) - along(b) || order.get(a.id)! - order.get(b.id)!))
      }
    }
  }
  const tree = (shape: CanvasShape): CanvasShape[] => childrenOf(shape).flatMap((child) => [child, ...tree(child)])
  return {
    isLane,
    isHost,
    isStacked,
    isVertical,
    lanesOf: (shape) => lanes.get(shape.id) ?? [],
    childrenOf,
    tree,
    directContent: (shape) => childrenOf(shape).filter((child) => !isLane(child)).flatMap((child) => [child, ...tree(child)]),
    byId,
  }
}

function sameGeometry(a: CanvasShape[], b: CanvasShape[]): boolean {
  return a.length === b.length && a.every((shape, position) => shape.x === b[position]!.x && shape.y === b[position]!.y && shape.width === b[position]!.width && shape.height === b[position]!.height)
}

/** The shape mirrored on the diagonal through the origin: x and y, width and height exchanged. It is its own inverse. */
function reflect(shape: CanvasShape, origin: { x: number; y: number }): CanvasShape {
  return { ...shape, x: round2(origin.x + (shape.y - origin.y)), y: round2(origin.y + (shape.x - origin.x)), width: shape.height, height: shape.width }
}

/**
 * Applies a change to the pool and what is nested in it as if the pool were horizontal (design D5: the vertical layout is
 * the horizontal one transposed): the shapes are mirrored on the diagonal through the pool's top left corner, changed, and
 * mirrored back with the same corner. The pool is horizontal inside the change.
 */
function inFrame(shapes: CanvasShape[], poolId: string, change: (list: CanvasShape[]) => CanvasShape[]): CanvasShape[] {
  const pool = shapes.find((shape) => shape.id === poolId)
  if (!pool) {
    return shapes
  }
  const ids = new Set([poolId, ...descendantsOf(shapes, poolId).map((shape) => shape.id)])
  const origin = { x: pool.x, y: pool.y }
  const inside = shapes.filter((shape) => ids.has(shape.id)).map((shape) => (shape.id === poolId ? { ...reflect(shape, origin), orientation: undefined } : reflect(shape, origin)))
  const back = new Map(change(inside).map((shape) => [shape.id, shape.id === poolId ? { ...reflect(shape, origin), orientation: 'vertical' as const } : reflect(shape, origin)]))
  return shapes.map((shape) => back.get(shape.id) ?? shape)
}

/** The pool or lane that stacks the shape's lanes from the outside: the shape itself, or the outermost pool or lane around a lane. */
function laneRoot(index: LaneIndex, id: string): CanvasShape | undefined {
  let current = index.byId.get(id)
  while (current && index.isStacked(current)) current = index.byId.get(current.parentShapeId!)
  return current && index.lanesOf(current).length > 0 ? current : undefined
}

/** Runs the change in the frame of the vertical pool that holds the lane, else on the shapes as they are. */
function framed(shapes: CanvasShape[], mappings: NotationMapping[], id: string, change: (list: CanvasShape[]) => CanvasShape[]): CanvasShape[] {
  const index = laneIndex(shapes, mappings)
  const root = laneRoot(index, id)
  return root && index.isVertical(root) ? inFrame(shapes, root.id, change) : change(shapes)
}

/** How the shape takes part in the lanes: a lane of a pool or lane, or the outermost pool or lane with lanes; and the direction of the lanes. */
export type LaneRole = { role: 'lane' | 'host'; vertical: boolean }

/** The lane roles of all shapes that have one. */
export function laneRoles(shapes: CanvasShape[], mappings: NotationMapping[]): Map<string, LaneRole> {
  const index = laneIndex(shapes, mappings)
  const roles = new Map<string, LaneRole>()
  for (const shape of shapes) {
    if (index.isStacked(shape)) roles.set(shape.id, { role: 'lane', vertical: index.isVertical(shape) })
    else if (index.lanesOf(shape).length > 0) roles.set(shape.id, { role: 'host', vertical: index.isVertical(shape) })
  }
  return roles
}

/** The core of the stacking for horizontal pools (design D1); a vertical pool is mirrored into it by {@link stackLanes}. */
function stackCore(shapes: CanvasShape[], mappings: NotationMapping[], ignore: Set<string>): CanvasShape[] {
  const index = laneIndex(shapes, mappings, ignore)
  const roots = shapes.filter((shape) => index.lanesOf(shape).length > 0 && !index.isStacked(shape))
  if (roots.length === 0) {
    return shapes
  }
  const result = shapes.map((shape) => ({ ...shape }))
  const out = new Map(result.map((shape) => [shape.id, shape]))
  const bottom = (list: CanvasShape[], top: number) => (list.length === 0 ? 0 : round2(Math.max(...list.map((shape) => round2(shape.y + shape.height))) - top))
  const natural = new Map<string, number>()
  const span = new Map<string, { left: number; right: number }>()
  // bottom-up: the heights and the horizontal extents, read from the shapes as they were
  const measure = (shape: CanvasShape): number => {
    const lanes = index.lanesOf(shape)
    let height: number
    if (lanes.length === 0) {
      height = Math.max(LANE_MIN_HEIGHT, shape.height, bottom(index.tree(shape), shape.y))
      span.set(shape.id, { left: shape.x, right: round2(shape.x + shape.width) })
    } else {
      const sum = round2(lanes.reduce((total, lane) => total + measure(lane), 0))
      height = Math.max(sum, bottom(index.directContent(shape), shape.y))
      span.set(shape.id, {
        left: Math.min(shape.x, ...lanes.map((lane) => round2(span.get(lane.id)!.left - BAND_WIDTH))),
        right: Math.max(round2(shape.x + shape.width), ...lanes.map((lane) => span.get(lane.id)!.right)),
      })
    }
    natural.set(shape.id, round2(height))
    return height
  }
  roots.forEach(measure)
  // top-down: the positions
  const place = (shape: CanvasShape, x: number, y: number, width: number, height: number) => {
    const target = out.get(shape.id)!
    const dy = round2(y - target.y)
    const moveAll = (list: CanvasShape[]) => {
      if (dy !== 0) list.forEach((entry) => (out.get(entry.id)!.y = limitPosition(round2(out.get(entry.id)!.y + dy))))
    }
    target.x = limitPosition(x)
    target.y = limitPosition(y)
    target.width = limitSize(width)
    target.height = limitSize(height)
    const lanes = index.lanesOf(shape)
    if (lanes.length === 0) {
      moveAll(index.tree(shape))
      return
    }
    moveAll(index.directContent(shape))
    const heights = lanes.map((lane) => natural.get(lane.id)!)
    const sum = round2(heights.reduce((total, entry) => total + entry, 0))
    if (height > sum) heights[heights.length - 1] = round2(heights[heights.length - 1]! + height - sum)
    let cursor = y
    lanes.forEach((lane, position) => {
      place(lane, round2(x + BAND_WIDTH), cursor, round2(width - BAND_WIDTH), heights[position]!)
      cursor = round2(cursor + heights[position]!)
    })
  }
  for (const root of roots) {
    const extent = span.get(root.id)!
    place(root, extent.left, out.get(root.id)!.y, round2(extent.right - extent.left), natural.get(root.id)!)
  }
  return result
}

/**
 * The lanes stacked (design D1, D5): the lanes nested in a pool or lane fill its width right of the label band and lie from
 * its top one below the other without gaps, in the order of their `y`; the height of a pool or lane with lanes is the sum of
 * the heights of its lanes, and a lane is at least 30 pt high and at least as high as its content. A lane that moves takes
 * its content with it; the content keeps its place across the lanes, and the container widens where a lane does. The lanes
 * of a vertical pool follow the same rules transposed: the band at the top, the lanes side by side. Without lanes the
 * shapes are returned as they are.
 */
export function stackLanes(shapes: CanvasShape[], mappings: NotationMapping[]): CanvasShape[] {
  const index = laneIndex(shapes, mappings)
  const keys = new Map(mappings.map((mapping) => [mapping.metatypeId, mapping.notationElement]))
  const inside = new Set<string>()
  let current = shapes
  for (const pool of shapes) {
    if (pool.orientation !== 'vertical' || keys.get(pool.metatypeId) !== 'bpmn:Pool' || index.lanesOf(pool).length === 0 || inside.has(pool.id)) {
      continue
    }
    inside.add(pool.id)
    index.tree(pool).forEach((entry) => inside.add(entry.id))
    current = inFrame(current, pool.id, (list) => stackCore(list, mappings, new Set()))
  }
  return stackCore(current, mappings, inside)
}

/**
 * The shapes with every container grown to hold what is nested in it and the lanes stacked (design D1): the stacking runs
 * after the growing and the growing once more after it, until nothing changes.
 */
export function layoutLanes(shapes: CanvasShape[], mappings: NotationMapping[]): CanvasShape[] {
  let current = growContainers(shapes)
  for (let round = 0; round < 4; round += 1) {
    const next = growContainers(stackLanes(current, mappings))
    if (sameGeometry(current, next)) {
      return next
    }
    current = next
  }
  return current
}

/**
 * Where a dropped lane goes (design D1, D5): onto the content area of a stacked lane it becomes the sibling before or after
 * it (by the half of the lane the point lies in, the upper or, in a vertical pool, the left one); onto a label band or into a
 * pool without lanes it is nested there, at the place of the point among the lanes; onto another shape it is nested in it as
 * any shape. `index` is the place among the lanes of the container.
 */
export function laneDrop(
  shapes: CanvasShape[],
  id: string,
  point: { x: number; y: number },
  mappings: NotationMapping[],
  canContainShape: (shape: CanvasShape) => boolean,
): { container: CanvasShape | undefined; index?: number } {
  const lanes = laneIndex(shapes, mappings)
  const own = new Set([id, ...descendantsOf(shapes, id).map((shape) => shape.id)])
  const candidate = containerAt(shapes, point, own, canContainShape)
  if (!candidate) {
    return { container: undefined }
  }
  const vertical = lanes.isVertical(candidate)
  const along = (shape: CanvasShape) => (vertical ? { start: shape.x, size: shape.width } : { start: shape.y, size: shape.height })
  const pointAlong = vertical ? point.x : point.y
  const pointAcross = vertical ? point.y : point.x
  const bandEnd = vertical ? candidate.y + BAND_WIDTH : candidate.x + BAND_WIDTH
  if (lanes.isStacked(candidate) && pointAcross >= bandEnd) {
    const parent = lanes.byId.get(candidate.parentShapeId!)!
    const siblings = lanes.lanesOf(parent).filter((lane) => lane.id !== id)
    const position = siblings.findIndex((lane) => lane.id === candidate.id)
    const first = pointAlong < along(candidate).start + along(candidate).size / 2
    return { container: parent, index: first ? position : position + 1 }
  }
  if (lanes.isHost(candidate)) {
    const siblings = lanes.lanesOf(candidate).filter((lane) => lane.id !== id)
    const before = siblings.findIndex((lane) => along(lane).start + along(lane).size / 2 > pointAlong)
    return { container: candidate, index: before < 0 ? siblings.length : before }
  }
  return { container: candidate }
}

/**
 * The lane put into the container at `index` among its lanes (design D1, D5): it takes the container's extent across the
 * lanes right of (below, in a vertical pool) the label band, the extent of the container along the lanes when it is the first
 * lane, and the lanes lie one after the other from the start of the container in the new order, each with its content. A lane
 * of another container is nested in it first.
 */
export function placeLane(shapes: CanvasShape[], id: string, containerId: string, index: number, mappings: NotationMapping[]): CanvasShape[] {
  const lanes = laneIndex(shapes, mappings)
  const container = lanes.byId.get(containerId)
  const lane = lanes.byId.get(id)
  if (!container || !lane) {
    return shapes
  }
  const vertical = lanes.isVertical(container)
  const others = lanes.lanesOf(container).filter((entry) => entry.id !== id)
  const first = others.length === 0
  const fitted = shapes.map((shape) => {
    if (shape.id !== id) return shape
    return vertical
      ? { ...shape, y: round2(container.y + BAND_WIDTH), height: limitSize(round2(container.height - BAND_WIDTH)), width: first ? limitSize(container.width) : shape.width, error: undefined }
      : { ...shape, x: round2(container.x + BAND_WIDTH), width: limitSize(round2(container.width - BAND_WIDTH)), height: first ? limitSize(container.height) : shape.height, error: undefined }
  })
  const order = [...others]
  order.splice(Math.min(Math.max(index, 0), order.length), 0, lane)
  let list = fitted
  let cursor = vertical ? container.x : container.y
  for (const entry of order) {
    const current = list.find((shape) => shape.id === entry.id)!
    list = vertical ? moveTree(list, entry.id, round2(cursor - current.x), 0) : moveTree(list, entry.id, 0, round2(cursor - current.y))
    cursor = round2(cursor + (vertical ? current.width : current.height))
  }
  return lane.parentShapeId === containerId ? list : nestShape(list, id, containerId)
}

/** The last lane below a pool or lane, at any depth: its last lane, and that one's last lane. */
function lastLeaf(lanes: LaneIndex, shape: CanvasShape): CanvasShape {
  const own = lanes.lanesOf(shape)
  return own.length === 0 ? shape : lastLeaf(lanes, own[own.length - 1]!)
}

function resizeBottom(shapes: CanvasShape[], id: string, height: number, mappings: NotationMapping[]): CanvasShape[] {
  const lanes = laneIndex(shapes, mappings)
  const shape = lanes.byId.get(id)
  if (!shape) {
    return shapes
  }
  const leaf = lastLeaf(lanes, shape)
  const delta = round2(limitSize(height) - shape.height)
  const withLeaf = (leafHeight: number) => layoutLanes(shapes.map((entry) => (entry.id === leaf.id ? { ...entry, height: limitSize(leafHeight), error: undefined } : entry)), mappings)
  // the extent of the outermost container is the bottom of its last lane: the container itself is limited to 10 000 pt
  const total = (list: CanvasShape[]) => {
    const found = laneIndex(list, mappings)
    const root = laneRoot(found, id) ?? found.byId.get(id)!
    const last = lastLeaf(found, root)
    return round2(last.y + last.height - root.y)
  }
  let next = withLeaf(round2(leaf.height + delta))
  const excess = round2(total(next) - CANVAS_LIMITS.sizeMax)
  if (excess > 0) {
    next = withLeaf(round2(leaf.height + delta - excess))
  }
  return next
}

/**
 * A lane resized at its far edge along the lanes (its bottom edge, or its right edge in a vertical pool), or a pool or lane
 * with lanes resized there, which changes its last lane, recursively (design D1, D5). `size` is the new height, or the new
 * width in a vertical pool. The lanes after it shift with their content and the containers follow; a lane stays at least
 * 30 pt in that extent and as large as its content, and the outermost container at most 10 000 pt.
 */
export function resizeAlong(shapes: CanvasShape[], id: string, size: number, mappings: NotationMapping[]): CanvasShape[] {
  return framed(shapes, mappings, id, (list) => resizeBottom(list, id, size, mappings))
}

/** The lane levels below a pool or lane: 0 without lanes, 1 for lanes without lanes of their own. */
function laneLevels(lanes: LaneIndex, shape: CanvasShape): number {
  const own = lanes.lanesOf(shape)
  return own.length === 0 ? 0 : 1 + Math.max(...own.map((lane) => laneLevels(lanes, lane)))
}

/** The bounds of the edges across the lanes of a horizontal pool or lane with lanes: how far left and right they may move and the least width. */
function hostBounds(shapes: CanvasShape[], id: string, mappings: NotationMapping[]): { maxLeft: number; minRight: number; minWidth: number } {
  const lanes = laneIndex(shapes, mappings)
  const host = lanes.byId.get(id)!
  let maxLeft = Number.POSITIVE_INFINITY
  let minRight = Number.NEGATIVE_INFINITY
  const visit = (container: CanvasShape, bands: number) => {
    for (const shape of lanes.directContent(container)) {
      maxLeft = Math.min(maxLeft, round2(shape.x - bands * BAND_WIDTH))
      minRight = Math.max(minRight, round2(shape.x + shape.width))
    }
    lanes.lanesOf(container).forEach((lane) => visit(lane, bands + 1))
  }
  visit(host, 0)
  return { maxLeft, minRight, minWidth: round2(laneLevels(lanes, host) * BAND_WIDTH + 1) }
}

function resizeSides(shapes: CanvasShape[], id: string, box: { x: number; width: number }, mappings: NotationMapping[]): CanvasShape[] {
  const lanes = laneIndex(shapes, mappings)
  const host = lanes.byId.get(id)
  if (!host) {
    return shapes
  }
  const bounds = hostBounds(shapes, id, mappings)
  const leftMoved = round2(box.x) !== host.x
  let left = Math.min(round2(box.x), leftMoved ? bounds.maxLeft : host.x)
  let right = Math.max(round2(box.x + box.width), leftMoved ? round2(host.x + host.width) : bounds.minRight)
  if (!leftMoved) left = host.x
  if (round2(right - left) < bounds.minWidth) {
    if (leftMoved) left = round2(right - bounds.minWidth)
    else right = round2(left + bounds.minWidth)
  }
  const extents = new Map<string, { x: number; width: number }>()
  const spread = (container: CanvasShape, x: number, width: number) => {
    extents.set(container.id, { x, width })
    lanes.lanesOf(container).forEach((lane) => spread(lane, round2(x + BAND_WIDTH), round2(width - BAND_WIDTH)))
  }
  spread(host, limitPosition(left), limitSize(round2(right - left)))
  return layoutLanes(shapes.map((shape) => (extents.has(shape.id) ? { ...shape, ...extents.get(shape.id)!, error: undefined } : shape)), mappings)
}

/**
 * A pool or lane with lanes resized at its edges across the lanes (left and right, or top and bottom in a vertical pool;
 * design D1, D5): the extent goes to every lane, recursively, and the content keeps its place. The new edges are kept clear
 * of the content, and the extent is at least 1 pt for the innermost lane beside one label band per lane level (31 pt for one
 * level, 271 pt for 9); the edge that did not move gives way to the one that did. `start` and `extent` are the new left and
 * width, or top and height in a vertical pool.
 */
export function resizeAcross(shapes: CanvasShape[], id: string, box: { start: number; extent: number }, mappings: NotationMapping[]): CanvasShape[] {
  const index = laneIndex(shapes, mappings)
  const root = laneRoot(index, id)
  if (!root) {
    return shapes
  }
  if (!index.isVertical(root)) {
    return resizeSides(shapes, id, { x: box.start, width: box.extent }, mappings)
  }
  // in the frame of the pool the top is the left edge, measured from the top left corner the pool has now
  const corner = { x: root.x, y: root.y }
  return inFrame(shapes, root.id, (list) => resizeSides(list, id, { x: round2(corner.x + (box.start - corner.y)), width: box.extent }, mappings))
}

/**
 * The orientation of a pool changed (design D5): the lanes keep their order and each lane's extent across the flow (a
 * horizontal lane of 100 pt height is a vertical one of 100 pt width), the content keeps its offset to the top left corner
 * of the lane with x and y exchanged and its size; then the stacking and the growing run.
 */
export function setOrientation(shapes: CanvasShape[], poolId: string, vertical: boolean, mappings: NotationMapping[]): CanvasShape[] {
  const pool = shapes.find((shape) => shape.id === poolId)
  if (!pool || (pool.orientation === 'vertical') === vertical) {
    return shapes
  }
  const index = laneIndex(shapes, mappings)
  const structure = new Set<string>([poolId])
  const collect = (container: CanvasShape) => index.lanesOf(container).forEach((lane) => (structure.add(lane.id), collect(lane)))
  collect(pool)
  const nested = new Set(descendantsOf(shapes, poolId).map((shape) => shape.id))
  const origin = { x: pool.x, y: pool.y }
  const mirrored = shapes.map((shape) => {
    if (structure.has(shape.id)) return reflect(shape, origin)
    if (nested.has(shape.id)) return { ...shape, x: reflect(shape, origin).x, y: reflect(shape, origin).y }
    return shape
  })
  return layoutLanes(
    mirrored.map((shape) => (shape.id === poolId ? { ...shape, orientation: vertical ? ('vertical' as const) : undefined, error: undefined } : shape)),
    mappings,
  )
}

/** The shapes drawn at their collapsed size: a collapsed shape is {@link COLLAPSED_SIZE}, the others as stored. */
export function drawnShapes(shapes: CanvasShape[]): CanvasShape[] {
  return shapes.some((shape) => shape.collapsed) ? shapes.map((shape) => (shape.collapsed ? { ...shape, ...COLLAPSED_SIZE } : shape)) : shapes
}

/** The IDs of the shapes that a collapsed sub-process hides: everything nested in it, at any depth. */
export function hiddenShapes(shapes: CanvasShape[]): Set<string> {
  const hidden = new Set<string>()
  for (const shape of shapes) {
    if (shape.collapsed) {
      descendantsOf(shapes, shape.id).forEach((entry) => hidden.add(entry.id))
    }
  }
  return hidden
}

/** The shapes with the collapsed sub-processes around a shape expanded, so that the shape is shown. */
export function expandAncestors(shapes: CanvasShape[], id: string): CanvasShape[] {
  const byId = new Map(shapes.map((shape) => [shape.id, shape]))
  const open = new Set<string>()
  for (let parent = byId.get(id)?.parentShapeId; parent; parent = byId.get(parent)?.parentShapeId) {
    if (byId.get(parent)?.collapsed) open.add(parent)
  }
  return open.size === 0 ? shapes : shapes.map((shape) => (open.has(shape.id) ? { ...shape, collapsed: undefined } : shape))
}

/** The lanes of a pool or lane in their order along the lanes (by `x` in a vertical pool, else by `y`). */
export function lanesIn(shapes: CanvasShape[], mappings: NotationMapping[], containerId: string): CanvasShape[] {
  const index = laneIndex(shapes, mappings)
  const container = index.byId.get(containerId)
  return container ? index.lanesOf(container) : []
}

/** Whether the lanes of the pool or lane lie side by side: its outermost pool is vertical. */
export function laneVertical(shapes: CanvasShape[], mappings: NotationMapping[], id: string): boolean {
  const index = laneIndex(shapes, mappings)
  const shape = index.byId.get(id)
  return shape !== undefined && index.isVertical(shape)
}
