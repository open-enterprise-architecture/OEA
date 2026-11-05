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
export type CatalogColumnConfig = components['schemas']['CatalogColumnConfig']
export type CatalogSystemField = components['schemas']['CatalogSystemField']
export type CatalogJoinDefinition = components['schemas']['CatalogJoinDefinition']
export type CatalogJoinInput = components['schemas']['CatalogJoinInput']
export type CatalogQueryJoin = components['schemas']['CatalogQueryJoin']
export type CatalogJoinResult = components['schemas']['CatalogJoinResult']
export type JoinDirection = CatalogJoinInput['joinDirection']

/** The system fields of every entity, in the order the configuration offers them (US-047). */
export const SYSTEM_FIELDS: CatalogSystemField[] = ['name', 'description', 'externalId', 'createdAt', 'updatedAt', 'entityVersion']

/** Paging and sort of a catalog query; omitted values are the defaults of the contract. */
export interface CatalogQuery {
  page?: number
  pageSize?: number
  sort?: CatalogSort
  /** Join mode per join ID for this query only (US-050). */
  joinModes?: Record<string, JoinMode>
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
 * PUT /catalogs/{id}/columns with the CSRF token (US-047): the complete column list in display
 * order. Invalid columns are the rejection `validation-failed` (422) with `invalidFields`.
 */
export async function updateCatalogColumns(
  id: string,
  columns: CatalogColumnConfig[],
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<Catalog>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`/api/v1/catalogs/${encodeURIComponent(id)}/columns`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      'Content-Type': 'application/json',
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    body: JSON.stringify({ columns }),
  })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as Catalog }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** A write of the joins with the CSRF token (US-048); invalid input is `validation-failed` (422). */
async function writeJoin(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  join: CatalogJoinInput | undefined,
  fetchFn: typeof fetch,
): Promise<Outcome<Catalog>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(path, {
    method,
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      ...(join ? { 'Content-Type': 'application/json' } : {}),
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    ...(join ? { body: JSON.stringify(join) } : {}),
  })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as Catalog }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /catalogs/{id}/joins: appends a join with a new ID (US-048). */
export function addCatalogJoin(id: string, join: CatalogJoinInput, fetchFn: typeof fetch = fetch): Promise<Outcome<Catalog>> {
  return writeJoin('POST', `/api/v1/catalogs/${encodeURIComponent(id)}/joins`, join, fetchFn)
}

/** PUT /catalogs/{id}/joins/{joinId}: replaces the join and keeps its ID (US-048). */
export function updateCatalogJoin(
  id: string,
  joinId: string,
  join: CatalogJoinInput,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<Catalog>> {
  return writeJoin('PUT', `/api/v1/catalogs/${encodeURIComponent(id)}/joins/${encodeURIComponent(joinId)}`, join, fetchFn)
}

/** DELETE /catalogs/{id}/joins/{joinId}: removes the join (US-048). */
export function removeCatalogJoin(id: string, joinId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<Catalog>> {
  return writeJoin('DELETE', `/api/v1/catalogs/${encodeURIComponent(id)}/joins/${encodeURIComponent(joinId)}`, undefined, fetchFn)
}

/**
 * GET /catalogs/{id}/query: one page of rows (US-049). A missing or foreign personal catalog is
 * the rejection with status 404, invalid paging, sort or join mode parameters `validation-failed`
 * (400) with `invalidFields`.
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
  for (const [joinId, mode] of Object.entries(query.joinModes ?? {})) {
    params.set(`joinMode[${joinId}]`, mode)
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
