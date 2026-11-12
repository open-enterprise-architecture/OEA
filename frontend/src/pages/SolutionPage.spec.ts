import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { testRouter } from '../test/router'
import { ME_PORTAL_READER, ME_SOLUTION_VIEWER, solutionApi } from '../test/solutions'
import SolutionPage from './SolutionPage.vue'

// spec solution/solution-overview "Page \"Solution\"" (US-038 AC1; design D4 of
// us-038-create-solution)
async function mountPage(id: string, me: unknown = ME_SOLUTION_VIEWER) {
  const router = testRouter()
  await router.push(`/solutions/${id}`)
  const wrapper = mount(SolutionPage, {
    props: { id, fetchFn: solutionApi({ me }).fetch },
    global: { plugins: [ui, router] },
  })
  await flushPromises()
  return wrapper
}

describe('SolutionPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('shows name, status, owner, creation time and the vision with its line breaks', async () => {
    const wrapper = await mountPage('5d000000-0000-4000-8000-000000000002')
    expect(wrapper.get('[data-testid="solution-title"]').text()).toBe('ERP Extension Logistics')
    expect(wrapper.get('[data-testid="solution-status"]').text()).toBe('Draft')
    expect(wrapper.get('[data-testid="solution-owner"]').text()).toBe('Michael')
    expect(wrapper.get('[data-testid="solution-created"]').text()).toContain('2026')
    const vision = wrapper.get('[data-testid="solution-vision"]')
    expect(vision.text()).toContain('Goal: warehouse logistics in the ERP.\nScope:')
    expect(vision.classes()).toContain('whitespace-pre-line')
  })

  it('says when a solution has no vision yet', async () => {
    const wrapper = await mountPage('5d000000-0000-4000-8000-000000000003')
    expect(wrapper.find('[data-testid="solution-no-vision"]').exists()).toBe(true)
  })

  // "Unknown solution page"
  it('shows that an unknown solution was not found', async () => {
    const wrapper = await mountPage('5d000000-0000-4000-8000-0000000000ff')
    expect(wrapper.find('[data-testid="solution-not-found"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="solution-title"]').exists()).toBe(false)
  })

  it('reads nothing without entity:read', async () => {
    const wrapper = await mountPage('5d000000-0000-4000-8000-000000000002', ME_PORTAL_READER)
    expect(wrapper.find('[data-testid="solutions-no-access"]').exists()).toBe(true)
  })
})
