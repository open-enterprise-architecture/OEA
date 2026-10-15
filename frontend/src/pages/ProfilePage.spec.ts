import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { currentCsrfToken } from '../api/session'
import { session } from '../session/store'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import ProfilePage from './ProfilePage.vue'

const ME = {
  principal: { principalType: 'system-admin-account', displayName: 'admin' },
  mfaSatisfied: false,
  roles: [{ role: { id: 'system-admin', name: 'System Admin', privileged: true }, source: 'system-admin-account', effective: true }],
  permissions: [{ permission: 'role:assign', grantedBy: [] }, { permission: 'person:manage', grantedBy: [] }],
  csrfToken: 'csrf-9',
}

async function mountPage(fetchFn: typeof fetch) {
  const router = testRouter()
  await router.push('/me')
  const wrapper = mount(ProfilePage, { props: { fetchFn }, global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('ProfilePage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('shows the principal, its second-factor state and its permissions', async () => {
    const { wrapper } = await mountPage(routedFetch({ '/api/v1/me': { body: ME } }))
    expect(wrapper.get('[data-testid="display-name"]').text()).toBe('admin')
    expect(wrapper.get('[data-testid="mfa"]').text()).toContain('Not confirmed')
    expect(wrapper.get('[data-testid="roles"]').text()).toBe('System Admin')
    expect(wrapper.get('[data-testid="permissions"]').findAll('li')).toHaveLength(2)
    expect(wrapper.find('[data-testid="set-up-second-factor"]').exists()).toBe(true)
  })

  it('logs out with the CSRF token and forgets the session', async () => {
    const recorded: RecordedRequest[] = []
    const { wrapper, router } = await mountPage(
      routedFetch({ '/api/v1/me': { body: ME }, '/api/v1/auth/logout': { body: {} } }, recorded),
    )
    await wrapper.get('[data-testid="logout"]').trigger('click')
    await flushPromises()
    const logout = recorded.find((request) => request.path === '/api/v1/auth/logout')!
    expect((logout.init!.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-9')
    expect(session.me).toBeUndefined()
    expect(currentCsrfToken()).toBeUndefined()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('offers the login without a session', async () => {
    const { wrapper } = await mountPage(
      routedFetch({
        '/api/v1/me': {
          status: 401,
          contentType: 'application/problem+json',
          body: { type: 'urn:oea:problem:unauthenticated', status: 401 },
        },
      }),
    )
    expect(wrapper.get('[data-testid="not-logged-in"]').text()).toContain('not logged in')
  })

  it('shows roles from the identity provider and a role that needs a second factor', async () => {
    const { wrapper } = await mountPage(
      routedFetch({
        '/api/v1/me': {
          body: {
            ...ME,
            principal: { principalType: 'person', displayName: 'Dev Admin' },
            roles: [
              { role: { id: 'viewer', name: 'Viewer', privileged: false }, source: 'idp', effective: true },
              {
                role: { id: 'system-admin', name: 'System Admin', privileged: true },
                source: 'idp',
                effective: false,
                notEffectiveReason: 'mfa-required',
              },
            ],
          },
        },
      }),
    )
    expect(wrapper.get('[data-testid="roles"]').text()).toBe(
      'Viewer (identity provider), System Admin (identity provider) – needs a second factor',
    )
    expect(wrapper.find('[data-testid="no-role"]').exists()).toBe(false)
  })

  it('tells a person without an effective role that no role is assigned (REQ-003)', async () => {
    const { wrapper } = await mountPage(
      routedFetch({ '/api/v1/me': { body: { ...ME, roles: [], permissions: [] } } }),
    )
    expect(wrapper.get('[data-testid="no-role"]').text()).toContain('No role assigned')
  })
})
