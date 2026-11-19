import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../session/store'
import { ME_WITHOUT_ROLE } from '../test/catalogs'
import { TABLE_WIDGET } from '../test/dashboards'
import { PORTAL_DASHBOARD, PORTAL_DASHBOARDS, portalDashboardApi } from '../test/portal'
import { testRouter } from '../test/router'
import PortalDashboardPage from './PortalDashboardPage.vue'

// ECharts draws in the browser; the stories show the charts. Here only what the page hands over.
vi.mock('../dashboard/chartRuntime', () => ({
  createChart: () => ({ setOption: () => undefined, resize: () => undefined, dispose: () => undefined }),
  encodeHTML: (text: string) => text,
}))

async function mountPage(fetchFn: typeof fetch, id: string = PORTAL_DASHBOARD.id) {
  const router = testRouter()
  await router.push(`/portal/dashboards/${id}`)
  const wrapper = mount(PortalDashboardPage, { props: { id, fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

// spec portal/portal-dashboards "Dashboards on the portal pages", "Read a dashboard in the portal"
describe('PortalDashboardPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('shows the widgets in their saved grid positions', async () => {
    const { wrapper } = await mountPage(portalDashboardApi())
    expect(wrapper.get('[data-testid="portal-dashboard-title"]').text()).toBe(PORTAL_DASHBOARD.name)
    for (const widget of PORTAL_DASHBOARD.widgets) {
      const section = wrapper.get(`[data-testid="widget-${widget.id}"]`)
      const { col, row, width, height } = widget.position
      // jsdom reports the two properties as the shorthand `grid-area`: row start, column start, spans
      expect(section.attributes('style')).toContain(`grid-area: ${row} / ${col} / span ${height} / span ${width}`)
    }
    expect(wrapper.get('[data-testid="widget-table"]').text()).toContain('Application 01')
    expect(wrapper.get('[data-testid="portal-dashboard-generated"]').text()).toContain('Data as of')
  })

  it('has no edit action, no drag handle and no resize handle', async () => {
    const { wrapper } = await mountPage(portalDashboardApi())
    for (const testId of ['edit-dashboard', 'add-widget', 'delete-dashboard', 'rename-dashboard', 'done-editing']) {
      expect(wrapper.find(`[data-testid="${testId}"]`).exists()).toBe(false)
    }
    for (const widget of PORTAL_DASHBOARD.widgets) {
      expect(wrapper.find(`[data-testid="move-${widget.id}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="resize-${widget.id}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="edit-${widget.id}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="remove-${widget.id}"]`).exists()).toBe(false)
    }
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('Edit')
  })

  it('marks a personal dashboard and not a shared one', async () => {
    const personal = PORTAL_DASHBOARDS[2]!
    const { wrapper } = await mountPage(portalDashboardApi(), personal.id)
    expect(wrapper.get('[data-testid="portal-dashboard-personal"]').text()).toBe('Personal')
    const shared = await mountPage(portalDashboardApi())
    expect(shared.wrapper.find('[data-testid="portal-dashboard-personal"]').exists()).toBe(false)
  })

  it('reads the configuration and the data once, and the data again on "Refresh" only', async () => {
    const recorded: string[] = []
    const { wrapper } = await mountPage(portalDashboardApi({ recorded }))
    const base = `/api/v1/portal/dashboards/${PORTAL_DASHBOARD.id}`
    expect(recorded.filter((path) => path === base)).toHaveLength(1)
    expect(recorded.filter((path) => path === `${base}/data`)).toHaveLength(1)
    const before = wrapper.get('[data-testid="portal-dashboard-generated"]').text()
    await wrapper.get('[data-testid="portal-dashboard-refresh"]').trigger('click')
    await vi.waitFor(() => expect(recorded.filter((path) => path === `${base}/data`)).toHaveLength(2))
    await vi.waitFor(() => expect(wrapper.get('[data-testid="portal-dashboard-generated"]').text()).not.toBe(before))
    expect(recorded.filter((path) => path === base)).toHaveLength(1)
  })

  it('keeps the shown data and says so when "Refresh" fails', async () => {
    const { wrapper } = await mountPage(portalDashboardApi({ failingDataFrom: 2 }))
    const before = wrapper.get('[data-testid="portal-dashboard-generated"]').text()
    await wrapper.get('[data-testid="portal-dashboard-refresh"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('[data-testid="portal-dashboard-refresh-failure"]').exists()).toBe(true))
    expect(wrapper.get('[data-testid="portal-dashboard-refresh-failure"]').text()).toContain('The data could not be refreshed.')
    expect(wrapper.get('[data-testid="portal-dashboard-generated"]').text()).toBe(before)
    expect(wrapper.find('[data-testid="widget-table"]').exists()).toBe(true)
  })

  it('reads further pages of a table widget through the portal widget operation', async () => {
    const recorded: string[] = []
    const { wrapper } = await mountPage(portalDashboardApi({ recorded }))
    wrapper.findComponent({ name: 'Pagination' }).vm.$emit('update:page', 2)
    await vi.waitFor(() => expect(wrapper.get('[data-testid="widget-page-info"]').text()).toContain('Page 2 of 5'))
    expect(recorded).toContain(`/api/v1/portal/dashboards/${PORTAL_DASHBOARD.id}/widgets/${TABLE_WIDGET.id}/data?page=1`)
    expect(recorded.some((path) => path.startsWith('/api/v1/views/'))).toBe(false)
    expect(wrapper.findAll('[data-testid="widget-row"]')[0]!.text()).toContain('Application 11')
  })

  it('shows "Dashboard not found" with a link back for an unknown dashboard', async () => {
    const { wrapper } = await mountPage(portalDashboardApi(), 'd0000000-0000-4000-8000-0000000000ff')
    expect(wrapper.get('[data-testid="portal-dashboard-not-found"]').text()).toContain('Dashboard not found')
    expect(wrapper.find('[data-testid="portal-dashboard-title"]').exists()).toBe(false)
  })

  it('shows an error when the dashboard cannot be loaded', async () => {
    const { wrapper } = await mountPage(portalDashboardApi({ failingDataFrom: 1 }))
    expect(wrapper.get('[data-testid="portal-dashboard-failure"]').text()).toContain('The dashboard could not be loaded.')
  })

  it('tells a session without portal:read that it has no access and reads nothing', async () => {
    const recorded: string[] = []
    const { wrapper } = await mountPage(portalDashboardApi({ me: ME_WITHOUT_ROLE, recorded }))
    expect(wrapper.find('[data-testid="no-portal-access"]').exists()).toBe(true)
    expect(recorded.some((path) => path.startsWith('/api/v1/portal/dashboards'))).toBe(false)
  })

  it('shows an empty dashboard without a grid', async () => {
    const empty = { ...PORTAL_DASHBOARD, widgets: [] }
    const { wrapper } = await mountPage(portalDashboardApi({ dashboards: [empty] }))
    expect(wrapper.get('[data-testid="dashboard-empty"]').text()).toContain('No widgets yet.')
    expect(wrapper.find('[data-testid="dashboard-grid"]').exists()).toBe(false)
  })

  it('shows a restricted widget with its message', async () => {
    const { wrapper } = await mountPage(portalDashboardApi({ restricted: true }))
    expect(wrapper.find('[data-testid="widget-restricted-status"]').exists()).toBe(true)
  })
})
