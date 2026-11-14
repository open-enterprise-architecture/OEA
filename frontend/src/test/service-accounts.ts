import type { ServiceAccount, ServiceAccountWrite } from '../api/service-accounts'

// A fake backend for SCR-041 "Service accounts" (us-142-service-accounts): the list, the roles
// assignable to service accounts, creating with a secret shown once, changing the roles, rotating
// the secret and the final deactivation, with the 422 of a non-assignable role (SoD-4) and the 401
// of an authentication older than 5 minutes.

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 9, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-service-accounts',
  }
}

export const ME_SYSTEM_ADMIN = me('Max', ['role:assign', 'person:manage', 'service-account:manage', 'auth-config:manage', 'settings:manage'])
export const ME_ARCHITECT = me('Erika', ['portal:read', 'entity:read', 'entity:write'])

/** The fixed time of the stories and tests; expiries are relative to it. */
export const NOW = new Date('2026-11-14T10:00:00Z')

export const SERVICE_ACCOUNT_IDS = {
  scanner: '5a000000-0000-4000-8000-000000000001',
  importer: '5a000000-0000-4000-8000-000000000002',
  retired: '5a000000-0000-4000-8000-000000000003',
}

/** The secret every create or rotate of the fake returns, numbered per call. */
export const SECRET = 'oea_cs_Zk3vQ9-example-secret-of-the-fake-backend'

const ASSIGNABLE = [
  { id: 'viewer', name: 'Viewer', permissions: ['portal:read', 'entity:read'] },
  { id: 'integration-writer', name: 'Integration Writer', permissions: ['entity:read', 'entity:write'] },
  { id: 'auditor', name: 'Auditor', permissions: ['audit:read'] },
].map((role) => ({ ...role, isBuiltin: true, privileged: false, assignableToServiceAccounts: true, overridesPropertyVisibility: false }))

/** All roles, with one that a service account may never hold. */
const ALL_ROLES = [
  ...ASSIGNABLE,
  {
    id: 'system-admin',
    name: 'System Administrator',
    permissions: ['role:assign', 'service-account:manage'],
    isBuiltin: true,
    privileged: true,
    assignableToServiceAccounts: false,
    overridesPropertyVisibility: false,
  },
]

function days(count: number): string {
  return new Date(NOW.getTime() + count * 86_400_000).toISOString()
}

function accounts(): ServiceAccount[] {
  return [
    {
      id: SERVICE_ACCOUNT_IDS.scanner,
      personId: 41,
      name: 'ci-scanner',
      description: 'Scans the CI pipelines',
      roles: ['integration-writer'],
      status: 'active',
      createdAt: days(-80),
      lastUsedAt: null,
      rotatedAt: null,
      secretExpiresAt: days(10),
      secretExpiresSoon: true,
    },
    {
      id: SERVICE_ACCOUNT_IDS.importer,
      personId: 42,
      name: 'cmdb-importer',
      description: null,
      roles: ['viewer'],
      status: 'active',
      createdAt: days(-20),
      lastUsedAt: null,
      rotatedAt: days(-5),
      secretExpiresAt: days(30),
      secretExpiresSoon: false,
    },
    {
      id: SERVICE_ACCOUNT_IDS.retired,
      personId: 43,
      name: 'legacy-sync',
      description: null,
      roles: [],
      status: 'inactive',
      createdAt: days(-300),
      lastUsedAt: null,
      rotatedAt: null,
      secretExpiresAt: null,
      secretExpiresSoon: false,
    },
  ]
}

export interface ServiceAccountApi {
  fetch: typeof fetch
  accounts: ServiceAccount[]
  creates: ServiceAccountWrite[]
  updates: { id: string; body: ServiceAccountWrite }[]
  rotations: string[]
  deactivations: string[]
}

export function serviceAccountApi(
  options: {
    me?: unknown
    /** answer create, rotate and deactivate with 401 `reauthentication-required` */
    staleAuthentication?: boolean
    /** no accounts in the list */
    empty?: boolean
    /** answer every role change with 422 on `roles`, as if a role had become non-assignable */
    rejectRoleChanges?: boolean
  } = {},
): ServiceAccountApi {
  const state = options.empty ? [] : accounts()
  const creates: ServiceAccountWrite[] = []
  const updates: { id: string; body: ServiceAccountWrite }[] = []
  const rotations: string[] = []
  const deactivations: string[] = []
  let secrets = 0
  const json = (body: unknown, status = 200) =>
    new Response(status === 204 ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' },
    })
  const problem = (status: number, type: string, invalidFields: string[] = [], detail?: string) =>
    json({ type: `urn:oea:problem:${type}`, title: 'Rejected', status, invalidFields, detail }, status)
  const stale = () => problem(401, 'reauthentication-required')
  const rejectRoles = (roles: string[] | undefined) => {
    const invalid = (roles ?? []).find((role) => !ASSIGNABLE.some((candidate) => candidate.id === role))
    return invalid ? problem(422, 'validation-failed', ['roles'], `The role ${invalid} cannot be assigned to a service account (SoD-4).`) : undefined
  }
  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const method = init?.method ?? 'GET'
    const body = init?.body ? (JSON.parse(String(init.body)) as ServiceAccountWrite) : undefined
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_SYSTEM_ADMIN)
    }
    if (url.pathname === '/api/v1/admin/roles' && method === 'GET') {
      return json(url.searchParams.get('assignableToServiceAccounts') === 'true' ? ASSIGNABLE : ALL_ROLES)
    }
    if (url.pathname === '/api/v1/admin/service-accounts' && method === 'GET') {
      return json(state)
    }
    if (url.pathname === '/api/v1/admin/service-accounts' && method === 'POST') {
      creates.push(body!)
      if (options.staleAuthentication) return stale()
      const rejected = rejectRoles(body!.roles)
      if (rejected) return rejected
      if (state.some((account) => account.name.toLowerCase() === body!.name.trim().toLowerCase())) {
        return problem(422, 'validation-failed', ['name'])
      }
      const created: ServiceAccount = {
        id: `5a000000-0000-4000-8000-0000000001${String(state.length).padStart(2, '0')}`,
        personId: 50 + state.length,
        name: body!.name.trim(),
        description: body!.description ?? null,
        roles: [...(body!.roles ?? ['integration-writer'])].sort(),
        status: 'active',
        createdAt: NOW.toISOString(),
        lastUsedAt: null,
        rotatedAt: null,
        secretExpiresAt: days(body!.secretValidityDays ?? 90),
        secretExpiresSoon: (body!.secretValidityDays ?? 90) <= 14,
      }
      state.push(created)
      secrets += 1
      return json({ ...created, clientSecret: `${SECRET}-${secrets}` }, 201)
    }
    const match = /^\/api\/v1\/admin\/service-accounts\/([^/]+)(\/credentials\/rotate)?$/.exec(url.pathname)
    const account = match ? state.find((candidate) => candidate.id === match[1]) : undefined
    if (match && !account) {
      return problem(404, 'not-found')
    }
    if (match && account) {
      if (method !== 'GET' && account.status === 'inactive') {
        return problem(409, 'conflict')
      }
      if (match[2] && method === 'POST') {
        rotations.push(account.id)
        if (options.staleAuthentication) return stale()
        account.rotatedAt = NOW.toISOString()
        account.secretExpiresAt = days(90)
        account.secretExpiresSoon = false
        secrets += 1
        return json({ ...account, clientSecret: `${SECRET}-${secrets}` })
      }
      if (method === 'PUT') {
        updates.push({ id: account.id, body: body! })
        const rejected = options.rejectRoleChanges ? rejectRoles(['no-longer-assignable']) : rejectRoles(body!.roles)
        if (rejected) return rejected
        account.name = body!.name
        account.description = body!.description ?? null
        account.roles = [...(body!.roles ?? [])].sort()
        return json(account)
      }
      if (method === 'DELETE') {
        deactivations.push(account.id)
        if (options.staleAuthentication) return stale()
        account.status = 'inactive'
        account.roles = []
        account.secretExpiresAt = null
        account.secretExpiresSoon = false
        return json(undefined, 204)
      }
      return json(account)
    }
    throw new TypeError(`no route for ${method} ${url.pathname}`)
  }
  return { fetch: fetchFn, accounts: state, creates, updates, rotations, deactivations }
}
