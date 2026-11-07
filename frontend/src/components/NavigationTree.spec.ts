import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { clearTree } from '../navigation/store'
import { CATALOGS } from '../test/catalogs'
import { COMPLIANCE_ID, LANDSCAPE_ID, navigationApi, ROOT_ID, type TreeRequest } from '../test/navigation'
import { testRouter } from '../test/router'
import NavigationTree from './NavigationTree.vue'

const mounted: VueWrapper[] = []

async function tree(canManage: boolean, options: Parameters<typeof navigationApi>[0] = {}) {
  const recorded: TreeRequest[] = options.recorded ?? []
  const router = testRouter()
  await router.push('/')
  const wrapper = mount(NavigationTree, {
    props: { canManage, fetchFn: navigationApi({ ...options, recorded }) },
    global: { plugins: [ui, router] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router, recorded }
}

function folderRow(id: string) {
  return document.querySelector<HTMLElement>(`[data-folder-id="${id}"]`)!
}

function names() {
  return [...document.querySelectorAll('[data-testid="tree-folder-name"]')].map((element) => element.textContent?.trim())
}

function labels() {
  // the label without the hint for screen readers
  return [...document.querySelectorAll('[data-testid="tree-item-link"]')].map((element) => element.firstChild?.textContent?.trim())
}

async function expand(id: string) {
  folderRow(id).querySelector<HTMLButtonElement>('[data-testid="tree-toggle"]')!.click()
  await flushPromises()
}

/** Opens the actions of the folder and chooses one by its label. */
async function choose(id: string, label: string) {
  const trigger = folderRow(id).querySelector<HTMLButtonElement>('[data-testid="tree-folder-actions"]')!
  trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
  await flushPromises()
  const item = [...document.querySelectorAll<HTMLElement>('[role="menuitem"]')].find((element) => element.textContent?.trim() === label)
  expect(item, label).toBeDefined()
  item!.click()
  await flushPromises()
}

async function enterName(name: string) {
  const input = document.querySelector<HTMLInputElement>('[data-testid="tree-name-input"]')!
  input.value = name
  input.dispatchEvent(new Event('input'))
  document.querySelector<HTMLButtonElement>('[data-testid="tree-name-submit"]')!.click()
  await flushPromises()
}

// spec navigation/navigation-tree "Navigation sidebar", "Create a folder", "Rename a folder",
// "Delete a folder", "Remove a catalog from the tree"
describe('NavigationTree', () => {
  beforeEach(() => {
    clearTree()
    sessionStorage.clear()
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
  })

  it('shows the folders of the root, which starts expanded', async () => {
    await tree(false)
    expect(names()).toEqual(['Repository', 'Compliance', 'IT Landscape'])
    await expand(LANDSCAPE_ID)
    expect(names()).toEqual(['Repository', 'Compliance', 'IT Landscape', 'Applications'])
    // the display label replaces the catalog name; a personal catalog is named as such
    expect(labels()).toEqual(['Application Inventory', 'My Candidates'])
    expect(document.querySelectorAll('[data-testid="tree-item-link"]')[1]?.textContent).toContain('(Personal catalog)')
  })

  it('keeps the expand state in the session', async () => {
    await tree(false)
    await expand(LANDSCAPE_ID)
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    clearTree()
    await tree(false)
    expect(labels()).toEqual(['Application Inventory', 'My Candidates'])
  })

  // "Open a catalog from the tree"
  it('opens a catalog from the tree without view state, so the default view applies', async () => {
    const { router } = await tree(false)
    await expand(LANDSCAPE_ID)
    document.querySelector<HTMLAnchorElement>('[data-testid="tree-item-link"]')!.click()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(`/catalogs/${CATALOGS[0]!.id}`)
    expect(router.currentRoute.value.query).toEqual({})
  })

  // "Reader without the right sees no actions"
  it('offers no actions to a reader without viewpoint:manage', async () => {
    await tree(false)
    await expand(COMPLIANCE_ID)
    expect(document.querySelector('[data-testid="tree-folder-actions"]')).toBeNull()
    expect(document.querySelector('[data-testid="tree-item-remove"]')).toBeNull()
  })

  // "New folder"
  it('creates a folder', async () => {
    const { recorded } = await tree(true)
    await choose(ROOT_ID, 'New folder')
    await enterName('Business Architecture')
    expect(recorded.at(-1)).toEqual({ path: '/api/v1/tree-nodes', method: 'POST', body: { name: 'Business Architecture', parentId: ROOT_ID } })
    expect(names()).toEqual(['Repository', 'Business Architecture', 'Compliance', 'IT Landscape'])
    expect(document.querySelector('[data-testid="tree-name-form"]')).toBeNull()
  })

  // "Duplicate name"
  it('says that a folder name is taken', async () => {
    await tree(true)
    await choose(ROOT_ID, 'New folder')
    await enterName('it landscape')
    expect(document.querySelector('[data-testid="tree-name-error"]')?.textContent).toBe('A folder with this name already exists.')
    expect(names()).toEqual(['Repository', 'Compliance', 'IT Landscape'])
  })

  // "Rename"
  it('renames a folder and keeps its entries', async () => {
    await tree(true)
    await choose(LANDSCAPE_ID, 'Rename')
    expect(document.querySelector<HTMLInputElement>('[data-testid="tree-name-input"]')!.value).toBe('IT Landscape')
    await enterName('Application Landscape')
    expect(names()).toEqual(['Repository', 'Application Landscape', 'Compliance'])
    await expand(LANDSCAPE_ID)
    expect(labels()).toContain('Application Inventory')
  })

  // "Delete with subfolders"
  it('asks before deleting and names the subfolders and entries', async () => {
    const { recorded } = await tree(true)
    await choose(LANDSCAPE_ID, 'Delete')
    expect(document.querySelector('[data-testid="tree-delete-hint"]')?.textContent?.trim()).toBe(
      'This deletes the folder with 1 subfolders and 2 entries. No catalog is deleted.',
    )
    expect(recorded.some((request) => request.method === 'DELETE')).toBe(false)
    document.querySelector<HTMLButtonElement>('[data-testid="tree-delete-confirm"]')!.click()
    await flushPromises()
    expect(recorded.at(-1)).toEqual({ path: `/api/v1/tree-nodes/${LANDSCAPE_ID}`, method: 'DELETE', body: undefined })
    expect(names()).toEqual(['Repository', 'Compliance'])
  })

  it('offers no deletion of the root', async () => {
    await tree(true)
    const trigger = folderRow(ROOT_ID).querySelector<HTMLButtonElement>('[data-testid="tree-folder-actions"]')!
    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    const items = [...document.querySelectorAll('[role="menuitem"]')].map((element) => element.textContent?.trim())
    expect(items).toEqual(['New folder', 'Rename'])
  })

  // "Remove one placement"
  it('removes an entry from the tree', async () => {
    const { recorded } = await tree(true)
    await expand(COMPLIANCE_ID)
    expect(labels()).toEqual(['Compliance Overview'])
    document.querySelector<HTMLButtonElement>('[data-testid="tree-item-remove"]')!.click()
    await flushPromises()
    expect(recorded.at(-1)?.method).toBe('DELETE')
    expect(recorded.at(-1)?.path).toMatch(new RegExp(`^/api/v1/tree-nodes/${COMPLIANCE_ID}/items/`))
    expect(labels()).toEqual([])
  })

  it('says when the tree cannot be loaded', async () => {
    await tree(false, { failing: true })
    expect(document.querySelector('[data-testid="tree-load-failed"]')?.textContent).toContain('The navigation tree could not be loaded.')
  })
})
