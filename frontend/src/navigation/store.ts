import { reactive, ref } from 'vue'
import {
  createStructuringAid,
  deleteStructuringAid,
  fetchBrowser,
  fetchBrowserItems,
  fetchTree,
  setEntityParent,
  type Browser,
  type BrowserItem,
  type BrowserItemsQuery,
  type TreeNode,
} from '../api/navigation'
import type { Rejection } from '../api/problem'

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
  /** what the objects were read with, so that a write can read the loaded pages again */
  query?: Omit<BrowserItemsQuery, 'page'>
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
  state.query = query
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

/** The key of an element entity of the hierarchy in `browser.expanded`, and of its children in `browser.categories`. */
export const entityKey = (id: string | number): string => `entity:${id}`
export const childrenKey = (id: string | number): string => `entity:${id}:children`

/** What the children of an element entity are read with (US-135). */
export function childrenQuery(id: number): Omit<BrowserItemsQuery, 'page'> {
  return { scope: 'current-state', category: 'components', parentEntityId: id }
}

/** The entity row that is being dragged; a drop takes it from here, never from `dataTransfer`. */
export const drag = reactive<{ item?: BrowserItem }>({})

const CHILDREN_KEY = /^entity:(\d+):children$/

/** The parent of a loaded component: `null` at the root level, `undefined` if it is not loaded. */
export function loadedParentOf(id: string): number | null | undefined {
  if (browser.categories['current-state:components']?.items.some((item) => item.id === id)) {
    return null
  }
  for (const [key, state] of Object.entries(browser.categories)) {
    const match = CHILDREN_KEY.exec(key)
    if (match && state.items.some((item) => item.id === id)) {
      return Number(match[1])
    }
  }
  return undefined
}

/** Whether `id` lies below `ancestorId` among the loaded nodes; the server decides beyond them. */
export function isLoadedDescendant(ancestorId: string, id: string): boolean {
  let current = id
  for (let level = 0; level < 12; level += 1) {
    const parent = loadedParentOf(current)
    if (parent === null || parent === undefined) {
      return false
    }
    if (String(parent) === ancestorId) {
      return true
    }
    current = String(parent)
  }
  return false
}

/**
 * Whether the dragged component may be dropped on `target`, an element row, or on the root level with `null`
 * (US-135 AC1): not onto itself, not onto a loaded descendant, and not where it already is.
 */
export function canDropOn(dragged: BrowserItem, target: BrowserItem | null): boolean {
  if (target === null) {
    return loadedParentOf(dragged.id) !== null
  }
  if (target.kind !== 'entity' || target.id === dragged.id || isLoadedDescendant(dragged.id, target.id)) {
    return false
  }
  return loadedParentOf(dragged.id) !== Number(target.id)
}

async function reloadCategory(key: string, fetchFn?: typeof fetch): Promise<void> {
  const state = browser.categories[key]
  if (!state?.query || !state.loaded) {
    return
  }
  const pages = Math.max(state.page, 1)
  try {
    const items: BrowserItem[] = []
    let total = state.total
    for (let page = 0; page < pages; page += 1) {
      const result = await fetchBrowserItems({ ...state.query, page }, fetchFn)
      items.push(...result.items)
      total = result.page.totalElements
    }
    state.items = items
    state.total = total
  } catch {
    // the entity may be gone meanwhile; its children are read again when it is expanded
    delete browser.categories[key]
  }
}

/**
 * Reads the top level and the loaded lists of components and connections again after a write of the hierarchy
 * (design D5 of us-135-component-hierarchy): the nodes stay expanded, and every loaded page is replaced.
 */
export async function reloadHierarchy(fetchFn?: typeof fetch): Promise<void> {
  const keys = Object.keys(browser.categories).filter((key) => key === 'current-state:components' || key === 'current-state:connections' || CHILDREN_KEY.test(key))
  await Promise.all([
    fetchBrowser(fetchFn)
      .then((view) => {
        browser.view = view
      })
      .catch(() => undefined),
    ...keys.map((key) => reloadCategory(key, fetchFn)),
  ])
}

/** Opens a node of the hierarchy and loads its children once. */
export async function expandChildren(id: number, fetchFn?: typeof fetch): Promise<void> {
  browser.expanded.add(entityKey(id))
  await loadCategory(childrenKey(id), childrenQuery(id), fetchFn)
}

/** Runs a write of the hierarchy and shows its result; a rejection is returned, a stale version also reloads the lists. */
async function changeHierarchy<T>(write: () => Promise<{ ok: true; value: T } | { ok: false; rejection: Rejection }>, fetchFn?: typeof fetch, open?: number | null): Promise<Rejection | undefined> {
  try {
    const outcome = await write()
    if (!outcome.ok) {
      if (outcome.rejection.status === 412) {
        await reloadHierarchy(fetchFn)
      }
      return outcome.rejection
    }
  } catch {
    return { status: 0, problemType: 'unknown', invalidFields: [] }
  }
  await reloadHierarchy(fetchFn)
  if (open !== undefined && open !== null) {
    await expandChildren(open, fetchFn)
  }
  return undefined
}

/** Places the component under `parentId`, or at the root level with `null` (US-135). */
export function moveComponent(item: BrowserItem, parentId: number | null, fetchFn?: typeof fetch): Promise<Rejection | undefined> {
  return changeHierarchy(() => setEntityParent(Number(item.id), item.version, parentId, fetchFn), fetchFn, parentId)
}

/** Creates a structuring aid at the root level or under `parentId` (US-136). */
export function addStructuringAid(input: { name: string; description?: string; parentEntityId: number | null }, fetchFn?: typeof fetch): Promise<Rejection | undefined> {
  return changeHierarchy(() => createStructuringAid({ name: input.name, ...(input.description ? { description: input.description } : {}), parentEntityId: input.parentEntityId }, fetchFn), fetchFn, input.parentEntityId)
}

/** Deletes a structuring aid; its children are at the root level afterwards (US-136 AC3). */
export function removeStructuringAid(item: BrowserItem, fetchFn?: typeof fetch): Promise<Rejection | undefined> {
  return changeHierarchy(() => deleteStructuringAid(Number(item.id), item.version, fetchFn), fetchFn)
}

/** Forgets the browser, e.g. on logout. */
export function clearBrowser(): void {
  drag.item = undefined
  browser.view = undefined
  browser.failed = false
  browser.loading = false
  browser.expanded = new Set<string>()
  browser.categories = {}
  pendingBrowser = undefined
}

/** Whether the sidebar is open as a slideover on narrow screens; the menu button of the header opens it. */
export const sidebar = reactive({ open: false })
