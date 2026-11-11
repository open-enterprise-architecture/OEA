import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { defineComponent, h } from 'vue'
import { session } from '../session/store'
import { ME_METAMODEL_ADMIN, visibilityApi, type VisibilityApi } from '../test/visibility'
import OverrideRolesPage from './OverrideRolesPage.vue'

const mounted: VueWrapper[] = []

async function open(options: Parameters<typeof visibilityApi>[0] = {}): Promise<{ wrapper: VueWrapper; api: VisibilityApi }> {
  const api = visibilityApi({ customRoles: true, ...options })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: { template: '<div />' } },
      { path: '/admin/override-roles', component: OverrideRolesPage, props: { fetchFn: api.fetch } },
    ],
  })
  await router.push('/admin/override-roles')
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

async function choose(wrapper: VueWrapper, role: string) {
  await wrapper.get(`[data-testid="override-role-${role}"] button, button[data-testid="override-role-${role}"]`).trigger('click')
  await flushPromises()
}

async function save(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="override-form"]').trigger('submit')
  await flushPromises()
}

function checked(wrapper: VueWrapper, role: string): boolean {
  return wrapper.get(`[data-testid="override-role-${role}"] button, button[data-testid="override-role-${role}"]`).attributes('aria-checked') === 'true'
}

// spec metamodel/visibility-override "Page \"Override roles\""
describe('OverrideRolesPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('changes the list on the page', async () => {
    const { wrapper, api } = await open()
    expect(wrapper.find('[data-testid="override-audit-warning"]').exists()).toBe(true)
    await choose(wrapper, 'migration-support')
    await save(wrapper)
    expect(api.overridePuts).toEqual([['migration-support']])
    expect(checked(wrapper, 'migration-support')).toBe(true)
    expect(wrapper.find('[data-testid="override-saved"]').exists()).toBe(true)
  })

  it('keeps the previous list when the change is rejected', async () => {
    const { wrapper, api } = await open({ overrideRoles: ['auditor'], rejectOverride: true })
    await choose(wrapper, 'migration-support')
    await save(wrapper)
    expect(api.overridePuts).toEqual([['auditor', 'migration-support']])
    expect(checked(wrapper, 'migration-support')).toBe(false)
    expect(checked(wrapper, 'auditor')).toBe(true)
    expect(wrapper.get('[data-testid="override-failure"]').text()).toContain('could not be saved')
  })

  it('shows the empty-list hint only while the list is empty', async () => {
    const empty = await open()
    expect(empty.wrapper.find('[data-testid="override-empty"]').exists()).toBe(true)
    const filled = await open({ overrideRoles: ['auditor'] })
    expect(filled.wrapper.find('[data-testid="override-empty"]').exists()).toBe(false)
  })

  it('shows no list without visibility:configure', async () => {
    const { wrapper } = await open({ me: ME_METAMODEL_ADMIN })
    expect(wrapper.find('[data-testid="no-override-access"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="override-form"]').exists()).toBe(false)
  })
})
