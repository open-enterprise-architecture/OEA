import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { defineComponent, h } from 'vue'
import { session } from '../session/store'
import { IDS, ME_METAMODEL_ADMIN, ME_NO_ACCESS, ME_READER, metamodelApi, type MetamodelApiOptions } from '../test/metamodel'
import MetamodelPage from './MetamodelPage.vue'

async function open(path: string, options: MetamodelApiOptions = {}) {
  const api = metamodelApi(options)
  const placeholder = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: placeholder },
      { path: '/login', component: placeholder },
      { path: '/metamodel/:section?/:id?', component: MetamodelPage, props: (route) => ({ ...route.params, fetchFn: api.fetch }) },
    ],
  })
  await router.push(path)
  const root = defineComponent({ render: () => h(RouterView) })
  const wrapper = mount(root, { global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router, api }
}

// spec metamodel/configuration-screen of scr-030-metamodel-configuration
describe('MetamodelPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('lists all metatypes with abbreviation and scope', async () => {
    const { wrapper } = await open('/metamodel')
    const items = wrapper.findAll('[data-testid="metatype-item"]').map((item) => item.text())
    expect(items).toHaveLength(5)
    expect(items[0]).toContain('AC')
    expect(items[0]).toContain('Package oea-starter-togaf-classic 1.1.0')
    expect(items[2]).toContain('Own')
    expect(wrapper.find('[data-testid="metatype-new"]').exists()).toBe(true)
  })

  it('marks connection metatypes and names their allowed endpoints', async () => {
    const { wrapper } = await open(`/metamodel/metatypes/${IDS.dataFlow}`)
    expect(wrapper.findAll('[data-testid="metatype-connection-badge"]')).toHaveLength(1)
    expect(wrapper.get('[data-testid="metatype-endpoints-source"]').text()).toBe('Application Component')
    const { wrapper: zone } = await open(`/metamodel/metatypes/${IDS.zone}`)
    expect(zone.find('[data-testid="metatype-endpoints"]').exists()).toBe(false)
  })

  // "Reader sees the metamodel"
  it('shows the metamodel read-only to a reader', async () => {
    const { wrapper } = await open(`/metamodel/metatypes/${IDS.zone}`, { me: ME_READER })
    expect(wrapper.find('[data-testid="metatype-title"]').text()).toContain('Network Zone')
    expect(wrapper.find('[data-testid="metatype-new"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="metatype-edit"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="mapping-new"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Settings')
  })

  // "Person without access"
  it('shows that access is missing', async () => {
    const { wrapper } = await open('/metamodel', { me: ME_NO_ACCESS })
    expect(wrapper.find('[data-testid="no-metamodel-access"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="metatype-list"]').exists()).toBe(false)
  })

  // "Built-in metatype is read-only" (REQ-032 AC5), here an imported one
  it('offers no changes of an imported metatype', async () => {
    const { wrapper } = await open(`/metamodel/metatypes/${IDS.component}`)
    expect(wrapper.find('[data-testid="metatype-scope"]').text()).toBe('Package oea-starter-togaf-classic 1.1.0')
    expect(wrapper.find('[data-testid="metatype-read-only"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="metatype-edit"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="metatype-delete"]').exists()).toBe(false)
    // own properties can still be added
    expect(wrapper.find('[data-testid="mapping-new"]').exists()).toBe(true)
  })

  // "Inherited and future entries"
  it('names the base type of inherited mappings and shows future entries', async () => {
    const { wrapper } = await open(`/metamodel/metatypes/${IDS.cloudZone}`)
    expect(wrapper.find('[data-testid="metatype-base-type"]').text()).toContain('Network Zone')
    expect(wrapper.get('[data-testid="mapping-origin"]').text()).toBe('Inherited from Network Zone')
    const { wrapper: component } = await open(`/metamodel/metatypes/${IDS.component}`)
    const states = component.findAll('[data-testid="mapping-state"]').map((badge) => badge.text())
    expect(states).toEqual(['active', 'future'])
  })

  it('switches sections by route', async () => {
    const { wrapper, router } = await open('/metamodel/properties')
    expect(wrapper.findAll('[data-testid="property-row"]')).toHaveLength(2)
    await router.push('/metamodel/categories')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="category-row"]').map((row) => row.text())).toEqual([expect.stringContaining('General')])
  })

  it('shows the list or the detail at a time below the large breakpoint', async () => {
    const { wrapper } = await open(`/metamodel/metatypes/${IDS.zone}`)
    expect(wrapper.get('[data-testid="metatype-list-panel"]').classes()).toContain('hidden')
    expect(wrapper.find('[data-testid="metatype-back"]').exists()).toBe(true)
  })

  // "Translating a metatype name"
  it('shows the German name after a translation is saved', async () => {
    const { wrapper, api, router } = await open('/metamodel/translations', { me: { ...ME_METAMODEL_ADMIN, uiLanguage: 'de' } })
    await wrapper.get('[data-testid="translation-input-Network Zone"] input, input[data-testid="translation-input-Network Zone"]').setValue('Netzwerkzone')
    await wrapper.get('[data-testid="translation-save"]').trigger('click')
    await flushPromises()
    expect(api.requests.find((request) => request.method === 'PUT')?.body).toEqual({ labels: { [`metatype.${IDS.zone}.name`]: 'Netzwerkzone' } })
    await router.push('/metamodel/metatypes')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="metatype-item"]').map((item) => item.text())).toEqual(
      expect.arrayContaining([expect.stringContaining('Netzwerkzone')]),
    )
  })

  // spec metamodel/configuration-screen of us-037-solution-extensions-and-lock-mode
  describe('lock mode', () => {
    // "Locked metamodel"
    it('shows a notice and no changes while the metamodel is locked', async () => {
      const { wrapper, router } = await open(`/metamodel/metatypes/${IDS.zone}`, { locked: true })
      expect(wrapper.find('[data-testid="metamodel-locked"]').text()).toContain('Metamodel locked')
      expect(wrapper.find('[data-testid="metamodel-locked"] a').attributes('href')).toBe(
        'https://open-enterprise-architecture.github.io/OEA/administration/metamodel-packages',
      )
      expect(wrapper.findAll('[data-testid="metatype-item"]')).toHaveLength(5)
      expect(wrapper.find('[data-testid="metatype-new"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="metatype-edit"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="mapping-new"]').exists()).toBe(false)
      // the re-import setting stays
      expect(
        wrapper.findAllComponents({ name: 'Select' }).some((select) => select.vm.$attrs['data-testid'] === 'metatype-reimport'),
      ).toBe(true)
      await router.push('/metamodel/properties')
      await flushPromises()
      expect(wrapper.find('[data-testid="metamodel-locked"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="property-new"]').exists()).toBe(false)
      await router.push('/metamodel/settings')
      await flushPromises()
      expect(wrapper.find('[data-testid="metamodel-lock"]').exists()).toBe(true)
    })

    // "Unlocking"
    it('offers the changes again after unlocking', async () => {
      const { wrapper, router, api } = await open('/metamodel/settings', { locked: true })
      lockSwitch(wrapper).vm.$emit('update:modelValue', false)
      await flushPromises()
      const put = api.requests.find((request) => request.method === 'PUT')
      expect(put?.path).toBe('/api/v1/metamodel/configurations/00000000-0000-0000-0000-000000000001')
      expect(put?.ifMatch).toBe('"1"')
      expect(put?.body).toEqual({ name: 'Instance metamodel', editMode: 'gui-and-import' })
      expect(wrapper.get('[data-testid="lock-notice"]').text()).toContain('unlocked')
      expect(wrapper.find('[data-testid="metamodel-locked"]').exists()).toBe(false)
      await router.push('/metamodel/metatypes')
      await flushPromises()
      expect(wrapper.find('[data-testid="metatype-new"]').exists()).toBe(true)
    })

    it('locks the metamodel under settings', async () => {
      const { wrapper, api } = await open('/metamodel/settings')
      lockSwitch(wrapper).vm.$emit('update:modelValue', true)
      await flushPromises()
      expect(api.state.configuration.editMode).toBe('import-only')
      expect(wrapper.find('[data-testid="metamodel-locked"]').exists()).toBe(true)
    })

    it('shows the current state when someone else switched meanwhile', async () => {
      const { wrapper, api } = await open('/metamodel/settings')
      api.switchElsewhere('import-only')
      lockSwitch(wrapper).vm.$emit('update:modelValue', true)
      await flushPromises()
      expect(wrapper.get('[data-testid="lock-failure"]').text()).toContain('Someone else changed this element meanwhile')
      expect(api.state.configuration.version).toBe(2)
      expect(wrapper.find('[data-testid="metamodel-locked"]').exists()).toBe(true)
    })
  })
})

function lockSwitch(wrapper: ReturnType<typeof mount>) {
  const found = wrapper.findAllComponents({ name: 'Switch' }).find((candidate) => candidate.vm.$attrs['data-testid'] === 'metamodel-lock')
  if (!found) {
    throw new Error('no lock switch')
  }
  return found
}
