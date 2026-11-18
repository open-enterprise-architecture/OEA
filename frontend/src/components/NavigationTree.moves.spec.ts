import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { TreeNode } from '../api/navigation'
import { clearTree } from '../navigation/store'
import { ENTITIES, entityItem, folder, navigationApi, ROOT_ID, SOLUTIONS, solutionItem, type TreeRequest } from '../test/navigation'
import { testRouter } from '../test/router'
import NavigationTree from './NavigationTree.vue'

// specs navigation/navigation-tree "Reorganize in the sidebar", "Move entries and folders" and
// "Place an entity" (US-095, US-096; design D5 and D7 of us-095-navigation-tree-items)

const FINANCE = 'f1000000-0000-4000-8000-000000000001'
const INITIATIVES = 'f1000000-0000-4000-8000-000000000002'
const CLOUD = 'f1000000-0000-4000-8000-000000000003'
const EMPTY = 'f1000000-0000-4000-8000-000000000004'
const SAP = 'e2000000-0000-4000-8000-000000000001'
const WORKDAY = 'e2000000-0000-4000-8000-000000000002'
const SOLUTION = 'e2000000-0000-4000-8000-000000000003'
const LEGACY = 'e2000000-0000-4000-8000-000000000004'

/** Finance holds four entries; Initiatives holds Cloud Migration; Empty holds nothing. */
function movesTree(): TreeNode {
  return folder(ROOT_ID, 'Repository', null, [], [
    folder(FINANCE, 'Finance', ROOT_ID, [
      entityItem(SAP, ENTITIES[0]!),
      entityItem(WORKDAY, ENTITIES[1]!),
      solutionItem(SOLUTION, SOLUTIONS[0]!),
      entityItem(LEGACY, ENTITIES[2]!),
    ]),
    folder(INITIATIVES, 'Initiatives', ROOT_ID, [], [folder(CLOUD, 'Cloud Migration', INITIATIVES)]),
    folder(EMPTY, 'Empty', ROOT_ID),
  ])
}

const mounted: VueWrapper[] = []

async function tree(canManage: boolean, root: TreeNode = movesTree(), rejectMoves?: 404 | 422) {
  const recorded: TreeRequest[] = []
  const router = testRouter()
  await router.push('/')
  const wrapper = mount(NavigationTree, {
    props: { canManage, fetchFn: navigationApi({ tree: root, recorded, rejectMoves }) },
    global: { plugins: [ui, router] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  for (const id of [FINANCE, INITIATIVES]) {
    await expand(id)
  }
  return { wrapper, router, recorded }
}

const folderEl = (id: string) => document.querySelector<HTMLElement>(`[data-folder-id="${id}"]`)!
const folderRow = (id: string) => folderEl(id).querySelector<HTMLElement>('[data-testid="tree-folder-row"]')!
const itemEl = (id: string) => document.querySelector<HTMLElement>(`[data-item-id="${id}"]`)!
const itemRow = (id: string) => itemEl(id).querySelector<HTMLElement>('[data-testid="tree-item-row"]')!
const labels = (id: string) =>
  [...folderEl(id).querySelectorAll('[data-testid="tree-item"]')].map((element) => element.querySelector('[data-testid="tree-item-link"], [data-testid="tree-item-label"]')?.firstChild?.textContent?.trim())

async function expand(id: string) {
  if (folderEl(id).getAttribute('aria-expanded') === 'true') return
  folderEl(id).querySelector<HTMLButtonElement>('[data-testid="tree-toggle"]')!.click()
  await flushPromises()
}

/** A drag event; `dataTransfer` is a stand-in because jsdom has none. */
function dragEvent(type: string, text?: string) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  const data = new Map<string, string>(text === undefined ? [] : [['text/plain', text]])
  Object.defineProperty(event, 'dataTransfer', {
    value: { effectAllowed: '', dropEffect: '', setData: (kind: string, value: string) => data.set(kind, value), getData: (kind: string) => data.get(kind) ?? '' },
  })
  return event
}

async function drag(source: HTMLElement, target: HTMLElement) {
  source.dispatchEvent(dragEvent('dragstart'))
  target.dispatchEvent(dragEvent('dragover'))
  await flushPromises()
  const marker = target.getAttribute('data-drop')
  const drop = dragEvent('drop')
  target.dispatchEvent(drop)
  source.dispatchEvent(dragEvent('dragend'))
  await flushPromises()
  return { marker, accepted: drop.defaultPrevented }
}

/** Opens the menu of the trigger and returns the labels of its entries. */
async function openMenu(trigger: HTMLElement) {
  trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
  await flushPromises()
  return [...document.querySelectorAll<HTMLElement>('[role="menuitem"]')]
}

const itemMenu = (id: string) => openMenu(itemEl(id).querySelector<HTMLElement>('[data-testid="tree-item-actions"]')!)
const folderMenu = (id: string) => openMenu(folderRow(id).querySelector<HTMLElement>('[data-testid="tree-folder-actions"]')!)
const texts = (entries: HTMLElement[]) => entries.map((entry) => entry.textContent?.trim())

async function choose(entries: HTMLElement[], label: string) {
  const entry = entries.find((candidate) => candidate.textContent?.trim() === label)
  expect(entry, label).toBeDefined()
  entry!.click()
  await flushPromises()
}

const moves = (recorded: TreeRequest[]) => recorded.filter((request) => request.method === 'PUT')

describe('NavigationTree entries and moves', () => {
  beforeEach(() => {
    clearTree()
    sessionStorage.clear()
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
  })

  // "Place an entity", "Deleted entity"
  it('links an entity to its history and a solution to its page, and marks a deleted entity', async () => {
    const { wrapper } = await tree(false)
    const href = (id: string) => itemEl(id).querySelector('[data-testid="tree-item-link"]')!.getAttribute('href')
    expect(href(SAP)).toBe('/entities/9101/history')
    expect(href(SOLUTION)).toBe(`/solutions/${SOLUTIONS[0]!.id}`)
    const icons = wrapper.findAllComponents({ name: 'Icon' }).map((icon) => icon.props('name'))
    expect(icons).toContain('i-lucide-box')
    expect(icons).toContain('i-lucide-milestone')
    expect(itemEl(LEGACY).querySelector('[data-testid="tree-item-deleted"]')?.textContent).toContain('Deleted')
    expect(itemEl(SAP).querySelector('[data-testid="tree-item-deleted"]')).toBeNull()
    expect(labels(FINANCE)).toEqual(['SAP ERP', 'Workday', 'Cloud Migration', 'Legacy CRM'])
  })

  // D7 output encoding
  it('shows an entity name and a display label with markup literally', async () => {
    const hostile = '<img src=x onerror=alert(1)>'
    const root = movesTree()
    root.children[0]!.items.push(
      { ...entityItem('e2000000-0000-4000-8000-000000000009', { id: 109, name: hostile, deleted: false, connection: false }) },
      { ...solutionItem('e2000000-0000-4000-8000-000000000010', SOLUTIONS[1]!, '<b>bold</b>') },
    )
    await tree(true, root)
    expect(document.querySelector('img')).toBeNull()
    expect(document.querySelector('[data-folder-id="' + FINANCE + '"] b')).toBeNull()
    expect(labels(FINANCE).slice(-2)).toEqual([hostile, '<b>bold</b>'])
  })

  // "Drag and drop moves"
  it.each([
    ['onto an entry: before it', () => itemRow(SAP), 'item', { nodeId: FINANCE, position: 0 }, FINANCE],
    ['onto another entry: before it', () => itemRow(LEGACY), 'item', { nodeId: FINANCE, position: 2 }, FINANCE],
    ['onto a folder: at its end', () => folderRow(EMPTY), 'item', { nodeId: EMPTY, position: 0 }, EMPTY],
    ['onto a folder with entries: at its end', () => folderRow(FINANCE), 'item', undefined, FINANCE],
  ])('drops the entry Workday %s', async (_name, target, _kind, body, folderId) => {
    const { recorded } = await tree(true)
    const outcome = await drag(itemRow(WORKDAY), target())
    if (body === undefined) {
      // dropped on the folder it is in: the end of that folder
      expect(moves(recorded).at(-1)).toEqual({
        path: `/api/v1/tree-nodes/${FINANCE}/items/${WORKDAY}/position`,
        method: 'PUT',
        body: { nodeId: FINANCE, position: 3 },
      })
      expect(labels(FINANCE)).toEqual(['SAP ERP', 'Cloud Migration', 'Legacy CRM', 'Workday'])
    } else {
      expect(moves(recorded).at(-1)).toEqual({ path: `/api/v1/tree-nodes/${FINANCE}/items/${WORKDAY}/position`, method: 'PUT', body })
      expect(labels(folderId).length).toBeGreaterThan(0)
    }
    expect(outcome.marker).toBe('valid')
    expect(outcome.accepted).toBe(true)
  })

  it('drops a folder under another folder with its position', async () => {
    const { recorded } = await tree(true)
    await drag(folderRow(EMPTY), folderRow(INITIATIVES))
    expect(moves(recorded).at(-1)).toEqual({
      path: `/api/v1/tree-nodes/${EMPTY}/position`,
      method: 'PUT',
      body: { parentId: INITIATIVES, position: 1 },
    })
    expect([...folderEl(INITIATIVES).querySelectorAll('[data-testid="tree-folder-name"]')].map((el) => el.textContent?.trim())).toEqual([
      'Initiatives',
      'Cloud Migration',
      'Empty',
    ])
  })

  // "Invalid drop target"
  it.each([
    ['its own subfolder', () => folderRow(INITIATIVES), () => folderRow(CLOUD)],
    ['itself', () => folderRow(INITIATIVES), () => folderRow(INITIATIVES)],
    ['an entry (a folder moves into folders only)', () => folderRow(EMPTY), () => itemRow(SAP)],
    ['the same entry', () => itemRow(SAP), () => itemRow(SAP)],
  ])('refuses to drop the folder or entry onto %s', async (_name, source, target) => {
    const { recorded } = await tree(true)
    const outcome = await drag(source(), target())
    expect(outcome.marker).toBe('invalid')
    expect(outcome.accepted).toBe(false)
    expect(moves(recorded)).toEqual([])
  })

  it('marks an invalid target with text for screen readers while the drag is over it', async () => {
    await tree(true)
    folderRow(INITIATIVES).dispatchEvent(dragEvent('dragstart'))
    folderRow(CLOUD).dispatchEvent(dragEvent('dragover'))
    await flushPromises()
    expect(folderRow(CLOUD).querySelector('[data-testid="tree-drop-invalid"]')?.textContent).toBe('Cannot be dropped here')
    folderRow(CLOUD).dispatchEvent(dragEvent('dragleave'))
    await flushPromises()
    expect(folderRow(CLOUD).querySelector('[data-testid="tree-drop-invalid"]')).toBeNull()
  })

  it('refuses to drop an entry onto a folder that holds its target already', async () => {
    const root = movesTree()
    root.children[2]!.items.push(entityItem('e2000000-0000-4000-8000-000000000011', ENTITIES[1]!))
    const { recorded } = await tree(true, root)
    const outcome = await drag(itemRow(WORKDAY), folderRow(EMPTY))
    expect(outcome.marker).toBe('invalid')
    expect(moves(recorded)).toEqual([])
  })

  // D7: the dragged element is taken from the state of the tree
  it('sends no request for a drop whose source is not an element of the tree', async () => {
    const { recorded } = await tree(true)
    const drop = dragEvent('drop', `${WORKDAY}`)
    folderRow(EMPTY).dispatchEvent(dragEvent('dragover', WORKDAY))
    folderRow(EMPTY).dispatchEvent(drop)
    await flushPromises()
    expect(drop.defaultPrevented).toBe(false)
    expect(moves(recorded)).toEqual([])
    expect(labels(EMPTY)).toEqual([])
  })

  it('forgets the dragged element when the drag ends without a drop', async () => {
    const { recorded } = await tree(true)
    itemRow(WORKDAY).dispatchEvent(dragEvent('dragstart'))
    itemRow(WORKDAY).dispatchEvent(dragEvent('dragend'))
    folderRow(EMPTY).dispatchEvent(dragEvent('drop'))
    await flushPromises()
    expect(moves(recorded)).toEqual([])
  })

  // "Move with the keyboard"
  it.each([
    [SAP, ['Move down', 'Move to…']],
    [WORKDAY, ['Move up', 'Move down', 'Move to…']],
    [SOLUTION, ['Move up', 'Move down', 'Move to…']],
    [LEGACY, ['Move up', 'Move to…']],
  ])('offers the moves of the entry %s', async (id, expected) => {
    await tree(true)
    expect(texts(await itemMenu(id))).toEqual(expected)
  })

  it('moves an entry up and down', async () => {
    const { recorded } = await tree(true)
    await choose(await itemMenu(WORKDAY), 'Move up')
    expect(moves(recorded).at(-1)).toEqual({ path: `/api/v1/tree-nodes/${FINANCE}/items/${WORKDAY}/position`, method: 'PUT', body: { nodeId: FINANCE, position: 0 } })
    expect(labels(FINANCE)).toEqual(['Workday', 'SAP ERP', 'Cloud Migration', 'Legacy CRM'])
    await choose(await itemMenu(WORKDAY), 'Move down')
    expect(moves(recorded).at(-1)?.body).toEqual({ nodeId: FINANCE, position: 1 })
    expect(labels(FINANCE)).toEqual(['SAP ERP', 'Workday', 'Cloud Migration', 'Legacy CRM'])
  })

  it.each([
    [FINANCE, ['New folder', 'Rename', 'Move down', 'Move to…', 'Delete']],
    [INITIATIVES, ['New folder', 'Rename', 'Move up', 'Move down', 'Move to…', 'Delete']],
    [EMPTY, ['New folder', 'Rename', 'Move up', 'Move to…', 'Delete']],
  ])('offers the moves of the folder %s', async (id, expected) => {
    await tree(true)
    expect(texts(await folderMenu(id))).toEqual(expected)
  })

  it('moves a folder up', async () => {
    const { recorded } = await tree(true)
    await choose(await folderMenu(EMPTY), 'Move up')
    expect(moves(recorded).at(-1)).toEqual({ path: `/api/v1/tree-nodes/${EMPTY}/position`, method: 'PUT', body: { parentId: ROOT_ID, position: 1 } })
  })

  // "Move to…"
  const choices = async (wrapper: VueWrapper) => {
    const select = wrapper.findAllComponents({ name: 'Select' }).find((component) => component.vm.$attrs['data-testid'] === 'tree-move-folder')!
    return { select, labels: (select.props('items') as { label: string; value: string }[]).map((entry) => entry.label) }
  }

  it('offers in "Move to…" only folders the folder can move to', async () => {
    const { wrapper } = await tree(true)
    await choose(await folderMenu(INITIATIVES), 'Move to…')
    // not itself, not its subfolder, not the folder it is in
    expect((await choices(wrapper)).labels).toEqual(['Repository / Finance', 'Repository / Empty'])
  })

  it('offers in "Move to…" no folder that holds the entry\'s target or the folder it is in', async () => {
    const root = movesTree()
    root.children[2]!.items.push(entityItem('e2000000-0000-4000-8000-000000000011', ENTITIES[1]!))
    const { wrapper } = await tree(true, root)
    await choose(await itemMenu(WORKDAY), 'Move to…')
    expect((await choices(wrapper)).labels).toEqual(['Repository', 'Repository / Initiatives', 'Repository / Initiatives / Cloud Migration'])
  })

  it('moves an entry to the folder chosen in "Move to…" at its end', async () => {
    const { wrapper, recorded } = await tree(true)
    await choose(await itemMenu(SAP), 'Move to…')
    const { select } = await choices(wrapper)
    // the first folder is chosen already
    expect(select.props('modelValue')).toBe(ROOT_ID)
    select.vm.$emit('update:modelValue', EMPTY)
    await flushPromises()
    document.querySelector<HTMLButtonElement>('[data-testid="tree-move-submit"]')!.click()
    await flushPromises()
    expect(moves(recorded).at(-1)).toEqual({ path: `/api/v1/tree-nodes/${FINANCE}/items/${SAP}/position`, method: 'PUT', body: { nodeId: EMPTY, position: 0 } })
    expect(document.querySelector('[data-testid="tree-move-form"]')).toBeNull()
    expect(labels(EMPTY)).toEqual(['SAP ERP'])
  })

  // permissions
  it('offers a reader neither dragging nor moving', async () => {
    const { recorded } = await tree(false)
    expect(document.querySelector('[draggable="true"]')).toBeNull()
    expect(document.querySelector('[data-testid="tree-item-actions"]')).toBeNull()
    expect(document.querySelector('[data-testid="tree-folder-actions"]')).toBeNull()
    const outcome = await drag(itemRow(WORKDAY), folderRow(EMPTY))
    expect(outcome.accepted).toBe(false)
    expect(moves(recorded)).toEqual([])
  })

  it('makes folders other than the root and every entry draggable for a manager', async () => {
    await tree(true)
    expect(folderRow(ROOT_ID).getAttribute('draggable')).toBeNull()
    expect(folderRow(EMPTY).getAttribute('draggable')).toBe('true')
    expect(itemRow(SAP).getAttribute('draggable')).toBe('true')
  })

  // a move the server rejects
  it.each([
    [422, 'It cannot be moved there. The tree may have changed meanwhile.'],
    [404, 'It cannot be moved there. The tree may have changed meanwhile.'],
  ] as const)('says that a move was rejected with %i and keeps the tree', async (status, message) => {
    const { recorded } = await tree(true, movesTree(), status)
    await drag(itemRow(WORKDAY), folderRow(EMPTY))
    expect(moves(recorded)).toHaveLength(1)
    expect(document.querySelector('[data-testid="tree-change-error"]')?.textContent).toBe(status === 404 ? 'The navigation tree could not be changed.' : message)
    expect(labels(FINANCE)).toEqual(['SAP ERP', 'Workday', 'Cloud Migration', 'Legacy CRM'])
    // a 404 means the tree changed: it is read again
    expect(recorded.filter((request) => request.method === 'GET')).toHaveLength(status === 404 ? 2 : 1)
  })
})
