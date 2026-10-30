import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { currentCsrfToken } from '../api/session'
import { session } from '../session/store'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import LoginPage from './LoginPage.vue'

const ME = {
  principal: { principalType: 'system-admin-account', displayName: 'admin' },
  mfaSatisfied: false,
  roles: [],
  permissions: [],
  csrfToken: 'csrf-1',
}
const LOGIN_RESULT = { principal: ME.principal, mfaSatisfied: false, csrfToken: 'csrf-1' }

const problem = (status: number, type: string, extra: Record<string, unknown> = {}) => ({
  status,
  contentType: 'application/problem+json',
  body: { type: `urn:oea:problem:${type}`, title: type, status, ...extra },
})

function byPath(recorded: RecordedRequest[], path: string): RecordedRequest {
  return recorded.find((request) => request.path === path)!
}

async function mountPage(fetchFn: typeof fetch, path = '/login', navigate?: (url: string) => void) {
  const router = testRouter()
  await router.push(path)
  const wrapper = mount(LoginPage, { props: { fetchFn, navigate }, global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router }
}

async function submitPassword(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], password = 'secret-pass') {
  await wrapper.get('[data-testid="loginName"] input, input[data-testid="loginName"]').setValue('admin')
  await wrapper.get('[data-testid="password"] input, input[data-testid="password"]').setValue(password)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('LoginPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('logs in during the setup phase and opens the profile', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = routedFetch(
      { '/api/v1/auth/local/login': { status: 201, body: LOGIN_RESULT }, '/api/v1/me': { body: ME } },
      recorded,
    )
    const { wrapper, router } = await mountPage(fetchFn)
    await submitPassword(wrapper)
    const request = byPath(recorded, '/api/v1/auth/local/login')
    expect(JSON.parse(request.init!.body as string)).toEqual({ loginName: 'admin', password: 'secret-pass' })
    expect(request.init!.credentials).toBe('same-origin')
    expect(router.currentRoute.value.path).toBe('/me')
    expect(session.me?.principal.displayName).toBe('admin')
    expect(currentCsrfToken()).toBe('csrf-1')
  })

  it('opens the password change after a login with a temporary password (US-011, REQ-173 AC2)', async () => {
    const fetchFn = routedFetch({
      '/api/v1/auth/local/login': { status: 201, body: { ...LOGIN_RESULT, requiredAction: 'change-password' } },
      '/api/v1/me': { body: { ...ME, requiredAction: 'change-password' } },
    })
    const { wrapper, router } = await mountPage(fetchFn)
    await submitPassword(wrapper)
    expect(router.currentRoute.value.path).toBe('/me/password')
  })

  it('shows one generic message for wrong credentials', async () => {
    const { wrapper } = await mountPage(
      routedFetch({ '/api/v1/auth/local/login': problem(401, 'invalid-credentials') }),
    )
    await submitPassword(wrapper)
    expect(wrapper.get('[data-testid="login-failure"]').text()).toContain(
      'Login failed. Check the user name or e-mail address and the password.',
    )
    expect(
      (wrapper.get('[data-testid="password"] input, input[data-testid="password"]').element as HTMLInputElement).value,
    ).toBe('')
  })

  it('asks for the TOTP code after the setup phase and sends it with the login transaction', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = routedFetch(
      {
        '/api/v1/auth/local/login': problem(401, 'second-factor-required', { loginTransaction: 'tx-1' }),
        '/api/v1/auth/local/login/totp': { status: 201, body: { ...LOGIN_RESULT, mfaSatisfied: true } },
        '/api/v1/me': { body: { ...ME, mfaSatisfied: true } },
      },
      recorded,
    )
    const { wrapper, router } = await mountPage(fetchFn)
    await submitPassword(wrapper)
    expect(wrapper.find('[data-testid="login-failure"]').exists()).toBe(false)
    await wrapper.get('[data-testid="code"] input, input[data-testid="code"]').setValue('123456')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(JSON.parse(byPath(recorded, '/api/v1/auth/local/login/totp').init!.body as string)).toEqual({
      loginTransaction: 'tx-1',
      code: '123456',
    })
    expect(router.currentRoute.value.path).toBe('/me')
  })

  it('explains a wrong code and lets the user start again', async () => {
    const { wrapper } = await mountPage(
      routedFetch({
        '/api/v1/auth/local/login': problem(401, 'second-factor-required', { loginTransaction: 'tx-1' }),
        '/api/v1/auth/local/login/totp': problem(401, 'invalid-credentials'),
      }),
    )
    await submitPassword(wrapper)
    await wrapper.get('[data-testid="code"] input, input[data-testid="code"]').setValue('000000')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="login-failure"]').text()).toContain('The code is not valid')
    await wrapper.get('[data-testid="start-again"]').trigger('click')
    expect(wrapper.find('[data-testid="password"]').exists()).toBe(true)
  })

  it('shows the waiting time while logins are throttled', async () => {
    const fetchFn: typeof fetch = async () =>
      new Response(JSON.stringify({ type: 'urn:oea:problem:invalid-credentials', status: 401 }), {
        status: 401,
        headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '8' },
      })
    const { wrapper } = await mountPage(fetchFn)
    await submitPassword(wrapper)
    expect(wrapper.get('[data-testid="login-failure"]').text()).toContain('Try again in 8 seconds')
  })

  it('offers the identity providers and starts their login', async () => {
    const targets: string[] = []
    const { wrapper } = await mountPage(
      routedFetch({ '/api/v1/auth/oidc/providers': { body: [{ id: 'p-1', name: 'Corporate IdP' }] } }),
      '/login',
      (url) => targets.push(url),
    )
    const button = wrapper.get('[data-testid="provider-p-1"]')
    expect(button.text()).toBe('Log in with Corporate IdP')
    await button.trigger('click')
    expect(targets).toEqual(['/api/v1/auth/oidc/p-1/start?returnTo=%2Fme'])
  })

  it.each([
    ['login-failed', 'Login failed.'],
    ['provider-unavailable', 'not reachable'],
  ])('explains the callback error %s', async (error, message) => {
    const { wrapper } = await mountPage(routedFetch({}), `/login?error=${error}`)
    expect(wrapper.get('[data-testid="login-failure"]').text()).toContain(message)
  })

  // design D7 of us-021-person-totp: the TOTP enrollment is offered after a password login
  const PERSON = { principalType: 'person' as const, displayName: 'Anna' }
  const PERSON_ME = { ...ME, principal: PERSON }
  const PERSON_LOGIN = { principal: PERSON, mfaSatisfied: false, csrfToken: 'csrf-1' }

  it('offers the TOTP enrollment to a person without a second factor', async () => {
    const fetchFn = routedFetch({
      '/api/v1/auth/local/login': { status: 201, body: PERSON_LOGIN },
      '/api/v1/me': { body: PERSON_ME },
      '/api/v1/me/second-factors': { body: [] },
    })
    const { wrapper, router } = await mountPage(fetchFn)
    await submitPassword(wrapper)
    expect(router.currentRoute.value.fullPath).toBe('/me/second-factor?offer=1')
  })

  it('offers nothing to a person who has a second factor', async () => {
    const fetchFn = routedFetch({
      '/api/v1/auth/local/login': { status: 201, body: PERSON_LOGIN },
      '/api/v1/me': { body: PERSON_ME },
      '/api/v1/me/second-factors': {
        body: [{ credentialId: 'c1', type: 'totp', label: 'Phone', createdAt: '2026-10-30T08:00:00Z' }],
      },
    })
    const { wrapper, router } = await mountPage(fetchFn)
    await submitPassword(wrapper)
    expect(router.currentRoute.value.path).toBe('/me')
  })

  it('opens the enrollment when the organization requires a second factor (US-031)', async () => {
    const fetchFn = routedFetch({
      '/api/v1/auth/local/login': {
        status: 201,
        body: { ...PERSON_LOGIN, requiredAction: 'enroll-second-factor' },
      },
      '/api/v1/me': { body: { ...PERSON_ME, requiredAction: 'enroll-second-factor' } },
    })
    const { wrapper, router } = await mountPage(fetchFn)
    await submitPassword(wrapper)
    expect(router.currentRoute.value.path).toBe('/me/second-factor')
  })
})
