import type { Plateau } from '../api/plateaus'

// A fake backend for SCR-065 and the plateau field of SCR-060 (us-084-plateaus): the plateaus in
// the order of the overview, creating one with the rules of REQ-084 (one baseline, a predecessor
// and a basis for target and transition plateaus, a unique name), and editing one with the
// read-only protection of a realized plateau (US-089) and the lock of the predecessor while
// solutions target it.

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 14, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-plateaus',
  }
}

export const ME_ENTERPRISE_ARCHITECT = me('Kurt', [
  'portal:read',
  'entity:read',
  'entity:write',
  'plateau:write',
  'plateau:go-live',
  'solution:write',
])
export const ME_PLATEAU_READER = me('Michael', ['portal:read', 'entity:read', 'solution:write'])

export const BASELINE_ID = 'b1000000-0000-4000-8000-000000000001'
export const TARGET_2027_ID = 'b1000000-0000-4000-8000-000000000002'
export const PHASE_1_ID = 'b1000000-0000-4000-8000-000000000003'
export const REALIZED_ID = 'b1000000-0000-4000-8000-000000000004'
export const TARGET_2028_ID = 'b1000000-0000-4000-8000-000000000005'

export function plateau(overrides: Partial<Plateau> & Pick<Plateau, 'id' | 'name' | 'status'>): Plateau {
  return {
    description: null,
    succeeds: null,
    basis: null,
    validFrom: null,
    validTo: null,
    solutionCount: 0,
    createdAt: '2026-11-01T09:00:00Z',
    createdBy: { id: 14, name: 'Kurt' },
    updatedAt: null,
    ...overrides,
  }
}

export const PLATEAUS: Plateau[] = [
  plateau({ id: BASELINE_ID, name: 'Baseline 2026-Q2', status: 'baseline', validFrom: '2026-04-01', description: 'The landscape in production.' }),
  plateau({
    id: PHASE_1_ID,
    name: 'Phase 1 – Salesforce introduction',
    status: 'transition',
    succeeds: BASELINE_ID,
    basis: 'catalog',
    validFrom: '2027-01-01',
    solutionCount: 1,
  }),
  plateau({
    id: TARGET_2027_ID,
    name: 'Target Cloud Migration 2027',
    status: 'target',
    succeeds: BASELINE_ID,
    basis: 'catalog',
    validFrom: '2027-07-01',
    description: 'All business applications run in the cloud.',
    solutionCount: 2,
  }),
  plateau({ id: TARGET_2028_ID, name: 'Target 2028', status: 'target', succeeds: TARGET_2027_ID, basis: 'predecessor', validFrom: null }),
  plateau({ id: REALIZED_ID, name: 'Baseline 2025', status: 'realized', validFrom: '2025-01-01', validTo: '2026-04-01' }),
]

export interface PlateauApi {
  fetch: typeof fetch
  posts: Record<string, unknown>[]
  puts: { id: string; body: Record<string, unknown> }[]
}

const ORDER = { baseline: 0, transition: 1, target: 1, realized: 2 }

export function plateauApi(options: { me?: unknown; plateaus?: Plateau[]; status?: number } = {}): PlateauApi {
  const plateaus = (options.plateaus ?? PLATEAUS).map((candidate) => ({ ...candidate }))
  const posts: Record<string, unknown>[] = []
  const puts: { id: string; body: Record<string, unknown> }[] = []
  let next = 10
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' } })
  const invalid = (field: string | null, detail: string) =>
    json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid plateau', status: 422, detail, invalidFields: field ? [field] : [] }, 422)
  const sorted = () =>
    [...plateaus].sort(
      (a, b) =>
        ORDER[a.status] - ORDER[b.status] ||
        (a.status === 'realized' ? (b.validTo ?? '').localeCompare(a.validTo ?? '') : 0) ||
        (a.validFrom ?? '9999').localeCompare(b.validFrom ?? '9999') ||
        a.name.toLowerCase().localeCompare(b.name.toLowerCase()),
    )
  const taken = (name: string, except?: string) =>
    plateaus.some((candidate) => candidate.id !== except && candidate.name.toLowerCase() === name.trim().toLowerCase())
  const predecessor = (body: Record<string, unknown>) => {
    const found = plateaus.find((candidate) => candidate.id === body.succeeds)
    return found && found.status !== 'realized' ? found : undefined
  }
  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const method = init?.method ?? 'GET'
    if (url.pathname === '/api/v1/me') return json(options.me ?? ME_ENTERPRISE_ARCHITECT)
    if (options.status) return json({ type: 'urn:oea:problem:forbidden', status: options.status }, options.status)
    if (url.pathname === '/api/v1/plateaus' && method === 'GET') return json(sorted())
    if (url.pathname === '/api/v1/plateaus' && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as Record<string, unknown>
      posts.push(body)
      const name = String(body.name ?? '')
      if (name.trim() === '') return invalid('name', 'The name must not be blank.')
      if (taken(name)) return invalid('name', `Name '${name.trim()}' is already taken`)
      if (body.status === 'baseline') {
        if (plateaus.some((candidate) => candidate.status === 'baseline')) return invalid('status', 'A baseline plateau already exists')
      } else {
        if (!plateaus.some((candidate) => candidate.status === 'baseline')) {
          return invalid('status', 'No baseline plateau exists. Create an initial baseline first.')
        }
        if (!predecessor(body)) return invalid('succeeds', 'A target or transition plateau needs a predecessor.')
        if (!body.basis) return invalid('basis', 'A target or transition plateau needs the basis catalog or predecessor.')
      }
      const created = plateau({
        id: `b1000000-0000-4000-8000-0000000000${next++}`,
        name: name.trim(),
        status: body.status as Plateau['status'],
        description: (body.description as string | undefined) ?? null,
        succeeds: (body.succeeds as string | undefined) ?? null,
        basis: (body.basis as Plateau['basis']) ?? null,
        validFrom: (body.validFrom as string | undefined) ?? null,
      })
      plateaus.push(created)
      return json(created, 201)
    }
    const match = /^\/api\/v1\/plateaus\/([^/]+)$/.exec(url.pathname)
    if (match && method === 'PUT') {
      const id = decodeURIComponent(match[1]!)
      const body = JSON.parse(String(init?.body)) as Record<string, unknown>
      puts.push({ id, body })
      const found = plateaus.find((candidate) => candidate.id === id)
      if (!found) return json({ type: 'urn:oea:problem:not-found', status: 404 }, 404)
      if (found.status === 'realized') return invalid(null, 'Realized plateau is read-only')
      const name = String(body.name ?? '')
      if (name.trim() === '') return invalid('name', 'The name must not be blank.')
      if (taken(name, id)) return invalid('name', `Name '${name.trim()}' is already taken`)
      if (found.status !== 'baseline') {
        if (!predecessor(body) || body.succeeds === id) return invalid('succeeds', 'The predecessor would lead back to this plateau.')
        if (body.succeeds !== found.succeeds && found.solutionCount > 0) {
          return json({ type: 'urn:oea:problem:plateau-in-use', title: 'Plateau in use', status: 409 }, 409)
        }
      }
      Object.assign(found, {
        name: name.trim(),
        description: (body.description as string | undefined) ?? null,
        succeeds: (body.succeeds as string | undefined) ?? null,
        basis: (body.basis as Plateau['basis']) ?? null,
        validFrom: (body.validFrom as string | undefined) ?? null,
        updatedAt: '2026-11-17T10:00:00Z',
      })
      return json(found)
    }
    if (match && method === 'GET') {
      const found = plateaus.find((candidate) => candidate.id === decodeURIComponent(match[1]!))
      return found ? json(found) : json({ type: 'urn:oea:problem:not-found', status: 404 }, 404)
    }
    if (url.pathname === '/api/v1/me') {
      return json(options.me ?? ME_ENTERPRISE_ARCHITECT)
    }
    throw new TypeError(`no fake route for ${method} ${url.pathname}`)
  }
  return { fetch: fetchFn, posts, puts }
}

/** A fetch that answers the plateaus from `plateaus` and everything else from `other`. */
export function withPlateaus(other: typeof fetch, plateaus: typeof fetch): typeof fetch {
  return async (input, init) => {
    const path = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
    return path.startsWith('/api/v1/plateaus') ? plateaus(input, init) : other(input, init)
  }
}
