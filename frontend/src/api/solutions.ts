import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken } from './session'

// Solutions (UC-05, US-038; design D4 of us-038-create-solution): the overview (SCR-060), one
// solution (SCR-061) and creating a solution in project mode.

export type Solution = components['schemas']['Solution']
export type SolutionSummary = components['schemas']['SolutionSummary']
export type SolutionCreate = components['schemas']['SolutionCreate']

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
