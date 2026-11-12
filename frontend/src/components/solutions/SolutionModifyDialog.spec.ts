import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { COST, MODIFIED_ERP, OWNER, TIER, USERS, solutionApi } from '../../test/solutions'
import SolutionModifyDialog from './SolutionModifyDialog.vue'

// spec solution/solution-deltas "Actions \"Modify\" and \"Edit\" on the page \"Solution\"" and
// "Before values recorded by the server" (US-041 AC1, AC2; design D6 of us-041-modified-delta)
const ERP_SOLUTION = '5d000000-0000-4000-8000-000000000002'
const ERP_CORE = 102

async function mountDialog(options: { delta?: typeof MODIFIED_ERP; api?: Parameters<typeof solutionApi>[0] } = {}) {
  const api = solutionApi({ deltas: options.delta ? [structuredClone(options.delta)] : [], ...options.api })
  const wrapper = mount(SolutionModifyDialog, {
    props: { solutionId: ERP_SOLUTION, entityId: ERP_CORE, delta: options.delta, fetchFn: api.fetch },
    global: { plugins: [ui] },
  })
  await flushPromises()
  return Object.assign(wrapper, { api })
}

function input(wrapper: VueWrapper, testId: string) {
  const field = wrapper.get(`[data-testid="${testId}"]`)
  return ['INPUT', 'TEXTAREA'].includes(field.element.tagName) ? field : field.get('input, textarea')
}

function current(wrapper: VueWrapper, testId: string) {
  return wrapper.get(`[data-testid="${testId}"] [data-testid="modify-current"]`).text()
}

async function save(wrapper: VueWrapper) {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('SolutionModifyDialog', () => {
  it('shows the current values read-only, a restricted one as hidden', async () => {
    const wrapper = await mountDialog()
    expect(current(wrapper, 'modify-field-name')).toBe('Current: ERP-Core')
    expect(current(wrapper, 'modify-property-Owner')).toBe('Current: IT')
    expect(current(wrapper, 'modify-property-Users')).toBe('Current: 250')
    expect(current(wrapper, 'modify-property-Tier')).toBe('Current: silver')
    expect(current(wrapper, 'modify-property-Cost')).toBe('Current: hidden')
    expect(current(wrapper, 'modify-property-Live')).toBe('Current: no value')
    // the current value is text, not an input
    expect(wrapper.get('[data-testid="modify-property-Users"] [data-testid="modify-current"]').element.tagName).toBe('P')
  })

  it('sends only the changed fields, a cleared input as empty', async () => {
    const wrapper = await mountDialog()
    await input(wrapper, 'modify-name').setValue('ERP-Core 2')
    await input(wrapper, 'modify-property-Users').setValue('400')
    await input(wrapper, 'modify-property-Owner').setValue('')
    await save(wrapper)
    expect(wrapper.api.deltaPosts).toEqual([
      {
        deltaType: 'modified',
        entityId: ERP_CORE,
        name: 'ERP-Core 2',
        properties: [
          { propertyDefId: USERS, value: '400' },
          { propertyDefId: OWNER, value: '' },
        ],
      },
    ])
    // Owner is mandatory, so the fake rejects removing it
    expect(wrapper.get('[data-testid="modify-failure"]').text()).toContain('The mandatory property Owner has no value.')
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('emits the saved modification', async () => {
    const wrapper = await mountDialog()
    await input(wrapper, 'modify-property-Users').setValue('400')
    await save(wrapper)
    const saved = wrapper.emitted('saved')![0]![0] as typeof MODIFIED_ERP
    expect(saved.deltaType).toBe('modified')
    expect(saved.changes[0]).toMatchObject({ propertyDefId: USERS, before: { valueInt: 250 }, after: { valueInt: 400 } })
  })

  it('shows the message of a rejected value at its field', async () => {
    const wrapper = await mountDialog()
    await input(wrapper, 'modify-property-Users').setValue('1.5')
    await save(wrapper)
    expect(wrapper.get('[data-testid="modify-property-Users"]').text()).toContain('The value must be an integer.')
  })

  it('names a restricted property it may not set', async () => {
    const wrapper = await mountDialog()
    await input(wrapper, 'modify-property-Cost').setValue('100')
    await save(wrapper)
    expect(wrapper.get('[data-testid="modify-failure"]').text()).toBe(
      'You may not set a restricted property. Leave it empty or ask a person with its write role.',
    )
  })

  it('edits a delta from its after values and sends the full set', async () => {
    const wrapper = await mountDialog({ delta: MODIFIED_ERP })
    expect((input(wrapper, 'modify-property-Users').element as HTMLInputElement).value).toBe('400')
    await input(wrapper, 'modify-property-Users').setValue('500')
    await save(wrapper)
    expect(wrapper.api.deltaPosts).toEqual([])
    expect(wrapper.api.deltaPuts).toEqual([
      {
        properties: [
          { propertyDefId: USERS, value: '500' },
          { propertyDefId: TIER, value: 'gold' },
        ],
      },
    ])
    const saved = wrapper.emitted('saved')![0]![0] as typeof MODIFIED_ERP
    // the recorded before value stays
    expect(saved.changes[0]).toMatchObject({ before: { valueInt: 250 }, after: { valueInt: 500 } })
  })

  it('reports a completed solution', async () => {
    const wrapper = await mountDialog({ api: { completeOnWrite: true } })
    await input(wrapper, 'modify-property-Users').setValue('400')
    await save(wrapper)
    expect(wrapper.emitted('completed')).toHaveLength(1)
  })

  // boundaries: the new name ends at 255 characters, the new description at 2000 (maxlength)
  it.each([
    ['modify-name', 255],
    ['modify-description', 2000],
  ])('limits %s to %i characters', async (testId, max) => {
    const wrapper = await mountDialog()
    expect(input(wrapper, testId).attributes('maxlength')).toBe(String(max))
  })

  it('leaves out a restricted property it may not read when editing', async () => {
    const hidden = structuredClone(MODIFIED_ERP)
    hidden.changes.push({
      field: 'property',
      propertyDefId: COST,
      propertyName: 'Cost',
      dataType: 'integer',
      redacted: true,
      before: null,
      after: null,
    })
    const wrapper = await mountDialog({ delta: hidden })
    expect((input(wrapper, 'modify-property-Cost').element as HTMLInputElement).value).toBe('')
  })
})
