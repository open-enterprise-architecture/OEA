import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { WidgetData, WidgetSeriesEntry, WidgetSettings } from '../../api/dashboards'
import { setLanguage } from '../../i18n'
import { clearLabels, labels } from '../../i18n/labels'

const created = vi.hoisted(() => ({ options: [] as Record<string, unknown>[], disposed: 0 }))

// the chart itself is drawn by ECharts and tested in the stories; here only what is handed to it
vi.mock('../../dashboard/chartRuntime', () => ({
  createChart: () => ({
    setOption: (option: Record<string, unknown>) => created.options.push(option),
    resize: () => undefined,
    dispose: () => {
      created.disposed += 1
    },
  }),
  encodeHTML: (text: string) => text.replaceAll('<', '&lt;'),
}))

const { default: ChartWidget } = await import('./ChartWidget.vue')

const SERIES: WidgetSeriesEntry[] = [
  { kind: 'value', key: 'P1', label: 'P1', labelKey: 'enum-value.e1.label', value: 5000000 },
  { kind: 'value', key: 'x', label: 'Plain', value: 3 },
  { kind: 'none', value: 2 },
  { kind: 'other', value: 1 },
  { kind: 'true', value: 4 },
  { kind: 'false', value: 6 },
]

function data(series: WidgetSeriesEntry[] = SERIES, overrides: Partial<WidgetData> = {}): WidgetData {
  return { widgetId: 'w1', type: 'chart', status: 'ok', series, ...overrides }
}

const mounted: { unmount: () => void }[] = []

function mountChart(props: { data?: WidgetData; config?: WidgetSettings }) {
  const wrapper = mount(ChartWidget, { props: { config: { chartType: 'bar' }, title: 'Per Plateau', ...props }, global: { plugins: [ui] } })
  mounted.push(wrapper)
  return wrapper
}

type Option = { series: { type: string; data: { name?: string }[] | number[] }[]; legend: { show: boolean }; xAxis?: { data: string[] } }

function last(): Option {
  return created.options.at(-1) as unknown as Option
}

// a chart still mounted would draw again when the language changes
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
})

beforeEach(() => {
  created.options.length = 0
  created.disposed = 0
  clearLabels()
  setLanguage('en')
})

// spec dashboard/widgets "Chart widget" (US-057)
describe('ChartWidget', () => {
  it('names the groups: labels through the label key with the name as fallback, and the client names of the rest', () => {
    labels.bundle = { 'enum-value.e1.label': 'Plateau One' }
    const wrapper = mountChart({ data: data() })
    const rows = wrapper.findAll('[data-testid="chart-table"] tbody tr').map((row) => row.findAll('td').map((cell) => cell.text()))
    expect(rows).toEqual([
      ['Plateau One', '5,000,000'],
      ['Plain', '3'],
      ['(none)', '2'],
      ['Other', '1'],
      ['Yes', '4'],
      ['No', '6'],
    ])
    expect(last().xAxis?.data).toEqual(['Plateau One', 'Plain', '(none)', 'Other', 'Yes', 'No'])
  })

  it('shows the name in the corporate language while no label is known', () => {
    const wrapper = mountChart({ data: data() })
    expect(wrapper.findAll('[data-testid="chart-table"] tbody tr td')[0]!.text()).toBe('P1')
  })

  it('names the groups in German', () => {
    setLanguage('de')
    const wrapper = mountChart({ data: data() })
    const names = wrapper.findAll('[data-testid="chart-table"] tbody tr').map((row) => row.find('td').text())
    expect(names.slice(2)).toEqual(['(keine)', 'Andere', 'Ja', 'Nein'])
    setLanguage('en')
  })

  it('formats the values with the unit', () => {
    const wrapper = mountChart({ data: data(SERIES.slice(0, 2)), config: { chartType: 'bar', unit: 'CHF' } })
    expect(wrapper.findAll('[data-testid="chart-table"] tbody td')[1]!.text()).toBe('5,000,000 CHF')
  })

  it.each([
    ['bar', 'bar'],
    ['line', 'line'],
    ['area', 'line'],
    ['pie', 'pie'],
    ['donut', 'pie'],
    ['treemap', 'treemap'],
  ] as const)('draws a %s chart as %s', (chartType, seriesType) => {
    mountChart({ data: data(), config: { chartType } })
    expect(last().series[0]!.type).toBe(seriesType)
  })

  it.each([
    [undefined, true],
    [true, true],
    [false, false],
  ])('the legend setting %s shows the legend: %s', (showLegend, shown) => {
    mountChart({ data: data(), config: { chartType: 'pie', showLegend } })
    expect(last().legend.show).toBe(shown)
  })

  it('is an image with a text alternative and a table for screen readers', () => {
    const wrapper = mountChart({ data: data() })
    expect(wrapper.get('[data-testid="chart"]').attributes('role')).toBe('img')
    expect(wrapper.get('[data-testid="chart"]').attributes('aria-label')).toBe('Chart Per Plateau')
    expect(wrapper.get('[data-testid="chart-table"] caption').text()).toBe('Data of the chart Per Plateau')
  })

  it('shows markup in a name literally in the table', () => {
    const wrapper = mountChart({ data: data([{ kind: 'value', key: 'x', label: '<img src=x onerror=alert(1)>', value: 1 }]) })
    expect(wrapper.find('[data-testid="chart-table"] img').exists()).toBe(false)
    expect(wrapper.get('[data-testid="chart-table"] tbody td').text()).toBe('<img src=x onerror=alert(1)>')
  })

  it('draws again when the data changes and disposes the chart with the widget', async () => {
    const wrapper = mountChart({ data: data(SERIES.slice(0, 1)) })
    const drawn = created.options.length
    await wrapper.setProps({ data: data(SERIES.slice(0, 2)) })
    await flushPromises()
    expect(created.options.length).toBeGreaterThan(drawn)
    expect(last().series[0]!.data).toHaveLength(2)
    created.disposed = 0
    wrapper.unmount()
    expect(created.disposed).toBe(1)
  })

  it('shows a note without groups and draws nothing', () => {
    const wrapper = mountChart({ data: data([]) })
    expect(wrapper.get('[data-testid="chart-no-groups"]').text()).toBe('No groups to show')
    expect(created.options).toHaveLength(0)
  })

  it.each([
    ['restricted', 'The values of this property are not readable for every entity, so no aggregate is shown.'],
    ['invalid-configuration', 'The widget no longer fits the metamodel. Edit or remove it.'],
  ] as const)('shows the error %s and draws nothing', (code, message) => {
    const wrapper = mountChart({ data: { widgetId: 'w1', type: 'chart', status: 'error', error: { code, message: 'x' } } })
    expect(wrapper.get('[data-testid="widget-error"]').text()).toContain(message)
    expect(created.options).toHaveLength(0)
  })

  it('shows a note while the data is missing', () => {
    expect(mountChart({}).get('[data-testid="widget-no-data"]').text()).toBe('No data')
  })
})
