/** Test and story data for the portal pages (SCR-050, s9-portal-catalogs). */
import { APPLICATIONS, CATALOGS, ME_ARCHITECT, type Application } from './catalogs'

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 9, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-portal',
  }
}

export const ME_PORTAL_READER = me('Petra', ['portal:read'])
export { ME_ARCHITECT }

/** The shared catalogs of the portal; the personal catalog of CATALOGS is never listed. */
export const SHARED_CATALOGS = CATALOGS.filter((catalog) => catalog.scope === 'instance')

export const INVENTORY_ID = SHARED_CATALOGS[0]!.id

/**
 * A fake of the portal API: `/me`, the list of shared catalogs and their query with sort and
 * paging over `applications` (columns "Name" and "Status"). `recorded` receives the paths;
 * `failing` answers the list with 500.
 */
export function portalApi(
  applications: Application[] = APPLICATIONS,
  options: { me?: unknown; catalogs?: unknown[]; recorded?: string[]; failing?: boolean; pageSize?: number } = {},
): typeof fetch {
  const catalogs = (options.catalogs ?? SHARED_CATALOGS) as { id: string }[]
  const json = (body: unknown, status = 200, contentType = 'application/json') =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
  const problem = (status: number, title: string, invalidFields?: string[]) =>
    json({ type: 'urn:oea:problem:validation-failed', title, status, invalidFields }, status, 'application/problem+json')
  return async (input) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    options.recorded?.push(url.pathname + url.search)
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_PORTAL_READER)
    }
    if (url.pathname === '/api/v1/portal/catalogs') {
      return options.failing ? problem(500, 'Internal error') : json(catalogs)
    }
    const match = /^\/api\/v1\/portal\/catalogs\/([^/]+)\/query$/.exec(url.pathname)
    if (match) {
      if (!catalogs.some((catalog) => catalog.id === match[1])) {
        return problem(404, 'Not found')
      }
      const sort = url.searchParams.get('sort')
      const sortMatch = sort ? /^(name|status):(asc|desc)$/.exec(sort) : null
      if (sort && !sortMatch) {
        return problem(400, 'Invalid query', ['sort'])
      }
      const pageSize = Number(url.searchParams.get('pageSize') ?? options.pageSize ?? 50)
      const page = Number(url.searchParams.get('page') ?? 0)
      const rows = [...applications]
      if (sortMatch) {
        const key = sortMatch[1] as 'name' | 'status'
        rows.sort((a, b) => (a[key] ?? '').localeCompare(b[key] ?? ''))
        if (sortMatch[2] === 'desc') {
          rows.reverse()
        }
      } else {
        rows.sort((a, b) => a.name.localeCompare(b.name))
      }
      const slice = rows.slice(page * pageSize, (page + 1) * pageSize)
      return json({
        columns: [
          { key: 'name', label: 'Name', sortable: true, filterType: 'text' },
          { key: 'status', label: 'Status', sortable: true, filterType: 'enum', enumValues: ['active', 'planned'] },
        ],
        rows: slice.map((application, index) => ({
          entityId: page * pageSize + index + 1,
          values: { name: application.name, status: application.status ?? null },
          joinResults: {},
        })),
        joins: [],
        page: { page, pageSize, totalElements: rows.length, totalPages: Math.ceil(rows.length / pageSize) },
        sort: sortMatch ? { column: sortMatch[1], direction: sortMatch[2] } : null,
      })
    }
    throw new TypeError(`no route for ${url.pathname}`)
  }
}
