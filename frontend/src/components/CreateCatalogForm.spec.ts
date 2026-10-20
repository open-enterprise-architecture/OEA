import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { CATALOG, METATYPES, NAME_TAKEN } from '../test/catalogs'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import CreateCatalogForm from './CreateCatalogForm.vue'

async function mountForm(canManage: boolean, created: { status?: number; body: unknown; contentType?: string }) {
  const recorded: RecordedRequest[] = []
  const fetchFn = routedFetch(
    { '/api/v1/metamodel/metatypes': { body: METATYPES }, '/api/v1/catalogs': created },
    recorded,
  )
  const wrapper = mount(CreateCatalogForm, { props: { fetchFn, canManage }, global: { plugins: [ui] } })
  await flushPromises()
  return { wrapper, recorded }
}

function radio(wrapper: Awaited<ReturnType<typeof mountForm>>['wrapper'], group: string, value: string) {
  return wrapper.get(`[data-testid="${group}"] [value="${value}"]`)
}

async function submit(wrapper: Awaited<ReturnType<typeof mountForm>>['wrapper'], name: string) {
  await wrapper.get('[data-testid="catalog-name"] input, input[data-testid="catalog-name"]').setValue(name)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('CreateCatalogForm', () => {
  it('creates a shared catalog with the first entity type and emits it', async () => {
    const { wrapper, recorded } = await mountForm(true, { status: 201, body: CATALOG })
    await submit(wrapper, '  Application Inventory  ')
    const post = recorded.find((request) => request.init?.method === 'POST')
    expect(JSON.parse(String(post?.init?.body))).toEqual({
      name: 'Application Inventory',
      description: '',
      primaryMetatypeId: '5a000000-0000-4000-8000-000000000001',
      scope: 'instance',
      defaultJoinMode: 'aggregate',
    })
    expect(wrapper.emitted('created')?.[0]).toEqual([CATALOG])
  })

  // spec "Collision in the creation form"
  it('shows a name collision at the name field and stays open', async () => {
    const { wrapper } = await mountForm(true, NAME_TAKEN)
    await submit(wrapper, 'Application Inventory')
    expect(wrapper.text()).toContain('A catalog with this name already exists')
    expect(wrapper.emitted('created')).toBeUndefined()
    expect(wrapper.find('[data-testid="create-catalog-form"]').exists()).toBe(true)
  })

  // spec "Viewer opens the creation form"
  it('preselects personal and disables shared without catalog:manage', async () => {
    const { wrapper } = await mountForm(false, { status: 201, body: CATALOG })
    expect(radio(wrapper, 'catalog-scope', 'personal').attributes('aria-checked')).toBe('true')
    expect(radio(wrapper, 'catalog-scope', 'instance').attributes('disabled')).toBeDefined()
  })

  it('does not call the API without a name', async () => {
    const { wrapper, recorded } = await mountForm(true, { status: 201, body: CATALOG })
    await submit(wrapper, '   ')
    expect(recorded.filter((request) => request.init?.method === 'POST')).toHaveLength(0)
    expect(wrapper.text()).toContain('Enter a name with 1 to 255 characters.')
  })

  it('maps other rejected fields to their messages', async () => {
    const { wrapper } = await mountForm(true, {
      status: 422,
      contentType: 'application/problem+json',
      body: { type: 'urn:oea:problem:validation-failed', title: 'Invalid', status: 422, invalidFields: ['primaryMetatypeId'] },
    })
    await submit(wrapper, 'Inventory')
    expect(wrapper.text()).toContain('Select an entity type of the metamodel.')
  })
})
