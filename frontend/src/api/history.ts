import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken } from './session'

// The change history of an entity (SCR-026, US-098; design D4, D5 of us-098-entity-history): the
// versions newest first with the fields they changed, without values, and the history visibility
// per role and metatype for security administration (section on SCR-032).

export type EntityHistory = components['schemas']['EntityHistoryResponse']
export type EntityVersion = components['schemas']['EntityVersionSummary']
export type EntityVersionChange = components['schemas']['EntityVersionChange']
export type HistoryActor = components['schemas']['EntityHistoryActor']
export type HistoryVisibilityEntry = components['schemas']['HistoryVisibilityEntry']
export type HistoryVisibilityMode = HistoryVisibilityEntry['mode']

/** The filters and the page of a history read. */
export interface HistoryQuery {
  from?: string
  to?: string
  actorId?: number
  page?: number
  pageSize?: number
  cursor?: string
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const HISTORY_VISIBILITY = '/api/v1/admin/history-visibility'

/**
 * GET /entities/{id}/history: one page of the versions. A reversed period is 400 naming `from`,
 * a cursor of another query 400 naming `cursor`, an unknown entity 404.
 */
export async function fetchEntityHistory(id: number, query: HistoryQuery = {}, fetchFn: typeof fetch = fetch): Promise<EntityHistory> {
  const params = new URLSearchParams()
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  if (query.actorId !== undefined) params.set('actorId', String(query.actorId))
  if (query.page) params.set('page', String(query.page))
  if (query.pageSize) params.set('pageSize', String(query.pageSize))
  if (query.cursor) params.set('cursor', query.cursor)
  const search = params.toString()
  const response = await fetchFn(`/api/v1/entities/${id}/history${search ? `?${search}` : ''}`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as EntityHistory
}

/** GET /admin/history-visibility: the configured entries; a pair that is not listed is `show-restricted`. */
export async function fetchHistoryVisibility(fetchFn: typeof fetch = fetch): Promise<HistoryVisibilityEntry[]> {
  const response = await fetchFn(HISTORY_VISIBILITY, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return ((await response.json()) as { entries: HistoryVisibilityEntry[] }).entries
}

/**
 * PUT /admin/history-visibility: sets the listed entries (1 to 500). An unknown role or
 * metatype is `validation-failed` (422) with `invalidFields` such as `entries[0].roleId`; nothing
 * changes then.
 */
export async function updateHistoryVisibility(
  entries: HistoryVisibilityEntry[],
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: HistoryVisibilityEntry[] } | { ok: false; rejection: Rejection }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(HISTORY_VISIBILITY, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify({ entries }),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: ((await response.json()) as { entries: HistoryVisibilityEntry[] }).entries }
}
