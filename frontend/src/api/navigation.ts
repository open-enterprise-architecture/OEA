import type { components } from './generated/schema'
import { readRejection, RequestRejected } from './problem'
import { currentCsrfToken, type Outcome } from './session'

export type TreeNode = components['schemas']['TreeNode']
export type TreeNodeItem = components['schemas']['TreeNodeItem']
export type TreeItemType = TreeNodeItem['itemType']
export type Browser = components['schemas']['Browser']
export type BrowserPlateau = components['schemas']['BrowserPlateau']
export type BrowserSolution = components['schemas']['BrowserSolution']
export type BrowserItem = components['schemas']['BrowserItem']
export type BrowserItemPage = components['schemas']['BrowserItemPage']
export type BrowserCategory = 'components' | 'connections' | 'catalogs'

/** What "Place in navigation" places (US-054, US-095): a catalog, an element entity or a solution. */
export type Placement =
  | { itemType: 'catalog'; referenceId: string; name: string }
  | { itemType: 'solution'; referenceId: string; name: string }
  | { itemType: 'entity'; entityId: number; name: string }

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

/** GET /views/browser: the top level of the solution browser with all counts (US-132, REQ-138). */
export async function fetchBrowser(fetchFn: typeof fetch = fetch): Promise<Browser> {
  const response = await fetchFn('/api/v1/views/browser', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as Browser
}

/** What a category of the browser lists: the current state or one solution, and a page of it. */
export interface BrowserItemsQuery {
  scope: 'current-state' | 'solution'
  solutionId?: string
  category: BrowserCategory
  page?: number
  pageSize?: number
}

/** GET /views/browser/items: one page of one category of the current state or of a solution (US-133). */
export async function fetchBrowserItems(query: BrowserItemsQuery, fetchFn: typeof fetch = fetch): Promise<BrowserItemPage> {
  const params = new URLSearchParams({ scope: query.scope, category: query.category, page: String(query.page ?? 0), pageSize: String(query.pageSize ?? 50) })
  if (query.solutionId) {
    params.set('solutionId', query.solutionId)
  }
  const response = await fetchFn(`/api/v1/views/browser/items?${params}`, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as BrowserItemPage
}

/** The route of a browser object, built only from its kind and IDs: entity history, catalog page or the solution of a delta. */
export function browserItemRoute(item: BrowserItem): string | undefined {
  if (item.kind === 'entity') {
    return `/entities/${encodeURIComponent(item.id)}/history`
  }
  if (item.kind === 'catalog') {
    return `/catalogs/${encodeURIComponent(item.id)}`
  }
  return item.solutionId ? `/solutions/${encodeURIComponent(item.solutionId)}` : undefined
}

/** A write of the tree with the CSRF token; every write answers with the whole tree. */
async function write(method: 'POST' | 'PATCH' | 'PUT' | 'DELETE', path: string, body: unknown, fetchFn: typeof fetch): Promise<Outcome<TreeNode>> {
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
 * POST /tree-nodes/{nodeId}/items: places the catalog, entity or solution at the end of the folder
 * (US-054, US-095, US-097). A second entry of the same target in the folder, an unknown, deleted or
 * connection entity and a full folder are 422 naming `referenceId` or `entityId`.
 */
export function placeInTree(
  nodeId: string,
  placement: Placement,
  displayLabel: string | null,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<TreeNode>> {
  const target = placement.itemType === 'entity' ? { entityId: placement.entityId } : { referenceId: placement.referenceId }
  return write('POST', `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}/items`, { itemType: placement.itemType, ...target, displayLabel }, fetchFn)
}

/**
 * PUT /tree-nodes/{nodeId}/position: moves the folder under `parentId` at the 0-based position
 * among the new siblings (US-096). A cycle, a taken name or a depth above the limit is 422 naming
 * `parentId`.
 */
export function moveTreeNode(nodeId: string, parentId: string, position: number, fetchFn: typeof fetch = fetch): Promise<Outcome<TreeNode>> {
  return write('PUT', `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}/position`, { parentId, position }, fetchFn)
}

/**
 * PUT /tree-nodes/{nodeId}/items/{itemId}/position: moves the entry to the folder `targetNodeId` at
 * the 0-based position among the entries the caller sees (US-096). A target folder that holds the
 * entry's target already, or is full, is 422 naming `nodeId`.
 */
export function moveTreeNodeItem(
  nodeId: string,
  itemId: string,
  targetNodeId: string,
  position: number,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<TreeNode>> {
  return write(
    'PUT',
    `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}/items/${encodeURIComponent(itemId)}/position`,
    { nodeId: targetNodeId, position },
    fetchFn,
  )
}

/** DELETE /tree-nodes/{nodeId}/items/{itemId}: removes only this entry (US-054 AC4). */
export function removeTreeItem(nodeId: string, itemId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<TreeNode>> {
  return write('DELETE', `/api/v1/tree-nodes/${encodeURIComponent(nodeId)}/items/${encodeURIComponent(itemId)}`, undefined, fetchFn)
}

/** The text the tree shows for an entry: its display label, else the current name of its target. */
export function itemLabel(item: TreeNodeItem): string {
  return item.displayLabel ?? item.name
}

/** The route of an entry, built only from its item type and target ID (design D7 of us-095). */
export function itemRoute(item: TreeNodeItem): string | undefined {
  if (item.itemType === 'entity') {
    return item.entityId === null ? undefined : `/entities/${item.entityId}/history`
  }
  if (!item.referenceId) {
    return undefined
  }
  return item.itemType === 'solution' ? `/solutions/${encodeURIComponent(item.referenceId)}` : `/catalogs/${encodeURIComponent(item.referenceId)}`
}

/** Whether two entries point to the same target, which a folder holds at most once (tree-node BR-06). */
export function sameTarget(a: Pick<TreeNodeItem, 'itemType' | 'referenceId' | 'entityId'>, b: Pick<TreeNodeItem, 'itemType' | 'referenceId' | 'entityId'>): boolean {
  if (a.itemType !== b.itemType) {
    return false
  }
  return a.itemType === 'entity' ? a.entityId === b.entityId : a.referenceId === b.referenceId
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
