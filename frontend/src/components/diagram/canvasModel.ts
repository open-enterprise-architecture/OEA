import type { DiagramEntity, DiagramEntityUpdate, DiagramShape, DiagramView, DiagramViewWrite } from '../../api/diagramView'
import { CANVAS_LIMITS } from '../../api/diagramView'

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
  newEntity?: { key: string; metatypeId: string; description?: string; properties?: Record<string, string> }
  name: string
  metatypeId: string
  x: number
  y: number
  width: number
  height: number
  missingMandatory: string[]
  /** the reason a save rejected the shape; cleared by the next change of the shape */
  error?: string
  /** the name of a new shape is entered in the shape itself */
  editing?: boolean
}

/** What the canvas hands to the node of a shape. */
export interface ShapeData {
  name: string
  abbreviation?: string
  hint?: string
  editable: boolean
  editing: boolean
  unsaved: boolean
  missingMandatory: string[]
  error?: string
  /** the name of the metatype, for the label of the input of a new shape */
  metatypeName: string
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
    x: shape.x,
    y: shape.y,
    width: shape.width,
    height: shape.height,
    missingMandatory: [...shape.missingMandatory],
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
export function writeRequest(shapes: CanvasShape[], edits: EntityEdits = {}): DiagramViewWrite {
  const named = shapes.filter((shape) => shape.entityId !== undefined || (shape.newEntity && shape.name.trim().length > 0))
  const shown = new Set(named.map((shape) => shape.entityId).filter((id): id is number => id !== undefined))
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
        }
      }),
    ...(updates.length > 0 ? { entityUpdates: updates } : {}),
  }
}

/** The part of a shape that a save stores; two shapes with the same fingerprint are the same. */
function fingerprint(shape: CanvasShape): string {
  return JSON.stringify([shape.id, shape.entityId ?? shape.newEntity?.key, shape.x, shape.y, shape.width, shape.height])
}

/**
 * Whether the shapes differ from the saved ones in shape, entity, position, size or order, or an
 * entity has changes of its fields.
 */
export function hasUnsavedChanges(shapes: CanvasShape[], saved: CanvasShape[], edits: EntityEdits = {}): boolean {
  const current = shapes.filter((shape) => !shape.editing || shape.name.trim().length > 0)
  return (
    current.length !== saved.length ||
    current.some((shape, index) => fingerprint(shape) !== fingerprint(saved[index])) ||
    editedCount(edits) > 0
  )
}

/** Whether a shape is new or differs from its saved version, or its entity has changes. */
export function isUnsaved(shape: CanvasShape, saved: CanvasShape[], edits: EntityEdits = {}): boolean {
  const before = saved.find((entry) => entry.id === shape.id)
  return !before || fingerprint(before) !== fingerprint(shape) || (shape.entityId !== undefined && isEdited(edits[shape.entityId]))
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
}

export function rejectedShapes(
  errors: { field: string; message: string }[],
  request: DiagramViewWrite,
  shapes: CanvasShape[],
): Rejected {
  const rejected = new Map<string, string>()
  const fields = new Map<string, string>()
  const byId = new Map(shapes.map((shape) => [shape.id, shape]))
  const mark = (shape: CanvasShape, message: string) => {
    if (!rejected.has(shape.id)) rejected.set(shape.id, message)
  }
  for (const error of errors) {
    const shape = /^shapes\[(\d+)]/.exec(error.field)
    const entity = /^newEntities\[(\d+)](?:\.(.*))?$/.exec(error.field)
    const update = /^entityUpdates\[(\d+)](?:\.(.*))?$/.exec(error.field)
    if (shape) {
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
      if (sent) fields.set(`${sent.entityId}|${fieldOf(update[2], sent.properties)}`, error.message)
    }
  }
  return { shapes: rejected, fields }
}

/** The field a path below an entity names: `name`, `description`, the property of `properties[k]...`, or empty. */
function fieldOf(path: string | undefined, properties: { propertyDefId: string }[] | undefined): string {
  if (path === 'name' || path === 'description') {
    return path
  }
  const property = /^properties\[(\d+)]/.exec(path ?? '')
  return property ? (properties?.[Number(property[1])]?.propertyDefId ?? '') : ''
}
