// The BPMN symbols of the canvas (US-074 AC2, REQ-076; design D3 to D5 of us-074-bpmn-process-diagrams): which
// symbol a notation element of the notation `bpmn2` is drawn as, which line a flow has, the group of the
// palette and which shapes are containers. Pure data; the shape and the edge components draw it.

/** The kind of a symbol: how the outline is drawn. */
export type SymbolKind =
  | 'task'
  | 'sub-process'
  | 'call-activity'
  | 'event'
  | 'gateway'
  | 'pool'
  | 'lane'
  | 'annotation'
  | 'group'
  | 'data-object'
  | 'data-store'

/** Where the name of a symbol is written. */
export type LabelPlace = 'inside' | 'top' | 'below' | 'band'

export type EventPosition = 'start' | 'end' | 'intermediate'

/** The markers of tasks, events and gateways. */
export type Marker =
  | 'user'
  | 'service'
  | 'send'
  | 'receive'
  | 'business-rule'
  | 'script'
  | 'message-throw'
  | 'message-catch'
  | 'timer'
  | 'terminate'
  | 'exclusive'
  | 'parallel'
  | 'inclusive'
  | 'event-based'

export interface BpmnSymbol {
  kind: SymbolKind
  label: LabelPlace
  /** only for `event` */
  position?: EventPosition
  marker?: Marker
}

const TASK: BpmnSymbol = { kind: 'task', label: 'inside' }

const SYMBOLS: Record<string, BpmnSymbol> = {
  'bpmn:Task': TASK,
  'bpmn:UserTask': { ...TASK, marker: 'user' },
  'bpmn:ServiceTask': { ...TASK, marker: 'service' },
  'bpmn:SendTask': { ...TASK, marker: 'send' },
  'bpmn:ReceiveTask': { ...TASK, marker: 'receive' },
  'bpmn:BusinessRuleTask': { ...TASK, marker: 'business-rule' },
  'bpmn:ScriptTask': { ...TASK, marker: 'script' },
  'bpmn:SubProcess': { kind: 'sub-process', label: 'top' },
  'bpmn:CallActivity': { kind: 'call-activity', label: 'inside' },
  'bpmn:StartEvent': { kind: 'event', label: 'below', position: 'start' },
  'bpmn:MessageStartEvent': { kind: 'event', label: 'below', position: 'start', marker: 'message-catch' },
  'bpmn:TimerStartEvent': { kind: 'event', label: 'below', position: 'start', marker: 'timer' },
  'bpmn:EndEvent': { kind: 'event', label: 'below', position: 'end' },
  'bpmn:MessageEndEvent': { kind: 'event', label: 'below', position: 'end', marker: 'message-throw' },
  'bpmn:TerminateEndEvent': { kind: 'event', label: 'below', position: 'end', marker: 'terminate' },
  'bpmn:IntermediateEvent': { kind: 'event', label: 'below', position: 'intermediate' },
  'bpmn:IntermediateCatchEvent': { kind: 'event', label: 'below', position: 'intermediate', marker: 'message-catch' },
  'bpmn:IntermediateThrowEvent': { kind: 'event', label: 'below', position: 'intermediate', marker: 'message-throw' },
  'bpmn:Gateway': { kind: 'gateway', label: 'below' },
  'bpmn:ExclusiveGateway': { kind: 'gateway', label: 'below', marker: 'exclusive' },
  'bpmn:ParallelGateway': { kind: 'gateway', label: 'below', marker: 'parallel' },
  'bpmn:InclusiveGateway': { kind: 'gateway', label: 'below', marker: 'inclusive' },
  'bpmn:EventBasedGateway': { kind: 'gateway', label: 'below', marker: 'event-based' },
  'bpmn:Pool': { kind: 'pool', label: 'band' },
  'bpmn:Lane': { kind: 'lane', label: 'band' },
  'bpmn:TextAnnotation': { kind: 'annotation', label: 'inside' },
  'bpmn:Group': { kind: 'group', label: 'top' },
  'bpmn:DataObject': { kind: 'data-object', label: 'below' },
  'bpmn:DataStore': { kind: 'data-store', label: 'below' },
}

/** The symbol of a notation element of `bpmn2`; `undefined` for an unmapped type, a generic element or an unknown key. */
export function bpmnSymbol(notation: string | undefined, key: string | undefined): BpmnSymbol | undefined {
  return notation === 'bpmn2' && key !== undefined ? SYMBOLS[key] : undefined
}

/** The width of the label band of a pool or lane in pt. */
export const BAND_WIDTH = 30

/** The line style of a flow. */
export type FlowStyle = 'sequence' | 'message' | 'association' | 'data'

const FLOWS: Record<string, FlowStyle> = {
  'bpmn:SequenceFlow': 'sequence',
  'bpmn:MessageFlow': 'message',
  'bpmn:Association': 'association',
  'bpmn:DataInputAssociation': 'data',
  'bpmn:DataOutputAssociation': 'data',
}

/** The line style of a connection type by its notation element; `undefined` keeps the default line. */
export function flowStyle(notation: string | undefined, key: string | undefined): FlowStyle | undefined {
  return notation === 'bpmn2' && key !== undefined ? FLOWS[key] : undefined
}

/** The elements that take nested shapes on a BPMN canvas: pool, lane and sub-process. */
const CONTAINERS = new Set(['bpmn:Pool', 'bpmn:Lane', 'bpmn:SubProcess'])

export function isBpmnContainer(key: string | undefined): boolean {
  return key !== undefined && CONTAINERS.has(key)
}

/** The groups of the palette in their order. */
export const PALETTE_GROUPS = ['pools', 'activities', 'events', 'gateways', 'data', 'artifacts', 'other'] as const
export type PaletteGroup = (typeof PALETTE_GROUPS)[number]

/** The palette group of an element by its notation element; types without a BPMN element are "other". */
export function paletteGroup(key: string | undefined): PaletteGroup {
  if (key === undefined || !SYMBOLS[key]) return 'other'
  switch (SYMBOLS[key]!.kind) {
    case 'pool':
    case 'lane':
      return 'pools'
    case 'task':
    case 'sub-process':
    case 'call-activity':
      return 'activities'
    case 'event':
      return 'events'
    case 'gateway':
      return 'gateways'
    case 'data-object':
    case 'data-store':
      return 'data'
    default:
      return 'artifacts'
  }
}

/** One path of a marker in a 24 x 24 box: drawn as outline, filled with the line color or filled with the background. */
export interface MarkerPath {
  d: string
  fill: 'none' | 'solid' | 'background'
}

const ENVELOPE = 'M3 6h18v12H3z'
const FLAP = 'M3 6l9 7 9-7'
const CIRCLE = (r: number) => `M${12 - r} 12a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`

/** The drawing of each marker. */
export const MARKER_PATHS: Record<Marker, MarkerPath[]> = {
  user: [
    { d: 'M8.5 8a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0', fill: 'none' },
    { d: 'M5 20c0-4 3-6.5 7-6.5s7 2.5 7 6.5z', fill: 'none' },
  ],
  service: [
    { d: CIRCLE(3.5), fill: 'none' },
    { d: 'M12 2v4M12 18v4M2 12h4M18 12h4M5 5l3 3M16 16l3 3M19 5l-3 3M8 16l-3 3', fill: 'none' },
  ],
  send: [
    { d: ENVELOPE, fill: 'solid' },
    { d: FLAP, fill: 'background' },
  ],
  receive: [
    { d: ENVELOPE, fill: 'none' },
    { d: FLAP, fill: 'none' },
  ],
  'business-rule': [
    { d: 'M3 5h18v14H3z', fill: 'none' },
    { d: 'M3 10h18M3 14.5h18M9 10v9', fill: 'none' },
  ],
  script: [
    { d: 'M7 3h11v18H7z', fill: 'none' },
    { d: 'M10 8h5M10 12h5M10 16h5', fill: 'none' },
  ],
  'message-throw': [
    { d: ENVELOPE, fill: 'solid' },
    { d: FLAP, fill: 'background' },
  ],
  'message-catch': [
    { d: ENVELOPE, fill: 'none' },
    { d: FLAP, fill: 'none' },
  ],
  timer: [
    { d: CIRCLE(9), fill: 'none' },
    { d: 'M12 6v6l4 2.5', fill: 'none' },
  ],
  terminate: [{ d: CIRCLE(7), fill: 'solid' }],
  exclusive: [{ d: 'M7 7l10 10M17 7L7 17', fill: 'none' }],
  parallel: [{ d: 'M12 5v14M5 12h14', fill: 'none' }],
  inclusive: [{ d: CIRCLE(7), fill: 'none' }],
  'event-based': [
    { d: CIRCLE(8), fill: 'none' },
    { d: 'M12 6.5l5 3.7-2 5.8H9l-2-5.8z', fill: 'none' },
  ],
}

/** The number of strokes of the outline of an event: 1 for a start, 2 (double) for an intermediate event. */
export function eventRings(position: EventPosition | undefined): 1 | 2 {
  return position === 'intermediate' ? 2 : 1
}

/** The stroke width of an event outline in px: thin for a start, thick for an end event. */
export function eventStroke(position: EventPosition | undefined): number {
  return position === 'end' ? 4 : 1.5
}
