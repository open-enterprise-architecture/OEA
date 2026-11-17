import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { testRouter } from '../test/router'
import {
  BASELINE_ID,
  ME_PLATEAU_READER,
  PHASE_1_ID,
  plateauApi,
  PLATEAUS,
} from '../test/plateaus'
import { ME_SOLUTION_VIEWER } from '../test/solutions'
import PlateausPage from './PlateausPage.vue'

// spec solution/plateaus "Screen \"Plateaus\"" and the dialog (US-084, US-085 AC1, AC2, US-089 AC2,
// US-155; UC-11 A1; design D8 of us-084-plateaus)
async function mountPage(api = plateauApi()) {
  const router = testRouter()
  await router.push('/plateaus')
  const wrapper = mount(PlateausPage, { props: { fetchFn: api.fetch }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, api }
}

function field(testId: string) {
  return document.querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!
}

async function type(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  element.value = value
  element.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('PlateausPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  it('highlights the baseline, lists the planned plateaus with their date, and collapses the realized ones', async () => {
    const { wrapper } = await mountPage()
    expect(wrapper.find('[data-testid="plateaus-baseline"]').text()).toContain('Baseline 2026-Q2')
    const planned = wrapper.findAll('[data-testid="plateau-item"]').map((item) => item.text())
    expect(planned).toEqual([expect.stringContaining('Phase 1'), expect.stringContaining('Target Cloud Migration 2027'), expect.stringContaining('Target 2028')])
    expect(planned[1]).toContain('Planned for')
    expect(planned[1]).toContain('2 solutions')
    expect(planned[2]).toContain('No planned date')
    expect(planned[2]).toContain('Content of the predecessor')
    expect(wrapper.find('[data-testid="plateaus-realized"]').text()).toContain('Realized plateaus (1)')
    expect(wrapper.find('[data-testid="plateau-realized-item"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('says that no target plateau is planned yet', async () => {
    const { wrapper } = await mountPage(plateauApi({ plateaus: [PLATEAUS[0]!] }))
    expect(wrapper.find('[data-testid="plateaus-no-target"]').text()).toBe('No target plateau planned yet')
    wrapper.unmount()
  })

  it('offers the initial baseline without a baseline, and creates it', async () => {
    const { wrapper, api } = await mountPage(plateauApi({ plateaus: [] }))
    const notice = wrapper.find('[data-testid="plateaus-no-baseline"]')
    expect(notice.text()).toContain('No baseline plateau exists yet')
    expect(wrapper.find('[data-testid="plateau-new"]').exists()).toBe(false)
    await notice.findAll('button').find((button) => button.text() === 'Create initial baseline')!.trigger('click')
    await flushPromises()
    expect(document.querySelector('[data-testid="plateau-succeeds"]')).toBeNull()
    await type(field('plateau-name'), 'Baseline 2026-Q2')
    field('plateau-save').click()
    await flushPromises()
    expect(api.posts).toEqual([{ name: 'Baseline 2026-Q2', description: null, validFrom: null, succeeds: null, basis: null, status: 'baseline' }])
    expect(wrapper.find('[data-testid="plateaus-baseline"]').text()).toContain('Baseline 2026-Q2')
    wrapper.unmount()
  })

  it('creates a target plateau after the preselected baseline with the chosen basis', async () => {
    const { wrapper, api } = await mountPage()
    await wrapper.find('[data-testid="plateau-new"]').trigger('click')
    await flushPromises()
    await type(field('plateau-name'), 'Target 2029')
    const predecessor = [...document.querySelectorAll<HTMLElement>('[data-testid="plateau-basis"] [value="predecessor"]')][0]!
    predecessor.click()
    await flushPromises()
    field('plateau-save').click()
    await flushPromises()
    expect(api.posts[0]).toMatchObject({ name: 'Target 2029', status: 'target', succeeds: BASELINE_ID, basis: 'predecessor' })
    expect(wrapper.findAll('[data-testid="plateau-item"]').map((item) => item.text())).toContainEqual(expect.stringContaining('Target 2029'))
    wrapper.unmount()
  })

  it('shows a taken name at the field and keeps the dialog open', async () => {
    const { wrapper } = await mountPage()
    await wrapper.find('[data-testid="plateau-new"]').trigger('click')
    await flushPromises()
    await type(field('plateau-name'), 'target 2028')
    field('plateau-save').click()
    await flushPromises()
    expect(document.querySelector('[data-testid="plateau-dialog"]')?.textContent).toContain("Name 'target 2028' is already taken")
    wrapper.unmount()
  })

  it('disables the predecessor of a plateau that solutions target', async () => {
    const { wrapper } = await mountPage()
    const items = wrapper.findAll('[data-testid="plateau-item"]')
    await items[1]!.find('[data-testid="plateau-edit"]').trigger('click')
    await flushPromises()
    const select = field('plateau-succeeds')
    expect(select.hasAttribute('disabled') || select.hasAttribute('data-disabled')).toBe(true)
    expect(document.querySelector('[data-testid="plateau-dialog"]')?.textContent).toContain('its predecessor is fixed')
    wrapper.unmount()
  })

  it('edits a plateau and shows the change', async () => {
    const { wrapper, api } = await mountPage()
    await wrapper.findAll('[data-testid="plateau-item"]')[0]!.find('[data-testid="plateau-edit"]').trigger('click')
    await flushPromises()
    await type(field('plateau-name'), 'Phase 1 – CRM')
    field('plateau-save').click()
    await flushPromises()
    expect(api.puts[0]).toMatchObject({ id: PHASE_1_ID, body: { name: 'Phase 1 – CRM', succeeds: BASELINE_ID, basis: 'catalog' } })
    expect(wrapper.text()).toContain('Phase 1 – CRM')
    wrapper.unmount()
  })

  it('offers no edit for a realized plateau', async () => {
    const { wrapper } = await mountPage()
    await wrapper.find('[data-testid="plateaus-realized"] button').trigger('click')
    await flushPromises()
    const realized = wrapper.find('[data-testid="plateau-realized-item"]')
    expect(realized.text()).toContain('Baseline 2025')
    expect(realized.find('[data-testid="plateau-edit"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the plateaus without "New plateau" and "Edit" to a holder of solution:write only', async () => {
    const { wrapper } = await mountPage(plateauApi({ me: ME_PLATEAU_READER }))
    expect(wrapper.findAll('[data-testid="plateau-item"]')).toHaveLength(3)
    expect(wrapper.find('[data-testid="plateau-new"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="plateau-edit"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('tells a reader without a plateau permission that the page needs one', async () => {
    const { wrapper } = await mountPage(plateauApi({ me: ME_SOLUTION_VIEWER }))
    expect(wrapper.find('[data-testid="plateaus-no-access"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="plateaus-baseline"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
