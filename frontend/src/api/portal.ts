import type { CatalogQueryResult, CatalogSort, CatalogSummary } from './catalogs'
import { readRejection, RequestRejected } from './problem'
import type { Outcome } from './session'

// The portal API of the web portal (SCR-050; design D7 of s9-portal-catalogs): the shared
// catalogs read-only for readers with portal:read.

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** Paging and sort of a portal query. */
export interface PortalQuery {
  page?: number
  pageSize?: number
  sort?: CatalogSort
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
