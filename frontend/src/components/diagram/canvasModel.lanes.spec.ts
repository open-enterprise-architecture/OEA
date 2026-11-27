import { describe, expect, it } from 'vitest'
import { BPMN_TYPES, bpmnViewpoint } from '../../test/bpmn'
import {
  hiddenShapes,
  laneDrop,
  layoutLanes,
  lies,
  placeLane,
  resizeAcross,
  resizeAlong,
  setOrientation,
  stackLanes,
  writeRequest,
  type CanvasShape,
} from './canvasModel'

// the lane layout of the canvas (specs diagram/bpmn-pools-and-lanes; us-074-bpmn-pools-and-lanes D1, D5): the rules are written
// for a horizontal pool and checked for a vertical one by mirroring the same layout on the diagonal

const MAPPINGS = bpmnViewpoint().effectiveMappings
type Type = keyof typeof BPMN_TYPES
type Spec = [id: string, type: Type, x: number, y: number, width: number, height: number, parent?: string]

function shapeOf([id, type, x, y, width, height, parent]: Spec, vertical: boolean): CanvasShape {
  const base: CanvasShape = { id, name: id, entityId: 1, metatypeId: BPMN_TYPES[type], x, y, width, height, missingMandatory: [], ...(parent ? { parentShapeId: parent } : {}) }
  if (!vertical) return base
  // the plane is mirrored on the diagonal: x and y, width and height exchanged
  return { ...base, x: y, y: x, width: height, height: width, ...(type === 'pool' ? { orientation: 'vertical' as const } : {}) }
}

function world(vertical: boolean, specs: Spec[]): CanvasShape[] {
  return specs.map((spec) => shapeOf(spec, vertical))
}

/** a shape as a horizontal pool would hold it */
function at(list: CanvasShape[], id: string, vertical: boolean) {
  const found = list.find((shape) => shape.id === id)!
  return vertical ? { x: found.y, y: found.x, width: found.height, height: found.width } : { x: found.x, y: found.y, width: found.width, height: found.height }
}

// a pool of 600 x 200 with the lanes "L1" and "L2" of 100 pt, a task in each
const BASIC: Spec[] = [
  ['P', 'pool', 0, 0, 600, 200],
  ['L1', 'lane', 30, 0, 570, 100, 'P'],
  ['L2', 'lane', 30, 100, 570, 100, 'P'],
  ['T1', 'task', 100, 20, 90, 60, 'L1'],
  ['T2', 'task', 100, 120, 90, 60, 'L2'],
]

describe.each([false, true])('lane layout (vertical: %s)', (vertical) => {
  const build = (specs: Spec[] = BASIC) => world(vertical, specs)
  const read = (list: CanvasShape[], id: string) => at(list, id, vertical)

  describe('stacking', () => {
    it('stacks lanes that lie anywhere in the order of their position, fills the width and sums the heights', () => {
      const list = build([
        ['P', 'pool', 0, 0, 700, 400],
        ['L2', 'lane', 30, 200, 660, 190, 'P'],
        ['L1', 'lane', 30, 10, 660, 180, 'P'],
        ['T1', 'task', 100, 40, 90, 60, 'L1'],
        ['T2', 'task', 100, 240, 90, 60, 'L2'],
      ])
      const stacked = stackLanes(list, MAPPINGS)
      expect(read(stacked, 'L1')).toEqual({ x: 30, y: 0, width: 670, height: 180 })
      expect(read(stacked, 'L2')).toEqual({ x: 30, y: 180, width: 670, height: 190 })
      expect(read(stacked, 'P')).toEqual({ x: 0, y: 0, width: 700, height: 370 })
      // the content moves with its lane and keeps its place across
      expect(read(stacked, 'T1')).toMatchObject({ x: 100, y: 30 })
      expect(read(stacked, 'T2')).toMatchObject({ x: 100, y: 220 })
    })

    it('is stable: stacking stacked lanes changes nothing', () => {
      const once = layoutLanes(build(), MAPPINGS)
      expect(layoutLanes(once, MAPPINGS)).toEqual(once)
      expect(read(once, 'P')).toEqual({ x: 0, y: 0, width: 600, height: 200 })
    })

    it('returns shapes without lanes, and shapes of a notation without pools and lanes, as they are', () => {
      const list = build([['T', 'task', 5, 5, 90, 60]])
      expect(stackLanes(list, MAPPINGS)).toBe(list)
      expect(stackLanes(build(), [])).toEqual(build())
    })

    it('stacks nested lanes inside their lane, and the lane is as high as both together', () => {
      const list = layoutLanes(
        build([
          ['P', 'pool', 0, 0, 600, 200],
          ['L1', 'lane', 30, 0, 570, 200, 'P'],
          ['A', 'lane', 60, 0, 540, 100, 'L1'],
          ['B', 'lane', 60, 100, 540, 120, 'L1'],
        ]),
        MAPPINGS,
      )
      expect(read(list, 'A')).toEqual({ x: 60, y: 0, width: 540, height: 100 })
      expect(read(list, 'B')).toEqual({ x: 60, y: 100, width: 540, height: 120 })
      expect(read(list, 'L1').height).toBe(220)
      expect(read(list, 'P').height).toBe(220)
    })

    it('a task directly in the pool keeps its place and the last lane grows to keep it in the pool', () => {
      const list = layoutLanes(build([...BASIC, ['D', 'task', 10, 190, 90, 60, 'P']]), MAPPINGS)
      expect(read(list, 'D')).toMatchObject({ x: 10, y: 190 })
      expect(read(list, 'L2').height).toBe(150)
      expect(read(list, 'P').height).toBe(250)
    })

    it('a collapsed sub-process in a lane keeps the lane as high as its stored size', () => {
      const list = layoutLanes(
        build([
          ['P', 'pool', 0, 0, 600, 200],
          ['L1', 'lane', 30, 0, 570, 100, 'P'],
          ['S', 'subProcess', 60, 10, 270, 150, 'L1'],
        ]).map((shape) => (shape.id === 'S' ? { ...shape, collapsed: true } : shape)),
        MAPPINGS,
      )
      expect(read(list, 'L1').height).toBe(160)
      expect(lies(list.find((shape) => shape.id === 'L1')!, list.find((shape) => shape.id === 'S')!)).toBe(true)
    })

    it('a lane removed closes the gap, and a pool whose last lane is removed keeps its height', () => {
      const without = layoutLanes(build().filter((shape) => shape.id !== 'L1' && shape.id !== 'T1'), MAPPINGS)
      expect(read(without, 'L2')).toMatchObject({ y: 0, height: 100 })
      expect(read(without, 'P').height).toBe(100)
      const none = layoutLanes(without.filter((shape) => shape.id !== 'L2' && shape.id !== 'T2'), MAPPINGS)
      expect(read(none, 'P').height).toBe(100)
    })
  })

  describe('heights', () => {
    it('a higher lane shifts the lanes below and their content, and the pool follows', () => {
      const list = resizeAlong(layoutLanes(build(), MAPPINGS), 'L1', 140, MAPPINGS)
      expect(read(list, 'L1').height).toBe(140)
      expect(read(list, 'L2')).toMatchObject({ y: 140, height: 100 })
      expect(read(list, 'T2')).toMatchObject({ y: 160 })
      expect(read(list, 'P').height).toBe(240)
    })

    it.each([
      [30, 30],
      [29, 30],
      [31, 31],
    ])('keeps a lane at least 30 pt high: %s becomes %s', (wanted, kept) => {
      const list = resizeAlong(layoutLanes(build([['P', 'pool', 0, 0, 600, 200], ['L1', 'lane', 30, 0, 570, 200, 'P']]), MAPPINGS), 'L1', wanted, MAPPINGS)
      expect(read(list, 'L1').height).toBe(kept)
    })

    it.each([
      [80, 80],
      [79, 80],
    ])('keeps a lane as high as its content: %s becomes %s', (wanted, kept) => {
      // the task reaches 80 pt below the top of the lane
      const list = resizeAlong(
        layoutLanes(build([['P', 'pool', 0, 0, 600, 200], ['L1', 'lane', 30, 0, 570, 200, 'P'], ['T', 'task', 100, 20, 90, 60, 'L1']]), MAPPINGS),
        'L1',
        wanted,
        MAPPINGS,
      )
      expect(read(list, 'L1').height).toBe(kept)
    })

    it.each([
      [5000, 5000, 10000],
      [5000.01, 5000, 10000],
    ])('keeps the pool at most 10 000 pt: a second lane of %s becomes %s', (wanted, kept, pool) => {
      const start = layoutLanes(build([['P', 'pool', 0, 0, 600, 5000], ['L1', 'lane', 30, 0, 570, 5000, 'P'], ['L2', 'lane', 30, 5000, 570, 100, 'P']]), MAPPINGS)
      const list = resizeAlong(start, 'L2', wanted, MAPPINGS)
      expect(read(list, 'L2').height).toBe(kept)
      expect(read(list, 'P').height).toBe(pool)
    })

    it('a lane that grows with its content, moved below, above or left, still lies in every container', () => {
      const start = layoutLanes(build(), MAPPINGS)
      const moves: [number, number][] = [
        [0, 80],
        [0, -50],
        [-90, 0],
      ]
      for (const [dx, dy] of moves) {
        const task = start.find((shape) => shape.id === 'T1')!
        // a move in x and y of the plane; in a vertical pool the plane is mirrored
        const next = vertical ? { x: task.x + dy, y: task.y + dx } : { x: task.x + dx, y: task.y + dy }
        const list = layoutLanes(start.map((shape) => (shape.id === 'T1' ? { ...shape, ...next } : shape)), MAPPINGS)
        const byId = new Map(list.map((shape) => [shape.id, shape]))
        for (const shape of list) {
          if (shape.parentShapeId) expect(lies(byId.get(shape.parentShapeId)!, shape), `${shape.id} in ${shape.parentShapeId} after ${dx},${dy}`).toBe(true)
        }
      }
    })
  })

  describe('widths', () => {
    it('a pool 100 pt wider makes every lane, nested ones included, 100 pt wider', () => {
      const start = layoutLanes(
        build([
          ['P', 'pool', 0, 0, 600, 200],
          ['L1', 'lane', 30, 0, 570, 200, 'P'],
          ['A', 'lane', 60, 0, 540, 200, 'L1'],
        ]),
        MAPPINGS,
      )
      const list = resizeAcross(start, 'P', { start: 0, extent: 700 }, MAPPINGS)
      expect(read(list, 'P').width).toBe(700)
      expect(read(list, 'L1')).toMatchObject({ x: 30, width: 670 })
      expect(read(list, 'A')).toMatchObject({ x: 60, width: 640 })
    })

    it.each([
      ['one level of lanes', 1, 31],
      ['lanes nested to the depth limit (the pool and 9 lane levels)', 9, 271],
    ])('keeps an empty pool with %s at least %s wide: one point less is clamped', (_name, levels, least) => {
      const specs: Spec[] = [['P', 'pool', 0, 0, 600, 200]]
      for (let level = 1; level <= levels; level += 1) {
        specs.push([`L${level}`, 'lane', level * 30, 0, 600 - level * 30, 200, level === 1 ? 'P' : `L${level - 1}`])
      }
      const start = layoutLanes(build(specs), MAPPINGS)
      const exact = resizeAcross(start, 'P', { start: 0, extent: least }, MAPPINGS)
      expect(read(exact, 'P').width).toBe(least)
      expect(read(exact, `L${levels}`).width).toBe(1)
      const less = resizeAcross(start, 'P', { start: 0, extent: least - 1 }, MAPPINGS)
      expect(read(less, 'P').width).toBe(least)
    })

    it('keeps the edges clear of the content, which keeps its place', () => {
      const start = layoutLanes(build(), MAPPINGS)
      // T1 spans 100 to 190 and sits right of one label band: the pool stays at least 190 wide
      const exact = resizeAcross(start, 'P', { start: 0, extent: 190 }, MAPPINGS)
      expect(read(exact, 'P').width).toBe(190)
      const less = resizeAcross(start, 'P', { start: 0, extent: 189 }, MAPPINGS)
      expect(read(less, 'P').width).toBe(190)
      expect(read(less, 'T1')).toMatchObject({ x: 100 })
    })
  })

  describe('drops', () => {
    const canContain = () => true
    const dropped: Spec[] = [['N', 'lane', 30, 300, 570, 100]]
    const drop = (x: number, y: number, list: CanvasShape[]) => {
      // a point in horizontal terms: x across the lanes, y along them
      const point = vertical ? { x: y, y: x } : { x, y }
      return laneDrop(list, 'N', point, MAPPINGS, canContain)
    }

    it.each([
      ['the upper half of the first lane', 200, 20, 'P', 0],
      ['the lower half of the first lane', 200, 80, 'P', 1],
      ['the upper half of the second lane', 200, 120, 'P', 1],
      ['the lower half of the second lane', 200, 180, 'P', 2],
      ['the label band of the pool', 10, 140, 'P', 1],
    ])('a lane dropped on %s lies in %s at the place %s', (_where, x, y, container, index) => {
      const list = [...layoutLanes(build(), MAPPINGS), ...build(dropped)]
      const found = drop(x, y, list)
      expect(found.container?.id).toBe(container)
      expect(found.index).toBe(index)
    })

    it('a lane dropped on the label band of a lane is nested there', () => {
      const list = [...layoutLanes(build(), MAPPINGS), ...build(dropped)]
      const found = drop(40, 50, list)
      expect(found.container?.id).toBe('L1')
      expect(found.index).toBe(0)
    })

    it('puts a new lane below the lane: it takes the width of the lane and the pool grows by its height', () => {
      const list = [...layoutLanes(build(), MAPPINGS), ...build(dropped)]
      const placed = layoutLanes(placeLane(list, 'N', 'P', 1, MAPPINGS), MAPPINGS)
      expect(read(placed, 'N')).toEqual({ x: 30, y: 100, width: 570, height: 100 })
      expect(read(placed, 'L2')).toMatchObject({ y: 200 })
      expect(read(placed, 'T2')).toMatchObject({ y: 220 })
      expect(read(placed, 'P').height).toBe(300)
    })

    it('the first lane of a pool takes the height of the pool, and the pool keeps its height', () => {
      const empty = build([['P', 'pool', 0, 0, 600, 200], ...dropped])
      const placed = layoutLanes(placeLane(empty, 'N', 'P', 0, MAPPINGS), MAPPINGS)
      expect(read(placed, 'N')).toEqual({ x: 30, y: 0, width: 570, height: 200 })
      expect(read(placed, 'P').height).toBe(200)
    })

    it('reordering keeps the content with each lane', () => {
      const start = layoutLanes(build(), MAPPINGS)
      // L2 goes before L1
      const placed = layoutLanes(placeLane(start, 'L2', 'P', 0, MAPPINGS), MAPPINGS)
      expect(read(placed, 'L2')).toMatchObject({ y: 0 })
      expect(read(placed, 'L1')).toMatchObject({ y: 100 })
      expect(read(placed, 'T2')).toMatchObject({ y: 20 })
      expect(read(placed, 'T1')).toMatchObject({ y: 120 })
      expect(placed.find((shape) => shape.id === 'L2')!.parentShapeId).toBe('P')
    })

    it('a lane dragged out of its pool restacks both: the pool closes the gap and the lane keeps its size', () => {
      const start = layoutLanes(build(), MAPPINGS)
      const out = start.map((shape) => (shape.id === 'L1' ? { ...shape, parentShapeId: undefined } : shape))
      const list = layoutLanes(out, MAPPINGS)
      expect(read(list, 'L2')).toMatchObject({ y: 0 })
      expect(read(list, 'P').height).toBe(100)
      expect(read(list, 'L1').height).toBe(100)
    })
  })

  describe('switching the orientation', () => {
    it('keeps the order, the extent of each lane and the place of the content, and switching back restores the layout', () => {
      const start = layoutLanes(
        build([
          ['P', 'pool', 0, 0, 600, 200],
          ['L1', 'lane', 30, 0, 570, 100, 'P'],
          ['L2', 'lane', 30, 100, 570, 120, 'P'],
          ['T1', 'task', 100, 20, 60, 60, 'L1'],
          ['T2', 'task', 100, 120, 60, 60, 'L2'],
        ]),
        MAPPINGS,
      )
      const switched = setOrientation(start, 'P', !vertical, MAPPINGS)
      // seen the other way round, L1 comes first, each lane keeps its extent along the lanes, and the task its offset
      const other = (id: string) => at(switched, id, !vertical)
      expect(other('L1')).toMatchObject({ y: 0, height: 100 })
      expect(other('L2')).toMatchObject({ y: 100, height: 120 })
      expect(other('T1')).toMatchObject({ x: 100, y: 20 })
      // the content keeps its size
      const task = switched.find((shape) => shape.id === 'T1')!
      expect(read(switched, 'T1')).toBeDefined()
      expect(vertical ? [task.height, task.width] : [task.width, task.height]).toEqual([60, 60])
      expect(switched.find((shape) => shape.id === 'P')!.orientation).toBe(vertical ? undefined : 'vertical')
      const back = setOrientation(switched, 'P', vertical, MAPPINGS)
      expect(back.map((shape) => ({ id: shape.id, x: shape.x, y: shape.y, width: shape.width, height: shape.height }))).toEqual(
        start.map((shape) => ({ id: shape.id, x: shape.x, y: shape.y, width: shape.width, height: shape.height })),
      )
    })
  })
})

describe('vertical pools', () => {
  const vertical = world(true, BASIC)
  const empty = world(true, [
    ['P', 'pool', 0, 0, 600, 200],
    ['L1', 'lane', 30, 0, 570, 100, 'P'],
    ['L2', 'lane', 30, 100, 570, 100, 'P'],
  ])

  it('puts the lanes side by side below a band at the top, the widths adding up', () => {
    const list = layoutLanes(vertical, MAPPINGS)
    const pool = list.find((shape) => shape.id === 'P')!
    const first = list.find((shape) => shape.id === 'L1')!
    const second = list.find((shape) => shape.id === 'L2')!
    expect(pool.orientation).toBe('vertical')
    expect([first.x, first.y, first.width, first.height]).toEqual([0, 30, 100, 570])
    expect([second.x, second.y]).toEqual([100, 30])
    expect(pool.width).toBe(200)
  })

  it.each([
    [30, 30],
    [29, 30],
  ])('keeps a lane at least 30 pt wide: %s becomes %s', (wanted, kept) => {
    const list = resizeAlong(layoutLanes(empty, MAPPINGS), 'L1', wanted, MAPPINGS)
    expect(list.find((shape) => shape.id === 'L1')!.width).toBe(kept)
  })

  it('a lane wider shifts the lanes to its right and their content, and the pool follows', () => {
    const list = resizeAlong(layoutLanes(vertical, MAPPINGS), 'L1', 140, MAPPINGS)
    expect(list.find((shape) => shape.id === 'L2')!.x).toBe(140)
    expect(list.find((shape) => shape.id === 'T2')!.x).toBe(160)
    expect(list.find((shape) => shape.id === 'P')!.width).toBe(240)
  })
})

describe('the save and the load', () => {
  it('sends the stacked geometry and the orientation of the pool', () => {
    const list = layoutLanes(world(true, BASIC), MAPPINGS)
    const request = writeRequest(list.map((shape) => ({ ...shape, newEntity: undefined })))
    const pool = request.shapes.find((shape) => shape.id === 'P')!
    expect(pool.orientation).toBe('vertical')
    expect(request.shapes.find((shape) => shape.id === 'L1')).toMatchObject({ x: 0, y: 30, width: 100, height: 570 })
    expect(request.shapes.find((shape) => shape.id === 'T1')!.orientation).toBe('horizontal')
  })

  it('hides what a collapsed sub-process holds, at any depth', () => {
    const list = [
      { ...shapeOf(['S', 'subProcess', 0, 0, 270, 150], false), collapsed: true },
      shapeOf(['A', 'task', 10, 10, 90, 60, 'S'], false),
      shapeOf(['B', 'subProcess', 100, 10, 90, 60, 'S'], false),
      shapeOf(['C', 'task', 105, 15, 40, 20, 'B'], false),
      shapeOf(['D', 'task', 300, 10, 90, 60], false),
    ]
    expect([...hiddenShapes(list)].sort()).toEqual(['A', 'B', 'C'])
  })
})
