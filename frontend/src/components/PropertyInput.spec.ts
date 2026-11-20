import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { PropertyMapping } from '../api/metamodel'
import PropertyInput from './PropertyInput.vue'

// The input of one property value by its data type, shared by SCR-061 and the details panel of SCR-021
function mapping(dataType: string, extra: Record<string, unknown> = {}): PropertyMapping {
  return {
    id: 'm1',
    necessity: 'optional',
    validFrom: '2026-01-01',
    sortOrder: 1,
    propertyDefinition: { id: 'p1', name: 'Value', dataType, enumValues: [], refMetatype: null, ...extra },
  } as unknown as PropertyMapping
}

function mountInput(dataType: string, props: Record<string, unknown> = {}, extra: Record<string, unknown> = {}) {
  return mount(PropertyInput, { props: { mapping: mapping(dataType, extra), ...props }, global: { plugins: [ui] } })
}

describe('PropertyInput', () => {
  it.each([
    ['string', 'input[type="text"], input:not([type])'],
    ['url', 'input'],
    ['text', 'textarea'],
    ['integer', 'input[type="number"]'],
    ['date', 'input[type="date"]'],
  ])('renders an input for the data type %s', (dataType, selector) => {
    expect(mountInput(dataType).find(selector).exists()).toBe(true)
  })

  it('hands a typed number on as text', async () => {
    const wrapper = mountInput('integer')
    await wrapper.get('input').setValue('42')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['42'])
  })

  it('hands a switched boolean on as "true" or "false"', async () => {
    const wrapper = mountInput('boolean', { modelValue: 'false' })
    await wrapper.get('[role="switch"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['true'])
  })

  it('disables the input and marks it invalid on request', () => {
    const wrapper = mountInput('string', { disabled: true, invalid: true })
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
  })

  it('asks the injected search for the candidates of a reference when it is mounted and when the term changes', async () => {
    const search = vi.fn(async (term: string) => [{ label: `Linux ${term}`, value: '5' }])
    const wrapper = mountInput('reference', { searchReferences: search, modelValue: '5' }, { refMetatype: { id: 'c1', name: 'Component' } })
    await flushPromises()
    expect(search).toHaveBeenCalledWith('')
    const select = wrapper.findComponent({ name: 'SelectMenu' })
    expect(select.exists()).toBe(true)
    select.vm.$emit('update:searchTerm', 'lin')
    await flushPromises()
    expect(search).toHaveBeenLastCalledWith('lin')
  })

  it('keeps a selected value that the search does not list, with its name', async () => {
    const wrapper = mountInput('reference', { searchReferences: async () => [{ label: 'Other', value: '9' }], modelValue: '5', selectedLabel: 'Linux Cluster' })
    await flushPromises()
    const items = wrapper.findComponent({ name: 'SelectMenu' }).props('items') as { label: string; value: string }[]
    expect(items.map((entry) => entry.value)).toEqual(['5', '9'])
    expect(items[0]!.label).toBe('Linux Cluster')
  })

  it('searches nothing without a search function', async () => {
    const wrapper = mountInput('reference')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'SelectMenu' }).props('items')).toEqual([])
  })
})
