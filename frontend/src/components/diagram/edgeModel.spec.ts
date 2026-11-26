import { describe, expect, it } from 'vitest'
import { CANVAS_LIMITS } from '../../api/diagramView'
import {
  allowedConnections,
  diagramView,
  edge,
  METATYPE_ASSOCIATION,
  METATYPE_COMPONENT,
  METATYPE_FLOW,
  METATYPE_PROCESS,
  shape,
} from '../../test/diagrams'
import { applyEdit, fromView, hasUnsavedChanges, isEdgeUnsaved, rejectedShapes, writeRequest, type CanvasShape } from './canvasModel'
import {
  addBendPoint,
  applyNewConnectionEdit,
  borderPoint,
  connectedCenter,
  connectedOptions,
  connectionLength,
  connectionOptions,
  edgeBlock,
  edgeLabel,
  edgeLine,
  edgeSubject,
  fromViewEdge,
  insertionIndex,
  labelPoint,
  moveBendPoint,
  newEdge,
  removeBendPoint,
  withoutShapeEdges,
  type CanvasEdge,
} from './edgeModel'

// The edges of the canvas editor in pt (spec diagram/diagram-connections)
describe('edgeModel', () => {
  const view = diagramView()
  const ERP = shape({ id: 's1', x: 0, y: 0, width: 100, height: 40 })
  const CRM = shape({ id: 's2', entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, x: 300, y: 0, width: 100, height: 40 })
  const shapes = [ERP, CRM].map(fromView)
  const drawn = (overrides: Partial<CanvasEdge> = {}): CanvasEdge => ({ ...fromViewEdge(edge({ sourceShapeId: 's1', targetShapeId: 's2' })), ...overrides })

  it('takes the length of a connection type from the viewpoint: the stored length, else 90 pt', () => {
    expect(connectionLength(view, METATYPE_FLOW)).toBe(150)
    expect(connectionLength(view, METATYPE_ASSOCIATION)).toBe(90)
    expect(connectionLength(view, 'c1000000-0000-4000-8000-0000000000ff')).toBe(90)
  })

  describe('connection types of a drawn line', () => {
    it('offers none when the metamodel allows none between the metatypes', () => {
      expect(connectionOptions(view, allowedConnections(METATYPE_PROCESS, METATYPE_PROCESS))).toEqual([])
    })

    it('offers the one type of a pair', () => {
      expect(connectionOptions(view, allowedConnections(METATYPE_COMPONENT, METATYPE_PROCESS))).toEqual([{ metatypeId: METATYPE_FLOW, name: 'Flow', reversed: false }])
    })

    it('offers several types, and leaves out one that the viewpoint does not allow', () => {
      const result = allowedConnections(METATYPE_COMPONENT, METATYPE_COMPONENT)
      expect(connectionOptions(view, result).map((option) => option.name)).toEqual(['Flow', 'Association'])
      const narrow = { ...view, viewpoint: { ...view.viewpoint, allowedRelations: view.viewpoint.allowedRelations.filter((type) => type.id === METATYPE_ASSOCIATION) } }
      expect(connectionOptions(narrow, result).map((option) => option.name)).toEqual(['Association'])
    })

    it('reverses a type that runs only from the target to the source', () => {
      expect(connectionOptions(view, allowedConnections(METATYPE_PROCESS, METATYPE_COMPONENT))).toEqual([{ metatypeId: METATYPE_FLOW, name: 'Flow', reversed: true }])
    })
  })

  describe('connected element', () => {
    it('offers the allowed types from the source, sorted by connection and target name', () => {
      const results = new Map([
        [METATYPE_COMPONENT, allowedConnections(METATYPE_COMPONENT, METATYPE_COMPONENT)],
        [METATYPE_PROCESS, allowedConnections(METATYPE_COMPONENT, METATYPE_PROCESS)],
      ])
      expect(connectedOptions(view, results).map((option) => `${option.connection.name} -> ${option.target.name}`)).toEqual([
        'Association -> Application Component',
        'Flow -> Application Component',
        'Flow -> Business Process',
      ])
    })

    it('leaves out a type that runs only towards the source', () => {
      const results = new Map([[METATYPE_COMPONENT, allowedConnections(METATYPE_PROCESS, METATYPE_COMPONENT)]])
      expect(connectedOptions(view, results)).toEqual([])
    })

    it.each([
      [150, 100 + 150 + 60],
      [90, 100 + 90 + 60],
    ])('places the new element with a gap of %i pt between the borders, vertically centered', (length, centerX) => {
      expect(connectedCenter(shapes[0]!, length, { width: 120, height: 40 })).toEqual({ x: centerX, y: 20 })
    })
  })

  describe('limits', () => {
    const many = (count: number, forNew = false): CanvasEdge[] =>
      Array.from({ length: count }, (_, index) => ({ ...drawn({ id: `e${index}` }), ...(forNew ? { newConnection: { key: `k${index}`, metatypeId: METATYPE_FLOW } } : {}) }))

    it.each([
      [CANVAS_LIMITS.edges - 1, undefined],
      [CANVAS_LIMITS.edges, 'edges'],
    ])('with %i edges the next one is %s', (count, block) => {
      expect(edgeBlock(many(count), false)).toBe(block)
    })

    it.each([
      [CANVAS_LIMITS.newConnections - 1, undefined],
      [CANVAS_LIMITS.newConnections, 'new'],
    ])('with %i new connections the next new one is %s', (count, block) => {
      expect(edgeBlock(many(count, true), true)).toBe(block)
      // an existing connection is not limited by the number of new ones
      expect(edgeBlock(many(count, true), false)).toBeUndefined()
    })

    it('blocks adding several edges at once beyond 4000', () => {
      expect(edgeBlock(many(3998), false, 2)).toBeUndefined()
      expect(edgeBlock(many(3998), false, 3)).toBe('edges')
    })

    it.each([
      [CANVAS_LIMITS.bendPoints - 1, true],
      [CANVAS_LIMITS.bendPoints, false],
    ])('accepts a bend point on an edge with %i bend points: %s', (count, accepted) => {
      const points = Array.from({ length: count }, (_, index) => ({ x: index, y: 0 }))
      const edgeWith = drawn({ points })
      const line = edgeLine(edgeWith, shapes)!
      expect(addBendPoint(edgeWith, line, { x: 200, y: 10 }) !== undefined).toBe(accepted)
    })
  })

  describe('bend points', () => {
    it('adds a bend point into the nearest segment, rounded to two decimals', () => {
      const bent = drawn({ points: [{ x: 150, y: 100 }] })
      const line = edgeLine(bent, shapes)!
      // the line runs down to the bend point, then up to the target: a point near the second segment goes behind the bend point
      expect(addBendPoint(bent, line, { x: 330.126, y: 80 })!.points).toEqual([{ x: 150, y: 100 }, { x: 330.13, y: 80 }])
      expect(addBendPoint(bent, line, { x: 120, y: 60 })!.points).toEqual([{ x: 120, y: 60 }, { x: 150, y: 100 }])
    })

    it('keeps a new bend point within the limits of the contract', () => {
      const plain = drawn()
      expect(addBendPoint(plain, edgeLine(plain, shapes)!, { x: 200000, y: -200000 })!.points).toEqual([{ x: 100000, y: -100000 }])
    })

    it('moves and removes a bend point', () => {
      const bent = drawn({ points: [{ x: 1, y: 1 }, { x: 2, y: 2 }], error: 'x' })
      expect(moveBendPoint(bent, 1, { x: 5.555, y: -3 })).toMatchObject({ points: [{ x: 1, y: 1 }, { x: 5.56, y: -3 }], error: undefined })
      expect(removeBendPoint(bent, 0).points).toEqual([{ x: 2, y: 2 }])
    })

    it('finds the segment nearest to a point', () => {
      const line = [{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 100, y: 100 }]
      expect(insertionIndex(line, { x: 50, y: 5 })).toBe(0)
      expect(insertionIndex(line, { x: 95, y: 60 })).toBe(1)
    })
  })

  describe('geometry', () => {
    it('leaves a shape at its border towards the target', () => {
      expect(borderPoint(shapes[0]!, { x: 300, y: 20 })).toEqual({ x: 100, y: 20 })
      expect(borderPoint(shapes[0]!, { x: 50, y: 400 })).toEqual({ x: 50 + 0, y: 40 })
      expect(borderPoint(shapes[0]!, { x: 50, y: 20 })).toEqual({ x: 50, y: 20 })
    })

    it('draws the line from the border of the source through the bend points to the border of the target', () => {
      const line = edgeLine(drawn({ points: [{ x: 200, y: 100 }] }), shapes)!
      expect(line).toHaveLength(3)
      expect(line[1]).toEqual({ x: 200, y: 100 })
      expect(line[0]!.x).toBeGreaterThan(0)
      expect(line[2]!.x).toBeLessThan(400)
    })

    it('draws a connection of a shape to itself as a loop and skips an edge of a shape that is not there', () => {
      expect(edgeLine(drawn({ targetShapeId: 's1' }), shapes)!.length).toBeGreaterThan(3)
      expect(edgeLine(drawn({ targetShapeId: 'gone' }), shapes)).toBeUndefined()
    })

    it('puts the label on the longest segment', () => {
      expect(labelPoint([{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 100 }])).toEqual({ x: 10, y: 50 })
    })

    it('removes the edges of a removed shape only', () => {
      const other = drawn({ id: 'other', sourceShapeId: 's1', targetShapeId: 's1' })
      expect(withoutShapeEdges([drawn(), other], 's2').map((entry) => entry.id)).toEqual(['other'])
    })
  })

  describe('names and the details panel', () => {
    it('shows the name of a connection, and "Source → Target" for a new one', () => {
      expect(edgeLabel(drawn(), shapes)).toBe('SAP ERP → Salesforce')
      const created = newEdge('s1', 's2', { metatypeId: METATYPE_FLOW, newKey: 'k' })
      expect(edgeLabel(created, shapes)).toBe('SAP ERP → Salesforce')
      expect(edgeLabel(drawn({ name: 'Orders' }), shapes)).toBe('Orders')
    })

    it('hands the connection to the panel as its entity', () => {
      expect(edgeSubject(drawn(), shapes)).toMatchObject({ entityId: 21, metatypeId: METATYPE_FLOW })
      const created = newEdge('s1', 's2', { metatypeId: METATYPE_FLOW, newKey: 'k' })
      expect(edgeSubject(created, shapes)).toMatchObject({ entityId: undefined, newEntity: { key: 'k' } })
    })

    it('enters the description and values of a new connection', () => {
      let created = newEdge('s1', 's2', { metatypeId: METATYPE_FLOW, newKey: 'k' })
      created = applyNewConnectionEdit(created, { field: 'description', value: 'Orders' })
      created = applyNewConnectionEdit(created, { field: 'property', propertyDefId: 'p', value: 'REST' })
      expect(created.newConnection).toMatchObject({ description: 'Orders', properties: { p: 'REST' } })
      expect(applyNewConnectionEdit(created, { field: 'property', propertyDefId: 'p', value: undefined }).newConnection?.properties).toEqual({})
      expect(applyNewConnectionEdit(drawn(), { field: 'description', value: 'x' })).toEqual(drawn())
    })
  })

  describe('the save', () => {
    const named: CanvasShape[] = shapes
    it('writes the edges with their bend points and the new connections once', () => {
      const created = newEdge('s1', 's2', { metatypeId: METATYPE_FLOW, newKey: 'k1' })
      const request = writeRequest(named, {}, [{ ...drawn({ points: [{ x: 1.5, y: 2 }] }), id: 'e1' }, created])
      expect(request.edges).toEqual([
        { id: 'e1', connectionId: 21, sourceShapeId: 's1', targetShapeId: 's2', points: [{ x: 1.5, y: 2 }] },
        { id: created.id, newConnectionKey: 'k1', sourceShapeId: 's1', targetShapeId: 's2', points: [] },
      ])
      expect(request.newConnections).toEqual([{ key: 'k1', metatypeId: METATYPE_FLOW }])
    })

    it('writes the description and values of a new connection, leaving out empty ones', () => {
      let created = newEdge('s1', 's2', { metatypeId: METATYPE_FLOW, newKey: 'k1' })
      created = applyNewConnectionEdit(applyNewConnectionEdit(created, { field: 'description', value: 'Orders' }), { field: 'property', propertyDefId: 'p', value: 'REST' })
      created = applyNewConnectionEdit(created, { field: 'property', propertyDefId: 'q', value: '' })
      expect(writeRequest(named, {}, [created]).newConnections).toEqual([
        { key: 'k1', metatypeId: METATYPE_FLOW, description: 'Orders', properties: [{ propertyDefId: 'p', value: 'REST' }] },
      ])
    })

    it('leaves out an edge of a shape that is not saved, and with it the new connection', () => {
      const unnamed: CanvasShape = { ...fromView(shape({ id: 's3' })), entityId: undefined, newEntity: { key: 'n', metatypeId: METATYPE_COMPONENT }, name: '', editing: true }
      const created = newEdge('s1', 's3', { metatypeId: METATYPE_FLOW, newKey: 'k1' })
      const request = writeRequest([...named, unnamed], {}, [created])
      expect(request.edges).toEqual([])
      expect(request.newConnections).toEqual([])
    })

    it('writes the change of a connection that an edge shows, and not that of one no edge shows', () => {
      const edits = applyEdit(applyEdit({}, 21, 2, { field: 'description', value: 'Changed' }), 99, 1, { field: 'description', value: 'x' })
      expect(writeRequest(named, edits, [drawn()]).entityUpdates).toEqual([{ entityId: 21, expectedVersion: 2, description: 'Changed' }])
      expect(writeRequest(named, edits, []).entityUpdates).toBeUndefined()
    })

    it('has unsaved changes for a new, moved or removed edge and a changed connection', () => {
      const saved = [drawn()]
      expect(hasUnsavedChanges(named, named, {}, saved, saved)).toBe(false)
      expect(hasUnsavedChanges(named, named, {}, [], saved)).toBe(true)
      expect(hasUnsavedChanges(named, named, {}, [...saved, newEdge('s1', 's2', { metatypeId: METATYPE_FLOW, newKey: 'k' })], saved)).toBe(true)
      expect(hasUnsavedChanges(named, named, {}, [drawn({ points: [{ x: 1, y: 1 }] })], saved)).toBe(true)
      expect(hasUnsavedChanges(named, named, applyEdit({}, 21, 2, { field: 'description', value: 'x' }), saved, saved)).toBe(true)
      expect(isEdgeUnsaved(drawn(), saved)).toBe(false)
      expect(isEdgeUnsaved(drawn({ points: [{ x: 1, y: 1 }] }), saved)).toBe(true)
    })

    it('marks the edges that a 422 names: by position, by new connection and by change of the connection', () => {
      const created = newEdge('s1', 's2', { metatypeId: METATYPE_FLOW, newKey: 'k1' })
      const existing = drawn({ id: 'e1' })
      const request = writeRequest(named, applyEdit({}, 21, 2, { field: 'description', value: 'x' }), [existing, created])
      const marks = rejectedShapes(
        [
          { field: 'edges[0].sourceShapeId', message: 'wrong shape' },
          { field: 'newConnections[0]', message: 'endpoint not allowed' },
          { field: 'entityUpdates[0].properties[0]', message: 'value missing' },
        ],
        request,
        named,
        [existing, created],
      )
      expect(marks.edges.get('e1')).toBe('wrong shape')
      expect(marks.edges.get(created.id)).toBe('endpoint not allowed')
      expect(marks.fields.get('k1|')).toBe('endpoint not allowed')
    })
  })
})
