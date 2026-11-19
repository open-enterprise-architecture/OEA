import { BarChart, LineChart, PieChart, TreemapChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { format, init, use, type EChartsCoreOption } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'

// Apache ECharts, imported modularly (US-057, ADR-043): only the charts and components the chart
// widget needs, rendered as SVG so that the text of a chart stays text.
use([BarChart, LineChart, PieChart, TreemapChart, GridComponent, LegendComponent, TooltipComponent, SVGRenderer])

/** A chart that shows an option; the chart widget works through this, so that specs can replace it. */
export interface ChartHandle {
  setOption(option: EChartsCoreOption): void
  resize(): void
  dispose(): void
}

/** Creates a chart in the element. */
export function createChart(element: HTMLElement): ChartHandle {
  const chart = init(element, undefined, { renderer: 'svg' })
  return {
    setOption: (option) => chart.setOption(option, true),
    resize: () => chart.resize(),
    dispose: () => chart.dispose(),
  }
}

/** Escapes text for the HTML tooltip of ECharts, which sets `innerHTML`. */
export function encodeHTML(text: string): string {
  return format.encodeHTML(text)
}
