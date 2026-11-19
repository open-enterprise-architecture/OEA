<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetData, WidgetSettings } from '../../api/dashboards'

// A KPI widget (US-056, design D6 of us-056-kpi-and-chart-widgets): the aggregated value with its
// unit and, with the comparison `vs-target`, the target, the difference and a trend that is an icon
// plus text, not color alone. With the comparison `vs-previous-value` (design D5 of
// us-056-kpi-previous-value) it shows the value of 30 days ago, the signed difference, the
// percentage (not for a previous value of 0) and a trend as icon plus text, or "No comparison
// value" if no entity had a value then. Without data it shows "– (no data)"; a widget the reader may not
// aggregate (`restricted`) or that no longer fits the metamodel shows its message.
const props = defineProps<{ data?: WidgetData; config: WidgetSettings }>()
const { t, locale } = useI18n()

const numbers = computed(() => new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }))
const unit = computed(() => (props.config.unit ? ` ${props.config.unit}` : ''))

function format(value: number): string {
  return `${numbers.value.format(value)}${unit.value}`
}

const value = computed(() => props.data?.value ?? undefined)
const target = computed(() => (props.config.comparison === 'vs-target' ? (props.data?.target ?? props.config.targetValue ?? undefined) : undefined))

const signed = computed(() => new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2, signDisplay: 'exceptZero' }))
const percent = computed(() => new Intl.NumberFormat(locale.value, { style: 'percent', maximumFractionDigits: 1, signDisplay: 'exceptZero' }))
const day = computed(() => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }))

const comparesWithPrevious = computed(() => props.config.comparison === 'vs-previous-value')
const previous = computed(() => props.data?.previousValue ?? undefined)
/** The date of the previous value, formatted locally and shown as text next to "30 days ago" (design D5 of us-061-portal-dashboards). */
const previousDate = computed(() => (props.data?.previousAt ? day.value.format(new Date(props.data.previousAt)) : undefined))

/** The change against the value of 30 days ago: icon, text with the signed difference and, unless that value is 0, the percentage. */
const change = computed(() => {
  if (value.value === undefined || previous.value === undefined) {
    return undefined
  }
  const difference = value.value - previous.value
  if (difference === 0) {
    return { icon: 'i-lucide-equal', text: t('dashboard.widget.kpi.unchanged') }
  }
  const amount = `${signed.value.format(difference)}${unit.value}`
  const change = previous.value === 0 ? amount : `${amount} (${percent.value.format(difference / Math.abs(previous.value))})`
  return difference > 0
    ? { icon: 'i-lucide-trending-up', text: t('dashboard.widget.kpi.increase', { change }) }
    : { icon: 'i-lucide-trending-down', text: t('dashboard.widget.kpi.decrease', { change }) }
})

/** The trend against the target: above, below or on it. */
const trend = computed(() => {
  if (value.value === undefined || target.value === undefined) {
    return undefined
  }
  const difference = value.value - target.value
  if (difference === 0) {
    return { icon: 'i-lucide-equal', text: t('dashboard.widget.kpi.onTarget') }
  }
  return difference > 0
    ? { icon: 'i-lucide-trending-up', text: t('dashboard.widget.kpi.above', { difference: format(difference) }) }
    : { icon: 'i-lucide-trending-down', text: t('dashboard.widget.kpi.below', { difference: format(-difference) }) }
})
</script>

<template>
  <div class="flex h-full flex-col justify-center gap-1 text-sm">
    <UAlert
      v-if="props.data?.status === 'error'"
      data-testid="widget-error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="props.data.error ? t(`dashboard.widget.error.${props.data.error.code}`) : t('dashboard.widget.noData')"
    />
    <p v-else-if="!props.data" data-testid="widget-no-data" class="text-muted">{{ t('dashboard.widget.noData') }}</p>
    <template v-else>
      <p v-if="value === undefined" data-testid="kpi-no-data" class="text-3xl font-semibold text-muted">{{ t('dashboard.widget.kpi.noData') }}</p>
      <p v-else data-testid="kpi-value" class="text-3xl font-semibold text-highlighted">{{ format(value) }}</p>
      <template v-if="target !== undefined">
        <p data-testid="kpi-target" class="text-muted">{{ t('dashboard.widget.kpi.target', { target: format(target) }) }}</p>
        <p v-if="trend" data-testid="kpi-trend" class="inline-flex items-center gap-1 text-default">
          <UIcon :name="trend.icon" class="size-4" aria-hidden="true" />
          {{ trend.text }}
        </p>
      </template>
      <template v-if="comparesWithPrevious">
        <template v-if="previous !== undefined">
          <p data-testid="kpi-previous" class="text-muted">
            {{
              previousDate
                ? t('dashboard.widget.kpi.previousOn', { date: previousDate, previous: format(previous) })
                : t('dashboard.widget.kpi.previous', { previous: format(previous) })
            }}
          </p>
          <p v-if="change" data-testid="kpi-trend" class="inline-flex items-center gap-1 text-default">
            <UIcon :name="change.icon" class="size-4" aria-hidden="true" />
            {{ change.text }}
          </p>
        </template>
        <p v-else data-testid="kpi-no-comparison" class="text-muted">{{ t('dashboard.widget.kpi.noComparison') }}</p>
      </template>
    </template>
  </div>
</template>
