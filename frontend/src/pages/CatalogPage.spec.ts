import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { CATALOG } from '../test/catalogs'
import { routedFetch } from '../test/fetch'
import { testRouter } from '../test/router'
import CatalogPage from './CatalogPage.vue'

async function mountPage(id: string) {
  const fetchFn = routedFetch({
    [`/api/v1/catalogs/${CATALOG.id}`]: { body: CATALOG },
    '/api/v1/catalogs/foreign': {
      status: 404,
      contentType: 'application/problem+json',
      body: { type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 },
    },
  })
  const wrapper = mount(CatalogPage, { props: { id, fetchFn }, global: { plugins: [ui, testRouter()] } })
  await flushPromises()
  return wrapper
}

describe('CatalogPage', () => {
  it('shows name, entity type and scope with the placeholder of the table', async () => {
    const wrapper = await mountPage(CATALOG.id)
    expect(wrapper.get('[data-testid="catalog-title"]').text()).toBe('Application Inventory')
    expect(wrapper.get('[data-testid="catalog-type"]').text()).toBe('Application Component')
    expect(wrapper.get('[data-testid="catalog-scope"]').text()).toBe('Shared')
    expect(wrapper.find('[data-testid="catalog-table-placeholder"]').exists()).toBe(true)
  })

  it('shows "Catalog not found" for 404', async () => {
    const wrapper = await mountPage('foreign')
    expect(wrapper.get('[data-testid="catalog-not-found"]').text()).toContain('Catalog not found')
  })
})
