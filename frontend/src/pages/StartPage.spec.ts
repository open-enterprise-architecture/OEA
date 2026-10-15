import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StartPage from './StartPage.vue'

function respond(body: unknown, status = 200): typeof fetch {
  return async () => new Response(JSON.stringify(body), { status })
}

describe('StartPage', () => {
  it('shows the backend status from GET /api/v1/health', async () => {
    const wrapper = mount(StartPage, { props: { fetchFn: respond({ status: 'UP' }) }, global: { plugins: [ui] } })
    await flushPromises()
    expect(wrapper.get('[data-testid="health"]').text()).toContain('UP')
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
