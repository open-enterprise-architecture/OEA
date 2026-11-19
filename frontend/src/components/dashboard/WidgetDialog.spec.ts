import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { CATALOGS } from '../../test/catalogs'
import {
  APPLICATION_ID,
  BAR_WIDGET,
  CRITICAL_ID,
  dashboardApi,
  GO_LIVE_ID,
  INVESTMENT_ID,
  KPI_INCREASED_WIDGET,
  KPI_WIDGET,
  ME_MANAGER,
  PLATEAU_ID,
  TABLE_WIDGET,
  TEXT_WIDGET,
  VENDOR_ID,
} from '../../test/dashboards'
import WidgetDialog from './WidgetDialog.vue'

const POSITION = { col: 1, row: 4, width: 12, height: 2 }

async function mountDialog(props: Record<string, unknown> = {}) {
  const fetchFn = dashboardApi({ me: ME_MANAGER })
  const wrapper = mount(WidgetDialog, { props: { fetchFn, shared: true, position: POSITION, ...props }, global: { plugins: [ui] } })
  await flushPromises()
  return wrapper
}

function select(wrapper: Awaited<ReturnType<typeof mountDialog>>, testid: string) {
  return wrapper.findAllComponents({ name: 'Select' }).find((component) => component.vm.$attrs['data-testid'] === testid)!
}

async function type(wrapper: Awaited<ReturnType<typeof mountDialog>>, value: 'text' | 'table' | 'kpi' | 'chart') {
  await wrapper.get(`[data-testid="widget-type"] [value="${value}"]`).trigger('click')
  await flushPromises()
}

// spec dashboard/widgets "Text widget", "Table widget" (US-058, US-059)
describe('WidgetDialog', () => {
  it('adds a text widget at the given position', async () => {
    const wrapper = await mountDialog()
    await wrapper.get('input[data-testid="widget-title"]').setValue('  Methodological Notes ')
    await wrapper.get('textarea[data-testid="widget-content"]').setValue('**bold**')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toEqual([[{ type: 'text', title: 'Methodological Notes', position: POSITION, config: { content: '**bold**' } }]])
  })

  it.each([
    [0, true],
    [10000, true],
    [10001, false],
  ])('content of %d characters: accepted %s', async (length, accepted) => {
    const wrapper = await mountDialog()
    await wrapper.get('input[data-testid="widget-title"]').setValue('Notes')
    await wrapper.get('textarea[data-testid="widget-content"]').setValue('c'.repeat(length))
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit') !== undefined).toBe(accepted)
    expect(wrapper.text().includes('The content may have at most 10000 characters.')).toBe(!accepted)
  })

  it.each([
    ['', false],
    ['  ', false],
    ['t', true],
    ['t'.repeat(150), true],
    ['t'.repeat(151), false],
  ])('title %j: accepted %s', async (title, accepted) => {
    const wrapper = await mountDialog()
    await wrapper.get('input[data-testid="widget-title"]').setValue(title)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit') !== undefined).toBe(accepted)
    expect(wrapper.text().includes('Enter a title with 1 to 150 characters.')).toBe(!accepted)
  })

  it('offers shared catalogs only on a shared dashboard and the views of the chosen catalog', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'table')
    const catalogs = select(wrapper, 'widget-catalog').props('items') as { label: string; value: string }[]
    expect(catalogs.map((item) => item.label)).toEqual(['Application Inventory', 'Compliance Overview'])
    select(wrapper, 'widget-catalog').vm.$emit('update:modelValue', CATALOGS[0]!.id)
    await flushPromises()
    const views = select(wrapper, 'widget-view').props('items') as { label: string }[]
    expect(views.map((item) => item.label)).toEqual(['Default columns', 'Compact view'])
  })

  it('offers personal catalogs on a personal dashboard', async () => {
    const wrapper = await mountDialog({ shared: false })
    await type(wrapper, 'table')
    expect((select(wrapper, 'widget-catalog').props('items') as unknown[]).length).toBe(3)
  })

  it('adds a table widget with a catalog, a view and a page size', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'table')
    await wrapper.get('input[data-testid="widget-title"]').setValue('Application Overview')
    select(wrapper, 'widget-catalog').vm.$emit('update:modelValue', CATALOGS[0]!.id)
    await flushPromises()
    select(wrapper, 'widget-view').vm.$emit('update:modelValue', TABLE_WIDGET.config.savedViewId)
    const size = wrapper.get('input[data-testid="widget-page-size"]')
    await size.setValue('25')
    await size.trigger('change')
    await size.trigger('blur')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      type: 'table',
      title: 'Application Overview',
      position: POSITION,
      config: { catalogId: CATALOGS[0]!.id, savedViewId: TABLE_WIDGET.config.savedViewId, pageSize: 25 },
    })
  })

  it('needs a catalog for a table widget and sends no view for the default columns', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'table')
    await wrapper.get('input[data-testid="widget-title"]').setValue('Table')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('Choose a catalog.')
    select(wrapper, 'widget-catalog').vm.$emit('update:modelValue', CATALOGS[0]!.id)
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ config: { catalogId: CATALOGS[0]!.id, pageSize: 10 } })
    expect((wrapper.emitted('submit')![0]![0] as { config: object }).config).not.toHaveProperty('savedViewId')
  })

  it('edits a widget without changing its type or position', async () => {
    const wrapper = await mountDialog({ widget: TEXT_WIDGET })
    expect(wrapper.find('[data-testid="widget-type"]').exists()).toBe(false)
    expect((wrapper.get('input[data-testid="widget-title"]').element as HTMLInputElement).value).toBe('Methodological Notes')
    await wrapper.get('input[data-testid="widget-title"]').setValue('Renamed')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ type: 'text', title: 'Renamed', position: TEXT_WIDGET.position })
  })

  it('shows the fields and the refusal of a rejection', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'table')
    await wrapper.setProps({ rejection: { invalidFields: ['catalogId', 'config.pageSize'], forbidden: false } })
    expect(wrapper.text()).toContain('Choose a catalog.')
    expect(wrapper.text()).toContain('The page size must be 5 to 100.')
    await wrapper.setProps({ rejection: { invalidFields: [], forbidden: true } })
    expect(wrapper.get('[data-testid="widget-failure"]').text()).toContain('You may not use this catalog.')
  })

  // ---- KPI and chart widgets (US-056, US-057; design D6 of us-056-kpi-and-chart-widgets)

  type Wrapper = Awaited<ReturnType<typeof mountDialog>>

  async function choose(wrapper: Wrapper, testid: string, value: unknown) {
    select(wrapper, testid).vm.$emit('update:modelValue', value)
    await flushPromises()
  }

  function items(wrapper: Wrapper, testid: string) {
    return select(wrapper, testid).props('items') as { label: string; value: string; disabled?: boolean }[]
  }

  async function kpi(wrapper: Wrapper, title = 'Total Investment') {
    await type(wrapper, 'kpi')
    await wrapper.get('input[data-testid="widget-title"]').setValue(title)
  }

  it('offers element types without connection types, and "All element types" only for a count of the entities', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    expect(items(wrapper, 'widget-metatype').map((item) => item.label)).toEqual(['All element types', 'Application Component', 'Data Object'])
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    await choose(wrapper, 'widget-function', 'sum')
    expect(items(wrapper, 'widget-metatype').map((item) => item.label)).toEqual(['Application Component', 'Data Object'])
  })

  it('offers only integer properties for sum, average, minimum and maximum', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    for (const fn of ['sum', 'avg', 'min', 'max']) {
      await choose(wrapper, 'widget-function', fn)
      expect(items(wrapper, 'widget-property').map((item) => item.label), fn).toEqual(['Investment'])
    }
  })

  it('offers every mapped property and the entities themselves for a count', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    expect(items(wrapper, 'widget-property').map((item) => item.label)).toEqual(['The entities themselves', 'Investment', 'Plateau', 'Vendor', 'Go live', 'Critical'])
  })

  it('takes a property that no longer fits out of the form when the function changes', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    await choose(wrapper, 'widget-property', VENDOR_ID)
    await choose(wrapper, 'widget-function', 'sum')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('Choose a property that fits the function.')
  })

  it('disables the numeric functions for all element types', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', '__all__')
    const disabled = items(wrapper, 'widget-function').filter((item) => item.disabled).map((item) => item.value)
    expect(disabled).toEqual(['sum', 'avg', 'min', 'max'])
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      type: 'kpi',
      config: { aggregation: { function: 'count', metatypeId: null, propertyDefId: null }, comparison: 'none' },
    })
  })

  it('adds a KPI with a sum, a unit and a target', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    await choose(wrapper, 'widget-function', 'sum')
    await choose(wrapper, 'widget-property', INVESTMENT_ID)
    await wrapper.get('input[data-testid="widget-unit"]').setValue(' CHF ')
    await choose(wrapper, 'widget-comparison', 'vs-target')
    const target = wrapper.get('input[data-testid="widget-target"]')
    await target.setValue('50000000')
    await target.trigger('change')
    await target.trigger('blur')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      type: 'kpi',
      title: 'Total Investment',
      position: POSITION,
      config: {
        aggregation: { function: 'sum', metatypeId: APPLICATION_ID, propertyDefId: INVESTMENT_ID },
        unit: 'CHF',
        comparison: 'vs-target',
        targetValue: 50000000,
      },
    })
  })

  it('adds a KPI compared with the value 30 days ago, without a target value', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    expect(items(wrapper, 'widget-comparison').map((item) => item.value)).toEqual(['none', 'vs-target', 'vs-previous-value'])
    await choose(wrapper, 'widget-comparison', 'vs-previous-value')
    expect(wrapper.find('input[data-testid="widget-target"]').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      type: 'kpi',
      config: { comparison: 'vs-previous-value' },
    })
    expect((wrapper.emitted('submit')?.[0]?.[0] as { config: Record<string, unknown> }).config).not.toHaveProperty('targetValue')
  })

  it('needs a target value for the comparison with a target', async () => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    await choose(wrapper, 'widget-comparison', 'vs-target')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('Enter a target value.')
  })

  it.each([
    [20, true],
    [21, false],
    [0, true],
  ])('a unit of %d characters: accepted %s', async (length, accepted) => {
    const wrapper = await mountDialog()
    await kpi(wrapper)
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    await wrapper.get('input[data-testid="widget-unit"]').setValue('u'.repeat(length))
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit') !== undefined).toBe(accepted)
    expect(wrapper.text().includes('The unit may have at most 20 characters.')).toBe(!accepted)
  })

  it('offers only enum, string, boolean and reference properties, the element type and the layer for the grouping', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'chart')
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    expect(items(wrapper, 'widget-group-by').map((item) => item.label)).toEqual([
      'Element type',
      'Architecture layer',
      'Property: Plateau',
      'Property: Vendor',
      'Property: Critical',
    ])
    expect(items(wrapper, 'widget-group-by').some((item) => item.value.includes(GO_LIVE_ID) || item.value.includes(INVESTMENT_ID))).toBe(false)
  })

  it('offers only the element type and the layer for the grouping of all element types', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'chart')
    await choose(wrapper, 'widget-metatype', '__all__')
    expect(items(wrapper, 'widget-group-by').map((item) => item.label)).toEqual(['Element type', 'Architecture layer'])
  })

  it('adds a chart with grouping, chart type, legend and data labels', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'chart')
    await wrapper.get('input[data-testid="widget-title"]').setValue('Per Plateau')
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    await choose(wrapper, 'widget-function', 'sum')
    await choose(wrapper, 'widget-property', INVESTMENT_ID)
    await choose(wrapper, 'widget-group-by', `property:${PLATEAU_ID}`)
    await choose(wrapper, 'widget-chart-type', 'donut')
    await wrapper.get('[data-testid="widget-show-legend"]').trigger('click')
    await wrapper.get('[data-testid="widget-show-data-labels"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      type: 'chart',
      title: 'Per Plateau',
      position: POSITION,
      config: {
        aggregation: {
          function: 'sum',
          metatypeId: APPLICATION_ID,
          propertyDefId: INVESTMENT_ID,
          groupBy: { kind: 'property', propertyDefId: PLATEAU_ID },
        },
        chartType: 'donut',
        showLegend: false,
        showDataLabels: true,
      },
    })
  })

  it.each([
    ['metatype', { kind: 'metatype' }],
    ['layer', { kind: 'layer' }],
  ])('groups a chart by the %s', async (value, groupBy) => {
    const wrapper = await mountDialog()
    await type(wrapper, 'chart')
    await wrapper.get('input[data-testid="widget-title"]').setValue('Entities')
    await choose(wrapper, 'widget-metatype', '__all__')
    await choose(wrapper, 'widget-group-by', value)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      config: { aggregation: { function: 'count', metatypeId: null, propertyDefId: null, groupBy }, chartType: 'bar', showLegend: true, showDataLabels: false },
    })
  })

  it('starts a chart with all entities counted, grouped by element type', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'chart')
    await wrapper.get('input[data-testid="widget-title"]').setValue('Entities per type')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      config: { aggregation: { function: 'count', metatypeId: null, propertyDefId: null, groupBy: { kind: 'metatype' } }, chartType: 'bar' },
    })
  })

  it('drops the grouping when the element type changes to one that does not have the property', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'chart')
    await wrapper.get('input[data-testid="widget-title"]').setValue('Entities')
    await choose(wrapper, 'widget-metatype', APPLICATION_ID)
    await choose(wrapper, 'widget-group-by', `property:${CRITICAL_ID}`)
    await choose(wrapper, 'widget-metatype', '__all__')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('Choose a grouping that fits the element type.')
  })

  it('edits a KPI with its settings and keeps its type', async () => {
    const wrapper = await mountDialog({ widget: KPI_WIDGET })
    expect(wrapper.find('[data-testid="widget-type"]').exists()).toBe(false)
    await flushPromises()
    expect((wrapper.get('input[data-testid="widget-unit"]').element as HTMLInputElement).value).toBe('CHF')
    expect((wrapper.get('input[data-testid="widget-target"]').element as HTMLInputElement).value).toBe('50,000,000')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      type: 'kpi',
      title: 'Total Investment',
      position: KPI_WIDGET.position,
      config: {
        aggregation: { function: 'sum', metatypeId: APPLICATION_ID, propertyDefId: INVESTMENT_ID },
        unit: 'CHF',
        comparison: 'vs-target',
        targetValue: 50000000,
      },
    })
  })

  it('edits a KPI compared with the value 30 days ago and keeps the mode', async () => {
    const wrapper = await mountDialog({ widget: KPI_INCREASED_WIDGET })
    await flushPromises()
    expect(wrapper.find('input[data-testid="widget-target"]').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ config: { comparison: 'vs-previous-value' } })
  })

  it('edits a chart with its settings', async () => {
    const wrapper = await mountDialog({ widget: BAR_WIDGET })
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      type: 'chart',
      config: { chartType: 'bar', unit: 'CHF', showLegend: true, showDataLabels: true, aggregation: { groupBy: { kind: 'property', propertyDefId: PLATEAU_ID } } },
    })
  })

  it('shows the fields of the rejection of an aggregation', async () => {
    const wrapper = await mountDialog()
    await type(wrapper, 'chart')
    await wrapper.setProps({ rejection: { invalidFields: ['config.aggregation.groupBy', 'unit', 'targetValue', 'chartType'], forbidden: false } })
    expect(wrapper.text()).toContain('Choose a grouping that fits the element type.')
    expect(wrapper.text()).toContain('The unit may have at most 20 characters.')
    expect(wrapper.text()).toContain('Choose a chart type.')
  })
})
