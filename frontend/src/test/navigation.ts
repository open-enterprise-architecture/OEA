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

function item(id: string, catalog: (typeof CATALOGS)[number], displayLabel: string | null = null): TreeNodeItem {
  return { id, itemType: 'catalog', referenceId: catalog.id, displayLabel, sortOrder: 0, name: catalog.name, scope: catalog.scope }
}

function folder(id: string, name: string, parentId: string | null, items: TreeNodeItem[] = [], children: TreeNode[] = []): TreeNode {
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

export const EMPTY_TREE: TreeNode = folder(ROOT_ID, 'Repository', null)

export interface TreeRequest {
  path: string
  method: string
  body?: Record<string, unknown>
}

/**
 * A fake of the tree operations with the tree as state, as the backend answers them: names are
 * unique among siblings regardless of case, a catalog once per folder, the root not deletable.
 * Other paths go to `fallback`.
 */
export function navigationApi(
  options: { tree?: TreeNode; recorded?: TreeRequest[]; fallback?: typeof fetch; failing?: boolean } = {},
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
  const sort = (node: TreeNode) => {
    node.children.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
    node.items.sort((a, b) => (a.displayLabel ?? a.name).toLowerCase().localeCompare((b.displayLabel ?? b.name).toLowerCase()))
  }
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
      sort(parent)
      return json(tree, 201)
    }
    const match = /^\/api\/v1\/tree-nodes\/([^/]+)(?:\/items(?:\/([^/]+))?)?$/.exec(path)
    if (match) {
      const node = nodes().find((candidate) => candidate.id === match[1])
      if (!node) {
        return notFound()
      }
      const parent = nodes().find((candidate) => candidate.children.includes(node))
      if (path.endsWith('/items') && method === 'POST') {
        const catalog = CATALOGS.find((candidate) => candidate.id === body?.referenceId)
        if (!catalog || node.items.some((entry) => entry.referenceId === catalog.id)) {
          return invalid('referenceId')
        }
        const label = typeof body?.displayLabel === 'string' && body.displayLabel.trim() ? body.displayLabel.trim() : null
        node.items.push(item(`e1000000-0000-4000-8000-${String(++count).padStart(12, '0')}`, catalog, label))
        sort(node)
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
        if (parent) {
          sort(parent)
        }
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
