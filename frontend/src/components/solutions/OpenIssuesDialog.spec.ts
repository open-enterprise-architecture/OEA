import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { OPEN_ISSUES, openIssue, SOLUTIONS, solutionApi } from '../../test/solutions'
import OpenIssuesDialog from './OpenIssuesDialog.vue'

// spec solution/solution-go-live "Resolve open issues on the page \"Solution\"" and "Resolve a
// value not in the metamodel" (US-153 AC2, AC3, US-154 AC4; design D7 of us-153-open-issues)

const SOLUTION = { ...SOLUTIONS[0]!, status: 'implemented' as const }
const CONTACT = OPEN_ISSUES[0]!
const USERS = OPEN_ISSUES[1]!

async function mountDialog(issues = OPEN_ISSUES, rejectIssues: string[] = []) {
  const api = solutionApi({ solutions: [SOLUTION], openIssues: issues, rejectIssues })
  const wrapper = mount(OpenIssuesDialog, {
    props: { solutionId: SOLUTION.id, issues, fetchFn: api.fetch },
    global: { plugins: [ui] },
  })
  await flushPromises()
  return { wrapper, api }
}

/** The input of an issue; Nuxt UI may put the test ID on the input or on its wrapper. */
function input(wrapper: Awaited<ReturnType<typeof mountDialog>>['wrapper'], id: string) {
  const direct = wrapper.find(`input[data-testid="open-issue-value-${id}"]`)
  return direct.exists() ? direct : wrapper.get(`[data-testid="open-issue-value-${id}"] input`)
}

/** The removal checkbox of an issue, on the element itself or inside its wrapper. */
function removal(wrapper: Awaited<ReturnType<typeof mountDialog>>['wrapper'], id: string) {
  const element = wrapper.get(`[data-testid="open-issue-remove-${id}"]`)
  return ['BUTTON', 'INPUT'].includes(element.element.tagName) ? element : element.get('button, input')
}

const apply = (wrapper: Awaited<ReturnType<typeof mountDialog>>['wrapper']) =>
  wrapper.get('[data-testid="open-issues-apply"]').attributes('disabled')

describe('OpenIssuesDialog', () => {
  it('groups the issues by entity with the current value', async () => {
    const { wrapper } = await mountDialog()
    expect(wrapper.text()).toContain('Salesforce')
    expect(wrapper.text()).toContain('ERP-Core')
    expect(wrapper.get(`[data-testid="open-issue-entry-${USERS.id}"]`).text()).toContain('Current value: 250')
  })

  it('offers "Remove the value" only where removing resolves the issue', async () => {
    const { wrapper } = await mountDialog()
    expect(wrapper.find(`[data-testid="open-issue-remove-${USERS.id}"]`).exists()).toBe(true)
    expect(wrapper.find(`[data-testid="open-issue-remove-${CONTACT.id}"]`).exists()).toBe(false)
  })

  it('types the input of an integer property as a number', async () => {
    const { wrapper } = await mountDialog()
    expect(input(wrapper, USERS.id).attributes('type')).toBe('number')
    expect(input(wrapper, CONTACT.id).attributes('type')).toBe('text')
  })

  it('enables "Apply" once every issue has a value or a removal', async () => {
    const { wrapper } = await mountDialog()
    expect(apply(wrapper)).toBeDefined()
    await input(wrapper, CONTACT.id).setValue('sales@example.com')
    expect(apply(wrapper)).toBeDefined()
    await removal(wrapper, USERS.id).trigger('click')
    await flushPromises()
    expect(apply(wrapper)).toBeUndefined()
  })

  it('sends a value and a removal and emits the issues afterwards', async () => {
    const { wrapper, api } = await mountDialog()
    await input(wrapper, CONTACT.id).setValue('sales@example.com')
    await input(wrapper, USERS.id).setValue('100')
    await wrapper.get('[data-testid="open-issues-apply"]').trigger('click')
    await flushPromises()
    expect(api.issueResolutions).toEqual([
      [
        { issueId: CONTACT.id, value: 'sales@example.com', remove: false },
        { issueId: USERS.id, value: '100', remove: false },
      ],
    ])
    const resolved = wrapper.emitted('resolved')![0]![0] as typeof OPEN_ISSUES
    expect(resolved.every((issue) => issue.status === 'resolved')).toBe(true)
  })

  it('marks the issues the server rejects', async () => {
    const { wrapper } = await mountDialog(OPEN_ISSUES, [USERS.id])
    await input(wrapper, CONTACT.id).setValue('sales@example.com')
    await input(wrapper, USERS.id).setValue('101')
    await wrapper.get('[data-testid="open-issues-apply"]').trigger('click')
    await flushPromises()
    expect(wrapper.find(`[data-testid="open-issue-error-${USERS.id}"]`).exists()).toBe(true)
    expect(wrapper.find(`[data-testid="open-issue-error-${CONTACT.id}"]`).exists()).toBe(false)
    expect(wrapper.get('[data-testid="open-issues-failure"]').text()).toContain('Check the marked issues')
    expect(wrapper.emitted('resolved')).toBeUndefined()
  })

  it('discards the kept value of a property no longer in the metamodel', async () => {
    const kept = openIssue({ id: '0e000000-0000-4000-8000-000000000003', kind: 'not-in-metamodel', keptValue: 'CC-100', removable: true, label: 'Cost Center' })
    const { wrapper } = await mountDialog([kept])
    expect(wrapper.text()).toContain('Kept value: CC-100')
    expect(wrapper.get(`[data-testid="open-issue-entry-${kept.id}"]`).text()).toContain('Discard the kept value')
  })
})
