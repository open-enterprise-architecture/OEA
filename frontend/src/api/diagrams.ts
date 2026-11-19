import type { components } from './generated/schema'
import { readRejection, RequestRejected } from './problem'
import { currentCsrfToken, type Outcome } from './session'

// Diagrams as objects (UC-10, US-074; design D2 and D8 of us-074-diagrams): the list with its
// name filter and cursor (SCR-067), one diagram, and creating, renaming, deleting and restoring it.

export type Diagram = components['schemas']['Diagram']
export type DeletedDiagram = components['schemas']['DeletedDiagram']
export type DiagramPage = components['schemas']['DiagramPage']
export type DeletedDiagramPage = components['schemas']['DeletedDiagramPage']
export type DiagramViewpoint = components['schemas']['DiagramViewpoint']

/** The input limits of the contract (design D2 of us-074-diagrams). */
export const DIAGRAM_LIMITS = { name: 200, description: 2000, filter: 200 }

/** The parameters of a list query. */
export interface DiagramQuery {
  q?: string
  page?: number
  pageSize?: number
  cursor?: string
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const DIAGRAMS = '/api/v1/diagrams'

function params(query: DiagramQuery): string {
  const search = new URLSearchParams()
  if (query.q && query.q.trim().length > 0) search.set('q', query.q)
  if (query.page) search.set('page', String(query.page))
  if (query.pageSize) search.set('pageSize', String(query.pageSize))
  if (query.cursor) search.set('cursor', query.cursor)
  const text = search.toString()
  return text ? `?${text}` : ''
}

async function list<T>(path: string, query: DiagramQuery, fetchFn: typeof fetch): Promise<Outcome<T>> {
  const response = await fetchFn(`${path}${params(query)}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as T }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** GET /diagrams: one page of the diagrams that are not deleted; a cursor of another query is 400 naming `cursor`. */
export function fetchDiagrams(query: DiagramQuery, fetchFn: typeof fetch = fetch): Promise<Outcome<DiagramPage>> {
  return list(DIAGRAMS, query, fetchFn)
}

/** GET /diagrams/deleted: one page of the deleted diagrams; needs `entity:restore`. */
export function fetchDeletedDiagrams(query: DiagramQuery, fetchFn: typeof fetch = fetch): Promise<Outcome<DeletedDiagramPage>> {
  return list(`${DIAGRAMS}/deleted`, query, fetchFn)
}

/** GET /diagrams/{id}: `undefined` for an unknown or deleted diagram (404). */
export async function fetchDiagram(id: string, fetchFn: typeof fetch = fetch): Promise<Diagram | undefined> {
  const response = await fetchFn(`${DIAGRAMS}/${encodeURIComponent(id)}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (response.status === 404) {
    return undefined
  }
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as Diagram
}

function writeHeaders(version: number | undefined, body: boolean): Record<string, string> {
  const csrfToken = currentCsrfToken()
  return {
    ...JSON_HEADERS,
    ...(body ? { 'Content-Type': 'application/json' } : {}),
    ...(version !== undefined ? { 'If-Match': `"${version}"` } : {}),
    ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
  }
}

async function diagramOutcome(response: Response, status: number): Promise<Outcome<Diagram>> {
  if (response.status === status) {
    return { ok: true, value: (await response.json()) as Diagram }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /diagrams. A taken name is 422 `diagram-name-taken`, other invalid input `validation-failed`, both with `invalidFields`. */
export async function createDiagram(
  request: { name: string; description: string | null; viewpointId: string },
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<Diagram>> {
  const response = await fetchFn(DIAGRAMS, {
    method: 'POST',
    credentials: 'same-origin',
    headers: writeHeaders(undefined, true),
    body: JSON.stringify(request),
  })
  return diagramOutcome(response, 201)
}

/** PUT /diagrams/{id} with `If-Match`: name and description; the viewpoint is fixed. */
export async function updateDiagram(
  id: string,
  request: { name: string; description: string | null },
  version: number,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<Diagram>> {
  const response = await fetchFn(`${DIAGRAMS}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: writeHeaders(version, true),
    body: JSON.stringify(request),
  })
  return diagramOutcome(response, 200)
}

/** DELETE /diagrams/{id} with `If-Match`: the diagram is marked deleted and can be restored. */
export async function deleteDiagram(id: string, version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<undefined>> {
  const response = await fetchFn(`${DIAGRAMS}/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: writeHeaders(version, false),
  })
  if (response.status === 204) {
    return { ok: true, value: undefined }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /diagrams/{id}/restore with `If-Match` (the version of the deleted diagram); needs `entity:restore`. */
export async function restoreDiagram(id: string, version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<Diagram>> {
  const response = await fetchFn(`${DIAGRAMS}/${encodeURIComponent(id)}/restore`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: writeHeaders(version, false),
  })
  return diagramOutcome(response, 200)
}
