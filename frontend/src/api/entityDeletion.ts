import type { components } from './generated/schema'
import { readRejection } from './problem'
import { currentCsrfToken, type Outcome } from './session'

// Deleting an entity from the repository (REQ-146, US-074; design D2 and D6 of s17-entity-delete):
// the preview of what a deletion takes along, and the deletion with its cascade.

export type EntityDeletionPreview = components['schemas']['EntityDeletionPreview']
export type EntityDeletionResult = components['schemas']['EntityDeletionResult']

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const ENTITIES = '/api/v1/entities'

/**
 * GET /entities/{id}/deletion: the name and version of the entity, its children and connections,
 * and the active solutions with deltas on them. 404 for an unknown or deleted entity, 422 for a
 * structuring aid or an automatic composition connection.
 */
export async function fetchDeletionPreview(entityId: number, fetchFn: typeof fetch = fetch): Promise<Outcome<EntityDeletionPreview>> {
  const response = await fetchFn(`${ENTITIES}/${entityId}/deletion`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as EntityDeletionPreview }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/**
 * DELETE /entities/{id} with `If-Match`: soft-deletes the entity, its children and their connections
 * and answers the IDs of everything deleted. 412 `stale-version` when the entity changed since the
 * preview, 409 `entity-blocked` for open issues, 403 without `entity:delete` (or
 * `visibility-scope:assign` for a connection of a scoping type).
 */
export async function deleteEntityFromRepository(entityId: number, version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<EntityDeletionResult>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`${ENTITIES}/${entityId}`, {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'If-Match': `"${version}"`, ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
  })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as EntityDeletionResult }
  }
  return { ok: false, rejection: await readRejection(response) }
}
