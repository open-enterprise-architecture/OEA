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
export type CatalogSavedFilter = components['schemas']['CatalogSavedFilter']
export type CatalogSavedFilterInput = components['schemas']['CatalogSavedFilterInput']
export type CatalogFilterExpression = components['schemas']['CatalogFilterExpression']
export type CatalogFilterOperator = components['schemas']['CatalogFilterOperator']
export type CatalogFilterMode = components['schemas']['CatalogFilterMode']
export type CatalogFilterType = CatalogColumn['filterType']
export type CatalogSavedView = components['schemas']['CatalogSavedView']
export type CatalogSavedViewInput = components['schemas']['CatalogSavedViewInput']

/** The operators per filter type, in the order the filter bar offers them (US-051, catalog BR-12). */
export const FILTER_OPERATORS: Record<CatalogFilterType, CatalogFilterOperator[]> = {
  text: ['eq', 'neq', 'contains', 'startsWith', 'in', 'notIn', 'isNull', 'isNotNull'],
  enum: ['eq', 'neq', 'in', 'notIn', 'isNull', 'isNotNull'],
  boolean: ['eq', 'neq', 'isNull', 'isNotNull'],
  number: ['eq', 'neq', 'gt', 'lt', 'gte', 'lte', 'isNull', 'isNotNull'],
  date: ['eq', 'neq', 'gt', 'lt', 'gte', 'lte', 'isNull', 'isNotNull'],
}

/** At most this many filters apply to a query (US-051). */
export const MAX_FILTERS = 20

/** A filter on a column of the result: no values for `isNull`/`isNotNull`, several for `in`/`notIn`. */
export interface CatalogFilter {
  key: string
  operator: CatalogFilterOperator
  values: string[]
}

const OPERATORS = new Set<string>(Object.values(FILTER_OPERATORS).flat())

/** Whether the operator takes a list of values. */
export function takesList(operator: CatalogFilterOperator): boolean {
  return operator === 'in' || operator === 'notIn'
}

/** Whether the operator takes no value. */
export function takesNoValue(operator: CatalogFilterOperator): boolean {
  return operator === 'isNull' || operator === 'isNotNull'
}

/** The parameter form `{key}:{operator}[:{value}]` of a filter; list values are comma-separated. */
export function filterText(filter: CatalogFilter): string {
  return filter.values.length > 0 ? `${filter.key}:${filter.operator}:${filter.values.join(',')}` : `${filter.key}:${filter.operator}`
}

/** The filter of a parameter `{key}:{operator}[:{value}]`; `undefined` if it does not parse. */
export function parseFilter(text: string): CatalogFilter | undefined {
  const match = /^([^:]+):([a-zA-Z]+)(?::([\s\S]*))?$/.exec(text)
  if (!match || !OPERATORS.has(match[2]!)) {
    return undefined
  }
  const operator = match[2] as CatalogFilterOperator
  const value = match[3]
  const values = value === undefined || value === '' ? [] : takesList(operator) ? value.split(',').map((item) => item.trim()).filter(Boolean) : [value]
  if (takesNoValue(operator) ? values.length > 0 : values.length === 0) {
    return undefined
  }
  return { key: match[1]!, operator, values }
}

/** The filter of a saved expression (US-052). */
export function filterOf(expression: CatalogFilterExpression): CatalogFilter {
  const value = expression.value
  const values = Array.isArray(value) ? value.map(String) : value === undefined || value === null ? [] : [String(value)]
  return { key: expression.attributePath, operator: expression.operator, values }
}

/** The saved expression of a filter (US-052). */
export function expressionOf(filter: CatalogFilter): CatalogFilterExpression {
  if (takesNoValue(filter.operator)) {
    return { attributePath: filter.key, operator: filter.operator }
  }
  return { attributePath: filter.key, operator: filter.operator, value: takesList(filter.operator) ? filter.values : filter.values[0] }
}

/** The system fields of every entity, in the order the configuration offers them (US-047). */
export const SYSTEM_FIELDS: CatalogSystemField[] = ['name', 'description', 'externalId', 'createdAt', 'updatedAt', 'entityVersion']

/** Paging and sort of a catalog query; omitted values are the defaults of the contract. */
export interface CatalogQuery {
  page?: number
  pageSize?: number
  sort?: CatalogSort
  /** Join mode per join ID for this query only (US-050). */
  joinModes?: Record<string, JoinMode>
  /** Filters `{key}:{operator}[:{value}]` on the columns of the result (US-051). */
  filters?: string[]
  /** How the filters combine; `and` is the default. */
  filterMode?: CatalogFilterMode
  /** A saved view whose columns the result shows (US-053). */
  view?: string
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

/**
 * A write of the joins or saved filters with the CSRF token (US-048, US-052); invalid input is
 * `validation-failed` (422).
 */
async function write(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  join: CatalogJoinInput | CatalogSavedFilterInput | CatalogSavedViewInput | undefined,
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
  return write('POST', `/api/v1/catalogs/${encodeURIComponent(id)}/joins`, join, fetchFn)
}

/** PUT /catalogs/{id}/joins/{joinId}: replaces the join and keeps its ID (US-048). */
export function updateCatalogJoin(
  id: string,
  joinId: string,
  join: CatalogJoinInput,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<Catalog>> {
  return write('PUT', `/api/v1/catalogs/${encodeURIComponent(id)}/joins/${encodeURIComponent(joinId)}`, join, fetchFn)
}

/**
 * POST /catalogs/{id}/filters: saves a filter (US-052). A taken name or an invalid expression is
 * `validation-failed` (422) with `invalidFields`, missing rights 403.
 */
export function addCatalogFilter(
  id: string,
  filter: CatalogSavedFilterInput,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<Catalog>> {
  return write('POST', `/api/v1/catalogs/${encodeURIComponent(id)}/filters`, filter, fetchFn)
}

/**
 * POST /catalogs/{id}/views: saves a view (US-053). A taken name or an invalid column, filter or
 * sort is `validation-failed` (422) with `invalidFields`, missing rights 403.
 */
export function addCatalogView(id: string, view: CatalogSavedViewInput, fetchFn: typeof fetch = fetch): Promise<Outcome<Catalog>> {
  return write('POST', `/api/v1/catalogs/${encodeURIComponent(id)}/views`, view, fetchFn)
}

/** PUT /catalogs/{id}/views/{viewId}: replaces a view and keeps its ID (US-053). */
export function updateCatalogView(
  id: string,
  viewId: string,
  view: CatalogSavedViewInput,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<Catalog>> {
  return write('PUT', `/api/v1/catalogs/${encodeURIComponent(id)}/views/${encodeURIComponent(viewId)}`, view, fetchFn)
}

/** DELETE /catalogs/{id}/views/{viewId}: removes a view (US-053). */
export function removeCatalogView(id: string, viewId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<Catalog>> {
  return write('DELETE', `/api/v1/catalogs/${encodeURIComponent(id)}/views/${encodeURIComponent(viewId)}`, undefined, fetchFn)
}

/** DELETE /catalogs/{id}/filters/{filterId}: removes a saved filter (US-052). */
export function removeCatalogFilter(id: string, filterId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<Catalog>> {
  return write('DELETE', `/api/v1/catalogs/${encodeURIComponent(id)}/filters/${encodeURIComponent(filterId)}`, undefined, fetchFn)
}

/** DELETE /catalogs/{id}/joins/{joinId}: removes the join (US-048). */
export function removeCatalogJoin(id: string, joinId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<Catalog>> {
  return write('DELETE', `/api/v1/catalogs/${encodeURIComponent(id)}/joins/${encodeURIComponent(joinId)}`, undefined, fetchFn)
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
  for (const filter of query.filters ?? []) {
    params.append('filter', filter)
  }
  if (query.filterMode === 'or') {
    params.set('filterMode', 'or')
  }
  if (query.view) {
    params.set('view', query.view)
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
