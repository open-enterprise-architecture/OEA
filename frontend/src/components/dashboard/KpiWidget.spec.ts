import ui from '@nuxt/ui/vue-plugin'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { WidgetData, WidgetSettings } from '../../api/dashboards'
import { setLanguage } from '../../i18n'
import KpiWidget from './KpiWidget.vue'

const CONFIG: WidgetSettings = { comparison: 'vs-target', targetValue: 50, unit: 'CHF' }

function data(overrides: Partial<WidgetData> = {}): WidgetData {
  return { widgetId: 'w1', type: 'kpi', status: 'ok', value: 42, target: 50, ...overrides }
}

function mountKpi(props: { data?: WidgetData; config?: WidgetSettings }) {
  return mount(KpiWidget, { props: { config: CONFIG, ...props }, global: { plugins: [ui] } })
}

// spec dashboard/widgets "KPI widget" (US-056)
describe('KpiWidget', () => {
  it('shows the value in the number format of the language, with the unit', () => {
    setLanguage('en')
    expect(mountKpi({ data: data({ value: 42000000 }), config: { unit: 'CHF', comparison: 'none' } }).get('[data-testid="kpi-value"]').text()).toBe('42,000,000 CHF')
    setLanguage('de')
    expect(mountKpi({ data: data({ value: 42000000 }), config: { unit: 'CHF', comparison: 'none' } }).get('[data-testid="kpi-value"]').text()).toBe('42.000.000 CHF')
    setLanguage('en')
  })

  it('shows decimals up to two places and no unit without one', () => {
    expect(mountKpi({ data: data({ value: 1.5 }), config: { comparison: 'none' } }).get('[data-testid="kpi-value"]').text()).toBe('1.5')
    expect(mountKpi({ data: data({ value: 1.23456 }), config: { comparison: 'none' } }).get('[data-testid="kpi-value"]').text()).toBe('1.23')
  })

  it('shows zero as a value, not as no data', () => {
    const wrapper = mountKpi({ data: data({ value: 0 }), config: { comparison: 'none' } })
    expect(wrapper.get('[data-testid="kpi-value"]').text()).toBe('0')
    expect(wrapper.find('[data-testid="kpi-no-data"]').exists()).toBe(false)
  })

  it.each([
    [42, 50, '8 CHF below the target'],
    [60, 50, '10 CHF above the target'],
    [50, 50, 'On target'],
  ])('value %d against the target %d: "%s"', (value, target, text) => {
    const wrapper = mountKpi({ data: data({ value, target }) })
    expect(wrapper.get('[data-testid="kpi-target"]').text()).toBe(`Target: ${target} CHF`)
    const trend = wrapper.get('[data-testid="kpi-trend"]')
    expect(trend.text()).toBe(text)
    // the trend is an icon plus text, not color alone
    expect(trend.find('svg[aria-hidden="true"]').exists()).toBe(true)
  })

  it('shows no target and no trend without the comparison', () => {
    const wrapper = mountKpi({ data: data(), config: { comparison: 'none', targetValue: 50 } })
    expect(wrapper.find('[data-testid="kpi-target"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="kpi-trend"]').exists()).toBe(false)
  })

  it('shows "– (no data)" without an error when there is no value, and no trend', () => {
    const wrapper = mountKpi({ data: data({ value: null }) })
    expect(wrapper.get('[data-testid="kpi-no-data"]').text()).toBe('– (no data)')
    expect(wrapper.find('[data-testid="widget-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="kpi-trend"]').exists()).toBe(false)
  })

  it.each([
    ['restricted', 'The values of this property are not readable for every entity, so no aggregate is shown.'],
    ['invalid-configuration', 'The widget no longer fits the metamodel. Edit or remove it.'],
  ] as const)('shows the error %s', (code, message) => {
    const wrapper = mountKpi({ data: { widgetId: 'w1', type: 'kpi', status: 'error', error: { code, message: 'x' } } })
    expect(wrapper.get('[data-testid="widget-error"]').text()).toContain(message)
    expect(wrapper.find('[data-testid="kpi-value"]').exists()).toBe(false)
  })

  it('shows a note while the data is missing', () => {
    expect(mountKpi({}).get('[data-testid="widget-no-data"]').text()).toBe('No data')
  })
})
