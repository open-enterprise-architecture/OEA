import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import StarterConfigurationCard from './StarterConfigurationCard.vue'

const me = (permissions: string[]) => ({
  principal: { principalType: 'person', displayName: 'Dev Metamodel Admin' },
  mfaSatisfied: true,
  roles: [],
  permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
  csrfToken: 'csrf-3',
})

const TOGAF = {
  packageId: 'oea-starter-togaf-classic',
  name: 'TOGAF classic starter configuration',
  version: '1.0.0',
  publisher: 'OEA',
  description: 'Starter metamodel after the classic TOGAF reading.',
  metatypeCount: 7,
  propertyDefinitionCount: 1,
  metatypeNames: ['Application Component', 'Data Object', 'Technology Component'],
  imported: false,
}

async function mountCard(fetchFn: typeof fetch) {
  const wrapper = mount(StarterConfigurationCard, { props: { fetchFn }, global: { plugins: [ui] } })
  await flushPromises()
  return wrapper
}

describe('StarterConfigurationCard', () => {
  it('offers the starter packages on a new instance and imports one', async () => {
    const recorded: RecordedRequest[] = []
    const wrapper = await mountCard(
      routedFetch(
        {
          '/api/v1/me': { body: me(['metamodel:write']) },
          '/api/v1/metamodel/metatypes': { body: [] },
          '/api/v1/admin/metamodel/starter-packages': { body: [TOGAF] },
          '/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import': {
            body: { status: 'completed', created: 7, updated: 0, conflicts: 0, batchId: 'b-1', conflictList: [] },
          },
        },
        recorded,
      ),
    )
    const entry = wrapper.get('[data-testid="starter-oea-starter-togaf-classic"]')
    expect(entry.text()).toContain('TOGAF classic starter configuration')
    expect(entry.text()).toContain('Application Component, Data Object, Technology Component')
    await wrapper.get('[data-testid="import-oea-starter-togaf-classic"]').trigger('click')
    await flushPromises()
    const post = recorded.find((r) => r.path.endsWith('/import'))!
    expect(post.init!.method).toBe('POST')
    expect((post.init!.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-3')
    expect(wrapper.get('[data-testid="starter-imported"]').text()).toContain('7 entity types are now available')
  })

  it('shows nothing without metamodel:write', async () => {
    const recorded: RecordedRequest[] = []
    const wrapper = await mountCard(routedFetch({ '/api/v1/me': { body: me(['entity:read']) } }, recorded))
    expect(wrapper.find('[data-testid="starter-configuration"]').exists()).toBe(false)
    expect(recorded.map((r) => r.path)).toEqual(['/api/v1/me'])
  })

  it('shows nothing once the instance has metatypes', async () => {
    const wrapper = await mountCard(
      routedFetch({
        '/api/v1/me': { body: me(['metamodel:write']) },
        '/api/v1/metamodel/metatypes': { body: [{ id: 'm-1', name: 'Application Component', abbreviation: 'AC' }] },
      }),
    )
    expect(wrapper.find('[data-testid="starter-configuration"]').exists()).toBe(false)
  })

  it('shows nothing without a session', async () => {
    const wrapper = await mountCard(
      routedFetch({
        '/api/v1/me': {
          status: 401,
          contentType: 'application/problem+json',
          body: { type: 'urn:oea:problem:unauthenticated', status: 401 },
        },
      }),
    )
    expect(wrapper.find('[data-testid="starter-configuration"]').exists()).toBe(false)
  })

  it('lists conflicts when the import is refused', async () => {
    const wrapper = await mountCard(
      routedFetch({
        '/api/v1/me': { body: me(['metamodel:write']) },
        '/api/v1/metamodel/metatypes': { body: [] },
        '/api/v1/admin/metamodel/starter-packages': { body: [TOGAF] },
        '/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import': {
          status: 409,
          body: {
            status: 'failed',
            created: 0,
            updated: 0,
            conflicts: 1,
            conflictList: [{ element: 'metatype Data Object', reason: 'organization' }],
          },
        },
      }),
    )
    await wrapper.get('[data-testid="import-oea-starter-togaf-classic"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="starter-failure"]').text()).toContain('metatype Data Object')
  })
})
