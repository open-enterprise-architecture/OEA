<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetData, WidgetSettings } from '../../api/dashboards'

// A KPI widget (US-056, design D6 of us-056-kpi-and-chart-widgets): the aggregated value with its
// unit and, with the comparison `vs-target`, the target, the difference and a trend that is an icon
// plus text, not color alone. Without data it shows "– (no data)"; a widget the reader may not
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
    </template>
  </div>
</template>
