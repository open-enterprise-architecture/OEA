import type { Solution } from '../api/solutions'

// A fake backend for SCR-060 and SCR-061 (us-038-create-solution): the solutions, creating one
// with the server-side name rule of REQ-038, and reading one.

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 12, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-solutions',
  }
}

export const ME_SOLUTION_ARCHITECT = me('Michael', ['portal:read', 'entity:read', 'solution:write', 'solution-metamodel:write'])
export const ME_SOLUTION_VIEWER = me('Anna', ['portal:read', 'entity:read'])
export const ME_PORTAL_READER = me('Clara', ['portal:read'])

function solution(id: string, name: string, status: Solution['status'], ownerName: string, createdAt: string, description: string | null): Solution {
  return {
    id,
    name,
    status,
    ownerId: 12,
    ownerName,
    createdAt,
    description,
    fromPlateauId: null,
    toPlateauId: null,
    createdBy: { principalType: 'person', id: 12, displayName: ownerName },
  }
}

export const SOLUTIONS: Solution[] = [
  solution(
    '5d000000-0000-4000-8000-000000000001',
    'CRM Replacement',
    'draft',
    'Michael',
    '2026-11-10T09:30:00Z',
    'Replace the legacy CRM by a cloud CRM.\nScope: sales and service, not marketing.',
  ),
  solution(
    '5d000000-0000-4000-8000-000000000002',
    'ERP Extension Logistics',
    'draft',
    'Michael',
    '2026-11-12T08:15:00Z',
    'Goal: warehouse logistics in the ERP.\nScope: inbound and outbound for DACH; transport planning stays external.\nRationale: three manual interfaces are retired.',
  ),
  solution('5d000000-0000-4000-8000-000000000003', 'Integration Platform', 'proposed', 'Kurt', '2026-11-03T14:00:00Z', null),
]

export interface SolutionApi {
  fetch: typeof fetch
  posts: Record<string, unknown>[]
}

export function solutionApi(options: { me?: unknown; solutions?: Solution[] } = {}): SolutionApi {
  const solutions = [...(options.solutions ?? SOLUTIONS)]
  const posts: Record<string, unknown>[] = []
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' } })
  const problem = (status: number, invalidFields: string[] = [], detail?: string) => ({
    type: status === 422 ? 'urn:oea:problem:validation-failed' : status === 404 ? 'urn:oea:problem:not-found' : 'urn:oea:problem:forbidden',
    title: 'Rejected',
    status,
    detail,
    invalidFields,
  })
  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const method = init?.method ?? 'GET'
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_SOLUTION_ARCHITECT)
    }
    if (url.pathname === '/api/v1/solutions' && method === 'GET') {
      return json([...solutions].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())))
    }
    if (url.pathname === '/api/v1/solutions' && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as { name: string; description?: string }
      posts.push(body)
      const name = body.name.trim()
      if (name.length === 0) {
        return json(problem(422, ['name'], 'The name must not be blank.'), 422)
      }
      if (solutions.some((candidate) => candidate.name.toLowerCase() === name.toLowerCase())) {
        return json(problem(422, ['name'], `Name '${name}' is already taken`), 422)
      }
      const created = solution(`5d000000-0000-4000-8000-${String(solutions.length + 1).padStart(12, '0')}`, name, 'draft', 'Michael', '2026-11-12T10:00:00Z', body.description ?? null)
      solutions.push(created)
      return json(created, 201)
    }
    const one = /^\/api\/v1\/solutions\/([^/]+)$/.exec(url.pathname)
    if (one && method === 'GET') {
      const found = solutions.find((candidate) => candidate.id === decodeURIComponent(one[1]!))
      return found ? json(found) : json(problem(404), 404)
    }
    // the header and the sidebar ask for further data; an empty answer keeps them quiet
    return json([])
  }
  return { fetch: fetchFn, posts }
}
