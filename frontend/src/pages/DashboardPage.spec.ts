import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DashboardConfig } from '../api/dashboards'
import WidgetDialog from '../components/dashboard/WidgetDialog.vue'
import { session } from '../session/store'
import {
  dashboard,
  dashboardApi,
  DASHBOARD_WITH_ERROR,
  EMPTY_DASHBOARD,
  FILLED_DASHBOARD,
  ME_ADMIN,
  ME_MANAGER,
  ME_READER,
  OVERLAPPING_DASHBOARD,
  PERSONAL_DASHBOARD,
  TABLE_WIDGET,
  TEXT_WIDGET,
  type DashboardApiOptions,
} from '../test/dashboards'
import type { RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import DashboardPage from './DashboardPage.vue'

async function mountPage(me: unknown, config: DashboardConfig, options: Partial<DashboardApiOptions> = {}) {
  const router = testRouter()
  await router.push(`/dashboards/${config.id}`)
  const recorded: RecordedRequest[] = options.recorded ?? []
  const fetchFn = dashboardApi({ me, dashboards: [config], ...options, recorded })
  const wrapper = mount(DashboardPage, { props: { id: config.id, fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  vi.spyOn(wrapper.get('[data-testid="dashboard-grid"]', ).element, 'getBoundingClientRect').mockReturnValue({ width: 1188 } as DOMRect)
  return { wrapper, router, recorded, fetchFn }
}

/** The page of a dashboard without widgets has no grid to measure. */
async function mountEmpty(me: unknown, config: DashboardConfig) {
  const router = testRouter()
  await router.push(`/dashboards/${config.id}`)
  const fetchFn = dashboardApi({ me, dashboards: [config] })
  const wrapper = mount(DashboardPage, { props: { id: config.id, fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router, fetchFn }
}

async function edit(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="edit-dashboard"]').trigger('click')
}

function writes(recorded: RecordedRequest[]) {
  return recorded
    .filter((request) => request.init?.method && request.init.method !== 'GET')
    .map((request) => ({
      method: request.init!.method,
      path: request.path,
      ifMatch: (request.init!.headers as Record<string, string>)['If-Match'],
      body: request.init!.body ? JSON.parse(String(request.init!.body)) : undefined,
    }))
}

function drag(wrapper: VueWrapper, testid: string, dx: number, dy: number) {
  wrapper.get(`[data-testid="${testid}"]`).element.dispatchEvent(new MouseEvent('pointerdown', { clientX: 0, clientY: 0, button: 0, bubbles: true, cancelable: true }))
  window.dispatchEvent(new MouseEvent('pointermove', { clientX: dx, clientY: dy, bubbles: true }))
  window.dispatchEvent(new MouseEvent('pointerup', { clientX: dx, clientY: dy, bubbles: true }))
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('DashboardPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  // spec dashboard/dashboards "Empty dashboard after creation"
  it('shows a new dashboard with the notice "No widgets yet. Add widget."', async () => {
    const { wrapper } = await mountEmpty(ME_MANAGER, EMPTY_DASHBOARD)
    expect(wrapper.get('[data-testid="dashboard-title"]').text()).toBe('IT Investment Planning 2026–2030')
    expect(wrapper.get('[data-testid="dashboard-empty"]').text()).toContain('No widgets yet. Add widget.')
    expect(wrapper.find('[data-testid="empty-add-widget"]').exists()).toBe(true)
  })

  // spec "Reader sees no edit actions"
  it('offers no edit action without canEdit', async () => {
    const { wrapper } = await mountPage(ME_READER, { ...FILLED_DASHBOARD, canEdit: false })
    expect(wrapper.text()).toContain('Methodological Notes')
    for (const testid of ['edit-dashboard', 'add-widget', 'delete-dashboard', 'rename-dashboard', 'done-editing']) {
      expect(wrapper.find(`[data-testid="${testid}"]`).exists()).toBe(false)
    }
    expect(wrapper.find('[data-testid="move-a0000000-0000-4000-8000-000000000001"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Add widget')
    expect(wrapper.text()).not.toContain('Edit')
  })

  it('shows an empty dashboard to a reader without the action', async () => {
    const { wrapper } = await mountEmpty(ME_READER, { ...EMPTY_DASHBOARD, canEdit: false })
    expect(wrapper.get('[data-testid="dashboard-empty"]').text()).toContain('No widgets yet.')
    expect(wrapper.get('[data-testid="dashboard-empty"]').text()).not.toContain('Add widget')
    expect(wrapper.find('[data-testid="empty-add-widget"]').exists()).toBe(false)
  })

  it('shows the text widget as formatted text and the table widget with its rows', async () => {
    const { wrapper } = await mountPage(ME_READER, { ...FILLED_DASHBOARD, canEdit: false })
    const text = wrapper.get('[data-testid="text-widget"]')
    expect(text.find('strong').text()).toBe('Method:')
    expect(text.findAll('li')).toHaveLength(2)
    expect(text.get('a').attributes('href')).toBe('https://example.org/guide')
    expect(text.text()).not.toContain('**')
    expect(wrapper.findAll('[data-testid="widget-row"]')).toHaveLength(10)
    expect(wrapper.get('[data-testid="widget-page-info"]').text()).toBe('Page 1 of 5 · 47 entries')
  })

  // spec "Script in the content"
  it('runs no script of a text widget and renders no clickable javascript link', async () => {
    const hostile = {
      ...TEXT_WIDGET,
      config: { content: '<script>window.hacked = 1</script>\n\n<img src=x onerror="window.hacked = 2">\n\n[x](javascript:window.hacked=3)' },
    }
    const { wrapper } = await mountPage(ME_READER, { ...FILLED_DASHBOARD, canEdit: false, widgets: [hostile] })
    const text = wrapper.get('[data-testid="text-widget"]')
    expect(text.find('script').exists()).toBe(false)
    expect(text.find('img').exists()).toBe(false)
    expect(text.find('a').exists()).toBe(false)
    expect((window as unknown as { hacked?: number }).hacked).toBeUndefined()
  })

  // spec "Second page of a table"
  it('pages a table through the widget data', async () => {
    const { wrapper, recorded } = await mountPage(ME_READER, { ...FILLED_DASHBOARD, canEdit: false })
    wrapper.findComponent({ name: 'Pagination' }).vm.$emit('update:page', 2)
    await flushPromises()
    expect(recorded.some((request) => request.path === `/api/v1/views/dashboards/${FILLED_DASHBOARD.id}/widgets/${TABLE_WIDGET.id}/data?page=1`)).toBe(true)
    const names = wrapper.findAll('[data-testid="widget-row"]').map((row) => row.findAll('td')[0]!.text())
    expect(names[0]).toBe('Application 11')
    expect(names[9]).toBe('Application 20')
    expect(wrapper.get('[data-testid="widget-page-info"]').text()).toContain('Page 2 of 5')
  })

  // spec "Deleted catalog"
  it('shows the message of a widget with an error and the other widgets normally', async () => {
    const { wrapper } = await mountPage(ME_READER, { ...DASHBOARD_WITH_ERROR, canEdit: false })
    expect(wrapper.get('[data-testid="widget-error"]').text()).toContain('Catalog not found')
    expect(wrapper.find('[data-testid="text-widget"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="widget-row"]')).toHaveLength(10)
  })

  // spec "Restricted values"
  it('marks a restricted column and shows no values of it', async () => {
    const { wrapper } = await mountPage(ME_READER, { ...FILLED_DASHBOARD, canEdit: false }, { restricted: true })
    expect(wrapper.find('[data-testid="widget-restricted-status"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="widget-restricted-name"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid="widget-row"]')[0]!.findAll('td')[1]!.text()).toBe('')
  })

  it('says so for a dashboard that does not exist', async () => {
    const router = testRouter()
    await router.push('/dashboards/unknown')
    const fetchFn = dashboardApi({ me: ME_READER, dashboards: [] })
    const wrapper = mount(DashboardPage, { props: { id: 'unknown', fetchFn }, global: { plugins: [ui, router] } })
    await flushPromises()
    expect(wrapper.find('[data-testid="dashboard-not-found"]').exists()).toBe(true)
  })

  it('shows the personal dashboard of another person read-only to an administrator', async () => {
    const { wrapper } = await mountPage(ME_ADMIN, { ...PERSONAL_DASHBOARD, canEdit: false })
    expect(wrapper.get('[data-testid="dashboard-foreign"]').text()).toContain('cannot be changed')
    expect(wrapper.find('[data-testid="edit-dashboard"]').exists()).toBe(false)
  })

  // ---- edit mode

  it('shows the edit actions of an editor only in the edit mode', async () => {
    const { wrapper } = await mountPage(ME_MANAGER, FILLED_DASHBOARD)
    expect(wrapper.find('[data-testid="add-widget"]').exists()).toBe(false)
    await edit(wrapper)
    for (const testid of ['add-widget', 'rename-dashboard', 'delete-dashboard', 'done-editing']) {
      expect(wrapper.find(`[data-testid="${testid}"]`).exists()).toBe(true)
    }
    await wrapper.get('[data-testid="done-editing"]').trigger('click')
    expect(wrapper.find('[data-testid="add-widget"]').exists()).toBe(false)
  })

  // spec "Move a widget by keyboard", "Position two widgets": one request with If-Match
  it('sends one layout request with If-Match after a drag', async () => {
    const { wrapper, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD)
    await edit(wrapper)
    drag(wrapper, `move-${TEXT_WIDGET.id}`, 0, 124)
    await flushPromises()
    expect(writes(recorded)).toEqual([
      { method: 'PUT', path: `/api/v1/dashboards/${FILLED_DASHBOARD.id}/layout`, ifMatch: '"4"', body: { positions: [{ widgetId: TEXT_WIDGET.id, col: 1, row: 2, width: 4, height: 2 }] } },
    ])
    expect(wrapper.get(`[data-testid="widget-${TEXT_WIDGET.id}"]`).attributes('style')).toContain('grid-area: 2 / 1')
    // the next write uses the version the server returned
    drag(wrapper, `move-${TEXT_WIDGET.id}`, 0, -124)
    await flushPromises()
    expect(writes(recorded)[1]!.ifMatch).toBe('"5"')
  })

  it('sends one layout request with If-Match for a keyboard move', async () => {
    const { wrapper, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD)
    await edit(wrapper)
    await wrapper.get(`[data-testid="move-${TEXT_WIDGET.id}"]`).trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    expect(writes(recorded)).toHaveLength(1)
    expect(writes(recorded)[0]).toMatchObject({ method: 'PUT', ifMatch: '"4"', body: { positions: [{ widgetId: TEXT_WIDGET.id, row: 2 }] } })
  })

  // spec "Width beyond the grid in the editor"
  it('shows "Widget exceeds the grid width" and sends nothing for a width beyond column 12', async () => {
    const { wrapper, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD)
    await edit(wrapper)
    // the table widget is at column 5 with width 8; one column to the right leaves the grid
    drag(wrapper, `move-${TABLE_WIDGET.id}`, 100, 0)
    await flushPromises()
    expect(wrapper.get('[data-testid="dashboard-failure"]').text()).toBe('Widget exceeds the grid width')
    expect(writes(recorded)).toEqual([])
  })

  it('marks overlapping widgets', async () => {
    const { wrapper } = await mountPage(ME_MANAGER, OVERLAPPING_DASHBOARD)
    expect(wrapper.get(`[data-testid="widget-${TEXT_WIDGET.id}"]`).attributes('data-overlap')).toBe('true')
    expect(wrapper.get(`[data-testid="widget-${TABLE_WIDGET.id}"]`).attributes('data-overlap')).toBe('true')
  })

  // spec "Lost updates": a 412 reloads the dashboard
  it('reloads the dashboard after a 412', async () => {
    const { wrapper, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD, { conflict: true })
    await edit(wrapper)
    await wrapper.get(`[data-testid="move-${TEXT_WIDGET.id}"]`).trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    expect(wrapper.get('[data-testid="dashboard-notice"]').text()).toContain('Someone else changed the dashboard')
    const gets = recorded.filter((request) => request.path === `/api/v1/views/dashboards/${FILLED_DASHBOARD.id}`)
    expect(gets).toHaveLength(2)
    // the dashboard shows the version of the server again: the next write succeeds
    await wrapper.get(`[data-testid="move-${TEXT_WIDGET.id}"]`).trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    expect(writes(recorded)[1]).toMatchObject({ ifMatch: '"5"' })
    expect(wrapper.find('[data-testid="dashboard-notice"]').exists()).toBe(false)
  })

  it('adds a text widget below the others and reloads the data', async () => {
    const { wrapper, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD)
    await edit(wrapper)
    await wrapper.get('[data-testid="add-widget"]').trigger('click')
    await flushPromises()
    const dialog = wrapper.findComponent(WidgetDialog)
    await dialog.get('input[data-testid="widget-title"]').setValue('Roadmap notes')
    await dialog.get('textarea[data-testid="widget-content"]').setValue('Some *notes*')
    await dialog.get('form').trigger('submit')
    await flushPromises()
    expect(writes(recorded)).toEqual([
      {
        method: 'POST',
        path: `/api/v1/dashboards/${FILLED_DASHBOARD.id}/widgets`,
        ifMatch: '"4"',
        body: { type: 'text', title: 'Roadmap notes', position: { col: 1, row: 4, width: 12, height: 2 }, config: { content: 'Some *notes*' } },
      },
    ])
    expect(wrapper.text()).toContain('Roadmap notes')
    expect(wrapper.findComponent(WidgetDialog).exists()).toBe(false)
  })

  it('keeps the widget dialog open and names the field of a rejection', async () => {
    const { wrapper } = await mountPage(ME_MANAGER, FILLED_DASHBOARD, { reject: { status: 422, type: 'validation-failed', invalidFields: ['config.content'] } })
    await edit(wrapper)
    await wrapper.get('[data-testid="add-widget"]').trigger('click')
    await flushPromises()
    const dialog = wrapper.findComponent(WidgetDialog)
    await dialog.get('input[data-testid="widget-title"]').setValue('Text')
    await dialog.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.findComponent(WidgetDialog).exists()).toBe(true)
    expect(wrapper.findComponent(WidgetDialog).text()).toContain('The content may have at most 10000 characters.')
  })

  it('removes a widget after the confirmation', async () => {
    const { wrapper, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD)
    await edit(wrapper)
    await wrapper.get(`[data-testid="remove-${TEXT_WIDGET.id}"]`).trigger('click')
    await flushPromises()
    expect(writes(recorded)).toEqual([])
    await document.querySelector<HTMLElement>('[data-testid="confirm-submit"]')!.click()
    await flushPromises()
    expect(writes(recorded)).toEqual([{ method: 'DELETE', path: `/api/v1/dashboards/${FILLED_DASHBOARD.id}/widgets/${TEXT_WIDGET.id}`, ifMatch: '"4"', body: undefined }])
    expect(wrapper.find(`[data-testid="widget-${TEXT_WIDGET.id}"]`).exists()).toBe(false)
  })

  it('renames the dashboard and reports a taken name', async () => {
    const other = dashboard({ id: 'd0000000-0000-4000-8000-000000000009', name: 'Security Posture' })
    const { wrapper, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD, { dashboards: [FILLED_DASHBOARD, other] })
    await edit(wrapper)
    await wrapper.get('[data-testid="rename-dashboard"]').trigger('click')
    await flushPromises()
    const input = () => document.querySelector<HTMLInputElement>('input[data-testid="rename-name"]')!
    input().value = 'security posture'
    input().dispatchEvent(new Event('input'))
    document.querySelector<HTMLFormElement>('[data-testid="rename-form"]')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()
    expect(document.body.textContent).toContain('A dashboard with this name already exists')
    input().value = 'Planning 2027'
    input().dispatchEvent(new Event('input'))
    document.querySelector<HTMLFormElement>('[data-testid="rename-form"]')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()
    expect(writes(recorded).at(-1)).toMatchObject({ method: 'PUT', path: `/api/v1/dashboards/${FILLED_DASHBOARD.id}`, body: { name: 'Planning 2027', scope: 'instance' } })
    expect(wrapper.get('[data-testid="dashboard-title"]').text()).toBe('Planning 2027')
  })

  it('deletes the dashboard after the confirmation and returns to the overview', async () => {
    const { wrapper, router, recorded } = await mountPage(ME_MANAGER, FILLED_DASHBOARD)
    await edit(wrapper)
    await wrapper.get('[data-testid="delete-dashboard"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('Delete "IT Investment Planning 2026–2030" with all its widgets?')
    document.querySelector<HTMLElement>('[data-testid="confirm-submit"]')!.click()
    await flushPromises()
    expect(writes(recorded)).toEqual([{ method: 'DELETE', path: `/api/v1/dashboards/${FILLED_DASHBOARD.id}`, ifMatch: '"4"', body: undefined }])
    expect(router.currentRoute.value.path).toBe('/dashboards')
  })

  it('shares a personal dashboard only for dashboard:manage', async () => {
    const personal = { ...PERSONAL_DASHBOARD, owner: { id: 7, displayName: 'Kurt' }, canEdit: true }
    const manager = await mountPage(ME_MANAGER, personal)
    await edit(manager.wrapper)
    await manager.wrapper.get('[data-testid="share-dashboard"]').trigger('click')
    await flushPromises()
    expect(writes(manager.recorded)[0]).toMatchObject({ method: 'PUT', body: { name: 'My analysis', scope: 'instance' } })
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
    const reader = await mountPage(ME_READER, { ...personal, owner: { id: 8, displayName: 'Franz' } })
    await edit(reader.wrapper)
    expect(reader.wrapper.find('[data-testid="share-dashboard"]').exists()).toBe(false)
  })
})
