import { describe, expect, it } from 'vitest'
import { bpmnSymbol, eventRings, eventStroke, flowStyle, isBpmnContainer, MARKER_PATHS, PALETTE_GROUPS, paletteGroup } from './bpmnSymbols'

// spec diagram/bpmn-process-diagrams "BPMN symbols on the canvas", "Grouped BPMN palette" (us-074-bpmn-process-diagrams D3, D4)

describe('bpmnSymbols', () => {
  it.each([
    ['bpmn:Task', 'task', undefined],
    ['bpmn:UserTask', 'task', 'user'],
    ['bpmn:ServiceTask', 'task', 'service'],
    ['bpmn:SendTask', 'task', 'send'],
    ['bpmn:ReceiveTask', 'task', 'receive'],
    ['bpmn:BusinessRuleTask', 'task', 'business-rule'],
    ['bpmn:ScriptTask', 'task', 'script'],
    ['bpmn:SubProcess', 'sub-process', undefined],
    ['bpmn:CallActivity', 'call-activity', undefined],
    ['bpmn:StartEvent', 'event', undefined],
    ['bpmn:MessageStartEvent', 'event', 'message-catch'],
    ['bpmn:TimerStartEvent', 'event', 'timer'],
    ['bpmn:EndEvent', 'event', undefined],
    ['bpmn:MessageEndEvent', 'event', 'message-throw'],
    ['bpmn:TerminateEndEvent', 'event', 'terminate'],
    ['bpmn:IntermediateEvent', 'event', undefined],
    ['bpmn:IntermediateCatchEvent', 'event', 'message-catch'],
    ['bpmn:IntermediateThrowEvent', 'event', 'message-throw'],
    ['bpmn:Gateway', 'gateway', undefined],
    ['bpmn:ExclusiveGateway', 'gateway', 'exclusive'],
    ['bpmn:ParallelGateway', 'gateway', 'parallel'],
    ['bpmn:InclusiveGateway', 'gateway', 'inclusive'],
    ['bpmn:EventBasedGateway', 'gateway', 'event-based'],
    ['bpmn:Pool', 'pool', undefined],
    ['bpmn:Lane', 'lane', undefined],
    ['bpmn:TextAnnotation', 'annotation', undefined],
    ['bpmn:Group', 'group', undefined],
    ['bpmn:DataObject', 'data-object', undefined],
    ['bpmn:DataStore', 'data-store', undefined],
  ])('draws %s as %s with the marker %s', (key, kind, marker) => {
    const symbol = bpmnSymbol('bpmn2', key)
    expect(symbol?.kind).toBe(kind)
    expect(symbol?.marker).toBe(marker)
  })

  it.each([
    ['bpmn2', 'generic:Box'],
    ['bpmn2', 'bpmn:Unknown'],
    ['bpmn2', 'bpmn:SequenceFlow'],
    ['bpmn2', undefined],
    ['archimate3', 'bpmn:Task'],
    ['generic', 'bpmn:Pool'],
    ['uml', 'uml:Component'],
    [undefined, 'bpmn:Task'],
  ])('keeps the box for the notation %s and the element %s', (notation, key) => {
    expect(bpmnSymbol(notation, key)).toBeUndefined()
  })

  it('draws a start event thin, an end event thick and an intermediate event double', () => {
    expect([eventRings('start'), eventStroke('start')]).toEqual([1, 1.5])
    expect([eventRings('end'), eventStroke('end')]).toEqual([1, 4])
    expect([eventRings('intermediate'), eventStroke('intermediate')]).toEqual([2, 1.5])
  })

  it('gives every marker of the symbols a drawing, and throw and catch envelopes differ', () => {
    for (const key of ['bpmn:UserTask', 'bpmn:SendTask', 'bpmn:TimerStartEvent', 'bpmn:EventBasedGateway', 'bpmn:TerminateEndEvent']) {
      expect(MARKER_PATHS[bpmnSymbol('bpmn2', key)!.marker!].length).toBeGreaterThan(0)
    }
    expect(MARKER_PATHS['message-throw'][0]!.fill).toBe('solid')
    expect(MARKER_PATHS['message-catch'][0]!.fill).toBe('none')
  })

  it.each([
    ['bpmn:SequenceFlow', 'sequence'],
    ['bpmn:MessageFlow', 'message'],
    ['bpmn:Association', 'association'],
    ['bpmn:DataInputAssociation', 'data'],
    ['bpmn:DataOutputAssociation', 'data'],
  ])('draws %s with the line %s', (key, style) => {
    expect(flowStyle('bpmn2', key)).toBe(style)
  })

  it.each([
    ['bpmn2', 'generic:Arrow'],
    ['bpmn2', 'bpmn:Task'],
    ['archimate3', 'bpmn:SequenceFlow'],
    ['bpmn2', undefined],
  ])('keeps the default line for the notation %s and the element %s', (notation, key) => {
    expect(flowStyle(notation, key)).toBeUndefined()
  })

  it.each([
    ['bpmn:Pool', 'pools'],
    ['bpmn:Lane', 'pools'],
    ['bpmn:Task', 'activities'],
    ['bpmn:UserTask', 'activities'],
    ['bpmn:SubProcess', 'activities'],
    ['bpmn:CallActivity', 'activities'],
    ['bpmn:StartEvent', 'events'],
    ['bpmn:TimerStartEvent', 'events'],
    ['bpmn:IntermediateThrowEvent', 'events'],
    ['bpmn:Gateway', 'gateways'],
    ['bpmn:EventBasedGateway', 'gateways'],
    ['bpmn:TextAnnotation', 'artifacts'],
    ['bpmn:Group', 'artifacts'],
    ['generic:Box', 'other'],
    ['bpmn:DataObject', 'data'],
    ['bpmn:DataStore', 'data'],
    [undefined, 'other'],
  ])('puts %s in the palette group %s', (key, group) => {
    expect(paletteGroup(key)).toBe(group)
  })

  it('orders the palette groups', () => {
    expect(PALETTE_GROUPS).toEqual(['pools', 'activities', 'events', 'gateways', 'data', 'artifacts', 'other'])
  })

  it.each([
    ['bpmn:Pool', true],
    ['bpmn:Lane', true],
    ['bpmn:SubProcess', true],
    ['bpmn:Task', false],
    ['bpmn:CallActivity', false],
    ['bpmn:Group', false],
    ['bpmn:TextAnnotation', false],
    ['generic:Box', false],
    [undefined, false],
  ])('%s takes nested shapes: %s', (key, container) => {
    expect(isBpmnContainer(key)).toBe(container)
  })
})
