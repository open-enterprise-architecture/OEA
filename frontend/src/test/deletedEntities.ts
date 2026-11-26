import type { DeletedEntity } from '../api/deletedEntities'
import { METATYPES } from './catalogs'
import type { RecordedRequest } from './fetch'

/** Test and story data and a fake backend for the page "Deleted entities" (SCR-068, s17-deleted-entities). */
function me(displayName: string, id: number, permissions: string[]) {
  return {
    principal: { principalType: 'person', id, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-deleted',
  }
}

/** Holds `entity:restore`: lists and restores the deleted entities. */
export const ME_DELETED_RESTORER = me('Petra', 8, ['portal:read', 'entity:read', 'entity:write', 'entity:restore'])
/** Holds `entity:write` but not `entity:restore`. */
export const ME_DELETED_WRITER = me('Anna', 7, ['portal:read', 'entity:read', 'entity:write'])

export const COMPONENT = METATYPES[1]!
const KURT = { principalType: 'person', id: 9, displayName: 'Kurt' } as const
const CONNECTION = { id: 'c1000000-0000-4000-8000-0000000000f1', name: 'Information Flow', abbreviation: 'IF' }

export function deletedEntity(overrides: Partial<DeletedEntity> & Pick<DeletedEntity, 'id' | 'name'>): DeletedEntity {
  return {
    metatype: { id: COMPONENT.id, name: COMPONENT.name, abbreviation: 'AC' },
    isConnection: false,
    version: 1,
    deletedAt: '2026-11-20T10:00:00Z',
    deletedBy: KURT,
    deletedWith: null,
    ...overrides,
  }
}

/** "ERP" deleted with its child "ERP Finance" and the connection "ERP to CRM" (the other endpoint, "CRM", is active). */
export const CASCADE: DeletedEntity[] = [
  deletedEntity({ id: 11, name: 'ERP' }),
  deletedEntity({ id: 12, name: 'ERP Finance', deletedWith: { id: 11, name: 'ERP' } }),
  deletedEntity({
    id: 13,
    name: 'ERP to CRM',
    metatype: CONNECTION,
    isConnection: true,
    source: { id: 11, name: 'ERP' },
    target: { id: 20, name: 'CRM' },
    deletedWith: { id: 11, name: 'ERP' },
  }),
]

export interface DeletedEntitiesApiOptions {
  me: unknown
  entries?: DeletedEntity[]
  recorded?: RecordedRequest[]
  /** The page size of the fake; the real default is 50. */
  pageSize?: number
  /** A restore of the entity answers with this problem instead of restoring it. */
  restoreFails?: { status: number; type: string }
  /** What stays deleted when an entity with a cascade is restored: its connections with this reason. */
  notRestored?: { entityId: number; reason: string }[]
}

function respond(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

/**
 * A fake backend: the list with its filters and paging (the cursor is `cursor-{page}`), and the
 * restore, which brings back the entry with the entries deleted with it, except those named in
 * `notRestored`.
 */
export function deletedEntitiesApi(options: DeletedEntitiesApiOptions): typeof fetch {
  let entries = (options.entries ?? CASCADE).map((entry) => structuredClone(entry))
  return async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://oea.test')
    options.recorded?.push({ path: url.pathname + url.search, init })
    const method = init?.method ?? 'GET'
    if (url.pathname === '/api/v1/me') {
      return respond(200, options.me)
    }
    if (url.pathname === '/api/v1/metamodel/metatypes') {
      return respond(200, METATYPES)
    }
    if (url.pathname === '/api/v1/entities/deleted' && method === 'GET') {
      const permissions = ((options.me ?? {}) as { permissions?: { permission: string }[] }).permissions ?? []
      if (!permissions.some((entry) => entry.permission === 'entity:restore')) {
        return respond(403, { type: 'urn:oea:problem:forbidden', title: 'Forbidden', status: 403 })
      }
      const q = (url.searchParams.get('q') ?? '').toLowerCase()
      const metatypeId = url.searchParams.get('metatypeId')
      const size = options.pageSize ?? Number(url.searchParams.get('pageSize') ?? 50)
      const page = Number(url.searchParams.get('page') ?? 0)
      const cursor = url.searchParams.get('cursor')
      if (cursor !== null && cursor !== `cursor-${page}`) {
        return respond(400, { type: 'urn:oea:problem:validation-failed', title: 'Invalid query', status: 400, invalidFields: ['cursor'] })
      }
      const matching = entries.filter((entry) => entry.name.toLowerCase().includes(q) && (metatypeId === null || entry.metatype.id === metatypeId))
      return respond(200, {
        items: matching.slice(page * size, (page + 1) * size),
        page: { page, pageSize: size, totalElements: matching.length, totalPages: Math.ceil(matching.length / size) },
        nextCursor: (page + 1) * size < matching.length ? `cursor-${page + 1}` : null,
      })
    }
    const undelete = /^\/api\/v1\/entities\/(\d+)\/undelete$/.exec(url.pathname)
    if (undelete && method === 'POST') {
      const id = Number(undelete[1])
      const entry = entries.find((candidate) => candidate.id === id)
      const given = (init?.headers as Record<string, string> | undefined)?.['If-Match']
      if (options.restoreFails) {
        return respond(options.restoreFails.status, { type: `urn:oea:problem:${options.restoreFails.type}`, title: 'Not restorable', status: options.restoreFails.status })
      }
      if (!entry) {
        return respond(409, { type: 'urn:oea:problem:entity-not-deleted', title: 'Not deleted', status: 409 })
      }
      if (given !== `"${entry.version}"`) {
        return respond(412, { type: 'urn:oea:problem:stale-version', title: 'Changed meanwhile', status: 412 })
      }
      const stays = new Set((options.notRestored ?? []).map((item) => item.entityId))
      const restored = entries.filter((candidate) => candidate.id === id || (candidate.deletedWith?.id === id && !stays.has(candidate.id)))
      entries = entries.filter((candidate) => !restored.includes(candidate))
      return respond(200, { restored: restored.map((item) => item.id), movedToTopLevel: [], notRestored: options.notRestored ?? [] })
    }
    return respond(404, { type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 })
  }
}
