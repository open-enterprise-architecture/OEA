import { describe, expect, it } from 'vitest'
import type { ChartType } from '../api/dashboards'
import { chartOption, type ChartPoint, type ChartSettings } from './chart'
import { encodeHTML } from './chartRuntime'

const POINTS: ChartPoint[] = [
  { name: 'P3', value: 25000000 },
  { name: 'P2', value: 12000000 },
  { name: '(none)', value: 1000000 },
]

function settings(overrides: Partial<ChartSettings> = {}): ChartSettings {
  return {
    chartType: 'bar',
    showLegend: true,
    showDataLabels: false,
    seriesName: 'Investment per Plateau',
    formatValue: (value) => `${value} CHF`,
    dark: false,
    escape: encodeHTML,
    ...overrides,
  }
}

type Option = {
  legend: { show: boolean; data?: string[] }
  series: { type: string; data: unknown[]; radius?: unknown; areaStyle?: unknown; label: { show: boolean; formatter: (params: unknown) => string } }[]
  tooltip: { trigger: string; formatter: (params: unknown) => string }
  xAxis?: { data: string[] }
  color: string[]
}

function option(points: ChartPoint[], overrides: Partial<ChartSettings> = {}): Option {
  return chartOption(points, settings(overrides)) as unknown as Option
}

// spec dashboard/widgets "Chart widget" (US-057)
describe('chartOption', () => {
  it.each<[ChartType, string]>([
    ['bar', 'bar'],
    ['line', 'line'],
    ['area', 'line'],
    ['pie', 'pie'],
    ['donut', 'pie'],
    ['treemap', 'treemap'],
  ])('%s is a series of type %s', (chartType, seriesType) => {
    const result = option(POINTS, { chartType })
    expect(result.series).toHaveLength(1)
    expect(result.series[0]!.type).toBe(seriesType)
    expect(result.series[0]!.data).toHaveLength(3)
  })

  it('area is a line with an area style, and a line has none', () => {
    expect(option(POINTS, { chartType: 'area' }).series[0]!.areaStyle).toBeDefined()
    expect(option(POINTS, { chartType: 'line' }).series[0]!.areaStyle).toBeUndefined()
  })

  it('donut is a pie with an inner radius, and a pie has none', () => {
    expect(option(POINTS, { chartType: 'donut' }).series[0]!.radius).toEqual(['45%', '68%'])
    expect(option(POINTS, { chartType: 'pie' }).series[0]!.radius).toBe('68%')
  })

  it('bar, line and area use the groups as categories and values as data', () => {
    const result = option(POINTS, { chartType: 'bar' })
    expect(result.xAxis?.data).toEqual(['P3', 'P2', '(none)'])
    expect(result.series[0]!.data).toEqual([25000000, 12000000, 1000000])
  })

  it.each<[ChartType, boolean]>([
    ['bar', true],
    ['line', true],
    ['area', true],
    ['pie', true],
    ['donut', true],
    ['treemap', false],
  ])('the legend of %s is shown with the legend switched on: %s, and never with it off', (chartType, shown) => {
    expect(option(POINTS, { chartType, showLegend: true }).legend.show).toBe(shown)
    expect(option(POINTS, { chartType, showLegend: false }).legend.show).toBe(false)
  })

  it('the legend of a pie names the groups, the one of a bar chart the series', () => {
    expect(option(POINTS, { chartType: 'pie' }).legend.data).toEqual(['P3', 'P2', '(none)'])
    expect(option(POINTS, { chartType: 'bar' }).legend.data).toEqual(['Investment per Plateau'])
  })

  it.each<[ChartType]>([['bar'], ['line'], ['area'], ['pie'], ['donut']])('data labels of %s on and off', (chartType) => {
    expect(option(POINTS, { chartType, showDataLabels: true }).series[0]!.label.show).toBe(true)
    expect(option(POINTS, { chartType, showDataLabels: false }).series[0]!.label.show).toBe(false)
  })

  it('the data label shows the formatted value, and that of a pie also the name', () => {
    expect(option(POINTS, { chartType: 'bar', showDataLabels: true }).series[0]!.label.formatter({ value: 5 })).toBe('5 CHF')
    expect(option(POINTS, { chartType: 'pie', showDataLabels: true }).series[0]!.label.formatter({ name: 'P3', value: 5 })).toBe('P3: 5 CHF')
  })

  it('the theme changes the colors', () => {
    const light = option(POINTS, { dark: false })
    const dark = option(POINTS, { dark: true })
    expect(light.color).not.toEqual(dark.color)
    expect(light.color).toHaveLength(8)
  })

  it('the labels of the treemap contrast with the tiles in both themes', () => {
    const label = (dark: boolean) => (option(POINTS, { chartType: 'treemap', dark }).series[0] as unknown as { label: { color: string } }).label.color
    expect(label(false)).toBe('#ffffff')
    expect(label(true)).toBe('#111827')
  })

  it('an empty chart has no data', () => {
    expect(option([], { chartType: 'pie' }).series[0]!.data).toEqual([])
  })

  // spec "labels with markup are shown literally, in the tooltip as well"
  describe('tooltip', () => {
    const markup = '<img src=x onerror=alert(1)> & "q"'

    it.each<[ChartType, 'item' | 'axis']>([
      ['bar', 'axis'],
      ['pie', 'item'],
      ['treemap', 'item'],
    ])('%s shows the name and the formatted value, escaped (%s)', (chartType, trigger) => {
      const result = option([{ name: markup, value: 7 }], { chartType })
      expect(result.tooltip.trigger).toBe(trigger)
      const html = result.tooltip.formatter(trigger === 'axis' ? [{ name: markup, value: 7 }] : { name: markup, value: 7 })
      expect(html).toBe('&lt;img src=x onerror=alert(1)&gt; &amp; &quot;q&quot;: 7 CHF')
      expect(html).not.toContain('<img')
    })

    it('escapes the formatted value as well', () => {
      const result = option(POINTS, { formatValue: () => '<b>1</b>' })
      expect(result.tooltip.formatter({ name: 'x', value: 1 })).toBe('x: &lt;b&gt;1&lt;/b&gt;')
    })
  })
})
