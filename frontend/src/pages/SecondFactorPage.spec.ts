import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { login } from '../api/session'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import SecondFactorPage from './SecondFactorPage.vue'

const ENROLLMENT = {
  credentialId: '7f0c1a2b-3c4d-4e5f-8a9b-0c1d2e3f4a5b',
  secret: 'JBSWY3DPEHPK3PXP',
  otpauthUri: 'otpauth://totp/OEA%3Aadmin?secret=JBSWY3DPEHPK3PXP&issuer=OEA',
  expiresAt: '2026-10-15T12:10:00Z',
}
const CONFIRM_PATH = `/api/v1/me/second-factors/totp/${ENROLLMENT.credentialId}/confirm`

async function mountPage(fetchFn: typeof fetch) {
  // a login sets the CSRF token that state-changing requests carry
  await login('admin', 'secret-pass', routedFetch({
    '/api/v1/auth/local/login': { status: 201, body: { principal: {}, mfaSatisfied: false, csrfToken: 'csrf-7' } },
  }))
  return mount(SecondFactorPage, { props: { fetchFn }, global: { plugins: [ui, testRouter()] } })
}

describe('SecondFactorPage', () => {
  it('shows the QR code rendered locally and the key, and confirms the enrollment', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = routedFetch(
      {
        '/api/v1/me/second-factors/totp': { status: 201, body: ENROLLMENT },
        [CONFIRM_PATH]: { body: { credentialId: ENROLLMENT.credentialId, type: 'totp', createdAt: ENROLLMENT.expiresAt } },
        '/api/v1/me': { body: { principal: {}, mfaSatisfied: true, roles: [], permissions: [], csrfToken: 'csrf-7' } },
      },
      recorded,
    )
    const wrapper = await mountPage(fetchFn)
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="secret"]').text()).toBe(ENROLLMENT.secret)
    expect(wrapper.get('svg[role="img"]').findAll('rect').length).toBeGreaterThan(100)
    expect((recorded[0]!.init!.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-7')
    // only the backend was called; nothing is sent to a QR service
    expect(recorded.every((request) => request.path.startsWith('/api/v1/'))).toBe(true)

    await wrapper.get('[data-testid="code"] input, input[data-testid="code"]').setValue('123456')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(recorded[1]!.path).toBe(CONFIRM_PATH)
    expect(JSON.parse(recorded[1]!.init!.body as string)).toEqual({ code: '123456' })
    expect(wrapper.get('[data-testid="enrollment-confirmed"]').text()).toContain('Second factor active')
    expect(wrapper.find('[data-testid="secret"]').exists()).toBe(false)
  })

  it('asks for a new login when the session is older than 5 minutes', async () => {
    const wrapper = await mountPage(
      routedFetch({
        '/api/v1/me/second-factors/totp': {
          status: 401,
          contentType: 'application/problem+json',
          body: { type: 'urn:oea:problem:reauthentication-required', status: 401 },
        },
      }),
    )
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="reauthentication-required"]').text()).toContain('Log in again')
  })

  it('explains a wrong code', async () => {
    const wrapper = await mountPage(
      routedFetch({
        '/api/v1/me/second-factors/totp': { status: 201, body: ENROLLMENT },
        [CONFIRM_PATH]: {
          status: 400,
          contentType: 'application/problem+json',
          body: { type: 'urn:oea:problem:invalid-totp-code', status: 400 },
        },
      }),
    )
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="code"] input, input[data-testid="code"]').setValue('000000')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="enrollment-failure"]').text()).toContain('The code is not valid')
    expect(wrapper.find('[data-testid="secret"]').exists()).toBe(true)
  })

  it('sends the label of the authenticator', async () => {
    const recorded: RecordedRequest[] = []
    const wrapper = await mountPage(routedFetch({ '/api/v1/me/second-factors/totp': { status: 201, body: ENROLLMENT } }, recorded))
    await wrapper.get('[data-testid="label"] input, input[data-testid="label"]').setValue('Backup phone')
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()
    expect(JSON.parse(recorded[0]!.init!.body as string)).toEqual({ label: 'Backup phone' })
  })

  it('lets a person skip the offer after the login', async () => {
    const router = testRouter()
    await router.push('/me/second-factor?offer=1')
    const wrapper = mount(SecondFactorPage, { props: { fetchFn: routedFetch({}) }, global: { plugins: [ui, router] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="offer"]').text()).toContain('second factor')
    await wrapper.get('[data-testid="skip"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/')
  })
})
