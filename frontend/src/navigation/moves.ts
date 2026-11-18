import type { InjectionKey } from 'vue'
import { folderPaths, sameTarget, type FolderPath, type TreeNode, type TreeNodeItem } from '../api/navigation'

/**
 * The moves of the navigation tree (US-096; design D5 of us-095-navigation-tree-items): drag and
 * drop and "Move up", "Move down" and "Move to…" compute the request here, and refuse the targets
 * the server would reject (a cycle, a taken name, the depth and the entries per folder).
 */

/** The deepest level of a folder below the root (tree-node BR-01). */
export const MAX_DEPTH = 10
/** The entries of one folder (tree-node BR-06). */
export const MAX_ITEMS_PER_FOLDER = 200

/** A folder (never the root) or an entry with its folder, as it is dragged or moved. */
export type Subject = { kind: 'folder'; node: TreeNode } | { kind: 'item'; node: TreeNode; item: TreeNodeItem }

/** Where a drop lands: on a folder (appends) or on an entry (inserts before it). */
export type DropTarget = { kind: 'folder'; node: TreeNode } | { kind: 'item'; node: TreeNode; item: TreeNodeItem }

/** A move request: `moveTreeNode` for a folder, `moveTreeNodeItem` for an entry. */
export type Move =
  | { kind: 'folder'; nodeId: string; parentId: string; position: number }
  | { kind: 'item'; nodeId: string; itemId: string; targetNodeId: string; position: number }

/** The drag state of one tree; the drop takes the dragged element from here, never from `dataTransfer` (D7). */
export interface DragState {
  dragged?: Subject
}

export const DRAG_STATE: InjectionKey<DragState> = Symbol('navigation-drag-state')

/** The parent of the folder in the tree, undefined for the root. */
export function parentOf(root: TreeNode, id: string): TreeNode | undefined {
  for (const child of root.children) {
    if (child.id === id) {
      return root
    }
    const found = parentOf(child, id)
    if (found) {
      return found
    }
  }
  return undefined
}

/** The level of the folder below the root (the root is 0), or -1 if it is not in the tree. */
export function depthOf(root: TreeNode, id: string, depth = 0): number {
  if (root.id === id) {
    return depth
  }
  for (const child of root.children) {
    const found = depthOf(child, id, depth + 1)
    if (found >= 0) {
      return found
    }
  }
  return -1
}

/** The levels of subfolders below the folder (0 without subfolders). */
export function heightOf(node: TreeNode): number {
  return node.children.reduce((height, child) => Math.max(height, heightOf(child) + 1), 0)
}

/** Whether `id` is the folder itself or one of its descendants. */
export function isWithin(node: TreeNode, id: string): boolean {
  return node.id === id || node.children.some((child) => isWithin(child, id))
}

/** Whether the folder may go under `parent`: no cycle, a free name, and within the depth (BR-01, BR-03). */
function folderFits(root: TreeNode, folder: TreeNode, parent: TreeNode): boolean {
  if (isWithin(folder, parent.id)) {
    return false
  }
  const name = folder.name.toLowerCase()
  if (parent.children.some((child) => child.id !== folder.id && child.name.toLowerCase() === name)) {
    return false
  }
  return depthOf(root, parent.id) + 1 + heightOf(folder) <= MAX_DEPTH
}

/** Whether the entry may go into `target`: its target not there yet, and room for it (BR-06). */
function itemFits(item: TreeNodeItem, from: TreeNode, target: TreeNode): boolean {
  if (target.items.some((other) => other.id !== item.id && sameTarget(other, item))) {
    return false
  }
  return target.id === from.id || target.items.length < MAX_ITEMS_PER_FOLDER
}

/** The move a drop asks for, or undefined when the target refuses the drop. */
export function planDrop(root: TreeNode, dragged: Subject, target: DropTarget): Move | undefined {
  if (dragged.kind === 'folder') {
    if (target.kind !== 'folder' || !folderFits(root, dragged.node, target.node)) {
      return undefined
    }
    const siblings = target.node.children.filter((child) => child.id !== dragged.node.id)
    return { kind: 'folder', nodeId: dragged.node.id, parentId: target.node.id, position: siblings.length }
  }
  if (!itemFits(dragged.item, dragged.node, target.node)) {
    return undefined
  }
  const others = target.node.items.filter((other) => other.id !== dragged.item.id)
  let position = others.length
  if (target.kind === 'item') {
    if (target.item.id === dragged.item.id) {
      return undefined
    }
    position = others.findIndex((other) => other.id === target.item.id)
  }
  return { kind: 'item', nodeId: dragged.node.id, itemId: dragged.item.id, targetNodeId: target.node.id, position }
}

/** "Move up" or "Move down" within the folder; undefined for the first or last element. */
export function planStep(root: TreeNode, subject: Subject, step: -1 | 1): Move | undefined {
  if (subject.kind === 'folder') {
    const parent = parentOf(root, subject.node.id)
    if (!parent) {
      return undefined
    }
    const position = parent.children.findIndex((child) => child.id === subject.node.id) + step
    if (position < 0 || position >= parent.children.length) {
      return undefined
    }
    return { kind: 'folder', nodeId: subject.node.id, parentId: parent.id, position }
  }
  const position = subject.node.items.findIndex((item) => item.id === subject.item.id) + step
  if (position < 0 || position >= subject.node.items.length) {
    return undefined
  }
  return { kind: 'item', nodeId: subject.node.id, itemId: subject.item.id, targetNodeId: subject.node.id, position }
}

/** The folders "Move to…" offers: every folder the element can move to, its current folder excluded. */
export function moveTargets(root: TreeNode, subject: Subject): FolderPath[] {
  const byId = new Map<string, TreeNode>()
  const visit = (node: TreeNode) => {
    byId.set(node.id, node)
    node.children.forEach(visit)
  }
  visit(root)
  const current = subject.kind === 'folder' ? parentOf(root, subject.node.id)?.id : subject.node.id
  return folderPaths(root).filter((path) => {
    const node = byId.get(path.id)
    if (!node || node.id === current) {
      return false
    }
    return subject.kind === 'folder' ? folderFits(root, subject.node, node) : itemFits(subject.item, subject.node, node)
  })
}

/** The move "Move to…" asks for: the end of the chosen folder. */
export function planMoveTo(root: TreeNode, subject: Subject, targetId: string): Move | undefined {
  if (!moveTargets(root, subject).some((path) => path.id === targetId)) {
    return undefined
  }
  const find = (node: TreeNode): TreeNode | undefined => (node.id === targetId ? node : node.children.map(find).find(Boolean))
  const target = find(root)
  return target ? planDrop(root, subject, { kind: 'folder', node: target }) : undefined
}
