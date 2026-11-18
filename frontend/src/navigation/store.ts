import { reactive, ref } from 'vue'
import { fetchBrowser, fetchBrowserItems, fetchTree, type Browser, type BrowserItem, type BrowserItemsQuery, type TreeNode } from '../api/navigation'

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
  clearBrowser()
  sidebarTab.value = 'browser'
}

/** The tabs of the sidebar (US-132 AC1): "Browser" is the default, and the chosen tab stays while the person moves between pages. */
export type SidebarTab = 'browser' | 'tree'
export const sidebarTab = ref<SidebarTab>('browser')

/** The loaded objects of one category of the solution browser; `page` is the next page to load. */
export interface BrowserCategoryState {
  items: BrowserItem[]
  total: number
  page: number
  loading: boolean
  failed: boolean
  loaded: boolean
}

/**
 * The solution browser of the session (design D4 of us-132-solution-browser): the top level is loaded once, a
 * category on its first expansion and then page by page; the expanded nodes are kept while the tab is closed.
 */
export const browser = reactive<{ view?: Browser; loading: boolean; failed: boolean; expanded: Set<string>; categories: Record<string, BrowserCategoryState> }>({
  loading: false,
  failed: false,
  expanded: new Set<string>(),
  categories: {},
})

let pendingBrowser: Promise<void> | undefined

/** Loads the top level of the browser unless it is loaded already; `force` reloads it and forgets the loaded categories. */
export function loadBrowser(fetchFn?: typeof fetch, force = false): Promise<void> {
  if (browser.view && !force) {
    return Promise.resolve()
  }
  if (pendingBrowser) {
    return pendingBrowser
  }
  browser.loading = true
  pendingBrowser = fetchBrowser(fetchFn)
    .then((view) => {
      browser.view = view
      browser.failed = false
      browser.categories = {}
    })
    .catch(() => {
      browser.failed = true
    })
    .finally(() => {
      browser.loading = false
      pendingBrowser = undefined
    })
  return pendingBrowser
}

/** Loads the first page of a category once, or the next page with `more`. */
export async function loadCategory(key: string, query: Omit<BrowserItemsQuery, 'page'>, fetchFn?: typeof fetch, more = false): Promise<void> {
  // read the state back from the reactive object, so that the changes below are tracked
  browser.categories[key] ??= { items: [], total: 0, page: 0, loading: false, failed: false, loaded: false }
  const state = browser.categories[key]!
  if (state.loading || (state.loaded && !more)) {
    return
  }
  state.loading = true
  state.failed = false
  try {
    const result = await fetchBrowserItems({ ...query, page: state.page }, fetchFn)
    state.items.push(...result.items)
    state.total = result.page.totalElements
    state.page += 1
    state.loaded = true
  } catch {
    state.failed = true
  } finally {
    state.loading = false
  }
}

/** Forgets the browser, e.g. on logout. */
export function clearBrowser(): void {
  browser.view = undefined
  browser.failed = false
  browser.loading = false
  browser.expanded = new Set<string>()
  browser.categories = {}
  pendingBrowser = undefined
}

/** Whether the sidebar is open as a slideover on narrow screens; the menu button of the header opens it. */
export const sidebar = reactive({ open: false })
