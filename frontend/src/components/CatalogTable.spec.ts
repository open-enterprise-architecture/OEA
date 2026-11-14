import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Me } from '../api/session'
import type { CatalogQueryResult } from '../api/catalogs'
import { session } from '../session/store'
import { ME_PORTAL_ONLY, ME_HISTORY_READER } from '../test/history'
import { testRouter } from '../test/router'
import CatalogTable from './CatalogTable.vue'

// spec repository/entity-history "Opening the history from the catalog" (design D5 of
// us-098-entity-history): every entity row of a catalog and of the portal catalog has the action
// "History" with an icon and a text, back to the catalog it was opened from.

const RESULT: CatalogQueryResult = {
  columns: [{ key: 'name', label: 'Name', sortable: true, filterType: 'text' }],
  rows: [
    { entityId: 101, values: { name: 'CRM System' } },
    { entityId: 102, values: { name: 'Billing' } },
  ],
  page: { page: 0, pageSize: 50, totalElements: 2, totalPages: 1 },
} as unknown as CatalogQueryResult

async function mountTable(me: unknown, path: string, readOnly = false) {
  session.me = me as Me
  const router = testRouter()
  await router.push(path)
  const wrapper = mount(CatalogTable, { props: { result: RESULT, readOnly }, global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('CatalogTable', () => {
  beforeEach(() => {
    session.me = undefined
  })

  it.each([
    ['/catalogs/c0000000-0000-4000-8000-000000000001?page=1', false],
    ['/portal/catalogs/c0000000-0000-4000-8000-000000000001', true],
  ])('gives each entity row of %s the action "History"', async (path, readOnly) => {
    const { wrapper } = await mountTable(ME_HISTORY_READER, path, readOnly)
    for (const id of [101, 102]) {
      const action = wrapper.get(`[data-testid="history-${id}"]`)
      expect(action.text()).toBe('History')
      expect(action.find('.iconify, svg, [class*="i-lucide-history"]').exists()).toBe(true)
      const href = new URL(action.attributes('href')!, 'http://localhost')
      expect(href.pathname).toBe(`/entities/${id}/history`)
      expect(href.searchParams.get('from')).toBe(path)
    }
  })

  it('opens the change history of the row', async () => {
    const { wrapper, router } = await mountTable(ME_HISTORY_READER, '/catalogs/c0000000-0000-4000-8000-000000000001')
    await wrapper.get('[data-testid="history-101"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/entities/101/history')
    expect(router.currentRoute.value.query.from).toBe('/catalogs/c0000000-0000-4000-8000-000000000001')
  })

  it('offers no history without entity:read', async () => {
    const { wrapper } = await mountTable(ME_PORTAL_ONLY, '/portal/catalogs/c0000000-0000-4000-8000-000000000001', true)
    expect(wrapper.find('[data-testid="history-101"]').exists()).toBe(false)
  })
})
