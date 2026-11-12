import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { APPLICATION, COMPONENT, HOSTED_ON, OWNER, TIER, USERS, LIVE, COST, solutionApi } from '../../test/solutions'
import SolutionDeltaDialog from './SolutionDeltaDialog.vue'

// spec solution/solution-deltas "Write actions on the page \"Solution\"" and "Checks of a new
// entity" (US-040 AC1, AC4; design D9 of us-040-entity-deltas): the dialog "New entity"
const ERP = '5d000000-0000-4000-8000-000000000002'

async function mountDialog(options: Parameters<typeof solutionApi>[0] = {}) {
  const api = solutionApi(options)
  const wrapper = mount(SolutionDeltaDialog, { props: { solutionId: ERP, fetchFn: api.fetch }, global: { plugins: [ui] } })
  await flushPromises()
  return Object.assign(wrapper, { api })
}

function selects(wrapper: VueWrapper) {
  return wrapper.findAllComponents({ name: 'Select' })
}

async function chooseMetatype(wrapper: VueWrapper, id: string) {
  selects(wrapper)[0]!.vm.$emit('update:modelValue', id)
  await flushPromises()
}

function input(wrapper: VueWrapper, testId: string) {
  const field = wrapper.get(`[data-testid="${testId}"]`)
  return ['INPUT', 'TEXTAREA'].includes(field.element.tagName) ? field : field.get('input, textarea')
}

async function save(wrapper: VueWrapper) {
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('SolutionDeltaDialog', () => {
  it('offers the element metatypes only', async () => {
    const wrapper = await mountDialog()
    const labels = (selects(wrapper)[0]!.props('items') as { label: string }[]).map((item) => item.label)
    expect(labels).toEqual(['Application', 'Technology Component'])
  })

  it('shows one field per mapping of the chosen metatype by data type, mandatory ones marked', async () => {
    const wrapper = await mountDialog()
    await chooseMetatype(wrapper, APPLICATION)
    expect(wrapper.find('[data-testid="delta-property-Owner"] input').exists()).toBe(true)
    expect(wrapper.get('[data-testid="delta-property-Users"] input').attributes('type')).toBe('number')
    const required = Object.fromEntries(
      wrapper.findAllComponents({ name: 'FormField' }).map((field) => [field.props('label'), field.props('required')]),
    )
    expect(required.Owner).toBe(true)
    expect(required.Users).toBeFalsy()
    expect(wrapper.find('[data-testid="delta-property-Live"] button[role="switch"]').exists()).toBe(true)
    const tier = wrapper.get('[data-testid="delta-property-Tier"]').findComponent({ name: 'Select' })
    expect((tier.props('items') as { value: string }[]).map((item) => item.value)).toEqual(['gold', 'silver'])
    const hosting = wrapper.get('[data-testid="delta-property-Hosted on"]').findComponent({ name: 'SelectMenu' })
    expect((hosting.props('items') as { label: string }[]).map((item) => item.label)).toEqual(['Message Broker', 'PostgreSQL Cluster'])
    // a metatype without mappings has no property fields
    await chooseMetatype(wrapper, COMPONENT)
    expect(wrapper.find('[data-testid="delta-property-Owner"]').exists()).toBe(false)
  })

  it('sends name, description and the values as text and emits the created delta', async () => {
    const wrapper = await mountDialog()
    await chooseMetatype(wrapper, APPLICATION)
    await input(wrapper, 'delta-name').setValue('Salesforce')
    await input(wrapper, 'delta-description').setValue('Sales CRM')
    await input(wrapper, 'delta-property-Owner').setValue('Sales')
    await input(wrapper, 'delta-property-Users').setValue('250')
    wrapper.get('[data-testid="delta-property-Tier"]').findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 'gold')
    wrapper.get('[data-testid="delta-property-Hosted on"]').findComponent({ name: 'SelectMenu' }).vm.$emit('update:modelValue', '106')
    await wrapper.get('[data-testid="delta-property-Live"] button[role="switch"]').trigger('click')
    await save(wrapper)
    expect(wrapper.api.deltaPosts).toEqual([
      {
        deltaType: 'new',
        metatypeId: APPLICATION,
        name: 'Salesforce',
        description: 'Sales CRM',
        properties: [
          { propertyDefId: OWNER, value: 'Sales' },
          { propertyDefId: USERS, value: '250' },
          { propertyDefId: TIER, value: 'gold' },
          { propertyDefId: HOSTED_ON, value: '106' },
          { propertyDefId: LIVE, value: 'true' },
        ],
      },
    ])
    expect(wrapper.emitted('created')![0]![0]).toMatchObject({ name: 'Salesforce', deltaType: 'new' })
  })

  it('keeps the dialog open and shows the message at the named property', async () => {
    const wrapper = await mountDialog()
    await chooseMetatype(wrapper, APPLICATION)
    await input(wrapper, 'delta-name').setValue('Salesforce')
    await input(wrapper, 'delta-property-Owner').setValue('Sales')
    await input(wrapper, 'delta-property-Users').setValue('1.5')
    await save(wrapper)
    expect(wrapper.emitted('created')).toBeUndefined()
    expect(wrapper.get('[data-testid="delta-property-Users"]').text()).toContain('The value must be an integer.')
  })

  it('shows a general message for a missing mandatory property, and the duplicate at the name', async () => {
    const wrapper = await mountDialog()
    await chooseMetatype(wrapper, APPLICATION)
    await input(wrapper, 'delta-name').setValue('Warehouse Cloud')
    await save(wrapper)
    expect(wrapper.get('[data-testid="delta-failure"]').text()).toContain('The mandatory property Owner has no value.')
    await input(wrapper, 'delta-property-Owner').setValue('Logistics')
    await save(wrapper)
    await input(wrapper, 'delta-name').setValue('warehouse cloud')
    await save(wrapper)
    expect(wrapper.text()).toContain('A delta already exists for this entity in this solution')
    expect(wrapper.emitted('created')).toHaveLength(1)
  })

  it('names a restricted property it may not set', async () => {
    const wrapper = await mountDialog()
    await chooseMetatype(wrapper, APPLICATION)
    await input(wrapper, 'delta-name').setValue('Salesforce')
    await input(wrapper, 'delta-property-Owner').setValue('Sales')
    await input(wrapper, 'delta-property-Cost').setValue('100')
    await save(wrapper)
    expect(wrapper.api.deltaPosts.at(-1)).toMatchObject({ properties: expect.arrayContaining([{ propertyDefId: COST, value: '100' }]) })
    expect(wrapper.get('[data-testid="delta-failure"]').text()).toBe(
      'You may not set a restricted property. Leave it empty or ask a person with its write role.',
    )
  })

  it('reports a completed solution', async () => {
    const wrapper = await mountDialog({ completeOnWrite: true })
    await chooseMetatype(wrapper, COMPONENT)
    await input(wrapper, 'delta-name').setValue('Gateway')
    await save(wrapper)
    expect(wrapper.emitted('completed')).toHaveLength(1)
  })

  // boundaries: the name ends at 255 characters, the description at 2000 (maxlength)
  it.each([
    ['delta-name', 255],
    ['delta-description', 2000],
  ])('limits %s to %i characters', async (testId, max) => {
    const wrapper = await mountDialog()
    expect(input(wrapper, testId).attributes('maxlength')).toBe(String(max))
  })

  it('blocks saving without a metatype or with a blank name', async () => {
    const wrapper = await mountDialog()
    await input(wrapper, 'delta-name').setValue('Salesforce')
    expect(wrapper.get('[data-testid="delta-save"]').attributes('disabled')).toBeDefined()
    await chooseMetatype(wrapper, COMPONENT)
    expect(wrapper.get('[data-testid="delta-save"]').attributes('disabled')).toBeUndefined()
    await input(wrapper, 'delta-name').setValue('   ')
    expect(wrapper.get('[data-testid="delta-save"]').attributes('disabled')).toBeDefined()
  })
})
