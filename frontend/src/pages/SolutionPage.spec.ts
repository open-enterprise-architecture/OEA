import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import type { SolutionLandscapeEntry } from '../api/solutions'
import { session } from '../session/store'
import { testRouter } from '../test/router'
import { APPLICATION, ME_PORTAL_READER, ME_SOLUTION_VIEWER, manyApplications, solutionApi } from '../test/solutions'
import SolutionPage from './SolutionPage.vue'

// spec solution/solution-overview "Page \"Solution\"" (US-038 AC1; design D4 of
// us-038-create-solution)
async function mountPage(
  id: string,
  me: unknown = ME_SOLUTION_VIEWER,
  landscape?: SolutionLandscapeEntry[],
  rejectCursors = false,
) {
  const router = testRouter()
  await router.push(`/solutions/${id}`)
  const api = solutionApi({ me, landscape, rejectCursors })
  const wrapper = mount(SolutionPage, {
    props: { id, fetchFn: api.fetch },
    global: { plugins: [ui, router] },
  })
  await flushPromises()
  return Object.assign(wrapper, { api })
}

const ERP = '5d000000-0000-4000-8000-000000000002'

function baselineNames(wrapper: VueWrapper) {
  return wrapper.findAll('[data-testid="baseline-table"] tbody tr').map((row) => row.findAll('td')[0]!.text())
}

async function toPage(wrapper: VueWrapper, label: string) {
  const button = wrapper.get('[data-testid="baseline-pagination"]').findAll('button').find((candidate) => candidate.text() === label)
  await button!.trigger('click')
  await flushPromises()
}

async function search(wrapper: VueWrapper, term: string) {
  const field = wrapper.get('[data-testid="baseline-search"]')
  await (field.element.tagName === 'INPUT' ? field : field.get('input')).setValue(term)
  await new Promise((resolve) => setTimeout(resolve, 350))
  await flushPromises()
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

  // spec solution/solution-baseline "Area \"Baseline\" on the page \"Solution\"" (US-039; design D5
  // of us-039-solution-baseline)

  describe('baseline', () => {
    it('lists the landscape with name and metatype', async () => {
      const wrapper = await mountPage(ERP)
      expect(baselineNames(wrapper)).toEqual([
        'CRM-Legacy',
        'Customer Portal',
        'Data Warehouse',
        'ERP-Core',
        'Message Broker',
        'PostgreSQL Cluster',
        'Warehouse Management',
      ])
      const crm = wrapper.findAll('[data-testid="baseline-table"] tbody tr')[0]!
      expect(crm.findAll('td')[1]!.text()).toBe('Application')
      expect(wrapper.get('[data-testid="baseline-page-info"]').text()).toContain('7 entities')
    })

    it('offers element metatypes only and reloads with the chosen one', async () => {
      const wrapper = await mountPage(ERP)
      const select = wrapper.findAllComponents({ name: 'Select' }).find((component) => Array.isArray(component.props('items')))!
      const values = (select.props('items') as { value: string; label: string }[]).map((item) => item.label)
      expect(values).toEqual(['All metatypes', 'Application', 'Technology Component'])
      select.vm.$emit('update:modelValue', APPLICATION)
      await flushPromises()
      expect(wrapper.api.landscapeQueries.at(-1)!.get('metatypeId')).toBe(APPLICATION)
      expect(baselineNames(wrapper)).not.toContain('Message Broker')
      expect(baselineNames(wrapper)).toHaveLength(5)
    })

    it('searches after the delay', async () => {
      const wrapper = await mountPage(ERP)
      await search(wrapper, 'erp')
      expect(wrapper.api.landscapeQueries.at(-1)!.get('q')).toBe('erp')
      expect(baselineNames(wrapper)).toEqual(['ERP-Core'])
    })

    it('loads the next page with the cursor and jumps without it', async () => {
      const wrapper = await mountPage(ERP, ME_SOLUTION_VIEWER, manyApplications(120))
      expect(baselineNames(wrapper)).toHaveLength(50)
      expect(wrapper.api.landscapeQueries.at(-1)!.has('cursor')).toBe(false)
      await toPage(wrapper, '2')
      expect(wrapper.api.landscapeQueries.at(-1)!.get('cursor')).toBe('cursor-1')
      expect(baselineNames(wrapper)[0]).toBe('Application 051')
      await toPage(wrapper, '1')
      await toPage(wrapper, '3')
      expect(wrapper.api.landscapeQueries.at(-1)!.has('cursor')).toBe(false)
      expect(wrapper.get('[data-testid="baseline-page-info"]').text()).toContain('Page 3 of 3')
    })

    it('repeats the query without the cursor when the cursor is rejected', async () => {
      const wrapper = await mountPage(ERP, ME_SOLUTION_VIEWER, manyApplications(120), true)
      await toPage(wrapper, '2')
      const [rejected, repeated] = wrapper.api.landscapeQueries.slice(-2)
      expect(rejected!.get('cursor')).toBe('cursor-1')
      expect(repeated!.has('cursor')).toBe(false)
      expect(repeated!.get('page')).toBe('1')
      expect(baselineNames(wrapper)[0]).toBe('Application 051')
      expect(wrapper.find('[data-testid="baseline-failure"]').exists()).toBe(false)
    })

    // "Empty landscape"
    it('shows the notice of the empty landscape', async () => {
      const wrapper = await mountPage(ERP, ME_SOLUTION_VIEWER, [])
      expect(wrapper.get('[data-testid="baseline-empty-landscape"]').text()).toBe(
        'No entities in the current landscape – you can only create new entities',
      )
      expect(wrapper.find('[data-testid="baseline-no-match"]').exists()).toBe(false)
    })

    // "No match"
    it('says that no entity matches, without the notice of the empty landscape', async () => {
      const wrapper = await mountPage(ERP)
      await search(wrapper, 'no such system')
      expect(wrapper.find('[data-testid="baseline-no-match"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="baseline-empty-landscape"]').exists()).toBe(false)
    })
  })
})
