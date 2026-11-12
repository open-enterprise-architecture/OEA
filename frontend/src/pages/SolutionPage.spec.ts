import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Solution, SolutionDelta, SolutionLandscapeEntry } from '../api/solutions'
import { session } from '../session/store'
import { testRouter } from '../test/router'
import {
  APPLICATION,
  DELTAS,
  ME_PORTAL_READER,
  ME_SOLUTION_ARCHITECT,
  ME_SOLUTION_VIEWER,
  SOLUTIONS,
  manyApplications,
  solutionApi,
} from '../test/solutions'
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

  // spec solution/solution-deltas "Area \"Changes\" on the page \"Solution\"", "Write actions on the
  // page \"Solution\"" and "Action \"Retire\" in the baseline" (US-040, US-042; design D9 of
  // us-040-entity-deltas)

  describe('deltas', () => {
    async function mountWith(
      options: { me?: unknown; deltas?: SolutionDelta[]; solutions?: Solution[]; completeOnWrite?: boolean } = {},
    ) {
      const router = testRouter()
      await router.push(`/solutions/${ERP}`)
      const api = solutionApi({ me: ME_SOLUTION_ARCHITECT, deltas: DELTAS, ...options })
      const wrapper = mount(SolutionPage, {
        props: { id: ERP, fetchFn: api.fetch },
        global: { plugins: [ui, router] },
        attachTo: document.body,
      })
      await flushPromises()
      return Object.assign(wrapper, { api })
    }

    function deltaRows(wrapper: VueWrapper) {
      return wrapper.findAll('[data-testid="deltas-table"] tbody tr').map((row) =>
        row
          .findAll('td')
          .slice(0, 3)
          .map((cell) => cell.text()),
      )
    }

    function baselineRow(wrapper: VueWrapper, name: string) {
      return wrapper.findAll('[data-testid="baseline-table"] tbody tr').find((row) => row.findAll('td')[0]!.text() === name)!
    }

    afterEach(() => {
      document.body.innerHTML = ''
    })

    it('lists the deltas with type, name and metatype', async () => {
      const wrapper = await mountWith()
      expect(deltaRows(wrapper)).toEqual([
        ['New', 'Scanner Gateway', 'Technology Component'],
        ['New', 'Warehouse Cloud', 'Application'],
        ['Retiring', 'Warehouse Management', 'Application'],
      ])
      wrapper.unmount()
    })

    it('says that there are no changes yet', async () => {
      const wrapper = await mountWith({ deltas: [] })
      expect(wrapper.get('[data-testid="deltas-empty"]').text()).toBe('No changes yet.')
      wrapper.unmount()
    })

    it('retires an entity of the baseline and then marks its row', async () => {
      const wrapper = await mountWith({ deltas: [] })
      await baselineRow(wrapper, 'CRM-Legacy').get('[data-testid="baseline-retire"]').trigger('click')
      await flushPromises()
      expect(wrapper.api.deltaPosts).toEqual([{ deltaType: 'retiring', entityId: 101 }])
      expect(deltaRows(wrapper)).toEqual([['Retiring', 'CRM-Legacy', 'Application']])
      const row = baselineRow(wrapper, 'CRM-Legacy')
      expect(row.find('[data-testid="baseline-retire"]').exists()).toBe(false)
      expect(row.get('[data-testid="baseline-retiring"]').text()).toBe('Retiring')
      wrapper.unmount()
    })

    it('creates a new entity through the dialog and lists it', async () => {
      const wrapper = await mountWith({ deltas: [] })
      await wrapper.get('[data-testid="delta-new"]').trigger('click')
      await flushPromises()
      expect(document.querySelector('[data-testid="delta-dialog"]')).not.toBeNull()
      wrapper.findComponent({ name: 'SolutionDeltaDialog' }).vm.$emit('created', DELTAS[1])
      await flushPromises()
      expect(document.querySelector('[data-testid="delta-dialog"]')).toBeNull()
      wrapper.unmount()
    })

    it('removes a delta only after the confirmation', async () => {
      const wrapper = await mountWith()
      await wrapper.findAll('[data-testid="delta-remove"]')[2]!.trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Remove the change for Warehouse Management from this solution?')
      document.querySelector<HTMLButtonElement>('[data-testid="delta-remove-cancel"]')!.click()
      await flushPromises()
      expect(wrapper.api.deltaDeletes).toEqual([])
      expect(deltaRows(wrapper)).toHaveLength(3)
      await wrapper.findAll('[data-testid="delta-remove"]')[2]!.trigger('click')
      await flushPromises()
      document.querySelector<HTMLButtonElement>('[data-testid="delta-remove-confirm"]')!.click()
      await flushPromises()
      expect(wrapper.api.deltaDeletes).toEqual(['ae000000-0000-4000-8000-000000000003'])
      expect(deltaRows(wrapper).map((row) => row[1])).toEqual(['Scanner Gateway', 'Warehouse Cloud'])
      expect(baselineRow(wrapper, 'Warehouse Management').find('[data-testid="baseline-retire"]').exists()).toBe(true)
      wrapper.unmount()
    })

    it.each([
      ['without solution:write', { me: ME_SOLUTION_VIEWER }],
      [
        'for an implemented solution',
        { solutions: SOLUTIONS.map((candidate) => (candidate.id === ERP ? { ...candidate, status: 'implemented' as const } : candidate)) },
      ],
    ])('shows the list without write actions %s', async (_, options) => {
      const wrapper = await mountWith(options)
      expect(deltaRows(wrapper)).toHaveLength(3)
      expect(wrapper.find('[data-testid="delta-new"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="delta-remove"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="baseline-retire"]').exists()).toBe(false)
      expect(baselineRow(wrapper, 'Warehouse Management').find('[data-testid="baseline-retiring"]').exists()).toBe(true)
      wrapper.unmount()
    })

    it('hides the write actions after a 409', async () => {
      const wrapper = await mountWith({ completeOnWrite: true })
      await baselineRow(wrapper, 'CRM-Legacy').get('[data-testid="baseline-retire"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="deltas-failure"]').text()).toBe('This solution is completed and can no longer be changed.')
      expect(wrapper.find('[data-testid="delta-new"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="baseline-retire"]').exists()).toBe(false)
      wrapper.unmount()
    })
  })
})
