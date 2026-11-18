import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import DashboardsPage from '../pages/DashboardsPage.vue'
import { dashboard, dashboardApi, FILLED_DASHBOARD, ME_ADMIN, ME_MANAGER, ME_READER, PERSONAL_DASHBOARD } from '../test/dashboards'

// SCR-056 Dashboards (UC-07, US-055, US-062): the shared dashboards and the own personal ones, and
// the dialog "Create dashboard" (docs/screens/SCREENS.md). The first story is the screen image.
const SECURITY = dashboard({
  id: 'd0000000-0000-4000-8000-000000000003',
  name: 'Security Posture',
  description: 'Findings and measures of the information security office',
  widgets: [FILLED_DASHBOARD.widgets[0]!],
})
const MINE = { ...PERSONAL_DASHBOARD, owner: { id: 7, displayName: 'Kurt' } }
const ALL = [FILLED_DASHBOARD, SECURITY, MINE]

const meta: Meta<typeof DashboardsPage> = {
  title: 'Screens/SCR-056 Dashboards',
  component: DashboardsPage,
}

export default meta
type Story = StoryObj<typeof meta>

/** A holder of `dashboard:manage`: shared dashboards and an own personal one. */
export const SharedAndPersonal: Story = { args: { fetchFn: dashboardApi({ me: ME_MANAGER, dashboards: ALL }) } }

/** A reader: the shared dashboards, and the own personal dashboard. */
export const Reader: Story = { args: { fetchFn: dashboardApi({ me: ME_READER, dashboards: [FILLED_DASHBOARD, SECURITY, PERSONAL_DASHBOARD] }) } }

/** An administrator also sees the personal dashboards of others, marked by their owner. */
export const Administrator: Story = { args: { fetchFn: dashboardApi({ me: ME_ADMIN, dashboards: [FILLED_DASHBOARD, SECURITY, PERSONAL_DASHBOARD] }) } }

export const NoDashboards: Story = { args: { fetchFn: dashboardApi({ me: ME_READER, dashboards: [] }) } }

/** The dialog: a shared dashboard needs `dashboard:manage`. */
export const CreateDialog: Story = {
  args: { fetchFn: dashboardApi({ me: ME_READER, dashboards: [FILLED_DASHBOARD] }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Create dashboard' }))
    const dialog = within(document.body)
    await expect(await dialog.findByRole('dialog')).toBeInTheDocument()
    await expect(dialog.getByRole('radio', { name: /Shared/ })).toBeDisabled()
    await expect(dialog.getByRole('radio', { name: /Personal/ })).toBeChecked()
  },
}
