import type { components } from './generated/schema'
import { readRejection, type Rejection } from './problem'
import { currentCsrfToken, type Outcome } from './session'

export type Catalog = components['schemas']['Catalog']
export type CatalogSummary = components['schemas']['CatalogSummary']
export type CatalogCreate = components['schemas']['CatalogCreate']
export type CatalogScope = components['schemas']['CatalogScope']
export type JoinMode = components['schemas']['JoinMode']

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** GET /catalogs: instance catalogs and the caller's personal catalogs, ordered by name (US-046). */
export async function fetchCatalogs(fetchFn: typeof fetch = fetch): Promise<CatalogSummary[]> {
  const response = await fetchFn('/api/v1/catalogs', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`catalogs not readable (HTTP ${response.status})`)
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
    throw new Error(`catalog not readable (HTTP ${response.status})`)
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
