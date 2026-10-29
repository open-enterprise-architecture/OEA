import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import type { Me } from '../api/session'
import ChangePasswordPage from './ChangePasswordPage.vue'

const ME: Me = {
  principal: { principalType: 'person', displayName: 'Anna' },
  mfaSatisfied: false,
  roles: [],
  permissions: [],
  csrfToken: 'csrf-1',
}

const problem = (status: number, type: string) => ({
  status,
  contentType: 'application/problem+json',
  body: { type: `urn:oea:problem:${type}`, title: type, status },
})

async function mountPage(fetchFn: typeof fetch) {
  const router = testRouter()
  await router.push('/me/password')
  const wrapper = mount(ChangePasswordPage, { props: { fetchFn }, global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router }
}

async function fill(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], current: string, next: string, repeated = next) {
  await wrapper.get('input[data-testid="currentPassword"], [data-testid="currentPassword"] input').setValue(current)
  await wrapper.get('input[data-testid="newPassword"], [data-testid="newPassword"] input').setValue(next)
  await wrapper.get('input[data-testid="repeated"], [data-testid="repeated"] input').setValue(repeated)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

// SCR-004 (REQ-173 AC2; design D8 of us-011-local-password)
describe('ChangePasswordPage', () => {
  beforeEach(() => {
    session.me = { ...ME, requiredAction: 'change-password' }
  })

  it('explains the required change and checks the input before sending', async () => {
    const recorded: RecordedRequest[] = []
    const { wrapper } = await mountPage(routedFetch({}, recorded))
    expect(wrapper.find('[data-testid="password-change-required"]').exists()).toBe(true)
    await fill(wrapper, 'initial-password-123', 'chosen-password-456', 'other-password-789')
    expect(wrapper.get('[data-testid="password-failure"]').text()).toContain('differ')
    await fill(wrapper, 'initial-password-123', 'short')
    expect(wrapper.get('[data-testid="password-failure"]').text()).toContain('12 to 128')
    expect(recorded).toHaveLength(0)
  })

  it('shows a wrong current password', async () => {
    const { wrapper } = await mountPage(routedFetch({ '/api/v1/me/password': problem(403, 'invalid-credentials') }))
    await fill(wrapper, 'wrong-password-000', 'chosen-password-456')
    expect(wrapper.get('[data-testid="password-failure"]').text()).toContain('current password is not correct')
  })

  it('changes the password and opens the profile', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = routedFetch(
      { '/api/v1/me/password': { status: 204, body: null }, '/api/v1/me': { body: { ...ME, requiredAction: null } } },
      recorded,
    )
    const { wrapper, router } = await mountPage(fetchFn)
    await fill(wrapper, 'initial-password-123', 'chosen-password-456')
    const sent = recorded.find((request) => request.path === '/api/v1/me/password')!
    expect(JSON.parse(sent.init!.body as string)).toEqual({
      currentPassword: 'initial-password-123',
      newPassword: 'chosen-password-456',
    })
    expect(router.currentRoute.value.path).toBe('/me')
    expect(session.me?.requiredAction).toBeNull()
  })
})
