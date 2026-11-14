import type { EntityVersion, EntityVersionChange, HistoryActor, HistoryVisibilityEntry } from '../api/history'

// A fake backend for SCR-026 (us-098-entity-history): the history of an entity with the period and
// person filters, paging by cursor, 404 for an unknown entity, and the history visibility of the
// section on SCR-032 with the 422 of an unknown role.

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 12, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-history',
  }
}

export const ME_HISTORY_READER = me('Anna', ['portal:read', 'entity:read'])
export const ME_PORTAL_ONLY = me('Clara', ['portal:read'])

export const MICHAEL: HistoryActor = { id: 21, name: 'Michael' }
export const KURT: HistoryActor = { id: 22, name: 'Kurt' }

export const HISTORY_IDS = {
  crm: 101,
  billing: 102,
  large: 103,
  cost: 'fd200000-0000-4000-8000-000000000001',
  status: 'fd200000-0000-4000-8000-000000000002',
}

const system = (field: string, label: string): EntityVersionChange => ({ field, label, isSystemField: true, restricted: false })
const property = (field: string, label: string, restricted = false): EntityVersionChange => ({ field, label, isSystemField: false, restricted })

/** Version n at 2026-11-02T08:00Z + n hours. */
export function at(version: number): string {
  return new Date(Date.UTC(2026, 10, 2, 8 + version)).toISOString().replace('.000Z', 'Z')
}

function version(number: number, actor: HistoryActor, changes: EntityVersionChange[] = []): EntityVersion {
  return { version: number, changedAt: at(number), changedBy: actor, changes }
}

/** CRM System: created by Kurt, then changes by Michael and Kurt; version 4 changed a restricted field. */
export const CRM_VERSIONS: EntityVersion[] = [
  version(5, MICHAEL, [property(HISTORY_IDS.status, 'Lifecycle Status')]),
  version(4, MICHAEL, [system('name', 'Name'), property(HISTORY_IDS.cost, 'Investment Cost', true)]),
  version(3, KURT, [system('description', 'Description')]),
  version(2, MICHAEL, [property(HISTORY_IDS.status, 'Lifecycle Status')]),
  version(1, KURT),
]

/** An entity with `count` versions, all by Michael. */
export function manyVersions(count: number): EntityVersion[] {
  return Array.from({ length: count }, (_, index) => version(count - index, MICHAEL, count - index > 1 ? [system('name', 'Name')] : []))
}

interface HistoryEntity {
  name: string
  versions: EntityVersion[]
}

export function historyApi(
  options: {
    me?: unknown
    entities?: Record<number, HistoryEntity>
    visibility?: HistoryVisibilityEntry[]
  } = {},
) {
  const entities: Record<number, HistoryEntity> = options.entities ?? {
    [HISTORY_IDS.crm]: { name: 'CRM System', versions: CRM_VERSIONS },
    [HISTORY_IDS.billing]: { name: 'Billing', versions: [version(1, KURT)] },
    [HISTORY_IDS.large]: { name: 'Large System', versions: manyVersions(51) },
  }
  let visibility = [...(options.visibility ?? [])]
  const queries: URLSearchParams[] = []
  const puts: HistoryVisibilityEntry[][] = []
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' } })
  const problem = (status: number, invalidFields: string[] = []) => ({
    type: status === 404 ? 'urn:oea:problem:not-found' : 'urn:oea:problem:validation-failed',
    title: 'Rejected',
    status,
    invalidFields,
  })
  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const method = init?.method ?? 'GET'
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_HISTORY_READER)
    }
    const match = /^\/api\/v1\/entities\/(\d+)\/history$/.exec(url.pathname)
    if (match) {
      queries.push(url.searchParams)
      const entity = entities[Number(match[1])]
      if (!entity) return json(problem(404), 404)
      const params = url.searchParams
      const from = params.get('from')
      const to = params.get('to')
      if (from && to && from > to) return json(problem(400, ['from']), 400)
      const actorId = params.get('actorId')
      const matching = entity.versions.filter(
        (candidate) =>
          (!actorId || String(candidate.changedBy.id) === actorId) &&
          (!from || candidate.changedAt >= from) &&
          (!to || candidate.changedAt <= to),
      )
      const page = Number(params.get('page') ?? '0')
      const pageSize = Number(params.get('pageSize') ?? '50')
      const cursor = params.get('cursor')
      if (cursor && cursor !== `hcursor-${page}`) return json(problem(400, ['cursor']), 400)
      const totalPages = Math.ceil(matching.length / pageSize)
      const created = entity.versions.find((candidate) => candidate.version === 1)!
      const actors = [...new Map(entity.versions.map((candidate) => [candidate.changedBy.id, candidate.changedBy])).values()].sort((a, b) =>
        a.name.localeCompare(b.name),
      )
      return json({
        entityId: Number(match[1]),
        entityName: entity.name,
        created: { changedAt: created.changedAt, changedBy: created.changedBy },
        actors,
        versions: matching.slice(page * pageSize, (page + 1) * pageSize),
        page: { page, pageSize, totalElements: matching.length, totalPages },
        nextCursor: page + 1 < totalPages ? `hcursor-${page + 1}` : null,
      })
    }
    if (url.pathname === '/api/v1/admin/history-visibility' && method === 'GET') {
      return json({ entries: visibility })
    }
    if (url.pathname === '/api/v1/admin/history-visibility' && method === 'PUT') {
      const entries = (JSON.parse(String(init?.body)) as { entries: HistoryVisibilityEntry[] }).entries
      puts.push(entries)
      const invalid = entries.flatMap((entry, index) => (entry.roleId === 'no-such-role' ? [`entries[${index}].roleId`] : []))
      if (invalid.length > 0) return json(problem(422, invalid), 422)
      for (const entry of entries) {
        visibility = visibility.filter((candidate) => !(candidate.roleId === entry.roleId && candidate.metatypeId === entry.metatypeId))
        if (entry.mode === 'hide') visibility.push(entry)
      }
      return json({ entries: visibility })
    }
    return json(problem(404), 404)
  }
  return { fetch: fetchFn, queries, puts }
}
