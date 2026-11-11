import type { PropertyVisibilityEntry, VisibilityChange } from '../api/visibility'
import { ME_METAMODEL_ADMIN, ME_SECURITY_ADMIN } from './metamodel'

// A fake backend for SCR-032 (us-124-property-visibility): the visibility list, the roles and the
// metatypes, and the PUT with the server-side rules of REQ-124; the person metatype of
// us-127-connection-scoped-visibility.

export { ME_METAMODEL_ADMIN, ME_SECURITY_ADMIN }

export const VISIBILITY_IDS = {
  cost: 'fd100000-0000-4000-8000-000000000001',
  classification: 'fd100000-0000-4000-8000-000000000002',
  status: 'fd100000-0000-4000-8000-000000000003',
  application: 'a1000000-0000-4000-8000-000000000001',
  assignment: 'a1000000-0000-4000-8000-000000000002',
  actor: 'a1000000-0000-4000-8000-000000000003',
}

export const ROLES = [
  { id: 'auditor', name: 'Auditor' },
  { id: 'enterprise-architect', name: 'Enterprise Architect' },
  { id: 'viewer', name: 'Viewer' },
].map((role) => ({ ...role, isBuiltin: true, privileged: false, assignableToServiceAccounts: false, overridesPropertyVisibility: false, permissions: [] }))

function entries(): PropertyVisibilityEntry[] {
  return [
    {
      propertyDefId: VISIBILITY_IDS.cost,
      propertyName: 'Investment Cost',
      label: 'Investment Cost',
      metatypes: ['Application Component'],
      visibilityMode: 'role-restricted',
      readRoles: ['auditor'],
      writeRoles: [],
      scopingConnectionType: null,
    },
    {
      propertyDefId: VISIBILITY_IDS.classification,
      propertyName: 'Security Classification',
      label: 'Security Classification',
      metatypes: [],
      visibilityMode: 'public',
      readRoles: [],
      writeRoles: [],
      scopingConnectionType: null,
    },
    {
      propertyDefId: VISIBILITY_IDS.status,
      propertyName: 'Status',
      label: 'Status',
      metatypes: ['Application Component'],
      visibilityMode: 'public',
      readRoles: [],
      writeRoles: [],
      scopingConnectionType: null,
    },
  ]
}

export interface VisibilityApi {
  fetch: typeof fetch
  puts: VisibilityChange[][]
  state: PropertyVisibilityEntry[]
  personMetatypePuts: (string | null)[]
}

export function visibilityApi(
  options: { me?: unknown; rejectWith?: string[]; personMetatype?: string | null; connectionScoped?: boolean } = {},
): VisibilityApi {
  const state = entries()
  if (options.connectionScoped) {
    state[1] = { ...state[1]!, visibilityMode: 'connection-scoped', scopingConnectionType: VISIBILITY_IDS.assignment }
  }
  const puts: VisibilityChange[][] = []
  const personMetatypePuts: (string | null)[] = []
  let personMetatype = options.personMetatype ?? null
  const personMetatypeBody = () => ({
    metatypeId: personMetatype,
    metatypeName: personMetatype === VISIBILITY_IDS.actor ? 'Business Actor' : null,
  })
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' } })
  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const method = init?.method ?? 'GET'
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_SECURITY_ADMIN)
    }
    if (url.pathname === '/api/v1/admin/roles') {
      return json(ROLES)
    }
    if (url.pathname === '/api/v1/metamodel/metatypes') {
      return json([
        { id: VISIBILITY_IDS.application, name: 'Application Component', isConnection: false },
        { id: VISIBILITY_IDS.assignment, name: 'Domain Assignment', isConnection: true },
        { id: VISIBILITY_IDS.actor, name: 'Business Actor', isConnection: false },
      ])
    }
    if (url.pathname === '/api/v1/admin/property-visibility/person-metatype' && method === 'GET') {
      return json(personMetatypeBody())
    }
    if (url.pathname === '/api/v1/admin/property-visibility/person-metatype' && method === 'PUT') {
      const { metatypeId } = JSON.parse(String(init?.body)) as { metatypeId: string | null }
      personMetatypePuts.push(metatypeId)
      if (metatypeId === VISIBILITY_IDS.assignment) {
        return json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid', status: 422, invalidFields: ['metatypeId'] }, 422)
      }
      personMetatype = metatypeId
      return json(personMetatypeBody())
    }
    if (url.pathname === '/api/v1/admin/property-visibility' && method === 'GET') {
      return json(state)
    }
    if (url.pathname === '/api/v1/admin/property-visibility' && method === 'PUT') {
      const changes = JSON.parse(String(init?.body)) as VisibilityChange[]
      puts.push(changes)
      if (options.rejectWith) {
        return json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid', status: 422, invalidFields: options.rejectWith }, 422)
      }
      const changed = changes.map((change) => {
        const index = state.findIndex((entry) => entry.propertyDefId === change.propertyDefId)
        state[index] = { ...state[index]!, ...change }
        return state[index]!
      })
      return json(changed)
    }
    throw new TypeError(`no route for ${method} ${url.pathname}`)
  }
  return { fetch: fetchFn, puts, state, personMetatypePuts }
}
