import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { ME_METAMODEL_ADMIN, ME_NO_ACCESS, ME_READER, ME_SECURITY_ADMIN, metamodelApi } from '../test/metamodel'
import { ME_PORTAL_READER } from '../test/portal'
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

  // spec metamodel/property-visibility "Page \"Property visibility\"" (us-124-property-visibility)
  it('links the property visibility for visibility:configure only', async () => {
    const admin = await header(ME_SECURITY_ADMIN)
    expect(admin.find('[data-testid="header-visibility"]').exists()).toBe(true)
    expect(admin.find('[data-testid="header-metamodel"]').exists()).toBe(false)
    session.me = undefined
    session.loaded = false
    expect((await header(ME_READER)).find('[data-testid="header-visibility"]').exists()).toBe(false)
  })

  // spec identity/authorization-roles "Page \"Authorization roles\"" and metamodel/visibility-override
  // "Page \"Override roles\"" (us-128-override-role-list D5)
  it('links the roles for role:assign and the override roles for visibility:configure only', async () => {
    const admin = await header(ME_SECURITY_ADMIN)
    expect(admin.find('[data-testid="header-roles"]').exists()).toBe(true)
    expect(admin.find('[data-testid="header-override"]').exists()).toBe(true)
    session.me = undefined
    session.loaded = false
    const kurt = await header(ME_METAMODEL_ADMIN)
    expect(kurt.find('[data-testid="header-roles"]').exists()).toBe(false)
    expect(kurt.find('[data-testid="header-override"]').exists()).toBe(false)
  })

  it('offers the metamodel to readers', async () => {
    expect((await header(ME_READER)).find('[data-testid="header-metamodel"]').exists()).toBe(true)
  })

  it('shows no metamodel entry without entity:read or metamodel:write', async () => {
    expect((await header(ME_NO_ACCESS)).find('[data-testid="header-metamodel"]').exists()).toBe(false)
  })

  // spec portal/portal-catalogs "Portal entry for architects", "Landing page of a portal reader"
  it('offers the portal next to the catalogs to readers with entity:read', async () => {
    const wrapper = await header(ME_READER)
    expect(wrapper.find('[data-testid="header-portal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="header-catalogs"]').exists()).toBe(true)
  })

  // spec solution/solution-overview "Page \"Solutions\"" (us-038-create-solution D4)
  it('links the solutions for entity:read', async () => {
    expect((await header(ME_READER)).find('[data-testid="header-solutions"]').exists()).toBe(true)
  })

  it('shows the portal as the only entry to a portal reader', async () => {
    const wrapper = await header(ME_PORTAL_READER)
    expect(wrapper.find('[data-testid="header-portal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="header-catalogs"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="header-solutions"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="header-metamodel"]').exists()).toBe(false)
  })
})
