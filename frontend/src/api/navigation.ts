import type { components } from './generated/schema'
import { readRejection, RequestRejected } from './problem'
import { currentCsrfToken, type Outcome } from './session'

export type TreeNode = components['schemas']['TreeNode']
export type TreeNodeItem = components['schemas']['TreeNodeItem']

/** A folder with its path from the root, e.g. "Repository / IT Landscape". */
export interface FolderPath {
  id: string
  path: string
  depth: number
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** GET /views/explorer: the navigation tree from the root as the caller sees it (US-054). */
export async function fetchTree(fetchFn: typeof fetch = fetch): Promise<TreeNode> {
  const response = await fetchFn('/api/v1/views/explorer', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as TreeNode
}

/** A write of the tree with the CSRF token; every write answers with the whole tree. */
async function write(method: 'POST' | 'PATCH' | 'DELETE', path: string, body: unknown, fetchFn: typeof fetch): Promise<Outcome<TreeNode>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(path, {
    method,
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  if (response.ok) {
    return { ok: true, value: (await response.json()) as TreeNode }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /tree-nodes: a folder under the parent; a taken name is 422 naming `name` (REQ-094). */
export function createFolder(parentId: string, name: string, fetchFn: typeof fetch = fetch): Promise<Outcome<TreeNode>> {
  return write('POST', '/api/v1/tree-nodes', { name, parentId }, fetchFn)
}

/** PATCH /tree-nodes/{nodeId}: renames the folder (REQ-094). */
export function renameFolder(nodeId: string, name: string, fetchFn: typeof fetch = fetch): Promise<Outcome<TreeNode>> {
  return write('PATCH', `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}`, { name }, fetchFn)
}

/** DELETE /tree-nodes/{nodeId}: deletes the folder with its subfolders and entries, never a catalog (REQ-094). */
export function deleteFolder(nodeId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<TreeNode>> {
  return write('DELETE', `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}`, undefined, fetchFn)
}

/**
 * POST /tree-nodes/{nodeId}/items: places the catalog in the folder (US-054). A second entry in
 * the folder is 422 naming `referenceId`.
 */
export function placeCatalog(
  nodeId: string,
  catalogId: string,
  displayLabel: string | null,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<TreeNode>> {
  return write('POST', `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}/items`, { itemType: 'catalog', referenceId: catalogId, displayLabel }, fetchFn)
}

/** DELETE /tree-nodes/{nodeId}/items/{itemId}: removes only this entry (US-054 AC4). */
export function removeTreeItem(nodeId: string, itemId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<TreeNode>> {
  return write('DELETE', `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}/items/${encodeURIComponent(itemId)}`, undefined, fetchFn)
}

/** The text the tree shows for an entry: its display label, else the catalog's name. */
export function itemLabel(item: TreeNodeItem): string {
  return item.displayLabel ?? item.name
}

/** Every folder of the tree with its path, in the order of the tree. */
export function folderPaths(root: TreeNode): FolderPath[] {
  const paths: FolderPath[] = []
  const visit = (node: TreeNode, prefix: string, depth: number) => {
    const path = prefix ? `${prefix} / ${node.name}` : node.name
    paths.push({ id: node.id, path, depth })
    node.children.forEach((child) => visit(child, path, depth + 1))
  }
  visit(root, '', 0)
  return paths
}

/** The number of subfolders and entries below the folder, which deleting it removes. */
export function contentsOf(node: TreeNode): { folders: number; items: number } {
  return node.children.reduce(
    (total, child) => {
      const below = contentsOf(child)
      return { folders: total.folders + 1 + below.folders, items: total.items + below.items }
    },
    { folders: 0, items: node.items.length },
  )
}
