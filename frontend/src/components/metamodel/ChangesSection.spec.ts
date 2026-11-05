import { flushPromises } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { mountWithMetamodel } from '../../test/metamodelMount'
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
    expect(wrapper.findAll('[data-testid="change-row"]')).toHaveLength(7)
    expect(wrapper.find('[data-testid="changes-more"]').exists()).toBe(false)
  })

})
