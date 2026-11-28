import ui from '@nuxt/ui/vue-plugin'
import USelect from '@nuxt/ui/components/Select.vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { IDS, metamodelApi, type MetamodelApi } from '../../test/metamodel'
import LineageModelSection from './LineageModelSection.vue'

// spec lineage/lineage-model "Lineage model setting" on SCR-030 (us-065-lineage-model-and-query D3, D6)

const mounted: VueWrapper[] = []

async function open(canWrite: boolean, options: Parameters<typeof metamodelApi>[0] = {}): Promise<{ wrapper: VueWrapper; api: MetamodelApi }> {
  const api = metamodelApi(options)
  const wrapper = mount(LineageModelSection, { props: { canWrite, fetchFn: api.fetch }, global: { plugins: [ui] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

const PARTS = ['dataObject', 'dataFlow', 'carriesData', 'dataInput', 'dataOutput']
const selectOf = (wrapper: VueWrapper, part: string) => wrapper.findAllComponents(USelect)[PARTS.indexOf(part)]!

const puts = (api: MetamodelApi) => api.requests.filter((request) => request.path === '/api/v1/metamodel/lineage-model' && request.method === 'PUT')

describe('Lineage model section', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
  })

  it('shows a reader the parts as text and offers no save', async () => {
    const { wrapper } = await open(false, { lineage: { dataObject: IDS.zone } })
    expect(wrapper.get('[data-testid="lineage-value-dataObject"]').text()).toBe('Network Zone')
    expect(wrapper.get('[data-testid="lineage-value-dataFlow"]').text()).toBe('Not set')
    expect(wrapper.find('[data-testid="lineage-save"]').exists()).toBe(false)
  })

  it('offers the save only after a change and writes the whole setting with the version read', async () => {
    const { wrapper, api } = await open(true)
    expect(wrapper.get('[data-testid="lineage-save"]').attributes('disabled')).toBeDefined()
    await selectOf(wrapper, 'dataObject').setValue(IDS.zone)
    await selectOf(wrapper, 'dataFlow').setValue(IDS.dataFlow)
    expect(wrapper.get('[data-testid="lineage-save"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="lineage-save"]').trigger('click')
    await flushPromises()
    expect(puts(api)).toHaveLength(1)
    expect(puts(api)[0]!.ifMatch).toBe('"1"')
    expect(puts(api)[0]!.body).toEqual({ dataObject: IDS.zone, dataFlow: IDS.dataFlow, carriesData: null, dataInput: null, dataOutput: null })
    expect(wrapper.get('[data-testid="lineage-notice"]').text()).toContain('saved')
    // the answer has the next version, and the save is offered again only after another change
    expect(wrapper.get('[data-testid="lineage-save"]').attributes('disabled')).toBeDefined()
  })

  it('offers element types for the element parts and connection types for the connection parts', async () => {
    const { wrapper } = await open(true)
    const element = (selectOf(wrapper, 'dataObject').vm.$props as unknown as { items: { label: string }[] }).items.map((item) => item.label)
    expect(element).toContain('Network Zone')
    expect(element).not.toContain('Data Flow')
    const connection = (selectOf(wrapper, 'carriesData').vm.$props as unknown as { items: { label: string }[] }).items.map((item) => item.label)
    expect(connection).toContain('Data Flow')
    expect(connection).not.toContain('Network Zone')
  })

  it('names the part that the server rejects with 422', async () => {
    const { wrapper } = await open(true)
    // a connection type as the data object
    await selectOf(wrapper, 'dataObject').setValue(IDS.dataFlow)
    await wrapper.get('[data-testid="lineage-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="lineage-failure"]').text()).toContain('Some parts do not fit')
    expect(wrapper.get('[data-testid="lineage-part-dataObject"]').text()).toContain('This part does not fit.')
    expect(wrapper.get('[data-testid="lineage-part-dataFlow"]').text()).not.toContain('This part does not fit.')
  })
})
