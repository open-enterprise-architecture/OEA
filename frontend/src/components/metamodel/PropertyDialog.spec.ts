import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../../session/store'
import { choose, field, mountWithMetamodel } from '../../test/metamodelMount'
import PropertyDialog from './PropertyDialog.vue'

// spec metamodel/configuration-screen "Editing properties and categories"
describe('PropertyDialog', () => {
  beforeEach(() => {
    session.me = undefined
  })

  // "Enum property with values"
  it('creates an enum property with three values, each based on the previous version', async () => {
    const { wrapper, api } = await mountWithMetamodel(PropertyDialog)
    await field(wrapper, 'property-name').setValue('trustLevel')
    await choose(wrapper, 'property-data-type', 'enum')
    for (const value of ['low', 'medium', 'high']) {
      await field(wrapper, 'property-value-input').setValue(value)
      await wrapper.get('[data-testid="property-value-add"]').trigger('click')
    }
    expect(wrapper.findAll('[data-testid="property-new-value"]')).toHaveLength(3)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const values = api.requests.filter((request) => request.path.endsWith('/enum-values'))
    expect(values.map((request) => [request.body, request.ifMatch])).toEqual([
      [{ value: 'low' }, '"1"'],
      [{ value: 'medium' }, '"2"'],
      [{ value: 'high' }, '"3"'],
    ])
    const created = api.state.properties.find((property) => property.name === 'trustLevel')!
    expect(created.enumValues?.map((value) => value.value)).toEqual(['low', 'medium', 'high'])
    expect(wrapper.emitted('saved')).toEqual([[created.id]])
  })

  it('sends only the limits of the data type', async () => {
    const { wrapper, api } = await mountWithMetamodel(PropertyDialog)
    await field(wrapper, 'property-name').setValue('Code')
    await field(wrapper, 'property-max-length').setValue('12')
    await field(wrapper, 'property-pattern').setValue('^[A-Z]+$')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.requests.find((request) => request.method === 'POST')?.body).toEqual({
      name: 'Code',
      dataType: 'string',
      categoryId: null,
      maxLength: 12,
      minValue: null,
      maxValue: null,
      pattern: '^[A-Z]+$',
    })
  })
})
