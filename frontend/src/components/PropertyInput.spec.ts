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

  // the tags of "Affected attributes" (design D5 of us-083-bpmn-data-objects)
  describe('tag mode of a text', () => {
    const tagsOf = (count: number) => Array.from({ length: count }, (_, index) => `tag${index}`).join('\n')
    const mountTags = (modelValue: string | undefined) => mountInput('text', { tags: true, modelValue })
    async function enter(wrapper: ReturnType<typeof mountTags>, text: string) {
      const input = wrapper.get('input')
      await input.setValue(text)
      await input.trigger('keydown', { key: 'Enter' })
      await flushPromises()
    }

    it('shows the stored lines as tags and no text area', () => {
      const wrapper = mountTags('status\nreviewedBy')
      expect(wrapper.find('textarea').exists()).toBe(false)
      expect(wrapper.findAll('[data-slot="item"]').map((tag) => tag.text())).toEqual(['status', 'reviewedBy'])
    })

    it('stores a new tag as one more line', async () => {
      const wrapper = mountTags('status')
      await enter(wrapper, 'reviewedBy')
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['status\nreviewedBy'])
      expect(wrapper.emitted('tagRefused')?.at(-1)).toEqual([undefined])
    })

    it('takes the 50th tag and refuses the 51st', async () => {
      const full = mountTags(tagsOf(49))
      await enter(full, 'last')
      expect((full.emitted('update:modelValue')?.at(-1)?.[0] as string).split('\n')).toHaveLength(50)
      const over = mountTags(tagsOf(50))
      await enter(over, 'one more')
      const stored = over.emitted('update:modelValue')?.at(-1)?.[0] as string | undefined
      expect(stored === undefined || stored.split('\n').length === 50).toBe(true)
      expect(over.emitted('tagRefused')?.some((event) => event[0] === 'count')).toBe(true)
    })

    it('takes a tag of 100 characters and refuses one of 101', async () => {
      const ok = mountTags('')
      await enter(ok, 'a'.repeat(100))
      expect(ok.emitted('update:modelValue')?.at(-1)).toEqual(['a'.repeat(100)])
      const long = mountTags('')
      await enter(long, 'a'.repeat(101))
      const stored = long.emitted('update:modelValue')?.at(-1)?.[0] as string | undefined
      expect(stored === undefined || stored === '').toBe(true)
    })

    it('refuses an empty tag and one of blanks, and drops a duplicate', async () => {
      const wrapper = mountTags('status')
      await enter(wrapper, '   ')
      await enter(wrapper, ' status ')
      const stored = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as string | undefined
      expect(stored === undefined || stored === 'status').toBe(true)
    })

    it('stays a text area for other properties and screens', () => {
      expect(mountInput('text', { modelValue: 'a\nb' }).find('textarea').exists()).toBe(true)
    })
  })
})
