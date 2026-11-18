import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { WidgetData } from '../../api/dashboards'
import { dashboardApi, FILLED_DASHBOARD, ME_READER, TABLE_WIDGET, widgetData } from '../../test/dashboards'
import type { RecordedRequest } from '../../test/fetch'
import TableWidget from './TableWidget.vue'

function mountWidget(data: WidgetData | null = widgetData(TABLE_WIDGET), recorded: RecordedRequest[] = []) {
  const fetchFn = dashboardApi({ me: ME_READER, dashboards: [FILLED_DASHBOARD], recorded })
  return mount(TableWidget, { props: { dashboardId: FILLED_DASHBOARD.id, widgetId: TABLE_WIDGET.id, data: data ?? undefined, fetchFn }, global: { plugins: [ui] } })
}

// spec dashboard/widgets "Table widget", "Dashboard data" (US-058)
describe('TableWidget', () => {
  it('renders cell values as text', () => {
    const data = widgetData(TABLE_WIDGET)
    data.table!.rows[0]!.values.name = '<b>bold</b>'
    const wrapper = mountWidget(data)
    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid="widget-row"]')[0]!.text()).toContain('<b>bold</b>')
  })

  it('shows the message of a widget with an error', () => {
    const wrapper = mountWidget(widgetData({ ...TABLE_WIDGET, id: 'a0000000-0000-4000-8000-000000000003' }))
    expect(wrapper.get('[data-testid="widget-error"]').text()).toContain('Catalog not found')
    expect(wrapper.find('[data-testid="widget-table"]').exists()).toBe(false)
  })

  it('names a saved view that is gone', () => {
    const wrapper = mountWidget({ widgetId: 'w', type: 'table', status: 'error', error: { code: 'view-not-found', message: 'x' } })
    expect(wrapper.get('[data-testid="widget-error"]').text()).toContain('Saved view not found')
  })

  it('says so for a table without rows and for missing data', () => {
    const empty = widgetData(TABLE_WIDGET)
    empty.table!.rows = []
    empty.table!.page.totalElements = 0
    expect(mountWidget(empty).find('[data-testid="widget-no-rows"]').exists()).toBe(true)
    expect(mountWidget(null).find('[data-testid="widget-no-data"]').exists()).toBe(true)
  })

  it('shows no pagination on a single page and one on several', () => {
    const single = widgetData(TABLE_WIDGET)
    single.table!.page = { page: 0, pageSize: 10, totalElements: 10, totalPages: 1 }
    expect(mountWidget(single).find('[data-testid="widget-pagination"]').exists()).toBe(false)
    expect(mountWidget().find('[data-testid="widget-pagination"]').exists()).toBe(true)
  })

  it('reads another page of the widget and keeps the page on a failure', async () => {
    const recorded: RecordedRequest[] = []
    const wrapper = mountWidget(widgetData(TABLE_WIDGET), recorded)
    wrapper.findComponent({ name: 'Pagination' }).vm.$emit('update:page', 5)
    await flushPromises()
    expect(recorded[0]!.path).toBe(`/api/v1/views/dashboards/${FILLED_DASHBOARD.id}/widgets/${TABLE_WIDGET.id}/data?page=4`)
    expect(wrapper.findAll('[data-testid="widget-row"]')).toHaveLength(7)
    expect(wrapper.get('[data-testid="widget-page-info"]').text()).toContain('Page 5 of 5')
    const failing = mount(TableWidget, {
      props: { dashboardId: 'unknown', widgetId: TABLE_WIDGET.id, data: widgetData(TABLE_WIDGET), fetchFn: dashboardApi({ me: ME_READER, dashboards: [] }) },
      global: { plugins: [ui] },
    })
    failing.findComponent({ name: 'Pagination' }).vm.$emit('update:page', 2)
    await flushPromises()
    expect(failing.find('[data-testid="widget-page-failure"]').exists()).toBe(true)
    expect(failing.findAll('[data-testid="widget-row"]')).toHaveLength(10)
  })
})
