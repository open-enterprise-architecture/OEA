import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../session/store'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import SecondFactorPage from './SecondFactorPage.vue'

// SCR-003 with passkeys (US-022, US-025 AC2; design D6 of us-022-passkeys); the browser API is stubbed
const browser = vi.hoisted(() => ({
  browserSupportsWebAuthn: vi.fn(() => true),
  startAuthentication: vi.fn(),
  startRegistration: vi.fn(),
}))
vi.mock('@simplewebauthn/browser', () => browser)

const ME = {
  principal: { principalType: 'person' as const, displayName: 'Anna' },
  mfaSatisfied: false,
  roles: [],
  permissions: [],
}
const REGISTRATION = { registrationId: 'r-1', options: { challenge: 'abc' }, expiresAt: '2026-10-30T10:10:00Z' }
const ATTESTATION = { id: 'cred-1', type: 'public-key', response: {} }

async function mountPage(fetchFn: typeof fetch) {
  const wrapper = mount(SecondFactorPage, { props: { fetchFn }, global: { plugins: [ui, testRouter()] } })
  await flushPromises()
  return wrapper
}

describe('SecondFactorPage with passkeys', () => {
  beforeEach(() => {
    session.me = { ...ME }
    browser.startRegistration.mockReset()
  })

  it('registers a passkey with its label', async () => {
    const recorded: RecordedRequest[] = []
    browser.startRegistration.mockResolvedValue(ATTESTATION)
    const fetchFn = routedFetch(
      {
        '/api/v1/me/second-factors/passkey': { status: 201, body: REGISTRATION },
        '/api/v1/me/second-factors/passkey/r-1/confirm': {
          body: { credentialId: 'p-1', type: 'passkey', label: 'Laptop', createdAt: '2026-10-30T10:00:00Z' },
        },
        '/api/v1/me': { body: { ...ME, mfaSatisfied: true } },
      },
      recorded,
    )
    const wrapper = await mountPage(fetchFn)
    expect(wrapper.find('[data-testid="method"]').exists()).toBe(true)
    await wrapper.get('[data-testid="label"] input, input[data-testid="label"]').setValue('Laptop')
    await wrapper.get('[data-testid="start-passkey"]').trigger('click')
    await flushPromises()
    expect(JSON.parse(recorded[0]!.init!.body as string)).toEqual({ label: 'Laptop' })
    expect(browser.startRegistration).toHaveBeenCalledWith({ optionsJSON: REGISTRATION.options })
    expect(JSON.parse(recorded[1]!.init!.body as string)).toEqual(ATTESTATION)
    expect(wrapper.get('[data-testid="enrollment-confirmed"]').text()).toContain('passkey')
  })

  it('explains a cancellation in the browser', async () => {
    browser.startRegistration.mockRejectedValue(Object.assign(new Error('cancelled'), { name: 'NotAllowedError' }))
    const wrapper = await mountPage(routedFetch({ '/api/v1/me/second-factors/passkey': { status: 201, body: REGISTRATION } }))
    await wrapper.get('[data-testid="start-passkey"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="enrollment-failure"]').text()).toContain('cancelled')
  })
})
