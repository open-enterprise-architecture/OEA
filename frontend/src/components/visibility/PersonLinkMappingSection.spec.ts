import ui from '@nuxt/ui/vue-plugin'
import USelect from '@nuxt/ui/components/Select.vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { VISIBILITY_IDS, visibilityApi } from '../../test/visibility'
import PersonLinkMappingSection from './PersonLinkMappingSection.vue'

// spec identity/person-entity-link "Account mapping" on SCR-032 (us-075-lane-assignments D5)

const mounted: VueWrapper[] = []

async function open(options: Parameters<typeof visibilityApi>[0] = {}) {
  const api = visibilityApi(options)
  const wrapper = mount(PersonLinkMappingSection, { props: { fetchFn: api.fetch }, global: { plugins: [ui] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

describe('Account mapping section', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
  })

  it('shows no mapping at first and offers the save after a choice', async () => {
    const { wrapper, api } = await open()
    expect(wrapper.get('[data-testid="person-link-mapping-save"]').attributes('disabled')).toBeDefined()
    await wrapper.findAllComponents(USelect)[0]!.setValue(VISIBILITY_IDS.accountId)
    await wrapper.findAllComponents(USelect)[1]!.setValue('idp-subject')
    await wrapper.get('[data-testid="person-link-mapping-save"]').trigger('click')
    await flushPromises()
    expect(api.mappingPuts).toEqual([{ propertyDefId: VISIBILITY_IDS.accountId, accountAttribute: 'idp-subject' }])
    expect(wrapper.get('[data-testid="person-link-mapping-notice"]').text()).toContain('saved')
  })

  it('offers only the string properties', async () => {
    const { wrapper } = await open()
    const options = (wrapper.findAllComponents(USelect)[0]!.vm.$props as unknown as { items: { label: string }[] }).items.map((item) => item.label)
    expect(options).toEqual(expect.arrayContaining(['Account ID', 'Classification']))
    expect(options).not.toContain('Investment Cost')
  })

  it('clears the mapping with a null property', async () => {
    const { wrapper, api } = await open({ mapping: { propertyDefId: VISIBILITY_IDS.accountId, accountAttribute: 'email' } })
    await wrapper.findAllComponents(USelect)[0]!.setValue('none')
    await wrapper.get('[data-testid="person-link-mapping-save"]').trigger('click')
    await flushPromises()
    expect(api.mappingPuts).toEqual([{ propertyDefId: null, accountAttribute: null }])
  })

  it('names a rejected property', async () => {
    const { wrapper } = await open({ rejectMapping: true })
    await wrapper.findAllComponents(USelect)[0]!.setValue(VISIBILITY_IDS.classification)
    await wrapper.get('[data-testid="person-link-mapping-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="person-link-mapping-failure"]').text()).toContain('restricted to roles')
  })

  it('shows nothing when the mapping cannot be read', async () => {
    const wrapper = mount(PersonLinkMappingSection, { props: { fetchFn: async () => new Response('{}', { status: 403 }) }, global: { plugins: [ui] } })
    mounted.push(wrapper)
    await flushPromises()
    expect(wrapper.find('[data-testid="person-link-mapping"]').exists()).toBe(false)
  })
})
