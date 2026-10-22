import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { APPLICATIONS, CATALOG, catalogApi, manyApplications, type Application, type CatalogRequest } from '../test/catalogs'
import CatalogPage from './CatalogPage.vue'

const CATALOG_PATH = `/catalogs/${CATALOG.id}`

interface Mounted {
  wrapper: VueWrapper
  router: Router
  recorded: CatalogRequest[]
}

/** The page behind a router, so that URL, back navigation and reload act as in the app. */
async function open(location: string, applications: Application[] = APPLICATIONS): Promise<Mounted> {
  const recorded: CatalogRequest[] = []
  const fetchFn = catalogApi(applications, { recorded })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/catalogs', component: { render: () => h('div', { 'data-testid': 'overview' }) } },
      { path: '/catalogs/:id', component: CatalogPage, props: (route) => ({ id: route.params.id, fetchFn }) },
    ],
  })
  await router.push(location)
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router, recorded }
}

function names(wrapper: VueWrapper): string[] {
  return wrapper.findAll('[data-testid="catalog-table"] tbody tr').map((row) => row.findAll('td')[0]!.text())
}

function header(wrapper: VueWrapper, key: string) {
  return wrapper.get(`[data-testid="sort-${key}"]`)
}

async function click(wrapper: VueWrapper, selector: string) {
  await wrapper.get(selector).trigger('click')
  await flushPromises()
}

describe('CatalogPage', () => {
  it('shows name, entity type and scope of the catalog', async () => {
    const { wrapper } = await open(CATALOG_PATH)
    expect(wrapper.get('[data-testid="catalog-title"]').text()).toBe('Application Inventory')
    expect(wrapper.get('[data-testid="catalog-type"]').text()).toBe('Application Component')
    expect(wrapper.get('[data-testid="catalog-scope"]').text()).toBe('Shared')
  })

  it('shows "Catalog not found" for 404', async () => {
    const { wrapper } = await open('/catalogs/foreign')
    expect(wrapper.get('[data-testid="catalog-not-found"]').text()).toContain('Catalog not found')
  })

  // Requirement: Rows of the primary metatype

  it('shows a catalog with three applications', async () => {
    const { wrapper } = await open(CATALOG_PATH)
    expect(wrapper.findAll('[data-testid="catalog-table"] thead th').map((th) => th.text())).toEqual(['Name', 'Status'])
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 1')
  })

  it('shows the hint of an empty repository', async () => {
    const { wrapper } = await open(CATALOG_PATH, [])
    expect(wrapper.find('[data-testid="catalog-table"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="catalog-empty"]').text()).toContain(
      'No entries found. Entities can be captured as a delta in a solution.',
    )
  })

  // Requirement: Sorting

  it('sorts ascending on a click on the header and shows ↑', async () => {
    const { wrapper, router } = await open(CATALOG_PATH)
    await click(wrapper, '[data-testid="sort-status"]')
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(header(wrapper, 'status').text()).toContain('↑')
    expect(wrapper.get('[data-testid="catalog-table"] thead th:nth-child(2)').attributes('aria-sort')).toBe('ascending')
    expect(router.currentRoute.value.query).toEqual({ sort: 'status:asc' })
  })

  it('switches to descending and back to the default order', async () => {
    const { wrapper, router } = await open(CATALOG_PATH)
    await click(wrapper, '[data-testid="sort-name"]')
    expect(header(wrapper, 'name').text()).toContain('↑')
    await click(wrapper, '[data-testid="sort-name"]')
    expect(names(wrapper)).toEqual(['Intranet', 'ERP', 'CRM System'])
    expect(header(wrapper, 'name').text()).toContain('↓')
    await click(wrapper, '[data-testid="sort-name"]')
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(header(wrapper, 'name').text()).not.toMatch(/[↑↓]/)
    expect(router.currentRoute.value.query).toEqual({})
  })

  // Requirement: Pagination

  it('shows the first of three pages and moves to page 2', async () => {
    const { wrapper } = await open(CATALOG_PATH, manyApplications(120))
    expect(names(wrapper)).toHaveLength(50)
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 3')
    const pageTwo = wrapper.get('[data-testid="catalog-pagination"]').findAll('button').find((button) => button.text() === '2')
    await pageTwo!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 2 of 3')
    expect(names(wrapper)[0]).toBe('Application 051')
  })

  it('resets to the first page when the sort changes', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?page=2`, manyApplications(120))
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 3 of 3')
    await click(wrapper, '[data-testid="sort-name"]')
    expect(router.currentRoute.value.query).toEqual({ sort: 'name:asc' })
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 3')
  })

  // Requirement: View state survives navigation (ADR-044)

  it('restores sort and page when navigating back to the catalog', async () => {
    const { wrapper, router } = await open('/catalogs', manyApplications(120))
    await router.push(CATALOG_PATH)
    await flushPromises()
    await click(wrapper, '[data-testid="sort-name"]')
    await click(wrapper, '[data-testid="sort-name"]')
    const pageTwo = wrapper.get('[data-testid="catalog-pagination"]').findAll('button').find((button) => button.text() === '2')
    await pageTwo!.trigger('click')
    await flushPromises()
    await router.push('/catalogs')
    await flushPromises()
    expect(wrapper.find('[data-testid="overview"]').exists()).toBe(true)

    router.back()
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ sort: 'name:desc', page: '1' })
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 2 of 3')
    expect(header(wrapper, 'name').text()).toContain('↓')
    expect(names(wrapper)[0]).toBe('Application 070')
  })

  it('adds no history entry per sort or page change', async () => {
    const { wrapper, router } = await open('/catalogs', manyApplications(120))
    await router.push(CATALOG_PATH)
    await flushPromises()
    await click(wrapper, '[data-testid="sort-name"]')
    await click(wrapper, '[data-testid="sort-status"]')
    router.back()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/catalogs')
  })

  it('keeps the sort after a reload', async () => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?sort=status:asc`)
    expect(recorded.at(-1)?.query).toEqual({ sort: 'status:asc' })
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(header(wrapper, 'status').text()).toContain('↑')
  })

  it.each([
    ['an unknown sort column', '?sort=owner:asc'],
    ['a malformed sort', '?sort=name'],
    ['an invalid page', '?page=abc'],
    ['a page beyond the last one', '?page=9'],
  ])('falls back to the default view for %s', async (_, query) => {
    const { wrapper, router } = await open(`${CATALOG_PATH}${query}`)
    expect(router.currentRoute.value.query).toEqual({})
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 1')
  })
})
