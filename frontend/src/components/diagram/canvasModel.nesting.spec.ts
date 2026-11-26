import { describe, expect, it } from 'vitest'
import { CANVAS_LIMITS } from '../../api/diagramView'
import { diagramView, METATYPE_COMPONENT, METATYPE_PROCESS } from '../../test/diagrams'
import {
  containerAt,
  descendantsOf,
  effectiveParent,
  fromView,
  growContainers,
  hasUnsavedChanges,
  layoutChildren,
  levelOf,
  lies,
  moveTree,
  nestBlock,
  nestedIn,
  nestShape,
  parentOfShape,
  rejectedShapes,
  sameParent,
  subtreeLevels,
  withoutShapeLines,
  writeRequest,
  CHILD_GAP,
  CONTAINER_HEADER,
  CONTAINER_PADDING,
  type CanvasLine,
  type CanvasShape,
} from './canvasModel'

// Nested shapes of the canvas editor in pt (spec diagram/diagram-nesting; design D1, D4 of us-074-diagram-nesting)

function box(id: string, x: number, y: number, width: number, height: number, extra: Partial<CanvasShape> = {}): CanvasShape {
  return { id, entityId: Number(id.replace(/\D/g, '')) || 1, name: id, metatypeId: METATYPE_COMPONENT, x, y, width, height, missingMandatory: [], ...extra }
}

/** A chain of `levels` shapes, each nested in the one before. */
function chain(levels: number): CanvasShape[] {
  return Array.from({ length: levels }, (_, index) => box(`s${index + 1}`, index, index, 1000 - index * 2, 1000 - index * 2, index === 0 ? {} : { parentShapeId: `s${index}` }))
}

describe('canvasModel nesting', () => {
  const view = diagramView()

  it('finds the shapes nested in a container at every level, in drawing order', () => {
    const shapes = [box('a', 0, 0, 500, 500), box('b', 10, 10, 100, 100, { parentShapeId: 'a' }), box('c', 20, 20, 10, 10, { parentShapeId: 'b' }), box('d', 600, 0, 50, 50)]
    expect(nestedIn(shapes, 'a').map((shape) => shape.id)).toEqual(['b'])
    expect(descendantsOf(shapes, 'a').map((shape) => shape.id)).toEqual(['b', 'c'])
    expect(descendantsOf(shapes, 'd')).toEqual([])
    expect([levelOf(shapes, 'a'), levelOf(shapes, 'b'), levelOf(shapes, 'c')]).toEqual([1, 2, 3])
    expect(subtreeLevels(shapes, 'a')).toBe(3)
    expect(subtreeLevels(shapes, 'c')).toBe(1)
  })

  it.each([
    // the levels of the chain the container ends, the levels of the subtree that is nested in it, and whether that is refused
    [9, 1, false],
    [10, 1, true],
    [8, 2, false],
    [9, 2, true],
    [1, 10, true],
    [1, 9, false],
  ])('a container on level %i holds a subtree of %i levels: refused %s', (container, subtree, refused) => {
    const nested = Array.from({ length: subtree }, (_, index) => box(`t${index + 1}`, 0, 0, 5, 5, index === 0 ? {} : { parentShapeId: `t${index}` }))
    const shapes = [...chain(container), ...nested]
    expect(nestBlock(shapes, 't1', `s${container}`)).toBe(refused ? 'depth' : undefined)
  })

  it('refuses to nest a shape in itself or in what is nested in it', () => {
    const shapes = [box('a', 0, 0, 500, 500), box('b', 10, 10, 100, 100, { parentShapeId: 'a' })]
    expect(nestBlock(shapes, 'a', 'a')).toBe('cycle')
    expect(nestBlock(shapes, 'a', 'b')).toBe('cycle')
    expect(nestBlock(shapes, 'b', 'a')).toBeUndefined()
  })

  it.each([
    [0, 0, 100, 100, true],
    [10, 20, 90, 80, true],
    [0, 0, 100.01, 100, false],
    [0, 0, 100, 100.01, false],
    [-0.01, 0, 50, 50, false],
    [0, -0.01, 50, 50, false],
    [50.01, 0, 50, 50, false],
  ])('a shape at %f, %f of %f x %f lies within a container of 100 x 100 at 0, 0: %s', (x, y, width, height, inside) => {
    expect(lies(box('c', 0, 0, 100, 100), box('s', x, y, width, height))).toBe(inside)
  })

  it('takes the topmost shape that holds the point as the container, without the excluded shapes', () => {
    const shapes = [box('a', 0, 0, 200, 200), box('b', 50, 50, 100, 100), box('c', 400, 0, 50, 50, { editing: true })]
    expect(containerAt(shapes, { x: 60, y: 60 }, new Set())?.id).toBe('b')
    expect(containerAt(shapes, { x: 60, y: 60 }, new Set(['b']))?.id).toBe('a')
    expect(containerAt(shapes, { x: 150, y: 150 }, new Set())?.id).toBe('b')
    expect(containerAt(shapes, { x: 150.01, y: 150.01 }, new Set())?.id).toBe('a')
    expect(containerAt(shapes, { x: 300, y: 300 }, new Set())).toBeUndefined()
    expect(containerAt(shapes, { x: 410, y: 10 }, new Set())).toBeUndefined()
  })

  it('grows a container to hold what lies outside it, through every level, and never shrinks it', () => {
    const shapes = [box('a', 0, 0, 100, 100), box('b', 10, 10, 50, 50, { parentShapeId: 'a' }), box('c', 90.5, -5, 40, 30, { parentShapeId: 'b' })]
    const grown = growContainers(shapes)
    expect(grown.find((shape) => shape.id === 'b')).toMatchObject({ x: 10, y: -5, width: 120.5, height: 65 })
    expect(grown.find((shape) => shape.id === 'a')).toMatchObject({ x: 0, y: -5, width: 130.5, height: 105 })
    expect(growContainers(grown)).toEqual(grown)
    // nothing outside: the container keeps its size
    const calm = [box('a', 0, 0, 500, 500), box('b', 10, 10, 5, 5, { parentShapeId: 'a' })]
    expect(growContainers(calm)[0]).toMatchObject({ x: 0, y: 0, width: 500, height: 500 })
  })

  it('moves a shape with what is nested in it', () => {
    const shapes = [box('a', 0, 0, 100, 100), box('b', 10, 10, 20, 20, { parentShapeId: 'a' }), box('c', 500, 500, 20, 20)]
    const result = moveTree(shapes, 'a', 5.5, -2)
    expect(result.map((shape) => [shape.x, shape.y])).toEqual([[5.5, -2], [15.5, 8], [500, 500]])
  })

  it('nests a shape directly behind its container, with what is nested in it, and grows the container', () => {
    const shapes = [box('a', 0, 0, 100, 100), box('x', 150, 0, 40, 40), box('y', 155, 5, 10, 10, { parentShapeId: 'x' }), box('z', 300, 300, 5, 5)]
    const result = nestShape(shapes, 'x', 'a')
    expect(result.map((shape) => shape.id)).toEqual(['a', 'x', 'y', 'z'])
    expect(result.find((shape) => shape.id === 'x')?.parentShapeId).toBe('a')
    expect(result.find((shape) => shape.id === 'a')).toMatchObject({ width: 190, height: 100 })
    // behind the container even when the shape was drawn before it
    const before = [box('x', 150, 0, 40, 40), box('a', 0, 0, 300, 100), box('z', 400, 0, 5, 5)]
    expect(nestShape(before, 'x', 'a').map((shape) => shape.id)).toEqual(['a', 'x', 'z'])
  })

  it('puts a shape back on the canvas without a container', () => {
    const shapes = [box('a', 0, 0, 100, 100), box('b', 10, 10, 20, 20, { parentShapeId: 'a' })]
    const result = nestShape(shapes, 'b', undefined)
    expect(result.find((shape) => shape.id === 'b')?.parentShapeId).toBeUndefined()
    expect(result.map((shape) => shape.id)).toEqual(['a', 'b'])
  })

  it('lays the children out in a grid with as many columns as make the container about square and grows the container', () => {
    const container = box('a', 100, 50, 90, 30)
    const children = Array.from({ length: 5 }, (_, index) => ({ id: 20 + index, name: `Child ${index}`, metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: 7 }))
    const { shapes, added } = layoutChildren(view, [container], container, children)
    // 5 children: 3 columns, 2 rows of 120 x 40 pt
    expect(added).toHaveLength(5)
    const first = added[0]!
    expect(first).toMatchObject({ x: 100 + CONTAINER_PADDING, y: 50 + CONTAINER_HEADER, width: 120, height: 40, parentShapeId: 'a', entityId: 20, parentEntityId: 7 })
    expect(added.map((shape) => shape.x)).toEqual([110, 240, 370, 110, 240])
    expect(added.map((shape) => shape.y)).toEqual([74, 74, 74, 124, 124])
    const grown = shapes[0]!
    expect(grown.width).toBe(CONTAINER_PADDING + 3 * 120 + 2 * CHILD_GAP)
    expect(grown.height).toBe(CONTAINER_HEADER + 2 * 40 + CHILD_GAP)
    expect(added.every((shape) => lies(grown, shape))).toBe(true)
    expect(shapes.map((shape) => shape.id)).toEqual(['a', ...added.map((shape) => shape.id)])
  })

  it('places further children below the ones nested in the container already, at the default size of their type', () => {
    const container = box('a', 0, 0, 500, 200)
    const existing = box('b', 10, 24, 100, 60, { parentShapeId: 'a' })
    const { added, shapes } = layoutChildren(view, [container, existing], container, [{ id: 30, name: 'Process', metatypeId: METATYPE_PROCESS, childCount: 0, parentEntityId: 7 }])
    expect(added[0]).toMatchObject({ x: 10, y: 84 + CHILD_GAP, width: 90, height: 30 })
    expect(shapes[0]).toMatchObject({ width: 500, height: 200 })
    expect(layoutChildren(view, [container], container, []).added).toEqual([])
  })

  it('knows the parent of an entity: the change, the one read, or none', () => {
    const saved = box('a', 0, 0, 10, 10, { entityId: 5, parentEntityId: 7 })
    expect(effectiveParent(saved, {})).toEqual({ entityId: 7 })
    expect(effectiveParent(saved, { 5: { version: 2, parent: null } })).toBeNull()
    expect(effectiveParent(box('b', 0, 0, 1, 1, { entityId: 6, parentEntityId: null }), {})).toBeNull()
    const fresh = box('n', 0, 0, 1, 1, { entityId: undefined, newEntity: { key: 'k', metatypeId: METATYPE_COMPONENT, parentNewEntityKey: 'p' } })
    expect(effectiveParent(fresh, {})).toEqual({ key: 'p' })
    expect(effectiveParent({ ...fresh, newEntity: { key: 'k', metatypeId: METATYPE_COMPONENT, parentEntityId: 9 } }, {})).toEqual({ entityId: 9 })
    expect(parentOfShape(undefined)).toBeNull()
    expect(parentOfShape(saved)).toEqual({ entityId: 5 })
    expect(parentOfShape(fresh)).toEqual({ key: 'k' })
  })

  it.each([
    [null, null, true],
    [null, { entityId: 1 }, false],
    [{ entityId: 1 }, { entityId: 1 }, true],
    [{ entityId: 1 }, { entityId: 2 }, false],
    [{ key: 'a' }, { key: 'a' }, true],
    [{ key: 'a' }, { entityId: 1 }, false],
  ])('compares the parents %j and %j: %s', (a, b, same) => {
    expect(sameParent(a, b)).toBe(same)
  })

  it('drops the lines of removed shapes', () => {
    const lines: CanvasLine[] = [
      { id: 'l1', parentShapeId: 'a', childShapeId: 'b' },
      { id: 'l2', parentShapeId: 'c', childShapeId: 'd' },
    ]
    expect(withoutShapeLines(lines, new Set(['b'])).map((line) => line.id)).toEqual(['l2'])
    expect(withoutShapeLines(lines, new Set())).toHaveLength(2)
  })

  it('keeps the container of a shape of the view and nothing for a shape on the canvas', () => {
    expect(fromView({ ...diagramView().shapes[0]!, id: 's', entity: { id: 1, name: 'A', metatypeId: METATYPE_COMPONENT, childCount: 2, parentEntityId: 9 }, parentShapeId: 'c', x: 0, y: 0, width: 1, height: 1, missingMandatory: [] })).toMatchObject({ parentShapeId: 'c', childCount: 2, parentEntityId: 9 })
    expect('parentShapeId' in fromView({ id: 's', entity: { id: 1, name: 'A', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, parentShapeId: null, x: 0, y: 0, width: 1, height: 1, missingMandatory: [] })).toBe(false)
  })

  describe('the request of the save', () => {
    it('carries the container, the hierarchy lines and the parent changes', () => {
      const shapes = [box('a', 0, 0, 300, 200, { entityId: 5, parentEntityId: null }), box('b', 10, 10, 90, 30, { entityId: 6, parentEntityId: 5, parentShapeId: 'a' }), box('c', 400, 0, 90, 30, { entityId: 7, parentEntityId: 5 })]
      const request = writeRequest(shapes, {}, [], [{ id: 'l1', parentShapeId: 'a', childShapeId: 'c' }], { 6: { version: 3, parent: null }, 7: { version: 1, parent: { entityId: 5 } } })
      expect(request.shapes.map((shape) => shape.parentShapeId)).toEqual([undefined, 'a', undefined])
      expect(request.hierarchyLines).toEqual([{ id: 'l1', parentShapeId: 'a', childShapeId: 'c' }])
      expect(request.parentChanges).toEqual([{ entityId: 6, expectedVersion: 3 }, { entityId: 7, expectedVersion: 1, parentEntityId: 5 }])
    })

    it('names a new entity as the parent by its key and drops a parent that is not saved', () => {
      const fresh = box('n1', 0, 0, 300, 200, { entityId: undefined, name: 'Shop', newEntity: { key: 'k1', metatypeId: METATYPE_COMPONENT } })
      const cart = box('n2', 10, 10, 90, 30, { entityId: undefined, name: 'Cart', parentShapeId: 'n1', newEntity: { key: 'k2', metatypeId: METATYPE_COMPONENT, parentNewEntityKey: 'k1' } })
      const orphan = box('n3', 400, 0, 90, 30, { entityId: undefined, name: 'Orphan', newEntity: { key: 'k3', metatypeId: METATYPE_COMPONENT, parentNewEntityKey: 'gone' } })
      const request = writeRequest([fresh, cart, orphan], {}, [], [], {})
      expect(request.newEntities?.map((entity) => [entity.name, entity.parentEntityId, entity.parentNewEntityKey])).toEqual([
        ['Shop', undefined, undefined],
        ['Cart', undefined, 'k1'],
        ['Orphan', undefined, undefined],
      ])
      const existing = box('e', 400, 400, 10, 10, { entityId: 8 })
      const target = writeRequest([existing, fresh], {}, [], [], { 8: { version: 1, parent: { key: 'k1' } }, 9: { version: 1, parent: { key: 'zzz' } } })
      expect(target.parentChanges).toEqual([{ entityId: 8, expectedVersion: 1, parentNewEntityKey: 'k1' }])
      // a change of an entity that no shape shows, or to a new entity that is not saved, is not sent
      expect(writeRequest([existing], {}, [], [], { 8: { version: 1, parent: { key: 'zzz' } } }).parentChanges).toBeUndefined()
      expect(writeRequest([existing], {}, [], [], { 99: { version: 1, parent: null } }).parentChanges).toBeUndefined()
    })

    it('leaves out a line of a shape that is not saved and the container of a shape that is not saved', () => {
      const named = box('a', 0, 0, 10, 10, { entityId: 5 })
      const unnamed = box('u', 0, 0, 10, 10, { entityId: undefined, name: '', newEntity: { key: 'k', metatypeId: METATYPE_COMPONENT }, editing: true })
      const nested = box('b', 1, 1, 5, 5, { entityId: 6, parentShapeId: 'u' })
      const request = writeRequest([unnamed, named, nested], {}, [], [{ id: 'l', parentShapeId: 'u', childShapeId: 'a' }], {})
      expect(request.hierarchyLines).toBeUndefined()
      expect(request.shapes.find((shape) => shape.id === 'b')?.parentShapeId).toBeUndefined()
    })

    it('keeps the limits of the contract in a model of 2000 shapes', () => {
      expect(CANVAS_LIMITS.hierarchyLines).toBe(4000)
      expect(CANVAS_LIMITS.parentChanges).toBe(500)
      expect(CANVAS_LIMITS.sizeMax).toBe(10_000)
    })
  })

  describe('unsaved changes', () => {
    const shapes = [box('a', 0, 0, 100, 100), box('b', 10, 10, 20, 20)]

    it('sees a new container, a new line and a parent change', () => {
      const nested = shapes.map((shape) => (shape.id === 'b' ? { ...shape, parentShapeId: 'a' } : shape))
      expect(hasUnsavedChanges(shapes, shapes)).toBe(false)
      expect(hasUnsavedChanges(nested, shapes)).toBe(true)
      expect(hasUnsavedChanges(shapes, shapes, {}, [], [], [{ id: 'l', parentShapeId: 'a', childShapeId: 'b' }], [])).toBe(true)
      const line = { id: 'l', parentShapeId: 'a', childShapeId: 'b' }
      expect(hasUnsavedChanges(shapes, shapes, {}, [], [], [line], [line])).toBe(false)
      expect(hasUnsavedChanges(shapes, shapes, {}, [], [], [], [line])).toBe(true)
      expect(hasUnsavedChanges(shapes, shapes, {}, [], [], [], [], { 1: { version: 1, parent: null } })).toBe(true)
    })
  })

  describe('rejected nesting', () => {
    it('marks the shape of a rejected container and the shape of an entity whose parent change was rejected', () => {
      const shapes = [box('a', 0, 0, 100, 100, { entityId: 5 }), box('b', 10, 10, 20, 20, { entityId: 6 })]
      const request = writeRequest(shapes, {}, [], [{ id: 'l', parentShapeId: 'a', childShapeId: 'b' }], { 6: { version: 1, parent: { entityId: 5 } } })
      const container = rejectedShapes([{ field: 'shapes[1].parentShapeId', message: 'outside' }], request, shapes)
      expect([...container.shapes]).toEqual([['b', 'outside']])
      const line = rejectedShapes([{ field: 'hierarchyLines[0]', message: 'no hierarchy' }], request, shapes)
      expect([...line.shapes]).toEqual([['b', 'no hierarchy']])
      expect([...line.lines]).toEqual([['l', 'no hierarchy']])
      const change = rejectedShapes([{ field: 'parentChanges[0].parentEntityId', message: 'cycle' }], request, shapes)
      expect([...change.shapes]).toEqual([['b', 'cycle']])
    })
  })
})
