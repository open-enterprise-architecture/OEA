/**
 * Test and story data for BPMN diagrams (us-074-bpmn-process-diagrams): the types of the starter package
 * `oea-starter-bpmn` as a viewpoint of the notation `bpmn2` with its mappings and the shapes of a process.
 */
import type { DiagramEdge, DiagramShape, DiagramView } from '../api/diagramView'
import type { Viewpoint } from '../api/metamodel'
import { diagramView, edge, shape, viewpointConfig } from './diagrams'

export const BPMN_TYPES = {
  pool: 'c2000000-0000-4000-8000-000000000001',
  lane: 'c2000000-0000-4000-8000-000000000002',
  task: 'c2000000-0000-4000-8000-000000000003',
  userTask: 'c2000000-0000-4000-8000-000000000004',
  subProcess: 'c2000000-0000-4000-8000-000000000005',
  startEvent: 'c2000000-0000-4000-8000-000000000006',
  endEvent: 'c2000000-0000-4000-8000-000000000007',
  gateway: 'c2000000-0000-4000-8000-000000000008',
  textAnnotation: 'c2000000-0000-4000-8000-000000000009',
  group: 'c2000000-0000-4000-8000-00000000000a',
  process: 'c2000000-0000-4000-8000-00000000000b',
  approvalTask: 'c2000000-0000-4000-8000-00000000000c',
  messageStart: 'c2000000-0000-4000-8000-00000000000d',
  catchEvent: 'c2000000-0000-4000-8000-00000000000e',
  sequenceFlow: 'c2000000-0000-4000-8000-000000000021',
  messageFlow: 'c2000000-0000-4000-8000-000000000022',
  association: 'c2000000-0000-4000-8000-000000000023',
  calls: 'c2000000-0000-4000-8000-000000000024',
} as const

type Mapping = Viewpoint['effectiveMappings'][number]

const ELEMENTS: [keyof typeof BPMN_TYPES, string, string, string | null, number, number][] = [
  ['pool', 'Pool', 'PL', 'bpmn:Pool', 600, 200],
  ['lane', 'Lane', 'LN', 'bpmn:Lane', 570, 100],
  ['task', 'Task', 'TK', 'bpmn:Task', 90, 60],
  ['userTask', 'User Task', 'UT', 'bpmn:UserTask', 90, 60],
  ['approvalTask', 'Approval Task', 'AT', 'bpmn:UserTask', 90, 60],
  ['subProcess', 'Sub-Process', 'SP', 'bpmn:SubProcess', 270, 150],
  ['startEvent', 'Start Event', 'SE', 'bpmn:StartEvent', 27, 27],
  ['messageStart', 'Message Start Event', 'MSE', 'bpmn:MessageStartEvent', 27, 27],
  ['catchEvent', 'Intermediate Catch Event', 'ICE', 'bpmn:IntermediateCatchEvent', 27, 27],
  ['endEvent', 'End Event', 'EE', 'bpmn:EndEvent', 27, 27],
  ['gateway', 'Exclusive Gateway', 'XG', 'bpmn:ExclusiveGateway', 38, 38],
  ['textAnnotation', 'Text Annotation', 'TA', 'bpmn:TextAnnotation', 90, 45],
  ['group', 'Group', 'GR', 'bpmn:Group', 180, 120],
  ['process', 'Process', 'PR', null, 90, 30],
]

const RELATIONS: [keyof typeof BPMN_TYPES, string, string, string | null, number][] = [
  ['sequenceFlow', 'Sequence Flow', 'SF', 'bpmn:SequenceFlow', 45],
  ['messageFlow', 'Message Flow', 'MF', 'bpmn:MessageFlow', 90],
  ['association', 'Association', 'ASC', 'bpmn:Association', 45],
  ['calls', 'Calls', 'CL', null, 90],
]

/** The viewpoint "Process Model (BPMN 2.0)" of the starter package, with the types of {@link BPMN_TYPES}. */
export function bpmnViewpoint(notation: Viewpoint['notation'] = 'bpmn2'): Viewpoint {
  const base = viewpointConfig()
  const element = (entry: (typeof ELEMENTS)[number]): Mapping => ({
    metatypeId: BPMN_TYPES[entry[0]],
    notationElement: entry[3] ?? 'generic:Box',
    width: entry[4],
    height: entry[5],
    length: null,
    sizeStored: true,
    visualHint: null,
    fallback: entry[3] === null,
  })
  const relation = (entry: (typeof RELATIONS)[number]): Mapping => ({
    metatypeId: BPMN_TYPES[entry[0]],
    notationElement: entry[3] ?? 'generic:Arrow',
    width: null,
    height: null,
    length: entry[4],
    sizeStored: true,
    visualHint: null,
    fallback: entry[3] === null,
  })
  const byName = <T extends { name: string }>(list: T[]) => [...list].sort((a, b) => a.name.localeCompare(b.name))
  return {
    ...base,
    notation,
    mappings: [],
    sizes: [],
    effectiveMappings: [...ELEMENTS.map(element), ...RELATIONS.map(relation)],
    allowedMetatypes: byName(ELEMENTS.map((entry) => ({ id: BPMN_TYPES[entry[0]], name: entry[1], abbreviation: entry[2] }))),
    allowedRelations: byName(RELATIONS.map((entry) => ({ id: BPMN_TYPES[entry[0]], name: entry[1], abbreviation: entry[2] }))),
  }
}

let entityCounter = 500

/** A shape of an entity of a BPMN type; positions in pt, the container by its shape ID. */
export function bpmnShape(
  id: string,
  name: string,
  type: keyof typeof BPMN_TYPES,
  x: number,
  y: number,
  width: number,
  height: number,
  parentShapeId: string | null = null,
): DiagramShape {
  entityCounter += 1
  return shape({ id, entity: { id: entityCounter, name, metatypeId: BPMN_TYPES[type], childCount: 0, parentEntityId: null }, parentShapeId, x, y, width, height })
}

/** The view of a BPMN diagram with the given shapes and edges (notation `bpmn2` unless given). */
export function bpmnView(shapes: DiagramShape[], edges: DiagramEdge[] = [], notation: Viewpoint['notation'] = 'bpmn2'): DiagramView {
  return { ...diagramView(shapes, {}, edges), viewpoint: bpmnViewpoint(notation) }
}

/** An edge of a BPMN flow between two shapes. */
export function bpmnEdge(id: string, type: keyof typeof BPMN_TYPES, source: string, target: string, name = 'Flow'): DiagramEdge {
  entityCounter += 1
  return edge({ id, connection: { id: entityCounter, name, metatypeId: BPMN_TYPES[type] }, sourceShapeId: source, targetShapeId: target })
}
