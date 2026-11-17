import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../../session/store'
import { goLivePreview, ME_ENTERPRISE_ARCHITECT, ME_PLATEAU_READER, plateauApi, PLATEAUS, TARGET_2027_ID } from '../../test/plateaus'
import { testRouter } from '../../test/router'
import { goLiveConflict } from '../../test/solutions'
import PlateausPage from '../../pages/PlateausPage.vue'

// spec solution/plateau-go-live "Dialog \"Take plateau live\"" and solution/plateaus "Action \"Go
// live\"" (US-086 AC1–AC3, US-087 AC1, AC3, US-157; design D7 of us-086-plateau-go-live)
async function mountPage(api = plateauApi()) {
  const router = testRouter()
  await router.push('/plateaus')
  const wrapper = mount(PlateausPage, { props: { fetchFn: api.fetch }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, api }
}

function el<T extends HTMLElement = HTMLElement>(testId: string) {
  return document.querySelector<T>(`[data-testid="${testId}"]`)
}

async function type(value: string) {
  const input = el<HTMLInputElement>('plateau-go-live-name')!
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function openDialog(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper']) {
  await wrapper.find('[data-testid="plateau-go-live"]').trigger('click')
  await flushPromises()
}

const TARGET_NAME = PLATEAUS.find((plateau) => plateau.id === TARGET_2027_ID)!.name

describe('Plateau go-live', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  it('offers "Go live" only on the plateaus that succeed the baseline', async () => {
    const { wrapper } = await mountPage()
    const items = wrapper.findAll('[data-testid="plateau-item"]')
    const withAction = items.filter((item) => item.find('[data-testid="plateau-go-live"]').exists()).map((item) => item.text())
    expect(withAction).toEqual([expect.stringContaining('Phase 1'), expect.stringContaining('Target Cloud Migration 2027')])
    wrapper.unmount()
  })

  it('offers no "Go live" without plateau:go-live', async () => {
    const { wrapper } = await mountPage(plateauApi({ me: ME_PLATEAU_READER }))
    expect(wrapper.find('[data-testid="plateau-go-live"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('enables "Confirm go-live" only for the exact name', async () => {
    const { wrapper, api } = await mountPage(plateauApi({ me: ME_ENTERPRISE_ARCHITECT }))
    await wrapper.findAll('[data-testid="plateau-item"]')[1]!.find('[data-testid="plateau-go-live"]').trigger('click')
    await flushPromises()
    expect(el('plateau-go-live-summary')?.textContent).toContain('Target Cloud Migration 2027 becomes the new baseline')
    expect(el('plateau-go-live-dialog')?.textContent).toContain('This action cannot be undone.')
    const confirm = () => el<HTMLButtonElement>('plateau-go-live-confirm')!
    expect(confirm().disabled).toBe(true)
    await type(TARGET_NAME.toLowerCase())
    expect(confirm().disabled).toBe(true)
    await type(TARGET_NAME)
    expect(confirm().disabled).toBe(false)
    confirm().click()
    await flushPromises()
    expect(api.goLives).toEqual([{ id: TARGET_2027_ID, name: TARGET_NAME }])
    expect(el('plateau-go-live-dialog')).toBeNull()
    expect(wrapper.find('[data-testid="plateaus-baseline"]').text()).toContain(TARGET_NAME)
    wrapper.unmount()
  })

  it('lists the solutions and the warnings', async () => {
    const { wrapper } = await mountPage()
    await wrapper.findAll('[data-testid="plateau-item"]')[1]!.find('[data-testid="plateau-go-live"]').trigger('click')
    await flushPromises()
    expect(document.querySelectorAll('[data-testid="plateau-go-live-solution"]')).toHaveLength(2)
    const warnings = el('plateau-go-live-warnings')!.textContent
    expect(warnings).toContain('One solution is not yet approved: CRM Replacement')
    expect(warnings).toContain('2 open issues remain after the go-live')
    wrapper.unmount()
  })

  it('shows no warning section without warnings', async () => {
    const { wrapper } = await mountPage(plateauApi({ goLive: goLivePreview({ warnings: false }) }))
    await openDialog(wrapper)
    expect(el('plateau-go-live-warnings')).toBeNull()
    wrapper.unmount()
  })

  it('lists conflicts per solution and opens the merge dialog to resolve them', async () => {
    const conflict = goLiveConflict('ae000000-0000-4000-8000-0000000000c9')
    const { wrapper } = await mountPage(plateauApi({ goLive: goLivePreview({ conflicts: [conflict] }) }))
    await openDialog(wrapper)
    expect(el<HTMLButtonElement>('plateau-go-live-confirm')!.disabled).toBe(true)
    el<HTMLButtonElement>('plateau-go-live-resolve')!.click()
    await flushPromises()
    expect(document.body.textContent).toContain('ERP-Core')
    wrapper.unmount()
  })
})
