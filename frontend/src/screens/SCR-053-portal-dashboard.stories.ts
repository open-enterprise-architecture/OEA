import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import PortalDashboardPage from '../pages/PortalDashboardPage.vue'
import { session } from '../session/store'
import { PERSONAL_DASHBOARD } from '../test/dashboards'
import { ME_PORTAL_READER, PORTAL_FAILING, PORTAL_OVERVIEW, portalDashboardApi } from '../test/portal'

// SCR-053 Dashboard view, read-only (UC-07, US-061, US-060 AC5): a dashboard in the portal with its
// widgets in the saved grid positions, without edit mode, drag handles or resizing, and with
// "Refresh" (docs/screens/SCREENS.md). The first story is the screen image.

/** The header with the dashboard `id` of `dashboards` for Petra, a portal reader. */
function screen(id: string, dashboards: Parameters<typeof portalDashboardApi>[0] = {}): Story {
  return {
    render: () => ({
      components: { AppHeader, PortalDashboardPage },
      setup: () => {
        session.me = ME_PORTAL_READER as Me
        session.loaded = true
        return { fetchFn: portalDashboardApi(dashboards), id }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <PortalDashboardPage :id="id" :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof PortalDashboardPage> = {
  title: 'Screens/SCR-053 Portal Dashboard',
  component: PortalDashboardPage,
}

export default meta
type Story = StoryObj<typeof meta>

/** A KPI, a table, a bar chart and a pie chart in their saved positions; no edit action. */
export const Overview: Story = {
  ...screen(PORTAL_OVERVIEW.id, { dashboards: [PORTAL_OVERVIEW] }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('heading', { name: 'IT Investment Planning' })).toBeInTheDocument()
    await expect(await canvas.findByRole('heading', { name: 'Application Overview' })).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Edit' })).toBeNull()
    await expect(canvas.queryByRole('button', { name: 'Add widget' })).toBeNull()
    await expect(canvas.queryByRole('button', { name: /Move widget/ })).toBeNull()
    await expect(canvas.queryByRole('button', { name: /Resize widget/ })).toBeNull()
  },
}

/** "Refresh" reads the data again and updates the time of the data. */
export const Refresh: Story = {
  ...screen(PORTAL_OVERVIEW.id, { dashboards: [PORTAL_OVERVIEW] }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const before = (await canvas.findByTestId('portal-dashboard-generated')).textContent
    await userEvent.click(await canvas.findByRole('button', { name: 'Refresh' }))
    await expect(await canvas.findByText((text) => text.startsWith('Data as of') && text !== before)).toBeInTheDocument()
  },
}

/** A widget that fails shows its message; the others show their data. */
export const FailingWidget: Story = {
  ...screen(PORTAL_FAILING.id, { dashboards: [PORTAL_FAILING] }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('The widget no longer fits the metamodel. Edit or remove it.')).toBeInTheDocument()
    await expect(canvas.getByText('Catalog not found')).toBeInTheDocument()
  },
}

/** The own personal dashboard carries the badge "Personal". */
export const Personal: Story = {
  ...screen(PERSONAL_DASHBOARD.id, { dashboards: [{ ...PERSONAL_DASHBOARD, canEdit: false }] }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('heading', { name: 'My analysis' })).toBeInTheDocument()
    await expect(canvas.getByText('Personal')).toBeInTheDocument()
  },
}

/** An unknown dashboard, or another person's personal one: "Dashboard not found" with a link back. */
export const NotFound: Story = {
  ...screen('d0000000-0000-4000-8000-0000000000ff'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Dashboard not found')).toBeInTheDocument()
    await expect(canvas.getAllByRole('link', { name: 'Back to the portal' }).length).toBeGreaterThan(0)
  },
}
