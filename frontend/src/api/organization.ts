import type { components } from './generated/schema'
import { readRejection, type Rejection } from './problem'
import { currentCsrfToken } from './session'

// The organization model of the instance (change us-075-lane-assignments; SCR-030) and the account
// mapping of the person link (SCR-032): which metatypes play organizational unit, role, person and
// the connections belongs-to, performs-role and responsible.

export type OrganizationModel = components['schemas']['OrganizationModel']
export type OrganizationModelWrite = components['schemas']['OrganizationModelWrite']
export type PersonLinkMapping = components['schemas']['PersonLinkMapping']

/** The parts of the organization model, as the API names them. */
export const ORGANIZATION_PARTS = ['orgUnit', 'role', 'person', 'belongsTo', 'performsRole', 'responsible'] as const
export type OrganizationPart = (typeof ORGANIZATION_PARTS)[number]

/** The account attributes the mapping compares: the e-mail address or a subject of an identity binding. */
export const ACCOUNT_ATTRIBUTES = ['email', 'idp-subject'] as const

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const MODEL = '/api/v1/metamodel/organization-model'
const MAPPING = '/api/v1/admin/property-visibility/person-link-mapping'

export type WriteOutcome<T> = { ok: true; value: T } | { ok: false; rejection: Rejection }

/** The organization model with its version (the `ETag`), read by every entity reader. */
export async function fetchOrganizationModel(fetchFn: typeof fetch = fetch): Promise<OrganizationModel> {
  const response = await fetchFn(MODEL, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`${MODEL} not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as OrganizationModel
}

/** PUT /metamodel/organization-model with `If-Match`: a part left out is unset; 412 for a stale version, 422 naming the part. */
export async function updateOrganizationModel(
  body: OrganizationModelWrite,
  version: number,
  fetchFn: typeof fetch = fetch,
): Promise<WriteOutcome<OrganizationModel>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(MODEL, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      'Content-Type': 'application/json',
      'If-Match': `"${version}"`,
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as OrganizationModel }
}

/** GET /admin/property-visibility/person-link-mapping. */
export async function fetchPersonLinkMapping(fetchFn: typeof fetch = fetch): Promise<PersonLinkMapping> {
  const response = await fetchFn(MAPPING, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`${MAPPING} not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as PersonLinkMapping
}

/** PUT /admin/property-visibility/person-link-mapping: sets the mapping or, with a null property, clears it (422 naming `propertyDefId`). */
export async function updatePersonLinkMapping(
  propertyDefId: string | null,
  accountAttribute: PersonLinkMapping['accountAttribute'],
  fetchFn: typeof fetch = fetch,
): Promise<WriteOutcome<PersonLinkMapping>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(MAPPING, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify({ propertyDefId, accountAttribute }),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as PersonLinkMapping }
}
