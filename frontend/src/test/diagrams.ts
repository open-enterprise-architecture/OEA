import type { DeletedDiagram, Diagram } from '../api/diagrams'
import type { AllowedConnectionResult, DiagramConnectionOffer, DiagramEdge, DiagramEntity, DiagramEntityDetails, DiagramShape, DiagramView, DiagramViewWrite } from '../api/diagramView'
import type { Viewpoint } from '../api/metamodel'
import type { RecordedRequest } from './fetch'

/** Test and story data and a fake backend for the diagram screens (US-074, SCR-067, SCR-021). */
function me(displayName: string, id: number, permissions: string[]) {
  return {
    principal: { principalType: 'person', id, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-diagrams',
  }
}

/** Holds `entity:write`: creates, renames and deletes diagrams. */
export const ME_DIAGRAM_WRITER = me('Anna', 7, ['portal:read', 'entity:read', 'entity:write'])
/** Holds `entity:restore` as well: lists and restores deleted diagrams. */
export const ME_DIAGRAM_RESTORER = me('Kurt', 8, ['portal:read', 'entity:read', 'entity:write', 'entity:restore', 'viewpoint:manage'])
/** Holds `viewpoint:manage` and `entity:read`: places diagrams in the navigation tree. */
export const ME_DIAGRAM_MANAGER = me('Michael', 9, ['portal:read', 'entity:read', 'viewpoint:manage'])
/** Holds `entity:read` only. */
export const ME_DIAGRAM_READER = me('Franz', 10, ['portal:read', 'entity:read'])

export const VIEWPOINT_BPMN = 'b1000000-0000-4000-8000-000000000001'
export const VIEWPOINT_ARCHIMATE = 'b1000000-0000-4000-8000-000000000002'

const ACTOR = { principalType: 'person', id: 7, displayName: 'Anna' } as const

export const VIEWPOINTS: Pick<Viewpoint, 'id' | 'name' | 'notation'>[] = [
  { id: VIEWPOINT_BPMN, name: 'Process Model (BPMN 2.0)', notation: 'bpmn2' },
  { id: VIEWPOINT_ARCHIMATE, name: 'Application Landscape', notation: 'archimate3' },
]

export function diagram(overrides: Partial<Diagram> = {}): Diagram {
  return {
    id: 'a1000000-0000-4000-8000-000000000001',
    name: 'Order to Cash',
    description: null,
    viewpoint: { id: VIEWPOINT_BPMN, name: 'Process Model (BPMN 2.0)', notation: 'bpmn2' },
    version: 1,
    createdBy: ACTOR,
    createdAt: '2026-11-19T09:00:00Z',
    updatedAt: '2026-11-19T09:00:00Z',
    ...overrides,
  }
}

export function deletedDiagram(overrides: Partial<DeletedDiagram> = {}): DeletedDiagram {
  return {
    ...diagram({ id: 'a1000000-0000-4000-8000-000000000009', name: 'Lead to Order', version: 2 }),
    deletedAt: '2026-11-19T10:00:00Z',
    deletedBy: { principalType: 'person', id: 8, displayName: 'Kurt' },
    ...overrides,
  }
}

export const DIAGRAMS: Diagram[] = [
  diagram({ id: 'a1000000-0000-4000-8000-000000000002', name: 'Hire to Retire', description: 'From hiring to leaving.' }),
  diagram(),
  diagram({
    id: 'a1000000-0000-4000-8000-000000000003',
    name: 'Procure to Pay',
    viewpoint: { id: VIEWPOINT_ARCHIMATE, name: 'Application Landscape', notation: 'archimate3' },
  }),
]


export const METATYPE_COMPONENT = 'c1000000-0000-4000-8000-000000000001'
export const METATYPE_PROCESS = 'c1000000-0000-4000-8000-000000000002'
export const METATYPE_OUTSIDER = 'c1000000-0000-4000-8000-000000000003'
/** Connection types of the viewpoint: "Flow" stores a length of 150 pt, "Association" has none (90 pt). */
export const METATYPE_FLOW = 'c1000000-0000-4000-8000-000000000011'
export const METATYPE_ASSOCIATION = 'c1000000-0000-4000-8000-000000000012'

type ViewpointConfig = Viewpoint

/**
 * The viewpoint of the canvas: "Application Component" stores 120 x 40 pt and a visual hint, "Business
 * Process" has no stored size (the default is 90 x 30 pt).
 */
export function viewpointConfig(): ViewpointConfig {
  return {
    id: VIEWPOINT_BPMN,
    name: 'Process Model (BPMN 2.0)',
    description: null,
    notation: 'bpmn2',
    viewpointType: 'user-defined',
    sourcePackage: null,
    mappings: [],
    sizes: [
      { metatypeId: METATYPE_COMPONENT, width: 120, height: 40, length: null },
      { metatypeId: METATYPE_FLOW, width: null, height: null, length: 150 },
    ],
    effectiveMappings: [
      { metatypeId: METATYPE_COMPONENT, notationElement: 'generic:Box', width: 120, height: 40, length: null, sizeStored: true, visualHint: '#2563eb', fallback: true },
      { metatypeId: METATYPE_PROCESS, notationElement: 'generic:Box', width: 90, height: 30, length: null, sizeStored: false, visualHint: null, fallback: true },
      { metatypeId: METATYPE_FLOW, notationElement: 'generic:Arrow', width: null, height: null, length: 150, sizeStored: true, visualHint: '#16a34a', fallback: true },
      { metatypeId: METATYPE_ASSOCIATION, notationElement: 'generic:Arrow', width: null, height: null, length: 90, sizeStored: false, visualHint: null, fallback: true },
    ],
    allowedMetatypes: [
      { id: METATYPE_COMPONENT, name: 'Application Component', abbreviation: 'AC' },
      { id: METATYPE_PROCESS, name: 'Business Process', abbreviation: 'BP' },
    ],
    allowedRelations: [
      { id: METATYPE_ASSOCIATION, name: 'Association', abbreviation: 'AS' },
      { id: METATYPE_FLOW, name: 'Flow', abbreviation: 'FL' },
    ],
    diagramCount: 1,
    version: 1,
    createdAt: '2026-11-19T09:00:00Z',
    createdBy: { id: 7, name: 'Anna' },
  }
}

export const ENTITIES: DiagramEntity[] = [
  { id: 11, name: 'SAP BW', metatype: { id: METATYPE_COMPONENT, name: 'Application Component', abbreviation: 'AC' } },
  { id: 12, name: 'SAP ERP', metatype: { id: METATYPE_COMPONENT, name: 'Application Component', abbreviation: 'AC' } },
  { id: 13, name: 'Salesforce', metatype: { id: METATYPE_COMPONENT, name: 'Application Component', abbreviation: 'AC' } },
  { id: 14, name: 'Invoice Approval', metatype: { id: METATYPE_PROCESS, name: 'Business Process', abbreviation: 'BP' } },
]

export const PROP_STATUS = 'e1000000-0000-4000-8000-000000000001'
export const PROP_INVESTMENT = 'e1000000-0000-4000-8000-000000000002'
export const PROP_HOSTED_ON = 'e1000000-0000-4000-8000-000000000003'
export const PROP_OWNER = 'e1000000-0000-4000-8000-000000000004'

function mapping(id: string, name: string, dataType: string, necessity: string, sortOrder: number, extra: Record<string, unknown> = {}) {
  return {
    id: `f${id.slice(1)}`,
    necessity,
    validFrom: '2026-01-01',
    sortOrder,
    state: 'active',
    propertyDefinition: { id, name, label: name, dataType, enumValues: [], refMetatype: null, ...extra },
  }
}

/** The active mappings of "Application Component" and "Business Process" as getMetatype answers them. */
export const COMPONENT_MAPPINGS = [
  mapping(PROP_STATUS, 'Status', 'enum', 'optional', 1, {
    enumValues: [
      { id: '9d000000-0000-4000-8000-0000000000a1', value: 'active', sortOrder: 1 },
      { id: '9d000000-0000-4000-8000-0000000000a2', value: 'retiring', sortOrder: 2 },
    ],
  }),
  mapping(PROP_INVESTMENT, 'Investment', 'integer', 'optional', 2),
  mapping(PROP_HOSTED_ON, 'Hosted on', 'reference', 'optional', 3, { refMetatype: { id: METATYPE_COMPONENT, name: 'Application Component' } }),
]
export const PROCESS_MAPPINGS = [mapping(PROP_OWNER, 'Process Owner', 'string', 'mandatory', 1)]

export const PROP_PROTOCOL = 'e1000000-0000-4000-8000-000000000005'
/** "Flow" has a mandatory "Protocol": a connection drawn on the canvas is incomplete until it has one. */
export const FLOW_MAPPINGS = [mapping(PROP_PROTOCOL, 'Protocol', 'string', 'mandatory', 1)]

const MAPPINGS: Record<string, ReturnType<typeof mapping>[]> = { [METATYPE_COMPONENT]: COMPONENT_MAPPINGS, [METATYPE_PROCESS]: PROCESS_MAPPINGS, [METATYPE_FLOW]: FLOW_MAPPINGS }

/**
 * The connection types between two metatypes of the fake metamodel: "Flow" runs from a component to a
 * component or a process, "Association" joins two components in both directions.
 */
export function allowedConnections(source: string, target: string): AllowedConnectionResult {
  const options: AllowedConnectionResult['options'] = []
  const flow = { id: METATYPE_FLOW, name: 'Flow', abbreviation: 'FL' }
  const association = { id: METATYPE_ASSOCIATION, name: 'Association', abbreviation: 'AS' }
  if (source === METATYPE_COMPONENT && target === METATYPE_COMPONENT) {
    options.push({ connectionMetatype: flow, direction: 'source-to-target' }, { connectionMetatype: association, direction: 'bidirectional' })
  } else if (source === METATYPE_COMPONENT && target === METATYPE_PROCESS) {
    options.push({ connectionMetatype: flow, direction: 'source-to-target' })
  } else if (source === METATYPE_PROCESS && target === METATYPE_COMPONENT) {
    options.push({ connectionMetatype: flow, direction: 'target-to-source' })
  }
  return { allowed: options.length > 0, options }
}

/** The state of an entity of the fake: version, fields and the values by property definition ID as text. */
export interface FakeEntity {
  id: number
  name: string
  description: string
  metatypeId: string
  version: number
  values: Record<string, string>
}

/** The saved entities "SAP ERP" (version 4, with values), "Salesforce" and the incomplete "Invoice Approval". */
export function fakeEntities(): Record<number, FakeEntity> {
  return {
    12: { id: 12, name: 'SAP ERP', description: 'Core ERP for finance and logistics', metatypeId: METATYPE_COMPONENT, version: 4, values: { [PROP_STATUS]: 'active', [PROP_INVESTMENT]: '5000000' } },
    13: { id: 13, name: 'Salesforce', description: '', metatypeId: METATYPE_COMPONENT, version: 1, values: {} },
    14: { id: 14, name: 'Invoice Approval', description: '', metatypeId: METATYPE_PROCESS, version: 1, values: {} },
    21: { id: 21, name: 'SAP ERP → Salesforce', description: 'Orders to the CRM', metatypeId: METATYPE_FLOW, version: 2, values: { [PROP_PROTOCOL]: 'REST' } },
  }
}

const DEFINITIONS = Object.fromEntries([...COMPONENT_MAPPINGS, ...PROCESS_MAPPINGS, ...FLOW_MAPPINGS].map((entry) => [entry.propertyDefinition.id, entry.propertyDefinition]))

function propertyValue(defId: string, text: string, redacted: boolean) {
  const definition = DEFINITIONS[defId]!
  const base = { propertyDefId: defId, propertyName: definition.name, label: definition.label, dataType: definition.dataType, redacted }
  if (redacted) return { ...base, valueText: null, valueInt: null, valueBool: null, valueDate: null, valueEnum: null, valueRef: null }
  switch (definition.dataType) {
    case 'integer':
      return { ...base, valueInt: Number(text) }
    case 'boolean':
      return { ...base, valueBool: text === 'true' }
    case 'enum':
      return { ...base, valueEnum: text }
    case 'reference': {
      const target = ENTITIES.find((entity) => String(entity.id) === text)
      return { ...base, valueRef: { id: Number(text), name: target?.name ?? text, metatypeAbbr: 'AC' } }
    }
    default:
      return { ...base, valueText: text }
  }
}

function mandatoryMissing(entity: FakeEntity): string[] {
  return (MAPPINGS[entity.metatypeId] ?? []).filter((entry) => entry.necessity === 'mandatory' && !entity.values[entry.propertyDefinition.id]).map((entry) => entry.propertyDefinition.name)
}

/** A shape of the view; the entity defaults to "SAP ERP". */
export function shape(overrides: Partial<DiagramShape> = {}): DiagramShape {
  return {
    id: 'd1000000-0000-4000-8000-000000000001',
    entity: { id: 12, name: 'SAP ERP', metatypeId: METATYPE_COMPONENT },
    x: 100,
    y: 50,
    width: 120,
    height: 40,
    missingMandatory: [],
    ...overrides,
  }
}

/** An edge of the view; the connection defaults to "SAP ERP → Salesforce" between the shapes of the stories. */
export function edge(overrides: Partial<DiagramEdge> = {}): DiagramEdge {
  return {
    id: 'f1000000-0000-4000-8000-000000000001',
    connection: { id: 21, name: 'SAP ERP → Salesforce', metatypeId: METATYPE_FLOW },
    sourceShapeId: 'd1000000-0000-4000-8000-000000000001',
    targetShapeId: 'd1000000-0000-4000-8000-000000000002',
    points: [],
    missingMandatory: [],
    ...overrides,
  }
}

/** The view of a diagram with the given shapes and edges, for the component specs of the canvas. */
export function diagramView(shapes: DiagramShape[] = [], overrides: Partial<Diagram> = {}, edges: DiagramEdge[] = []): DiagramView {
  return { diagram: diagram(overrides), viewpoint: viewpointConfig(), shapes, edges }
}

function respond(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json', ...headers },
  })
}

function problem(status: number, type: string, extra: Record<string, unknown> = {}): Response {
  return respond(status, { type: `urn:oea:problem:${type}`, title: type, status, ...extra })
}

export interface DiagramApiOptions {
  me: unknown
  diagrams?: Diagram[]
  deleted?: DeletedDiagram[]
  recorded?: RecordedRequest[]
  /** the page size of the fake list (the contract allows 1 to 200) */
  pageSize?: number
  /** the shapes of the diagram "Order to Cash" (SCR-021) */
  shapes?: DiagramShape[]
  /** the edges of the diagram "Order to Cash" (SCR-021) */
  edges?: DiagramEdge[]
  /** the connections the offer finds when an entity is placed: the fake answers those between the placed and the shown entities */
  offers?: DiagramConnectionOffer[]
  /** the connection types between two metatypes; default `allowedConnections` */
  allowed?: (source: string, target: string) => AllowedConnectionResult
  /** the entities "Add existing" finds */
  entities?: DiagramEntity[]
  /** the names of the mandatory properties a new entity of a metatype misses (an entity created on the canvas is incomplete) */
  missingMandatory?: Record<string, string[]>
  /** a save answers this status instead of saving (412, 422 with `errors`, 403, 413, or 409 with the members of its problem) */
  rejectSave?: { status: number; type?: string; errors?: { field: string; code: string; message: string }[]; extra?: Record<string, unknown> }
  /** the saved entities of the details panel; default `fakeEntities()` */
  entityStates?: Record<number, FakeEntity>
  /** the properties the caller may not write (`writable: false`), e.g. a restricted one */
  readOnlyProperties?: string[]
  /** the properties whose value the caller may not read (redacted) */
  redactedProperties?: string[]
  /** the entities with an open issue: a change of one answers 409 `entity-blocked` */
  blockedEntities?: number[]
  /** the page size of the fake entity search */
  searchPageSize?: number
}

export type DiagramApi = typeof fetch & {
  state: { diagrams: Diagram[]; deleted: DeletedDiagram[]; shapes: DiagramShape[]; edges: DiagramEdge[]; saves: DiagramViewWrite[]; entities: Record<number, FakeEntity>; queries: { entityIds: number[]; shownEntityIds: number[] }[] }
}

/**
 * A fake backend of the diagram API with state: the list is ordered by name ignoring case and
 * filtered by `q`, the writes check `If-Match`, names are unique, and a deleted diagram moves to
 * the deleted list until it is restored.
 */
export function diagramApi(options: DiagramApiOptions): DiagramApi {
  const state = {
    diagrams: (options.diagrams ?? DIAGRAMS).map((entry) => structuredClone(entry)),
    deleted: (options.deleted ?? []).map((entry) => structuredClone(entry)),
    shapes: (options.shapes ?? []).map((entry) => structuredClone(entry)),
    edges: (options.edges ?? []).map((entry) => structuredClone(entry)),
    saves: [] as DiagramViewWrite[],
    queries: [] as { entityIds: number[]; shownEntityIds: number[] }[],
    entities: structuredClone(options.entityStates ?? fakeEntities()),
  }
  let counter = 100
  let entityCounter = 200
  const sorted = <T extends Diagram>(list: T[]) => [...list].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
  const taken = (name: string, except?: string) => state.diagrams.some((entry) => entry.id !== except && entry.name.toLowerCase() === name.trim().toLowerCase())
  const page = <T extends Diagram>(list: T[], url: URL) => {
    const q = (url.searchParams.get('q') ?? '').toLowerCase()
    const size = options.pageSize ?? Number(url.searchParams.get('pageSize') ?? 50)
    const index = Number(url.searchParams.get('page') ?? 0)
    const all = sorted(list).filter((entry) => entry.name.toLowerCase().includes(q))
    const items = all.slice(index * size, (index + 1) * size)
    const more = (index + 1) * size < all.length
    return {
      items,
      page: { page: index, pageSize: size, totalElements: all.length, totalPages: Math.ceil(all.length / size) },
      nextCursor: more ? `cursor-${index + 1}` : null,
    }
  }
  const api = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const path = url.pathname
    const method = (init?.method ?? 'GET').toUpperCase()
    options.recorded?.push({ path: path + url.search, init })
    const headers = (init?.headers ?? {}) as Record<string, string>
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : undefined
    const version = headers['If-Match'] ? Number(headers['If-Match'].replaceAll('"', '')) : undefined

    if (path === '/api/v1/me') {
      return respond(200, options.me)
    }
    if (path === '/api/v1/admin/viewpoints' && method === 'GET') {
      return respond(200, VIEWPOINTS)
    }
    const view = /^\/api\/v1\/views\/diagrams\/([^/]+)$/.exec(path)
    if (view) {
      const found = state.diagrams.find((entry) => entry.id === view[1])
      if (!found) {
        return problem(404, 'not-found')
      }
      if (method === 'GET') {
        return respond(200, { diagram: found, viewpoint: viewpointConfig(), shapes: state.shapes, edges: state.edges }, { ETag: `"${found.version}"` })
      }
      if (method === 'PUT') {
        if (version !== found.version) {
          return problem(412, 'stale-version', { currentVersion: found.version })
        }
        const request = body as unknown as DiagramViewWrite
        if (options.rejectSave) {
          return problem(options.rejectSave.status, options.rejectSave.type ?? 'validation-failed', {
            errors: options.rejectSave.errors ?? [],
            invalidFields: (options.rejectSave.errors ?? []).map((error) => error.field),
            ...options.rejectSave.extra,
          })
        }
        const updates = request.entityUpdates ?? []
        const stale = updates.flatMap((update, index) => {
          const entity = state.entities[update.entityId]
          return entity && entity.version !== update.expectedVersion ? [{ index, entityId: update.entityId, currentVersion: entity.version }] : []
        })
        if (stale.length > 0) {
          return problem(409, 'entity-version-conflict', { conflicts: stale.map(({ entityId, currentVersion }) => ({ entityId, currentVersion })), invalidFields: stale.map((entry) => `entityUpdates[${entry.index}]`) })
        }
        const blocked = updates.filter((update) => options.blockedEntities?.includes(update.entityId))
        if (blocked.length > 0) {
          return problem(409, 'entity-blocked', {
            entities: blocked.map((update) => ({
              entityId: update.entityId,
              entityName: state.entities[update.entityId]?.name ?? '',
              issues: [{ id: 'a0000000-0000-4000-8000-000000000001', field: 'Owner', label: 'Owner', kind: 'mandatory-missing', solutionId: 'b0000000-0000-4000-8000-000000000001', solutionName: 'CRM Replacement' }],
            })),
          })
        }
        // the changed entities must be complete (strict), as the server requires
        const incomplete = updates.flatMap((update, index) => {
          const entity = state.entities[update.entityId]
          if (!entity) return []
          const after = { ...entity, values: { ...entity.values } }
          for (const property of update.properties ?? []) {
            if (property.value === '') delete after.values[property.propertyDefId]
            else after.values[property.propertyDefId] = property.value
          }
          return mandatoryMissing(after).length > 0 ? [{ field: `entityUpdates[${index}].properties`, code: 'MANDATORY_MISSING', message: `The mandatory property ${mandatoryMissing(after)[0]} has no value.` }] : []
        })
        if (incomplete.length > 0) {
          return problem(422, 'validation-failed', { errors: incomplete, invalidFields: incomplete.map((error) => error.field) })
        }
        state.saves.push(structuredClone(request))
        for (const update of updates) {
          const entity = state.entities[update.entityId]
          if (!entity) continue
          if (update.name !== undefined) entity.name = update.name
          if (update.description !== undefined) entity.description = update.description
          for (const property of update.properties ?? []) {
            if (property.value === '') delete entity.values[property.propertyDefId]
            else entity.values[property.propertyDefId] = property.value
          }
          entity.version += 1
        }
        const created = new Map<string, { id: number; name: string; metatypeId: string; values: Record<string, string> }>()
        for (const entity of request.newEntities ?? []) {
          entityCounter += 1
          const values = Object.fromEntries((entity.properties ?? []).map((property) => [property.propertyDefId, property.value]))
          created.set(entity.key, { id: entityCounter, name: entity.name, metatypeId: entity.metatypeId, values })
          state.entities[entityCounter] = { id: entityCounter, name: entity.name, description: entity.description ?? '', metatypeId: entity.metatypeId, version: 1, values }
        }
        state.shapes = request.shapes.map((entry) => {
          const own = entry.newEntityKey ? created.get(entry.newEntityKey) : undefined
          const known = state.entities[entry.entityId ?? -1] ?? ENTITIES.map((entity) => ({ id: entity.id, name: entity.name, metatypeId: entity.metatype.id })).find((entity) => entity.id === entry.entityId)
          const entity = own
            ? { id: own.id, name: own.name, metatypeId: own.metatypeId }
            : { id: entry.entityId!, name: known?.name ?? `Entity ${entry.entityId}`, metatypeId: known?.metatypeId ?? METATYPE_COMPONENT }
          const saved = state.entities[entity.id]
          const missing = own
            ? (options.missingMandatory?.[own.metatypeId] ?? []).filter((name) => !Object.keys(own.values).some((id) => DEFINITIONS[id]?.name === name))
            : saved
              ? mandatoryMissing(saved)
              : []
          return { id: entry.id, entity, x: entry.x, y: entry.y, width: entry.width, height: entry.height, missingMandatory: missing }
        })
        // the connections created with the save: named after their ends unless named, incomplete without a mandatory value
        const createdConnections = new Map<string, { id: number; metatypeId: string; values: Record<string, string> }>()
        const entityOfShape = (shapeId: string) => state.shapes.find((entry) => entry.id === shapeId)?.entity
        for (const connection of request.newConnections ?? []) {
          const first = (request.edges ?? []).find((entry) => entry.newConnectionKey === connection.key)
          entityCounter += 1
          const values = Object.fromEntries((connection.properties ?? []).map((property) => [property.propertyDefId, property.value]))
          const name = connection.name ?? `${entityOfShape(first?.sourceShapeId ?? '')?.name} → ${entityOfShape(first?.targetShapeId ?? '')?.name}`
          createdConnections.set(connection.key, { id: entityCounter, metatypeId: connection.metatypeId, values })
          state.entities[entityCounter] = { id: entityCounter, name, description: connection.description ?? '', metatypeId: connection.metatypeId, version: 1, values }
        }
        state.edges = (request.edges ?? []).map((entry) => {
          const own = entry.newConnectionKey ? createdConnections.get(entry.newConnectionKey) : undefined
          const connection = own ? state.entities[own.id]! : state.entities[entry.connectionId ?? -1]
          return {
            id: entry.id,
            connection: { id: connection?.id ?? entry.connectionId!, name: connection?.name ?? `Connection ${entry.connectionId}`, metatypeId: connection?.metatypeId ?? METATYPE_FLOW },
            sourceShapeId: entry.sourceShapeId,
            targetShapeId: entry.targetShapeId,
            points: entry.points,
            missingMandatory: connection ? mandatoryMissing(connection) : [],
          }
        })
        found.version += 1
        return respond(200, { diagram: found, viewpoint: viewpointConfig(), shapes: state.shapes, edges: state.edges }, { ETag: `"${found.version}"` })
      }
    }
    const detail = /^\/api\/v1\/views\/diagrams\/([^/]+)\/entities\/(\d+)$/.exec(path)
    if (detail && method === 'GET') {
      const entity = state.entities[Number(detail[2])]
      if (!entity || (!state.shapes.some((entry) => entry.entity.id === entity.id) && !state.edges.some((entry) => entry.connection.id === entity.id))) {
        return problem(404, 'not-found')
      }
      const writer = ((options.me as { permissions: { permission: string }[] }).permissions ?? []).some((entry) => entry.permission === 'entity:write')
      const type = ENTITIES.find((candidate) => candidate.metatype.id === entity.metatypeId)?.metatype ?? viewpointConfig().allowedRelations.find((candidate) => candidate.id === entity.metatypeId)
      const details: DiagramEntityDetails = {
        id: entity.id,
        name: entity.name,
        description: entity.description,
        metatype: type ?? { id: entity.metatypeId, name: 'Entity', abbreviation: '' },
        version: entity.version,
        properties: Object.entries(entity.values).map(([defId, text]) => propertyValue(defId, text, options.redactedProperties?.includes(defId) ?? false)) as DiagramEntityDetails['properties'],
        fields: (MAPPINGS[entity.metatypeId] ?? []).map((entry) => ({
          propertyDefId: entry.propertyDefinition.id,
          readable: !options.redactedProperties?.includes(entry.propertyDefinition.id),
          writable: writer && !options.readOnlyProperties?.includes(entry.propertyDefinition.id),
        })),
        missingMandatory: mandatoryMissing(entity),
      }
      return respond(200, details)
    }
    const query = /^\/api\/v1\/views\/diagrams\/([^/]+)\/connections\/query$/.exec(path)
    if (query && method === 'POST') {
      const { entityIds, shownEntityIds } = body as unknown as { entityIds: number[]; shownEntityIds: number[] }
      state.queries.push({ entityIds, shownEntityIds })
      const others = [...entityIds, ...shownEntityIds]
      const items = (options.offers ?? []).filter(
        (offer) => (entityIds.includes(offer.sourceEntityId) && others.includes(offer.targetEntityId)) || (entityIds.includes(offer.targetEntityId) && others.includes(offer.sourceEntityId)),
      )
      return respond(200, { items, truncated: false })
    }
    if (path === '/api/v1/metamodel/allowed-connections' && method === 'GET') {
      return respond(200, (options.allowed ?? allowedConnections)(url.searchParams.get('sourceMetatypeId') ?? '', url.searchParams.get('targetMetatypeId') ?? ''))
    }
    const targets = /^\/api\/v1\/views\/diagrams\/([^/]+)\/reference-targets$/.exec(path)
    if (targets && method === 'GET') {
      const q = (url.searchParams.get('q') ?? '').toLowerCase()
      const items = ENTITIES.filter((entity) => entity.metatype.id === METATYPE_COMPONENT && entity.name.toLowerCase().includes(q))
      return respond(200, { items, page: { page: 0, pageSize: 20, totalElements: items.length, totalPages: 1 } })
    }
    const metatype = /^\/api\/v1\/metamodel\/metatypes\/([^/]+)$/.exec(path)
    if (metatype && method === 'GET') {
      const type = [...viewpointConfig().allowedMetatypes, ...viewpointConfig().allowedRelations].find((candidate) => candidate.id === metatype[1])
      return type ? respond(200, { ...type, propertyMappings: MAPPINGS[type.id] ?? [] }) : problem(404, 'not-found')
    }
    const search = /^\/api\/v1\/views\/diagrams\/([^/]+)\/entities$/.exec(path)
    if (search && method === 'GET') {
      const q = (url.searchParams.get('q') ?? '').toLowerCase()
      const size = options.searchPageSize ?? Number(url.searchParams.get('pageSize') ?? 20)
      const index = Number(url.searchParams.get('page') ?? 0)
      const all = [...(options.entities ?? ENTITIES)].filter((entity) => entity.name.toLowerCase().includes(q)).sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
      return respond(200, {
        items: all.slice(index * size, (index + 1) * size),
        page: { page: index, pageSize: size, totalElements: all.length, totalPages: Math.ceil(all.length / size) },
      })
    }
    if (path === '/api/v1/diagrams' && method === 'GET') {
      return respond(200, page(state.diagrams, url))
    }
    if (path === '/api/v1/diagrams/deleted' && method === 'GET') {
      return respond(200, page(state.deleted, url))
    }
    if (path === '/api/v1/diagrams' && method === 'POST') {
      const name = String(body?.name ?? '').trim()
      if (taken(name)) {
        return problem(422, 'diagram-name-taken', { invalidFields: ['name'] })
      }
      const viewpoint = VIEWPOINTS.find((entry) => entry.id === body?.viewpointId)
      if (!viewpoint) {
        return problem(422, 'validation-failed', { invalidFields: ['viewpointId'] })
      }
      counter += 1
      const created = diagram({
        id: `a1000000-0000-4000-8000-${String(counter).padStart(12, '0')}`,
        name,
        description: (body?.description as string | null) ?? null,
        viewpoint,
      })
      state.diagrams.push(created)
      return respond(201, created, { ETag: '"1"' })
    }
    const restore = /^\/api\/v1\/diagrams\/([^/]+)\/restore$/.exec(path)
    if (restore && method === 'POST') {
      const found = state.deleted.find((entry) => entry.id === restore[1])
      if (!found) {
        return state.diagrams.some((entry) => entry.id === restore[1]) ? problem(409, 'diagram-not-deleted') : problem(404, 'not-found')
      }
      if (version !== found.version) {
        return problem(412, 'stale-version', { currentVersion: found.version })
      }
      if (taken(found.name)) {
        return problem(422, 'diagram-name-taken', { invalidFields: ['name'] })
      }
      state.deleted.splice(state.deleted.indexOf(found), 1)
      const { deletedAt: _at, deletedBy: _by, ...restored } = found
      const next = { ...restored, version: found.version + 1 }
      state.diagrams.push(next)
      return respond(200, next, { ETag: `"${next.version}"` })
    }
    const one = /^\/api\/v1\/diagrams\/([^/]+)$/.exec(path)
    if (one) {
      const found = state.diagrams.find((entry) => entry.id === one[1])
      if (!found) {
        return problem(404, 'not-found')
      }
      if (method === 'GET') {
        return respond(200, found, { ETag: `"${found.version}"` })
      }
      if (version !== found.version) {
        return problem(412, 'stale-version', { currentVersion: found.version })
      }
      if (method === 'PUT') {
        const name = String(body?.name ?? '').trim()
        if (taken(name, found.id)) {
          return problem(422, 'diagram-name-taken', { invalidFields: ['name'] })
        }
        Object.assign(found, { name, description: (body?.description as string | null) ?? null, version: found.version + 1 })
        return respond(200, found, { ETag: `"${found.version}"` })
      }
      if (method === 'DELETE') {
        state.diagrams.splice(state.diagrams.indexOf(found), 1)
        state.deleted.push({ ...found, version: found.version + 1, deletedAt: '2026-11-19T11:00:00Z', deletedBy: { principalType: 'person', id: 7, displayName: 'Anna' } })
        return respond(204, undefined)
      }
    }
    return problem(404, 'not-found')
  }) as DiagramApi
  api.state = state
  return api
}
