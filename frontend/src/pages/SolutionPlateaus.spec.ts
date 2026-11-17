import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Solution, SolutionLandscapeEntry } from '../api/solutions'
import { session } from '../session/store'
import { BASELINE_ID, PHASE_1_ID, plateauApi, PLATEAUS, TARGET_2027_ID, TARGET_2028_ID, withPlateaus } from '../test/plateaus'
import { testRouter } from '../test/router'
import { APPLICATION, LANDSCAPE, ME_SOLUTION_ARCHITECT, SOLUTIONS, solutionApi } from '../test/solutions'
import SolutionPage from './SolutionPage.vue'
import SolutionsPage from './SolutionsPage.vue'

// specs solution/solution-creation "Plateau in the dialog \"New solution\"" and
// solution/solution-baseline "Basis in the area \"Baseline\"" (US-038 AC3, US-155 AC2; design D8
// of us-084-plateaus)

const IN_2028: Solution = { ...SOLUTIONS[0]!, fromPlateauId: TARGET_2027_ID, toPlateauId: TARGET_2028_ID }
const CRM_ID = '5d000000-0000-4000-8000-0000000000aa'
const BASIS: SolutionLandscapeEntry[] = [
  { ...LANDSCAPE[0]!, lifecycle: 'retiring', changedBy: { solutionId: CRM_ID, name: 'CRM Replacement' } },
  LANDSCAPE[1]!,
  {
    id: null,
    name: 'Salesforce',
    metatypeId: APPLICATION,
    metatypeName: 'Application',
    version: 0,
    parallelChanges: [],
    lifecycle: 'proposed',
    changedBy: { solutionId: CRM_ID, name: 'CRM Replacement' },
    deltaId: 'ae000000-0000-4000-8000-0000000000aa',
  },
]

function field(testId: string) {
  return document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)
}

describe('Plateaus of a solution', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  it('creates a solution without a plateau field when no plateau exists', async () => {
    const solutions = solutionApi()
    const router = testRouter()
    await router.push('/solutions')
    const wrapper = mount(SolutionsPage, {
      props: { fetchFn: withPlateaus(solutions.fetch, plateauApi({ plateaus: [] }).fetch) },
      global: { plugins: [ui, router] },
      attachTo: document.body,
    })
    await flushPromises()
    await wrapper.find('[data-testid="solution-new"]').trigger('click')
    await flushPromises()
    expect(field('solution-target-plateau')).toBeNull()
    wrapper.unmount()
  })

  it('offers the planned plateaus as target, shows the start, and sends both references', async () => {
    const solutions = solutionApi()
    const router = testRouter()
    await router.push('/solutions')
    const wrapper = mount(SolutionsPage, {
      props: { fetchFn: withPlateaus(solutions.fetch, plateauApi().fetch) },
      global: { plugins: [ui, router] },
      attachTo: document.body,
    })
    await flushPromises()
    await wrapper.find('[data-testid="solution-new"]').trigger('click')
    await flushPromises()
    expect(field('solution-target-plateau')).not.toBeNull()
    expect(field('solution-start-plateau')).toBeNull()
    const dialog = wrapper.findComponent({ name: 'SolutionCreateDialog' })
    ;(dialog.vm as unknown as { target: string }).target = PHASE_1_ID
    await flushPromises()
    expect(field('solution-start-plateau')?.textContent).toContain('Starts from Baseline 2026-Q2')
    const name = document.querySelector<HTMLInputElement>('[data-testid="solution-name"]')!
    name.value = 'Phase Work'
    name.dispatchEvent(new Event('input'))
    await flushPromises()
    document.querySelector<HTMLButtonElement>('[data-testid="solution-save"]')!.click()
    await flushPromises()
    expect(solutions.posts[0]).toMatchObject({ name: 'Phase Work', fromPlateauId: BASELINE_ID, toPlateauId: PHASE_1_ID })
    wrapper.unmount()
  })

  it('names the plateaus and the basis, and marks retiring and proposed entries without actions', async () => {
    const solutions = solutionApi({ me: ME_SOLUTION_ARCHITECT, solutions: [IN_2028], landscape: BASIS })
    const router = testRouter()
    await router.push(`/solutions/${IN_2028.id}`)
    const wrapper = mount(SolutionPage, {
      props: { id: IN_2028.id, fetchFn: withPlateaus(solutions.fetch, plateauApi({ plateaus: PLATEAUS }).fetch) },
      global: { plugins: [ui, router] },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="solution-plateaus"]').text()).toBe(
      'Plateau mode: from Target Cloud Migration 2027 to Target 2028',
    )
    expect(wrapper.find('[data-testid="baseline-basis"]').text()).toBe('Basis: content of Target Cloud Migration 2027')
    const rows = wrapper.findAll('[data-testid="baseline-table"] tbody tr')
    const byName = (name: string) => rows.find((row) => row.text().includes(name))!
    expect(byName('CRM-Legacy').find('[data-testid="baseline-lifecycle-retiring"]').text()).toBe('Retiring by CRM Replacement')
    expect(byName('CRM-Legacy').find('[data-testid="baseline-retire"]').exists()).toBe(true)
    const proposed = byName('Salesforce')
    expect(proposed.find('[data-testid="baseline-lifecycle-proposed"]').text()).toBe('Proposed by CRM Replacement')
    expect(proposed.find('[data-testid="baseline-modify"]').exists()).toBe(false)
    expect(proposed.find('[data-testid="baseline-retire"]').exists()).toBe(false)
    expect(byName('ERP-Core').find('[data-testid^="baseline-lifecycle"]').exists()).toBe(false)
    // a solution of a plateau goes live with its plateau (US-157 AC3)
    expect(wrapper.find('[data-testid="solution-go-live"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="solution-goes-live-with-plateau"]').text()).toBe('Goes live with plateau Target 2028')
    wrapper.unmount()
  })

  it('names the project mode for a solution without plateaus', async () => {
    const solutions = solutionApi({ me: ME_SOLUTION_ARCHITECT })
    const router = testRouter()
    await router.push(`/solutions/${SOLUTIONS[0]!.id}`)
    const wrapper = mount(SolutionPage, {
      props: { id: SOLUTIONS[0]!.id, fetchFn: solutions.fetch },
      global: { plugins: [ui, router] },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="solution-plateaus"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Project mode (without plateaus)')
    expect(wrapper.find('[data-testid="baseline-basis"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
