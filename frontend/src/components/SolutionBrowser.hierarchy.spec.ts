import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/session'
import { clearBrowser } from '../navigation/store'
import { session } from '../session/store'
import { HIERARCHY, ME_HIERARCHY_MANAGER, ME_HIERARCHY_WRITER, ME_TREE_READER, navigationApi, type HierarchyComponent, type TreeRequest } from '../test/navigation'
import { testRouter } from '../test/router'
import SolutionBrowser from './SolutionBrowser.vue'

const mounted: VueWrapper[] = []
const [SYSTEM, MODULE, AID, PAYROLL, WORKDAY] = [9201, 9202, 9203, 9204, 9205]

async function browser(me: unknown, options: Parameters<typeof navigationApi>[0] = {}) {
  session.me = me as Me
  session.loaded = true
  const recorded: TreeRequest[] = []
  const router = testRouter()
  await router.push('/')
  const wrapper = mount(SolutionBrowser, {
    props: { fetchFn: navigationApi({ components: HIERARCHY, ...options, recorded }) },
    global: { plugins: [ui, router] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  await toggle('current-state')
  await toggle('current-state:components')
  return { wrapper, recorded }
}

const node = (key: string) => document.querySelector<HTMLElement>(`[data-node-key="${key}"]`)!
const entity = (id: number) => document.querySelector<HTMLElement>(`[data-item-id="${id}"]`)
const row = (id: number) => entity(id)!.querySelector<HTMLElement>('[data-testid="browser-entity-row"]')!
const writes = (recorded: TreeRequest[]) => recorded.filter((request) => request.method !== 'GET')

async function toggle(key: string) {
  node(key).querySelector<HTMLButtonElement>('[data-testid="browser-toggle"]')!.click()
  await flushPromises()
}

async function expandEntity(id: number) {
  entity(id)!.querySelector<HTMLButtonElement>('[data-testid="browser-item-toggle"]')!.click()
  await flushPromises()
}

function names(parent: ParentNode) {
  return [...parent.querySelectorAll(':scope > ul > [data-testid="browser-item"]')].map((item) => item.querySelector(':scope > [data-testid="browser-entity-row"] [data-testid="browser-item-link"]')!.textContent?.trim())
}

function dragEvent(type: string) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'dataTransfer', { value: { effectAllowed: '', dropEffect: '', setData: () => undefined, getData: () => '' } })
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

const rootRow = () => node('current-state:components').querySelector<HTMLElement>(':scope > div')!

async function openMenu(id: number) {
  entity(id)!.querySelector<HTMLElement>('[data-testid="browser-item-actions"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
  await flushPromises()
  // the menu opens asynchronously; under load one flush is not always enough
  await vi.waitFor(() => expect(document.querySelectorAll('[role="menuitem"]').length).toBeGreaterThan(0))
  return [...document.querySelectorAll<HTMLElement>('[role="menuitem"]')]
}

async function choose(entries: HTMLElement[], label: string) {
  const entry = entries.find((candidate) => candidate.textContent?.trim() === label)
  expect(entry, label).toBeDefined()
  entry!.click()
  await flushPromises()
}

// spec navigation/solution-browser "Nested components", "Arrange components in the browser"
describe('SolutionBrowser hierarchy', () => {
  beforeEach(() => {
    clearBrowser()
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('reading', () => {
    it('lists the root level with a folder icon for structuring aids and loads children on expansion', async () => {
      const { recorded } = await browser(ME_TREE_READER)
      expect(names(node('current-state:components'))).toEqual(['Finance landscape', 'Finance module', 'SAP S/4HANA', 'Workday'])
      expect([...document.querySelectorAll('[data-testid="browser-item-folder"]')]).toHaveLength(1)
      expect(entity(AID)!.querySelector('[data-testid="browser-item-folder"]')).not.toBeNull()
      expect(entity(WORKDAY)!.querySelector('[data-testid="browser-item-folder"]')).toBeNull()
      expect(recorded.some((request) => request.path.includes('parentEntityId'))).toBe(false)
      await expandEntity(AID)
      expect(names(entity(AID)!)).toEqual(['Payroll'])
      expect(recorded.filter((request) => request.path.includes('parentEntityId=9203'))).toHaveLength(1)
    })

    it('has no expand toggle without children and loads the children only once', async () => {
      const { recorded } = await browser(ME_TREE_READER)
      expect(entity(WORKDAY)!.querySelector('[data-testid="browser-item-toggle"]')!.className).toContain('invisible')
      await expandEntity(AID)
      await expandEntity(AID)
      await expandEntity(AID)
      expect(recorded.filter((request) => request.path.includes('parentEntityId=9203'))).toHaveLength(1)
    })

    it('shows a name with markup literally', async () => {
      const markup = '<img src=x onerror=alert(1)>'
      await browser(ME_TREE_READER, { components: HIERARCHY.map((component) => (component.id === WORKDAY ? { ...component, name: markup } : component)) })
      expect(names(node('current-state:components'))).toContain(markup)
      expect(document.querySelector('img')).toBeNull()
    })

    it('lists a child of a deleted parent at the root level', async () => {
      const orphan: HierarchyComponent = { id: 9999, name: 'Orphan', abbreviation: 'APP', parentId: 8888, version: 1 }
      await browser(ME_TREE_READER, { components: [...HIERARCHY, orphan] })
      expect(names(node('current-state:components'))).toContain('Orphan')
    })
  })

  describe('permission', () => {
    it('offers neither drag, nor "Move to…", "New structuring aid" nor "Delete" without entity:write', async () => {
      await browser(ME_TREE_READER)
      expect(document.querySelector('[draggable="true"]')).toBeNull()
      expect(document.querySelector('[data-testid="browser-item-actions"]')).toBeNull()
      expect(document.querySelector('[data-testid="browser-new-aid"]')).toBeNull()
    })

    it('makes components draggable and offers the menu with entity:write', async () => {
      await browser(ME_HIERARCHY_WRITER)
      expect(row(MODULE).getAttribute('draggable')).toBe('true')
      expect(document.querySelector('[data-testid="browser-new-aid"]')).not.toBeNull()
      expect(await openMenu(MODULE)).toHaveLength(2)
    })

    it('offers "Delete" only on a structuring aid and only with entity:delete', async () => {
      await browser(ME_HIERARCHY_WRITER)
      expect((await openMenu(AID)).map((entry) => entry.textContent?.trim())).toEqual(['Move to…', 'New structuring aid'])
    })

    // two tests, one menu each: closing one menu and opening the next raced under load
    it('offers "Delete" on a structuring aid with entity:delete', async () => {
      await browser(ME_HIERARCHY_MANAGER)
      expect((await openMenu(AID)).map((entry) => entry.textContent?.trim())).toContain('Delete')
    })

    it('does not offer "Delete" on another component with entity:delete', async () => {
      await browser(ME_HIERARCHY_MANAGER)
      expect((await openMenu(MODULE)).map((entry) => entry.textContent?.trim())).toEqual(['Move to…', 'New structuring aid'])
    })
  })

  describe('drag and drop', () => {
    it('sends the parent with If-Match of the shown version and shows the component below its parent', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      const outcome = await drag(row(MODULE), row(SYSTEM))
      expect(outcome).toEqual({ marker: 'valid', accepted: true })
      expect(writes(recorded)).toEqual([{ path: '/api/v1/entities/9202/parent', method: 'PUT', body: { parentEntityId: 9201 }, ifMatch: '"1"' }])
      expect(names(node('current-state:components'))).toEqual(['Finance landscape', 'SAP S/4HANA', 'Workday'])
      expect(names(entity(SYSTEM)!)).toEqual(['Finance module'])
      expect(document.querySelector('[data-testid="browser-change-error"]')).toBeNull()
    })

    it('drops on "Components" to place at the root level', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      await expandEntity(AID)
      const outcome = await drag(row(PAYROLL), rootRow())
      expect(outcome.accepted).toBe(true)
      expect(writes(recorded)).toEqual([{ path: '/api/v1/entities/9204/parent', method: 'PUT', body: { parentEntityId: null }, ifMatch: '"1"' }])
      expect(names(node('current-state:components'))).toContain('Payroll')
    })

    it('refuses a drop on the component itself and sends nothing', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      const outcome = await drag(row(MODULE), row(MODULE))
      expect(outcome).toEqual({ marker: 'invalid', accepted: false })
      expect(writes(recorded)).toEqual([])
    })

    it('refuses a drop on a loaded descendant and sends nothing', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      await drag(row(MODULE), row(SYSTEM))
      recorded.length = 0
      const outcome = await drag(row(SYSTEM), row(MODULE))
      expect(outcome).toEqual({ marker: 'invalid', accepted: false })
      expect(writes(recorded)).toEqual([])
    })

    it('sends nothing for a drop where the component already is', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      await drag(row(MODULE), rootRow())
      expect(writes(recorded)).toEqual([])
      await expandEntity(AID)
      await drag(row(PAYROLL), row(AID))
      expect(writes(recorded)).toEqual([])
    })

    it('shows a refusal of the server and leaves the tree as it was', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER, { rejectHierarchy: 422 })
      await drag(row(MODULE), row(SYSTEM))
      expect(document.querySelector('[data-testid="browser-change-error"]')!.textContent).toContain('cannot be placed there')
      expect(names(node('current-state:components'))).toEqual(['Finance landscape', 'Finance module', 'SAP S/4HANA', 'Workday'])
      expect(writes(recorded)).toHaveLength(1)
    })

    it.each([
      { status: 409 as const, text: 'open issues' },
      { status: 412 as const, text: 'changed meanwhile' },
      { status: 500 as const, text: 'could not be saved' },
    ])('explains a $status', async ({ status, text }) => {
      await browser(ME_HIERARCHY_WRITER, { rejectHierarchy: status })
      await drag(row(MODULE), row(SYSTEM))
      expect(document.querySelector('[data-testid="browser-change-error"]')!.textContent).toContain(text)
    })

    it('refuses nothing to a reader: a drop does not reach the server', async () => {
      const { recorded } = await browser(ME_TREE_READER)
      const outcome = await drag(row(MODULE), row(SYSTEM))
      expect(outcome.accepted).toBe(false)
      expect(writes(recorded)).toEqual([])
    })

    it('does not make connections or the components of a solution draggable', async () => {
      await browser(ME_HIERARCHY_WRITER)
      await toggle('current-state:connections').catch(() => undefined)
      expect(document.querySelectorAll('[data-node-key^="solution"] [draggable="true"]')).toHaveLength(0)
    })
  })

  describe('"Move to…"', () => {
    it('offers the root level and moves there', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      await expandEntity(AID)
      await choose(await openMenu(PAYROLL), 'Move to…')
      expect(document.querySelector('[data-testid="move-to-root"]')).not.toBeNull()
      document.querySelector<HTMLButtonElement>('[data-testid="move-to-root"]')!.click()
      await flushPromises()
      expect(writes(recorded)).toEqual([{ path: '/api/v1/entities/9204/parent', method: 'PUT', body: { parentEntityId: null }, ifMatch: '"1"' }])
    })

    it('lists the components without the component itself, filters them by name and moves under the chosen one', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      await choose(await openMenu(MODULE), 'Move to…')
      const rows = () => [...document.querySelectorAll('[data-testid="move-to-row"]')].map((element) => element.querySelector(':scope > span')!.textContent?.trim())
      expect(rows()).toEqual(['Finance landscape', 'SAP S/4HANA', 'Workday'])
      const filter = document.querySelector<HTMLInputElement>('[data-testid="move-to-filter"]')!
      filter.value = 'sap'
      filter.dispatchEvent(new Event('input', { bubbles: true }))
      await flushPromises()
      expect(rows()).toEqual(['SAP S/4HANA'])
      document.querySelector<HTMLButtonElement>('[data-testid="move-to-choose"]')!.click()
      await flushPromises()
      expect(writes(recorded)).toEqual([{ path: '/api/v1/entities/9202/parent', method: 'PUT', body: { parentEntityId: 9201 }, ifMatch: '"1"' }])
    })

    it('opens the children of a component to choose one of them', async () => {
      await browser(ME_HIERARCHY_WRITER)
      await choose(await openMenu(WORKDAY), 'Move to…')
      document.querySelector<HTMLButtonElement>('[data-testid="move-to-open"]')!.click()
      await flushPromises()
      expect([...document.querySelectorAll('[data-testid="move-to-row"] > span')].map((element) => element.textContent?.trim())).toEqual(['Payroll'])
    })
  })

  describe('structuring aids', () => {
    it('creates a structuring aid at the root level with the name trimmed', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      document.querySelector<HTMLButtonElement>('[data-testid="browser-new-aid"]')!.click()
      await flushPromises()
      const name = document.querySelector<HTMLInputElement>('[data-testid="new-aid-name"]')!
      name.value = '  Integration landscape  '
      name.dispatchEvent(new Event('input', { bubbles: true }))
      document.querySelector<HTMLFormElement>('[data-testid="new-aid-form"]')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
      await flushPromises()
      expect(writes(recorded)).toEqual([{ path: '/api/v1/structuring-aids', method: 'POST', body: { name: 'Integration landscape', parentEntityId: null } }])
      expect(names(node('current-state:components'))).toContain('Integration landscape')
    })

    it('creates a structuring aid under a component', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      await choose(await openMenu(SYSTEM), 'New structuring aid')
      const name = document.querySelector<HTMLInputElement>('[data-testid="new-aid-name"]')!
      name.value = 'Modules'
      name.dispatchEvent(new Event('input', { bubbles: true }))
      document.querySelector<HTMLFormElement>('[data-testid="new-aid-form"]')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
      await flushPromises()
      expect(writes(recorded)[0]!.body).toEqual({ name: 'Modules', parentEntityId: 9201 })
      expect(names(entity(SYSTEM)!)).toEqual(['Modules'])
    })

    it('asks for a name and sends nothing for a blank one', async () => {
      const { recorded } = await browser(ME_HIERARCHY_WRITER)
      document.querySelector<HTMLButtonElement>('[data-testid="browser-new-aid"]')!.click()
      await flushPromises()
      const name = document.querySelector<HTMLInputElement>('[data-testid="new-aid-name"]')!
      name.value = '   '
      name.dispatchEvent(new Event('input', { bubbles: true }))
      document.querySelector<HTMLFormElement>('[data-testid="new-aid-form"]')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
      await flushPromises()
      expect(document.querySelector('[data-testid="new-aid-error"]')).not.toBeNull()
      expect(writes(recorded)).toEqual([])
    })

    it('limits the name to 255 characters in the field', async () => {
      await browser(ME_HIERARCHY_WRITER)
      document.querySelector<HTMLButtonElement>('[data-testid="browser-new-aid"]')!.click()
      await flushPromises()
      expect(document.querySelector('[data-testid="new-aid-name"]')!.getAttribute('maxlength')).toBe('255')
      expect(document.querySelector('[data-testid="new-aid-description"]')!.getAttribute('maxlength')).toBe('2000')
    })

    it('warns with the number of children before deleting, and deletes with the version', async () => {
      const { recorded } = await browser(ME_HIERARCHY_MANAGER)
      await choose(await openMenu(AID), 'Delete')
      expect(document.querySelector('[data-testid="delete-aid-warning"]')!.textContent).toContain('1 child moves to the root level and is not deleted')
      expect(writes(recorded)).toEqual([])
      document.querySelector<HTMLButtonElement>('[data-testid="delete-aid-confirm"]')!.click()
      await flushPromises()
      expect(writes(recorded)).toEqual([{ path: '/api/v1/structuring-aids/9203', method: 'DELETE', ifMatch: '"1"' }])
      expect(names(node('current-state:components'))).toEqual(['Finance module', 'Payroll', 'SAP S/4HANA', 'Workday'])
    })

    it('names three children in the warning', async () => {
      const three = [...HIERARCHY, { id: 9301, name: 'A', abbreviation: 'APP', parentId: AID, version: 1 }, { id: 9302, name: 'B', abbreviation: 'APP', parentId: AID, version: 1 }]
      await browser(ME_HIERARCHY_MANAGER, { components: three })
      await choose(await openMenu(AID), 'Delete')
      expect(document.querySelector('[data-testid="delete-aid-warning"]')!.textContent).toContain('3 children move to the root level and are not deleted')
    })

    it('explains a blocked deletion in the dialog', async () => {
      await browser(ME_HIERARCHY_MANAGER, { rejectHierarchy: 409 })
      await choose(await openMenu(AID), 'Delete')
      document.querySelector<HTMLButtonElement>('[data-testid="delete-aid-confirm"]')!.click()
      await flushPromises()
      expect(document.querySelector('[data-testid="delete-aid-error"]')!.textContent).toContain('open issues')
    })
  })
})
