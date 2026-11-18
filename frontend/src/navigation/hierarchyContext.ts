import type { InjectionKey } from 'vue'
import type { BrowserItem } from '../api/navigation'

/**
 * What the rows of the components of "Current state" need to arrange components (US-135, US-136; design D5 of
 * us-135-component-hierarchy): the permissions of the person and the actions the browser carries out with its dialogs.
 */
export interface HierarchyContext {
  /** `entity:write`: drag and drop, "Move to…" and "New structuring aid" */
  canWrite: boolean
  /** `entity:delete` together with `entity:write`: "Delete" of a structuring aid */
  canDelete: boolean
  moveTo: (item: BrowserItem) => void
  newAid: (parent: BrowserItem | null) => void
  deleteAid: (item: BrowserItem) => void
  /** drops the dragged component on an element row, or on the root level with `null` */
  drop: (dragged: BrowserItem, target: BrowserItem | null) => void
  fetchFn?: typeof fetch
}

export const HIERARCHY_CONTEXT: InjectionKey<HierarchyContext> = Symbol('browser-hierarchy-context')
