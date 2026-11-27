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
import { isBpmnContainer, type BpmnSymbol } from './bpmnSymbols'
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
  return JSON.stringify([shape.id, shape.entityId ?? shape.newEntity?.key, shape.x, shape.y, shape.width, shape.height, shape.parentShapeId ?? null])
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
