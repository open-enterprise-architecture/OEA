import type { DiagramEntity, DiagramShape, DiagramView, DiagramViewWrite } from '../../api/diagramView'
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
  /** a new entity, created with the save; its shape shows it by `key` */
  newEntity?: { key: string; metatypeId: string }
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

/** The request of the save: the shapes in drawing order and the new entities, whose names are trimmed. */
export function writeRequest(shapes: CanvasShape[]): DiagramViewWrite {
  const named = shapes.filter((shape) => shape.entityId !== undefined || (shape.newEntity && shape.name.trim().length > 0))
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
      .map((shape) => ({ key: shape.newEntity!.key, metatypeId: shape.newEntity!.metatypeId, name: shape.name.trim() })),
  }
}

/** The part of a shape that a save stores; two shapes with the same fingerprint are the same. */
function fingerprint(shape: CanvasShape): string {
  return JSON.stringify([shape.id, shape.entityId ?? shape.newEntity?.key, shape.x, shape.y, shape.width, shape.height])
}

/** Whether the shapes differ from the saved ones in shape, entity, position, size or order. */
export function hasUnsavedChanges(shapes: CanvasShape[], saved: CanvasShape[]): boolean {
  const current = shapes.filter((shape) => !shape.editing || shape.name.trim().length > 0)
  return current.length !== saved.length || current.some((shape, index) => fingerprint(shape) !== fingerprint(saved[index]))
}

/** Whether a shape is new or differs from its saved version. */
export function isUnsaved(shape: CanvasShape, saved: CanvasShape[]): boolean {
  const before = saved.find((entry) => entry.id === shape.id)
  return !before || fingerprint(before) !== fingerprint(shape)
}

/**
 * The shapes a 422 names, as the indexes of `shapes` of the request: `shapes[i]` names the shape,
 * `newEntities[j]` the shape of the j-th new entity.
 */
export function rejectedShapes(
  errors: { field: string; message: string }[],
  request: DiagramViewWrite,
  shapes: CanvasShape[],
): Map<string, string> {
  const rejected = new Map<string, string>()
  const byId = new Map(shapes.map((shape) => [shape.id, shape]))
  for (const error of errors) {
    const shape = /^shapes\[(\d+)]/.exec(error.field)
    const entity = /^newEntities\[(\d+)]/.exec(error.field)
    if (shape) {
      const id = request.shapes[Number(shape[1])]?.id
      if (id && byId.has(id) && !rejected.has(id)) rejected.set(id, error.message)
    } else if (entity) {
      const key = request.newEntities?.[Number(entity[1])]?.key
      for (const candidate of shapes) {
        if (candidate.newEntity?.key === key && !rejected.has(candidate.id)) rejected.set(candidate.id, error.message)
      }
    }
  }
  return rejected
}
