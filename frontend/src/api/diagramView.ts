import type { components } from './generated/schema'
import { readRejection, RequestRejected } from './problem'
import { currentCsrfToken, type Outcome } from './session'

// The canvas of a diagram (UC-10, US-074; design D2 of us-074-diagram-canvas): its view with the
// shapes in drawing order, the entity search of "Add existing" and the atomic save of the layout.

export type DiagramView = components['schemas']['DiagramView']
export type DiagramShape = components['schemas']['DiagramShape']
export type DiagramViewWrite = components['schemas']['DiagramViewWrite']
export type DiagramShapeWrite = components['schemas']['DiagramShapeWrite']
export type DiagramNewEntity = components['schemas']['DiagramNewEntity']
export type DiagramEntity = components['schemas']['DiagramEntity']
export type DiagramEntityPage = components['schemas']['DiagramEntityPage']
export type DiagramEntityDetails = components['schemas']['DiagramEntityDetails']
export type DiagramEntityField = components['schemas']['DiagramEntityField']
export type DiagramEntityUpdate = components['schemas']['DiagramEntityUpdate']
export type DiagramEdge = components['schemas']['DiagramEdge']
export type DiagramEdgeWrite = components['schemas']['DiagramEdgeWrite']
export type DiagramNewConnection = components['schemas']['DiagramNewConnection']
export type DiagramConnectionOffer = components['schemas']['DiagramConnectionOffer']
export type DiagramConnectionOfferList = components['schemas']['DiagramConnectionOfferList']
export type AllowedConnectionResult = components['schemas']['AllowedConnectionResult']
export type DiagramHierarchyLine = components['schemas']['DiagramHierarchyLine']
export type DiagramHierarchyLineWrite = components['schemas']['DiagramHierarchyLineWrite']
export type DiagramParentChange = components['schemas']['DiagramParentChange']
export type DiagramEntityChild = components['schemas']['DiagramEntityChild']
export type DiagramEntityChildList = components['schemas']['DiagramEntityChildList']

/** The limits of the contract (design D1 and D2 of us-074-diagram-canvas, us-074-diagram-nesting), in pt. */
export const CANVAS_LIMITS = {
  position: 100_000,
  sizeMin: 1,
  /** a container may hold many children (design D1 of us-074-diagram-nesting); the default sizes of a viewpoint stay at 2000 */
  sizeMax: 10_000,
  shapes: 2000,
  newEntities: 500,
  /** the changed entities of one save (design D2 of us-074-diagram-entity-details) */
  entityUpdates: 500,
  /** the edges of one diagram and the new connections of one save (design D2 of us-074-diagram-connections) */
  edges: 4000,
  /** the bend points of one edge */
  bendPoints: 50,
  newConnections: 500,
  /** the hierarchy lines of one diagram and the parent changes of one save (design D2 of us-074-diagram-nesting) */
  hierarchyLines: 4000,
  parentChanges: 500,
  /** the levels of nested shapes (a shape on the canvas is on level 1) and the children `listDiagramEntityChildren` returns */
  nesting: 10,
  children: 500,
  entityName: 255,
  entityDescription: 2000,
  search: 255,
  searchPageSize: 20,
}

/** A saved layout: the view and the version of the diagram it has now. */
export interface SavedView {
  view: DiagramView
  version: number
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const VIEWS = '/api/v1/views/diagrams'

/** GET /views/diagrams/{id}: `undefined` for an unknown or deleted diagram (404). */
export async function fetchDiagramView(id: string, fetchFn: typeof fetch = fetch): Promise<DiagramView | undefined> {
  const response = await fetchFn(`${VIEWS}/${encodeURIComponent(id)}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.status === 404) {
    return undefined
  }
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DiagramView
}

/**
 * PUT /views/diagrams/{id} with `If-Match`: all shapes in drawing order and the new entities. A stale
 * version is 412, a faulty shape or new entity 422 with `invalidFields` and `errors`.
 */
export async function saveDiagramView(
  id: string,
  request: DiagramViewWrite,
  version: number,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<SavedView>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`${VIEWS}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', 'If-Match': `"${version}"`, ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify(request),
  })
  if (response.ok) {
    const view = (await response.json()) as DiagramView
    return { ok: true, value: { view, version: view.diagram.version } }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** The parameters of the entity search. */
export interface EntitySearchQuery {
  q?: string
  metatypeId?: string
  page?: number
  pageSize?: number
}

/** GET /views/diagrams/{id}/entities: the active element entities the viewpoint allows, by name. */
export async function searchDiagramEntities(id: string, query: EntitySearchQuery, fetchFn: typeof fetch = fetch): Promise<Outcome<DiagramEntityPage>> {
  const search = new URLSearchParams()
  if (query.q && query.q.trim().length > 0) search.set('q', query.q.trim())
  if (query.metatypeId) search.set('metatypeId', query.metatypeId)
  if (query.page) search.set('page', String(query.page))
  search.set('pageSize', String(query.pageSize ?? CANVAS_LIMITS.searchPageSize))
  const response = await fetchFn(`${VIEWS}/${encodeURIComponent(id)}/entities?${search.toString()}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as DiagramEntityPage }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** GET /views/diagrams/{id}/entities/{entityId}: the details of a shown entity; `undefined` for a 404. */
export async function fetchDiagramEntityDetails(
  id: string,
  entityId: number,
  fetchFn: typeof fetch = fetch,
): Promise<DiagramEntityDetails | undefined> {
  const response = await fetchFn(`${VIEWS}/${encodeURIComponent(id)}/entities/${entityId}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.status === 404) {
    return undefined
  }
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DiagramEntityDetails
}

/** GET /views/diagrams/{id}/reference-targets: the entities a reference property may point to, by name. */
export async function searchDiagramReferenceTargets(
  id: string,
  propertyDefId: string,
  term: string,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<DiagramEntityPage>> {
  const search = new URLSearchParams({ propertyDefId, pageSize: String(CANVAS_LIMITS.searchPageSize) })
  if (term.trim().length > 0) search.set('q', term.trim())
  const response = await fetchFn(`${VIEWS}/${encodeURIComponent(id)}/reference-targets?${search.toString()}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as DiagramEntityPage }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/**
 * POST /views/diagrams/{id}/connections/query: the connections between the placed entities and the
 * other shown ones that the viewpoint allows (design D2 of us-074-diagram-connections), for the offer
 * "N connections available".
 */
export async function findDiagramConnections(
  id: string,
  entityIds: number[],
  shownEntityIds: number[],
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<DiagramConnectionOfferList>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`${VIEWS}/${encodeURIComponent(id)}/connections/query`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify({ entityIds, shownEntityIds }),
  })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as DiagramConnectionOfferList }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/**
 * GET /metamodel/allowed-connections: the connection metatypes between two metatypes, with the
 * direction they run (the source metatype is the source, the target, or both).
 */
export async function fetchAllowedConnections(
  sourceMetatypeId: string,
  targetMetatypeId: string,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<AllowedConnectionResult>> {
  const query = new URLSearchParams({ sourceMetatypeId, targetMetatypeId })
  const response = await fetchFn(`/api/v1/metamodel/allowed-connections?${query.toString()}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as AllowedConnectionResult }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/**
 * GET /views/diagrams/{id}/entities/{entityId}/children: the direct children of an entity that are not
 * deleted, with whether the viewpoint allows them and the composition connection of the pair (design D2 of
 * us-074-diagram-nesting); at most 500, `truncated` says that there are more.
 */
export async function fetchDiagramEntityChildren(
  id: string,
  entityId: number,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<DiagramEntityChildList>> {
  const response = await fetchFn(`${VIEWS}/${encodeURIComponent(id)}/entities/${entityId}/children`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as DiagramEntityChildList }
  }
  return { ok: false, rejection: await readRejection(response) }
}
