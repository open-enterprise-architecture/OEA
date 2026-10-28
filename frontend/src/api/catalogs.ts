import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken, type Outcome } from './session'

export type Catalog = components['schemas']['Catalog']
export type CatalogSummary = components['schemas']['CatalogSummary']
export type CatalogCreate = components['schemas']['CatalogCreate']
export type CatalogScope = components['schemas']['CatalogScope']
export type JoinMode = components['schemas']['JoinMode']
export type CatalogColumn = components['schemas']['CatalogColumn']
export type CatalogRow = components['schemas']['CatalogRow']
export type CatalogSort = components['schemas']['CatalogSort']
export type CatalogQueryResult = components['schemas']['CatalogQueryResult']

/** Paging and sort of a catalog query; omitted values are the defaults of the contract. */
export interface CatalogQuery {
  page?: number
  pageSize?: number
  sort?: CatalogSort
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** GET /catalogs: instance catalogs and the caller's personal catalogs, ordered by name (US-046). */
export async function fetchCatalogs(fetchFn: typeof fetch = fetch): Promise<CatalogSummary[]> {
  const response = await fetchFn('/api/v1/catalogs', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as CatalogSummary[]
}

/** GET /catalogs/{id}: `undefined` if the catalog does not exist or is another person's personal catalog. */
export async function fetchCatalog(id: string, fetchFn: typeof fetch = fetch): Promise<Catalog | undefined> {
  const response = await fetchFn(`/api/v1/catalogs/${encodeURIComponent(id)}`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (response.status === 404) {
    return undefined
  }
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as Catalog
}

/**
 * POST /catalogs with the CSRF token of the session. A taken name is the rejection
 * `catalog-name-taken`, other invalid input `validation-failed`, both with `invalidFields`.
 */
export async function createCatalog(request: CatalogCreate, fetchFn: typeof fetch = fetch): Promise<Outcome<Catalog>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn('/api/v1/catalogs', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      'Content-Type': 'application/json',
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    body: JSON.stringify(request),
  })
  if (response.status === 201) {
    return { ok: true, value: (await response.json()) as Catalog }
  }
  const rejection: Rejection = await readRejection(response)
  return { ok: false, rejection }
}

/**
 * GET /catalogs/{id}/query: one page of rows (US-049). A missing or foreign personal catalog is
 * the rejection with status 404, invalid paging or sort parameters `validation-failed` (400) with
 * `invalidFields`.
 */
export async function queryCatalog(
  id: string,
  query: CatalogQuery = {},
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
  const response = await fetchFn(`/api/v1/catalogs/${encodeURIComponent(id)}/query${search ? `?${search}` : ''}`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as CatalogQueryResult }
  }
  return { ok: false, rejection: await readRejection(response) }
}
