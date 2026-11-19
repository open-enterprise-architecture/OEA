/** Test and story data for the portal pages (SCR-050, SCR-053; s9-portal-catalogs, us-061-portal-dashboards). */
import type { DashboardConfig } from '../api/dashboards'
import { APPLICATIONS, CATALOGS, ME_ARCHITECT, type Application } from './catalogs'
import { BAR_WIDGET, BROKEN_WIDGET, dashboard, FILLED_DASHBOARD, INVALID_WIDGET, KPI_AND_CHARTS_DASHBOARD, KPI_WIDGET, PERSONAL_DASHBOARD, PIE_WIDGET, position, TABLE_WIDGET, widgetData } from './dashboards'

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
  options: {
    me?: unknown
    catalogs?: unknown[]
    recorded?: string[]
    failing?: boolean
    pageSize?: number
    /** Rejects every cursor with 400 `cursor`, as after a restart of the backend. */
    rejectCursors?: boolean
    /** The reader may not see the status (us-126-role-restricted-enforcement). */
    statusUnreadable?: boolean
  } = {},
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
      const cursor = url.searchParams.get('cursor')
      if (cursor !== null && (options.rejectCursors || cursor !== `cursor-${page}`)) {
        return problem(400, 'Invalid query', ['cursor'])
      }
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
          options.statusUnreadable
            ? { key: 'status', label: 'Status', sortable: false, readable: false, filterType: 'enum', enumValues: ['active', 'planned'] }
            : { key: 'status', label: 'Status', sortable: true, filterType: 'enum', enumValues: ['active', 'planned'] },
        ],
        rows: slice.map((application, index) => ({
          entityId: page * pageSize + index + 1,
          values: { name: application.name, status: options.statusUnreadable ? null : (application.status ?? null) },
          joinResults: {},
        })),
        joins: [],
        page: { page, pageSize, totalElements: rows.length, totalPages: Math.ceil(rows.length / pageSize) },
        nextCursor: (page + 1) * pageSize < rows.length ? `cursor-${page + 1}` : null,
        sort: sortMatch ? { column: sortMatch[1], direction: sortMatch[2] } : null,
      })
    }
    throw new TypeError(`no route for ${url.pathname}`)
  }
}

/** The dashboards the portal fake lists by default: two shared ones and the reader's own personal one. */
export const PORTAL_DASHBOARDS: DashboardConfig[] = [
  FILLED_DASHBOARD,
  { ...KPI_AND_CHARTS_DASHBOARD, id: 'd0000000-0000-4000-8000-000000000012', name: 'Portfolio Health' },
  PERSONAL_DASHBOARD,
].map((config) => ({ ...config, canEdit: false }))
export const PORTAL_DASHBOARD = PORTAL_DASHBOARDS[0]!

/** The dashboard of the screen image SCR-053: a KPI, a table, a bar chart and a pie chart. */
export const PORTAL_OVERVIEW = dashboard({
  id: 'd0000000-0000-4000-8000-000000000010',
  name: 'IT Investment Planning',
  canEdit: false,
  widgets: [
    { ...KPI_WIDGET, position: position(1, 1, 4, 3) },
    { ...TABLE_WIDGET, position: position(5, 1, 8, 3) },
    { ...BAR_WIDGET, position: position(1, 4, 6, 3) },
    { ...PIE_WIDGET, position: position(7, 4, 6, 3) },
  ],
})

/** A dashboard with a widget that fails next to ones that work (the aggregation no longer fits, the catalog is gone). */
export const PORTAL_FAILING = dashboard({
  id: 'd0000000-0000-4000-8000-000000000011',
  name: 'Portfolio Health',
  canEdit: false,
  widgets: [
    { ...KPI_WIDGET, position: position(1, 1, 4, 2) },
    { ...INVALID_WIDGET, position: position(5, 1, 4, 2) },
    { ...BROKEN_WIDGET, position: position(9, 1, 4, 2) },
  ],
})

/**
 * A fake of the portal dashboard API on top of {@link portalApi}: the list, the configuration, the
 * data and the data of one widget of `dashboards`, always with `canEdit: false`. Every read of the
 * data carries a later `generatedAt`, so a refresh can be seen. `recorded` receives all paths.
 */
export function portalDashboardApi(
  options: {
    me?: unknown
    dashboards?: DashboardConfig[]
    recorded?: string[]
    /** Answers the list of dashboards with 500. */
    failingDashboards?: boolean
    /** Answers the list of catalogs with 500. */
    failingCatalogs?: boolean
    /** The shared catalogs the fake lists (default: all). */
    catalogs?: unknown[]
    /** Answers the data of a dashboard with 500 from the n-th read on (1-based). */
    failingDataFrom?: number
    restricted?: boolean
  } = {},
): typeof fetch {
  const dashboards = options.dashboards ?? PORTAL_DASHBOARDS
  const catalogsApi = portalApi(undefined, { me: options.me, failing: options.failingCatalogs, catalogs: options.catalogs })
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
  const problem = (status: number, title: string) => json({ type: 'urn:oea:problem:not-found', title, status }, status)
  let reads = 0
  return async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    options.recorded?.push(url.pathname + url.search)
    if (url.pathname === '/api/v1/portal/dashboards') {
      return options.failingDashboards
        ? problem(500, 'Internal error')
        : json(
            dashboards.map((config) => ({
              id: config.id,
              name: config.name,
              description: config.description,
              scope: config.scope,
              owner: config.owner,
              widgetCount: config.widgets.length,
              canEdit: false,
            })),
          )
    }
    const match = /^\/api\/v1\/portal\/dashboards\/([^/]+)(?:\/(data)|\/widgets\/([^/]+)\/data)?$/.exec(url.pathname)
    if (match) {
      const found = dashboards.find((config) => config.id === match[1])
      if (!found) {
        return problem(404, 'Not found')
      }
      if (match[2]) {
        reads += 1
        if (options.failingDataFrom !== undefined && reads >= options.failingDataFrom) {
          return problem(500, 'Internal error')
        }
        return json({
          dashboardId: found.id,
          generatedAt: new Date(Date.UTC(2026, 10, 19, 10, reads, 0)).toISOString(),
          widgets: found.widgets.map((widget) => widgetData(widget, 0, options.restricted)),
        })
      }
      if (match[3]) {
        const widget = found.widgets.find((candidate) => candidate.id === match[3])
        return widget ? json(widgetData(widget, Number(url.searchParams.get('page') ?? 0), options.restricted)) : problem(404, 'Not found')
      }
      return json({ ...found, canEdit: false })
    }
    return catalogsApi(input, init)
  }
}
