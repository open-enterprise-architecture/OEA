import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken } from './session'
import type { AuthorizationRole } from './roles'

// Service accounts (SCR-041, US-142, US-149; design D8 of us-142-service-accounts): the list, the
// roles assignable to service accounts, creating with a secret shown once, changing the roles,
// rotating the secret and the final deactivation. Create, rotate and deactivate need an
// authentication within the last 5 minutes (401 `reauthentication-required`).

export type ServiceAccount = components['schemas']['ServiceAccount']
export type ServiceAccountCreated = components['schemas']['ServiceAccountCreated']
export type ServiceAccountWrite = components['schemas']['ServiceAccountWrite']

export type Outcome<T> = { ok: true; value: T } | { ok: false; rejection: Rejection }

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const BASE = '/api/v1/admin/service-accounts'

async function read<T>(path: string, fetchFn: typeof fetch): Promise<T> {
  const response = await fetchFn(path, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as T
}

async function write<T>(method: string, path: string, body: unknown, fetchFn: typeof fetch): Promise<Outcome<T>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(path, {
    method,
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (response.status === 204 ? undefined : await response.json()) as T }
}

/** GET /admin/service-accounts: all service accounts, without secrets. */
export function fetchServiceAccounts(fetchFn: typeof fetch = fetch): Promise<ServiceAccount[]> {
  return read(BASE, fetchFn)
}

/** GET /admin/roles?assignableToServiceAccounts=true: the roles a service account may hold (SoD-4). */
export function fetchAssignableRoles(fetchFn: typeof fetch = fetch): Promise<AuthorizationRole[]> {
  return read('/api/v1/admin/roles?assignableToServiceAccounts=true', fetchFn)
}

/** POST /admin/service-accounts: the account with its secret, shown once. */
export function createServiceAccount(
  account: ServiceAccountWrite,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<ServiceAccountCreated>> {
  return write('POST', BASE, account, fetchFn)
}

/** PUT /admin/service-accounts/{id}: name, description and roles of an active account. */
export function updateServiceAccount(
  id: string,
  account: ServiceAccountWrite,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<ServiceAccount>> {
  return write('PUT', `${BASE}/${id}`, account, fetchFn)
}

/** POST /admin/service-accounts/{id}/credentials/rotate: a new secret, shown once; the old one is invalid. */
export function rotateServiceAccountSecret(id: string, fetchFn: typeof fetch = fetch): Promise<Outcome<ServiceAccountCreated>> {
  return write('POST', `${BASE}/${id}/credentials/rotate`, {}, fetchFn)
}

/** DELETE /admin/service-accounts/{id}: the final deactivation. */
export function deactivateServiceAccount(id: string, fetchFn: typeof fetch = fetch): Promise<Outcome<void>> {
  return write('DELETE', `${BASE}/${id}`, undefined, fetchFn)
}
