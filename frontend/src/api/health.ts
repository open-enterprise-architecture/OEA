import type { components } from './generated/schema'

/** Overall status of the backend, generated from api/openapi.yaml (ADR-013, spec-first). */
export type HealthStatus = components['schemas']['HealthStatus']['status']

/** Reads GET /api/v1/health; any failure counts as DOWN. */
export async function fetchHealth(fetchFn: typeof fetch = fetch): Promise<HealthStatus> {
  try {
    const response = await fetchFn('/api/v1/health', { headers: { Accept: 'application/json' } })
    const body = (await response.json()) as components['schemas']['HealthStatus']
    return body.status
  } catch {
    return 'DOWN'
  }
}
