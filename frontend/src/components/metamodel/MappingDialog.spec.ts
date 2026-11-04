import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { metatypeById, propertyById } from '../../metamodel/store'
import { session } from '../../session/store'
import { IDS } from '../../test/metamodel'
import { choose, field, mountWithMetamodel, select } from '../../test/metamodelMount'
import MappingDialog from './MappingDialog.vue'

// spec metamodel/configuration-screen "Editing property mappings"
describe('MappingDialog', () => {
  beforeEach(() => {
    session.me = undefined
  })

  // "New mandatory property on a metatype with entities"
  it('maps a property as mandatory and reports the marked entities', async () => {
    const { wrapper, api } = await mountWithMetamodel(
      MappingDialog,
      () => ({ metatype: metatypeById(IDS.service) }),
      { unmarkedEntities: 3 },
    )
    await choose(wrapper, 'mapping-property', IDS.owner)
    wrapper.findAllComponents({ name: 'RadioGroup' })[0]!.vm.$emit('update:modelValue', 'mandatory')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const post = api.requests.find((request) => request.method === 'POST')!
    expect(post.ifMatch).toBe('"1"')
    expect(post.body).toEqual(expect.objectContaining({ propertyDefId: IDS.owner, necessity: 'mandatory', defaultValue: null }))
    expect(wrapper.emitted('saved')).toEqual([[3]])
  })

  // "Overriding a package mapping"
  it('adds a dated entry to an imported mapping', async () => {
    const { wrapper, api } = await mountWithMetamodel(MappingDialog, () => ({
      metatype: metatypeById(IDS.component),
      property: propertyById(IDS.status),
    }))
    expect(wrapper.get('[data-testid="mapping-fixed-property"]').text()).toBe('Status')
    await field(wrapper, 'mapping-valid-from').setValue('2099-06-01')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.requests.find((request) => request.method === 'POST')?.body).toEqual(
      expect.objectContaining({ propertyDefId: IDS.status, validFrom: '2099-06-01' }),
    )
    expect(api.state.entries.filter((entry) => entry.propertyDefId === IDS.status)).toHaveLength(3)
  })

  it('offers only organization properties that the metatype does not have', async () => {
    const { wrapper } = await mountWithMetamodel(MappingDialog, () => ({ metatype: metatypeById(IDS.zone) }))
    expect(select(wrapper, 'mapping-property')!.props('items')).toEqual([])
  })
})
