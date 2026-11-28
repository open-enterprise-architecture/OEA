import type { DiagramAssignments, DiagramNewConnection, DiagramNewEntity, DiagramRemovedConnection, DiagramShape } from '../../api/diagramView'
import { CANVAS_LIMITS } from '../../api/diagramView'
import type { OrganizationModel } from '../../api/organization'
import type { CanvasShape } from './canvasModel'

// The assignments of lanes, pools and tasks (change us-075-lane-assignments; US-075, US-076, REQ-077): the organizational
// unit, the roles and the responsible persons of a shape are connections without an edge. This module keeps the unsaved
// changes of them, builds their part of the save request and decides which assignments the lane a shape lies in
// stands for (design D8: a lane is an assignment).

/** What an assignment points to: an organizational unit, a role or a person. */
export type Part = 'orgUnit' | 'role' | 'person'

/** The API name of a part in the search (`part` of `searchDiagramReferenceTargets`). */
export const SEARCH_PART: Record<Part, 'org-unit' | 'role' | 'person'> = { orgUnit: 'org-unit', role: 'role', person: 'person' }

/** The notation keys of the shapes that carry assignments besides lanes and pools (design D3). */
export const TASK_ELEMENTS = [
  'bpmn:Task',
  'bpmn:UserTask',
  'bpmn:ServiceTask',
  'bpmn:SendTask',
  'bpmn:ReceiveTask',
  'bpmn:BusinessRuleTask',
  'bpmn:ScriptTask',
]

/** The assignments of a saved shape, as the view read them: a connection with its version. */
export interface SavedAssignment {
  part: Part
  connectionId: number
  connectionVersion: number
  entityId: number
  name: string
}

/** A new assignment: a connection without an edge, created with the save. Its target is an entity or a new entity by its key. */
export interface AddedAssignment {
  key: string
  shapeId: string
  part: Part
  entityId?: number
  newEntityKey?: string
  name: string
}

/** The removal of a saved assignment with the connection version read. */
export interface RemovedAssignment {
  shapeId: string
  part: Part
  name: string
  connectionId: number
  connectionVersion: number
}

/** An organizational unit, role or person created with the save, without a shape (quick create). */
export interface CreatedTarget {
  key: string
  part: Part
  metatypeId: string
  name: string
}

/** The unsaved changes of the assignments. */
export interface AssignmentEdits {
  added: AddedAssignment[]
  removed: RemovedAssignment[]
  created: CreatedTarget[]
}

export const NO_ASSIGNMENT_EDITS: AssignmentEdits = { added: [], removed: [], created: [] }

/** An assignment as the shape shows it: saved, or pending until the next save. */
export interface ShownAssignment {
  part: Part
  name: string
  entityId?: number
  newEntityKey?: string
  /** the saved connection; none for a pending assignment */
  connectionId?: number
  connectionVersion?: number
  pending: boolean
  /** the lane the shape lies in stands for it: it is removed by moving the shape out, not with "×" */
  contained?: boolean
}

/** The key of a target: `e<ID>` for an entity, `n<key>` for a new one. */
export function targetKey(target: { entityId?: number; newEntityKey?: string }): string {
  return target.entityId !== undefined ? `e${target.entityId}` : `n${target.newEntityKey}`
}

/** The saved assignments of a shape of the view; none for a shape that carries none. */
export function savedAssignments(shape: Pick<DiagramShape, 'assignments'>): SavedAssignment[] {
  const read = (items: DiagramAssignments['orgUnits'], part: Part): SavedAssignment[] =>
    items.map((item) => ({ part, connectionId: item.connectionId, connectionVersion: item.connectionVersion, entityId: item.entityId, name: item.name }))
  const assignments = shape.assignments
  return assignments ? [...read(assignments.orgUnits, 'orgUnit'), ...read(assignments.roles, 'role'), ...read(assignments.persons, 'person')] : []
}

/** The assignments the shape shows: the saved ones without the removed, then the pending ones; by part, then name. */
export function shownAssignments(saved: SavedAssignment[], edits: AssignmentEdits, shapeId: string, part?: Part): ShownAssignment[] {
  const removed = new Set(edits.removed.filter((entry) => entry.shapeId === shapeId).map((entry) => entry.connectionId))
  const fromSaved: ShownAssignment[] = saved
    .filter((entry) => !removed.has(entry.connectionId))
    .map((entry) => ({ part: entry.part, name: entry.name, entityId: entry.entityId, connectionId: entry.connectionId, connectionVersion: entry.connectionVersion, pending: false }))
  const fromAdded: ShownAssignment[] = edits.added
    .filter((entry) => entry.shapeId === shapeId)
    .map((entry) => ({ part: entry.part, name: entry.name, entityId: entry.entityId, newEntityKey: entry.newEntityKey, pending: true }))
  return [...fromSaved, ...fromAdded].filter((entry) => part === undefined || entry.part === part).sort((a, b) => order(a.part) - order(b.part) || a.name.localeCompare(b.name))
}

function order(part: Part): number {
  return part === 'orgUnit' ? 0 : part === 'role' ? 1 : 2
}

/** Whether an assignment to this target is shown already. */
export function isAssigned(saved: SavedAssignment[], edits: AssignmentEdits, shapeId: string, part: Part, target: { entityId?: number; newEntityKey?: string }): boolean {
  const key = targetKey(target)
  return shownAssignments(saved, edits, shapeId, part).some((entry) => targetKey(entry) === key)
}

/**
 * The edits with an assignment added; one that is shown already changes nothing, and one that was removed before
 * and is added again only cancels the removal. A created target stays with the edits only while an assignment uses it.
 */
export function addAssignment(
  edits: AssignmentEdits,
  saved: SavedAssignment[],
  shapeId: string,
  part: Part,
  target: { entityId?: number; newEntityKey?: string; name: string },
): AssignmentEdits {
  if (isAssigned(saved, edits, shapeId, part, target)) {
    return edits
  }
  const cancelled = target.entityId !== undefined ? saved.find((entry) => entry.part === part && entry.entityId === target.entityId) : undefined
  const removal = cancelled ? edits.removed.find((entry) => entry.shapeId === shapeId && entry.connectionId === cancelled.connectionId) : undefined
  if (removal) {
    return { ...edits, removed: edits.removed.filter((entry) => entry !== removal) }
  }
  const added: AddedAssignment = {
    key: crypto.randomUUID(),
    shapeId,
    part,
    ...(target.entityId !== undefined ? { entityId: target.entityId } : { newEntityKey: target.newEntityKey }),
    name: target.name,
  }
  return { ...edits, added: [...edits.added, added] }
}

/** The edits with a target created for the save (quick create) and assigned to the shape. */
export function addCreated(edits: AssignmentEdits, saved: SavedAssignment[], shapeId: string, created: Omit<CreatedTarget, 'key'>): AssignmentEdits {
  const target: CreatedTarget = { ...created, key: crypto.randomUUID() }
  const next = addAssignment({ ...edits, created: [...edits.created, target] }, saved, shapeId, created.part, { newEntityKey: target.key, name: created.name })
  return next
}

/** The edits without an assignment: a pending one is dropped, a saved one is removed with its connection version. */
export function removeAssignment(edits: AssignmentEdits, shapeId: string, assignment: ShownAssignment): AssignmentEdits {
  if (assignment.pending) {
    const key = targetKey(assignment)
    const added = edits.added.filter((entry) => !(entry.shapeId === shapeId && entry.part === assignment.part && targetKey(entry) === key))
    return pruned({ ...edits, added })
  }
  if (assignment.connectionId === undefined || assignment.connectionVersion === undefined) {
    return edits
  }
  if (edits.removed.some((entry) => entry.connectionId === assignment.connectionId)) {
    return edits
  }
  return { ...edits, removed: [...edits.removed, { shapeId, part: assignment.part, name: assignment.name, connectionId: assignment.connectionId, connectionVersion: assignment.connectionVersion }] }
}

/** The created targets that no assignment uses any more are dropped. */
function pruned(edits: AssignmentEdits): AssignmentEdits {
  const used = new Set(edits.added.map((entry) => entry.newEntityKey).filter((key): key is string => key !== undefined))
  const created = edits.created.filter((entry) => used.has(entry.key))
  return created.length === edits.created.length ? edits : { ...edits, created }
}

/** The edits for a shape that left the diagram: its pending assignments are dropped; the saved ones stay unless removed on purpose. */
export function forgetShape(edits: AssignmentEdits, shapeId: string): AssignmentEdits {
  return pruned({ ...edits, added: edits.added.filter((entry) => entry.shapeId !== shapeId) })
}

/**
 * The edits with the organizational unit of the shape replaced: the ones shown are removed and the new one is added
 * (BR-03, one unit at most); the replacement is one save.
 */
export function replaceOrgUnit(
  edits: AssignmentEdits,
  saved: SavedAssignment[],
  shapeId: string,
  target: { entityId?: number; newEntityKey?: string; name: string },
): AssignmentEdits {
  let next = edits
  for (const shown of shownAssignments(saved, edits, shapeId, 'orgUnit')) {
    if (targetKey(shown) !== targetKey(target)) {
      next = removeAssignment(next, shapeId, shown)
    }
  }
  return addAssignment(next, saved, shapeId, 'orgUnit', target)
}

/** Whether anything is edited. */
export function assignmentsEdited(edits: AssignmentEdits): boolean {
  return edits.added.length > 0 || edits.removed.length > 0
}

/** The saved removals that a shape of the diagram and a pending assignment of it no longer need are kept; the others are those of the shapes still on the canvas. */
export function pendingCount(edits: AssignmentEdits): number {
  return edits.added.length + edits.removed.length
}

/** The connection types and element types of the organization model, as IDs. */
export interface OrganizationIds {
  orgUnit?: string
  role?: string
  person?: string
  belongsTo?: string
  performsRole?: string
  responsible?: string
}

/** The IDs of the parts of the organization model that are set. */
export function organizationIds(model: OrganizationModel): OrganizationIds {
  const id = (part: { metatypeId?: string | null }) => part.metatypeId ?? undefined
  return {
    orgUnit: id(model.orgUnit),
    role: id(model.role),
    person: id(model.person),
    belongsTo: id(model.belongsTo),
    performsRole: id(model.performsRole),
    responsible: id(model.responsible),
  }
}

/** The connection type of the part: belongs-to, performs-role or responsible; `undefined` while it is unset. */
export function connectionOf(ids: OrganizationIds, part: Part): string | undefined {
  return part === 'orgUnit' ? ids.belongsTo : part === 'role' ? ids.performsRole : ids.responsible
}

/** What the assignments add to the request of the save. */
export interface AssignmentRequest {
  newConnections: DiagramNewConnection[]
  newEntities: DiagramNewEntity[]
  removedConnections: DiagramRemovedConnection[]
}

/**
 * The new connections without an edge (the source shape and the target entity or new entity), the created targets they
 * name, and the removed connections with their versions. An assignment of a shape that is not placed is left out.
 */
export function assignmentRequest(edits: AssignmentEdits, placed: Set<string>, ids: OrganizationIds): AssignmentRequest {
  const added = edits.added.filter((entry) => placed.has(entry.shapeId) && connectionOf(ids, entry.part) !== undefined)
  const used = new Set(added.map((entry) => entry.newEntityKey).filter((key): key is string => key !== undefined))
  return {
    newConnections: added.map((entry) => ({
      key: entry.key,
      metatypeId: connectionOf(ids, entry.part)!,
      sourceShapeId: entry.shapeId,
      ...(entry.entityId !== undefined ? { targetEntityId: entry.entityId } : { targetNewEntityKey: entry.newEntityKey }),
    })),
    newEntities: edits.created
      .filter((entry) => used.has(entry.key))
      .map((entry) => ({ key: entry.key, metatypeId: entry.metatypeId, name: entry.name.trim() })),
    removedConnections: edits.removed.map((entry) => ({ connectionId: entry.connectionId, expectedVersion: entry.connectionVersion })),
  }
}

/**
 * Why another assignment cannot be added: a save creates at most 500 connections and 500 entities and removes at most
 * 500 connections (`forNew`: the assignment creates a target); `undefined` when there is room. `connections` and
 * `entities` count what the canvas already holds in the request (`newConnections` and `newEntities` of the save).
 */
export function assignmentBlock(edits: AssignmentEdits, held: { connections: number; entities: number }, forNew: boolean): 'connections' | 'entities' | undefined {
  if (held.connections + edits.added.length >= CANVAS_LIMITS.newConnections) {
    return 'connections'
  }
  if (forNew && held.entities + edits.created.length >= CANVAS_LIMITS.newEntities) {
    return 'entities'
  }
  return undefined
}

/** Whether another removal fits: a save removes at most 500 connections. */
export function removalBlocked(edits: AssignmentEdits): boolean {
  return edits.removed.length >= CANVAS_LIMITS.removedConnections
}

/** The initials of a name: the first letters of its first two words, upper case. */
export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .filter((word) => word.length > 0)
    .slice(0, 2)
    .map((word) => Array.from(word)[0]!.toUpperCase())
    .join('')
}

/** The chips of the roles of a lane: at most three, then "+N" for the others. */
export function roleChips<T>(roles: T[], limit = 3): { shown: T[]; more: number } {
  return { shown: roles.slice(0, limit), more: Math.max(0, roles.length - limit) }
}

/** Whether a user task has several responsible persons: a warning in the editor, the save is accepted (BR-05). */
export function severalPersons(shown: ShownAssignment[]): boolean {
  return shown.filter((entry) => entry.part === 'person').length > 1
}

// Assignment lanes and pools (design D8)

/** A lane or pool whose entity is of the organizational unit or role part: a shape in it is assigned instead of nested. */
export interface LaneTarget {
  shapeId: string
  part: 'orgUnit' | 'role'
  entityId?: number
  newEntityKey?: string
  name: string
}

/**
 * The assignment lanes and pools around a shape, nearest first: its ancestors whose entity is an organizational unit or a role
 * (`lanePart` decides for a shape; it answers `undefined` for a shape that is no lane or pool of such an entity).
 */
export function assignmentLanesAbove(
  shapes: CanvasShape[],
  id: string,
  lanePart: (shape: CanvasShape) => 'orgUnit' | 'role' | undefined,
  index?: Map<string, CanvasShape>,
): LaneTarget[] {
  const byId = index ?? new Map(shapes.map((shape) => [shape.id, shape]))
  const lanes: LaneTarget[] = []
  const seen = new Set<string>([id])
  let next = byId.get(id)?.parentShapeId
  while (next && !seen.has(next)) {
    seen.add(next)
    const container = byId.get(next)
    if (!container) break
    const part = lanePart(container)
    if (part && (container.entityId !== undefined || container.newEntity)) {
      lanes.push({ shapeId: container.id, part, name: container.name, ...(container.entityId !== undefined ? { entityId: container.entityId } : { newEntityKey: container.newEntity!.key }) })
    }
    next = container.parentShapeId
  }
  return lanes
}

/** The nearest container of the shape that is no assignment lane or pool: the entity that becomes its parent. */
export function parentContainer(shapes: CanvasShape[], id: string, lanePart: (shape: CanvasShape) => 'orgUnit' | 'role' | undefined): CanvasShape | undefined {
  const byId = new Map(shapes.map((shape) => [shape.id, shape]))
  const seen = new Set<string>([id])
  let next = byId.get(id)?.parentShapeId
  while (next && !seen.has(next)) {
    seen.add(next)
    const container = byId.get(next)
    if (!container) return undefined
    if (!lanePart(container)) return container
    next = container.parentShapeId
  }
  return undefined
}

/**
 * The edits after a shape entered or left assignment lanes (design D8): it is assigned to each lane it entered and the
 * assignments to the lanes it left are removed; an assignment shown already is not added again. Removing a saved assignment
 * needs `entity:delete`: without it the move is `blocked`.
 */
export function containmentChange(
  edits: AssignmentEdits,
  saved: SavedAssignment[],
  shapeId: string,
  before: LaneTarget[],
  after: LaneTarget[],
  canDelete: boolean,
): { edits: AssignmentEdits } | { blocked: true } {
  const kept = new Set(after.map(targetKey))
  const had = new Set(before.map(targetKey))
  const left = before.filter((lane) => !kept.has(targetKey(lane)))
  const entered = after.filter((lane) => !had.has(targetKey(lane)))
  let next = edits
  for (const lane of left) {
    for (const shown of shownAssignments(saved, next, shapeId, lane.part)) {
      if (targetKey(shown) !== targetKey(lane)) continue
      if (!shown.pending && !canDelete) return { blocked: true }
      next = removeAssignment(next, shapeId, shown)
    }
  }
  for (const lane of entered) {
    next = addAssignment(next, saved, shapeId, lane.part, lane)
  }
  return { edits: next }
}

/** Whether moving the shape out of the lanes `left` removes a saved assignment, which needs `entity:delete`. */
export function removalNeeded(saved: SavedAssignment[], edits: AssignmentEdits, shapeId: string, left: LaneTarget[]): boolean {
  return left.some((lane) => shownAssignments(saved, edits, shapeId, lane.part).some((shown) => !shown.pending && targetKey(shown) === targetKey(lane)))
}

/** The assignments of the shape that the lanes around it stand for: they have no "×". */
export function markContained(shown: ShownAssignment[], lanes: LaneTarget[]): ShownAssignment[] {
  const keys = new Set(lanes.map((lane) => `${lane.part}:${targetKey(lane)}`))
  return shown.map((entry) => (keys.has(`${entry.part}:${targetKey(entry)}`) ? { ...entry, contained: true } : entry))
}

/** A field of the details panel that lists the assignments of one part: "Required role" or "Responsible person". */
export interface AssignmentField {
  part: Part
  label: string
  items: ShownAssignment[]
  /** "Add" is offered: an editor */
  mayAdd: boolean
  /** "×" is offered for an assignment: not for one the lane stands for, and a saved one only with `entity:delete` */
  removable: (assignment: ShownAssignment) => boolean
  /** more than one responsible person: a warning, the save is accepted (BR-05) */
  several: boolean
}

/** Whether the shape has unsaved assignments: a pending one, or a removed saved one. */
export function hasPendingAssignments(edits: AssignmentEdits, shapeId: string): boolean {
  return edits.added.some((entry) => entry.shapeId === shapeId) || edits.removed.some((entry) => entry.shapeId === shapeId)
}

/** The edits without the pending assignments of shapes that are gone; the removals stay (their source is on the stored layout). */
export function pruneAssignments(edits: AssignmentEdits, shapeIds: Set<string>): AssignmentEdits {
  const added = edits.added.filter((entry) => shapeIds.has(entry.shapeId))
  return added.length === edits.added.length ? edits : pruned({ ...edits, added })
}

/** The organization model with the part each metatype plays, subtypes included (design D2, D8). */
export interface OrganizationContext {
  ids: OrganizationIds
  /** the part a metatype plays: the metatype of a part or a subtype of it */
  partOf: (metatypeId: string) => Part | undefined
  /** the metatype of an element part and its subtypes, for "Create new" */
  types: (part: Part) => { id: string; name: string }[]
}

export function organizationContext(model: OrganizationModel, metatypes: { id: string; name: string; baseMetatypeId?: string | null }[]): OrganizationContext {
  const ids = organizationIds(model)
  const bases = new Map(metatypes.map((type) => [type.id, type.baseMetatypeId ?? undefined]))
  const roots: [Part, string | undefined][] = [
    ['orgUnit', ids.orgUnit],
    ['role', ids.role],
    ['person', ids.person],
  ]
  const partOf = (metatypeId: string): Part | undefined => {
    let next: string | undefined = metatypeId
    for (let guard = 0; next !== undefined && guard < 64; guard++) {
      const found = roots.find(([, root]) => root === next)
      if (found) return found[0]
      next = bases.get(next)
    }
    return undefined
  }
  return {
    ids,
    partOf,
    types: (part) => metatypes.filter((type) => partOf(type.id) === part).sort((a, b) => (a.id === roots.find(([name]) => name === part)?.[1] ? -1 : b.id === roots.find(([name]) => name === part)?.[1] ? 1 : a.name.localeCompare(b.name))).map((type) => ({ id: type.id, name: type.name })),
  }
}
