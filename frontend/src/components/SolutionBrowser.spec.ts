import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Browser, BrowserItem } from '../api/navigation'
import { clearBrowser } from '../navigation/store'
import {
  BROWSER,
  BROWSER_ITEMS,
  BROWSER_SOLUTION_ID,
  BROWSER_WITHOUT_PLATEAUS,
  ENTITIES,
  navigationApi,
  PLATEAU_2027_ID,
  PLATEAU_2030_ID,
  SOLUTIONS,
  type TreeRequest,
} from '../test/navigation'
import { testRouter } from '../test/router'
import SolutionBrowser from './SolutionBrowser.vue'

const mounted: VueWrapper[] = []

async function browser(options: Parameters<typeof navigationApi>[0] = {}) {
  const recorded: TreeRequest[] = options.recorded ?? []
  const router = testRouter()
  await router.push('/')
  const wrapper = mount(SolutionBrowser, {
    props: { fetchFn: navigationApi({ ...options, recorded }) },
    global: { plugins: [ui, router] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router, recorded }
}

function row(key: string) {
  return document.querySelector<HTMLElement>(`[data-node-key="${key}"]`)
}

async function expand(key: string) {
  row(key)!.querySelector<HTMLButtonElement>('[data-testid="browser-toggle"]')!.click()
  await flushPromises()
}

function topLevel() {
  return [...document.querySelectorAll('[role="tree"] > [data-testid="browser-node"] > div [data-testid="browser-label"]')].map((element) => element.textContent?.replace(/\s+/g, ' ').trim())
}

function itemsOf(key: string) {
  return [...row(key)!.querySelectorAll('[data-testid="browser-item"]')].map((element) => element.querySelector('[data-testid="browser-item-link"]')!.textContent?.trim())
}

function itemRequests(recorded: TreeRequest[]) {
  return recorded.filter((request) => request.path.startsWith('/api/v1/views/browser/items'))
}

function manyEntities(count: number): BrowserItem[] {
  return Array.from({ length: count }, (_, index) => ({
    kind: 'entity' as const,
    id: String(100 + index),
    name: `Entity ${String(index).padStart(3, '0')}`,
    metatypeAbbreviation: 'APP',
    entityId: null,
    solutionId: null,
    deltaType: null,
    childCount: 0,
    structuringAid: false,
    version: 1,
  }))
}

// spec navigation/solution-browser
describe('SolutionBrowser', () => {
  beforeEach(() => {
    clearBrowser()
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
  })

  describe('top level', () => {
    it('shows the current state first, then the plateaus, then the projects', async () => {
      await browser()
      expect(topLevel()).toEqual(['Current state', '2027 (1)', 'TO-BE 2030 (0)', 'Projects (1)'])
    })

    it('shows only the current state and the projects without plateau mode', async () => {
      await browser({ browser: BROWSER_WITHOUT_PLATEAUS })
      expect(topLevel()).toEqual(['Current state', 'Projects (0)'])
    })

    it('greys out an empty plateau and an empty group, not hidden', async () => {
      await browser({ browser: BROWSER_WITHOUT_PLATEAUS })
      const projects = row('projects')!
      expect(projects.querySelector('[data-testid="browser-label"]')!.getAttribute('data-empty')).toBe('true')
      expect(projects.querySelector('[data-testid="browser-label"]')!.className).toContain('text-muted')
      expect(projects.querySelector('[data-testid="browser-toggle"]')!.className).toContain('invisible')
    })

    it('greys out an empty plateau', async () => {
      await browser()
      expect(row(`plateau:${PLATEAU_2030_ID}`)!.querySelector('[data-testid="browser-label"]')!.getAttribute('data-empty')).toBe('true')
      expect(row(`plateau:${PLATEAU_2027_ID}`)!.querySelector('[data-testid="browser-label"]')!.getAttribute('data-empty')).toBeNull()
    })

    it('lists plateau solutions under their plateau and project solutions under the projects', async () => {
      await browser()
      await expand(`plateau:${PLATEAU_2027_ID}`)
      await expand('projects')
      const under2027 = [...row(`plateau:${PLATEAU_2027_ID}`)!.querySelectorAll('[data-testid="browser-node"]')].map((node) => node.getAttribute('data-node-key'))
      expect(under2027).toContain(`solution:${SOLUTIONS[0]!.id}`)
      expect(row('projects')!.querySelector(`[data-node-key="solution:${SOLUTIONS[1]!.id}"]`)).not.toBeNull()
    })

    it('shows a retry when the browser cannot be loaded', async () => {
      await browser({ failingBrowser: true })
      expect(document.querySelector('[data-testid="browser-load-failed"]')).not.toBeNull()
      expect(document.querySelector('[role="tree"]')).toBeNull()
    })
  })

  describe('categories', () => {
    it('shows the counts of the current state and greys out an empty category', async () => {
      await browser({ browser: { ...BROWSER, currentState: { components: 4, connections: 2, catalogs: 0 } } })
      await expand('current-state')
      const labels = ['components', 'connections', 'catalogs'].map((name) => row(`current-state:${name}`)!.querySelector('[data-testid="browser-label"]')!)
      expect(labels.map((label) => label.textContent?.replace(/\s+/g, ' ').trim())).toEqual(['Components (4)', 'Connections (2)', 'Catalogs (0)'])
      expect(labels.map((label) => label.getAttribute('data-empty'))).toEqual([null, null, 'true'])
    })

    it('has the category Components only under a solution, with its number of deltas', async () => {
      await browser()
      await expand('projects')
      await expand(`solution:${SOLUTIONS[1]!.id}`)
      expect([...row(`solution:${SOLUTIONS[1]!.id}`)!.querySelectorAll('[data-testid="browser-label"]')].map((label) => label.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
        'ERP Consolidation',
        'Components (0)',
      ])
    })

    it('loads no category before it is expanded and each only once', async () => {
      const { recorded } = await browser()
      await expand('current-state')
      expect(itemRequests(recorded)).toHaveLength(0)
      await expand('current-state:components')
      expect(itemRequests(recorded)).toHaveLength(1)
      await expand('current-state:components')
      await expand('current-state:components')
      expect(itemRequests(recorded)).toHaveLength(1)
      expect(itemRequests(recorded)[0]!.path).toContain('scope=current-state&category=components&page=0&pageSize=50')
    })

    it('does not load a category with 0 objects', async () => {
      const { recorded } = await browser()
      await expand('projects')
      await expand(`solution:${SOLUTIONS[1]!.id}`)
      await expand(`solution:${SOLUTIONS[1]!.id}:components`)
      expect(itemRequests(recorded)).toHaveLength(0)
    })

    it('asks for the solution of a solution category', async () => {
      const { recorded } = await browser()
      await expand(`plateau:${PLATEAU_2027_ID}`)
      await expand(`solution:${BROWSER_SOLUTION_ID}`)
      await expand(`solution:${BROWSER_SOLUTION_ID}:components`)
      expect(itemRequests(recorded)[0]!.path).toContain(`scope=solution&category=components&page=0&pageSize=50&solutionId=${BROWSER_SOLUTION_ID}`)
    })
  })

  describe('contents', () => {
    it('links entities to their history, with the metatype abbreviation where there is one', async () => {
      await browser()
      await expand('current-state')
      await expand('current-state:components')
      const links = [...row('current-state:components')!.querySelectorAll<HTMLAnchorElement>('[data-testid="browser-item-link"]')]
      expect(links.map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual([
        ['SAP ERP', '/entities/9101/history'],
        ['Workday', '/entities/9102/history'],
        ['Zendesk', '/entities/9105/history'],
      ])
      expect([...row('current-state:components')!.querySelectorAll('[data-testid="browser-item"]')].map((item) => item.querySelector('[data-testid="browser-item-abbreviation"]')?.textContent?.trim() ?? null)).toEqual(['APP', 'APP', null])
    })

    it('links a catalog to its page', async () => {
      await browser()
      await expand('current-state')
      await expand('current-state:catalogs')
      const links = [...row('current-state:catalogs')!.querySelectorAll<HTMLAnchorElement>('[data-testid="browser-item-link"]')]
      expect(links.map((link) => link.getAttribute('href'))).toEqual(['/catalogs/c0000000-0000-4000-8000-000000000001', '/catalogs/c0000000-0000-4000-8000-000000000002'])
    })

    it('links a delta to its solution and a solution to its page, with a badge for each delta type', async () => {
      await browser()
      await expand(`plateau:${PLATEAU_2027_ID}`)
      await expand(`solution:${BROWSER_SOLUTION_ID}`)
      await expand(`solution:${BROWSER_SOLUTION_ID}:components`)
      const category = row(`solution:${BROWSER_SOLUTION_ID}:components`)!
      expect(itemsOf(`solution:${BROWSER_SOLUTION_ID}:components`)).toEqual(['CRM Cloud', 'Legacy CRM', 'SAP ERP'])
      expect([...category.querySelectorAll('[data-testid="browser-item-link"]')].map((link) => link.getAttribute('href'))).toEqual(Array(3).fill(`/solutions/${BROWSER_SOLUTION_ID}`))
      expect([...category.querySelectorAll('[data-testid="browser-item-delta"]')].map((badge) => [badge.getAttribute('data-delta-type'), badge.textContent?.trim()])).toEqual([
        ['new', 'New'],
        ['retiring', 'Retiring'],
        ['modified', 'Modified'],
      ])
      expect(row(`solution:${BROWSER_SOLUTION_ID}`)!.querySelector('[data-testid="browser-solution-link"]')!.getAttribute('href')).toBe(`/solutions/${BROWSER_SOLUTION_ID}`)
    })

    it('shows a name with markup literally', async () => {
      const markup = '<img src=x onerror=alert(1)>'
      const solutions = { ...BROWSER, projects: [{ id: SOLUTIONS[1]!.id, name: markup, status: 'draft', components: 1 }] } as Browser
      await browser({
        browser: solutions,
        browserItems: { ...BROWSER_ITEMS, [`solution:${SOLUTIONS[1]!.id}:components`]: [{ ...BROWSER_ITEMS[`solution:${BROWSER_SOLUTION_ID}:components`]![0]!, name: markup }] },
      })
      await expand('projects')
      await expand(`solution:${SOLUTIONS[1]!.id}`)
      await expand(`solution:${SOLUTIONS[1]!.id}:components`)
      expect(row(`solution:${SOLUTIONS[1]!.id}`)!.querySelector('[data-testid="browser-label"]')!.textContent).toContain(markup)
      expect(itemsOf(`solution:${SOLUTIONS[1]!.id}:components`)).toEqual([markup])
      expect(document.querySelector('img')).toBeNull()
    })

    it('shows an error with a retry when a category cannot be loaded', async () => {
      const failing = navigationApi()
      let failItems = true
      const fetchFn: typeof fetch = async (input, init) => {
        if (failItems && String(input).includes('/views/browser/items')) {
          return new Response('{}', { status: 500, headers: { 'Content-Type': 'application/problem+json' } })
        }
        return failing(input, init)
      }
      const router = testRouter()
      await router.push('/')
      mounted.push(mount(SolutionBrowser, { props: { fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body }))
      await flushPromises()
      await expand('current-state')
      await expand('current-state:components')
      expect(document.querySelector('[data-testid="browser-category-failed"]')).not.toBeNull()
      failItems = false
      document.querySelector<HTMLButtonElement>('[data-testid="browser-category-failed"] button')!.click()
      await flushPromises()
      expect(document.querySelector('[data-testid="browser-category-failed"]')).toBeNull()
      expect(itemsOf('current-state:components')).toHaveLength(3)
    })
  })

  describe('paging', () => {
    // total 51 pages as 50 + 1; 50 and 49 test the boundary below the page size
    it.each([
      { total: 51, loaded: 50, more: true },
      { total: 50, loaded: 50, more: false },
      { total: 49, loaded: 49, more: false },
      { total: 1, loaded: 1, more: false },
    ])('offers "Load more" only while fewer than the total are loaded (total $total)', async ({ total, loaded, more }) => {
      const items = manyEntities(total)
      await browser({
        browser: { ...BROWSER, currentState: { components: total, connections: 0, catalogs: 0 } },
        browserItems: { 'current-state:components': items },
      })
      await expand('current-state')
      await expand('current-state:components')
      expect(itemsOf('current-state:components')).toHaveLength(loaded)
      expect(row('current-state:components')!.querySelector('[data-testid="browser-load-more"]') !== null).toBe(more)
    })

    it('loads the second page and then no more', async () => {
      const { recorded } = await browser({
        browser: { ...BROWSER, currentState: { components: 51, connections: 0, catalogs: 0 } },
        browserItems: { 'current-state:components': manyEntities(51) },
      })
      await expand('current-state')
      await expand('current-state:components')
      row('current-state:components')!.querySelector<HTMLButtonElement>('[data-testid="browser-load-more"]')!.click()
      await flushPromises()
      expect(itemsOf('current-state:components')).toHaveLength(51)
      expect(itemsOf('current-state:components')[50]).toBe('Entity 050')
      expect(row('current-state:components')!.querySelector('[data-testid="browser-load-more"]')).toBeNull()
      expect(itemRequests(recorded).map((request) => /page=(\d+)/.exec(request.path)![1])).toEqual(['0', '1'])
    })

    it('keeps the loaded objects when the node is collapsed and expanded again', async () => {
      const { recorded } = await browser()
      await expand('current-state')
      await expand('current-state:components')
      await expand('current-state:components')
      await expand('current-state:components')
      expect(itemsOf('current-state:components')).toEqual([ENTITIES[0]!.name, ENTITIES[1]!.name, 'Zendesk'])
      expect(itemRequests(recorded)).toHaveLength(1)
    })
  })
})
