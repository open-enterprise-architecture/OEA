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

/** An application row of the catalog query (US-049). */
export interface Application {
  name: string
  status?: string
}

export const APPLICATIONS: Application[] = [
  { name: 'CRM System', status: 'active' },
  { name: 'ERP', status: 'active' },
  { name: 'Intranet', status: 'planned' },
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
}

/**
 * A fetch for the catalog page: session, catalog and a catalog query that sorts and pages the
 * applications like the server (design D1–D3 of us-049-show-catalog-data).
 */
export function catalogApi(
  applications: Application[],
  options: { me?: unknown; catalog?: unknown; recorded?: CatalogRequest[]; throttledFor?: number } = {},
): typeof fetch {
  const catalog = (options.catalog ?? CATALOG) as { id: string }
  const columns = [
    { key: 'name', label: 'Name', sortable: true },
    { key: 'status', label: 'Status', sortable: true },
  ]
  return async (input) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://oea.test')
    const query = Object.fromEntries(url.searchParams.entries())
    options.recorded?.push({ path: url.pathname, query })
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
    const match = query.sort === undefined ? null : /^(name|status):(asc|desc)$/.exec(query.sort)
    if (query.sort !== undefined && !match) {
      return json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid query', status: 400, invalidFields: ['sort'] }, 400)
    }
    const key = (match?.[1] ?? 'name') as keyof Application
    const direction = match?.[2] === 'desc' ? -1 : 1
    const sorted = [...applications].sort((a, b) => {
      const left = a[key]?.toLowerCase()
      const right = b[key]?.toLowerCase()
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
    return json({
      columns,
      rows: sorted.slice(page * pageSize, (page + 1) * pageSize).map((application) => ({
        entityId: applications.indexOf(application) + 1,
        values: { name: application.name, status: application.status ?? null },
      })),
      page: { page, pageSize, totalElements: applications.length, totalPages: Math.ceil(applications.length / pageSize) },
      sort: match ? { column: match[1], direction: match[2] } : null,
    })
  }
}
