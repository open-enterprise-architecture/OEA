/** Test and story data for the catalog screens (US-046, US-049, SCR-020, SCR-028). */
function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 7, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-catalogs',
  }
}

export const ME_ARCHITECT = me('Kurt', ['portal:read', 'entity:read', 'entity:write', 'catalog:manage'])
export const ME_VIEWER = me('Franz', ['portal:read', 'entity:read'])
export const ME_WITHOUT_ROLE = me('Max', [])

function metatype(id: string, name: string, sortOrder: number) {
  return { id, name, abbreviation: '', isConnection: false, sortOrder, scope: 'imported', propertyMappings: [], connectionConstraints: [] }
}

export const METATYPES = [
  metatype('5a000000-0000-4000-8000-000000000002', 'Business Process', 2),
  metatype('5a000000-0000-4000-8000-000000000001', 'Application Component', 1),
  metatype('5a000000-0000-4000-8000-000000000003', 'Technology Node', 3),
]

function summary(id: string, name: string, primaryMetatypeName: string, scope: 'instance' | 'personal') {
  return {
    id,
    name,
    primaryMetatypeId: '5a000000-0000-4000-8000-000000000001',
    primaryMetatypeName,
    scope,
    createdBy: { principalType: 'person', id: 7, displayName: 'Kurt' },
    createdAt: '2026-10-19T10:00:00Z',
  }
}

export const CATALOGS = [
  summary('c0000000-0000-4000-8000-000000000001', 'Application Inventory', 'Application Component', 'instance'),
  summary('c0000000-0000-4000-8000-000000000002', 'Compliance Overview', 'Application Component', 'instance'),
  summary('c0000000-0000-4000-8000-000000000003', 'My Integration Candidates', 'Application Component', 'personal'),
]

export const CATALOG = {
  ...CATALOGS[0]!,
  description: 'All applications of the group with their lifecycle status',
  defaultJoinMode: 'aggregate',
  lastModifiedAt: '2026-10-19T10:00:00Z',
  columns: [],
  joinDefinitions: [],
  savedFilters: [],
  savedViews: [],
}

export const NAME_TAKEN = {
  status: 422,
  contentType: 'application/problem+json',
  body: {
    type: 'urn:oea:problem:catalog-name-taken',
    title: 'Catalog name taken',
    status: 422,
    detail: 'A catalog with this name already exists',
    invalidFields: ['name'],
  },
}

/** The enum property behind the column `status` (US-140). */
export const STATUS_DEFINITION = {
  id: 'fc59811d-d59e-539e-877a-c9164435eba7',
  name: 'Status',
  dataType: 'enum',
  scope: 'imported',
  sqlColumnName: 'status',
  enumValues: [
    { id: 'a3474cb3-3251-5979-82c0-1684659d9d39', value: 'planned', sortOrder: 0 },
    { id: '9fc16c24-3814-5c3a-aa99-7aba8ef9ede9', value: 'active', sortOrder: 1 },
  ],
}

/** An organization property mapped to applications, for the column configuration (US-047). */
export const VENDOR_DEFINITION = {
  id: 'fd000000-0000-4000-8000-0000000000aa',
  name: 'Vendor',
  dataType: 'string',
  scope: 'organization',
  sqlColumnName: 'vendor',
  enumValues: [],
}

/** Application Component with its mappings, as `getMetatype` answers. */
export const APPLICATION_COMPONENT = {
  ...METATYPES[1]!,
  propertyMappings: [
    { id: 'b0000000-0000-4000-8000-000000000001', propertyDefinition: STATUS_DEFINITION, necessity: 'optional', validFrom: '2026-01-01', sortOrder: 0 },
    { id: 'b0000000-0000-4000-8000-000000000002', propertyDefinition: VENDOR_DEFINITION, necessity: 'optional', validFrom: '2026-01-01', sortOrder: 1 },
  ],
}

/** The protocol of an interface, for the joins (US-048). */
export const PROTOCOL_DEFINITION = {
  id: 'fd000000-0000-4000-8000-0000000000bb',
  name: 'Protocol',
  dataType: 'string',
  scope: 'organization',
  sqlColumnName: 'protocol',
  enumValues: [],
}

/** Interfaces, reached from applications through the connection metatype Data Flow (US-048). */
export const INTERFACE = {
  ...metatype('5a000000-0000-4000-8000-000000000010', 'Interface', 10),
  propertyMappings: [
    { id: 'b0000000-0000-4000-8000-000000000010', propertyDefinition: PROTOCOL_DEFINITION, necessity: 'optional', validFrom: '2026-01-01', sortOrder: 0 },
  ],
}

export const DATA_FLOW = {
  ...metatype('5a000000-0000-4000-8000-000000000020', 'Data Flow', 20),
  isConnection: true,
  connectionConstraints: [
    { direction: 'source', allowedMetatype: { id: METATYPES[1]!.id, name: 'Application Component', abbreviation: '' } },
    { direction: 'target', allowedMetatype: { id: '5a000000-0000-4000-8000-000000000010', name: 'Interface', abbreviation: '' } },
  ],
}

/** The metatypes with interfaces and data flows, as `listMetatypes` answers on the catalog page. */
export const METATYPES_WITH_CONNECTIONS = [...METATYPES, INTERFACE, DATA_FLOW]

/** The join "Interfaces" of the catalog (US-048). */
export const INTERFACES_JOIN = {
  id: 'f0000000-0000-4000-8000-000000000001',
  name: 'Interfaces',
  connectionMetatypeId: DATA_FLOW.id,
  joinDirection: 'outbound',
  targetMetatypeId: INTERFACE.id,
  targetColumns: [{ field: 'name' }, { propertyDefId: PROTOCOL_DEFINITION.id }],
  defaultJoinMode: null,
  available: true,
}

/** German labels of the starter package for the catalog screens (US-140). */
export const GERMAN_LABELS: Record<string, string> = {
  'metatype.5a000000-0000-4000-8000-000000000001.name': 'Anwendungskomponente',
  'property-def.fc59811d-d59e-539e-877a-c9164435eba7.label': 'Status',
  'enum-value.a3474cb3-3251-5979-82c0-1684659d9d39.label': 'Geplant',
  'enum-value.9fc16c24-3814-5c3a-aa99-7aba8ef9ede9.label': 'Aktiv',
}

/** An application row of the catalog query (US-049). */
export interface Application {
  name: string
  status?: string
  vendor?: string
  description?: string
  /** The interfaces reached through data flows (US-048). */
  interfaces?: { name: string; protocol?: string }[]
}

export const APPLICATIONS: Application[] = [
  {
    name: 'CRM System',
    status: 'active',
    vendor: 'Salesforce',
    description: 'Customer relations',
    interfaces: [
      { name: 'REST API', protocol: 'REST' },
      { name: 'SFTP Export', protocol: 'SFTP' },
    ],
  },
  { name: 'ERP', status: 'active', vendor: 'SAP' },
  { name: 'Intranet', status: 'planned', interfaces: [{ name: 'LDAP', protocol: 'LDAP' }] },
]

/** `count` applications "Application 001" … with alternating status. */
export function manyApplications(count: number): Application[] {
  return Array.from({ length: count }, (_, i) => ({
    name: `Application ${String(i + 1).padStart(3, '0')}`,
    status: i % 3 === 0 ? undefined : i % 3 === 1 ? 'active' : 'planned',
  }))
}

/** A request the catalog API received; `query` holds the parameters of a catalog query. */
export interface CatalogRequest {
  path: string
  query: Record<string, string>
  method?: string
  body?: unknown
}

/**
 * A fetch for the catalog page: session, catalog and a catalog query that sorts and pages the
 * applications like the server (design D1–D3 of us-049-show-catalog-data).
 */
export function catalogApi(
  applications: Application[],
  options: { me?: unknown; catalog?: unknown; recorded?: CatalogRequest[]; throttledFor?: number } = {},
): typeof fetch {
  // the catalog is state: saving columns changes what the next query returns (US-047)
  type ColumnConfig = { field?: string; propertyDefId?: string; displayLabel?: string | null; visible?: boolean; sortable?: boolean; defaultSortDirection?: string | null }
  type Join = { id: string; name: string; targetColumns: ColumnConfig[]; defaultJoinMode?: string | null }
  const catalog = structuredClone(options.catalog ?? CATALOG) as {
    id: string
    defaultJoinMode: string
    columns: ColumnConfig[]
    joinDefinitions: Join[]
  }
  catalog.joinDefinitions ??= []
  const definitions = [STATUS_DEFINITION, VENDOR_DEFINITION, PROTOCOL_DEFINITION]
  let joinCount = 0
  const resolved = () => {
    const configured = catalog.columns
      .filter((column) => column.visible !== false)
      .map((column) => {
        const definition = definitions.find((candidate) => candidate.id === column.propertyDefId)
        const key = column.field ?? definition!.sqlColumnName
        return { key, label: column.displayLabel ?? (definition?.name ?? key[0]!.toUpperCase() + key.slice(1)), sortable: column.sortable !== false }
      })
    return configured.length > 0
      ? configured
      : [
          { key: 'name', label: 'Name', sortable: true },
          { key: 'status', label: 'Status', sortable: true },
        ]
  }
  return async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://oea.test')
    const query = Object.fromEntries(url.searchParams.entries())
    const method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    options.recorded?.push({ path: url.pathname, query, method, body })
    const json = (body: unknown, status = 200) =>
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
      })
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_ARCHITECT)
    }
    if (url.pathname === '/api/v1/catalogs') {
      return json(CATALOGS)
    }
    if (url.pathname === '/api/v1/metamodel/property-definitions') {
      return json(definitions)
    }
    if (url.pathname === '/api/v1/metamodel/metatypes') {
      return json(METATYPES_WITH_CONNECTIONS)
    }
    if (url.pathname === `/api/v1/metamodel/metatypes/${INTERFACE.id}`) {
      return json(INTERFACE)
    }
    if (url.pathname === `/api/v1/catalogs/${catalog.id}/joins` && method === 'POST') {
      joinCount += 1
      catalog.joinDefinitions.push({ ...(body as Join), id: `f0000000-0000-4000-8000-1000000000${String(joinCount).padStart(2, '0')}`, available: true } as Join)
      return json(catalog, 201)
    }
    const joinPath = new RegExp(`^/api/v1/catalogs/${catalog.id}/joins/([0-9a-f-]+)$`).exec(url.pathname)
    if (joinPath) {
      const index = catalog.joinDefinitions.findIndex((join) => join.id === joinPath[1])
      if (index < 0) {
        return json({ type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 }, 404)
      }
      if (method === 'DELETE') {
        catalog.joinDefinitions.splice(index, 1)
      } else {
        catalog.joinDefinitions[index] = { ...(body as Join), id: joinPath[1]!, available: true } as Join
      }
      return json(catalog)
    }
    if (url.pathname === `/api/v1/metamodel/metatypes/${APPLICATION_COMPONENT.id}`) {
      return json(APPLICATION_COMPONENT)
    }
    if (url.pathname === `/api/v1/catalogs/${catalog.id}/columns` && method === 'PUT') {
      const columns = (body?.columns ?? []) as typeof catalog.columns
      if (columns.length > 0 && !columns.some((column) => column.visible !== false)) {
        return json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid catalog', status: 422, invalidFields: ['columns'] }, 422)
      }
      catalog.columns = columns.map((column) => ({ ...column, available: true }))
      return json(catalog)
    }
    if (url.pathname === '/api/v1/i18n/de') {
      return new Response(JSON.stringify(GERMAN_LABELS), {
        headers: { 'Content-Type': 'application/json', ETag: 'W/"labels-de"' },
      })
    }
    if (url.pathname === `/api/v1/catalogs/${catalog.id}`) {
      return json(catalog)
    }
    if (options.throttledFor !== undefined && url.pathname === `/api/v1/catalogs/${catalog.id}/query`) {
      return new Response(JSON.stringify({ type: 'urn:oea:problem:too-many-requests', title: 'Too many requests', status: 429 }), {
        status: 429,
        headers: { 'Content-Type': 'application/problem+json', 'Retry-After': String(options.throttledFor) },
      })
    }
    if (url.pathname !== `/api/v1/catalogs/${catalog.id}/query`) {
      return json({ type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 }, 404)
    }
    const page = Number(query.page ?? '0')
    const pageSize = Number(query.pageSize ?? '50')
    const columns = resolved()
    // joins: the interfaces of the applications, in their mode (US-048, US-050)
    const modes = Object.fromEntries(
      Object.entries(query)
        .filter(([name]) => name.startsWith('joinMode['))
        .map(([name, value]) => [name.slice('joinMode['.length, -1), value]),
    )
    if (Object.keys(modes).some((id) => !catalog.joinDefinitions.some((join) => join.id === id))) {
      return json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid query', status: 400, invalidFields: ['joinMode'] }, 400)
    }
    const joins = catalog.joinDefinitions.map((join) => {
      const mode = modes[join.id] ?? join.defaultJoinMode ?? catalog.defaultJoinMode
      const joinColumns = join.targetColumns
        .filter((column) => column.visible !== false)
        .map((column) => {
          const key = column.field ?? 'protocol'
          return { key, label: column.displayLabel ?? (column.field ? 'Name' : 'Protocol'), sortable: mode === 'rows' && column.sortable !== false }
        })
      return { id: join.id, name: join.name, mode, columns: joinColumns }
    })
    const rowsJoin = joins.find((join) => join.mode === 'rows')
    const match = query.sort === undefined ? null : /^((?:[0-9a-f-]{36}\.)?[a-zA-Z_]+):(asc|desc)$/.exec(query.sort)
    const sortable = (key: string) =>
      columns.some((column) => column.key === key && column.sortable) ||
      joins.some((join) => join.columns.some((column) => `${join.id}.${column.key}` === key && column.sortable))
    if (query.sort !== undefined && (!match || !sortable(match[1]!))) {
      return json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid query', status: 400, invalidFields: ['sort'] }, 400)
    }
    type Expanded = Application & { joined?: { name: string; protocol?: string } }
    const expanded: Expanded[] = applications.flatMap((application) =>
      rowsJoin && (application.interfaces ?? []).length > 0
        ? application.interfaces!.map((joined) => ({ ...application, joined }))
        : [{ ...application }],
    )
    const joinKey = match?.[1]?.includes('.') ? (match[1].split('.')[1] as 'name' | 'protocol') : undefined
    const key = (match?.[1] ?? 'name') as keyof Application
    const direction = match?.[2] === 'desc' ? -1 : 1
    const sorted = [...expanded].sort((a, b) => {
      const left = joinKey ? a.joined?.[joinKey]?.toLowerCase() : (a[key] as string | undefined)?.toLowerCase()
      const right = joinKey ? b.joined?.[joinKey]?.toLowerCase() : (b[key] as string | undefined)?.toLowerCase()
      if (left === right) {
        return a.name.toLowerCase().localeCompare(b.name.toLowerCase())
      }
      if (left === undefined) {
        return 1
      }
      if (right === undefined) {
        return -1
      }
      return direction * left.localeCompare(right)
    })
    const result = (joined: { name: string; protocol?: string }, index: number) => ({
      entityId: 100 + index,
      values: { name: joined.name, protocol: joined.protocol ?? null },
    })
    return json({
      columns,
      joins,
      rows: sorted.slice(page * pageSize, (page + 1) * pageSize).map((application) => ({
        entityId: applications.findIndex((candidate) => candidate.name === application.name) + 1,
        values: Object.fromEntries(
          columns.map((column) => [column.key, (application[column.key as keyof Application] as string | undefined) ?? null]),
        ),
        joinResults: Object.fromEntries(
          joins.map((join) => [
            join.id,
            join.mode === 'rows'
              ? application.joined
                ? [result(application.joined, 0)]
                : []
              : (application.interfaces ?? []).map(result),
          ]),
        ),
      })),
      page: { page, pageSize, totalElements: sorted.length, totalPages: Math.ceil(sorted.length / pageSize) },
      sort: match ? { column: match[1], direction: match[2] } : null,
    })
  }
}
