import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { clearTree } from '../navigation/store'
import { session } from '../session/store'
import type { Me } from '../api/session'
import { ME_TREE_MANAGER, ME_TREE_READER, navigationApi } from '../test/navigation'
import { testRouter } from '../test/router'
import AppSidebar from './AppSidebar.vue'

async function sidebar(me: unknown) {
  session.me = me as Me | undefined
  session.loaded = true
  const router = testRouter()
  await router.push('/')
  const wrapper = mount(AppSidebar, { props: { fetchFn: navigationApi() }, global: { plugins: [ui, router] } })
  await flushPromises()
  return wrapper
}

// spec navigation/navigation-tree "Navigation sidebar"
describe('AppSidebar', () => {
  beforeEach(() => {
    clearTree()
    sessionStorage.clear()
  })

  afterEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('shows the tree to a reader with entity:read', async () => {
    const wrapper = await sidebar(ME_TREE_READER)
    expect(wrapper.find('[data-testid="app-sidebar"] [data-testid="navigation-tree"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tree-folder-actions"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('offers the folder actions with viewpoint:manage', async () => {
    const wrapper = await sidebar(ME_TREE_MANAGER)
    expect(wrapper.find('[data-testid="app-sidebar"] [data-testid="tree-folder-actions"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('is not shown without a session', async () => {
    const wrapper = await sidebar(undefined)
    expect(wrapper.find('[data-testid="app-sidebar"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
