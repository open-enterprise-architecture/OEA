/**
 * Test and story data for the metamodel configuration SCR-030 (scr-030-metamodel-configuration):
 * a stateful fake of the metamodel API with versions, `If-Match` and the problem types of the
 * contract, seeded with a part of the starter package and one organization metatype.
 */
import type { components } from '../api/generated/schema'

// the fake changes the read-only fields of the contract (versions, re-import setting)
type Mutable<T> = { -readonly [K in keyof T]: T[K] }
type Metatype = Mutable<components['schemas']['MetatypeDetail']>
type Property = Mutable<components['schemas']['PropertyDefinitionDetail']>
type Category = Mutable<components['schemas']['PropertyCategory']>
type Mapping = components['schemas']['MetatypePropertyMapping']
type Scope = components['schemas']['MetamodelScope']

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 7, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-metamodel',
  }
}

export const ME_METAMODEL_ADMIN = me('Kurt', ['portal:read', 'entity:read', 'metamodel:write'])
export const ME_READER = me('Franz', ['portal:read', 'entity:read'])
export const ME_NO_ACCESS = me('Max', ['portal:read'])

export const IDS = {
  general: 'ca000000-0000-4000-8000-000000000001',
  status: 'fd000000-0000-4000-8000-000000000001',
  planned: 'e0000000-0000-4000-8000-000000000001',
  active: 'e0000000-0000-4000-8000-000000000002',
  owner: 'fd000000-0000-4000-8000-000000000002',
  component: 'a0000000-0000-4000-8000-000000000001',
  service: 'a0000000-0000-4000-8000-000000000002',
  zone: 'a0000000-0000-4000-8000-000000000003',
  cloudZone: 'a0000000-0000-4000-8000-000000000004',
}

const STARTER = { packageId: 'oea-starter-togaf-classic', version: '1.1.0' }

interface Entry {
  id: string
  metatypeId: string
  propertyDefId: string
  necessity: Mapping['necessity']
  validFrom: string
  scope: Scope
}

interface Stored {
  metatypes: (Omit<Metatype, 'propertyMappings' | 'effectiveReimportPolicy'> & { entities: number })[]
  properties: Property[]
  categories: Category[]
  entries: Entry[]
  globalReimport: 'keep' | 'remove'
  labels: Record<string, Record<string, { packageText?: string; organizationText?: string }>>
}

export interface MetamodelApiOptions {
  me?: unknown
  /** Entities without a value that a new mandatory mapping marks. */
  unmarkedEntities?: number
  /** Answers every request after the given path with an error, for failure states. */
  failListing?: boolean
}

/** A request the fake received, with the headers that matter for the metamodel writes. */
export interface MetamodelRequest {
  method: string
  path: string
  ifMatch?: string
  csrf?: string
  body?: unknown
}

export interface MetamodelApi {
  fetch: typeof fetch
  requests: MetamodelRequest[]
  state: Stored
  /** Another administrator changes a metatype: new abbreviation, version + 1. */
  changeElsewhere(metatypeId: string, abbreviation: string): void
}

function seed(): Stored {
  const today = new Date().toISOString().slice(0, 10)
  const future = `${Number(today.slice(0, 4)) + 1}-01-01`
  const general: Category = { id: IDS.general, name: 'General', sortOrder: 0, scope: 'imported', version: 1 }
  return {
    categories: [general],
    properties: [
      {
        id: IDS.status,
        name: 'Status',
        dataType: 'enum',
        category: general,
        scope: 'imported',
        sourcePackage: STARTER,
        sqlColumnName: 'status',
        version: 1,
        enumValues: [
          { id: IDS.planned, value: 'planned', sortOrder: 0 },
          { id: IDS.active, value: 'active', sortOrder: 1 },
        ],
      },
      { id: IDS.owner, name: 'Owner', dataType: 'string', scope: 'organization', sqlColumnName: 'owner', version: 1, maxLength: 100, enumValues: [] },
    ],
    metatypes: [
      { id: IDS.component, name: 'Application Component', abbreviation: 'AC', sortOrder: 1, scope: 'imported', sourcePackage: STARTER, isConnection: false, reimportPolicy: 'inherit', version: 1, entities: 12, connectionConstraints: [] },
      { id: IDS.service, name: 'Application Service', abbreviation: 'AS', sortOrder: 2, scope: 'imported', sourcePackage: STARTER, isConnection: false, reimportPolicy: 'inherit', version: 1, entities: 0, connectionConstraints: [] },
      { id: IDS.zone, name: 'Network Zone', abbreviation: 'NZ', sortOrder: 10, scope: 'organization', isConnection: false, reimportPolicy: 'inherit', version: 3, entities: 5, connectionConstraints: [] },
      { id: IDS.cloudZone, name: 'Cloud Zone', abbreviation: 'CZ', sortOrder: 11, scope: 'organization', isConnection: false, baseMetatypeId: IDS.zone, reimportPolicy: 'inherit', version: 1, entities: 0, connectionConstraints: [] },
    ],
    entries: [
      { id: 'b0000000-0000-4000-8000-000000000001', metatypeId: IDS.component, propertyDefId: IDS.status, necessity: 'optional', validFrom: '2026-01-01', scope: 'imported' },
      { id: 'b0000000-0000-4000-8000-000000000002', metatypeId: IDS.component, propertyDefId: IDS.status, necessity: 'mandatory', validFrom: future, scope: 'organization' },
      { id: 'b0000000-0000-4000-8000-000000000003', metatypeId: IDS.zone, propertyDefId: IDS.owner, necessity: 'warning', validFrom: '2026-02-01', scope: 'organization' },
    ],
    globalReimport: 'keep',
    labels: { de: { [`metatype.${IDS.component}.name`]: { packageText: 'Anwendungskomponente' } } },
  }
}

function problem(status: number, type: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ type: `urn:oea:problem:${type}`, title: type, status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function json(body: unknown, status = 200, etag?: number): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...(etag !== undefined ? { ETag: `"${etag}"` } : {}) },
  })
}

let counter = 0
function newId(): string {
  counter += 1
  return `d0000000-0000-4000-8000-${String(counter).padStart(12, '0')}`
}

/** The fake metamodel API; `state` can be inspected and changed by a test. */
export function metamodelApi(options: MetamodelApiOptions = {}): MetamodelApi {
  const state = seed()
  const requests: MetamodelRequest[] = []
  const today = new Date().toISOString().slice(0, 10)

  const chain = (id: string): string[] => {
    const ids: string[] = []
    let current: string | null | undefined = id
    while (current && !ids.includes(current)) {
      ids.push(current)
      current = state.metatypes.find((metatype) => metatype.id === current)?.baseMetatypeId
    }
    return ids
  }
  const stateOf = (entry: Entry): Mapping['state'] => {
    if (entry.validFrom > today) {
      return 'future'
    }
    const latest = state.entries
      .filter((other) => other.metatypeId === entry.metatypeId && other.propertyDefId === entry.propertyDefId && other.validFrom <= today)
      .map((other) => other.validFrom)
      .sort()
      .at(-1)
    return latest === entry.validFrom ? 'active' : 'past'
  }
  const mappings = (id: string, history: boolean): Mapping[] =>
    chain(id)
      .reverse()
      .flatMap((owner) =>
        state.entries
          .filter((entry) => entry.metatypeId === owner && (history || stateOf(entry) === 'active'))
          .map((entry) => {
            const base = state.metatypes.find((metatype) => metatype.id === owner)!
            return {
              id: entry.id,
              propertyDefinition: state.properties.find((property) => property.id === entry.propertyDefId)!,
              necessity: entry.necessity,
              validFrom: entry.validFrom,
              sortOrder: 0,
              scope: entry.scope,
              state: stateOf(entry),
              inheritedFrom: owner === id ? null : { id: base.id, name: base.name, abbreviation: base.abbreviation },
            }
          }),
      )
  const effective = (id: string): 'keep' | 'remove' => {
    for (const owner of chain(id)) {
      const policy = state.metatypes.find((metatype) => metatype.id === owner)?.reimportPolicy
      if (policy === 'keep' || policy === 'remove') {
        return policy
      }
    }
    return state.globalReimport
  }
  const detail = (id: string): Metatype => {
    const { entities: _entities, ...metatype } = state.metatypes.find((candidate) => candidate.id === id)!
    return { ...metatype, propertyMappings: mappings(id, false), effectiveReimportPolicy: effective(id) }
  }
  const labelList = (locale: string) => {
    const own = state.labels[locale] ?? {}
    const entry = (key: string, elementType: string, name: string) => ({
      key,
      elementType,
      name,
      packageText: own[key]?.packageText ?? null,
      organizationText: own[key]?.organizationText ?? null,
    })
    return [
      ...state.metatypes.map((metatype) => entry(`metatype.${metatype.id}.name`, 'metatype', metatype.name)),
      ...state.properties.map((property) => entry(`property-def.${property.id}.label`, 'property-def', property.name)),
      ...state.properties.flatMap((property) =>
        (property.enumValues ?? []).map((value) => entry(`enum-value.${value.id}.label`, 'enum-value', value.value)),
      ),
      ...state.categories.map((category) => entry(`property-category.${category.id}.name`, 'property-category', category.name)),
    ]
  }
  const stale = (element: { version?: number }, ifMatch: string | undefined): Response | undefined =>
    ifMatch !== undefined && ifMatch !== '*' && ifMatch !== `"${element.version}"`
      ? problem(412, 'stale-version', { currentVersion: element.version })
      : undefined

  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://oea.test')
    const method = init?.method ?? 'GET'
    const headers = new Headers(init?.headers)
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : undefined
    const ifMatch = headers.get('If-Match') ?? undefined
    requests.push({ method, path: url.pathname + url.search, ifMatch, csrf: headers.get('X-XSRF-TOKEN') ?? undefined, body })
    const path = url.pathname.replace(/^\/api\/v1/, '')
    const parts = path.split('/').filter(Boolean)

    if (path === '/me') {
      return json(options.me ?? ME_METAMODEL_ADMIN)
    }
    if (parts[0] === 'i18n') {
      const own = state.labels[parts[1]!] ?? {}
      const bundle = Object.fromEntries(
        labelList(parts[1]!).map((entry) => [entry.key, own[entry.key]?.organizationText ?? own[entry.key]?.packageText ?? entry.name]),
      )
      return json(bundle)
    }
    if (parts[0] !== 'metamodel') {
      return problem(404, 'not-found')
    }
    if (options.failListing && method === 'GET') {
      return problem(500, 'internal')
    }
    const [, kind, id, sub, subId] = parts

    if (kind === 'metatypes') {
      const stored = state.metatypes.find((metatype) => metatype.id === id)
      if (!id) {
        if (method === 'GET') {
          return json(state.metatypes.map((metatype) => detail(metatype.id)))
        }
        const name = String(body?.name ?? '')
        if (state.metatypes.some((metatype) => metatype.name.toLowerCase() === name.toLowerCase())) {
          return problem(409, 'name-taken', { invalidFields: ['name'] })
        }
        const created = {
          id: newId(),
          name,
          abbreviation: String(body?.abbreviation ?? ''),
          sortOrder: Number(body?.sortOrder ?? 0),
          scope: 'organization' as Scope,
          isConnection: false,
          baseMetatypeId: (body?.baseMetatypeId as string | null) ?? null,
          reimportPolicy: 'inherit' as const,
          version: 1,
          entities: 0,
          connectionConstraints: [],
        }
        state.metatypes.push(created)
        return json(detail(created.id), 201, 1)
      }
      if (!stored) {
        return problem(404, 'not-found')
      }
      if (!sub) {
        if (method === 'GET') {
          return json(detail(stored.id), 200, stored.version)
        }
        const rejected = stale(stored, ifMatch)
        if (rejected) {
          return rejected
        }
        if (stored.scope !== 'organization') {
          return problem(409, 'read-only-metatype')
        }
        if (method === 'DELETE') {
          if (stored.entities > 0) {
            return problem(409, 'in-use', { count: stored.entities })
          }
          state.metatypes = state.metatypes.filter((metatype) => metatype.id !== stored.id)
          return json(undefined, 204)
        }
        Object.assign(stored, {
          name: String(body?.name),
          abbreviation: String(body?.abbreviation ?? ''),
          sortOrder: Number(body?.sortOrder ?? 0),
          baseMetatypeId: (body?.baseMetatypeId as string | null) ?? null,
          version: (stored.version ?? 1) + 1,
        })
        return json(detail(stored.id), 200, stored.version)
      }
      if (sub === 'property-mappings' && method === 'GET') {
        return json(mappings(stored.id, url.searchParams.get('includeHistory') === 'true'))
      }
      const rejected = stale(stored, ifMatch)
      if (rejected) {
        return rejected
      }
      if (sub === 'reimport-policy') {
        stored.reimportPolicy = body?.organizationMappings as 'inherit' | 'keep' | 'remove'
        stored.version = (stored.version ?? 1) + 1
        return json(detail(stored.id), 200, stored.version)
      }
      if (sub === 'property-mappings' && method === 'POST') {
        const propertyDefId = String(body?.propertyDefId)
        const validFrom = String(body?.validFrom ?? today)
        if (state.entries.some((entry) => entry.metatypeId === stored.id && entry.propertyDefId === propertyDefId && entry.validFrom === validFrom)) {
          return problem(409, 'conflict')
        }
        const entry: Entry = { id: newId(), metatypeId: stored.id, propertyDefId, necessity: body?.necessity as Entry['necessity'], validFrom, scope: 'organization' }
        state.entries.push(entry)
        stored.version = (stored.version ?? 1) + 1
        const marked = entry.necessity === 'mandatory' && validFrom <= today ? (options.unmarkedEntities ?? 0) : 0
        return json({ ...mappings(stored.id, true).find((mapping) => mapping.id === entry.id), markedEntityCount: marked }, 201)
      }
      if (sub === 'property-mappings' && method === 'DELETE') {
        state.entries = state.entries.filter((entry) => entry.id !== subId)
        stored.version = (stored.version ?? 1) + 1
        return json(undefined, 204)
      }
    }

    if (kind === 'property-definitions') {
      const stored = state.properties.find((property) => property.id === id)
      if (!id) {
        if (method === 'GET') {
          return json(state.properties)
        }
        const name = String(body?.name ?? '')
        if (state.properties.some((property) => property.name.toLowerCase() === name.toLowerCase())) {
          return problem(409, 'name-taken', { invalidFields: ['name'] })
        }
        const created: Property = {
          id: newId(),
          name,
          dataType: body?.dataType as Property['dataType'],
          category: state.categories.find((category) => category.id === body?.categoryId) ?? null,
          maxLength: (body?.maxLength as number | null) ?? null,
          scope: 'organization',
          sqlColumnName: name.toLowerCase().replace(/\W+/g, '_'),
          version: 1,
          enumValues: [],
        }
        state.properties.push(created)
        return json(created, 201)
      }
      if (!stored) {
        return problem(404, 'not-found')
      }
      if (sub === 'enum-values' && method === 'GET') {
        return json(stored.enumValues)
      }
      const rejected = stale(stored, ifMatch)
      if (rejected) {
        return rejected
      }
      if (stored.scope !== 'organization') {
        return problem(409, 'read-only-metatype')
      }
      if (sub === 'enum-values') {
        stored.version = (stored.version ?? 1) + 1
        if (method === 'POST') {
          const value = { id: newId(), value: String(body?.value), sortOrder: stored.enumValues?.length ?? 0 }
          stored.enumValues = [...(stored.enumValues ?? []), value]
          return json(value, 201)
        }
        stored.enumValues = (stored.enumValues ?? []).filter((value) => value.id !== subId)
        return json(undefined, 204)
      }
      if (method === 'DELETE') {
        if (state.entries.some((entry) => entry.propertyDefId === stored.id)) {
          return problem(409, 'in-use')
        }
        state.properties = state.properties.filter((property) => property.id !== stored.id)
        return json(undefined, 204)
      }
      Object.assign(stored, {
        name: String(body?.name),
        dataType: body?.dataType,
        category: state.categories.find((category) => category.id === body?.categoryId) ?? null,
        version: (stored.version ?? 1) + 1,
      })
      return json(stored, 200, stored.version)
    }

    if (kind === 'property-categories') {
      const stored = state.categories.find((category) => category.id === id)
      if (!id) {
        if (method === 'GET') {
          return json(state.categories)
        }
        const created: Category = { id: newId(), name: String(body?.name), description: (body?.description as string) ?? null, sortOrder: Number(body?.sortOrder ?? 0), scope: 'organization', version: 1 }
        state.categories.push(created)
        return json(created, 201)
      }
      if (!stored) {
        return problem(404, 'not-found')
      }
      const rejected = stale(stored, ifMatch)
      if (rejected) {
        return rejected
      }
      if (method === 'DELETE') {
        state.categories = state.categories.filter((category) => category.id !== stored.id)
        return json(undefined, 204)
      }
      Object.assign(stored, { name: String(body?.name), description: body?.description ?? null, version: (stored.version ?? 1) + 1 })
      return json(stored, 200, stored.version)
    }

    if (kind === 'settings') {
      if (method === 'PUT') {
        state.globalReimport = body?.organizationMappings as 'keep' | 'remove'
      }
      return json({ organizationMappings: state.globalReimport })
    }

    if (kind === 'labels') {
      const locale = id!
      if (method === 'PUT') {
        const own = (state.labels[locale] ??= {})
        for (const [key, text] of Object.entries((body?.labels ?? {}) as Record<string, string | null>)) {
          own[key] = { ...own[key], organizationText: text ?? undefined }
        }
      }
      return json(labelList(locale))
    }
    return problem(404, 'not-found')
  }

  return {
    fetch: fetchFn,
    requests,
    state,
    changeElsewhere(metatypeId, abbreviation) {
      const stored = state.metatypes.find((metatype) => metatype.id === metatypeId)!
      stored.abbreviation = abbreviation
      stored.version = (stored.version ?? 1) + 1
    },
  }
}
