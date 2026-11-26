import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { ME_DELETED_RESTORER, ME_DELETED_WRITER } from '../test/deletedEntities'
import { testRouter } from '../test/router'
import AppHeader from './AppHeader.vue'

// spec repository/deleted-entities "Not offered": the header entry only for entity:restore
describe('AppHeader entry "Deleted entities"', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  afterEach(() => {
    session.me = undefined
    session.loaded = false
  })

  async function header(me: unknown) {
    const fetchFn = (async () => new Response(JSON.stringify(me), { headers: { 'Content-Type': 'application/json' } })) as typeof fetch
    const wrapper = mount(AppHeader, { props: { fetchFn }, global: { plugins: [ui, testRouter()] } })
    await flushPromises()
    return wrapper
  }

  it('is shown to a holder of entity:restore and links to the page', async () => {
    const wrapper = await header(ME_DELETED_RESTORER)
    const entry = wrapper.get('[data-testid="header-deleted"]')
    expect(entry.text()).toBe('Deleted entities')
    expect(entry.attributes('href')).toBe('/entities/deleted')
  })

  it('is not shown without entity:restore', async () => {
    const wrapper = await header(ME_DELETED_WRITER)
    expect(wrapper.find('[data-testid="header-deleted"]').exists()).toBe(false)
  })
})
