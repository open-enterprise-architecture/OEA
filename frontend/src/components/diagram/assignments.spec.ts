import { describe, expect, it } from 'vitest'
import {
  addAssignment,
  addCreated,
  assignmentBlock,
  assignmentLanesAbove,
  assignmentRequest,
  containmentChange,
  forgetShape,
  initials,
  isAssigned,
  markContained,
  NO_ASSIGNMENT_EDITS,
  organizationContext,
  parentContainer,
  removalBlocked,
  removalNeeded,
  removeAssignment,
  replaceOrgUnit,
  roleChips,
  savedAssignments,
  severalPersons,
  shownAssignments,
  type AssignmentEdits,
  type OrganizationIds,
  type SavedAssignment,
} from './assignments'
import { writeRequest, type CanvasShape } from './canvasModel'

// specs diagram/bpmn-assignments and diagram/diagram-connections at the model of the canvas (us-075-lane-assignments D3, D4, D6, D8)

const IDS: OrganizationIds = { orgUnit: 'ou', role: 'ro', person: 'pe', belongsTo: 'bt', performsRole: 'pr', responsible: 're' }

const saved = (part: SavedAssignment['part'], connectionId: number, entityId: number, name: string, version = 1): SavedAssignment => ({ part, connectionId, connectionVersion: version, entityId, name })

function shape(id: string, entityId: number, name: string, metatypeId: string, parentShapeId?: string): CanvasShape {
  return { id, entityId, name, metatypeId, x: 0, y: 0, width: 90, height: 60, missingMandatory: [], ...(parentShapeId ? { parentShapeId } : {}) }
}

describe('shown assignments', () => {
  it('reads the assignments of a shape of the view by part', () => {
    const read = savedAssignments({
      assignments: {
        orgUnits: [{ connectionId: 1, connectionVersion: 2, entityId: 10, name: 'Purchasing Department' }],
        roles: [{ connectionId: 2, connectionVersion: 1, entityId: 11, name: 'Approver' }],
        persons: [],
        realizedBy: [{ connectionId: 3, connectionVersion: 1, entityId: 12, name: 'PostgreSQL' }],
      },
    })
    expect(read).toEqual([saved('orgUnit', 1, 10, 'Purchasing Department', 2), saved('role', 2, 11, 'Approver'), saved('realizer', 3, 12, 'PostgreSQL')])
    expect(savedAssignments({ assignments: null })).toEqual([])
  })

  it('shows the saved ones without the removed and the pending ones, by part and name', () => {
    const list = [saved('role', 2, 11, 'Reviewer'), saved('role', 3, 12, 'Approver'), saved('orgUnit', 1, 10, 'Unit')]
    let edits = removeAssignment(NO_ASSIGNMENT_EDITS, 's1', shownAssignments(list, NO_ASSIGNMENT_EDITS, 's1')[2]!)
    edits = addAssignment(edits, list, 's1', 'role', { entityId: 13, name: 'Auditor' })
    const shown = shownAssignments(list, edits, 's1')
    expect(shown.map((entry) => `${entry.part}:${entry.name}${entry.pending ? '*' : ''}`)).toEqual(['orgUnit:Unit', 'role:Approver', 'role:Auditor*'])
  })

  it('does not add an assignment that is shown already', () => {
    const list = [saved('role', 2, 11, 'Approver')]
    const same = addAssignment(NO_ASSIGNMENT_EDITS, list, 's1', 'role', { entityId: 11, name: 'Approver' })
    expect(same).toBe(NO_ASSIGNMENT_EDITS)
    const once = addAssignment(NO_ASSIGNMENT_EDITS, list, 's1', 'role', { entityId: 12, name: 'Reviewer' })
    expect(addAssignment(once, list, 's1', 'role', { entityId: 12, name: 'Reviewer' })).toBe(once)
    expect(isAssigned(list, once, 's1', 'role', { entityId: 12 })).toBe(true)
    expect(isAssigned(list, once, 's2', 'role', { entityId: 12 })).toBe(false)
  })

  it('adds a removed assignment again by cancelling the removal', () => {
    const list = [saved('orgUnit', 1, 10, 'Unit')]
    const removed = removeAssignment(NO_ASSIGNMENT_EDITS, 's1', shownAssignments(list, NO_ASSIGNMENT_EDITS, 's1')[0]!)
    expect(removed.removed).toHaveLength(1)
    const again = addAssignment(removed, list, 's1', 'orgUnit', { entityId: 10, name: 'Unit' })
    expect(again.removed).toHaveLength(0)
    expect(again.added).toHaveLength(0)
  })

  it('removes a pending assignment from the edits and a saved one with its version', () => {
    const list = [saved('person', 4, 20, 'Maria Müller', 3)]
    const added = addAssignment(NO_ASSIGNMENT_EDITS, list, 's1', 'role', { entityId: 11, name: 'Approver' })
    const pending = shownAssignments(list, added, 's1', 'role')[0]!
    expect(removeAssignment(added, 's1', pending).added).toHaveLength(0)
    const gone = removeAssignment(added, 's1', shownAssignments(list, added, 's1', 'person')[0]!)
    expect(gone.removed).toEqual([{ shapeId: 's1', part: 'person', name: 'Maria Müller', connectionId: 4, connectionVersion: 3 }])
    // removed twice: once
    expect(removeAssignment(gone, 's1', shownAssignments(list, NO_ASSIGNMENT_EDITS, 's1')[0]!).removed).toHaveLength(1)
  })

  it('replaces the organizational unit: the old connection is removed and the new one added in one change', () => {
    const list = [saved('orgUnit', 1, 10, 'First')]
    const replaced = replaceOrgUnit(NO_ASSIGNMENT_EDITS, list, 's1', { entityId: 11, name: 'Second' })
    expect(replaced.removed.map((entry) => entry.connectionId)).toEqual([1])
    expect(shownAssignments(list, replaced, 's1', 'orgUnit').map((entry) => entry.name)).toEqual(['Second'])
    // the unit it has already: nothing changes
    expect(replaceOrgUnit(NO_ASSIGNMENT_EDITS, list, 's1', { entityId: 10, name: 'First' })).toBe(NO_ASSIGNMENT_EDITS)
  })

  it('keeps a created target while an assignment uses it and drops it with the last one', () => {
    const created = addCreated(NO_ASSIGNMENT_EDITS, [], 's1', { part: 'orgUnit', metatypeId: 'ou', name: 'Logistics' })
    expect(created.created).toHaveLength(1)
    expect(created.added[0]!.newEntityKey).toBe(created.created[0]!.key)
    const dropped = removeAssignment(created, 's1', shownAssignments([], created, 's1')[0]!)
    expect(dropped.created).toHaveLength(0)
    expect(forgetShape(created, 's1').created).toHaveLength(0)
  })

  it('marks the assignments the lanes around stand for', () => {
    const shown = shownAssignments([saved('role', 2, 11, 'Approver'), saved('role', 3, 12, 'Reviewer')], NO_ASSIGNMENT_EDITS, 's1')
    const marked = markContained(shown, [{ shapeId: 'l', part: 'role', entityId: 11, name: 'Approver' }])
    expect(marked.map((entry) => entry.contained === true)).toEqual([true, false])
  })
})

describe('the request of the save', () => {
  it('names new connections without an edge, created targets without a shape and removed connections with their versions', () => {
    const list = [saved('orgUnit', 1, 10, 'Old', 4)]
    let edits = replaceOrgUnit(NO_ASSIGNMENT_EDITS, list, 'lane', { entityId: 11, name: 'New' })
    edits = addCreated(edits, list, 'lane', { part: 'role', metatypeId: 'ro', name: ' Auditor ' })
    const request = assignmentRequest(edits, new Set(['lane']), IDS)
    expect(request.newConnections).toEqual([
      { key: edits.added[0]!.key, metatypeId: 'bt', sourceShapeId: 'lane', targetEntityId: 11 },
      { key: edits.added[1]!.key, metatypeId: 'pr', sourceShapeId: 'lane', targetNewEntityKey: edits.created[0]!.key },
    ])
    expect(request.newEntities).toEqual([{ key: edits.created[0]!.key, metatypeId: 'ro', name: 'Auditor' }])
    expect(request.removedConnections).toEqual([{ connectionId: 1, expectedVersion: 4 }])
  })

  it('leaves out an assignment of a shape that is not placed and one of a part that is unset', () => {
    const edits = addAssignment(addAssignment(NO_ASSIGNMENT_EDITS, [], 'gone', 'role', { entityId: 1, name: 'A' }), [], 'lane', 'person', { entityId: 2, name: 'B' })
    expect(assignmentRequest(edits, new Set(['lane']), { ...IDS, responsible: undefined }).newConnections).toEqual([])
    expect(assignmentRequest(edits, new Set(['lane']), IDS).newConnections).toHaveLength(1)
  })

  it('is part of the write request, next to the connections of the edges', () => {
    const lane = shape('lane', 5, 'Purchasing', 'lane-type')
    const edits = addAssignment(NO_ASSIGNMENT_EDITS, [], 'lane', 'orgUnit', { entityId: 11, name: 'Unit' })
    const request = writeRequest([lane], {}, [], [], {}, edits, IDS)
    expect(request.newConnections).toHaveLength(1)
    expect(request.newConnections![0]).toMatchObject({ sourceShapeId: 'lane', targetEntityId: 11, metatypeId: 'bt' })
    expect(writeRequest([lane], {}, [], [], {}, NO_ASSIGNMENT_EDITS, IDS).removedConnections).toBeUndefined()
    const removal = removeAssignment(NO_ASSIGNMENT_EDITS, 'lane', shownAssignments([saved('orgUnit', 1, 10, 'Old', 7)], NO_ASSIGNMENT_EDITS, 'lane')[0]!)
    expect(writeRequest([lane], {}, [], [], {}, removal, IDS).removedConnections).toEqual([{ connectionId: 1, expectedVersion: 7 }])
  })

  /** held connections, the first limit hit: a save creates at most 500 connections */
  it.each([
    [498, undefined],
    [499, undefined],
    [500, 'connections'],
  ])('accepts the 500th connection of a save and refuses the 501st (%i held)', (held, expected) => {
    expect(assignmentBlock(NO_ASSIGNMENT_EDITS, { connections: held, entities: 0 }, false)).toBe(expected)
  })

  it.each([
    [499, undefined],
    [500, 'entities'],
  ])('accepts the 500th created entity of a save and refuses the 501st (%i held)', (held, expected) => {
    expect(assignmentBlock(NO_ASSIGNMENT_EDITS, { connections: 0, entities: held }, true)).toBe(expected)
    // an assignment without a created target is not limited by entities
    expect(assignmentBlock(NO_ASSIGNMENT_EDITS, { connections: 0, entities: held }, false)).toBeUndefined()
  })

  it.each([
    [499, false],
    [500, true],
  ])('refuses a removal at the limit of removed connections (%i removed)', (count, blocked) => {
    const edits: AssignmentEdits = { added: [], created: [], removed: Array.from({ length: count }, (_, index) => ({ shapeId: 's', part: 'role' as const, name: 'R', connectionId: index + 1, connectionVersion: 1 })) }
    expect(removalBlocked(edits)).toBe(blocked)
  })
})

describe('badges, chips and avatar', () => {
  it.each([
    ['Maria', 'M'],
    ['Maria Müller', 'MM'],
    ['Maria Anna Müller', 'MA'],
    ['  maria   müller ', 'MM'],
    ['élodie dupont', 'ÉD'],
    ['', ''],
  ])('takes the initials of the first two words of "%s"', (name, expected) => {
    expect(initials(name)).toBe(expected)
  })

  it.each([
    [0, 0, 0],
    [3, 3, 0],
    [4, 3, 1],
    [7, 3, 4],
  ])('shows %i roles as %i chips and "+%i"', (count, shown, more) => {
    const chips = roleChips(Array.from({ length: count }, (_, index) => index))
    expect(chips.shown).toHaveLength(shown)
    expect(chips.more).toBe(more)
  })

  it('warns when a user task has more than one responsible person', () => {
    const one = shownAssignments([saved('person', 1, 20, 'Maria')], NO_ASSIGNMENT_EDITS, 's')
    expect(severalPersons(one)).toBe(false)
    const two = shownAssignments([saved('person', 1, 20, 'Maria'), saved('person', 2, 21, 'Kurt')], NO_ASSIGNMENT_EDITS, 's')
    expect(severalPersons(two)).toBe(true)
    // two roles are no warning
    expect(severalPersons(shownAssignments([saved('role', 1, 20, 'A'), saved('role', 2, 21, 'B')], NO_ASSIGNMENT_EDITS, 's'))).toBe(false)
  })
})

describe('the organization context', () => {
  const metatypes = [
    { id: 'ou', name: 'Organizational Unit' },
    { id: 'dept', name: 'Department', baseMetatypeId: 'ou' },
    { id: 'ro', name: 'Role' },
    { id: 'lane', name: 'Lane' },
  ]
  const model = (part: (id: string | null) => { metatypeId: string | null }) => ({
    orgUnit: part('ou'),
    role: part('ro'),
    person: part(null),
    belongsTo: part(null),
    performsRole: part(null),
    responsible: part(null),
    version: 1,
  })

  it('knows the part of a metatype and of its subtypes', () => {
    const context = organizationContext(model((id) => ({ metatypeId: id })), metatypes)
    expect(context.partOf('ou')).toBe('orgUnit')
    expect(context.partOf('dept')).toBe('orgUnit')
    expect(context.partOf('ro')).toBe('role')
    expect(context.partOf('lane')).toBeUndefined()
    expect(context.partOf('person')).toBeUndefined()
    expect(context.types('orgUnit').map((type) => type.name)).toEqual(['Organizational Unit', 'Department'])
    expect(context.types('person')).toEqual([])
    expect(context.ids.orgUnit).toBe('ou')
  })
})

describe('assignment lanes and pools (design D8)', () => {
  // the viewpoint maps "Role" to a lane and "Organizational Unit" to a pool: those shapes are assignment lanes
  const part = (shape: CanvasShape): 'orgUnit' | 'role' | undefined => (shape.metatypeId === 'role' ? 'role' : shape.metatypeId === 'unit' ? 'orgUnit' : undefined)
  const unit = shape('unit', 1, 'Purchasing Department', 'unit')
  const role = shape('role', 2, 'Approver', 'role', 'unit')
  const other = shape('other', 3, 'Clerk', 'role', 'unit')
  const plainPool = shape('pool', 4, 'Order handling', 'pool')
  const plainRole = shape('prole', 5, 'Approver 2', 'role', 'pool')
  const bpmnLane = shape('lane', 6, 'Lane', 'lane', 'pool')
  const task = (parent?: string) => shape('task', 9, 'Approve order', 'task', parent)
  const all = [unit, role, other, plainPool, plainRole, bpmnLane, task('role')]

  it('lists the lanes around a shape, nearest first, and the parent container that is no assignment lane', () => {
    expect(assignmentLanesAbove(all, 'task', part).map((lane) => `${lane.part}:${lane.name}`)).toEqual(['role:Approver', 'orgUnit:Purchasing Department'])
    expect(parentContainer(all, 'task', part)).toBeUndefined()
    const inPool = [plainPool, plainRole, task('prole')]
    expect(parentContainer(inPool, 'task', part)?.name).toBe('Order handling')
    // a lane of another metatype is the parent, no assignment lane
    expect(assignmentLanesAbove([plainPool, bpmnLane, task('lane')], 'task', part)).toEqual([])
    expect(parentContainer([plainPool, bpmnLane, task('lane')], 'task', part)?.name).toBe('Lane')
  })

  it('assigns a task dropped into a role lane: performs-role, and the parent stays that of the pool', () => {
    const list = [plainPool, plainRole, task('prole')]
    const after = assignmentLanesAbove(list, 'task', part)
    const outcome = containmentChange(NO_ASSIGNMENT_EDITS, [], 'task', [], after, true)
    expect('edits' in outcome && outcome.edits.added.map((entry) => `${entry.part}:${entry.entityId}`)).toEqual(['role:5'])
    expect(parentContainer(list, 'task', part)?.entityId).toBe(4)
  })

  it('assigns both the organizational unit and the role for a task in a role lane of an organizational unit pool', () => {
    const outcome = containmentChange(NO_ASSIGNMENT_EDITS, [], 'task', [], assignmentLanesAbove(all, 'task', part), true)
    expect('edits' in outcome && outcome.edits.added.map((entry) => `${entry.part}:${entry.entityId}`).sort()).toEqual(['orgUnit:1', 'role:2'])
    // a role lane in an organizational unit pool gets the belongs-to connection itself
    const lane = containmentChange(NO_ASSIGNMENT_EDITS, [], 'role', [], assignmentLanesAbove(all, 'role', part), true)
    expect('edits' in lane && lane.edits.added.map((entry) => `${entry.part}:${entry.entityId}`)).toEqual(['orgUnit:1'])
  })

  it('moves a task between two role lanes: one connection removed, one added, and the unit stays', () => {
    const connections = [saved('role', 30, 2, 'Approver', 5), saved('orgUnit', 31, 1, 'Purchasing Department', 6)]
    const before = assignmentLanesAbove([unit, role, other, task('role')], 'task', part)
    const after = assignmentLanesAbove([unit, role, other, task('other')], 'task', part)
    const outcome = containmentChange(NO_ASSIGNMENT_EDITS, connections, 'task', before, after, true)
    expect('edits' in outcome).toBe(true)
    if ('edits' in outcome) {
      expect(outcome.edits.removed).toEqual([{ shapeId: 'task', part: 'role', name: 'Approver', connectionId: 30, connectionVersion: 5 }])
      expect(outcome.edits.added.map((entry) => `${entry.part}:${entry.entityId}`)).toEqual(['role:3'])
    }
  })

  it('refuses moving out of a lane without entity:delete when the assignment is saved, and allows it for a pending one', () => {
    const connections = [saved('role', 30, 2, 'Approver', 5)]
    const before = assignmentLanesAbove([unit, role, task('role')], 'task', part)
    expect(containmentChange(NO_ASSIGNMENT_EDITS, connections, 'task', before, [], false)).toEqual({ blocked: true })
    expect(removalNeeded(connections, NO_ASSIGNMENT_EDITS, 'task', before)).toBe(true)
    expect(containmentChange(NO_ASSIGNMENT_EDITS, connections, 'task', before, [], true)).toMatchObject({ edits: { removed: [{ connectionId: 30 }] } })
    const pending = addAssignment(NO_ASSIGNMENT_EDITS, [], 'task', 'role', { entityId: 2, name: 'Approver' })
    expect(containmentChange(pending, [], 'task', before, [], false)).toMatchObject({ edits: { added: [] } })
    expect(removalNeeded([], pending, 'task', before)).toBe(false)
  })

  it('does not send an existing connection twice', () => {
    const connections = [saved('role', 30, 2, 'Approver', 5)]
    const after = assignmentLanesAbove([unit, role, task('role')], 'task', part)
    const outcome = containmentChange(NO_ASSIGNMENT_EDITS, connections, 'task', [], after, true)
    expect('edits' in outcome && outcome.edits.added.filter((entry) => entry.part === 'role')).toEqual([])
  })

  it('keeps the connection when the shape stays in the lane', () => {
    const lanes = assignmentLanesAbove([unit, role, task('role')], 'task', part)
    const outcome = containmentChange(NO_ASSIGNMENT_EDITS, [saved('role', 30, 2, 'Approver')], 'task', lanes, lanes, false)
    expect(outcome).toEqual({ edits: NO_ASSIGNMENT_EDITS })
  })

  it('assigns a lane of a new entity by its key', () => {
    const fresh: CanvasShape = { ...shape('new-role', 0, 'Reviewer', 'role'), entityId: undefined, newEntity: { key: 'k1', metatypeId: 'role' } }
    const lanes = assignmentLanesAbove([fresh, task('new-role')], 'task', part)
    const outcome = containmentChange(NO_ASSIGNMENT_EDITS, [], 'task', [], lanes, true)
    expect('edits' in outcome && outcome.edits.added[0]).toMatchObject({ part: 'role', newEntityKey: 'k1' })
    const request = assignmentRequest('edits' in outcome ? outcome.edits : NO_ASSIGNMENT_EDITS, new Set(['task']), IDS)
    expect(request.newConnections[0]).toMatchObject({ sourceShapeId: 'task', targetNewEntityKey: 'k1', metatypeId: 'pr' })
  })
})
