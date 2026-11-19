<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetData, WidgetSeriesEntry, WidgetSettings } from '../../api/dashboards'
import { chartOption, type ChartPoint } from '../../dashboard/chart'
import { createChart, encodeHTML, type ChartHandle } from '../../dashboard/chartRuntime'
import { useDarkMode } from '../../dashboard/useDarkMode'
import { label } from '../../i18n/labels'

// A chart widget (US-057, design D6 of us-056-kpi-and-chart-widgets): one aggregation grouped into
// bars, lines, areas, pie or donut segments, or a treemap, drawn by Apache ECharts. The names of
// the groups are translated here: with the label bundle for enum values, metatypes and layers, and
// by the client for "(none)", "Other", "Yes" and "No". A table with the same data is there for
// screen readers; the chart itself is an image with a text alternative.
const props = defineProps<{ data?: WidgetData; config: WidgetSettings; title: string }>()
const { t, locale } = useI18n()

const element = ref<HTMLElement>()
const dark = useDarkMode()
let chart: ChartHandle | undefined
let resizer: ResizeObserver | undefined

const numbers = computed(() => new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }))
const unit = computed(() => (props.config.unit ? ` ${props.config.unit}` : ''))

function format(value: number): string {
  return `${numbers.value.format(value)}${unit.value}`
}

/** The translated name of a group. */
function nameOf(entry: WidgetSeriesEntry): string {
  switch (entry.kind) {
    case 'none':
      return t('dashboard.widget.chart.none')
    case 'other':
      return t('dashboard.widget.chart.other')
    case 'true':
      return t('dashboard.widget.chart.yes')
    case 'false':
      return t('dashboard.widget.chart.no')
    default:
      return entry.labelKey ? label(entry.labelKey, entry.label ?? '') : (entry.label ?? '')
  }
}

const points = computed<ChartPoint[]>(() => (props.data?.series ?? []).map((entry) => ({ name: nameOf(entry), value: entry.value })))
const shown = computed(() => props.data?.status === 'ok' && points.value.length > 0)

function option() {
  return chartOption(points.value, {
    chartType: props.config.chartType ?? 'bar',
    showLegend: props.config.showLegend !== false,
    showDataLabels: props.config.showDataLabels === true,
    seriesName: props.title,
    formatValue: format,
    dark: dark.value,
    escape: encodeHTML,
  })
}

function draw() {
  if (!element.value || !shown.value) {
    return
  }
  if (!chart) {
    chart = createChart(element.value)
    if (typeof ResizeObserver !== 'undefined') {
      resizer = new ResizeObserver(() => chart?.resize())
      resizer.observe(element.value)
    }
  }
  chart.setOption(option())
}

onMounted(draw)

watch([points, () => props.config, dark, locale], draw, { deep: true, flush: 'post' })
// the chart element goes away without data and is created again when data comes
watch(shown, (visible) => {
  if (!visible) {
    resizer?.disconnect()
    chart?.dispose()
    chart = undefined
  }
})

onBeforeUnmount(() => {
  resizer?.disconnect()
  chart?.dispose()
  chart = undefined
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-col gap-2 text-sm">
    <UAlert
      v-if="props.data?.status === 'error'"
      data-testid="widget-error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="props.data.error ? t(`dashboard.widget.error.${props.data.error.code}`) : t('dashboard.widget.noData')"
    />
    <p v-else-if="!props.data" data-testid="widget-no-data" class="text-muted">{{ t('dashboard.widget.noData') }}</p>
    <p v-else-if="!shown" data-testid="chart-no-groups" class="text-muted">{{ t('dashboard.widget.chart.noGroups') }}</p>
    <template v-else>
      <div
        ref="element"
        data-testid="chart"
        role="img"
        class="min-h-40 w-full flex-1"
        :aria-label="t('dashboard.widget.chart.region', { title: props.title })"
      />
      <table data-testid="chart-table" class="sr-only">
        <caption>{{ t('dashboard.widget.chart.tableCaption', { title: props.title }) }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ t('dashboard.widget.chart.group') }}</th>
            <th scope="col">{{ t('dashboard.widget.chart.value') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(point, index) in points" :key="index">
            <td>{{ point.name }}</td>
            <td>{{ format(point.value) }}</td>
          </tr>
        </tbody>
      </table>
    </template>
  </div>
</template>
