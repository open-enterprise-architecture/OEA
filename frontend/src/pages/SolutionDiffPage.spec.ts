import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import type { SolutionDelta, SolutionDeltaConflict, SolutionLandscapeEntry } from '../api/solutions'
import { session } from '../session/store'
import { testRouter } from '../test/router'
import {
  CONFLICT_MARIA,
  DIFF_DELTAS,
  LANDSCAPE,
  ME_PORTAL_READER,
  ME_SOLUTION_VIEWER,
  MODIFIED_ERP,
  manyDeltas,
  solutionApi,
} from '../test/solutions'
import SolutionDiffPage from './SolutionDiffPage.vue'

// spec solution/solution-diff "Screen \"Solution comparison\"", "Markings on the screen", "Drift
// markings on the screen" and "Loading the screen" (US-043 AC1–AC6; design D3 of us-043-diff-view)
const ERP_SOLUTION = '5d000000-0000-4000-8000-000000000002'

async function mountPage(
  options: {
    deltas?: SolutionDelta[]
    landscape?: SolutionLandscapeEntry[]
    me?: unknown
    diffStatus?: number
    id?: string
    conflicts?: Record<number, SolutionDeltaConflict[]>
  } = {},
) {
  const router = testRouter()
  const id = options.id ?? ERP_SOLUTION
  await router.push(`/solutions/${id}/diff`)
  const api = solutionApi({
    me: options.me ?? ME_SOLUTION_VIEWER,
    deltas: options.deltas ?? DIFF_DELTAS,
    landscape: options.landscape,
    diffStatus: options.diffStatus,
    conflicts: options.conflicts,
  })
  const wrapper = mount(SolutionDiffPage, { props: { id, fetchFn: api.fetch }, global: { plugins: [ui, router] } })
  await flushPromises()
  return Object.assign(wrapper, { api })
}

function card(wrapper: VueWrapper, name: string) {
  return wrapper.findAll('[data-testid="diff-entries"] article').find((article) => article.get('h2').text() === name)!
}

function field(wrapper: VueWrapper, name: string, side: 'current' | 'target', label: string) {
  return card(wrapper, name).get(`[data-testid="diff-${side}"] [data-testid="diff-field-${label}"]`)
}

describe('SolutionDiffPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('shows one card per delta in the order of the delta list, with the counts', async () => {
    const wrapper = await mountPage()
    expect(wrapper.findAll('[data-testid="diff-entries"] article h2').map((heading) => heading.text())).toEqual([
      'Scanner Gateway',
      'Warehouse Cloud',
      'ERP-Core',
      'Warehouse Management',
      'CRM-Legacy',
    ])
    expect(wrapper.get('[data-testid="diff-counts"]').text()).toBe('2 new · 2 modified · 1 retiring')
    expect(wrapper.get('[data-testid="diff-title"]').text()).toBe('Comparison: ERP Extension Logistics')
    expect(wrapper.get('[data-testid="diff-back"]').attributes('href')).toBe(`/solutions/${ERP_SOLUTION}`)
  })

  it('marks a new entity and says it does not exist yet', async () => {
    const wrapper = await mountPage()
    const entry = card(wrapper, 'Warehouse Cloud')
    expect(entry.attributes('data-testid')).toBe('diff-entry-new')
    expect(entry.get('[data-testid="diff-type"]').text()).toBe('New')
    expect(entry.get('[data-testid="diff-current"] [data-testid="diff-no-state"]').text()).toBe('Does not exist yet')
    expect(field(wrapper, 'Warehouse Cloud', 'target', 'Owner').text()).toBe('Logistics')
    expect(field(wrapper, 'Warehouse Cloud', 'target', 'Users').text()).toBe('120')
  })

  it('marks a retiring entity as out of service with its values struck through', async () => {
    const wrapper = await mountPage()
    const entry = card(wrapper, 'CRM-Legacy')
    expect(entry.get('[data-testid="diff-type"]').text()).toBe('Retiring')
    expect(entry.get('[data-testid="diff-target"] [data-testid="diff-out-of-service"]').text()).toBe('Out of service')
    const name = field(wrapper, 'CRM-Legacy', 'target', 'name')
    expect(name.text()).toBe('CRM-Legacy')
    expect(name.classes()).toContain('line-through')
    expect(field(wrapper, 'CRM-Legacy', 'current', 'name').classes()).not.toContain('line-through')
  })

  it('highlights a changed field of a modification and dims an unchanged one', async () => {
    const wrapper = await mountPage()
    const users = field(wrapper, 'ERP-Core', 'target', 'Users')
    expect(users.text()).toContain('400')
    expect(users.attributes('data-changed')).toBe('true')
    expect(users.text()).toContain('(changes)')
    expect(field(wrapper, 'ERP-Core', 'current', 'Users').text()).toContain('250')
    const owner = field(wrapper, 'ERP-Core', 'target', 'Owner')
    expect(owner.text()).toBe('IT')
    expect(owner.attributes('data-changed')).toBeUndefined()
    expect(owner.classes()).toContain('text-muted')
  })

  it('shows a restricted value as hidden', async () => {
    const wrapper = await mountPage()
    expect(field(wrapper, 'ERP-Core', 'current', 'Cost').get('[data-testid="diff-hidden"]').text()).toBe('hidden')
  })

  it('marks drift since capture at the current value', async () => {
    const wrapper = await mountPage()
    // Warehouse Management: recorded Users 25, the catalog holds 40 now
    const users = field(wrapper, 'Warehouse Management', 'current', 'Users')
    expect(users.text()).toContain('40')
    expect(users.get('[data-testid="diff-drift"]').text()).toBe('Changed in the catalog since capture')
    // ERP-Core: recorded Users 250, unchanged in the catalog
    expect(field(wrapper, 'ERP-Core', 'current', 'Users').find('[data-testid="diff-drift"]').exists()).toBe(false)
  })

  it('says that an entity is no longer in the catalog', async () => {
    const wrapper = await mountPage({ deltas: [MODIFIED_ERP], landscape: LANDSCAPE.filter((entry) => entry.name !== 'ERP-Core') })
    expect(card(wrapper, 'ERP-Core').get('[data-testid="diff-current"] [data-testid="diff-no-state"]').text()).toBe(
      'No longer in the catalog',
    )
    expect(field(wrapper, 'ERP-Core', 'target', 'Users').text()).toContain('400')
  })

  // spec solution/solution-conflicts "Warnings on the screen \"Solution comparison\"" (US-044)
  it('names the conflicts on the card of a conflicting entry only', async () => {
    const wrapper = await mountPage({ conflicts: { 102: [CONFLICT_MARIA] } })
    expect(card(wrapper, 'ERP-Core').findAll('[data-testid="delta-conflict"]').map((line) => line.text())).toEqual([
      "Also changed in 'Solution A' (owner: Maria)",
    ])
    expect(card(wrapper, 'CRM-Legacy').find('[data-testid="delta-conflict"]').exists()).toBe(false)
  })

  it('shows the notice without deltas and no error', async () => {
    const wrapper = await mountPage({ deltas: [] })
    expect(wrapper.get('[data-testid="diff-empty"]').text()).toContain('No changes captured yet')
    expect(wrapper.find('[data-testid="diff-failure"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="diff-entries"]').exists()).toBe(false)
  })

  it('shows one card for one delta', async () => {
    const wrapper = await mountPage({ deltas: [MODIFIED_ERP] })
    expect(wrapper.findAll('[data-testid="diff-entries"] article')).toHaveLength(1)
    expect(wrapper.find('[data-testid="diff-more"]').exists()).toBe(false)
  })

  it('shows 50 of 60 entries and appends the rest with "Load more"', async () => {
    const wrapper = await mountPage({ deltas: manyDeltas(60) })
    expect(wrapper.findAll('[data-testid="diff-entries"] article')).toHaveLength(50)
    expect(wrapper.get('[data-testid="diff-shown"]').text()).toBe('50 of 60 entries')
    expect(wrapper.get('[data-testid="diff-counts"]').text()).toBe('60 new · 0 modified · 0 retiring')
    await wrapper.get('[data-testid="diff-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="diff-entries"] article')).toHaveLength(60)
    expect(wrapper.api.diffQueries.at(-1)!.get('cursor')).toBe('dcursor-1')
    expect(wrapper.find('[data-testid="diff-more"]').exists()).toBe(false)
  })

  it('loads the first page again on "Refresh" and shows a delta added meanwhile', async () => {
    const wrapper = await mountPage({ deltas: [MODIFIED_ERP] })
    wrapper.api.deltas.push(DIFF_DELTAS[4]!)
    await wrapper.get('[data-testid="diff-refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="diff-entries"] article h2').map((heading) => heading.text())).toEqual(['ERP-Core', 'CRM-Legacy'])
    expect(wrapper.api.diffQueries).toHaveLength(2)
  })

  it('shows that an unknown solution was not found', async () => {
    const wrapper = await mountPage({ id: '5d000000-0000-4000-8000-0000000000ff' })
    expect(wrapper.find('[data-testid="diff-not-found"]').exists()).toBe(true)
  })

  it('shows the message of a failed load', async () => {
    const wrapper = await mountPage({ diffStatus: 500 })
    expect(wrapper.get('[data-testid="diff-failure"]').text()).toBe('The comparison could not be loaded.')
  })

  it('reads nothing without entity:read', async () => {
    const wrapper = await mountPage({ me: ME_PORTAL_READER })
    expect(wrapper.find('[data-testid="solutions-no-access"]').exists()).toBe(true)
    expect(wrapper.api.diffQueries).toHaveLength(0)
  })
})
