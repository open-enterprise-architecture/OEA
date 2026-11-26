import type { AllowedConnectionResult, DiagramEdge, DiagramView } from '../../api/diagramView'
import { CANVAS_LIMITS } from '../../api/diagramView'
import type { CanvasLine, CanvasShape, FieldChange } from './canvasModel'

// The edges of the canvas editor (design D5 of us-074-diagram-connections), without the canvas library:
// connections drawn between shapes with bend points, the connection types between two metatypes, the
// default length for a connected element, and the geometry of a line. Positions are in pt.

/** The length of a connection metatype that the viewpoint stores no length for, in pt. */
export const DEFAULT_LENGTH = 90

/** A bend point or a point of a line, in pt. */
export interface Point {
  x: number
  y: number
}

/**
 * An edge on the canvas: a connection from a shape of its source to a shape of its target entity. A
 * new connection is created with the next save; its description and values (property definition ID to
 * text) are entered in the details panel, like those of a new entity.
 */
export interface CanvasEdge {
  id: string
  /** the existing connection; `undefined` for a connection that is created with the next save */
  connectionId?: number
  newConnection?: { key: string; metatypeId: string; description?: string; properties?: Record<string, string> }
  /** the name of an existing connection; empty for a new one (it is named after its ends) */
  name: string
  metatypeId: string
  sourceShapeId: string
  targetShapeId: string
  points: Point[]
  missingMandatory: string[]
  /** the reason a save rejected the edge; cleared by the next change of the edge */
  error?: string
}

/** The edge of a saved view. */
export function fromViewEdge(edge: DiagramEdge): CanvasEdge {
  return {
    id: edge.id,
    connectionId: edge.connection.id,
    name: edge.connection.name,
    metatypeId: edge.connection.metatypeId,
    sourceShapeId: edge.sourceShapeId,
    targetShapeId: edge.targetShapeId,
    points: edge.points.map((point) => ({ x: point.x, y: point.y })),
    missingMandatory: [...edge.missingMandatory],
  }
}

/** The name an edge shows: the name of its connection, or "Source → Target" for a new one. */
export function edgeLabel(edge: CanvasEdge, shapes: CanvasShape[]): string {
  if (edge.name.trim().length > 0) {
    return edge.name
  }
  const source = shapes.find((shape) => shape.id === edge.sourceShapeId)?.name ?? ''
  const target = shapes.find((shape) => shape.id === edge.targetShapeId)?.name ?? ''
  return `${source} → ${target}`
}

/** What the details panel needs of a shape or an edge: the entity or connection it shows. */
export interface DetailsSubject {
  entityId?: number
  newEntity?: { key: string; metatypeId: string; description?: string; properties?: Record<string, string> }
  name: string
  metatypeId: string
}

/** The details panel's view of an edge: its connection is the entity. */
export function edgeSubject(edge: CanvasEdge, shapes: CanvasShape[]): DetailsSubject {
  return { entityId: edge.connectionId, newEntity: edge.newConnection, name: edgeLabel(edge, shapes), metatypeId: edge.metatypeId }
}

/** The length in pt of a connection metatype in the viewpoint: its stored length, else 90. */
export function connectionLength(view: DiagramView, metatypeId: string): number {
  return view.viewpoint.effectiveMappings.find((entry) => entry.metatypeId === metatypeId)?.length ?? DEFAULT_LENGTH
}

/** A connection metatype that a line between two shapes may have; `reversed` when the dragged-to shape is its source. */
export interface ConnectionOption {
  metatypeId: string
  name: string
  reversed: boolean
}

/**
 * The connection types for a line drawn from a shape of metatype A to one of metatype B: the
 * metamodel's answer for A and B, narrowed to the connection types the viewpoint allows. A type that
 * runs only from B to A is reversed (the edge runs from the shape of B).
 */
export function connectionOptions(view: DiagramView, result: AllowedConnectionResult): ConnectionOption[] {
  const allowed = new Map(view.viewpoint.allowedRelations.map((type) => [type.id, type.name]))
  return result.options
    .filter((option) => allowed.has(option.connectionMetatype.id))
    .map((option) => ({
      metatypeId: option.connectionMetatype.id,
      name: allowed.get(option.connectionMetatype.id)!,
      reversed: option.direction === 'target-to-source',
    }))
}

/** A connection type and a target metatype for "Add connected element". */
export interface ConnectedOption {
  connection: { id: string; name: string }
  target: { id: string; name: string }
}

/**
 * The choices for a connected new element of a source metatype: the connection types the viewpoint
 * allows from it, each with the viewpoint's element metatypes it allows as target. `results` holds
 * the allowed connections from the source metatype to each element metatype of the viewpoint.
 */
export function connectedOptions(view: DiagramView, results: Map<string, AllowedConnectionResult>): ConnectedOption[] {
  const relations = new Map(view.viewpoint.allowedRelations.map((type) => [type.id, type.name]))
  const options: ConnectedOption[] = []
  for (const target of view.viewpoint.allowedMetatypes) {
    for (const option of results.get(target.id)?.options ?? []) {
      if (option.direction !== 'target-to-source' && relations.has(option.connectionMetatype.id)) {
        options.push({ connection: { id: option.connectionMetatype.id, name: relations.get(option.connectionMetatype.id)! }, target: { id: target.id, name: target.name } })
      }
    }
  }
  return options.sort((a, b) => a.connection.name.localeCompare(b.connection.name) || a.target.name.localeCompare(b.target.name))
}

/** The centre of a new element placed to the right of its source: the gap between the borders is the length. */
export function connectedCenter(source: CanvasShape, length: number, target: { width: number; height: number }): Point {
  return { x: source.x + source.width + length + target.width / 2, y: source.y + source.height / 2 }
}

/** A new edge of an existing connection or of a new one, without bend points. */
export function newEdge(source: string, target: string, connection: { connectionId?: number; name?: string; metatypeId: string; newKey?: string }): CanvasEdge {
  return {
    id: crypto.randomUUID(),
    ...(connection.connectionId !== undefined ? { connectionId: connection.connectionId } : {}),
    ...(connection.newKey !== undefined ? { newConnection: { key: connection.newKey, metatypeId: connection.metatypeId } } : {}),
    name: connection.name ?? '',
    metatypeId: connection.metatypeId,
    sourceShapeId: source,
    targetShapeId: target,
    points: [],
    missingMandatory: [],
  }
}

/**
 * Why another edge cannot be added: a diagram holds at most 4000 edges and a save creates at most
 * 500 new connections; `undefined` when there is room.
 */
export function edgeBlock(edges: CanvasEdge[], forNew: boolean, adding = 1): 'edges' | 'new' | undefined {
  if (edges.length + adding > CANVAS_LIMITS.edges) {
    return 'edges'
  }
  if (forNew && edges.filter((edge) => edge.newConnection).length + adding > CANVAS_LIMITS.newConnections) {
    return 'new'
  }
  return undefined
}

/** The edges without those of a removed shape. */
export function withoutShapeEdges(edges: CanvasEdge[], shapeId: string): CanvasEdge[] {
  return edges.filter((edge) => edge.sourceShapeId !== shapeId && edge.targetShapeId !== shapeId)
}

/** The bend points of a connection of a shape to itself, so that it is drawn as a loop. */
function loop(source: CanvasShape): Point[] {
  const cx = source.x + source.width / 2
  const cy = source.y + source.height / 2
  return [
    { x: cx + source.width / 2 + 30, y: cy },
    { x: cx + source.width / 2 + 30, y: cy - source.height / 2 - 30 },
    { x: cx, y: cy - source.height / 2 - 30 },
  ]
}

/** The point where the line from the centre of the shape towards `toward` leaves the shape. */
export function borderPoint(shape: CanvasShape, toward: Point): Point {
  const cx = shape.x + shape.width / 2
  const cy = shape.y + shape.height / 2
  const dx = toward.x - cx
  const dy = toward.y - cy
  if (dx === 0 && dy === 0) {
    return { x: cx, y: cy }
  }
  const scale = Math.min(dx === 0 ? Infinity : shape.width / 2 / Math.abs(dx), dy === 0 ? Infinity : shape.height / 2 / Math.abs(dy), 1)
  return { x: cx + dx * scale, y: cy + dy * scale }
}

/**
 * The line of an edge in pt: from the border of its source shape through the bend points to the
 * border of its target shape; `undefined` when a shape of the edge is not on the canvas.
 */
export function edgeLine(edge: CanvasEdge, shapes: CanvasShape[]): Point[] | undefined {
  const source = shapes.find((shape) => shape.id === edge.sourceShapeId)
  const target = shapes.find((shape) => shape.id === edge.targetShapeId)
  if (!source || !target) {
    return undefined
  }
  const bends = edge.points.length === 0 && source.id === target.id ? loop(source) : edge.points
  const centreOf = (shape: CanvasShape): Point => ({ x: shape.x + shape.width / 2, y: shape.y + shape.height / 2 })
  const start = borderPoint(source, bends[0] ?? centreOf(target))
  const end = borderPoint(target, bends.at(-1) ?? centreOf(source))
  return [start, ...bends, end]
}

/**
 * The line of a hierarchy line in pt: from the border of the container to the border of the child's shape;
 * `undefined` when a shape of the line is not on the canvas.
 */
export function hierarchyPath(line: CanvasLine, shapes: CanvasShape[]): Point[] | undefined {
  return edgeLine(
    { id: line.id, name: '', metatypeId: '', sourceShapeId: line.parentShapeId, targetShapeId: line.childShapeId, points: [], missingMandatory: [] },
    shapes,
  )
}

/** The middle of the longest segment of a line, where the name of the connection is drawn. */
export function labelPoint(line: Point[]): Point {
  let best = 0
  let point = line[0]!
  for (let i = 0; i + 1 < line.length; i += 1) {
    const length = Math.hypot(line[i + 1]!.x - line[i]!.x, line[i + 1]!.y - line[i]!.y)
    if (length >= best) {
      best = length
      point = { x: (line[i]!.x + line[i + 1]!.x) / 2, y: (line[i]!.y + line[i + 1]!.y) / 2 }
    }
  }
  return point
}

function distanceToSegment(p: Point, a: Point, b: Point): number {
  const dx = b.x - a.x
  const dy = b.y - a.y
  const lengthSquared = dx * dx + dy * dy
  const t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared))
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

/** The index at which a bend point at `point` is inserted into the bend points of a line: the nearest segment. */
export function insertionIndex(line: Point[], point: Point): number {
  let best = 0
  let distance = Infinity
  for (let i = 0; i + 1 < line.length; i += 1) {
    const d = distanceToSegment(point, line[i]!, line[i + 1]!)
    if (d < distance) {
      distance = d
      best = i
    }
  }
  return best
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

function limit(value: number): number {
  return Math.min(CANVAS_LIMITS.position, Math.max(-CANVAS_LIMITS.position, round2(value)))
}

/** The edge with a bend point added at `point` (pt); `undefined` when it has 50 already. */
export function addBendPoint(edge: CanvasEdge, line: Point[], point: Point): CanvasEdge | undefined {
  if (edge.points.length >= CANVAS_LIMITS.bendPoints) {
    return undefined
  }
  const points = [...edge.points]
  points.splice(insertionIndex(line, point), 0, { x: limit(point.x), y: limit(point.y) })
  return { ...edge, points, error: undefined }
}

/** The edge with a bend point moved to `point` (pt). */
export function moveBendPoint(edge: CanvasEdge, index: number, point: Point): CanvasEdge {
  const points = edge.points.map((entry, i) => (i === index ? { x: limit(point.x), y: limit(point.y) } : entry))
  return { ...edge, points, error: undefined }
}

/** The edge without a bend point. */
export function removeBendPoint(edge: CanvasEdge, index: number): CanvasEdge {
  return { ...edge, points: edge.points.filter((_, i) => i !== index), error: undefined }
}

/** The part of an edge that a save stores; two edges with the same fingerprint are the same. */
export function edgeFingerprint(edge: CanvasEdge): string {
  return JSON.stringify([edge.id, edge.connectionId ?? edge.newConnection?.key, edge.sourceShapeId, edge.targetShapeId, edge.points.map((point) => [point.x, point.y])])
}

/** The edge of a new connection with one field changed; its name is the one of its ends. */
export function applyNewConnectionEdit(edge: CanvasEdge, change: FieldChange): CanvasEdge {
  if (!edge.newConnection) {
    return edge
  }
  const newConnection = { ...edge.newConnection, properties: { ...edge.newConnection.properties } }
  if (change.field === 'property') {
    if (change.value === undefined) delete newConnection.properties[change.propertyDefId]
    else newConnection.properties[change.propertyDefId] = change.value
  } else if (change.field === 'description') {
    newConnection.description = change.value
  }
  return { ...edge, newConnection, error: undefined }
}
