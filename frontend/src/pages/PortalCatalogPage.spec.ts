import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { defineComponent, h } from 'vue'
import { session } from '../session/store'
import { manyApplications, type Application } from '../test/catalogs'
import { INVENTORY_ID, portalApi } from '../test/portal'
import PortalCatalogPage from './PortalCatalogPage.vue'

const PATH = `/portal/catalogs/${INVENTORY_ID}`

async function open(
  location: string,
  applications?: Application[],
  recorded: string[] = [],
  rejectCursors = false,
  statusUnreadable = false,
): Promise<{ wrapper: VueWrapper; router: Router }> {
  const fetchFn = portalApi(applications, { recorded, rejectCursors, statusUnreadable })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/portal', component: { render: () => h('div', { 'data-testid': 'portal' }) } },
      { path: '/portal/catalogs/:id', component: PortalCatalogPage, props: (route) => ({ id: route.params.id, fetchFn }) },
    ],
  })
  await router.push(location)
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router }
}

function names(wrapper: VueWrapper): string[] {
  return wrapper.findAll('[data-testid="catalog-table"] tbody tr').map((row) => row.findAll('td')[0]!.text())
}

async function click(wrapper: VueWrapper, selector: string) {
  await wrapper.get(selector).trigger('click')
  await flushPromises()
}

// spec portal/portal-catalogs "Opening a shared catalog"
describe('PortalCatalogPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('shows the shared catalog with its columns and rows', async () => {
    const { wrapper } = await open(PATH)
    expect(wrapper.get('[data-testid="portal-catalog-name"]').text()).toBe('Application Inventory')
    expect(wrapper.text()).toContain('Application Component')
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(wrapper.find('[data-testid="sort-status"]').exists()).toBe(true)
  })

  it('offers no action that changes the catalog', async () => {
    const { wrapper } = await open(PATH)
    for (const action of ['configure-columns', 'configure-joins', 'place-in-navigation', 'save-view', 'filter-add']) {
      expect(wrapper.find(`[data-testid="${action}"]`).exists()).toBe(false)
    }
    expect(wrapper.find('[data-testid^="join-mode-"]').exists()).toBe(false)
  })

  it('sorts through the URL and back to the default order', async () => {
    const recorded: string[] = []
    const { wrapper, router } = await open(PATH, undefined, recorded)
    await click(wrapper, '[data-testid="sort-name"]')
    await click(wrapper, '[data-testid="sort-name"]')
    expect(names(wrapper)).toEqual(['Intranet', 'ERP', 'CRM System'])
    expect(router.currentRoute.value.query).toEqual({ sort: 'name:desc' })
    expect(recorded).toContain(`/api/v1/portal/catalogs/${INVENTORY_ID}/query?sort=name%3Adesc`)
    await click(wrapper, '[data-testid="sort-name"]')
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('moves to the next page', async () => {
    const { wrapper, router } = await open(PATH, manyApplications(120))
    expect(names(wrapper)).toHaveLength(50)
    const pageTwo = wrapper.get('[data-testid="catalog-pagination"]').findAll('button').find((button) => button.text() === '2')
    await pageTwo!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ page: '1' })
    expect(names(wrapper)[0]).toBe('Application 051')
  })

  // spec portal/portal-catalogs "Next page through the cursor" (s9-catalog-query-performance)

  async function toPage(wrapper: VueWrapper, label: string) {
    const button = wrapper.get('[data-testid="catalog-pagination"]').findAll('button').find((candidate) => candidate.text() === label)
    await button!.trigger('click')
    await flushPromises()
  }

  function queries(recorded: string[]) {
    return recorded.filter((path) => path.includes('/query')).map((path) => new URL(path, 'http://localhost').searchParams)
  }

  it('pages forward with the cursor and jumps with the page only', async () => {
    const recorded: string[] = []
    const { wrapper, router } = await open(PATH, manyApplications(160), recorded)
    await toPage(wrapper, '2')
    expect(queries(recorded).at(-1)!.get('cursor')).toBe('cursor-1')
    expect(queries(recorded).at(-1)!.get('page')).toBe('1')
    expect(router.currentRoute.value.query).toEqual({ page: '1' })
    expect(names(wrapper)[0]).toBe('Application 051')
    await toPage(wrapper, '4')
    expect(queries(recorded).at(-1)!.has('cursor')).toBe(false)
    expect(names(wrapper)[0]).toBe('Application 151')
  })

  it('falls back to the page when the cursor is rejected', async () => {
    const recorded: string[] = []
    const { wrapper } = await open(PATH, manyApplications(120), recorded, true)
    await toPage(wrapper, '2')
    const sent = queries(recorded)
    expect(sent.at(-2)!.get('cursor')).toBe('cursor-1')
    expect(sent.at(-1)!.has('cursor')).toBe(false)
    expect(sent.at(-1)!.get('page')).toBe('1')
    expect(names(wrapper)[0]).toBe('Application 051')
  })

  // portal/portal-catalogs "Restricted property" (us-126-role-restricted-enforcement)
  it('shows a column the reader may not see with a lock and without sort', async () => {
    const { wrapper } = await open(PATH, undefined, [], false, true)
    expect(wrapper.find('[data-testid="restricted-status"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sort-status"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="sort-name"]').exists()).toBe(true)
  })

  it('drops an invalid sort from the URL', async () => {
    const { wrapper, router } = await open(`${PATH}?sort=unknown:asc`)
    expect(router.currentRoute.value.query).toEqual({})
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
  })

  it('shows "Catalog not found" for a catalog that is not shared', async () => {
    const { wrapper } = await open('/portal/catalogs/c0000000-0000-4000-8000-000000000003')
    expect(wrapper.get('[data-testid="portal-catalog-not-found"]').text()).toContain('Catalog not found')
    expect(wrapper.find('[data-testid="catalog-table"]').exists()).toBe(false)
  })
})
