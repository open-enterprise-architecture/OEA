import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../session/store'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import LoginPage from './LoginPage.vue'

// SCR-001 with passkeys (US-009; design D6 of us-022-passkeys); the browser API is stubbed
const browser = vi.hoisted(() => ({
  browserSupportsWebAuthn: vi.fn(() => true),
  startAuthentication: vi.fn(),
  startRegistration: vi.fn(),
}))
vi.mock('@simplewebauthn/browser', () => browser)

const PERSON = { principalType: 'person' as const, displayName: 'Anna' }
const ME = { principal: PERSON, mfaSatisfied: true, roles: [], permissions: [], csrfToken: 'csrf-1' }
const CHALLENGE = { challengeId: 'c-1', options: { challenge: 'abc', rpId: 'oea.example.org' } }
const ASSERTION = { id: 'cred-1', type: 'public-key', response: {} }

async function mountPage(fetchFn: typeof fetch) {
  const router = testRouter()
  await router.push('/login')
  const wrapper = mount(LoginPage, { props: { fetchFn }, global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('LoginPage with passkeys', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    browser.browserSupportsWebAuthn.mockReturnValue(true)
    browser.startAuthentication.mockReset()
  })

  it('logs in without password with a passkey', async () => {
    const recorded: RecordedRequest[] = []
    browser.startAuthentication.mockResolvedValue(ASSERTION)
    const fetchFn = routedFetch(
      {
        '/api/v1/auth/passkey/options': { body: CHALLENGE },
        '/api/v1/auth/passkey/login': { status: 201, body: { principal: PERSON, mfaSatisfied: true, csrfToken: 'csrf-1' } },
        '/api/v1/me': { body: ME },
      },
      recorded,
    )
    const { wrapper, router } = await mountPage(fetchFn)
    await wrapper.get('[data-testid="passkey-login"]').trigger('click')
    await flushPromises()
    expect(browser.startAuthentication).toHaveBeenCalledWith({ optionsJSON: CHALLENGE.options })
    const login = recorded.find((request) => request.path === '/api/v1/auth/passkey/login')!
    expect(JSON.parse(login.init!.body as string)).toEqual({ challengeId: 'c-1', credential: ASSERTION })
    expect(router.currentRoute.value.path).toBe('/me')
  })

  it('stays quiet when the person cancels the browser dialog', async () => {
    browser.startAuthentication.mockRejectedValue(Object.assign(new Error('cancelled'), { name: 'NotAllowedError' }))
    const { wrapper, router } = await mountPage(routedFetch({ '/api/v1/auth/passkey/options': { body: CHALLENGE } }))
    await wrapper.get('[data-testid="passkey-login"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="login-failure"]').exists()).toBe(false)
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('confirms a password login with the passkey when it is the only second factor', async () => {
    const recorded: RecordedRequest[] = []
    browser.startAuthentication.mockResolvedValue(ASSERTION)
    const fetchFn = routedFetch(
      {
        '/api/v1/auth/local/login': {
          status: 401,
          contentType: 'application/problem+json',
          body: {
            type: 'urn:oea:problem:second-factor-required',
            status: 401,
            loginTransaction: 'tx-1',
            secondFactors: ['passkey'],
          },
        },
        '/api/v1/auth/local/login/passkey/options': { body: CHALLENGE },
        '/api/v1/auth/local/login/passkey': { status: 201, body: { principal: PERSON, mfaSatisfied: true, csrfToken: 'csrf-1' } },
        '/api/v1/me': { body: ME },
      },
      recorded,
    )
    const { wrapper, router } = await mountPage(fetchFn)
    await wrapper.get('[data-testid="loginName"] input, input[data-testid="loginName"]').setValue('anna@example.org')
    await wrapper.get('[data-testid="password"] input, input[data-testid="password"]').setValue('chosen-password-456')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="code"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="second-step-hint"]').text()).toBe('Confirm the login with your passkey.')
    await wrapper.get('[data-testid="use-passkey"]').trigger('click')
    await flushPromises()
    const step = recorded.find((request) => request.path === '/api/v1/auth/local/login/passkey')!
    expect(JSON.parse(step.init!.body as string)).toEqual({ loginTransaction: 'tx-1', challengeId: 'c-1', credential: ASSERTION })
    expect(router.currentRoute.value.path).toBe('/me')
  })

  it('offers no passkey in a browser without WebAuthn', async () => {
    browser.browserSupportsWebAuthn.mockReturnValue(false)
    const { wrapper } = await mountPage(routedFetch({}))
    expect(wrapper.find('[data-testid="passkey-login"]').exists()).toBe(false)
  })
})
