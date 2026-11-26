import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../../session/store'
import { IDS, ME_ARCHITECT, ME_READER } from '../../test/metamodel'
import { choose, mountWithMetamodel, select } from '../../test/metamodelMount'
import ViewpointsSection from './ViewpointsSection.vue'

// spec diagram/diagram-nesting "Setting Ask for a new parent" and metamodel/viewpoints "Ask for a new parent per
// viewpoint" on SCR-031 (design D4 of us-074-diagram-nesting)

const el = <T extends HTMLElement = HTMLElement>(testId: string) => document.querySelector<T>(`[data-testid="${testId}"]`)

async function type(testId: string, value: string) {
  const input = el<HTMLInputElement>(testId)!
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

const switchOf = (wrapper: { find: (selector: string) => { element: Element; exists: () => boolean } }) => wrapper.find('[data-testid="diagram-settings-ask"]')

describe('Diagram settings on SCR-031', () => {
  beforeEach(() => {
    session.me = undefined
    document.body.innerHTML = ''
  })

  it('shows the global setting to a reader as text, without a switch to change it', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: false }, { me: ME_READER })
    expect(wrapper.get('[data-testid="diagram-settings-value"]').text()).toBe("Ask 'New parent?' when moving shapes: Yes")
    expect(wrapper.find('[data-testid="diagram-settings-ask"]').exists()).toBe(false)
    api.state.diagramSettings.askNewParent = false
    const again = await mountWithMetamodel(ViewpointsSection, { canManage: false }, { me: ME_READER })
    expect(again.wrapper.get('[data-testid="diagram-settings-value"]').text()).toBe("Ask 'New parent?' when moving shapes: Yes")
    expect(api.requests.some((request) => request.method === 'PUT')).toBe(false)
  })

  it('changes the global setting with the version read and shows the new state', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    expect(switchOf(wrapper).element.getAttribute('aria-checked')).toBe('true')
    await wrapper.get('[data-testid="diagram-settings-ask"]').trigger('click')
    await flushPromises()
    const put = api.requests.find((request) => request.path === '/api/v1/admin/diagram-settings' && request.method === 'PUT')
    expect(put?.ifMatch).toBe('"1"')
    expect(put?.body).toEqual({ askNewParent: false })
    expect(wrapper.get('[data-testid="diagram-settings-ask"]').attributes('aria-checked')).toBe('false')
    expect(wrapper.get('[data-testid="diagram-settings-notice"]').text()).toContain('Setting saved')
    // the next change carries the version of the answer
    await wrapper.get('[data-testid="diagram-settings-ask"]').trigger('click')
    await flushPromises()
    const second = api.requests.filter((request) => request.path === '/api/v1/admin/diagram-settings' && request.method === 'PUT')[1]
    expect(second?.ifMatch).toBe('"2"')
    expect(second?.body).toEqual({ askNewParent: true })
  })

  it('reads the setting again when it was changed meanwhile', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    api.state.diagramSettings.version = 5
    api.state.diagramSettings.askNewParent = false
    await wrapper.get('[data-testid="diagram-settings-ask"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="diagram-settings-failure"]').text()).toContain('changed')
    expect(wrapper.get('[data-testid="diagram-settings-ask"]').attributes('aria-checked')).toBe('false')
  })

  it('stores "Ask" for a viewpoint with the choices Global setting, Ask and Do not ask', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    await wrapper.find('[data-testid="viewpoint-list"] [data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    const choices = (select(wrapper, 'viewpoint-ask-new-parent')!.props('items') as { value: string; label: string }[]).map((item) => [item.value, item.label])
    expect(choices).toEqual([
      ['global', 'Global setting'],
      ['ask', 'Ask'],
      ['no', 'Do not ask'],
    ])
    await choose(wrapper, 'viewpoint-ask-new-parent', 'ask')
    el<HTMLButtonElement>('viewpoint-submit')!.click()
    await flushPromises()
    const put = api.requests.find((request) => request.path.startsWith('/api/v1/admin/viewpoints/') && request.method === 'PUT')
    expect(put?.body).toMatchObject({ askNewParent: true })
    expect(wrapper.get('[data-testid="viewpoint-list"] [data-testid="viewpoint-ask-new-parent"]').text()).toContain("Ask 'New parent?': Ask")
  })

  it.each([
    ['no', false],
    ['global', null],
  ])('sends %s as %s and shows the stored value in the dialog again', async (choice, sent) => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    api.state.viewpoints[0]!.askNewParent = true
    await wrapper.find('[data-testid="viewpoint-list"] [data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="viewpoint-list"] [data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    expect(select(wrapper, 'viewpoint-ask-new-parent')!.props('modelValue')).toBe('global')
    await choose(wrapper, 'viewpoint-ask-new-parent', choice)
    await type('viewpoint-description', 'Changed')
    el<HTMLButtonElement>('viewpoint-submit')!.click()
    await flushPromises()
    const put = api.requests.find((request) => request.method === 'PUT' && request.path.startsWith('/api/v1/admin/viewpoints/'))
    expect(put?.body).toMatchObject({ askNewParent: sent })
  })

  it('shows the stored value of a viewpoint and nothing for a viewpoint that follows the global setting', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: false }, { me: ME_READER })
    expect(wrapper.find('[data-testid="viewpoint-ask-new-parent"]').exists()).toBe(false)
    api.state.viewpoints[1]!.askNewParent = false
    const again = await mountWithMetamodel(ViewpointsSection, { canManage: false }, { me: ME_READER })
    expect(again.wrapper.find('[data-testid="viewpoint-ask-new-parent"]').exists()).toBe(false)
    expect(IDS.component).toBeTruthy()
  })
})
