import { reactive } from 'vue'
import { fetchTree, type TreeNode } from '../api/navigation'

/**
 * The navigation tree of the session, shared by the sidebar and "Place in navigation" (design D4
 * of us-054-catalog-navigation-tree): loaded once per session and replaced by the answer of each
 * write.
 */
export const navigation = reactive<{ tree?: TreeNode; loading: boolean; failed: boolean }>({ loading: false, failed: false })

let pending: Promise<void> | undefined

/** Loads the tree unless it is loaded already; `force` reloads it. */
export function loadTree(fetchFn?: typeof fetch, force = false): Promise<void> {
  if (navigation.tree && !force) {
    return Promise.resolve()
  }
  if (pending) {
    return pending
  }
  navigation.loading = true
  pending = fetchTree(fetchFn)
    .then((tree) => {
      navigation.tree = tree
      navigation.failed = false
    })
    .catch(() => {
      navigation.failed = true
    })
    .finally(() => {
      navigation.loading = false
      pending = undefined
    })
  return pending
}

/** Shows the tree a write answered with. */
export function showTree(tree: TreeNode): void {
  navigation.tree = tree
  navigation.failed = false
}

/** Forgets the tree, e.g. on logout. */
export function clearTree(): void {
  navigation.tree = undefined
  navigation.failed = false
}

/** Whether the sidebar is open as a slideover on narrow screens; the menu button of the header opens it. */
export const sidebar = reactive({ open: false })
