import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { metatypeById } from '../../metamodel/store'
import { session } from '../../session/store'
import { IDS } from '../../test/metamodel'
import { choose, field, mountWithMetamodel } from '../../test/metamodelMount'
import MetatypeDialog from './MetatypeDialog.vue'

// spec metamodel/configuration-screen "Editing metatypes" and "Warning on a collision"
describe('MetatypeDialog', () => {
  beforeEach(() => {
    session.me = undefined
  })

  it('creates an organization metatype with a base type', async () => {
    const { wrapper, api } = await mountWithMetamodel(MetatypeDialog)
    await field(wrapper, 'metatype-name').setValue('  SecurityZone ')
    await field(wrapper, 'metatype-abbreviation').setValue('SZ')
    await choose(wrapper, 'metatype-base', IDS.zone)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const post = api.requests.find((request) => request.method === 'POST')!
    expect(post.body).toEqual(expect.objectContaining({ name: 'SecurityZone', abbreviation: 'SZ', baseMetatypeId: IDS.zone, isConnection: false }))
    expect(post.ifMatch).toBeUndefined()
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  // "Name already taken"
  it('shows a taken name at the field and creates nothing', async () => {
    const { wrapper, api } = await mountWithMetamodel(MetatypeDialog)
    await field(wrapper, 'metatype-name').setValue('application component')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('This name is already used, regardless of upper and lower case.')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(api.state.metatypes).toHaveLength(4)
  })

  // "Overwriting after a warning"
  it('warns on a collision with the current state and overwrites on request', async () => {
    const { wrapper, api } = await mountWithMetamodel(MetatypeDialog, () => ({ metatype: metatypeById(IDS.zone) }))
    api.changeElsewhere(IDS.zone, 'NET')
    await field(wrapper, 'metatype-abbreviation').setValue('ZN')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="collision-warning"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="collision-abbreviation"]').text()).toBe('NET')
    expect(api.requests.filter((request) => request.method === 'PUT').map((request) => request.ifMatch)).toEqual(['"3"'])

    await wrapper.get('[data-testid="collision-overwrite"]').trigger('click')
    await flushPromises()
    expect(api.requests.filter((request) => request.method === 'PUT').map((request) => request.ifMatch)).toEqual(['"3"', '"4"'])
    expect(api.state.metatypes.find((metatype) => metatype.id === IDS.zone)?.abbreviation).toBe('ZN')
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('discards the input after a collision', async () => {
    const { wrapper, api } = await mountWithMetamodel(MetatypeDialog, () => ({ metatype: metatypeById(IDS.zone) }))
    api.changeElsewhere(IDS.zone, 'NET')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-testid="collision-discard"]').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(api.state.metatypes.find((metatype) => metatype.id === IDS.zone)?.abbreviation).toBe('NET')
  })
})
