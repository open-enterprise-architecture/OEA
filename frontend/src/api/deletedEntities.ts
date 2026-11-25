import type { components } from './generated/schema'
import { readRejection } from './problem'
import { currentCsrfToken, type Outcome } from './session'

// The deleted entities and their restore (REQ-159, ADR-019; design D1 and D3 of s17-deleted-entities):
// the list with its filters and cursor (SCR-068) and the restore of an entity with its cascade.

export type DeletedEntity = components['schemas']['DeletedEntity']
export type DeletedEntityPage = components['schemas']['DeletedEntityPage']
export type EntityUndeleteResult = components['schemas']['EntityUndeleteResult']

/** The input limit of the name filter (contract). */
export const DELETED_ENTITY_LIMITS = { filter: 255 }

/** The parameters of a list query. */
export interface DeletedEntityQuery {
  q?: string
  metatypeId?: string
  page?: number
  pageSize?: number
  cursor?: string
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const ENTITIES = '/api/v1/entities'

/**
 * GET /entities/deleted: one page of the deleted entities, newest deletion first; needs
 * `entity:restore`. A cursor of another query is 400 naming `cursor`.
 */
export async function fetchDeletedEntities(query: DeletedEntityQuery, fetchFn: typeof fetch = fetch): Promise<Outcome<DeletedEntityPage>> {
  const search = new URLSearchParams()
  if (query.q && query.q.trim().length > 0) search.set('q', query.q)
  if (query.metatypeId) search.set('metatypeId', query.metatypeId)
  if (query.page) search.set('page', String(query.page))
  if (query.pageSize) search.set('pageSize', String(query.pageSize))
  if (query.cursor) search.set('cursor', query.cursor)
  const text = search.toString()
  const response = await fetchFn(`${ENTITIES}/deleted${text ? `?${text}` : ''}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as DeletedEntityPage }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/**
 * POST /entities/{id}/undelete with `If-Match` (the version of the deleted entity): restores the
 * entity with what its deletion took along. 412 when it changed, 409 when it is not deleted or is a
 * connection with a deleted endpoint (`connection-not-restorable`), 403 without `entity:restore`.
 */
export async function restoreDeletedEntity(entityId: number, version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<EntityUndeleteResult>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`${ENTITIES}/${entityId}/undelete`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'If-Match': `"${version}"`, ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
  })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as EntityUndeleteResult }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** What a finished restore tells the user: the names of the restored entities and of those that stayed deleted. */
export interface RestoreSummary {
  restored: string[]
  /** `reason` is `endpoint-deleted` or `parent-changed` */
  stayed: { name: string; reason: string }[]
  movedToTopLevel: string[]
}

/** The summary of a result, with the names the page knows; an unknown entity is shown as `#id`. */
export function summarize(result: EntityUndeleteResult, names: ReadonlyMap<number, string>): RestoreSummary {
  const name = (id: number) => names.get(id) ?? `#${id}`
  return {
    restored: result.restored.map(name),
    stayed: result.notRestored.map((entry) => ({ name: name(entry.entityId), reason: entry.reason })),
    movedToTopLevel: result.movedToTopLevel.map(name),
  }
}

/** What the page or the catalog shows after a restore (design D3 of s17-deleted-entities). */
export type RestoreNoticeData =
  | { kind: 'done'; name: string; summary: RestoreSummary }
  | { kind: 'stale'; name: string }
  | { kind: 'endpoint-deleted'; name: string }

/** The deleted entity a restore is asked for: its version is the `If-Match` of the restore. */
export interface RestoreTarget {
  id: number
  name: string
  version: number
  /** the name of the entity whose deletion took this one along, if any */
  deletedWith?: string
}
