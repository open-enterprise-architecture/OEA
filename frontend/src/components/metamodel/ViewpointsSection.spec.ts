import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../../session/store'
import { IDS, ME_ARCHITECT, ME_READER } from '../../test/metamodel'
import { mountWithMetamodel } from '../../test/metamodelMount'
import ViewpointsSection from './ViewpointsSection.vue'

// spec metamodel/viewpoints "Section \"Viewpoints\"" (US-090, US-093 AC2; design D4 of
// us-090-viewpoints)

function el<T extends HTMLElement = HTMLElement>(testId: string) {
  return document.querySelector<T>(`[data-testid="${testId}"]`)
}

async function check(group: string, value: string) {
  const box = [...document.querySelectorAll<HTMLElement>(`[data-testid="${group}"] button[role="checkbox"], [data-testid="${group}"] input[type="checkbox"]`)].find(
    (candidate) => candidate.getAttribute('value') === value || candidate.id.endsWith(value),
  )
  box!.click()
  await flushPromises()
}

async function type(testId: string, value: string) {
  const input = el<HTMLInputElement>(testId)!
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('ViewpointsSection', () => {
  beforeEach(() => {
    session.me = undefined
    document.body.innerHTML = ''
  })

  it('lists the viewpoints with their allowed types', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: false }, { me: ME_READER })
    const row = wrapper.find('[data-testid="viewpoint-row"]')
    expect(row.text()).toContain('Application Landscape')
    expect(row.text()).toContain('Application Component')
    expect(row.text()).toContain('Data Flow')
    expect(wrapper.find('[data-testid="viewpoint-new"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="viewpoint-edit"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="viewpoint-delete"]').exists()).toBe(false)
  })

  it('creates a viewpoint with element metatypes and connection types', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    await wrapper.find('[data-testid="viewpoint-new"]').trigger('click')
    await flushPromises()
    expect(el('viewpoint-metatypes')!.textContent).not.toContain('Data Flow')
    expect(el('viewpoint-connections')!.textContent).toContain('Data Flow')
    expect(el<HTMLButtonElement>('viewpoint-submit')!.disabled).toBe(true)
    await type('viewpoint-name', 'Integration View')
    await check('viewpoint-metatypes', IDS.component)
    await check('viewpoint-connections', IDS.dataFlow)
    el<HTMLButtonElement>('viewpoint-submit')!.click()
    await flushPromises()
    const post = api.requests.find((request) => request.method === 'POST' && request.path === '/api/v1/admin/viewpoints')
    expect(post?.body).toMatchObject({ name: 'Integration View', metatypeIds: [IDS.component], connectionTypeIds: [IDS.dataFlow] })
    expect(wrapper.findAll('[data-testid="viewpoint-row"]').map((row) => row.text())).toEqual([
      expect.stringContaining('Application Landscape'),
      expect.stringContaining('Integration View'),
    ])
  })

  it('shows a taken name at its field', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    await wrapper.find('[data-testid="viewpoint-new"]').trigger('click')
    await flushPromises()
    await type('viewpoint-name', 'application landscape')
    await check('viewpoint-metatypes', IDS.component)
    el<HTMLButtonElement>('viewpoint-submit')!.click()
    await flushPromises()
    expect(el('viewpoint-form')!.textContent).toContain("Name 'application landscape' is already taken")
  })

  it('shows the collision warning when the viewpoint changed meanwhile', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    api.state.viewpoints[0]!.version = 3
    await wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await type('viewpoint-description', 'Changed')
    el<HTMLButtonElement>('viewpoint-submit')!.click()
    await flushPromises()
    const put = api.requests.find((request) => request.method === 'PUT')
    expect(put?.ifMatch).toBe('"1"')
    expect(el('viewpoint-form')!.textContent).toContain('Application Landscape')
    expect(el('viewpoint-submit')).toBeNull()
  })

  it('deletes a viewpoint after confirmation', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    await wrapper.find('[data-testid="viewpoint-delete"]').trigger('click')
    await flushPromises()
    expect(el('delete-dialog')!.textContent).not.toContain('diagrams')
    el<HTMLButtonElement>('delete-confirm')!.click()
    await flushPromises()
    expect(api.requests.find((request) => request.method === 'DELETE')?.ifMatch).toBe('"1"')
    expect(wrapper.findAll('[data-testid="viewpoint-row"]')).toHaveLength(0)
  })
})
