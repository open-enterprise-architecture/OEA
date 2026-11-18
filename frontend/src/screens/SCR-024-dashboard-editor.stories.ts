import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import DashboardPage from '../pages/DashboardPage.vue'
import {
  DASHBOARD_WITH_ERROR,
  dashboardApi,
  EMPTY_DASHBOARD,
  FILLED_DASHBOARD,
  ME_ADMIN,
  ME_MANAGER,
  ME_READER,
  OVERLAPPING_DASHBOARD,
  PERSONAL_DASHBOARD,
  TEXT_WIDGET,
} from '../test/dashboards'
import type { DashboardConfig } from '../api/dashboards'

// SCR-024 Dashboard editor (UC-07, US-055, US-058 to US-060): a dashboard with its widgets in the
// 12-column grid; the editor of a holder of `dashboard:manage` moves and resizes them
// (docs/screens/SCREENS.md). The first story is the screen image.
function page(me: unknown, config: DashboardConfig, options: { restricted?: boolean } = {}): Story {
  return {
    render: () => ({
      components: { DashboardPage },
      setup: () => ({ fetchFn: dashboardApi({ me, dashboards: [config], ...options }), id: config.id }),
      template: '<DashboardPage :id="id" :fetch-fn="fetchFn" />',
    }),
  }
}

const meta: Meta<typeof DashboardPage> = {
  title: 'Screens/SCR-024 Dashboard Editor',
  component: DashboardPage,
}

export default meta
type Story = StoryObj<typeof meta>

/** The edit mode: the handle of each widget, its corner for resizing, and the arrangement buttons. */
export const EditMode: Story = {
  ...page(ME_MANAGER, FILLED_DASHBOARD),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Edit' }))
    await expect(await canvas.findByRole('button', { name: 'Move widget Methodological Notes' })).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Add widget' })).toBeInTheDocument()
  },
}

/** The view mode of a text and a table widget. */
export const TextAndTable: Story = page(ME_MANAGER, FILLED_DASHBOARD)

/** A new dashboard: "No widgets yet. Add widget." */
export const Empty: Story = page(ME_MANAGER, EMPTY_DASHBOARD)

/** A reader sees the widgets and no edit action. */
export const ReadOnly: Story = {
  ...page(ME_READER, { ...FILLED_DASHBOARD, canEdit: false }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Application Overview')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Edit' })).toBeNull()
  },
}

/** The table widget of a retired catalog shows its message; the other widgets are shown normally. */
export const ErrorWidget: Story = page(ME_READER, { ...DASHBOARD_WITH_ERROR, canEdit: false })

/** Overlapping widgets get a red border and the note "Overlaps another widget". */
export const Overlap: Story = {
  ...page(ME_MANAGER, OVERLAPPING_DASHBOARD),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect((await canvas.findAllByText('Overlaps another widget')).length).toBe(2)
  },
}

/** The values of a column the reader may not see are left out, as in the catalog. */
export const RestrictedColumn: Story = page(ME_READER, { ...FILLED_DASHBOARD, canEdit: false }, { restricted: true })

/** An administrator reads the personal dashboard of another person, read-only. */
export const PersonalOfAnotherPerson: Story = page(ME_ADMIN, { ...PERSONAL_DASHBOARD, canEdit: false, widgets: [TEXT_WIDGET] })

/** The dialog "Add widget". */
export const AddWidget: Story = {
  ...page(ME_MANAGER, EMPTY_DASHBOARD),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Add widget' }))
    await expect(await within(document.body).findByRole('dialog')).toBeInTheDocument()
  },
}
