import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { metatypeById } from '../../metamodel/store'
import { session } from '../../session/store'
import { IDS } from '../../test/metamodel'
import { choose, mountWithMetamodel } from '../../test/metamodelMount'
import MetatypeDetail from './MetatypeDetail.vue'

// spec metamodel/configuration-screen "Re-import settings"
describe('MetatypeDetail', () => {
  beforeEach(() => {
    session.me = undefined
  })

  // "Setting a metatype to remove"
  it('shows remove as the setting that applies after it is set', async () => {
    const { wrapper, api } = await mountWithMetamodel(MetatypeDetail, () => ({ metatype: metatypeById(IDS.component), canWrite: true }))
    expect(wrapper.get('[data-testid="metatype-reimport-effective"]').text()).toBe('Applies: Keep own properties')
    await choose(wrapper, 'metatype-reimport', 'remove')
    expect(api.requests.find((request) => request.method === 'PUT')).toEqual(
      expect.objectContaining({ body: { organizationMappings: 'remove' }, ifMatch: '"1"' }),
    )
    await wrapper.setProps({ metatype: metatypeById(IDS.component) })
    await flushPromises()
    expect(wrapper.get('[data-testid="metatype-reimport-effective"]').text()).toBe('Applies: Remove own properties')
  })

  // spec metamodel/configuration-screen "Parallel-change setting on the metatype detail" (US-044)
  describe('parallel changes', () => {
    function parallelSwitch(wrapper: Awaited<ReturnType<typeof mountWithMetamodel>>['wrapper']) {
      return wrapper.findAllComponents({ name: 'Switch' }).find((candidate) => candidate.vm.$attrs['data-testid'] === 'metatype-parallel-changes')
    }

    it('turns the setting on for an imported metatype with its version', async () => {
      const { wrapper, api } = await mountWithMetamodel(MetatypeDetail, () => ({ metatype: metatypeById(IDS.component), canWrite: false, canConfigure: true }))
      parallelSwitch(wrapper)!.vm.$emit('update:modelValue', true)
      await flushPromises()
      expect(api.requests.find((request) => request.method === 'PUT')).toEqual(
        expect.objectContaining({ path: `/api/v1/metamodel/metatypes/${IDS.component}/parallel-changes`, body: { showInBaseline: true }, ifMatch: '"1"' }),
      )
      expect(metatypeById(IDS.component)?.showParallelChanges).toBe(true)
    })

    it('shows the value as text without metamodel administration', async () => {
      const { wrapper } = await mountWithMetamodel(MetatypeDetail, () => ({ metatype: metatypeById(IDS.component), canWrite: false, canConfigure: false }))
      expect(parallelSwitch(wrapper)).toBeUndefined()
      expect(wrapper.get('[data-testid="metatype-parallel-changes-value"]').text()).toBe('Not shown in the baseline')
    })

    it('warns of a collision and returns the switch to the stored value', async () => {
      const { wrapper, api } = await mountWithMetamodel(MetatypeDetail, () => ({ metatype: metatypeById(IDS.zone), canWrite: true }))
      // someone else changed the metatype meanwhile
      api.state.metatypes.find((metatype) => metatype.id === IDS.zone)!.version = 9
      parallelSwitch(wrapper)!.vm.$emit('update:modelValue', true)
      await flushPromises()
      expect(wrapper.text()).toContain('Someone else changed this element meanwhile')
      expect(parallelSwitch(wrapper)!.props('modelValue')).toBe(false)
    })
  })

  it('hides past entries until the history is shown', async () => {
    const { wrapper, api } = await mountWithMetamodel(MetatypeDetail, () => ({ metatype: metatypeById(IDS.zone), canWrite: true }))
    api.state.entries.push({ id: 'b0000000-0000-4000-8000-000000000009', metatypeId: IDS.zone, propertyDefId: IDS.owner, necessity: 'optional', validFrom: '2025-01-01', scope: 'organization' })
    await wrapper.setProps({ metatype: { ...metatypeById(IDS.zone)!, version: 99 } })
    await flushPromises()
    expect(wrapper.findAll('[data-testid="mapping-row"]')).toHaveLength(1)
    wrapper.findAllComponents({ name: 'Switch' })[0]!.vm.$emit('update:modelValue', true)
    await flushPromises()
    expect(wrapper.findAll('[data-testid="mapping-row"]')).toHaveLength(2)
  })
})
