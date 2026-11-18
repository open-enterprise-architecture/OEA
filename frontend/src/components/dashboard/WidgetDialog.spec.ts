import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { CATALOGS } from '../../test/catalogs'
import { dashboardApi, ME_MANAGER, TABLE_WIDGET, TEXT_WIDGET } from '../../test/dashboards'
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

async function type(wrapper: Awaited<ReturnType<typeof mountDialog>>, value: 'text' | 'table') {
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
})
