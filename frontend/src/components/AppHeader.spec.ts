import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { ME_NO_ACCESS, ME_READER, metamodelApi } from '../test/metamodel'
import { testRouter } from '../test/router'
import AppHeader from './AppHeader.vue'

async function header(me: unknown) {
  const router = testRouter()
  await router.push('/')
  const wrapper = mount(AppHeader, { props: { fetchFn: metamodelApi({ me }).fetch }, global: { plugins: [ui, router] } })
  await flushPromises()
  return wrapper
}

// spec metamodel/configuration-screen "Access to the metamodel configuration"
describe('AppHeader', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('offers the metamodel to readers', async () => {
    expect((await header(ME_READER)).find('[data-testid="header-metamodel"]').exists()).toBe(true)
  })

  it('shows no metamodel entry without entity:read or metamodel:write', async () => {
    expect((await header(ME_NO_ACCESS)).find('[data-testid="header-metamodel"]').exists()).toBe(false)
  })
})
