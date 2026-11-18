import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import CreateDashboardForm from '../components/dashboard/CreateDashboardForm.vue'
import { session } from '../session/store'
import { ME_WITHOUT_ROLE } from '../test/catalogs'
import { dashboard, dashboardApi, FILLED_DASHBOARD, ME_ADMIN, ME_MANAGER, ME_READER, PERSONAL_DASHBOARD } from '../test/dashboards'
import { testRouter } from '../test/router'
import DashboardsPage from './DashboardsPage.vue'

async function mountPage(me: unknown, dashboards = [FILLED_DASHBOARD, dashboard({ id: 'd0000000-0000-4000-8000-000000000009', name: 'Security Posture' }), PERSONAL_DASHBOARD]) {
  const router = testRouter()
  await router.push('/dashboards')
  const fetchFn = dashboardApi({ me, dashboards })
  const wrapper = mount(DashboardsPage, { props: { fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

// spec dashboard/dashboards "Dashboard pages" (SCR-056)
describe('DashboardsPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('lists the dashboards with scope, owner and number of widgets', async () => {
    const { wrapper } = await mountPage(ME_MANAGER)
    const items = wrapper.findAll('[data-testid="dashboard-item"]')
    expect(items).toHaveLength(3)
    expect(items[0]!.text()).toContain('IT Investment Planning 2026–2030')
    expect(items[0]!.text()).toContain('Shared')
    expect(items[0]!.text()).toContain('Owner: Kurt')
    expect(items[0]!.text()).toContain('2 widgets')
    expect(items[1]!.text()).toContain('no widgets')
    expect(items[2]!.text()).toContain('My analysis')
    expect(items[2]!.text()).toContain('Personal')
    expect(items[2]!.text()).toContain('Owner: Franz')
    expect(items[0]!.attributes('href')).toBe('/dashboards/d0000000-0000-4000-8000-000000000001')
  })

  // the administrator's list holds personal dashboards of others, marked by their owner
  it('marks the personal dashboard of another person by its owner for an administrator', async () => {
    const { wrapper } = await mountPage(ME_ADMIN)
    const foreign = wrapper.findAll('[data-testid="dashboard-item"]').find((item) => item.text().includes('My analysis'))!
    expect(foreign.text()).toContain('Owner: Franz')
    expect(foreign.text()).toContain('Personal')
  })

  it('shows an empty state without dashboards', async () => {
    const { wrapper } = await mountPage(ME_READER, [])
    expect(wrapper.find('[data-testid="dashboards-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="new-dashboard"]').exists()).toBe(true)
  })

  it('hides "Create dashboard" without entity:read', async () => {
    const { wrapper } = await mountPage(ME_WITHOUT_ROLE)
    expect(wrapper.find('[data-testid="new-dashboard"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="no-dashboard-access"]').exists()).toBe(true)
  })

  it('offers a shared dashboard in the creation form only with dashboard:manage', async () => {
    const reader = await mountPage(ME_READER)
    await reader.wrapper.get('[data-testid="new-dashboard"]').trigger('click')
    await flushPromises()
    expect(reader.wrapper.findComponent(CreateDashboardForm).props('canManage')).toBe(false)
    session.me = undefined
    session.loaded = false
    const manager = await mountPage(ME_MANAGER)
    await manager.wrapper.get('[data-testid="new-dashboard"]').trigger('click')
    await flushPromises()
    expect(manager.wrapper.findComponent(CreateDashboardForm).props('canManage')).toBe(true)
  })

  // spec "Empty dashboard after creation": the page opens the new dashboard
  it('opens the created dashboard', async () => {
    const { wrapper, router } = await mountPage(ME_MANAGER)
    await wrapper.get('[data-testid="new-dashboard"]').trigger('click')
    await flushPromises()
    wrapper.findComponent(CreateDashboardForm).vm.$emit('created', dashboard({ id: 'd1' }))
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/dashboards/d1')
  })
})
