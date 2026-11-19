import { flushPromises } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { choose, mountWithMetamodel } from '../../test/metamodelMount'
import ChangesSection from './ChangesSection.vue'

// paging of the section "Changes" (design D6 of us-034-metamodel-changes)
describe('ChangesSection', () => {
  it('appends the next page with load more', async () => {
    const { wrapper, api } = await mountWithMetamodel(ChangesSection, { pageSize: 3 })
    expect(wrapper.findAll('[data-testid="change-row"]')).toHaveLength(3)
    await wrapper.get('[data-testid="changes-more"]').trigger('click')
    await flushPromises()
    expect(api.requests.at(-1)?.path).toContain('page=1')
    expect(wrapper.findAll('[data-testid="change-row"]')).toHaveLength(6)
    await wrapper.get('[data-testid="changes-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="change-row"]')).toHaveLength(8)
    expect(wrapper.find('[data-testid="changes-more"]').exists()).toBe(false)
  })

  // spec metamodel/architecture-layers "Create a layer": the kind "Layers" lists the layer events
  it('filters the changes by the kind layer', async () => {
    const { wrapper, api } = await mountWithMetamodel(ChangesSection)
    await choose(wrapper, 'changes-kind', 'layer')
    expect(api.requests.at(-1)?.path).toContain('kind=layer')
    const rows = wrapper.findAll('[data-testid="change-row"]')
    expect(rows).toHaveLength(1)
    expect(rows[0]!.get('[data-testid="change-action"]').text()).toBe('Layer created')
    expect(rows[0]!.get('[data-testid="change-element"]').text()).toBe('Strategy')
    // the kind is offered by name
    const kinds = wrapper.findAllComponents({ name: 'Select' }).find((candidate) => candidate.vm.$attrs['data-testid'] === 'changes-kind')!
    expect((kinds.props('items') as { label: string; value: string }[]).find((item) => item.value === 'layer')?.label).toBe('Layers')
  })

})
