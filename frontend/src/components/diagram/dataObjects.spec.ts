import { describe, expect, it } from 'vitest'
import { BPMN_TYPES, bpmnEdge, bpmnShape, bpmnView } from '../../test/bpmn'
import { bpmnFlowWarnings, fromView, type CanvasShape } from './canvasModel'
import { fromViewEdge } from './edgeModel'
import {
  AFFECTED_ATTRIBUTES_ID,
  addTag,
  COLLECTION_ID,
  collectionOf,
  dataRuleAllows,
  isDataKey,
  joinTags,
  MAX_TAG_LENGTH,
  MAX_TAGS,
  normalizeTags,
  splitTags,
  withoutParent,
} from './dataObjects'

// specs diagram/bpmn-data-objects (US-083, REQ-081; us-083-bpmn-data-objects D2, D5): the data keys, the tags of
// "Affected attributes", the collection marker and the direction rule of a drawn line

const tag = (length: number, letter = 'a') => letter.repeat(length)
const tags = (count: number) => Array.from({ length: count }, (_, index) => `tag${index}`)

describe('data keys', () => {
  it.each([
    ['bpmn:DataObject', true],
    ['bpmn:DataStore', true],
    ['bpmn:Task', false],
    ['bpmn:Association', false],
    ['generic:Box', false],
    [undefined, false],
  ])('%s is a data shape: %s', (key, expected) => {
    expect(isDataKey(key)).toBe(expected)
  })
})

describe('the tags of "Affected attributes"', () => {
  it.each([
    [50, 50],
    [51, 50],
  ])('takes %i tags and keeps %i: at most 50, the 51st is refused', (entered, kept) => {
    const outcome = normalizeTags(tags(entered))
    expect(outcome.tags).toHaveLength(kept)
    expect(outcome.refused).toBe(entered > MAX_TAGS ? 'count' : undefined)
  })

  it('refuses a 51st tag that is added one by one and keeps the 50', () => {
    expect(addTag(tags(49), 'last')).toEqual({ tags: [...tags(49), 'last'] })
    expect(addTag(tags(50), 'one more')).toEqual({ tags: tags(50), refused: 'count' })
  })

  it.each([
    [1, true],
    [100, true],
    [101, false],
  ])('takes a tag of %i characters: %s', (length, accepted) => {
    const outcome = addTag([], tag(length))
    expect(outcome.tags).toEqual(accepted ? [tag(length)] : [])
    expect(outcome.refused).toBe(accepted ? undefined : 'length')
    expect(MAX_TAG_LENGTH).toBe(100)
  })

  it('counts a tag of 100 characters outside the basic plane as 100 characters', () => {
    expect(addTag([], '😀'.repeat(100)).tags).toHaveLength(1)
    expect(addTag([], '😀'.repeat(101)).refused).toBe('length')
  })

  it.each(['', ' ', '   ', '\t'])('refuses the empty tag %j', (entered) => {
    expect(addTag(['a'], entered)).toEqual({ tags: ['a'], refused: 'empty' })
  })

  it('trims a tag and drops a duplicate after trimming without a complaint', () => {
    expect(addTag(['status'], '  reviewedBy ')).toEqual({ tags: ['status', 'reviewedBy'] })
    expect(addTag(['status'], ' status ')).toEqual({ tags: ['status'] })
    expect(normalizeTags([' a ', 'a', 'b', ' b']).tags).toEqual(['a', 'b'])
    expect(normalizeTags([' a ', 'a']).refused).toBeUndefined()
  })

  it('keeps the case: only an exact match is a duplicate', () => {
    expect(normalizeTags(['Status', 'status']).tags).toEqual(['Status', 'status'])
  })

  it('stores the tags joined by line feeds and reads them back', () => {
    expect(joinTags(['status', 'reviewedBy'])).toBe('status\nreviewedBy')
    expect(splitTags('status\nreviewedBy')).toEqual(['status', 'reviewedBy'])
    expect(splitTags('status\n\n reviewedBy \nstatus')).toEqual(['status', 'reviewedBy'])
    expect(splitTags(undefined)).toEqual([])
    expect(splitTags('')).toEqual([])
  })

  it('stores 50 tags of 100 characters in at most 5049 characters, as the server accepts them', () => {
    const value = joinTags(Array.from({ length: 50 }, (_, index) => `${index}`.padStart(2, '0') + tag(98)))
    expect(value).toHaveLength(50 * 100 + 49)
    expect(splitTags(value)).toHaveLength(50)
  })
})

describe('the collection marker', () => {
  const object = fromView(bpmnShape('order', 'Order', 'dataObject', 0, 0, 36, 50))
  const marked: CanvasShape = { ...object, collection: true }

  it('is drawn for the value of the view', () => {
    expect(collectionOf(marked, {})).toBe(true)
    expect(collectionOf(object, {})).toBe(false)
  })

  it('is drawn from the pending edit before the value of the view', () => {
    expect(collectionOf(object, { [object.entityId!]: { version: 1, properties: { [COLLECTION_ID]: 'true' } } })).toBe(true)
    expect(collectionOf(marked, { [marked.entityId!]: { version: 1, properties: { [COLLECTION_ID]: 'false' } } })).toBe(false)
    expect(collectionOf(marked, { [marked.entityId!]: { version: 1, properties: { [AFFECTED_ATTRIBUTES_ID]: 'x' } } })).toBe(true)
  })

  it('is drawn from the value entered for a new entity', () => {
    const created: CanvasShape = { id: 'n', name: 'Orders', metatypeId: BPMN_TYPES.dataObject, x: 0, y: 0, width: 36, height: 50, missingMandatory: [], newEntity: { key: 'k', metatypeId: BPMN_TYPES.dataObject } }
    expect(collectionOf(created, {})).toBe(false)
    expect(collectionOf({ ...created, newEntity: { ...created.newEntity!, properties: { [COLLECTION_ID]: 'true' } } }, {})).toBe(true)
    expect(collectionOf({ ...created, newEntity: { ...created.newEntity!, properties: { [COLLECTION_ID]: 'false' } } }, {})).toBe(false)
  })

  it('takes the parent away from a new data entity', () => {
    expect(withoutParent({ key: 'k', metatypeId: 'm', parentEntityId: 5, parentNewEntityKey: 'p' })).toEqual({ key: 'k', metatypeId: 'm' })
  })
})

describe('the direction rule of a drawn line', () => {
  const view = bpmnView([])
  const keys = { object: 'bpmn:DataObject', store: 'bpmn:DataStore', task: 'bpmn:Task', gateway: 'bpmn:ExclusiveGateway' }

  it.each([
    ['input from an object', BPMN_TYPES.dataInput, false, { source: keys.object, target: keys.task }, true],
    ['input from a store', BPMN_TYPES.dataInput, false, { source: keys.store, target: keys.task }, true],
    ['input from a task', BPMN_TYPES.dataInput, false, { source: keys.task, target: keys.task }, false],
    ['input from a gateway', BPMN_TYPES.dataInput, false, { source: keys.gateway, target: keys.task }, false],
    ['input reversed', BPMN_TYPES.dataInput, true, { source: keys.task, target: keys.object }, false],
    ['output to an object', BPMN_TYPES.dataOutput, false, { source: keys.task, target: keys.object }, true],
    ['output to a store', BPMN_TYPES.dataOutput, false, { source: keys.task, target: keys.store }, true],
    ['output to a gateway', BPMN_TYPES.dataOutput, false, { source: keys.task, target: keys.gateway }, false],
    ['output reversed', BPMN_TYPES.dataOutput, true, { source: keys.object, target: keys.task }, false],
    ['realized by from a store', BPMN_TYPES.realizedBy, false, { source: keys.store, target: undefined }, true],
    ['realized by from an object', BPMN_TYPES.realizedBy, false, { source: keys.object, target: undefined }, false],
    ['realized by from a task', BPMN_TYPES.realizedBy, false, { source: keys.task, target: keys.store }, false],
    ['realized by reversed', BPMN_TYPES.realizedBy, true, { source: keys.task, target: keys.store }, false],
    ['a flow of a task', BPMN_TYPES.sequenceFlow, false, { source: keys.task, target: keys.task }, true],
    ['a flow reversed', BPMN_TYPES.sequenceFlow, true, { source: keys.task, target: keys.task }, true],
    ['an association', BPMN_TYPES.association, false, { source: keys.task, target: keys.object }, true],
  ])('%s: offered %s', (_name, type, reversed, ends, expected) => {
    expect(dataRuleAllows(view, type, reversed, ends)).toBe(expected)
  })

  it('offers every type in a diagram of another notation', () => {
    const other = bpmnView([], [], 'archimate3')
    expect(dataRuleAllows(other, BPMN_TYPES.dataInput, true, { source: keys.task, target: keys.task })).toBe(true)
    expect(dataRuleAllows(other, BPMN_TYPES.realizedBy, false, { source: keys.task })).toBe(true)
  })
})

describe('an input and an output association between one task and one object', () => {
  it('raises no BPMN warning, so that a task can read and create the same data object (BR-08 retired)', () => {
    const view = bpmnView([bpmnShape('review', 'Review order', 'task', 0, 0, 90, 60), bpmnShape('order', 'Order', 'dataObject', 300, 0, 36, 50)])
    const shapes = view.shapes.map(fromView)
    const edges = [bpmnEdge('in', 'dataInput', 'order', 'review', 'Order → Review order'), bpmnEdge('out', 'dataOutput', 'review', 'order', 'Review order → Order')].map(fromViewEdge)
    expect(bpmnFlowWarnings(shapes, edges, view.viewpoint.effectiveMappings).size).toBe(0)
  })
})
