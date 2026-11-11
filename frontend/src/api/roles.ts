import type { components } from './generated/schema'
import { readRejection, RequestRejected, type Rejection } from './problem'
import { currentCsrfToken } from './session'

// Authorization roles (SCR-039, US-151; design D4, D5 of us-128-override-role-list): the built-in
// and custom roles with their permissions, the permission catalog, and creating a custom role.

export type AuthorizationRole = components['schemas']['AuthorizationRole']
export type Permission = components['schemas']['Permission']
export type AuthorizationRoleCreate = components['schemas']['AuthorizationRoleCreate']

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const ROLES = '/api/v1/admin/roles'

async function read<T>(path: string, fetchFn: typeof fetch): Promise<T> {
  const response = await fetchFn(path, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as T
}

/** GET /admin/roles: the built-in roles first, then the custom ones. */
export function fetchAuthorizationRoles(fetchFn: typeof fetch = fetch): Promise<AuthorizationRole[]> {
  return read(ROLES, fetchFn)
}

/** GET /admin/permissions: the permission catalog, with the admin-class marker. */
export function fetchPermissions(fetchFn: typeof fetch = fetch): Promise<Permission[]> {
  return read('/api/v1/admin/permissions', fetchFn)
}

/**
 * POST /admin/roles: creates a custom role. A taken ID is 409; an unknown permission or SoD-2 is
 * `validation-failed` (422) on `permissions`, SoD-4 on `assignableToServiceAccounts`.
 */
export async function createAuthorizationRole(
  role: AuthorizationRoleCreate,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; value: AuthorizationRole } | { ok: false; rejection: Rejection }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(ROLES, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, 'Content-Type': 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify(role),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as AuthorizationRole }
}
