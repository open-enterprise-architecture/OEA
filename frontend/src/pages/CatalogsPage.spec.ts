import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import CreateCatalogForm from '../components/CreateCatalogForm.vue'
import { session } from '../session/store'
import { CATALOG, CATALOGS, METATYPES, ME_ARCHITECT, ME_VIEWER, ME_WITHOUT_ROLE } from '../test/catalogs'
import { routedFetch } from '../test/fetch'
import { testRouter } from '../test/router'
import CatalogsPage from './CatalogsPage.vue'

async function mountPage(me: unknown, catalogs: unknown[] = CATALOGS) {
  const router = testRouter()
  await router.push('/catalogs')
  const fetchFn = routedFetch({
    '/api/v1/me': { body: me },
    '/api/v1/catalogs': { body: catalogs },
    '/api/v1/metamodel/metatypes': { body: METATYPES },
  })
  const wrapper = mount(CatalogsPage, { props: { fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

describe('CatalogsPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('lists the visible catalogs with their entity type and scope', async () => {
    const { wrapper } = await mountPage(ME_ARCHITECT)
    const items = wrapper.findAll('[data-testid="catalog-item"]')
    expect(items.map((item) => item.text())).toEqual([
      expect.stringContaining('Application Inventory'),
      expect.stringContaining('Compliance Overview'),
      expect.stringContaining('My Integration Candidates'),
    ])
    expect(items[2]!.text()).toContain('Personal')
    expect(wrapper.find('[data-testid="new-catalog"]').exists()).toBe(true)
  })

  // spec platform/request-limits "Principal above the limit"
  it('shows the waiting time when the request limit is reached', async () => {
    const router = testRouter()
    await router.push('/catalogs')
    const fetchFn = routedFetch({
      '/api/v1/me': { body: ME_VIEWER },
      '/api/v1/catalogs': {
        status: 429,
        body: { type: 'urn:oea:problem:too-many-requests', title: 'Too many requests', status: 429 },
        contentType: 'application/problem+json',
        headers: { 'Retry-After': '42' },
      },
    })
    const wrapper = mount(CatalogsPage, { props: { fetchFn }, global: { plugins: [ui, router] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="catalogs-failure"]').text()).toContain('Too many requests. Try again in 42 seconds.')
  })

  it('shows an empty state without catalogs', async () => {
    const { wrapper } = await mountPage(ME_VIEWER, [])
    expect(wrapper.find('[data-testid="catalogs-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="new-catalog"]').exists()).toBe(true)
  })

  // spec "Principal without read permission"
  it('hides "New catalog" without entity:read', async () => {
    const { wrapper } = await mountPage(ME_WITHOUT_ROLE)
    expect(wrapper.find('[data-testid="new-catalog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="no-catalog-access"]').exists()).toBe(true)
  })

  it('opens the creation form and navigates to the created catalog', async () => {
    const { wrapper, router } = await mountPage(ME_VIEWER)
    await wrapper.get('[data-testid="new-catalog"]').trigger('click')
    await flushPromises()
    const form = wrapper.findComponent(CreateCatalogForm)
    expect(form.props('canManage')).toBe(false)
    form.vm.$emit('created', CATALOG)
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(`/catalogs/${CATALOG.id}`)
    wrapper.unmount()
  })
})
