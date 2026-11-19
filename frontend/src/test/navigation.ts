/** Test and story data for the navigation tree (US-054, SCR-055, SCR-020). */
import type { Browser, BrowserItem, TreeNode, TreeNodeItem } from '../api/navigation'
import { CATALOGS } from './catalogs'

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 7, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-navigation',
  }
}

export const ME_TREE_MANAGER = me('Kurt', ['portal:read', 'entity:read', 'entity:write', 'catalog:manage', 'viewpoint:manage'])
export const ME_TREE_READER = me('Franz', ['portal:read', 'entity:read'])
/** Holds `entity:write` but not `entity:delete` (US-135, US-136). */
export const ME_HIERARCHY_WRITER = me('Michael', ['portal:read', 'entity:read', 'entity:write'])
/** Holds `entity:write` and `entity:delete`. */
export const ME_HIERARCHY_MANAGER = me('Kurt', ['portal:read', 'entity:read', 'entity:write', 'entity:delete'])

export const ROOT_ID = 'f0000000-0000-4000-8000-000000000000'
export const LANDSCAPE_ID = 'f0000000-0000-4000-8000-000000000001'
export const APPLICATIONS_ID = 'f0000000-0000-4000-8000-000000000002'
export const COMPLIANCE_ID = 'f0000000-0000-4000-8000-000000000003'

/**
 * Element entities the fake knows; 9103 is deleted and 9104 is a connection (US-095). Any other ID
 * below 9000 is taken for a plain element, such as the rows of the catalog fakes.
 */
export const ENTITIES = [
  { id: 9101, name: 'SAP ERP', deleted: false, connection: false },
  { id: 9102, name: 'Workday', deleted: false, connection: false },
  { id: 9103, name: 'Legacy CRM', deleted: true, connection: false },
  { id: 9104, name: 'SAP ERP to Workday', deleted: false, connection: true },
]

/** Solutions the fake knows. */
export const SOLUTIONS = [
  { id: 'd0000000-0000-4000-8000-000000000001', name: 'Cloud Migration' },
  { id: 'd0000000-0000-4000-8000-000000000002', name: 'ERP Consolidation' },
]

const NO_TARGET = { referenceId: null, entityId: null, displayLabel: null, sortOrder: 0, scope: null, deleted: false }

function item(id: string, catalog: (typeof CATALOGS)[number], displayLabel: string | null = null): TreeNodeItem {
  return { ...NO_TARGET, id, itemType: 'catalog', referenceId: catalog.id, displayLabel, name: catalog.name, scope: catalog.scope }
}

/** An entity entry; a deleted entity keeps its entry, marked (US-095 AC2). */
export function entityItem(id: string, entity: (typeof ENTITIES)[number], displayLabel: string | null = null): TreeNodeItem {
  return { ...NO_TARGET, id, itemType: 'entity', entityId: entity.id, displayLabel, name: entity.name, deleted: entity.deleted }
}

export function solutionItem(id: string, solution: (typeof SOLUTIONS)[number], displayLabel: string | null = null): TreeNodeItem {
  return { ...NO_TARGET, id, itemType: 'solution', referenceId: solution.id, displayLabel, name: solution.name }
}

/** A diagram entry (us-074-diagrams). */
export function diagramItem(id: string, diagramId: string, name: string, displayLabel: string | null = null): TreeNodeItem {
  return { ...NO_TARGET, id, itemType: 'diagram', referenceId: diagramId, displayLabel, name }
}

export function folder(id: string, name: string, parentId: string | null, items: TreeNodeItem[] = [], children: TreeNode[] = []): TreeNode {
  return { id, name, parentId, sortOrder: 0, items, children }
}

/** "Repository" with "IT Landscape" (the application inventory, a subfolder) and "Compliance". */
export const TREE: TreeNode = folder(ROOT_ID, 'Repository', null, [], [
  folder(COMPLIANCE_ID, 'Compliance', ROOT_ID, [item('e0000000-0000-4000-8000-000000000003', CATALOGS[1]!)]),
  folder(LANDSCAPE_ID, 'IT Landscape', ROOT_ID, [
    item('e0000000-0000-4000-8000-000000000001', CATALOGS[0]!),
    item('e0000000-0000-4000-8000-000000000002', CATALOGS[2]!, 'My Candidates'),
  ], [folder(APPLICATIONS_ID, 'Applications', LANDSCAPE_ID)]),
])

/**
 * The tree of SCR-055 with entities and solutions (US-095): "IT Landscape" holds a catalog, an
 * entity, a solution and the entry of a deleted entity; "Compliance" holds a catalog.
 */
export const TREE_WITH_ENTITIES: TreeNode = folder(ROOT_ID, 'Repository', null, [], [
  folder(COMPLIANCE_ID, 'Compliance', ROOT_ID, [item('e0000000-0000-4000-8000-000000000003', CATALOGS[1]!)]),
  folder(LANDSCAPE_ID, 'IT Landscape', ROOT_ID, [
    item('e0000000-0000-4000-8000-000000000001', CATALOGS[0]!),
    entityItem('e0000000-0000-4000-8000-000000000011', ENTITIES[0]!),
    solutionItem('e0000000-0000-4000-8000-000000000012', SOLUTIONS[0]!),
    entityItem('e0000000-0000-4000-8000-000000000013', ENTITIES[2]!),
    item('e0000000-0000-4000-8000-000000000002', CATALOGS[2]!, 'My Candidates'),
  ], [folder(APPLICATIONS_ID, 'Applications', LANDSCAPE_ID)]),
])

export const EMPTY_TREE: TreeNode = folder(ROOT_ID, 'Repository', null)

export const PLATEAU_2027_ID = 'b0000000-0000-4000-8000-000000000001'
export const PLATEAU_2030_ID = 'b0000000-0000-4000-8000-000000000002'
export const BROWSER_SOLUTION_ID = SOLUTIONS[0]!.id

/**
 * The top level of the solution browser (US-132): the transition plateau "2027" with the solution "Cloud Migration"
 * (three deltas), the empty target plateau "TO-BE 2030", and the project solution "ERP Consolidation" without deltas.
 */
export const BROWSER: Browser = {
  currentState: { components: 3, connections: 1, catalogs: 2, diagrams: 2 },
  plateaus: [
    { id: PLATEAU_2027_ID, name: '2027', status: 'transition', solutions: [{ id: SOLUTIONS[0]!.id, name: 'Cloud Migration', status: 'draft', components: 3 }] },
    { id: PLATEAU_2030_ID, name: 'TO-BE 2030', status: 'target', solutions: [] },
  ],
  projects: [{ id: SOLUTIONS[1]!.id, name: 'ERP Consolidation', status: 'proposed', components: 0 }],
}

/** The top level without plateau mode: no plateaus, an empty "Projects" and nothing in the repository yet. */
export const BROWSER_WITHOUT_PLATEAUS: Browser = {
  currentState: { components: 3, connections: 0, catalogs: 2, diagrams: 0 },
  plateaus: [],
  projects: [],
}

function entityRow(entity: (typeof ENTITIES)[number], abbreviation: string | null = 'APP'): BrowserItem {
  return {
    kind: 'entity',
    id: String(entity.id),
    name: entity.name,
    metatypeAbbreviation: abbreviation,
    entityId: null,
    solutionId: null,
    deltaType: null,
    notation: null,
    childCount: 0,
    structuringAid: false,
    version: 1,
  }
}

function deltaRow(id: string, name: string, deltaType: 'new' | 'modified' | 'retiring', entityId: number | null): BrowserItem {
  return { kind: 'delta', id, name, metatypeAbbreviation: 'APP', entityId, solutionId: BROWSER_SOLUTION_ID, deltaType, notation: null, childCount: 0, structuringAid: false, version: 0 }
}

/** The objects of the categories of BROWSER, by category key (`current-state:components`, `solution:<id>:components`), in the order of the backend. */
export const BROWSER_ITEMS: Record<string, BrowserItem[]> = {
  'current-state:components': [entityRow(ENTITIES[0]!), entityRow(ENTITIES[1]!), { ...entityRow(ENTITIES[1]!), id: '9105', name: 'Zendesk', metatypeAbbreviation: null }],
  'current-state:connections': [entityRow(ENTITIES[3]!, null)],
  'current-state:catalogs': CATALOGS.slice(0, 2).map((catalog) => ({
    kind: 'catalog' as const,
    id: catalog.id,
    name: catalog.name,
    metatypeAbbreviation: null,
    entityId: null,
    solutionId: null,
    deltaType: null,
    notation: null,
    childCount: 0,
    structuringAid: false,
    version: 0,
  })),
  // two diagrams by name, with the notation of their viewpoint (us-074-diagrams)
  'current-state:diagrams': [
    { kind: 'diagram', id: 'a1000000-0000-4000-8000-000000000001', name: 'Order to Cash', metatypeAbbreviation: null, entityId: null, solutionId: null, deltaType: null, notation: 'archimate3', childCount: 0, structuringAid: false, version: 0 },
    { kind: 'diagram', id: 'a1000000-0000-4000-8000-000000000002', name: 'Procure to Pay', metatypeAbbreviation: null, entityId: null, solutionId: null, deltaType: null, notation: 'bpmn2', childCount: 0, structuringAid: false, version: 0 },
  ],
  [`solution:${BROWSER_SOLUTION_ID}:components`]: [
    deltaRow('c1000000-0000-4000-8000-000000000001', 'CRM Cloud', 'new', null),
    deltaRow('c1000000-0000-4000-8000-000000000002', 'Legacy CRM', 'retiring', 9103),
    deltaRow('c1000000-0000-4000-8000-000000000003', 'SAP ERP', 'modified', 9101),
  ],
}

export interface TreeRequest {
  path: string
  method: string
  body?: Record<string, unknown>
  /** the `If-Match` header of a write of the hierarchy */
  ifMatch?: string
}

/** A component of the hierarchy fake: an element entity of "Current state", or a structuring aid. */
export interface HierarchyComponent {
  id: number
  name: string
  abbreviation: string | null
  parentId: number | null
  version: number
  structuringAid?: boolean
}

/**
 * Components for the hierarchy (US-135, US-136): "Finance landscape" is a structuring aid holding "Payroll", and
 * "Finance module" stays at the root level next to "SAP S/4HANA", "Workday" and the aid.
 */
export const HIERARCHY: HierarchyComponent[] = [
  { id: 9201, name: 'SAP S/4HANA', abbreviation: 'APP', parentId: null, version: 1 },
  { id: 9202, name: 'Finance module', abbreviation: 'APP', parentId: null, version: 1 },
  { id: 9203, name: 'Finance landscape', abbreviation: null, parentId: null, version: 1, structuringAid: true },
  { id: 9204, name: 'Payroll', abbreviation: 'APP', parentId: 9203, version: 1 },
  { id: 9205, name: 'Workday', abbreviation: 'APP', parentId: null, version: 1 },
]

/**
 * A fake of the tree operations with the tree as state, as the backend answers them: names are
 * unique among siblings regardless of case, an entry once per folder, the root not deletable, new
 * folders and entries at the end, and the moves of US-096 with their checks.
 * Other paths go to `fallback`.
 */
export function navigationApi(
  options: {
    tree?: TreeNode
    recorded?: TreeRequest[]
    fallback?: typeof fetch
    failing?: boolean
    rejectMoves?: 404 | 422
    /** the top level of the browser, default BROWSER */
    browser?: Browser
    /** the objects of the categories, default BROWSER_ITEMS */
    browserItems?: Record<string, BrowserItem[]>
    /** the browser answers 500 */
    failingBrowser?: boolean
    /** the components of "Current state" with their parents: the fake then keeps them and answers the writes of the hierarchy */
    components?: HierarchyComponent[]
    /** the write of a parent or of a structuring aid is refused with this status (a 422 names `parentEntityId`) */
    rejectHierarchy?: 409 | 412 | 422 | 429 | 500
  } = {},
): typeof fetch {
  const tree = structuredClone(options.tree ?? TREE)
  let count = 0
  const nodes = () => {
    const all: TreeNode[] = []
    const visit = (node: TreeNode) => {
      all.push(node)
      node.children.forEach(visit)
    }
    visit(tree)
    return all
  }
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
  const invalid = (field?: string) =>
    json({ type: 'urn:oea:problem:validation-failed', title: 'Invalid navigation tree change', status: 422, invalidFields: field ? [field] : [] }, 422)
  const notFound = () => json({ type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 }, 404)
  const taken = (parent: TreeNode, name: string, except?: string) =>
    parent.children.some((child) => child.id !== except && child.name.toLowerCase() === name.trim().toLowerCase())
  const components = options.components ? structuredClone(options.components) : undefined
  let nextComponent = 9300
  const isActive = (id: number | null) => id !== null && components?.some((component) => component.id === id)
  const childrenOf = (id: number) => components?.filter((component) => component.parentId === id) ?? []
  const row = (component: HierarchyComponent): BrowserItem => ({
    kind: 'entity',
    id: String(component.id),
    name: component.name,
    metatypeAbbreviation: component.abbreviation,
    entityId: null,
    solutionId: null,
    deltaType: null,
    notation: null,
    childCount: childrenOf(component.id).length,
    structuringAid: component.structuringAid ?? false,
    version: component.version,
  })
  const problem = (status: number, type: string, extra: Record<string, unknown> = {}) => json({ type: `urn:oea:problem:${type}`, title: type, status, ...extra }, status)
  const refusal = () => {
    switch (options.rejectHierarchy) {
      case 409:
        return problem(409, 'entity-blocked', { entities: [] })
      case 412:
        return problem(412, 'stale-version', { currentVersion: 7 })
      case 422:
        return problem(422, 'validation-failed', { invalidFields: ['parentEntityId'] })
      case 429:
        return json({ type: 'urn:oea:problem:too-many-requests', status: 429 }, 429)
      default:
        return problem(500, 'internal')
    }
  }
  /** The hierarchy of the fake: lists, the parent change and the structuring aids. */
  const hierarchy = (path: string, method: string, search: URLSearchParams, body: Record<string, unknown> | undefined, ifMatch: string | null): Response | undefined => {
    if (!components) {
      return undefined
    }
    if (path === '/api/v1/views/browser') {
      return json({ ...(options.browser ?? BROWSER), currentState: { ...(options.browser ?? BROWSER).currentState, components: components.length } })
    }
    if (path === '/api/v1/views/browser/items' && search.get('scope') === 'current-state' && search.get('category') === 'components') {
      const parent = search.get('parentEntityId')
      if (parent !== null && !isActive(Number(parent))) {
        return notFound()
      }
      const all = components
        .filter((component) => (parent === null ? component.parentId === null || !isActive(component.parentId) : component.parentId === Number(parent)))
        .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()) || a.id - b.id)
      const page = Number(search.get('page') ?? 0)
      const pageSize = Number(search.get('pageSize') ?? 50)
      return json({
        items: all.slice(page * pageSize, (page + 1) * pageSize).map(row),
        page: { page, pageSize, totalElements: all.length, totalPages: Math.ceil(all.length / pageSize) },
      })
    }
    const parentPath = /^\/api\/v1\/entities\/(\d+)\/parent$/.exec(path)
    if (parentPath && method === 'PUT') {
      if (options.rejectHierarchy) {
        return refusal()
      }
      const component = components.find((candidate) => candidate.id === Number(parentPath[1]))
      if (!component) {
        return notFound()
      }
      if (ifMatch !== `"${component.version}"`) {
        return problem(412, 'stale-version', { currentVersion: component.version })
      }
      const parentId = (body?.parentEntityId ?? null) as number | null
      let cursor: HierarchyComponent | undefined = parentId === null ? undefined : components.find((candidate) => candidate.id === parentId)
      if (parentId !== null && !cursor) {
        return problem(422, 'validation-failed', { invalidFields: ['parentEntityId'] })
      }
      for (let level = 0; cursor && level < 20; level += 1) {
        if (cursor.id === component.id) {
          return problem(422, 'validation-failed', { invalidFields: ['parentEntityId'] })
        }
        cursor = components.find((candidate) => candidate.id === cursor!.parentId)
      }
      if (component.parentId !== parentId) {
        component.parentId = parentId
        component.version += 1
      }
      return json({ entityId: component.id, version: component.version, parentEntityId: parentId, composition: { created: false, connectionId: null, reason: parentId === null ? 'cleared' : 'no-composition-metatype' }, removedConnectionId: null })
    }
    if (path === '/api/v1/structuring-aids' && method === 'POST') {
      if (options.rejectHierarchy) {
        return refusal()
      }
      const name = String(body?.name ?? '').trim()
      if (!name || name.length > 255) {
        return problem(422, 'validation-failed', { invalidFields: ['name'] })
      }
      const parentId = (body?.parentEntityId ?? null) as number | null
      if (parentId !== null && !isActive(parentId)) {
        return problem(422, 'validation-failed', { invalidFields: ['parentEntityId'] })
      }
      const aid = { id: ++nextComponent, name, abbreviation: null, parentId, version: 1, structuringAid: true }
      components.push(aid)
      return json({ id: aid.id, name, description: String(body?.description ?? ''), parentEntityId: parentId, version: 1 }, 201)
    }
    const aidPath = /^\/api\/v1\/structuring-aids\/(\d+)$/.exec(path)
    if (aidPath && method === 'DELETE') {
      if (options.rejectHierarchy) {
        return refusal()
      }
      const aid = components.find((candidate) => candidate.id === Number(aidPath[1]))
      if (!aid) {
        return notFound()
      }
      if (ifMatch !== `"${aid.version}"`) {
        return problem(412, 'stale-version', { currentVersion: aid.version })
      }
      if (!aid.structuringAid) {
        return problem(422, 'validation-failed', { invalidFields: ['entityId'] })
      }
      childrenOf(aid.id).forEach((child) => {
        child.parentId = null
        child.version += 1
      })
      components.splice(components.indexOf(aid), 1)
      return new Response(null, { status: 204 })
    }
    return undefined
  }
  return async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://oea.test')
    const method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : undefined
    const path = url.pathname
    const ifMatch = new Headers(init?.headers).get('If-Match')
    if (components && (path.startsWith('/api/v1/views/browser') || path.startsWith('/api/v1/entities/') || path.startsWith('/api/v1/structuring-aids'))) {
      const answered = hierarchy(path, method, url.searchParams, body, ifMatch)
      if (answered) {
        options.recorded?.push({ path: `${path}${url.search}`, method, body, ...(ifMatch ? { ifMatch } : {}) })
        return answered
      }
    }
    if (path === '/api/v1/views/browser' && method === 'GET') {
      options.recorded?.push({ path, method, body })
      return options.failingBrowser ? json({ type: 'urn:oea:problem:internal', status: 500 }, 500) : json(options.browser ?? BROWSER)
    }
    if (path === '/api/v1/views/browser/items' && method === 'GET') {
      options.recorded?.push({ path: `${path}${url.search}`, method, body })
      const scope = url.searchParams.get('scope')
      const category = url.searchParams.get('category')
      const page = Number(url.searchParams.get('page') ?? 0)
      const pageSize = Number(url.searchParams.get('pageSize') ?? 50)
      if (options.failingBrowser || !scope || !category || !(pageSize >= 1 && pageSize <= 200) || !(page >= 0)) {
        return options.failingBrowser ? json({ type: 'urn:oea:problem:internal', status: 500 }, 500) : json({ type: 'urn:oea:problem:validation-failed', status: 400 }, 400)
      }
      const key = scope === 'solution' ? `solution:${url.searchParams.get('solutionId')}:${category}` : `current-state:${category}`
      const all = (options.browserItems ?? BROWSER_ITEMS)[key] ?? []
      return json({
        items: all.slice(page * pageSize, (page + 1) * pageSize),
        page: { page, pageSize, totalElements: all.length, totalPages: Math.ceil(all.length / pageSize) },
      })
    }
    if (path === '/api/v1/views/explorer' || path.startsWith('/api/v1/tree-nodes')) {
      options.recorded?.push({ path, method, body })
    }
    if (path === '/api/v1/views/explorer' && method === 'GET') {
      return options.failing ? json({ type: 'urn:oea:problem:internal', status: 500 }, 500) : json(tree)
    }
    if (path === '/api/v1/tree-nodes' && method === 'POST') {
      const parent = nodes().find((node) => node.id === body?.parentId)
      const name = String(body?.name ?? '').trim()
      if (!parent) {
        return invalid('parentId')
      }
      if (!name || taken(parent, name)) {
        return invalid('name')
      }
      parent.children.push(folder(`f1000000-0000-4000-8000-${String(++count).padStart(12, '0')}`, name, parent.id))
      return json(tree, 201)
    }
    const move = /^\/api\/v1\/tree-nodes\/([^/]+)(?:\/items\/([^/]+))?\/position$/.exec(path)
    if (move && method === 'PUT') {
      if (options.rejectMoves) {
        return options.rejectMoves === 404 ? notFound() : invalid('parentId')
      }
      const node = nodes().find((candidate) => candidate.id === move[1])
      if (!node) {
        return notFound()
      }
      const position = body?.position
      if (typeof position !== 'number' || !Number.isInteger(position) || position < 0 || position > 10000) {
        return invalid('position')
      }
      const parentOf = (target: TreeNode) => nodes().find((candidate) => candidate.children.includes(target))
      if (!move[2]) {
        const parent = nodes().find((candidate) => candidate.id === body?.parentId)
        const levelOf = (target: TreeNode): number => (parentOf(target) ? levelOf(parentOf(target)!) + 1 : 0)
        const heightOf = (target: TreeNode): number => target.children.reduce((height, child) => Math.max(height, heightOf(child) + 1), 0)
        const within = (target: TreeNode, id: string): boolean => target.id === id || target.children.some((child) => within(child, id))
        if (!parentOf(node)) {
          return invalid()
        }
        if (!parent || within(node, parent.id) || taken(parent, node.name, node.id) || levelOf(parent) + 1 + heightOf(node) > 10) {
          return invalid('parentId')
        }
        parentOf(node)!.children.splice(parentOf(node)!.children.indexOf(node), 1)
        parent.children.splice(Math.min(position, parent.children.length), 0, node)
        node.parentId = parent.id
        return json(tree)
      }
      const entry = node.items.find((candidate) => candidate.id === move[2])
      if (!entry) {
        return notFound()
      }
      const target = nodes().find((candidate) => candidate.id === body?.nodeId)
      const same = (a: TreeNodeItem) => a.id !== entry.id && a.itemType === entry.itemType && a.entityId === entry.entityId && a.referenceId === entry.referenceId
      if (!target || target.items.some(same) || (target !== node && target.items.length >= 200)) {
        return invalid('nodeId')
      }
      node.items.splice(node.items.indexOf(entry), 1)
      target.items.splice(Math.min(position, target.items.length), 0, entry)
      return json(tree)
    }
    const match = /^\/api\/v1\/tree-nodes\/([^/]+)(?:\/items(?:\/([^/]+))?)?$/.exec(path)
    if (match) {
      const node = nodes().find((candidate) => candidate.id === match[1])
      if (!node) {
        return notFound()
      }
      const parent = nodes().find((candidate) => candidate.children.includes(node))
      if (path.endsWith('/items') && method === 'POST') {
        const label = typeof body?.displayLabel === 'string' && body.displayLabel.trim() ? body.displayLabel.trim() : null
        const id = `e1000000-0000-4000-8000-${String(++count).padStart(12, '0')}`
        if (node.items.length >= 200) {
          return invalid(body?.itemType === 'entity' ? 'entityId' : 'referenceId')
        }
        if (body?.itemType === 'entity') {
          const entityId = Number(body.entityId)
          const entity = ENTITIES.find((candidate) => candidate.id === entityId) ?? (entityId > 0 && entityId < 9000 ? { id: entityId, name: `Entity ${entityId}`, deleted: false, connection: false } : undefined)
          if (!entity || entity.deleted || entity.connection || node.items.some((entry) => entry.entityId === entity.id)) {
            return invalid('entityId')
          }
          node.items.push(entityItem(id, entity, label))
        } else if (body?.itemType === 'solution') {
          // a solution of another fake is taken as it comes
          const solution = SOLUTIONS.find((candidate) => candidate.id === body.referenceId) ?? (typeof body.referenceId === 'string' && body.referenceId ? { id: body.referenceId, name: 'Solution' } : undefined)
          if (!solution || node.items.some((entry) => entry.referenceId === solution.id)) {
            return invalid('referenceId')
          }
          node.items.push(solutionItem(id, solution, label))
        } else {
          const catalog = CATALOGS.find((candidate) => candidate.id === body?.referenceId)
          if (!catalog || node.items.some((entry) => entry.referenceId === catalog.id)) {
            return invalid('referenceId')
          }
          node.items.push(item(id, catalog, label))
        }
        return json(tree, 201)
      }
      if (match[2] && method === 'DELETE') {
        const index = node.items.findIndex((entry) => entry.id === match[2])
        if (index < 0) {
          return notFound()
        }
        node.items.splice(index, 1)
        return json(tree)
      }
      if (method === 'PATCH') {
        const name = String(body?.name ?? '').trim()
        if (!name || (parent && taken(parent, name, node.id))) {
          return invalid('name')
        }
        node.name = name
        return json(tree)
      }
      if (method === 'DELETE') {
        if (!parent) {
          return invalid()
        }
        parent.children.splice(parent.children.indexOf(node), 1)
        return json(tree)
      }
    }
    if (options.fallback) {
      return options.fallback(input, init)
    }
    throw new TypeError(`no route for ${method} ${path}`)
  }
}
