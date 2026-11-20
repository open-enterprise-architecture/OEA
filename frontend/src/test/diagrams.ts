import type { DeletedDiagram, Diagram } from '../api/diagrams'
import type { DiagramEntity, DiagramShape, DiagramView, DiagramViewWrite } from '../api/diagramView'
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
    sizes: [{ metatypeId: METATYPE_COMPONENT, width: 120, height: 40, length: null }],
    effectiveMappings: [
      { metatypeId: METATYPE_COMPONENT, notationElement: 'generic:Box', width: 120, height: 40, length: null, sizeStored: true, visualHint: '#2563eb', fallback: true },
      { metatypeId: METATYPE_PROCESS, notationElement: 'generic:Box', width: 90, height: 30, length: null, sizeStored: false, visualHint: null, fallback: true },
    ],
    allowedMetatypes: [
      { id: METATYPE_COMPONENT, name: 'Application Component', abbreviation: 'AC' },
      { id: METATYPE_PROCESS, name: 'Business Process', abbreviation: 'BP' },
    ],
    allowedRelations: [],
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

/** The view of a diagram with the given shapes, for the component specs of the canvas. */
export function diagramView(shapes: DiagramShape[] = [], overrides: Partial<Diagram> = {}): DiagramView {
  return { diagram: diagram(overrides), viewpoint: viewpointConfig(), shapes }
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
  /** the entities "Add existing" finds */
  entities?: DiagramEntity[]
  /** the names of the mandatory properties a new entity of a metatype misses (an entity created on the canvas is incomplete) */
  missingMandatory?: Record<string, string[]>
  /** a save answers this status instead of saving (412 or 422 with `errors`) */
  rejectSave?: { status: number; errors?: { field: string; code: string; message: string }[] }
  /** the page size of the fake entity search */
  searchPageSize?: number
}

export type DiagramApi = typeof fetch & {
  state: { diagrams: Diagram[]; deleted: DeletedDiagram[]; shapes: DiagramShape[]; saves: DiagramViewWrite[] }
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
    saves: [] as DiagramViewWrite[],
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
        return respond(200, { diagram: found, viewpoint: viewpointConfig(), shapes: state.shapes }, { ETag: `"${found.version}"` })
      }
      if (method === 'PUT') {
        if (version !== found.version) {
          return problem(412, 'stale-version', { currentVersion: found.version })
        }
        const request = body as unknown as DiagramViewWrite
        if (options.rejectSave) {
          return problem(options.rejectSave.status, 'validation-failed', { errors: options.rejectSave.errors ?? [], invalidFields: (options.rejectSave.errors ?? []).map((error) => error.field) })
        }
        state.saves.push(structuredClone(request))
        const created = new Map<string, { id: number; name: string; metatypeId: string }>()
        for (const entity of request.newEntities ?? []) {
          entityCounter += 1
          created.set(entity.key, { id: entityCounter, name: entity.name, metatypeId: entity.metatypeId })
        }
        state.shapes = request.shapes.map((entry) => {
          const own = entry.newEntityKey ? created.get(entry.newEntityKey) : undefined
          const known = ENTITIES.find((entity) => entity.id === entry.entityId)
          const entity = own
            ? { id: own.id, name: own.name, metatypeId: own.metatypeId }
            : { id: entry.entityId!, name: known?.name ?? `Entity ${entry.entityId}`, metatypeId: known?.metatype.id ?? METATYPE_COMPONENT }
          return { id: entry.id, entity, x: entry.x, y: entry.y, width: entry.width, height: entry.height, missingMandatory: own ? (options.missingMandatory?.[own.metatypeId] ?? []) : [] }
        })
        found.version += 1
        return respond(200, { diagram: found, viewpoint: viewpointConfig(), shapes: state.shapes }, { ETag: `"${found.version}"` })
      }
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
