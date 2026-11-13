import type {
  Solution,
  SolutionDelta,
  SolutionDeltaChange,
  SolutionDeltaValue,
  SolutionDiffEntry,
  SolutionDiffField,
  SolutionLandscapeEntity,
  SolutionLandscapeEntry,
} from '../api/solutions'

// A fake backend for SCR-060 and SCR-061 (us-038-create-solution, us-039-solution-baseline,
// us-040-entity-deltas): the solutions, creating one with the server-side name rule of REQ-038,
// reading one, its baseline with search, metatype filter, paging and cursors, and its deltas with
// the rules of a new entity, one delta per entity and the lock of a completed solution; the delta
// list and the diff paged by cursor with the counts per type (us-043-diff-view).

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 12, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-solutions',
  }
}

export const ME_SOLUTION_ARCHITECT = me('Michael', ['portal:read', 'entity:read', 'solution:write', 'solution-metamodel:write'])
export const ME_SOLUTION_VIEWER = me('Anna', ['portal:read', 'entity:read'])
export const ME_PORTAL_READER = me('Clara', ['portal:read'])

function solution(id: string, name: string, status: Solution['status'], ownerName: string, createdAt: string, description: string | null): Solution {
  return {
    id,
    name,
    status,
    ownerId: 12,
    ownerName,
    createdAt,
    description,
    fromPlateauId: null,
    toPlateauId: null,
    createdBy: { principalType: 'person', id: 12, displayName: ownerName },
  }
}

export const SOLUTIONS: Solution[] = [
  solution(
    '5d000000-0000-4000-8000-000000000001',
    'CRM Replacement',
    'draft',
    'Michael',
    '2026-11-10T09:30:00Z',
    'Replace the legacy CRM by a cloud CRM.\nScope: sales and service, not marketing.',
  ),
  solution(
    '5d000000-0000-4000-8000-000000000002',
    'ERP Extension Logistics',
    'draft',
    'Michael',
    '2026-11-12T08:15:00Z',
    'Goal: warehouse logistics in the ERP.\nScope: inbound and outbound for DACH; transport planning stays external.\nRationale: three manual interfaces are retired.',
  ),
  solution('5d000000-0000-4000-8000-000000000003', 'Integration Platform', 'proposed', 'Kurt', '2026-11-03T14:00:00Z', null),
]

export const APPLICATION = '6a000000-0000-4000-8000-000000000001'
export const COMPONENT = '6a000000-0000-4000-8000-000000000002'
export const DATA_FLOW = '6a000000-0000-4000-8000-000000000003'

export const METATYPES = [
  { id: APPLICATION, name: 'Application', isConnection: false },
  { id: COMPONENT, name: 'Technology Component', isConnection: false },
  { id: DATA_FLOW, name: 'Data Flow', isConnection: true },
]

function entry(id: number, name: string, metatypeId: string, version = 1): SolutionLandscapeEntry {
  const metatypeName = METATYPES.find((candidate) => candidate.id === metatypeId)!.name
  return { id, name, metatypeId, metatypeName, version }
}

export const LANDSCAPE: SolutionLandscapeEntry[] = [
  entry(101, 'CRM-Legacy', APPLICATION, 3),
  entry(102, 'ERP-Core', APPLICATION, 4),
  entry(103, 'Warehouse Management', APPLICATION),
  entry(104, 'Customer Portal', APPLICATION, 2),
  entry(105, 'PostgreSQL Cluster', COMPONENT),
  entry(106, 'Message Broker', COMPONENT, 2),
  entry(107, 'Data Warehouse', APPLICATION),
]

/** A landscape of `count` applications, for paging. */
export function manyApplications(count: number): SolutionLandscapeEntry[] {
  return Array.from({ length: count }, (_, index) => entry(1000 + index, `Application ${String(index + 1).padStart(3, '0')}`, APPLICATION))
}

export const OWNER = '7b000000-0000-4000-8000-000000000001'
export const USERS = '7b000000-0000-4000-8000-000000000002'
export const TIER = '7b000000-0000-4000-8000-000000000003'
export const HOSTED_ON = '7b000000-0000-4000-8000-000000000004'
export const LIVE = '7b000000-0000-4000-8000-000000000005'
export const COST = '7b000000-0000-4000-8000-000000000006'

function mapping(id: string, name: string, dataType: string, necessity: string, sortOrder: number, extra: Record<string, unknown> = {}) {
  return {
    id: `8c${id.slice(2)}`,
    necessity,
    validFrom: '2026-01-01',
    sortOrder,
    state: 'active',
    propertyDefinition: { id, name, label: name, dataType, enumValues: [], refMetatype: null, ...extra },
  }
}

/** The active mappings of the application metatype, one per data type the dialog offers. */
export const APPLICATION_MAPPINGS = [
  mapping(OWNER, 'Owner', 'string', 'mandatory', 1),
  mapping(USERS, 'Users', 'integer', 'optional', 2),
  mapping(TIER, 'Tier', 'enum', 'optional', 3, {
    enumValues: [
      { id: '9d000000-0000-4000-8000-000000000001', value: 'gold', sortOrder: 1 },
      { id: '9d000000-0000-4000-8000-000000000002', value: 'silver', sortOrder: 2 },
    ],
  }),
  mapping(HOSTED_ON, 'Hosted on', 'reference', 'optional', 4, { refMetatype: { id: COMPONENT, name: 'Technology Component' } }),
  mapping(LIVE, 'Live', 'boolean', 'optional', 5),
  mapping(COST, 'Cost', 'integer', 'optional', 6),
]

function newDelta(id: string, name: string, metatypeId: string, description: string | null = null): SolutionDelta {
  const metatypeName = METATYPES.find((candidate) => candidate.id === metatypeId)!.name
  return {
    id,
    deltaType: 'new',
    entityId: null,
    name,
    metatypeId,
    metatypeName,
    description,
    properties: [],
    changes: [],
    createdAt: '2026-11-12T10:00:00Z',
    updatedAt: null,
  }
}

function retiringDelta(id: string, entity: SolutionLandscapeEntry): SolutionDelta {
  return {
    id,
    deltaType: 'retiring',
    entityId: entity.id,
    name: entity.name,
    metatypeId: entity.metatypeId,
    metatypeName: entity.metatypeName,
    description: null,
    properties: [],
    changes: [],
    createdAt: '2026-11-12T10:00:00Z',
    updatedAt: null,
  }
}

const DATA_TYPES: Record<string, string> = Object.fromEntries(
  APPLICATION_MAPPINGS.map((entry) => [entry.propertyDefinition.id, entry.propertyDefinition.dataType]),
)
const PROPERTY_NAMES: Record<string, string> = Object.fromEntries(
  APPLICATION_MAPPINGS.map((entry) => [entry.propertyDefinition.id, entry.propertyDefinition.name]),
)

/** A value as the contract carries it, from its text by the data type of the property. */
function typed(propertyDefId: string, text: string): SolutionDeltaValue {
  switch (DATA_TYPES[propertyDefId]) {
    case 'integer':
      return { valueInt: Number(text) }
    case 'boolean':
      return { valueBool: text === 'true' }
    case 'enum':
      return { valueEnum: text }
    default:
      return { valueText: text }
  }
}

function propertyChange(propertyDefId: string, before: string | undefined, after: string | undefined): SolutionDeltaChange {
  return {
    field: 'property',
    propertyDefId,
    propertyName: PROPERTY_NAMES[propertyDefId] ?? propertyDefId,
    dataType: (DATA_TYPES[propertyDefId] ?? 'string') as SolutionDeltaChange['dataType'],
    redacted: false,
    before: before !== undefined ? typed(propertyDefId, before) : null,
    after: after !== undefined && after !== '' ? typed(propertyDefId, after) : null,
  }
}

/** The current values of the entities of the landscape, as text by property, with a description. */
export const ENTRY_VALUES: Record<number, { description: string; values: Record<string, string> }> = {
  102: { description: 'Core ERP for finance and logistics', values: { [OWNER]: 'IT', [USERS]: '250', [TIER]: 'silver', [COST]: '900' } },
  103: { description: 'Warehouse management, on premises', values: { [OWNER]: 'Logistics', [USERS]: '40' } },
}

function modifiedDelta(id: string, entity: SolutionLandscapeEntry, changes: SolutionDeltaChange[]): SolutionDelta {
  return { ...retiringDelta(id, entity), deltaType: 'modified', changes }
}

/** The entry of the baseline as getSolutionLandscapeEntity returns it; Cost is hidden. */
export function landscapeEntity(entry: SolutionLandscapeEntry): SolutionLandscapeEntity {
  const stored = ENTRY_VALUES[entry.id] ?? { description: '', values: { [OWNER]: 'IT' } }
  return {
    ...entry,
    description: stored.description,
    properties: Object.entries(stored.values).map(
      ([propertyDefId, text]) =>
        ({
          propertyDefId,
          propertyName: PROPERTY_NAMES[propertyDefId] ?? propertyDefId,
          dataType: DATA_TYPES[propertyDefId] ?? 'string',
          redacted: propertyDefId === COST,
          ...(propertyDefId === COST ? {} : typed(propertyDefId, text)),
        }) as SolutionLandscapeEntity['properties'][number],
    ),
  }
}

/** The deltas of "ERP Extension Logistics" in the screen: two new entities, one retiring. */
export const DELTAS: SolutionDelta[] = [
  newDelta('ae000000-0000-4000-8000-000000000001', 'Warehouse Cloud', APPLICATION, 'Cloud WMS for DACH'),
  newDelta('ae000000-0000-4000-8000-000000000002', 'Scanner Gateway', COMPONENT),
  retiringDelta('ae000000-0000-4000-8000-000000000003', LANDSCAPE[2]!),
]

/** A delta modified of ERP-Core: Users from 250 to 400, Tier from silver to gold. */
export const MODIFIED_ERP = modifiedDelta('ae000000-0000-4000-8000-000000000004', LANDSCAPE[1]!, [
  propertyChange(USERS, '250', '400'),
  propertyChange(TIER, 'silver', 'gold'),
])

/** The deltas of the screen with a modification. */
export const DELTAS_WITH_MODIFIED: SolutionDelta[] = [...DELTAS, MODIFIED_ERP]

/**
 * A delta modified of Warehouse Management whose recorded before value of Users (25) no longer
 * matches the catalog (40): the diff marks the drift.
 */
export const MODIFIED_WAREHOUSE_DRIFT = modifiedDelta('ae000000-0000-4000-8000-000000000005', LANDSCAPE[2]!, [
  { field: 'name', propertyDefId: null, propertyName: null, dataType: null, redacted: false, before: { valueText: 'Warehouse Management' }, after: { valueText: 'Warehouse Management 2' } },
  propertyChange(USERS, '25', '120'),
])

/** The deltas of the comparison screen: two new, two modified (one with drift), one retiring. */
export const DIFF_DELTAS: SolutionDelta[] = [
  { ...newDelta('ae000000-0000-4000-8000-000000000001', 'Warehouse Cloud', APPLICATION, 'Cloud WMS for DACH'), properties: [
    { propertyDefId: OWNER, propertyName: 'Owner', dataType: 'string', redacted: false, valueText: 'Logistics' },
    { propertyDefId: USERS, propertyName: 'Users', dataType: 'integer', redacted: false, valueInt: 120 },
  ] as SolutionDelta['properties'] },
  newDelta('ae000000-0000-4000-8000-000000000002', 'Scanner Gateway', COMPONENT),
  MODIFIED_ERP,
  MODIFIED_WAREHOUSE_DRIFT,
  retiringDelta('ae000000-0000-4000-8000-000000000003', LANDSCAPE[0]!),
]

/** `count` new deltas, for paging. */
export function manyDeltas(count: number): SolutionDelta[] {
  return Array.from({ length: count }, (_, index) =>
    newDelta(`ae100000-0000-4000-8000-${String(index).padStart(12, '0')}`, `Delta ${String(index + 1).padStart(3, '0')}`, COMPONENT),
  )
}

function value(found: { valueText?: string | null; valueInt?: number | null; valueBool?: boolean | null; valueDate?: string | null; valueEnum?: string | null; valueRef?: unknown }): SolutionDeltaValue {
  return {
    valueText: found.valueText ?? null,
    valueInt: found.valueInt ?? null,
    valueBool: found.valueBool ?? null,
    valueDate: found.valueDate ?? null,
    valueEnum: found.valueEnum ?? null,
    valueRef: (found.valueRef as SolutionDeltaValue['valueRef']) ?? null,
  }
}

function same(a: SolutionDeltaValue | null, b: SolutionDeltaValue | null): boolean {
  return JSON.stringify(a ? value(a) : null) === JSON.stringify(b ? value(b) : null)
}

function systemField(field: 'name' | 'description', current: SolutionDeltaValue | null, target: SolutionDeltaValue | null, changed: boolean, catalogChanged = false): SolutionDiffField {
  return { field, propertyDefId: null, propertyName: null, dataType: null, redacted: false, current, target, changed, catalogChanged }
}

/** The diff entry of a delta against the landscape, as getSolutionDiff computes it (design D2). */
export function diffEntry(delta: SolutionDelta, landscape: SolutionLandscapeEntry[]): SolutionDiffEntry {
  const entity = delta.entityId !== null ? landscape.find((candidate) => candidate.id === delta.entityId) : undefined
  const base = { deltaId: delta.id, deltaType: delta.deltaType, entityId: delta.entityId, metatypeId: delta.metatypeId, metatypeName: delta.metatypeName, name: delta.name, inCatalog: entity !== undefined }
  const text = (content: string | null | undefined) => (content ? { valueText: content } : null)
  if (delta.deltaType === 'new') {
    return {
      ...base,
      fields: [
        systemField('name', null, text(delta.name), true),
        ...(delta.description ? [systemField('description', null, text(delta.description), true)] : []),
        ...delta.properties.map((property) => ({
          field: 'property' as const,
          propertyDefId: property.propertyDefId,
          propertyName: property.propertyName,
          dataType: property.dataType as SolutionDiffField['dataType'],
          redacted: property.redacted,
          current: null,
          target: property.redacted ? null : value(property),
          changed: true,
          catalogChanged: false,
        })),
      ],
    }
  }
  const stored = entity ? landscapeEntity(entity) : undefined
  const current = new Map((stored?.properties ?? []).map((property) => [property.propertyDefId, property]))
  if (delta.deltaType === 'retiring') {
    if (!stored) return { ...base, fields: [] }
    return {
      ...base,
      fields: [
        systemField('name', text(stored.name), null, true),
        ...(stored.description ? [systemField('description', text(stored.description), null, true)] : []),
        ...stored.properties.map((property) => ({
          field: 'property' as const,
          propertyDefId: property.propertyDefId,
          propertyName: property.propertyName,
          dataType: property.dataType as SolutionDiffField['dataType'],
          redacted: property.redacted,
          current: property.redacted ? null : value(property),
          target: null,
          changed: true,
          catalogChanged: false,
        })),
      ],
    }
  }
  const change = (field: string) => delta.changes.find((candidate) => candidate.field === field)
  const system = (field: 'name' | 'description', now: SolutionDeltaValue | null) => {
    const found = change(field)
    return found ? systemField(field, now, found.after, true, stored !== undefined && !same(found.before, now)) : systemField(field, now, now, false)
  }
  const fields: SolutionDiffField[] = [system('name', stored ? text(stored.name) : null)]
  const description = system('description', stored ? text(stored.description) : null)
  if (description.current || description.target) fields.push(description)
  const properties = new Map<string, SolutionDiffField>()
  for (const property of stored?.properties ?? []) {
    const now = property.redacted ? null : value(property)
    properties.set(property.propertyDefId, { field: 'property', propertyDefId: property.propertyDefId, propertyName: property.propertyName, dataType: property.dataType as SolutionDiffField['dataType'], redacted: property.redacted, current: now, target: now, changed: false, catalogChanged: false })
  }
  for (const found of delta.changes.filter((candidate) => candidate.field === 'property')) {
    const known = current.get(found.propertyDefId!)
    const redacted = found.redacted || known?.redacted === true
    const now = known && !redacted ? value(known) : null
    properties.set(found.propertyDefId!, {
      field: 'property',
      propertyDefId: found.propertyDefId,
      propertyName: found.propertyName,
      dataType: found.dataType,
      redacted,
      current: redacted ? null : now,
      target: redacted ? null : found.after,
      changed: true,
      catalogChanged: !redacted && stored !== undefined && !same(found.before, now),
    })
  }
  fields.push(...[...properties.values()].sort((a, b) => a.propertyName!.toLowerCase().localeCompare(b.propertyName!.toLowerCase())))
  return { ...base, fields }
}

interface SolutionDeltaWriteBody {
  deltaType: 'new' | 'modified' | 'retiring'
  entityId?: number
  metatypeId?: string
  name?: string
  description?: string
  properties?: { propertyDefId: string; value: string }[]
}

export interface SolutionApi {
  fetch: typeof fetch
  /** the deltas the fake holds; a test adds one "meanwhile" */
  deltas: SolutionDelta[]
  diffQueries: URLSearchParams[]
  deltaQueries: URLSearchParams[]
  posts: Record<string, unknown>[]
  landscapeQueries: URLSearchParams[]
  deltaPosts: Record<string, unknown>[]
  deltaPuts: Record<string, unknown>[]
  deltaDeletes: string[]
}

export function solutionApi(
  options: {
    me?: unknown
    solutions?: Solution[]
    landscape?: SolutionLandscapeEntry[]
    rejectCursors?: boolean
    deltas?: SolutionDelta[]
    /** answer the diff with this status, e.g. 404 or 500 */
    diffStatus?: number
    /** answer 409 to every delta write, as for a solution completed meanwhile */
    completeOnWrite?: boolean
  } = {},
): SolutionApi {
  const solutions = [...(options.solutions ?? SOLUTIONS)]
  const landscape = [...(options.landscape ?? LANDSCAPE)].sort(
    (a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()) || a.id - b.id,
  )
  const posts: Record<string, unknown>[] = []
  const landscapeQueries: URLSearchParams[] = []
  const deltaPosts: Record<string, unknown>[] = []
  const deltaDeletes: string[] = []
  const deltaPuts: Record<string, unknown>[] = []
  const deltas = [...(options.deltas ?? [])]
  const diffQueries: URLSearchParams[] = []
  const deltaQueries: URLSearchParams[] = []
  // one page of a list in the order of the delta list, with the counts, by page number or cursor
  const paged = <T>(all: T[], params: URLSearchParams, key: 'items' | 'entries') => {
    const page = Number(params.get('page') ?? '0')
    const pageSize = Number(params.get('pageSize') ?? '50')
    const cursor = params.get('cursor')
    if (cursor && cursor !== `dcursor-${page}`) {
      return json({ ...problem(400, ['cursor']), type: 'urn:oea:problem:validation-failed' }, 400)
    }
    const totalPages = Math.ceil(all.length / pageSize)
    const counts = { new: 0, modified: 0, retiring: 0 }
    for (const delta of deltas) counts[delta.deltaType] += 1
    return json({
      [key]: all.slice(page * pageSize, (page + 1) * pageSize),
      page: { page, pageSize, totalElements: all.length, totalPages },
      counts,
      nextCursor: page + 1 < totalPages ? `dcursor-${page + 1}` : null,
    })
  }
  let nextDelta = 100
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' } })
  const problem = (status: number, invalidFields: string[] = [], detail?: string) => ({
    type:
      status === 422
        ? 'urn:oea:problem:validation-failed'
        : status === 404
          ? 'urn:oea:problem:not-found'
          : status === 409
            ? 'urn:oea:problem:solution-completed'
            : 'urn:oea:problem:forbidden',
    title: 'Rejected',
    status,
    detail,
    invalidFields,
    errors: invalidFields.map((field) => ({ field, message: detail ?? 'Invalid' })),
  })
  const sorted = () =>
    [...deltas].sort(
      (a, b) =>
        ['new', 'modified', 'retiring'].indexOf(a.deltaType) - ['new', 'modified', 'retiring'].indexOf(b.deltaType) ||
        a.name.toLowerCase().localeCompare(b.name.toLowerCase()),
    )
  const completedSolution = (id: string) => {
    const found = solutions.find((candidate) => candidate.id === id)
    return options.completeOnWrite || found?.status === 'implemented' || found?.status === 'archived'
  }
  // the changes of a modification against the entry, keeping recorded before values (D2, D3)
  const changesOf = (entity: SolutionLandscapeEntry, body: SolutionDeltaWriteBody, recorded: SolutionDeltaChange[] = []) => {
    const properties = body.properties ?? []
    if (properties.some((property) => property.propertyDefId === COST)) return { status: 403 as const }
    if (body.name !== undefined && body.name.trim().length === 0) return { status: 422 as const, field: 'name', detail: 'The name must not be blank.' }
    const usersIndex = properties.findIndex((property) => property.propertyDefId === USERS && property.value !== '')
    if (usersIndex >= 0 && !/^-?\d+$/.test(properties[usersIndex]!.value)) {
      return { status: 422 as const, field: `properties[${usersIndex}].value`, detail: 'The value must be an integer.' }
    }
    if (properties.some((property) => property.propertyDefId === OWNER && property.value === '')) {
      return { status: 422 as const, field: 'properties', detail: 'The mandatory property Owner has no value.' }
    }
    const stored = landscapeEntity(entity)
    const current = ENTRY_VALUES[entity.id]?.values ?? { [OWNER]: 'IT' }
    const changes: SolutionDeltaChange[] = []
    const keep = (change: SolutionDeltaChange) => {
      const earlier = recorded.find((candidate) => candidate.field === change.field && candidate.propertyDefId === change.propertyDefId)
      const before = earlier ? earlier.before : change.before
      if (JSON.stringify(before) !== JSON.stringify(change.after)) changes.push({ ...change, before })
    }
    if (body.name !== undefined) {
      keep({ field: 'name', propertyDefId: null, propertyName: null, dataType: null, redacted: false, before: { valueText: entity.name }, after: { valueText: body.name } })
    }
    if (body.description !== undefined) {
      keep({ field: 'description', propertyDefId: null, propertyName: null, dataType: null, redacted: false, before: { valueText: stored.description }, after: { valueText: body.description } })
    }
    for (const property of properties) {
      keep(propertyChange(property.propertyDefId, current[property.propertyDefId], property.value))
    }
    if (changes.length === 0) return { status: 422 as const, field: 'properties', detail: 'The delta changes nothing.' }
    return { status: 200 as const, changes }
  }
  const updateDelta = (solutionId: string, deltaId: string, body: SolutionDeltaWriteBody) => {
    const found = deltas.find((candidate) => candidate.id === deltaId)
    if (!found) return json(problem(404), 404)
    if (completedSolution(solutionId)) {
      return json(problem(409, [], 'This solution is completed and can no longer be changed'), 409)
    }
    if (found.deltaType !== 'modified') return json(problem(422, ['deltaType'], 'Only a modified delta can be edited.'), 422)
    const entity = landscape.find((candidate) => candidate.id === found.entityId)!
    const outcome = changesOf(entity, body, found.changes)
    if (outcome.status === 403) return json(problem(403), 403)
    if (outcome.status === 422) return json(problem(422, [outcome.field], outcome.detail), 422)
    found.changes = outcome.changes
    found.updatedAt = '2026-11-12T11:00:00Z'
    return json(found)
  }
  const createDelta = (solutionId: string, body: SolutionDeltaWriteBody) => {
    if (completedSolution(solutionId)) {
      return json(problem(409, [], 'This solution is completed and can no longer be changed'), 409)
    }
    if (body.deltaType === 'modified') {
      const entity = landscape.find((candidate) => candidate.id === body.entityId)
      if (!entity) return json(problem(422, ['entityId'], 'Entity not found in the current landscape'), 422)
      const outcome = changesOf(entity, body)
      if (outcome.status === 403) return json(problem(403), 403)
      if (outcome.status === 422) return json(problem(422, [outcome.field], outcome.detail), 422)
      if (deltas.some((candidate) => candidate.entityId === entity.id)) {
        return json(problem(422, ['entityId'], 'A delta already exists for this entity in this solution'), 422)
      }
      const created = modifiedDelta(`ae000000-0000-4000-8000-${String(nextDelta++).padStart(12, '0')}`, entity, outcome.changes)
      deltas.push(created)
      return json(created, 201)
    }
    if (body.deltaType === 'retiring') {
      const entity = landscape.find((candidate) => candidate.id === body.entityId)
      if (!entity) return json(problem(422, ['entityId'], 'Entity not found in the current landscape'), 422)
      if (deltas.some((candidate) => candidate.entityId === entity.id)) {
        return json(problem(422, ['entityId'], 'A delta already exists for this entity in this solution'), 422)
      }
      const created = retiringDelta(`ae000000-0000-4000-8000-${String(nextDelta++).padStart(12, '0')}`, entity)
      deltas.push(created)
      return json(created, 201)
    }
    const properties = body.properties ?? []
    if (properties.some((property) => property.propertyDefId === COST)) {
      return json(problem(403), 403)
    }
    const name = body.name ?? ''
    if (name.trim().length === 0) return json(problem(422, ['name'], 'The name must not be blank.'), 422)
    const usersIndex = properties.findIndex((property) => property.propertyDefId === USERS)
    if (usersIndex >= 0 && !/^-?\d+$/.test(properties[usersIndex]!.value)) {
      return json(problem(422, [`properties[${usersIndex}].value`], 'The value must be an integer.'), 422)
    }
    if (body.metatypeId === APPLICATION && !properties.some((property) => property.propertyDefId === OWNER)) {
      return json(problem(422, ['properties'], 'The mandatory property Owner has no value.'), 422)
    }
    if (deltas.some((candidate) => candidate.deltaType === 'new' && candidate.metatypeId === body.metatypeId && candidate.name.toLowerCase() === name.toLowerCase())) {
      return json(problem(422, ['name'], 'A delta already exists for this entity in this solution'), 422)
    }
    const created = newDelta(`ae000000-0000-4000-8000-${String(nextDelta++).padStart(12, '0')}`, name, body.metatypeId!, body.description ?? null)
    deltas.push(created)
    return json(created, 201)
  }
  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const method = init?.method ?? 'GET'
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_SOLUTION_ARCHITECT)
    }
    if (url.pathname === '/api/v1/solutions' && method === 'GET') {
      return json([...solutions].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())))
    }
    if (url.pathname === '/api/v1/solutions' && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as { name: string; description?: string }
      posts.push(body)
      const name = body.name.trim()
      if (name.length === 0) {
        return json(problem(422, ['name'], 'The name must not be blank.'), 422)
      }
      if (solutions.some((candidate) => candidate.name.toLowerCase() === name.toLowerCase())) {
        return json(problem(422, ['name'], `Name '${name}' is already taken`), 422)
      }
      const created = solution(`5d000000-0000-4000-8000-${String(solutions.length + 1).padStart(12, '0')}`, name, 'draft', 'Michael', '2026-11-12T10:00:00Z', body.description ?? null)
      solutions.push(created)
      return json(created, 201)
    }
    if (url.pathname === '/api/v1/metamodel/metatypes') {
      return json(METATYPES)
    }
    const metatypeDetail = /^\/api\/v1\/metamodel\/metatypes\/([^/]+)$/.exec(url.pathname)
    if (metatypeDetail) {
      const found = METATYPES.find((candidate) => candidate.id === decodeURIComponent(metatypeDetail[1]!))
      return found ? json({ ...found, propertyMappings: found.id === APPLICATION ? APPLICATION_MAPPINGS : [] }) : json(problem(404), 404)
    }
    const deltaList = /^\/api\/v1\/solutions\/([^/]+)\/deltas$/.exec(url.pathname)
    if (deltaList && method === 'GET') {
      deltaQueries.push(url.searchParams)
      return paged(sorted(), url.searchParams, 'items')
    }
    const diff = /^\/api\/v1\/solutions\/([^/]+)\/diff$/.exec(url.pathname)
    if (diff && method === 'GET') {
      diffQueries.push(url.searchParams)
      if (options.diffStatus) return json(problem(options.diffStatus), options.diffStatus)
      if (!solutions.some((candidate) => candidate.id === decodeURIComponent(diff[1]!))) return json(problem(404), 404)
      return paged(sorted().map((delta) => diffEntry(delta, landscape)), url.searchParams, 'entries')
    }
    if (deltaList && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as SolutionDeltaWriteBody
      deltaPosts.push(body as unknown as Record<string, unknown>)
      return createDelta(decodeURIComponent(deltaList[1]!), body)
    }
    const deltaOne = /^\/api\/v1\/solutions\/([^/]+)\/deltas\/([^/]+)$/.exec(url.pathname)
    if (deltaOne && method === 'PUT') {
      const body = JSON.parse(String(init?.body)) as SolutionDeltaWriteBody
      deltaPuts.push(body as unknown as Record<string, unknown>)
      return updateDelta(decodeURIComponent(deltaOne[1]!), decodeURIComponent(deltaOne[2]!), body)
    }
    const entry = /^\/api\/v1\/solutions\/([^/]+)\/landscape\/(\d+)$/.exec(url.pathname)
    if (entry && method === 'GET') {
      const found = landscape.find((candidate) => candidate.id === Number(entry[2]))
      return found ? json(landscapeEntity(found)) : json(problem(404), 404)
    }
    if (deltaOne && method === 'DELETE') {
      deltaDeletes.push(decodeURIComponent(deltaOne[2]!))
      if (completedSolution(decodeURIComponent(deltaOne[1]!))) {
        return json(problem(409, [], 'This solution is completed and can no longer be changed'), 409)
      }
      const index = deltas.findIndex((candidate) => candidate.id === decodeURIComponent(deltaOne[2]!))
      if (index < 0) return json(problem(404), 404)
      deltas.splice(index, 1)
      return new Response(null, { status: 204 })
    }
    const baseline = /^\/api\/v1\/solutions\/([^/]+)\/landscape$/.exec(url.pathname)
    if (baseline && method === 'GET') {
      landscapeQueries.push(url.searchParams)
      if (!solutions.some((candidate) => candidate.id === decodeURIComponent(baseline[1]!))) {
        return json(problem(404), 404)
      }
      const q = (url.searchParams.get('q') ?? '').trim().toLowerCase()
      const metatypeId = url.searchParams.get('metatypeId')
      const page = Number(url.searchParams.get('page') ?? '0')
      const pageSize = Number(url.searchParams.get('pageSize') ?? '50')
      const cursor = url.searchParams.get('cursor')
      const matches = landscape.filter(
        (candidate) => (!q || candidate.name.toLowerCase().includes(q)) && (!metatypeId || candidate.metatypeId === metatypeId),
      )
      if (cursor && (options.rejectCursors || cursor !== `cursor-${page}`)) {
        return json({ ...problem(400, ['cursor']), type: 'urn:oea:problem:validation-failed' }, 400)
      }
      const items = matches.slice(page * pageSize, (page + 1) * pageSize).map((candidate) => {
        const delta = deltas.find((found) => found.entityId === candidate.id && found.deltaType !== 'new')
        return { ...candidate, deltaType: delta ? delta.deltaType : null }
      })
      const totalPages = Math.ceil(matches.length / pageSize)
      return json({
        items,
        page: { page, pageSize, totalElements: matches.length, totalPages },
        nextCursor: page + 1 < totalPages ? `cursor-${page + 1}` : null,
      })
    }
    const one = /^\/api\/v1\/solutions\/([^/]+)$/.exec(url.pathname)
    if (one && method === 'GET') {
      const found = solutions.find((candidate) => candidate.id === decodeURIComponent(one[1]!))
      return found ? json(found) : json(problem(404), 404)
    }
    // the header and the sidebar ask for further data; an empty answer keeps them quiet
    return json([])
  }
  return { fetch: fetchFn, deltas, diffQueries, deltaQueries, posts, landscapeQueries, deltaPosts, deltaPuts, deltaDeletes }
}
