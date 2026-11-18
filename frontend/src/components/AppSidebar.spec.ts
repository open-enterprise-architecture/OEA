import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { clearTree, sidebarTab } from '../navigation/store'
import { session } from '../session/store'
import type { Me } from '../api/session'
import { ME_TREE_MANAGER, ME_TREE_READER, navigationApi } from '../test/navigation'
import { testRouter } from '../test/router'
import AppSidebar from './AppSidebar.vue'

async function sidebar(me: unknown, tab: 'browser' | 'tree' = 'tree') {
  sidebarTab.value = tab
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

  it('opens on the tab "Browser" and shows "Current state" first', async () => {
    const wrapper = await sidebar(ME_TREE_READER, 'browser')
    expect(wrapper.find('[data-testid="solution-browser"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="navigation-tree"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="browser-label"]').text()).toBe('Current state')
    expect(wrapper.findAll('[role="tab"]').map((tab) => [tab.text(), tab.attributes('aria-selected')])).toEqual([
      ['Browser', 'true'],
      ['Navigation tree', 'false'],
    ])
    wrapper.unmount()
  })

  it('starts on the browser after the session was cleared', async () => {
    sidebarTab.value = 'tree'
    clearTree()
    expect(sidebarTab.value).toBe('browser')
  })

  it('shows the navigation tree from "Repository" in the second tab', async () => {
    const wrapper = await sidebar(ME_TREE_READER, 'browser')
    await wrapper.findAll('[role="tab"]')[1]!.trigger('mousedown', { button: 0 })
    await flushPromises()
    expect(sidebarTab.value).toBe('tree')
    expect(wrapper.find('[data-testid="solution-browser"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="navigation-tree"]').text()).toContain('Repository')
    wrapper.unmount()
  })

  it('keeps the chosen tab while the person moves between pages', async () => {
    const wrapper = await sidebar(ME_TREE_READER, 'browser')
    await wrapper.findAll('[role="tab"]')[1]!.trigger('mousedown', { button: 0 })
    await flushPromises()
    await wrapper.vm.$router.push('/catalogs')
    await flushPromises()
    expect(wrapper.find('[data-testid="navigation-tree"]').exists()).toBe(true)
    wrapper.unmount()
    // a sidebar mounted later, as on another page, shows the chosen tab
    const again = await sidebar(ME_TREE_READER, sidebarTab.value)
    expect(again.find('[data-testid="navigation-tree"]').exists()).toBe(true)
    again.unmount()
  })

  it('shows neither tab without entity:read', async () => {
    const wrapper = await sidebar({ ...ME_TREE_READER, permissions: [{ permission: 'portal:read', grantedBy: [] }] }, 'browser')
    expect(wrapper.find('[role="tab"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="solution-browser"]').exists()).toBe(false)
    wrapper.unmount()
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

  it('offers no folder actions with viewpoint:manage but without entity:read, as every tree write needs both (design D7 of us-095)', async () => {
    const permissions = [{ permission: 'viewpoint:manage', grantedBy: [] }, { permission: 'entity:read', grantedBy: [] }]
    const both = await sidebar({ ...ME_TREE_MANAGER, permissions })
    expect(both.find('[data-testid="tree-folder-actions"]').exists()).toBe(true)
    both.unmount()
    // without entity:read there is no sidebar at all, so nothing to drag or move
    const manageOnly = await sidebar({ ...ME_TREE_MANAGER, permissions: [permissions[0]] })
    expect(manageOnly.find('[data-testid="app-sidebar"]').exists()).toBe(false)
    manageOnly.unmount()
  })

  it('is not shown without a session', async () => {
    const wrapper = await sidebar(undefined)
    expect(wrapper.find('[data-testid="app-sidebar"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
