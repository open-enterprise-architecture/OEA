import { describe, expect, it } from 'vitest'
import { BPMN_TYPES, bpmnEdge, bpmnShape, bpmnView } from '../../test/bpmn'
import { bpmnFlowWarnings, canContain, containerAt, fromView, isBpmnView, type CanvasShape } from './canvasModel'
import { fromViewEdge } from './edgeModel'

// spec diagram/bpmn-process-diagrams "Pools, lanes and sub-processes as containers", "Warnings of the pool rules"
// (design D5, D6 of us-074-bpmn-process-diagrams)

// the process of the stories: two pools, the first with two lanes, the second lane with a nested lane
const SHAPES = [
  bpmnShape('customer', 'Customer', 'pool', 0, 0, 700, 400),
  bpmnShape('sales', 'Sales', 'lane', 30, 10, 660, 180, 'customer'),
  bpmnShape('office', 'Back office', 'lane', 30, 200, 660, 190, 'customer'),
  bpmnShape('team', 'Team', 'lane', 60, 210, 620, 170, 'office'),
  bpmnShape('check', 'Check order', 'userTask', 100, 40, 90, 60, 'sales'),
  bpmnShape('ship', 'Ship order', 'task', 300, 40, 90, 60, 'sales'),
  bpmnShape('pack', 'Pack order', 'task', 100, 240, 90, 60, 'team'),
  bpmnShape('supplier', 'Supplier', 'pool', 0, 500, 700, 200),
  bpmnShape('deliver', 'Deliver', 'task', 100, 540, 90, 60, 'supplier'),
  bpmnShape('loose', 'Loose task', 'task', 800, 40, 90, 60),
  bpmnShape('other', 'Other loose task', 'task', 800, 200, 90, 60),
]

const view = bpmnView(SHAPES)
const shapes = view.shapes.map(fromView)

function warnings(type: 'sequenceFlow' | 'messageFlow', source: string, target: string) {
  const edge = fromViewEdge(bpmnEdge('e', type, source, target))
  return bpmnFlowWarnings(shapes, [edge], view.viewpoint.effectiveMappings).get('e')
}

describe('bpmnFlowWarnings', () => {
  it.each([
    ['in one lane', 'check', 'ship'],
    ['across two lanes of a pool', 'check', 'pack'],
    ['across a nested lane', 'pack', 'check'],
    ['from a pool to its task', 'customer', 'check'],
    ['from a lane to its pool', 'sales', 'customer'],
    ['between two shapes without a pool', 'loose', 'other'],
  ])('gives a sequence flow %s no warning', (_name, source, target) => {
    expect(warnings('sequenceFlow', source, target)).toBeUndefined()
  })

  it.each([
    ['across two pools', 'check', 'deliver'],
    ['across two pools, from a nested lane', 'pack', 'deliver'],
    ['from one pool to another', 'customer', 'supplier'],
    ['from a pool to a shape without a pool', 'check', 'loose'],
    ['from a shape without a pool to a pool', 'loose', 'deliver'],
  ])('warns a sequence flow %s', (_name, source, target) => {
    expect(warnings('sequenceFlow', source, target)).toBe('sequence-crosses-pool')
  })

  it.each([
    ['between two pools', 'customer', 'supplier'],
    ['from a pool to a task of another pool', 'customer', 'deliver'],
    ['between tasks of two pools', 'check', 'deliver'],
    ['from a task to a shape without a pool', 'check', 'loose'],
    ['between shapes without a pool', 'loose', 'other'],
  ])('gives a message flow %s no warning', (_name, source, target) => {
    expect(warnings('messageFlow', source, target)).toBeUndefined()
  })

  it.each([
    ['between tasks of one pool', 'check', 'pack'],
    ['between tasks of one lane', 'check', 'ship'],
    ['from a pool to its own task', 'customer', 'check'],
  ])('warns a message flow %s', (_name, source, target) => {
    expect(warnings('messageFlow', source, target)).toBe('message-within-pool')
  })

  it('counts the pool itself as the pool of an end', () => {
    expect(warnings('sequenceFlow', 'customer', 'customer')).toBeUndefined()
    expect(warnings('sequenceFlow', 'customer', 'check')).toBeUndefined()
  })

  it('checks neither associations nor other connection types', () => {
    const association = fromViewEdge(bpmnEdge('a', 'association', 'check', 'deliver'))
    const calls = fromViewEdge(bpmnEdge('c', 'calls', 'check', 'deliver'))
    expect(bpmnFlowWarnings(shapes, [association, calls], view.viewpoint.effectiveMappings).size).toBe(0)
  })

  it('ignores flows of conflicts and flows with an end that is not on the canvas', () => {
    const conflict = { ...fromViewEdge(bpmnEdge('x', 'sequenceFlow', 'check', 'deliver')), conflict: true }
    const hidden = fromViewEdge(bpmnEdge('y', 'sequenceFlow', 'check', 'gone'))
    expect(bpmnFlowWarnings(shapes, [conflict, hidden], view.viewpoint.effectiveMappings).size).toBe(0)
    const conflicting = shapes.map((shape) => (shape.id === 'deliver' ? { ...shape, conflict: true } : shape))
    const edge = fromViewEdge(bpmnEdge('z', 'sequenceFlow', 'check', 'deliver'))
    expect(bpmnFlowWarnings(conflicting, [edge], view.viewpoint.effectiveMappings).size).toBe(0)
  })

  it('drops the warning when the target is moved into the pool of the source', () => {
    const edge = fromViewEdge(bpmnEdge('e', 'sequenceFlow', 'check', 'deliver'))
    expect(bpmnFlowWarnings(shapes, [edge], view.viewpoint.effectiveMappings).get('e')).toBe('sequence-crosses-pool')
    const moved = shapes.map((shape) => (shape.id === 'deliver' ? { ...shape, parentShapeId: 'sales' } : shape))
    expect(bpmnFlowWarnings(moved, [edge], view.viewpoint.effectiveMappings).size).toBe(0)
  })

  it('does not loop on a cycle of containers', () => {
    const cyclic: CanvasShape[] = shapes.map((shape) => (shape.id === 'sales' ? { ...shape, parentShapeId: 'team' } : shape))
    const edge = fromViewEdge(bpmnEdge('e', 'sequenceFlow', 'check', 'deliver'))
    expect(() => bpmnFlowWarnings(cyclic, [edge], view.viewpoint.effectiveMappings)).not.toThrow()
  })
})

describe('canContain and containerAt', () => {
  const take = canContain(view)
  const byId = (id: string) => shapes.find((shape) => shape.id === id)!

  it.each([
    ['customer', true],
    ['sales', true],
    ['team', true],
    ['check', false],
    ['ship', false],
  ])('in a bpmn2 diagram %s takes nested shapes: %s', (id, container) => {
    expect(take(byId(id))).toBe(container)
  })

  it('lets a sub-process, but not a group or a text annotation, take nested shapes', () => {
    const extra = bpmnView([bpmnShape('sub', 'Pick', 'subProcess', 0, 0, 270, 150), bpmnShape('g', 'Group', 'group', 0, 0, 180, 120), bpmnShape('t', 'Note', 'textAnnotation', 0, 0, 90, 45)])
    const check = canContain(extra)
    const all = extra.shapes.map(fromView)
    expect(all.map((shape) => check(shape))).toEqual([true, false, false])
  })

  it('lets every shape take nested shapes in the other notations', () => {
    for (const notation of ['archimate3', 'generic', 'uml'] as const) {
      const other = bpmnView(SHAPES, [], notation)
      expect(isBpmnView(other)).toBe(false)
      expect(other.shapes.map(fromView).every(canContain(other))).toBe(true)
    }
    expect(isBpmnView(view)).toBe(true)
  })

  it('falls through a task to the lane below it', () => {
    const point = { x: 145, y: 70 }
    expect(containerAt(shapes, point, new Set()) ?.id).toBe('check')
    expect(containerAt(shapes, point, new Set(), take)?.id).toBe('sales')
  })

  it('finds no container when only a task, a group or an annotation lies there', () => {
    expect(containerAt(shapes, { x: 845, y: 70 }, new Set(), take)).toBeUndefined()
    const loose = bpmnView([bpmnShape('g', 'Group', 'group', 0, 0, 180, 120), bpmnShape('t', 'Note', 'textAnnotation', 10, 10, 90, 45)])
    expect(containerAt(loose.shapes.map(fromView), { x: 50, y: 30 }, new Set(), canContain(loose))).toBeUndefined()
  })

  it('finds the container on the boundary of its box and not outside it', () => {
    expect(containerAt(shapes, { x: 700, y: 400 }, new Set(), take)?.id).toBe('customer')
    expect(containerAt(shapes, { x: 700.01, y: 400 }, new Set(), take)).toBeUndefined()
  })

  it('keeps the unrestricted rule without a predicate', () => {
    expect(containerAt(shapes, { x: 845, y: 70 }, new Set())?.id).toBe('loose')
    expect(BPMN_TYPES.pool).toBeDefined()
  })
})
