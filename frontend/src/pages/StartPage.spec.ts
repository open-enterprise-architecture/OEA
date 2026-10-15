import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { routedFetch } from '../test/fetch'
import StartPage from './StartPage.vue'

function respond(health: unknown, setupRequired = false): typeof fetch {
  return routedFetch({
    '/api/v1/health': { body: health },
    '/api/v1/setup/status': { body: { setupRequired } },
  })
}

describe('StartPage', () => {
  it('shows the backend status from GET /api/v1/health', async () => {
    const wrapper = mount(StartPage, { props: { fetchFn: respond({ status: 'UP' }) }, global: { plugins: [ui] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="health"]').text()).toContain('UP')
  })

  it('shows a setup hint while setup is required', async () => {
    const wrapper = mount(StartPage, {
      props: { fetchFn: respond({ status: 'UP' }, true) },
      global: { plugins: [ui], stubs: { RouterLink: true } },
    })
    await flushPromises()
    expect(wrapper.get('[data-testid="setup-hint"]').text()).toContain('Setup required')
  })

  it('shows no setup hint on a set-up instance', async () => {
    const wrapper = mount(StartPage, { props: { fetchFn: respond({ status: 'UP' }) }, global: { plugins: [ui] } })
    await flushPromises()
    expect(wrapper.find('[data-testid="setup-hint"]').exists()).toBe(false)
  })

  it('shows DOWN when the backend is unreachable', async () => {
    const failing: typeof fetch = async () => {
      throw new TypeError('network error')
    }
    const wrapper = mount(StartPage, { props: { fetchFn: failing }, global: { plugins: [ui] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="health"]').text()).toContain('DOWN')
  })
})
