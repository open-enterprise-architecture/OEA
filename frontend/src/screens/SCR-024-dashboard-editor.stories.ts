import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import DashboardPage from '../pages/DashboardPage.vue'
import {
  BAR_WIDGET,
  dashboard,
  DASHBOARD_WITH_ERROR,
  dashboardApi,
  DONUT_WIDGET,
  EMPTY_DASHBOARD,
  FILLED_DASHBOARD,
  INVALID_WIDGET,
  KPI_AND_CHARTS_DASHBOARD,
  KPI_DECREASED_WIDGET,
  KPI_EQUAL_WIDGET,
  KPI_INCREASED_WIDGET,
  KPI_NO_COMPARISON_WIDGET,
  KPI_NO_DATA_WIDGET,
  KPI_PREVIOUS_ZERO_WIDGET,
  KPI_WIDGET,
  ME_ADMIN,
  ME_MANAGER,
  ME_READER,
  OVERLAPPING_DASHBOARD,
  PERSONAL_DASHBOARD,
  PIE_WIDGET,
  position,
  RESTRICTED_WIDGET,
  TEXT_WIDGET,
  TREEMAP_WIDGET,
} from '../test/dashboards'
import type { DashboardConfig, WidgetConfig } from '../api/dashboards'

// SCR-024 Dashboard editor (UC-07, US-055, US-056, US-057, US-058 to US-060): a dashboard with its
// widgets in the 12-column grid; the editor of a holder of `dashboard:manage` moves and resizes
// them (docs/screens/SCREENS.md). The first story is the screen image.
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

/** A dashboard with one widget at the top left, for the stories of single widgets. */
function single(widget: WidgetConfig, width = 6, height = 3): DashboardConfig {
  return dashboard({ version: 9, canEdit: false, widgets: [{ ...widget, position: position(1, 1, width, height) }] })
}

// ---- KPI and chart widgets (US-056, US-057; us-056-kpi-and-chart-widgets)

/** KPI and chart widgets in one dashboard, with the widgets that cannot be shown. */
export const KpiAndCharts: Story = page(ME_READER, { ...KPI_AND_CHARTS_DASHBOARD, canEdit: false })

/** A KPI with the unit, the target, the difference and the trend as icon and text. */
export const KpiWithTarget: Story = {
  ...page(ME_READER, single(KPI_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('42,000,000 CHF')).toBeInTheDocument()
    await expect(canvas.getByText('8,000,000 CHF below the target')).toBeInTheDocument()
  },
}

/** A KPI without a value shows "– (no data)", not an error. */
export const KpiWithoutData: Story = {
  ...page(ME_READER, single(KPI_NO_DATA_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('– (no data)')).toBeInTheDocument()
  },
}

/** Compared with 30 days ago, increased: the previous value, the signed difference with the percentage, an arrow up. */
export const KpiIncreased: Story = {
  ...page(ME_READER, single(KPI_INCREASED_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('30 days ago (Oct 20, 2026): 35,000,000 CHF')).toBeInTheDocument()
    await expect(canvas.getByText('Increase: +7,000,000 CHF (+20%)')).toBeInTheDocument()
  },
}

/** Compared with 30 days ago, decreased: an arrow down. */
export const KpiDecreased: Story = {
  ...page(ME_READER, single(KPI_DECREASED_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('Decrease: -8,000,000 CHF (-16%)')).toBeInTheDocument()
  },
}

/** Compared with 30 days ago, unchanged: the equal sign. */
export const KpiEqual: Story = {
  ...page(ME_READER, single(KPI_EQUAL_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('Unchanged')).toBeInTheDocument()
  },
}

/** No entity had a value 30 days ago: "No comparison value". */
export const KpiWithoutComparisonValue: Story = {
  ...page(ME_READER, single(KPI_NO_COMPARISON_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('No comparison value')).toBeInTheDocument()
  },
}

/** A previous value of 0 has no percentage. */
export const KpiFromZero: Story = {
  ...page(ME_READER, single(KPI_PREVIOUS_ZERO_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('Increase: +42,000,000 CHF')).toBeInTheDocument()
  },
}

/** Bars with data labels and the legend: the investment per plateau, "(none)" for entities without one. */
export const BarChart: Story = {
  ...page(ME_READER, single(BAR_WIDGET)),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByRole('img', { name: 'Chart Investment per Plateau' })).toBeInTheDocument()
  },
}

/** A pie of the entities per architecture layer. */
export const PieChart: Story = page(ME_READER, single(PIE_WIDGET))

/** A donut of a boolean property: "Yes" and "No" are named by the client. */
export const DonutChart: Story = page(ME_READER, single(DONUT_WIDGET))

/** A treemap with "(none)" and "Other". */
export const TreemapChart: Story = page(ME_READER, single(TREEMAP_WIDGET))

/** The reader may not read the values of the property for every entity: no aggregate is shown. */
export const RestrictedWidget: Story = {
  ...page(ME_READER, single(RESTRICTED_WIDGET, 4, 2)),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText(/not readable for every entity/)).toBeInTheDocument()
  },
}

/** A widget whose property was unmapped later: the message for this widget, the others stay. */
export const InvalidConfiguration: Story = {
  ...page(ME_READER, dashboard({ canEdit: false, widgets: [{ ...KPI_WIDGET, position: position(1, 1, 4, 2) }, { ...INVALID_WIDGET, position: position(5, 1, 8, 3) }] })),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText(/no longer fits the metamodel/)).toBeInTheDocument()
    await expect(canvas.getByText('42,000,000 CHF')).toBeInTheDocument()
  },
}

/** The dialog "Add widget" for a KPI: element type, function, property, unit and comparison. */
export const AddKpiWidget: Story = {
  ...page(ME_MANAGER, EMPTY_DASHBOARD),
  play: async ({ canvasElement }) => {
    await userEvent.click(await within(canvasElement).findByRole('button', { name: 'Add widget' }))
    const dialog = within(await within(document.body).findByRole('dialog'))
    await userEvent.click(await dialog.findByRole('radio', { name: /KPI/ }))
    await expect(await dialog.findByText('Function')).toBeInTheDocument()
    await expect(dialog.getByText('Comparison')).toBeInTheDocument()
  },
}

/** The dialog "Add widget" for a chart: grouping, chart type, legend and data labels. */
export const AddChartWidget: Story = {
  ...page(ME_MANAGER, EMPTY_DASHBOARD),
  play: async ({ canvasElement }) => {
    await userEvent.click(await within(canvasElement).findByRole('button', { name: 'Add widget' }))
    const dialog = within(await within(document.body).findByRole('dialog'))
    await userEvent.click(await dialog.findByRole('radio', { name: /Chart/ }))
    await expect(await dialog.findByText('Group by')).toBeInTheDocument()
    await expect(dialog.getByText('Chart type')).toBeInTheDocument()
    await expect(dialog.getByText('Show legend')).toBeInTheDocument()
  },
}
