import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken } from './session'

// Property visibility for security administration (SCR-032; design D2, D5 of
// us-124-property-visibility): read and change the visibility of property definitions.

export type PropertyVisibilityEntry = components['schemas']['PropertyVisibilityEntry']
export type VisibilityMode = PropertyVisibilityEntry['visibilityMode']
export type AuthorizationRole = components['schemas']['AuthorizationRole']
export type PersonMetatype = components['schemas']['PersonMetatype']

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const VISIBILITY = '/api/v1/admin/property-visibility'
const PERSON_METATYPE = `${VISIBILITY}/person-metatype`

async function read<T>(path: string, fetchFn: typeof fetch): Promise<T> {
  const response = await fetchFn(path, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as T
}

/** GET /admin/property-visibility: every property definition that is not retired, by name. */
export function fetchPropertyVisibility(fetchFn: typeof fetch = fetch): Promise<PropertyVisibilityEntry[]> {
  return read(VISIBILITY, fetchFn)
}

/** GET /admin/roles: the authorization roles, offered as read and write roles. */
export function fetchRoles(fetchFn: typeof fetch = fetch): Promise<AuthorizationRole[]> {
  return read('/api/v1/admin/roles', fetchFn)
}

/** One change of the visibility of a property. */
export interface VisibilityChange {
  propertyDefId: string
  visibilityMode: VisibilityMode
  readRoles: string[]
  writeRoles: string[]
  scopingConnectionType: string | null
}

/**
 * PUT /admin/property-visibility: changes the listed properties. An invalid entry is
 * `validation-failed` (422) with `invalidFields` such as `[0].readRoles`; nothing changes then.
 */
export async function updatePropertyVisibility(
  changes: VisibilityChange[],
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: PropertyVisibilityEntry[] } | { ok: false; rejection: Rejection }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(VISIBILITY, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify(changes),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as PropertyVisibilityEntry[] }
}

/** The fields of a change that the dialog rejects before sending it (design D2, D5). */
export function invalidFields(change: VisibilityChange): string[] {
  const invalid: string[] = []
  if (change.visibilityMode === 'role-restricted') {
    if (change.readRoles.length === 0) {
      invalid.push('readRoles')
    }
    if (change.writeRoles.some((role) => !change.readRoles.includes(role))) {
      invalid.push('writeRoles')
    }
  }
  if (change.visibilityMode === 'connection-scoped' && !change.scopingConnectionType) {
    invalid.push('scopingConnectionType')
  }
  return invalid
}

/**
 * GET /admin/property-visibility/person-metatype: the entity type whose elements person accounts
 * are linked to, the anchor of connection-scoped visibility (design D2, D8 of
 * us-127-connection-scoped-visibility); `metatypeId` is `null` while none is set.
 */
export function fetchPersonMetatype(fetchFn: typeof fetch = fetch): Promise<PersonMetatype> {
  return read(PERSON_METATYPE, fetchFn)
}

/**
 * PUT /admin/property-visibility/person-metatype: sets or, with `null`, unsets the person
 * metatype. A connection metatype or an unknown one is `validation-failed` (422) on `metatypeId`.
 */
export async function updatePersonMetatype(
  metatypeId: string | null,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: PersonMetatype } | { ok: false; rejection: Rejection }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(PERSON_METATYPE, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify({ metatypeId }),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as PersonMetatype }
}
