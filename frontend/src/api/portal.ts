import type { CatalogQueryResult, CatalogSort, CatalogSummary } from './catalogs'
import type { DashboardConfig, DashboardData, DashboardSummary, WidgetData } from './dashboards'
import { readRejection, RequestRejected } from './problem'
import type { Outcome } from './session'

// The portal API of the web portal (SCR-050; design D7 of s9-portal-catalogs): the shared
// catalogs and, since us-061-portal-dashboards, the dashboards read-only for readers with portal:read.

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** Paging and sort of a portal query. */
export interface PortalQuery {
  page?: number
  pageSize?: number
  sort?: CatalogSort
  /** The `nextCursor` of the previous page, sent with `page` (design D6 of s9-catalog-query-performance). */
  cursor?: string
}

/** GET /portal/catalogs: the shared catalogs, ordered by name. */
export async function fetchPortalCatalogs(fetchFn: typeof fetch = fetch): Promise<CatalogSummary[]> {
  const response = await fetchFn('/api/v1/portal/catalogs', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as CatalogSummary[]
}

/** GET /portal/catalogs/{id}/query: one page of a shared catalog; 404 for a personal or unknown catalog. */
export async function queryPortalCatalog(
  id: string,
  query: PortalQuery = {},
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<CatalogQueryResult>> {
  const params = new URLSearchParams()
  if (query.page !== undefined) {
    params.set('page', String(query.page))
  }
  if (query.pageSize !== undefined) {
    params.set('pageSize', String(query.pageSize))
  }
  if (query.sort) {
    params.set('sort', `${query.sort.column}:${query.sort.direction}`)
  }
  if (query.cursor) {
    params.set('cursor', query.cursor)
  }
  const search = params.toString()
  const response = await fetchFn(
    `/api/v1/portal/catalogs/${encodeURIComponent(id)}/query${search ? `?${search}` : ''}`,
    { credentials: 'same-origin', headers: JSON_HEADERS },
  )
  if (response.ok) {
    return { ok: true, value: (await response.json()) as CatalogQueryResult }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** GET /portal/dashboards: the shared dashboards and the caller's own personal ones, ordered by name (US-061). */
export async function fetchPortalDashboards(fetchFn: typeof fetch = fetch): Promise<DashboardSummary[]> {
  const response = await fetchFn('/api/v1/portal/dashboards', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DashboardSummary[]
}

/** GET /portal/dashboards/{id}: `undefined` if the dashboard does not exist or is not visible in the portal (404). */
export async function fetchPortalDashboard(id: string, fetchFn: typeof fetch = fetch): Promise<DashboardConfig | undefined> {
  const response = await fetchFn(`/api/v1/portal/dashboards/${encodeURIComponent(id)}`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (response.status === 404) {
    return undefined
  }
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DashboardConfig
}

/** GET /portal/dashboards/{id}/data: the data of all widgets, computed with the reader's visibility. */
export async function fetchPortalDashboardData(id: string, fetchFn: typeof fetch = fetch): Promise<DashboardData> {
  const response = await fetchFn(`/api/v1/portal/dashboards/${encodeURIComponent(id)}/data`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DashboardData
}

/** GET /portal/dashboards/{id}/widgets/{widgetId}/data: another page of a table widget (`page` is 0-based). */
export async function fetchPortalWidgetData(
  id: string,
  widgetId: string,
  page: number,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<WidgetData>> {
  const response = await fetchFn(
    `/api/v1/portal/dashboards/${encodeURIComponent(id)}/widgets/${encodeURIComponent(widgetId)}/data?page=${page}`,
    { credentials: 'same-origin', headers: JSON_HEADERS },
  )
  if (response.ok) {
    return { ok: true, value: (await response.json()) as WidgetData }
  }
  return { ok: false, rejection: await readRejection(response) }
}
