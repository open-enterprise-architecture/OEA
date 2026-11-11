import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { defineComponent, h } from 'vue'
import { session } from '../session/store'
import { ME_METAMODEL_ADMIN, visibilityApi, type VisibilityApi } from '../test/visibility'
import AuthorizationRolesPage from './AuthorizationRolesPage.vue'

const mounted: VueWrapper[] = []

async function open(options: Parameters<typeof visibilityApi>[0] = {}): Promise<{ wrapper: VueWrapper; api: VisibilityApi }> {
  const api = visibilityApi({ customRoles: true, overrideRoles: ['migration-support'], ...options })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: { template: '<div />' } },
      { path: '/admin/roles', component: AuthorizationRolesPage, props: { fetchFn: api.fetch } },
    ],
  })
  await router.push('/admin/roles')
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

function row(wrapper: VueWrapper, id: string) {
  return wrapper.findAll('[data-testid="role-row"]').find((candidate) => candidate.text().includes(id))!
}

function dialog(): HTMLElement {
  return document.body.querySelector('[data-testid="role-dialog"]') as HTMLElement
}

async function fill(testId: string, value: string) {
  const input = dialog().querySelector(`[data-testid="${testId}"] input, input[data-testid="${testId}"]`) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function check(testId: string) {
  ;(dialog().querySelector(`[data-testid="${testId}"]`) as HTMLElement).click()
  await flushPromises()
}

async function newRole(wrapper: VueWrapper, id: string, permissions: string[] = []) {
  await wrapper.get('[data-testid="role-new"]').trigger('click')
  await flushPromises()
  await fill('role-id', id)
  await fill('role-name', `Role ${id}`)
  for (const permission of permissions) {
    await check(`role-permission-${permission}`)
  }
  ;(dialog().querySelector('[data-testid="role-save"]') as HTMLElement).click()
  await flushPromises()
}

// spec identity/authorization-roles "Page \"Authorization roles\""
describe('AuthorizationRolesPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('lists the roles with their permissions and markers', async () => {
    const { wrapper } = await open()
    expect(wrapper.findAll('[data-testid="role-row"]')).toHaveLength(4)
    expect(row(wrapper, 'viewer').find('[data-testid="role-builtin"]').exists()).toBe(true)
    expect(row(wrapper, 'viewer').text()).toContain('portal:read')
    expect(row(wrapper, 'migration-support').find('[data-testid="role-builtin"]').exists()).toBe(false)
    expect(row(wrapper, 'migration-support').find('[data-testid="role-override"]').exists()).toBe(true)
    expect(row(wrapper, 'migration-support').text()).toContain('Data migration of 2026')
  })

  it('creates a role and reloads the list ("List and create")', async () => {
    const { wrapper, api } = await open()
    await newRole(wrapper, 'iso')
    expect(api.rolePosts).toEqual([
      { id: 'iso', name: 'Role iso', description: null, permissions: [], assignableToServiceAccounts: false },
    ])
    expect(dialog()).toBeNull()
    expect(row(wrapper, 'iso').text()).toContain('No permissions')
  })

  it.each([
    ['metamodel:write and visibility:configure', 'combined', ['metamodel:write', 'visibility:configure'], 'combines metamodel administration'],
    ['a taken ID', 'viewer', [], 'The ID viewer is already taken.'],
  ])('keeps the dialog open with the reason for %s ("Rejected definition")', async (_, id, permissions, message) => {
    const { wrapper } = await open()
    await newRole(wrapper, id, permissions)
    expect(dialog()).not.toBeNull()
    expect(dialog().querySelector('[data-testid="role-failure"]')!.textContent).toContain(message)
  })

  it('does not send an invalid ID', async () => {
    const { wrapper, api } = await open()
    await newRole(wrapper, 'X')
    expect(api.rolePosts).toEqual([])
    expect(dialog().textContent).toContain('Use 2 to 63 lowercase letters')
  })

  it('shows no roles without role:assign', async () => {
    const { wrapper } = await open({ me: ME_METAMODEL_ADMIN })
    expect(wrapper.find('[data-testid="no-roles-access"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="roles-table"]').exists()).toBe(false)
  })
})
