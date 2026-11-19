import type { DeletedDiagram, Diagram } from '../api/diagrams'
import type { Viewpoint } from '../api/metamodel'
import type { RecordedRequest } from './fetch'

/** Test and story data and a fake backend for the diagram screens (US-074, SCR-067). */
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
}

export type DiagramApi = typeof fetch & { state: { diagrams: Diagram[]; deleted: DeletedDiagram[] } }

/**
 * A fake backend of the diagram API with state: the list is ordered by name ignoring case and
 * filtered by `q`, the writes check `If-Match`, names are unique, and a deleted diagram moves to
 * the deleted list until it is restored.
 */
export function diagramApi(options: DiagramApiOptions): DiagramApi {
  const state = {
    diagrams: (options.diagrams ?? DIAGRAMS).map((entry) => structuredClone(entry)),
    deleted: (options.deleted ?? []).map((entry) => structuredClone(entry)),
  }
  let counter = 100
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
