import type { EChartsCoreOption } from 'echarts/core'
import type { ChartType } from '../api/dashboards'

// The options of the chart widget (US-057, design D6 of us-056-kpi-and-chart-widgets). A pure
// function of the groups and the settings, so that every chart type is tested without a chart.

/** One group of a chart with its name already translated. */
export interface ChartPoint {
  name: string
  value: number
}

export interface ChartSettings {
  chartType: ChartType
  showLegend: boolean
  showDataLabels: boolean
  /** the name of the series, shown in the legend of bar, line and area charts */
  seriesName: string
  /** the value as shown: number format of the locale and the unit */
  formatValue: (value: number) => string
  dark: boolean
  /** escapes text for the HTML of the tooltip */
  escape: (text: string) => string
}

/** Colors that keep their contrast on the light and the dark surface. */
const PALETTE = {
  light: ['#1d4ed8', '#b45309', '#047857', '#6d28d9', '#be185d', '#0e7490', '#4d7c0f', '#b91c1c'],
  dark: ['#60a5fa', '#fbbf24', '#34d399', '#a78bfa', '#f472b6', '#22d3ee', '#a3e635', '#f87171'],
}

const TEXT = { light: '#374151', dark: '#e5e7eb' }

const GRID = { light: '#d1d5db', dark: '#4b5563' }

/** The ECharts option of the groups. */
export function chartOption(points: ChartPoint[], settings: ChartSettings): EChartsCoreOption {
  const mode = settings.dark ? 'dark' : 'light'
  const base = {
    animation: false,
    color: PALETTE[mode],
    textStyle: { color: TEXT[mode] },
    legend: settings.showLegend && settings.chartType !== 'treemap' ? legend(points, settings, mode) : { show: false },
  }
  switch (settings.chartType) {
    case 'pie':
    case 'donut':
      return { ...base, tooltip: tooltip('item', settings), series: [pie(points, settings, mode)] }
    case 'treemap':
      return { ...base, tooltip: tooltip('item', settings), series: [treemap(points, settings)] }
    default:
      return { ...base, ...cartesian(points, settings, mode) }
  }
}

function legend(points: ChartPoint[], settings: ChartSettings, mode: 'light' | 'dark') {
  const pie = settings.chartType === 'pie' || settings.chartType === 'donut'
  return {
    show: true,
    bottom: 0,
    type: 'scroll',
    textStyle: { color: TEXT[mode] },
    data: pie ? points.map((point) => point.name) : [settings.seriesName],
  }
}

function tooltip(trigger: 'item' | 'axis', settings: ChartSettings) {
  return {
    trigger,
    // the tooltip is HTML: the name of a group is data and is escaped
    formatter: (params: unknown) => {
      const first = (Array.isArray(params) ? params[0] : params) as { name?: string; value?: unknown } | undefined
      const value = typeof first?.value === 'number' ? first.value : Number(first?.value)
      return `${settings.escape(first?.name ?? '')}: ${settings.escape(settings.formatValue(value))}`
    },
  }
}

function dataLabel(settings: ChartSettings) {
  return {
    show: settings.showDataLabels,
    formatter: (params: { name: string; value: unknown }) => `${params.name}: ${settings.formatValue(Number(params.value))}`,
  }
}

function pie(points: ChartPoint[], settings: ChartSettings, mode: 'light' | 'dark') {
  return {
    type: 'pie',
    radius: settings.chartType === 'donut' ? ['45%', '68%'] : '68%',
    center: ['50%', settings.showLegend ? '45%' : '50%'],
    data: points.map((point) => ({ name: point.name, value: point.value })),
    label: { ...dataLabel(settings), color: TEXT[mode] },
    labelLine: { show: settings.showDataLabels },
    itemStyle: { borderColor: settings.dark ? '#111827' : '#ffffff', borderWidth: 1 },
  }
}

function treemap(points: ChartPoint[], settings: ChartSettings) {
  return {
    type: 'treemap',
    roam: false,
    nodeClick: false,
    breadcrumb: { show: false },
    width: '100%',
    height: '100%',
    data: points.map((point) => ({ name: point.name, value: point.value })),
    // the text sits on the colored tiles: light on the dark palette of the light theme, dark on the light palette
    label: { show: true, color: settings.dark ? '#111827' : '#ffffff', formatter: (params: { name: string; value: unknown }) => (settings.showDataLabels ? `${params.name}\n${settings.formatValue(Number(params.value))}` : params.name) },
    itemStyle: { borderColor: settings.dark ? '#111827' : '#ffffff', borderWidth: 1 },
  }
}

function cartesian(points: ChartPoint[], settings: ChartSettings, mode: 'light' | 'dark') {
  const line = settings.chartType === 'line' || settings.chartType === 'area'
  return {
    tooltip: tooltip('axis', settings),
    grid: { left: 8, right: 16, top: 16, bottom: settings.showLegend ? 40 : 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.name),
      axisLabel: { interval: 0, width: 90, overflow: 'truncate', color: TEXT[mode] },
      axisLine: { lineStyle: { color: GRID[mode] } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: TEXT[mode] },
      splitLine: { lineStyle: { color: GRID[mode] } },
    },
    series: [
      {
        type: line ? 'line' : 'bar',
        name: settings.seriesName,
        data: points.map((point) => point.value),
        ...(settings.chartType === 'area' ? { areaStyle: {} } : {}),
        label: {
          show: settings.showDataLabels,
          position: 'top',
          color: TEXT[mode],
          formatter: (params: { value: unknown }) => settings.formatValue(Number(params.value)),
        },
      },
    ],
  }
}
