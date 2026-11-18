import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { dashboard, dashboardApi, ME_MANAGER } from '../../test/dashboards'
import { routedFetch, type RecordedRequest } from '../../test/fetch'
import CreateDashboardForm from './CreateDashboardForm.vue'

async function mountForm(canManage: boolean, fetchFn = dashboardApi({ me: ME_MANAGER, dashboards: [] })) {
  const wrapper = mount(CreateDashboardForm, { props: { fetchFn, canManage }, global: { plugins: [ui] } })
  await flushPromises()
  return wrapper
}

async function submit(wrapper: Awaited<ReturnType<typeof mountForm>>, name: string, description = '') {
  await wrapper.get('input[data-testid="dashboard-name"]').setValue(name)
  await wrapper.get('textarea[data-testid="dashboard-description"]').setValue(description)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

// spec dashboard/dashboards "Create a dashboard" (US-055, US-062)
describe('CreateDashboardForm', () => {
  it('creates a shared dashboard for a manager and emits it', async () => {
    const recorded: RecordedRequest[] = []
    const wrapper = await mountForm(true, dashboardApi({ me: ME_MANAGER, dashboards: [], recorded }))
    await submit(wrapper, '  IT Investment Planning 2026–2030  ', 'Plan')
    const post = recorded.find((request) => request.init?.method === 'POST')
    expect(JSON.parse(String(post?.init?.body))).toEqual({ name: 'IT Investment Planning 2026–2030', description: 'Plan', scope: 'instance' })
    expect(wrapper.emitted('created')?.[0]?.[0]).toMatchObject({ name: 'IT Investment Planning 2026–2030', scope: 'instance', widgets: [] })
  })

  it('preselects personal and disables shared without dashboard:manage', async () => {
    const wrapper = await mountForm(false)
    expect(wrapper.get('[data-testid="dashboard-scope"] [value="personal"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.get('[data-testid="dashboard-scope"] [value="instance"]').attributes('disabled')).toBeDefined()
    await submit(wrapper, 'My analysis')
    expect(wrapper.emitted('created')?.[0]?.[0]).toMatchObject({ scope: 'personal' })
  })

  it('shows a name collision at the name field and stays open', async () => {
    const wrapper = await mountForm(true, dashboardApi({ me: ME_MANAGER, dashboards: [dashboard()] }))
    await submit(wrapper, 'it investment planning 2026–2030')
    expect(wrapper.text()).toContain('A dashboard with this name already exists')
    expect(wrapper.emitted('created')).toBeUndefined()
  })

  it.each([
    ['', false],
    ['   ', false],
    ['n', true],
    ['n'.repeat(200), true],
    ['n'.repeat(201), false],
  ])('a name of %j: accepted %s', async (name, accepted) => {
    const recorded: RecordedRequest[] = []
    const wrapper = await mountForm(true, dashboardApi({ me: ME_MANAGER, dashboards: [], recorded }))
    await submit(wrapper, name)
    expect(recorded.some((request) => request.init?.method === 'POST')).toBe(accepted)
    expect(wrapper.text().includes('Enter a name with 1 to 200 characters.')).toBe(!accepted)
  })

  it.each([
    [1000, true],
    [1001, false],
  ])('a description of %d characters: accepted %s', async (length, accepted) => {
    const recorded: RecordedRequest[] = []
    const wrapper = await mountForm(true, dashboardApi({ me: ME_MANAGER, dashboards: [], recorded }))
    await submit(wrapper, 'Described', 'd'.repeat(length))
    expect(recorded.some((request) => request.init?.method === 'POST')).toBe(accepted)
    expect(wrapper.text().includes('The description may have at most 1000 characters.')).toBe(!accepted)
  })

  it('shows a refusal of the server', async () => {
    const fetchFn = routedFetch({
      '/api/v1/dashboards': { status: 403, contentType: 'application/problem+json', body: { type: 'urn:oea:problem:forbidden', status: 403 } },
    })
    const wrapper = await mountForm(true, fetchFn as ReturnType<typeof dashboardApi>)
    await submit(wrapper, 'Forbidden')
    expect(wrapper.get('[data-testid="create-dashboard-failure"]').text()).toContain('You may not create this dashboard.')
  })
})
