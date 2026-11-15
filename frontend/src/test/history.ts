import type {
  EntityVersion,
  EntityVersionChange,
  EntityVersionDataType,
  EntityVersionField,
  FieldDiff,
  HistoryActor,
  HistoryVisibilityEntry,
  RestoreField,
} from '../api/history'

// A fake backend for SCR-026 (us-098-entity-history, us-099-version-diff): the history of an
// entity with the period and person filters, paging by cursor, 404 for an unknown entity, the
// complete state of a version and the net diff of two versions, and the history visibility of
// the section on SCR-032 with the 422 of an unknown role. The restore (us-101-entity-restore): the
// preview of a restore with a configurable set of fields and warnings, and the restore itself with
// `If-Match`, 412 when the entity changed meanwhile and a new version that names its target.

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
export const ME_RESTORER = me('Kurt', ['portal:read', 'entity:read', 'entity:write', 'entity:restore'])
export const ME_WRITER = me('Wanda', ['portal:read', 'entity:read', 'entity:write'])

export const MICHAEL: HistoryActor = { id: 21, name: 'Michael' }
export const KURT: HistoryActor = { id: 22, name: 'Kurt' }

export const HISTORY_IDS = {
  crm: 101,
  billing: 102,
  large: 103,
  cost: 'fd200000-0000-4000-8000-000000000001',
  status: 'fd200000-0000-4000-8000-000000000002',
  owner: 'fd200000-0000-4000-8000-000000000003',
  goLive: 'fd200000-0000-4000-8000-000000000004',
}

const system = (field: string, label: string): EntityVersionChange => ({ field, label, isSystemField: true, restricted: false })
const property = (field: string, label: string, restricted = false): EntityVersionChange => ({ field, label, isSystemField: false, restricted })

/** Version n at 2026-11-02T08:00Z + n hours. */
export function at(version: number): string {
  return new Date(Date.UTC(2026, 10, 2, 8 + version)).toISOString().replace('.000Z', 'Z')
}

function version(
  number: number,
  actor: HistoryActor,
  changes: EntityVersionChange[] = [],
  restoredFromVersion: number | null = null,
  reason: string | null = null,
): EntityVersion {
  return { version: number, changedAt: at(number), changedBy: actor, changes, restoredFromVersion, reason }
}

/** CRM System: created by Kurt, then changes by Michael and Kurt; version 4 changed a restricted field. */
export const CRM_VERSIONS: EntityVersion[] = [
  version(5, MICHAEL, [property(HISTORY_IDS.status, 'Lifecycle Status')]),
  version(4, MICHAEL, [system('name', 'Name'), property(HISTORY_IDS.cost, 'Investment Cost', true)]),
  version(3, KURT, [system('description', 'Description')]),
  version(2, MICHAEL, [property(HISTORY_IDS.status, 'Lifecycle Status')]),
  version(1, KURT),
]

/** CRM System after a restore: version 6 restores version 3 with a reason. */
export function restoredVersions(): EntityVersion[] {
  return [
    version(6, KURT, [system('name', 'Name'), property(HISTORY_IDS.status, 'Lifecycle Status')], 3, 'Wrong lifecycle status reverted'),
    ...CRM_VERSIONS,
  ]
}

const restoreField = (
  field: string,
  label: string,
  dataType: EntityVersionDataType,
  currentValue: string | null,
  targetValue: string | null,
  reason: RestoreField['reason'] = null,
  redacted = false,
): RestoreField => ({
  field,
  label,
  isSystemField: dataType === 'system',
  dataType,
  currentValue: redacted ? null : currentValue,
  targetValue: redacted ? null : targetValue,
  redacted,
  restore: reason === null,
  reason,
})

/** The preview of a restore of CRM System (version 5) to version 3: name and lifecycle status change. */
export const PREVIEW_FIELDS: RestoreField[] = [
  restoreField('name', 'Name', 'system', 'CRM System', 'CRM'),
  restoreField(HISTORY_IDS.status, 'Lifecycle Status', 'enum', 'Retiring', 'Active'),
]

/** As PREVIEW_FIELDS, with a write-protected restricted field and a value that is no longer valid. */
export const PREVIEW_WITH_KEPT: RestoreField[] = [
  ...PREVIEW_FIELDS,
  restoreField(HISTORY_IDS.cost, 'Investment Cost', 'integer', null, null, 'write-protected', true),
  restoreField(HISTORY_IDS.goLive, 'Go-live', 'date', '2026-12-01', '2026-06-01', 'invalid-value'),
]

/** Only fields that are kept: nothing can be restored. */
export const PREVIEW_NOTHING: RestoreField[] = [restoreField(HISTORY_IDS.cost, 'Investment Cost', 'integer', null, null, 'write-protected', true)]

export const OWNER_MISSING = [{ field: 'properties[Owner]', code: 'MANDATORY_MISSING', message: 'The mandatory property Owner has no value.' }]

/** An entity with `count` versions, all by Michael. */
export function manyVersions(count: number): EntityVersion[] {
  return Array.from({ length: count }, (_, index) => version(count - index, MICHAEL, count - index > 1 ? [system('name', 'Name')] : []))
}

function field(field: string, label: string, dataType: EntityVersionDataType, value: string | null, restricted = false): EntityVersionField {
  return { field, label, isSystemField: dataType === 'system', dataType, value: restricted ? null : value, restricted }
}

/**
 * The state of CRM System at a version, matching CRM_VERSIONS: the name is "CRM" up to version 3,
 * the description is set in version 3, the lifecycle status is Planned, Active from version 2 and
 * Retiring from version 5; Owner is mapped and empty; Investment Cost is restricted.
 */
export function crmState(version: number): EntityVersionField[] {
  return [
    field('name', 'Name', 'system', version >= 4 ? 'CRM System' : 'CRM'),
    field('description', 'Description', 'system', version >= 3 ? 'Customer relationship management' : null),
    field('isLogical', 'Logical', 'system', 'true'),
    field('metatypeId', 'Metatype', 'system', 'Application Component'),
    field(HISTORY_IDS.status, 'Lifecycle Status', 'enum', version >= 5 ? 'Retiring' : version >= 2 ? 'Active' : 'Planned'),
    field(HISTORY_IDS.owner, 'Owner', 'string', null),
    field(HISTORY_IDS.cost, 'Investment Cost', 'integer', null, true),
    field(HISTORY_IDS.goLive, 'Go-live', 'date', '2026-12-01'),
  ]
}

interface HistoryEntity {
  name: string
  versions: EntityVersion[]
  /** The fields of a version; without it, only the name. */
  state?: (version: number) => EntityVersionField[]
}

export function historyApi(
  options: {
    me?: unknown
    entities?: Record<number, HistoryEntity>
    visibility?: HistoryVisibilityEntry[]
    /** The fields and warnings of a restore preview; defaults to PREVIEW_FIELDS without warnings. */
    preview?: { fields: RestoreField[]; warnings?: { field: string; code: string; message: string }[] }
    /** The version the backend has when another person changed the entity after the history was read. */
    changedMeanwhileTo?: number
  } = {},
) {
  const entities: Record<number, HistoryEntity> = options.entities ?? {
    [HISTORY_IDS.crm]: { name: 'CRM System', versions: CRM_VERSIONS, state: crmState },
    [HISTORY_IDS.billing]: { name: 'Billing', versions: [version(1, KURT)] },
    [HISTORY_IDS.large]: { name: 'Large System', versions: manyVersions(51) },
  }
  let visibility = [...(options.visibility ?? [])]
  const queries: URLSearchParams[] = []
  const puts: HistoryVisibilityEntry[][] = []
  const diffs: string[] = []
  const states: number[] = []
  const previews: number[] = []
  const restores: { ifMatch: string | null; body: { targetVersion: number; reason?: string } }[] = []
  let meanwhile = options.changedMeanwhileTo
  let previewsUntilMeanwhile = meanwhile !== undefined ? 1 : 0
  const stateOf = (entity: HistoryEntity, version: number) => entity.state?.(version) ?? [field('name', 'Name', 'system', entity.name)]
  const latest = (entity: HistoryEntity) => Math.max(...entity.versions.map((candidate) => candidate.version))
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
    const versionMatch = /^\/api\/v1\/entities\/(\d+)\/versions\/(\d+)$/.exec(url.pathname)
    if (versionMatch && method === 'GET') {
      const entity = entities[Number(versionMatch[1])]
      const number = Number(versionMatch[2])
      states.push(number)
      if (!entity || number < 1 || number > latest(entity)) return json(problem(404), 404)
      const header = entity.versions.find((candidate) => candidate.version === number)!
      return json({
        entityId: Number(versionMatch[1]),
        entityName: entity.name,
        version: number,
        changedAt: header.changedAt,
        changedBy: header.changedBy,
        fields: stateOf(entity, number),
      })
    }
    const restoreMatch = /^\/api\/v1\/entities\/(\d+)\/restore$/.exec(url.pathname)
    if (restoreMatch) {
      const id = Number(restoreMatch[1])
      const entity = entities[id]
      if (!entity) return json(problem(404), 404)
      // the first preview still sees the version of the history; the change meanwhile follows
      const current = meanwhile !== undefined && previewsUntilMeanwhile === 0 ? meanwhile : latest(entity)
      const preview = options.preview ?? { fields: PREVIEW_FIELDS }
      if (method === 'GET') {
        const target = Number(url.searchParams.get('targetVersion'))
        previews.push(target)
        if (previewsUntilMeanwhile > 0) previewsUntilMeanwhile -= 1
        if (target > current) return json(problem(404), 404)
        if (target < 1 || target === current) return json(problem(400, ['targetVersion']), 400)
        return json({ entityId: id, currentVersion: current, targetVersion: target, fields: preview.fields, warnings: preview.warnings ?? [] })
      }
      const body = JSON.parse(String(init?.body)) as { targetVersion: number; reason?: string }
      const ifMatch = new Headers(init?.headers).get('If-Match')
      restores.push({ ifMatch, body })
      if (ifMatch !== `"${current}"`) {
        return json({ type: 'urn:oea:problem:stale-version', title: 'Changed meanwhile', status: 412, currentVersion: current }, 412)
      }
      const next = current + 1
      entity.versions = [version(next, KURT, [], body.targetVersion, body.reason ?? null), ...entity.versions]
      meanwhile = undefined
      return json({
        entityId: id,
        version: next,
        restoredFromVersion: body.targetVersion,
        restored: preview.fields.filter((field) => field.restore).map((field) => ({ field: field.field, label: field.label, reason: null })),
        notRestored: preview.fields.filter((field) => !field.restore).map((field) => ({ field: field.field, label: field.label, reason: field.reason })),
        warnings: preview.warnings ?? [],
      })
    }
    const diffMatch = /^\/api\/v1\/entities\/(\d+)\/diff$/.exec(url.pathname)
    if (diffMatch) {
      const entity = entities[Number(diffMatch[1])]
      const from = Number(url.searchParams.get('from'))
      const to = Number(url.searchParams.get('to'))
      diffs.push(`from=${from}&to=${to}`)
      if (!entity) return json(problem(404), 404)
      if (from < 1 || from >= to) return json(problem(400, ['from']), 400)
      if (to > latest(entity)) return json(problem(404), 404)
      const before = new Map(stateOf(entity, from).map((candidate) => [candidate.field, candidate]))
      // a restricted field is listed when a version in between changed it
      const changedBetween = new Set(
        entity.versions.filter((candidate) => candidate.version > from && candidate.version <= to).flatMap((candidate) => candidate.changes.map((change) => change.field)),
      )
      const changes: FieldDiff[] = stateOf(entity, to).flatMap((after): FieldDiff[] => {
        const old = before.get(after.field)
        if (after.restricted) {
          return changedBetween.has(after.field)
            ? [{ field: after.field, label: after.label, isSystemField: false, dataType: after.dataType, oldValue: null, newValue: null, redacted: true }]
            : []
        }
        if ((old?.value ?? null) === after.value) return []
        return [{ field: after.field, label: after.label, isSystemField: after.isSystemField, dataType: after.dataType, oldValue: old?.value ?? null, newValue: after.value, redacted: false }]
      })
      return json({ entityId: Number(diffMatch[1]), from, to, changes })
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
        currentVersion: latest(entity),
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
  return { fetch: fetchFn, queries, puts, diffs, states, previews, restores }
}
