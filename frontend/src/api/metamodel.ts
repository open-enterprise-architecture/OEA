import type { components } from './generated/schema'
import { readRejection, type Rejection } from './problem'
import { currentCsrfToken } from './session'

export type StarterPackage = components['schemas']['StarterPackage']
export type ImportResult = components['schemas']['ImportResult']
export type MetatypeDetail = components['schemas']['MetatypeDetail']

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** GET /metamodel/metatypes: the metatypes of the instance (US-033, design D8). */
export async function fetchMetatypes(fetchFn: typeof fetch = fetch): Promise<MetatypeDetail[]> {
  const response = await fetchFn('/api/v1/metamodel/metatypes', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`metatypes not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as MetatypeDetail[]
}

/** GET /admin/metamodel/starter-packages: the shipped packages with summary (ADR-005, REQ-112). */
export async function fetchStarterPackages(fetchFn: typeof fetch = fetch): Promise<StarterPackage[]> {
  const response = await fetchFn('/api/v1/admin/metamodel/starter-packages', {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new Error(`starter packages not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as StarterPackage[]
}

/** POST /admin/metamodel/starter-packages/{packageId}/import with the CSRF token of the session. */
export async function importStarterPackage(
  packageId: string,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; result: ImportResult } | { ok: false; rejection: Rejection; result?: ImportResult }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`/api/v1/admin/metamodel/starter-packages/${encodeURIComponent(packageId)}/import`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
  })
  if (response.ok) {
    return { ok: true, result: (await response.json()) as ImportResult }
  }
  if (response.status === 409) {
    const result = (await response.json()) as ImportResult
    return { ok: false, rejection: { status: 409, problemType: 'conflict', invalidFields: [] }, result }
  }
  return { ok: false, rejection: await readRejection(response) }
}
