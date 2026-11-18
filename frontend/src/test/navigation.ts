/** Test and story data for the navigation tree (US-054, SCR-055, SCR-020). */
import type { TreeNode, TreeNodeItem } from '../api/navigation'
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

export interface TreeRequest {
  path: string
  method: string
  body?: Record<string, unknown>
}

/**
 * A fake of the tree operations with the tree as state, as the backend answers them: names are
 * unique among siblings regardless of case, an entry once per folder, the root not deletable, new
 * folders and entries at the end, and the moves of US-096 with their checks.
 * Other paths go to `fallback`.
 */
export function navigationApi(
  options: { tree?: TreeNode; recorded?: TreeRequest[]; fallback?: typeof fetch; failing?: boolean; rejectMoves?: 404 | 422 } = {},
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
  return async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://oea.test')
    const method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : undefined
    const path = url.pathname
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
