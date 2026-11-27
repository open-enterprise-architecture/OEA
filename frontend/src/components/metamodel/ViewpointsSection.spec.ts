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

/** The count of the affected diagrams is asked after a pause of 250 ms. */
async function debounced() {
  await new Promise((resolve) => setTimeout(resolve, 400))
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
    const row = wrapper.find('[data-testid="viewpoint-list"] [data-testid="viewpoint-row"]')
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
    expect(wrapper.findAll('[data-testid="viewpoint-list"] [data-testid="viewpoint-row"]').map((row) => row.text())).toEqual([
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
    expect(wrapper.findAll('[data-testid="viewpoint-list"] [data-testid="viewpoint-row"]')).toHaveLength(0)
    // the system-defined viewpoint stays
    expect(wrapper.findAll('[data-testid="viewpoint-list-system"] [data-testid="viewpoint-row"]')).toHaveLength(1)
  })

  // spec "Section Viewpoints" (US-093 AC1): the deletion warns with the number of diagrams that will be deleted
  it.each([
    [5, '5 diagrams will be deleted with the viewpoint'],
    [1, '1 diagram will be deleted with the viewpoint'],
  ])('warns before deleting a viewpoint with %i diagram(s)', async (count, text) => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: count })
    await wrapper.find('[data-testid="viewpoint-delete"]').trigger('click')
    await flushPromises()
    expect(el('delete-warning')!.textContent).toContain(text)
  })

  it('shows no warning about diagrams when deleting an unused viewpoint', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: 0 })
    await wrapper.find('[data-testid="viewpoint-delete"]').trigger('click')
    await flushPromises()
    expect(el('delete-dialog')).not.toBeNull()
    expect(el('delete-warning')).toBeNull()
  })

  // spec "Section Viewpoints" (US-090 AC2, owner decision 2026-11-26): removing an allowed type from a used viewpoint
  // warns with the number of diagrams that get conflicts, without blocking
  it('warns "3 diagrams affected" with the number the backend counts, and still saves', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: 5, affectedCount: 3 })
    await wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    expect(el('viewpoint-diagrams-affected')).toBeNull()
    await check('viewpoint-metatypes', IDS.service)
    await debounced()
    expect(el('viewpoint-diagrams-affected')!.textContent).toContain('3 diagrams affected – their shapes of removed types become conflicts')
    el<HTMLButtonElement>('viewpoint-submit')!.click()
    await flushPromises()
    expect(api.requests.find((request) => request.method === 'PUT')).toBeDefined()
  })

  it('asks for the types selected now and shows the answer for the last selection', async () => {
    const asked: string[][] = []
    const { wrapper } = await mountWithMetamodel(
      ViewpointsSection,
      { canManage: true },
      {
        me: ME_ARCHITECT,
        diagramCount: 5,
        affectedCount: (metatypeIds, connectionTypeIds) => {
          asked.push([...metatypeIds, ...connectionTypeIds])
          return connectionTypeIds.length === 0 ? 4 : 2
        },
      },
    )
    await wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await check('viewpoint-metatypes', IDS.service)
    await check('viewpoint-connections', IDS.dataFlow)
    await debounced()
    // the quick selections count once, for what is selected now
    expect(asked).toHaveLength(1)
    expect(asked[0]).not.toContain(IDS.service)
    expect(asked[0]).not.toContain(IDS.dataFlow)
    expect(el('viewpoint-diagrams-affected')!.textContent).toContain('4 diagrams affected')
  })

  it('warns "1 diagram affected" in the singular', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: 5, affectedCount: 1 })
    await wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await check('viewpoint-connections', IDS.dataFlow)
    await debounced()
    expect(el('viewpoint-diagrams-affected')!.textContent).toContain('1 diagram affected')
  })

  it('shows no warning when a type is removed that no diagram shows', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: 5, affectedCount: 0 })
    await wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await check('viewpoint-metatypes', IDS.service)
    await debounced()
    expect(api.requests.some((request) => request.path.endsWith('/affected-diagrams/query'))).toBe(true)
    expect(el('viewpoint-diagrams-affected')).toBeNull()
  })

  it('asks nothing for an unused viewpoint and when a type is only added', async () => {
    const unused = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: 0, affectedCount: 9 })
    await unused.wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await check('viewpoint-metatypes', IDS.service)
    await debounced()
    expect(el('viewpoint-diagrams-affected')).toBeNull()
    expect(unused.api.requests.some((request) => request.path.endsWith('/affected-diagrams/query'))).toBe(false)
    document.body.innerHTML = ''
    const used = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: 5, affectedCount: 9 })
    await used.wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await check('viewpoint-metatypes', IDS.zone)
    await debounced()
    expect(el('viewpoint-diagrams-affected')).toBeNull()
    expect(used.api.requests.some((request) => request.path.endsWith('/affected-diagrams/query'))).toBe(false)
  })

  it('takes the warning back when the removed type is selected again', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, diagramCount: 5, affectedCount: 3 })
    await wrapper.find('[data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await check('viewpoint-metatypes', IDS.service)
    await debounced()
    expect(el('viewpoint-diagrams-affected')).not.toBeNull()
    await check('viewpoint-metatypes', IDS.service)
    await debounced()
    expect(el('viewpoint-diagrams-affected')).toBeNull()
  })
})
