import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import SetupPage from './SetupPage.vue'

const problem = (status: number, type: string, invalidFields?: string[]) => ({
  status,
  contentType: 'application/problem+json',
  body: { type: `urn:oea:problem:${type}`, title: type, status, ...(invalidFields ? { invalidFields } : {}) },
})

async function fillAndSubmit(fetchFn: typeof fetch, overrides: Record<string, string> = {}) {
  const wrapper = mount(SetupPage, { props: { fetchFn }, global: { plugins: [ui, testRouter()] } })
  const values: Record<string, string> = {
    setupToken: 'tok-1',
    loginName: 'admin',
    password: 'correct horse battery',
    passwordConfirmation: 'correct horse battery',
    instanceSlug: 'acme-corp',
    ...overrides,
  }
  for (const [field, value] of Object.entries(values)) {
    await wrapper.get(`[data-testid="${field}"] input, input[data-testid="${field}"]`).setValue(value)
  }
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  return wrapper
}

describe('SetupPage', () => {
  it('creates the system admin account and clears the secrets', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = routedFetch(
      { '/api/v1/setup': { status: 201, body: { loginName: 'admin', instanceSlug: 'acme-corp' } } },
      recorded,
    )
    const wrapper = await fillAndSubmit(fetchFn)
    expect(wrapper.get('[data-testid="setup-success"]').text()).toContain('acme-corp')
    expect(JSON.parse(recorded[0]!.init!.body as string)).toEqual({
      setupToken: 'tok-1',
      loginName: 'admin',
      password: 'correct horse battery',
      instanceSlug: 'acme-corp',
    })
  })

  it('does not call the API while client-side checks fail', async () => {
    const recorded: RecordedRequest[] = []
    const wrapper = await fillAndSubmit(routedFetch({}, recorded), { password: 'short', passwordConfirmation: 'short' })
    expect(recorded).toHaveLength(0)
    expect(wrapper.text()).toContain('12–128 characters.')
  })

  it.each([
    ['invalid-setup-token', 401, 'wrong or expired'],
    ['already-bootstrapped', 409, 'already set up'],
  ])('explains the problem %s', async (type, status, message) => {
    const wrapper = await fillAndSubmit(routedFetch({ '/api/v1/setup': problem(status, type) }))
    expect(wrapper.get('[data-testid="setup-failure"]').text()).toContain(message)
  })

  // spec identity/bootstrapping "Setup page shows the waiting time"
  it('shows the waiting time of a throttled address', async () => {
    const wrapper = await fillAndSubmit(
      routedFetch({ '/api/v1/setup': { ...problem(429, 'too-many-requests'), headers: { 'Retry-After': '30' } } }),
    )
    expect(wrapper.get('[data-testid="setup-failure"]').text()).toBe('Too many failed attempts. Try again in 30 seconds.')
  })

  it('marks the fields named in a validation problem', async () => {
    const wrapper = await fillAndSubmit(
      routedFetch({ '/api/v1/setup': problem(400, 'validation-failed', ['instanceSlug']) }),
    )
    expect(wrapper.get('[data-testid="setup-failure"]').text()).toContain('do not meet the requirements')
    expect(wrapper.text()).toContain('not at the start or end')
  })
})
