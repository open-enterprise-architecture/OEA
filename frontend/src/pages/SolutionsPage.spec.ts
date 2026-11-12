import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { testRouter } from '../test/router'
import { ME_PORTAL_READER, ME_SOLUTION_VIEWER, solutionApi } from '../test/solutions'
import SolutionsPage from './SolutionsPage.vue'

// spec solution/solution-overview "Page \"Solutions\"" (US-038 AC1, AC2, AC5; design D4 of
// us-038-create-solution)
async function mountPage(api = solutionApi()) {
  const router = testRouter()
  await router.push('/solutions')
  const wrapper = mount(SolutionsPage, { props: { fetchFn: api.fetch }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router, api }
}

function dialog() {
  return {
    name: () => document.querySelector<HTMLInputElement>('[data-testid="solution-name"]')!,
    vision: () => document.querySelector<HTMLTextAreaElement>('[data-testid="solution-vision"]')!,
    save: () => document.querySelector<HTMLButtonElement>('[data-testid="solution-save"]')!,
  }
}

async function type(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  element.value = value
  element.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('SolutionsPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  it('lists the solutions with status, owner and creation time, linked to their page', async () => {
    const { wrapper } = await mountPage()
    const items = wrapper.findAll('[data-testid="solution-item"]')
    expect(items.map((item) => item.text())).toEqual([
      expect.stringContaining('CRM Replacement'),
      expect.stringContaining('ERP Extension Logistics'),
      expect.stringContaining('Integration Platform'),
    ])
    expect(items[0]!.text()).toContain('Draft')
    expect(items[0]!.text()).toContain('Michael')
    expect(items[0]!.text()).toContain('2026')
    expect(items[2]!.text()).toContain('Proposed')
    expect(items[0]!.attributes('href')).toBe('/solutions/5d000000-0000-4000-8000-000000000001')
    wrapper.unmount()
  })

  it('shows an empty state without solutions', async () => {
    const { wrapper } = await mountPage(solutionApi({ solutions: [] }))
    expect(wrapper.find('[data-testid="solutions-empty"]').exists()).toBe(true)
    wrapper.unmount()
  })

  // "Reader without solution:write"
  it('hides "New solution" from a reader without solution:write', async () => {
    const { wrapper } = await mountPage(solutionApi({ me: ME_SOLUTION_VIEWER }))
    expect(wrapper.findAll('[data-testid="solution-item"]')).toHaveLength(3)
    expect(wrapper.find('[data-testid="solution-new"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('says that a principal without entity:read has no access', async () => {
    const { wrapper } = await mountPage(solutionApi({ me: ME_PORTAL_READER }))
    expect(wrapper.find('[data-testid="solutions-no-access"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="solution-list"]').exists()).toBe(false)
    wrapper.unmount()
  })

  // "Create through the dialog"
  it('creates a solution and opens its page', async () => {
    const { wrapper, router, api } = await mountPage()
    await wrapper.get('[data-testid="solution-new"]').trigger('click')
    await flushPromises()
    await type(dialog().name(), '  Data Platform  ')
    await type(dialog().vision(), 'Goal: one data platform.')
    dialog().save().click()
    await flushPromises()
    expect(api.posts).toEqual([{ name: 'Data Platform', description: 'Goal: one data platform.' }])
    expect(router.currentRoute.value.path).toBe('/solutions/5d000000-0000-4000-8000-000000000004')
    wrapper.unmount()
  })

  // "Name taken in the dialog"
  it('keeps the dialog open and names the taken name', async () => {
    const { wrapper, router } = await mountPage()
    await wrapper.get('[data-testid="solution-new"]').trigger('click')
    await flushPromises()
    await type(dialog().name(), 'erp extension logistics')
    dialog().save().click()
    await flushPromises()
    expect(document.body.textContent).toContain("Name 'erp extension logistics' is already taken")
    expect(dialog().name()).not.toBeNull()
    expect(router.currentRoute.value.path).toBe('/solutions')
    wrapper.unmount()
  })

  it('blocks saving a blank name', async () => {
    const { wrapper, api } = await mountPage()
    await wrapper.get('[data-testid="solution-new"]').trigger('click')
    await flushPromises()
    await type(dialog().name(), '   ')
    expect(dialog().save().disabled).toBe(true)
    expect(api.posts).toHaveLength(0)
    wrapper.unmount()
  })

  // limit of the architecture vision: 2000 characters are saved, 2001 block saving
  it.each([
    [2000, false],
    [2001, true],
  ])('a vision of %i characters disables saving: %s', async (length, disabled) => {
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="solution-new"]').trigger('click')
    await flushPromises()
    await type(dialog().name(), 'Long Vision')
    await type(dialog().vision(), 'v'.repeat(length))
    expect(dialog().save().disabled).toBe(disabled)
    expect(document.querySelector('[data-testid="solution-vision-count"]')!.textContent).toContain(`${length} / 2000`)
    wrapper.unmount()
  })
})
