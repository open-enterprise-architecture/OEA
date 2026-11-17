import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { goLiveConflict, MODIFIED_ERP, SOLUTIONS, solutionApi } from '../../test/solutions'
import DeltaMergeDialog from './DeltaMergeDialog.vue'

// spec solution/solution-go-live "Resolution of field conflicts" and "Go-live on the page
// \"Solution\"" (US-152 AC2; design D6 of us-152-solution-go-live)

const SOLUTION = SOLUTIONS[0]!.id
const USERS = goLiveConflict(MODIFIED_ERP.id)
const OWNER = goLiveConflict(MODIFIED_ERP.id, { field: 'fd000000-0000-4000-8000-000000000001', label: 'Owner', dataType: 'string', before: 'IT', current: 'Finance', after: 'Sales' })

async function mountDialog(conflicts = [USERS]) {
  const api = solutionApi({ deltas: [MODIFIED_ERP], goLiveConflicts: conflicts })
  const wrapper = mount(DeltaMergeDialog, { props: { solutionId: SOLUTION, conflicts, fetchFn: api.fetch }, global: { plugins: [ui] } })
  await flushPromises()
  return { wrapper, api }
}

async function choose(wrapper: Awaited<ReturnType<typeof mountDialog>>['wrapper'], field: string, value: string) {
  const radio = wrapper.get(`[data-testid="merge-choice-${field}"]`).findAll('button[role="radio"], input[type="radio"]').find((candidate) => candidate.attributes('value') === value)
  await radio!.trigger('click')
  await flushPromises()
}

describe('DeltaMergeDialog', () => {
  it('shows the before, current and delta value of each conflict, grouped by entity', async () => {
    const { wrapper } = await mountDialog([USERS, OWNER])
    expect(wrapper.text()).toContain('ERP-Core')
    expect(wrapper.get(`[data-testid="merge-conflict-${USERS.field}"]`).text()).toContain('When captured: 10')
    expect(wrapper.get(`[data-testid="merge-conflict-${USERS.field}"]`).text()).toContain('Keep the current value: 15')
    expect(wrapper.get(`[data-testid="merge-conflict-${USERS.field}"]`).text()).toContain('Take the value of the solution: 20')
  })

  it('enables "Apply" only when every conflict has a choice', async () => {
    const { wrapper } = await mountDialog([USERS, OWNER])
    const apply = () => wrapper.get('[data-testid="merge-apply"]').attributes('disabled')
    expect(apply()).toBeDefined()
    await choose(wrapper, USERS.field, 'delta')
    expect(apply()).toBeDefined()
    await choose(wrapper, OWNER.field, 'current')
    expect(apply()).toBeUndefined()
  })

  it('sends one resolution per delta with the choices', async () => {
    const { wrapper, api } = await mountDialog([USERS, OWNER])
    await choose(wrapper, USERS.field, 'delta')
    await choose(wrapper, OWNER.field, 'current')
    await wrapper.get('[data-testid="merge-apply"]').trigger('click')
    await flushPromises()
    expect(api.resolutions).toEqual([
      { deltaId: MODIFIED_ERP.id, fields: [{ field: USERS.field, choice: 'delta' }, { field: OWNER.field, choice: 'current' }] },
    ])
    expect(wrapper.emitted('resolved')).toHaveLength(1)
  })

  it('sends an own value', async () => {
    const { wrapper, api } = await mountDialog()
    await choose(wrapper, USERS.field, 'value')
    const field = wrapper.get(`[data-testid="merge-value-${USERS.field}"]`)
    await (field.element.tagName === 'INPUT' ? field : field.get('input')).setValue('18')
    await wrapper.get('[data-testid="merge-apply"]').trigger('click')
    await flushPromises()
    expect(api.resolutions[0]!.fields).toEqual([{ field: USERS.field, choice: 'value', value: '18' }])
  })

  it('offers no own value for a redacted field and shows no values', async () => {
    const { wrapper } = await mountDialog([goLiveConflict(MODIFIED_ERP.id, { redacted: true, before: null, current: null, after: null })])
    const conflict = wrapper.get(`[data-testid="merge-conflict-${USERS.field}"]`)
    expect(conflict.text()).toContain('Restricted')
    expect(conflict.text()).not.toContain('Enter an own value')
    expect(conflict.text()).not.toContain('15')
  })
})

// spec solution/solution-go-live "Snapshot values at the go-live" (US-154 AC4; design D12 of
// us-153-open-issues)
describe('DeltaMergeDialog with values only a snapshot keeps', () => {
  const KEPT = goLiveConflict(MODIFIED_ERP.id, {
    kind: 'not-in-metamodel',
    field: 'fd000000-0000-4000-8000-000000000007',
    label: 'Cost Center',
    dataType: 'string',
    before: null,
    current: null,
    after: 'CC-100',
  })

  it('offers "Discard the value" and "Resolve later" and needs no choice', async () => {
    const { wrapper } = await mountDialog([KEPT])
    const conflict = wrapper.get(`[data-testid="merge-conflict-${KEPT.field}"]`).text()
    expect(conflict).toContain('kept by this solution: CC-100')
    expect(conflict).toContain('Discard the value')
    expect(conflict).toContain('Resolve later as an open issue')
    expect(wrapper.get('[data-testid="merge-apply"]').attributes('disabled')).toBeUndefined()
  })

  it('sends "discard" and nothing for "Resolve later"', async () => {
    const later = await mountDialog([KEPT])
    await later.wrapper.get('[data-testid="merge-apply"]').trigger('click')
    await flushPromises()
    expect(later.api.resolutions).toEqual([])
    expect(later.wrapper.emitted('resolved')).toHaveLength(1)

    const discarded = await mountDialog([KEPT])
    await choose(discarded.wrapper, KEPT.field, 'discard')
    await discarded.wrapper.get('[data-testid="merge-apply"]').trigger('click')
    await flushPromises()
    expect(discarded.api.resolutions).toEqual([{ deltaId: MODIFIED_ERP.id, fields: [{ field: KEPT.field, choice: 'discard' }] }])
  })
})
