import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken } from './session'

// Solutions (UC-05, US-038; design D4 of us-038-create-solution): the overview (SCR-060), one
// solution (SCR-061) with its baseline (US-039) and its entity deltas (US-040, US-042), and creating
// a solution in project mode.

export type Solution = components['schemas']['Solution']
export type SolutionSummary = components['schemas']['SolutionSummary']
export type SolutionCreate = components['schemas']['SolutionCreate']
export type SolutionLandscapePage = components['schemas']['SolutionLandscapePage']
export type SolutionLandscapeEntry = components['schemas']['SolutionLandscapeEntry']
export type SolutionDelta = components['schemas']['SolutionDelta']
export type SolutionDeltaWrite = components['schemas']['SolutionDeltaWrite']
export type SolutionDeltaUpdate = components['schemas']['SolutionDeltaUpdate']
export type SolutionDeltaChange = components['schemas']['SolutionDeltaChange']
export type SolutionDeltaValue = components['schemas']['SolutionDeltaValue']
export type SolutionLandscapeEntity = components['schemas']['SolutionLandscapeEntity']
export type SolutionDeltaList = components['schemas']['SolutionDeltaList']
export type SolutionDeltaCounts = components['schemas']['SolutionDeltaCounts']
export type SolutionDiff = components['schemas']['SolutionDiff']
export type SolutionDiffEntry = components['schemas']['SolutionDiffEntry']
export type SolutionDiffField = components['schemas']['SolutionDiffField']

/** The parameters of a baseline query (design D4 of us-039-solution-baseline). */
export interface LandscapeQuery {
  q?: string
  metatypeId?: string
  page?: number
  pageSize?: number
  cursor?: string
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const SOLUTIONS = '/api/v1/solutions'

async function read<T>(path: string, fetchFn: typeof fetch): Promise<T> {
  const response = await fetchFn(path, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as T
}

/** GET /solutions: all solutions, ordered by name. */
export function fetchSolutions(fetchFn: typeof fetch = fetch): Promise<SolutionSummary[]> {
  return read(SOLUTIONS, fetchFn)
}

/** GET /solutions/{id}: one solution with its architecture vision; 404 for an unknown ID. */
export function fetchSolution(id: string, fetchFn: typeof fetch = fetch): Promise<Solution> {
  return read(`${SOLUTIONS}/${encodeURIComponent(id)}`, fetchFn)
}

/**
 * GET /solutions/{id}/landscape: one page of the baseline, the current catalog (US-039). A cursor
 * of another query is rejected with 400 naming `cursor`; the caller then repeats without it.
 */
export async function fetchSolutionLandscape(
  id: string,
  query: LandscapeQuery,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: SolutionLandscapePage } | { ok: false; rejection: Rejection }> {
  const params = new URLSearchParams()
  if (query.q && query.q.trim().length > 0) params.set('q', query.q)
  if (query.metatypeId) params.set('metatypeId', query.metatypeId)
  if (query.page) params.set('page', String(query.page))
  if (query.pageSize) params.set('pageSize', String(query.pageSize))
  if (query.cursor) params.set('cursor', query.cursor)
  const search = params.toString()
  const response = await fetchFn(`${SOLUTIONS}/${encodeURIComponent(id)}/landscape${search ? `?${search}` : ''}`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as SolutionLandscapePage }
}

/** POST /solutions: a taken or blank name is `validation-failed` (422) on `name`. */
export async function createSolution(
  solution: SolutionCreate,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: Solution } | { ok: false; rejection: Rejection }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(SOLUTIONS, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify(solution),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as Solution }
}

function writeHeaders(): Record<string, string> {
  const csrfToken = currentCsrfToken()
  return { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) }
}

/** The paging of the delta list and the diff (design D0 of us-043-diff-view). */
export interface DeltaPageQuery {
  page?: number
  pageSize?: number
  cursor?: string
}

function pageSearch(query: DeltaPageQuery): string {
  const params = new URLSearchParams()
  if (query.page) params.set('page', String(query.page))
  if (query.pageSize) params.set('pageSize', String(query.pageSize))
  if (query.cursor) params.set('cursor', query.cursor)
  const search = params.toString()
  return search ? `?${search}` : ''
}

/**
 * GET /solutions/{id}/deltas: one page of the deltas, `new`, `modified`, then `retiring`, each by
 * name, with the counts per type over all pages. A rejected cursor is 400 naming `cursor`.
 */
export function fetchSolutionDeltas(id: string, query: DeltaPageQuery = {}, fetchFn: typeof fetch = fetch): Promise<SolutionDeltaList> {
  return read(`${SOLUTIONS}/${encodeURIComponent(id)}/deltas${pageSearch(query)}`, fetchFn)
}

/** GET /solutions/{id}/diff: one page of the diff, current and target state per delta (US-043). */
export function fetchSolutionDiff(id: string, query: DeltaPageQuery = {}, fetchFn: typeof fetch = fetch): Promise<SolutionDiff> {
  return read(`${SOLUTIONS}/${encodeURIComponent(id)}/diff${pageSearch(query)}`, fetchFn)
}

/**
 * POST /solutions/{id}/deltas: a rule violation is `validation-failed` (422) with a message per
 * field, a restricted value without write access 403, and a completed solution 409.
 */
export async function createSolutionDelta(
  id: string,
  delta: SolutionDeltaWrite,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: SolutionDelta } | { ok: false; rejection: Rejection }> {
  const response = await fetchFn(`${SOLUTIONS}/${encodeURIComponent(id)}/deltas`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: writeHeaders(),
    body: JSON.stringify(delta),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as SolutionDelta }
}

/**
 * GET /solutions/{id}/landscape/{entityId}: one entity of the baseline with its values, redacted
 * where the caller may not read them (US-041 AC2); 404 for an entity outside the baseline.
 */
export function fetchSolutionLandscapeEntity(
  id: string,
  entityId: number,
  fetchFn: typeof fetch = fetch,
): Promise<SolutionLandscapeEntity> {
  return read(`${SOLUTIONS}/${encodeURIComponent(id)}/landscape/${entityId}`, fetchFn)
}

/**
 * PUT /solutions/{id}/deltas/{deltaId}: replaces the changes of a delta `modified` (US-041); the
 * rejections are those of a capture, and 422 naming `deltaType` for another type.
 */
export async function updateSolutionDelta(
  id: string,
  deltaId: string,
  update: SolutionDeltaUpdate,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: SolutionDelta } | { ok: false; rejection: Rejection }> {
  const response = await fetchFn(`${SOLUTIONS}/${encodeURIComponent(id)}/deltas/${encodeURIComponent(deltaId)}`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: writeHeaders(),
    body: JSON.stringify(update),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as SolutionDelta }
}

/** DELETE /solutions/{id}/deltas/{deltaId}: 409 for a completed solution. */
export async function deleteSolutionDelta(
  id: string,
  deltaId: string,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true } | { ok: false; rejection: Rejection }> {
  const response = await fetchFn(`${SOLUTIONS}/${encodeURIComponent(id)}/deltas/${encodeURIComponent(deltaId)}`, {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: writeHeaders(),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true }
}
