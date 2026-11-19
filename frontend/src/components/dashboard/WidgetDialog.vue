<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchCatalog, fetchCatalogs, type CatalogSavedView, type CatalogSummary } from '../../api/catalogs'
import { LIMITS, type ChartType, type WidgetConfig, type WidgetGroupBy, type WidgetType, type WidgetWrite } from '../../api/dashboards'
import { fetchMetatypes, type MetatypeDetail, type PropertyDefinitionDetail } from '../../api/metamodel'
import { RequestRejected } from '../../api/problem'

// Add and edit a widget (US-058, US-059; design D4 of us-055-dashboards). A text widget has Markdown
// content, a table widget a catalog, optionally one of its saved views, and a page size. A KPI or
// chart widget (US-056, US-057; design D6 of us-056-kpi-and-chart-widgets) aggregates the entities of
// an element type: the function, a property that fits it, and for a chart the grouping. The type
// of an existing widget does not change. A shared dashboard offers shared catalogs only; the server
// rejects the others with 422 naming catalogId.
const props = defineProps<{
  fetchFn?: typeof fetch
  /** the widget to edit, or undefined to add one */
  widget?: WidgetConfig
  /** whether the dashboard is shared: only shared catalogs fit */
  shared: boolean
  /** where a new widget goes */
  position: { col: number; row: number; width: number; height: number }
  /** the rejection of the last submit, shown at its fields */
  rejection?: { invalidFields: string[]; forbidden: boolean }
  submitting?: boolean
}>()
const emit = defineEmits<{ submit: [widget: WidgetWrite]; cancel: [] }>()
const { t } = useI18n()

const NO_VIEW = '__none__'
const NO_PROPERTY = '__none__'
const ALL_TYPES = '__all__'
/** The data types a chart can group by. */
const GROUPABLE: string[] = ['enum', 'string', 'boolean', 'reference']

function groupValue(groupBy: WidgetGroupBy | null | undefined): string {
  if (!groupBy) {
    return ''
  }
  return groupBy.kind === 'property' ? `property:${groupBy.propertyDefId ?? ''}` : groupBy.kind
}

function groupOf(value: string): WidgetGroupBy {
  return value.startsWith('property:') ? { kind: 'property', propertyDefId: value.slice('property:'.length) } : { kind: value as 'metatype' | 'layer' }
}

type Field =
  | 'title'
  | 'content'
  | 'catalogId'
  | 'savedViewId'
  | 'pageSize'
  | 'metatypeId'
  | 'propertyDefId'
  | 'function'
  | 'groupBy'
  | 'unit'
  | 'targetValue'
  | 'chartType'
  | 'comparison'

const FIELDS: Field[] = ['title', 'content', 'catalogId', 'savedViewId', 'pageSize', 'metatypeId', 'propertyDefId', 'function', 'groupBy', 'unit', 'targetValue', 'chartType', 'comparison']

type AggregationFunction = 'count' | 'sum' | 'avg' | 'min' | 'max'

const aggregation = props.widget?.config.aggregation

const form = reactive<{
  type: WidgetType
  title: string
  content: string
  catalogId?: string
  savedViewId: string
  pageSize: number
  metatypeId: string
  function: AggregationFunction
  propertyDefId: string
  groupBy: string
  unit: string
  comparison: 'none' | 'vs-target'
  targetValue: number | undefined
  chartType: ChartType
  showLegend: boolean
  showDataLabels: boolean
}>({
  type: props.widget?.type ?? 'text',
  title: props.widget?.title ?? '',
  content: props.widget?.config.content ?? '',
  catalogId: props.widget?.config.catalogId,
  savedViewId: props.widget?.config.savedViewId ?? '',
  pageSize: props.widget?.config.pageSize ?? LIMITS.defaultPageSize,
  // a new widget starts with all entities, counted (and grouped by element type for a chart)
  metatypeId: aggregation?.metatypeId ?? ALL_TYPES,
  function: aggregation?.function ?? 'count',
  propertyDefId: aggregation?.propertyDefId ?? NO_PROPERTY,
  groupBy: aggregation ? groupValue(aggregation.groupBy) : 'metatype',
  unit: props.widget?.config.unit ?? '',
  comparison: props.widget?.config.comparison === 'vs-target' ? 'vs-target' : 'none',
  targetValue: props.widget?.config.targetValue ?? undefined,
  chartType: props.widget?.config.chartType ?? 'bar',
  showLegend: props.widget?.config.showLegend ?? true,
  showDataLabels: props.widget?.config.showDataLabels ?? false,
})
const errors = ref<Partial<Record<Field, string>>>({})
const catalogs = ref<CatalogSummary[]>([])
const views = ref<CatalogSavedView[]>([])
const metatypes = ref<MetatypeDetail[]>([])
const failure = ref<string>()

const typeItems = computed(() => [
  { label: t('widgetDialog.text'), description: t('widgetDialog.textHint'), value: 'text' },
  { label: t('widgetDialog.table'), description: t('widgetDialog.tableHint'), value: 'table' },
  { label: t('widgetDialog.kpi'), description: t('widgetDialog.kpiHint'), value: 'kpi' },
  { label: t('widgetDialog.chart'), description: t('widgetDialog.chartHint'), value: 'chart' },
])
const aggregating = computed(() => form.type === 'kpi' || form.type === 'chart')
const allTypes = computed(() => form.metatypeId === ALL_TYPES)

/** The properties mapped to the chosen element type, base types included. */
const mappedProperties = computed<PropertyDefinitionDetail[]>(() => {
  const metatype = metatypes.value.find((candidate) => candidate.id === form.metatypeId)
  return (metatype?.propertyMappings ?? []).map((mapping) => mapping.propertyDefinition)
})
const functionItems = computed(() =>
  (['count', 'sum', 'avg', 'min', 'max'] as const).map((value) => ({
    label: t(`widgetDialog.functions.${value}`),
    value,
    disabled: value !== 'count' && allTypes.value,
  })),
)
// sum, average, minimum and maximum need a number property; a count takes any property or none
const propertyItems = computed(() => {
  const fitting = form.function === 'count' ? mappedProperties.value : mappedProperties.value.filter((property) => property.dataType === 'integer')
  return [
    ...(form.function === 'count' ? [{ label: t('widgetDialog.noProperty'), value: NO_PROPERTY }] : []),
    ...fitting.map((property) => ({ label: property.label ?? property.name, value: property.id })),
  ]
})
const groupItems = computed(() => [
  { label: t('widgetDialog.groupMetatype'), value: 'metatype' },
  { label: t('widgetDialog.groupLayer'), value: 'layer' },
  ...(allTypes.value
    ? []
    : mappedProperties.value
        .filter((property) => GROUPABLE.includes(property.dataType))
        .map((property) => ({ label: t('widgetDialog.groupProperty', { name: property.label ?? property.name }), value: `property:${property.id}` }))),
])
const metatypeItems = computed(() => [
  ...(form.function === 'count' && form.propertyDefId === NO_PROPERTY ? [{ label: t('widgetDialog.allElementTypes'), value: ALL_TYPES }] : []),
  ...metatypes.value.filter((metatype) => !metatype.isConnection).map((metatype) => ({ label: metatype.name, value: metatype.id })),
])
const chartTypeItems = computed(() =>
  (['bar', 'line', 'area', 'pie', 'donut', 'treemap'] as const).map((value) => ({ label: t(`widgetDialog.chartTypes.${value}`), value })),
)
const comparisonItems = computed(() => [
  { label: t('widgetDialog.comparisonNone'), value: 'none' },
  { label: t('widgetDialog.comparisonTarget'), value: 'vs-target' },
])
const catalogItems = computed(() =>
  catalogs.value.filter((catalog) => !props.shared || catalog.scope === 'instance').map((catalog) => ({ label: catalog.name, value: catalog.id })),
)
const viewItems = computed(() => [
  { label: t('widgetDialog.noView'), value: NO_VIEW },
  ...views.value.map((view) => ({ label: view.name, value: view.id })),
])

async function loadViews(catalogId: string | undefined) {
  views.value = []
  if (!catalogId) {
    return
  }
  try {
    views.value = (await fetchCatalog(catalogId, props.fetchFn))?.savedViews ?? []
  } catch {
    views.value = []
  }
}

let metamodelRequested = false

async function loadMetamodel() {
  if (metamodelRequested) {
    return
  }
  metamodelRequested = true
  try {
    metatypes.value = await fetchMetatypes(props.fetchFn)
  } catch {
    failure.value = t('widgetDialog.metamodelFailed')
  }
}

onMounted(async () => {
  if (aggregating.value) {
    void loadMetamodel()
  }
  try {
    catalogs.value = await fetchCatalogs(props.fetchFn)
  } catch (error) {
    failure.value = error instanceof RequestRejected || error instanceof Error ? t('widgetDialog.catalogsFailed') : undefined
  }
  await loadViews(form.catalogId)
})

watch(aggregating, (value) => {
  if (value) {
    void loadMetamodel()
  }
})

// what no longer fits the element type or the function is dropped from the form
watch(
  () => [form.metatypeId, form.function, metatypes.value] as const,
  () => {
    if (metatypes.value.length === 0) {
      return
    }
    if (allTypes.value && (form.function !== 'count' || form.propertyDefId !== NO_PROPERTY)) {
      form.function = 'count'
      form.propertyDefId = NO_PROPERTY
    }
    if (!propertyItems.value.some((item) => item.value === form.propertyDefId)) {
      form.propertyDefId = form.function === 'count' ? NO_PROPERTY : ''
    }
    if (form.groupBy && !groupItems.value.some((item) => item.value === form.groupBy)) {
      form.groupBy = ''
    }
  },
)

watch(
  () => form.catalogId,
  async (catalogId, previous) => {
    if (previous !== undefined || catalogId !== props.widget?.config.catalogId) {
      form.savedViewId = ''
    }
    await loadViews(catalogId)
  },
)

// the rejection of the server lands at its fields; a nested field is named by its last part
watch(
  () => props.rejection,
  (rejection) => {
    if (!rejection) {
      return
    }
    failure.value = rejection.forbidden ? t('widgetDialog.catalogForbidden') : undefined
    const found: Partial<Record<Field, string>> = {}
    for (const name of rejection.invalidFields) {
      const field = name.split('.').pop() as string
      if (field === 'aggregation') {
        found.metatypeId = t('widgetDialog.field.aggregation')
      } else if (FIELDS.includes(field as Field)) {
        found[field as Field] = t(`widgetDialog.field.${field}`)
      } else if (['type', 'widgets', 'position', 'width'].includes(field)) {
        failure.value = t(`widgetDialog.field.${field}`)
      }
    }
    errors.value = found
  },
)

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const title = form.title.trim()
  if (title.length === 0 || title.length > LIMITS.title) {
    found.title = t('widgetDialog.field.title')
  }
  if (form.type === 'text' && form.content.length > LIMITS.content) {
    found.content = t('widgetDialog.field.content')
  }
  if (aggregating.value) {
    Object.assign(found, validateAggregation())
  }
  if (form.type === 'table') {
    if (!form.catalogId) {
      found.catalogId = t('widgetDialog.field.catalogId')
    }
    if (!Number.isInteger(form.pageSize) || form.pageSize < LIMITS.minPageSize || form.pageSize > LIMITS.maxPageSize) {
      found.pageSize = t('widgetDialog.field.pageSize')
    }
  }
  return found
}

function validateAggregation(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  if (!form.metatypeId) {
    found.metatypeId = t('widgetDialog.field.metatypeId')
  }
  if (form.function !== 'count' && (!form.propertyDefId || form.propertyDefId === NO_PROPERTY)) {
    found.propertyDefId = t('widgetDialog.field.propertyDefId')
  }
  if (form.type === 'chart') {
    if (!form.groupBy) {
      found.groupBy = t('widgetDialog.field.groupBy')
    }
    if (!form.chartType) {
      found.chartType = t('widgetDialog.field.chartType')
    }
  }
  if (form.unit.trim().length > LIMITS.unit) {
    found.unit = t('widgetDialog.field.unit')
  }
  if (form.type === 'kpi' && form.comparison === 'vs-target' && (typeof form.targetValue !== 'number' || !Number.isFinite(form.targetValue))) {
    found.targetValue = t('widgetDialog.field.targetValue')
  }
  return found
}

function aggregationConfig(): WidgetWrite['config'] {
  const unit = form.unit.trim()
  const common = {
    aggregation: {
      function: form.function,
      metatypeId: allTypes.value ? null : form.metatypeId,
      propertyDefId: form.propertyDefId === NO_PROPERTY ? null : form.propertyDefId,
      ...(form.type === 'chart' ? { groupBy: groupOf(form.groupBy) } : {}),
    },
    ...(unit ? { unit } : {}),
  }
  if (form.type === 'chart') {
    return { ...common, chartType: form.chartType, showLegend: form.showLegend, showDataLabels: form.showDataLabels }
  }
  return {
    ...common,
    comparison: form.comparison,
    ...(form.comparison === 'vs-target' ? { targetValue: form.targetValue } : {}),
  }
}

function configOf(): WidgetWrite['config'] {
  if (form.type === 'text') {
    return { content: form.content }
  }
  if (aggregating.value) {
    return aggregationConfig()
  }
  return {
    catalogId: form.catalogId,
    ...(form.savedViewId && form.savedViewId !== NO_VIEW ? { savedViewId: form.savedViewId } : {}),
    pageSize: form.pageSize,
  }
}

function submit() {
  failure.value = undefined
  errors.value = validate()
  if (Object.keys(errors.value).length > 0) {
    return
  }
  const position = props.widget?.position ?? props.position
  emit('submit', {
    type: form.type,
    title: form.title.trim(),
    position: { ...position },
    config: configOf(),
  })
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="widget-form" @submit.prevent="submit">
    <UAlert v-if="failure" data-testid="widget-failure" color="error" variant="subtle" :description="failure" />

    <UFormField v-if="!props.widget" :label="t('widgetDialog.type')" name="type">
      <URadioGroup v-model="form.type" data-testid="widget-type" orientation="horizontal" :items="typeItems" />
    </UFormField>
    <UFormField :label="t('widgetDialog.title')" name="title" :error="errors.title" required>
      <UInput v-model="form.title" data-testid="widget-title" class="w-full" autofocus />
    </UFormField>

    <template v-if="form.type === 'text'">
      <UFormField
        :label="t('widgetDialog.content')"
        name="content"
        :error="errors.content"
        :help="t('widgetDialog.contentHelp', { count: form.content.length })"
      >
        <UTextarea v-model="form.content" data-testid="widget-content" :rows="6" class="w-full" />
      </UFormField>
    </template>
    <template v-else-if="aggregating">
      <UFormField :label="t('widgetDialog.metatype')" name="metatypeId" :error="errors.metatypeId" required>
        <USelect
          v-model="form.metatypeId"
          data-testid="widget-metatype"
          :items="metatypeItems"
          :placeholder="t('widgetDialog.metatypePlaceholder')"
          class="w-full"
        />
      </UFormField>
      <UFormField :label="t('widgetDialog.function')" name="function" :error="errors.function" required>
        <USelect v-model="form.function" data-testid="widget-function" :items="functionItems" class="w-full" />
      </UFormField>
      <UFormField :label="t('widgetDialog.property')" name="propertyDefId" :error="errors.propertyDefId" :help="t('widgetDialog.propertyHelp')">
        <USelect
          v-model="form.propertyDefId"
          data-testid="widget-property"
          :items="propertyItems"
          :disabled="allTypes || !form.metatypeId"
          class="w-full"
        />
      </UFormField>
      <UFormField v-if="form.type === 'chart'" :label="t('widgetDialog.groupBy')" name="groupBy" :error="errors.groupBy" required>
        <USelect
          v-model="form.groupBy"
          data-testid="widget-group-by"
          :items="groupItems"
          :placeholder="t('widgetDialog.groupPlaceholder')"
          class="w-full"
        />
      </UFormField>
      <UFormField v-if="form.type === 'chart'" :label="t('widgetDialog.chartType')" name="chartType" :error="errors.chartType" required>
        <USelect v-model="form.chartType" data-testid="widget-chart-type" :items="chartTypeItems" class="w-full" />
      </UFormField>
      <UFormField :label="t('widgetDialog.unit')" name="unit" :error="errors.unit" :help="t('widgetDialog.unitHelp')">
        <UInput v-model="form.unit" data-testid="widget-unit" class="w-full" />
      </UFormField>
      <template v-if="form.type === 'kpi'">
        <UFormField :label="t('widgetDialog.comparison')" name="comparison" :error="errors.comparison">
          <USelect v-model="form.comparison" data-testid="widget-comparison" :items="comparisonItems" class="w-full" />
        </UFormField>
        <UFormField v-if="form.comparison === 'vs-target'" :label="t('widgetDialog.targetValue')" name="targetValue" :error="errors.targetValue" required>
          <UInputNumber v-model="form.targetValue" data-testid="widget-target" class="w-48" />
        </UFormField>
      </template>
      <template v-else>
        <UCheckbox v-model="form.showLegend" data-testid="widget-show-legend" :label="t('widgetDialog.showLegend')" />
        <UCheckbox v-model="form.showDataLabels" data-testid="widget-show-data-labels" :label="t('widgetDialog.showDataLabels')" />
      </template>
    </template>
    <template v-else>
      <UFormField :label="t('widgetDialog.catalog')" name="catalogId" :error="errors.catalogId" required>
        <USelect
          v-model="form.catalogId"
          data-testid="widget-catalog"
          :items="catalogItems"
          :placeholder="t('widgetDialog.catalogPlaceholder')"
          class="w-full"
        />
      </UFormField>
      <UFormField :label="t('widgetDialog.view')" name="savedViewId" :error="errors.savedViewId" :help="t('widgetDialog.viewHelp')">
        <USelect
          :model-value="form.savedViewId || NO_VIEW"
          data-testid="widget-view"
          :items="viewItems"
          :disabled="!form.catalogId"
          class="w-full"
          @update:model-value="(value: string) => (form.savedViewId = value)"
        />
      </UFormField>
      <UFormField :label="t('widgetDialog.pageSize')" name="pageSize" :error="errors.pageSize" :help="t('widgetDialog.pageSizeHelp')">
        <UInputNumber v-model="form.pageSize" data-testid="widget-page-size" :min="LIMITS.minPageSize" :max="LIMITS.maxPageSize" class="w-40" />
      </UFormField>
    </template>

    <div class="flex justify-end gap-2">
      <UButton data-testid="widget-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton
        type="submit"
        data-testid="widget-submit"
        :loading="props.submitting"
        :label="props.widget ? t('widgetDialog.submitSave') : t('widgetDialog.submitAdd')"
      />
    </div>
  </form>
</template>
