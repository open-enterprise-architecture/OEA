import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken } from './session'

// Plateaus of plateau mode (UC-11, US-084, US-085, US-089, US-155; design D7, D8 of
// us-084-plateaus): the overview SCR-065, creating and editing a plateau, and the plateaus a
// solution may target.

export type Plateau = components['schemas']['Plateau']
export type PlateauCreate = components['schemas']['PlateauCreate']
export type PlateauUpdate = components['schemas']['PlateauUpdate']
export type PlateauBasis = components['schemas']['PlateauBasis']

export type PlateauOutcome = { ok: true; value: Plateau } | { ok: false; rejection: Rejection }

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const PLATEAUS = '/api/v1/plateaus'

function writeHeaders(): Record<string, string> {
  const csrfToken = currentCsrfToken()
  return { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) }
}

/** GET /plateaus: the baseline first, then the planned plateaus by `validFrom`, then the realized ones. */
export async function fetchPlateaus(fetchFn: typeof fetch = fetch): Promise<Plateau[]> {
  const response = await fetchFn(PLATEAUS, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as Plateau[]
}

async function write(path: string, method: 'POST' | 'PUT', body: unknown, fetchFn: typeof fetch): Promise<PlateauOutcome> {
  const response = await fetchFn(path, { method, credentials: 'same-origin', headers: writeHeaders(), body: JSON.stringify(body) })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as Plateau }
}

/** POST /plateaus: 422 names the field, or carries the detail of a rule ("A baseline plateau already exists"). */
export function createPlateau(plateau: PlateauCreate, fetchFn: typeof fetch = fetch): Promise<PlateauOutcome> {
  return write(PLATEAUS, 'POST', plateau, fetchFn)
}

/** PUT /plateaus/{id}: 422 for a realized plateau or a broken rule, 409 `plateau-in-use` for a new predecessor. */
export function updatePlateau(id: string, plateau: PlateauUpdate, fetchFn: typeof fetch = fetch): Promise<PlateauOutcome> {
  return write(`${PLATEAUS}/${encodeURIComponent(id)}`, 'PUT', plateau, fetchFn)
}

/** The plateaus a solution may target: target and transition plateaus that are not realized. */
export function plannedPlateaus(plateaus: Plateau[]): Plateau[] {
  return plateaus.filter((plateau) => plateau.status === 'target' || plateau.status === 'transition')
}

export type PlateauGoLivePreview = components['schemas']['PlateauGoLivePreview']
export type PlateauGoLiveResult = components['schemas']['PlateauGoLiveResult']

/**
 * GET /plateaus/{id}/go-live: what the go-live of the plateau would do (US-086, US-087, US-157);
 * 409 `plateau-not-next` for a plateau that does not succeed the baseline.
 */
export async function fetchPlateauGoLivePreview(
  id: string,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: PlateauGoLivePreview } | { ok: false; rejection: Rejection }> {
  const response = await fetchFn(`${PLATEAUS}/${encodeURIComponent(id)}/go-live`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as PlateauGoLivePreview }
}

/**
 * POST /plateaus/{id}/go-live with the exact name of the plateau (US-086, US-088): 422 naming
 * `name` for another name, 409 `merge-required` with the conflicts of its solutions.
 */
export async function goLivePlateau(
  id: string,
  name: string,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: PlateauGoLiveResult } | { ok: false; rejection: Rejection }> {
  const response = await fetchFn(`${PLATEAUS}/${encodeURIComponent(id)}/go-live`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: writeHeaders(),
    body: JSON.stringify({ name }),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as PlateauGoLiveResult }
}
