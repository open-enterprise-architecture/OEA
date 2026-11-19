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

  describe('comparison with the value 30 days ago', () => {
    const PREVIOUS: WidgetSettings = { comparison: 'vs-previous-value', unit: 'CHF' }
    const AT = '2026-10-20T12:00:00Z'

    function compared(value: number | null, previousValue: number | null) {
      return mountKpi({ data: data({ value, target: undefined, previousValue, previousAt: AT }), config: PREVIOUS })
    }

    it.each([
      [42, 35, 'Increase: +7 CHF (+20%)', 'i-lucide-trending-up'],
      [42, 50, 'Decrease: -8 CHF (-16%)', 'i-lucide-trending-down'],
      [42, 42, 'Unchanged', 'i-lucide-equal'],
      [42, 0, 'Increase: +42 CHF', 'i-lucide-trending-up'],
      [0, 5, 'Decrease: -5 CHF (-100%)', 'i-lucide-trending-down'],
      [-3, -4, 'Increase: +1 CHF (+25%)', 'i-lucide-trending-up'],
      [0, 0, 'Unchanged', 'i-lucide-equal'],
    ])('value %d against the previous value %d: "%s"', (value, previous, text) => {
      setLanguage('en')
      const wrapper = compared(value, previous)
      expect(wrapper.get('[data-testid="kpi-previous"]').text()).toBe(`30 days ago (Oct 20, 2026): ${previous} CHF`)
      const trend = wrapper.get('[data-testid="kpi-trend"]')
      expect(trend.text()).toBe(text)
      // the arrow is an icon plus text, not color alone
      expect(trend.find('svg[aria-hidden="true"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="kpi-no-comparison"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="kpi-target"]').exists()).toBe(false)
    })

    it('uses a different icon for up, down and equal', () => {
      const icon = (value: number, previous: number) => compared(value, previous).get('[data-testid="kpi-trend"] svg').html()
      expect(new Set([icon(42, 35), icon(42, 50), icon(42, 42)]).size).toBe(3)
    })

    it('shows the date of the previous value as visible text, in the language, and without a tooltip', () => {
      setLanguage('en')
      const en = compared(42, 35).get('[data-testid="kpi-previous"]')
      expect(en.text()).toContain('(Oct 20, 2026)')
      expect(en.attributes('title')).toBeUndefined()
      setLanguage('de')
      const de = compared(42, 35).get('[data-testid="kpi-previous"]')
      expect(de.text()).toContain('(20.10.2026)')
      expect(de.attributes('title')).toBeUndefined()
      setLanguage('en')
    })

    it('shows the previous value without a date when the answer carries none', () => {
      setLanguage('en')
      const wrapper = mountKpi({ data: data({ value: 42, previousValue: 35, previousAt: undefined }), config: PREVIOUS })
      expect(wrapper.get('[data-testid="kpi-previous"]').text()).toBe('30 days ago: 35 CHF')
    })

    it('formats the numbers and the percentage in the language', () => {
      setLanguage('de')
      const wrapper = mountKpi({ data: data({ value: 42000000, previousValue: 35000000, previousAt: AT }), config: PREVIOUS })
      expect(wrapper.get('[data-testid="kpi-previous"]').text()).toBe('Vor 30 Tagen (20.10.2026): 35.000.000 CHF')
      expect(wrapper.get('[data-testid="kpi-trend"]').text()).toContain('+7.000.000 CHF')
      expect(wrapper.get('[data-testid="kpi-trend"]').text()).toMatch(/\+20\s?%/)
      setLanguage('en')
    })

    it('shows "No comparison value" without a previous value, and no trend', () => {
      setLanguage('en')
      const wrapper = compared(42, null)
      expect(wrapper.get('[data-testid="kpi-no-comparison"]').text()).toBe('No comparison value')
      expect(wrapper.get('[data-testid="kpi-value"]').text()).toBe('42 CHF')
      expect(wrapper.find('[data-testid="kpi-previous"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="kpi-trend"]').exists()).toBe(false)
    })

    it('shows the previous value but no trend when the current value has no data', () => {
      const wrapper = compared(null, 5)
      expect(wrapper.find('[data-testid="kpi-no-data"]').exists()).toBe(true)
      expect(wrapper.get('[data-testid="kpi-previous"]').text()).toBe('30 days ago (Oct 20, 2026): 5 CHF')
      expect(wrapper.find('[data-testid="kpi-trend"]').exists()).toBe(false)
    })

    it('shows nothing of the comparison for another comparison', () => {
      const wrapper = mountKpi({ data: data({ previousValue: 35, previousAt: AT }), config: { comparison: 'none' } })
      expect(wrapper.find('[data-testid="kpi-previous"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="kpi-no-comparison"]').exists()).toBe(false)
    })

    it('shows the error and no comparison for a restricted property', () => {
      const wrapper = mountKpi({ data: { widgetId: 'w1', type: 'kpi', status: 'error', error: { code: 'restricted', message: 'x' } }, config: PREVIOUS })
      expect(wrapper.find('[data-testid="widget-error"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="kpi-no-comparison"]').exists()).toBe(false)
    })
  })

  it('shows a note while the data is missing', () => {
    expect(mountKpi({}).get('[data-testid="widget-no-data"]').text()).toBe('No data')
  })
})
