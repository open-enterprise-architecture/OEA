import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { ME_WITHOUT_ROLE } from '../test/catalogs'
import { ME_ARCHITECT, ME_PORTAL_READER, PORTAL_DASHBOARDS, portalApi, portalDashboardApi, SHARED_CATALOGS } from '../test/portal'
import { testRouter } from '../test/router'
import PortalPage from './PortalPage.vue'

async function mountPage(fetchFn: typeof fetch) {
  const router = testRouter()
  await router.push('/portal')
  const wrapper = mount(PortalPage, { props: { fetchFn }, global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router }
}

// spec portal/portal-catalogs "Portal pages"
describe('PortalPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('lists the shared catalogs with their entity type as links into the portal', async () => {
    const { wrapper } = await mountPage(portalApi())
    const items = wrapper.findAll('[data-testid="portal-catalog-item"]')
    expect(items.map((item) => item.text())).toEqual([
      expect.stringContaining('Application Inventory'),
      expect.stringContaining('Compliance Overview'),
    ])
    expect(items[0]!.text()).toContain('Application Component')
    expect(items[0]!.attributes('href')).toBe(`/portal/catalogs/${SHARED_CATALOGS[0]!.id}`)
  })

  it('lists the same shared catalogs for an architect', async () => {
    const { wrapper } = await mountPage(portalApi(undefined, { me: ME_ARCHITECT }))
    expect(wrapper.findAll('[data-testid="portal-catalog-item"]')).toHaveLength(2)
  })

  it('shows an empty state without shared catalogs', async () => {
    const { wrapper } = await mountPage(portalApi(undefined, { catalogs: [] }))
    expect(wrapper.find('[data-testid="portal-empty"]').text()).toContain('No shared catalogs yet')
  })

  it('shows an error when the catalogs cannot be loaded', async () => {
    const { wrapper } = await mountPage(portalApi(undefined, { failing: true }))
    expect(wrapper.get('[data-testid="portal-failure"]').text()).toContain('The shared catalogs could not be loaded.')
  })

  it('tells a session without portal:read that it has no access', async () => {
    const recorded: string[] = []
    const { wrapper } = await mountPage(portalApi(undefined, { me: ME_WITHOUT_ROLE, recorded }))
    expect(wrapper.find('[data-testid="no-portal-access"]').exists()).toBe(true)
    expect(recorded).not.toContain('/api/v1/portal/catalogs')
  })

  it('keeps the portal reader on the portal', async () => {
    const { router } = await mountPage(portalApi(undefined, { me: ME_PORTAL_READER }))
    expect(router.currentRoute.value.path).toBe('/portal')
  })

  // spec portal/portal-dashboards "Dashboards on the portal pages"
  describe('section "Dashboards"', () => {
    it('lists the dashboards with the badge "Personal" on personal ones, as links into the portal', async () => {
      const { wrapper } = await mountPage(portalDashboardApi())
      const items = wrapper.findAll('[data-testid="portal-dashboard-item"]')
      expect(items).toHaveLength(PORTAL_DASHBOARDS.length)
      expect(items[0]!.text()).toContain(PORTAL_DASHBOARDS[0]!.name)
      expect(items[0]!.attributes('href')).toBe(`/portal/dashboards/${PORTAL_DASHBOARDS[0]!.id}`)
      expect(items[0]!.find('[data-testid="portal-dashboard-badge"]').exists()).toBe(false)
      expect(items[0]!.get('[data-testid="portal-dashboard-widget-count"]').text()).toBe('2 widgets')
      const personal = items[2]!
      expect(personal.text()).toContain('My analysis')
      expect(personal.get('[data-testid="portal-dashboard-badge"]').text()).toBe('Personal')
    })

    it('shows "No dashboards yet." without dashboards, next to the catalogs', async () => {
      const { wrapper } = await mountPage(portalDashboardApi({ dashboards: [] }))
      expect(wrapper.get('[data-testid="portal-dashboards-empty"]').text()).toContain('No dashboards yet.')
      expect(wrapper.findAll('[data-testid="portal-catalog-item"]')).toHaveLength(2)
    })

    it('keeps the catalogs when the dashboards cannot be loaded', async () => {
      const { wrapper } = await mountPage(portalDashboardApi({ failingDashboards: true }))
      expect(wrapper.get('[data-testid="portal-dashboards-failure"]').text()).toContain('The dashboards could not be loaded.')
      expect(wrapper.find('[data-testid="portal-failure"]').exists()).toBe(false)
      expect(wrapper.findAll('[data-testid="portal-catalog-item"]')).toHaveLength(2)
    })

    it('keeps the dashboards when the catalogs cannot be loaded', async () => {
      const { wrapper } = await mountPage(portalDashboardApi({ failingCatalogs: true }))
      expect(wrapper.get('[data-testid="portal-failure"]').text()).toContain('The shared catalogs could not be loaded.')
      expect(wrapper.find('[data-testid="portal-catalog-item"]').exists()).toBe(false)
      expect(wrapper.findAll('[data-testid="portal-dashboard-item"]')).toHaveLength(PORTAL_DASHBOARDS.length)
      expect(wrapper.find('[data-testid="portal-dashboards-failure"]').exists()).toBe(false)
    })

    it('reads no dashboards for a session without portal:read', async () => {
      const recorded: string[] = []
      const { wrapper } = await mountPage(portalDashboardApi({ me: ME_WITHOUT_ROLE, recorded }))
      expect(wrapper.find('[data-testid="no-portal-access"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="portal-dashboard-list"]').exists()).toBe(false)
      expect(recorded).not.toContain('/api/v1/portal/dashboards')
    })
  })
})
