import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { defineComponent, h } from 'vue'
import { session } from '../session/store'
import { ME_METAMODEL_ADMIN, VISIBILITY_IDS, visibilityApi, type VisibilityApi } from '../test/visibility'
import PropertyVisibilityPage from './PropertyVisibilityPage.vue'

const mounted: VueWrapper[] = []

async function open(options: Parameters<typeof visibilityApi>[0] = {}): Promise<{ wrapper: VueWrapper; api: VisibilityApi }> {
  const api = visibilityApi(options)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: { template: '<div />' } },
      { path: '/admin/property-visibility', component: PropertyVisibilityPage, props: { fetchFn: api.fetch } },
      { path: '/admin/override-roles', component: { template: '<div />' } },
    ],
  })
  await router.push('/admin/property-visibility')
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

function row(wrapper: VueWrapper, name: string) {
  return wrapper.findAll('[data-testid="visibility-row"]').find((candidate) => candidate.text().includes(name))!
}

async function edit(wrapper: VueWrapper, name: string) {
  await row(wrapper, name).get('[data-testid="visibility-edit"]').trigger('click')
  await flushPromises()
}

function dialog(): HTMLElement {
  return document.body.querySelector('[data-testid="visibility-dialog"]') as HTMLElement
}

async function chooseMode(value: string) {
  const radio = dialog().querySelector(`[data-testid="visibility-mode"] [value="${value}"]`) as HTMLElement
  radio.click()
  await flushPromises()
}

async function check(testId: string) {
  ;(dialog().querySelector(`[data-testid="${testId}"]`) as HTMLElement).click()
  await flushPromises()
}

async function save() {
  ;(dialog().querySelector('[data-testid="visibility-save"]') as HTMLElement).click()
  await flushPromises()
}

// spec metamodel/property-visibility "Page \"Property visibility\""
describe('PropertyVisibilityPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  // the select of the person metatype re-renders on shared state, so a test unmounts its pages
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('lists the properties with mode, badge and metatypes, filtered by name', async () => {
    const { wrapper } = await open()
    expect(wrapper.findAll('[data-testid="visibility-row"]')).toHaveLength(3)
    // "Badge in the list"
    expect(row(wrapper, 'Investment Cost').find('[data-testid="visibility-restricted"]').text()).toBe('Restricted')
    expect(row(wrapper, 'Investment Cost').text()).toContain('Read: Auditor · Write: nobody')
    expect(row(wrapper, 'Status').find('[data-testid="visibility-restricted"]').exists()).toBe(false)
    expect(row(wrapper, 'Status').text()).toContain('Application Component')
    // SCR-032 leads to the override roles (us-128-override-role-list D5)
    expect(wrapper.get('[data-testid="visibility-override-link"]').attributes('href')).toBe('/admin/override-roles')
    await wrapper.get('[data-testid="visibility-filter"] input, input[data-testid="visibility-filter"]').setValue('secur')
    expect(wrapper.findAll('[data-testid="visibility-row"]').map((candidate) => candidate.text())).toEqual([
      expect.stringContaining('Security Classification'),
    ])
  })

  it('shows the role selections for "Role-based" only', async () => {
    const { wrapper } = await open()
    await edit(wrapper, 'Security Classification')
    expect(dialog().querySelector('[data-testid="visibility-read-roles"]')).toBeNull()
    await chooseMode('role-restricted')
    expect(dialog().querySelector('[data-testid="visibility-read-roles"]')).not.toBeNull()
    expect(dialog().querySelector('[data-testid="visibility-write-roles"]')).not.toBeNull()
    expect(dialog().querySelector('[data-testid="visibility-scoping-type"]')).toBeNull()
  })

  it('rejects "Role-based" without a read role without a request', async () => {
    const { wrapper, api } = await open()
    await edit(wrapper, 'Security Classification')
    await chooseMode('role-restricted')
    await save()
    expect(dialog().querySelector('[data-testid="visibility-read-roles"]')!.textContent).toContain('Choose at least one read role.')
    expect(api.puts).toHaveLength(0)
  })

  it('rejects a write role that is no read role without a request', async () => {
    const { wrapper, api } = await open()
    await edit(wrapper, 'Security Classification')
    await chooseMode('role-restricted')
    await check('read-role-auditor')
    await check('write-role-viewer')
    await save()
    expect(dialog().querySelector('[data-testid="visibility-write-roles"]')!.textContent).toContain('must also be a read role')
    expect(api.puts).toHaveLength(0)
  })

  it('saves "Role-based" with read and write roles', async () => {
    const { wrapper, api } = await open()
    await edit(wrapper, 'Security Classification')
    await chooseMode('role-restricted')
    await check('read-role-auditor')
    await check('read-role-enterprise-architect')
    await check('write-role-enterprise-architect')
    await save()
    expect(api.puts).toEqual([
      [
        {
          propertyDefId: VISIBILITY_IDS.classification,
          visibilityMode: 'role-restricted',
          readRoles: ['auditor', 'enterprise-architect'],
          writeRoles: ['enterprise-architect'],
          scopingConnectionType: null,
        },
      ],
    ])
    expect(dialog()).toBeNull()
    expect(row(wrapper, 'Security Classification').find('[data-testid="visibility-restricted"]').exists()).toBe(true)
  })

  it('offers only connection metatypes for "Connection-based" and requires one', async () => {
    const { wrapper, api } = await open()
    await edit(wrapper, 'Security Classification')
    await chooseMode('connection-scoped')
    expect(dialog().querySelector('[data-testid="visibility-read-roles"]')).toBeNull()
    await save()
    expect(dialog().querySelector('[data-testid="visibility-scoping-type"]')!.textContent).toContain('Choose a connection type.')
    expect(api.puts).toHaveLength(0)
    const page = wrapper.findComponent({ name: 'PropertyVisibilityDialog' })
    const items = (page.vm as unknown as { $: { setupState: { typeItems: { value: string; label: string }[] } } }).$.setupState.typeItems
    expect(items.map((item) => item.label)).toEqual(['Domain Assignment'])
  })

  it('saves "Public" without roles and type', async () => {
    const { wrapper, api } = await open()
    await edit(wrapper, 'Investment Cost')
    await chooseMode('public')
    await save()
    expect(api.puts[0]).toEqual([
      { propertyDefId: VISIBILITY_IDS.cost, visibilityMode: 'public', readRoles: [], writeRoles: [], scopingConnectionType: null },
    ])
  })

  it('marks the field named by a 422', async () => {
    const { wrapper, api } = await open({ rejectWith: ['[0].readRoles'] })
    await edit(wrapper, 'Investment Cost')
    await save()
    expect(api.puts).toHaveLength(1)
    expect(dialog().querySelector('[data-testid="visibility-read-roles"]')!.textContent).toContain('Choose at least one read role.')
  })

  // "Choose the person metatype" (us-127-connection-scoped-visibility D8)
  it('offers the element metatypes as the entity type of persons and saves the choice', async () => {
    const { wrapper, api } = await open()
    const page = wrapper.findComponent(PropertyVisibilityPage)
    const state = (page.vm as unknown as {
      $: { setupState: { personMetatypeItems: { value: string; label: string }[]; choosePersonMetatype: (value: string) => Promise<void> } }
    }).$.setupState
    expect(state.personMetatypeItems.map((item) => item.label)).toEqual(['None', 'Application Component', 'Business Actor'])
    expect(wrapper.get('[data-testid="person-metatype"]').text()).toContain('Entity type of persons')
    await state.choosePersonMetatype(VISIBILITY_IDS.actor)
    await flushPromises()
    expect(api.personMetatypePuts).toEqual([VISIBILITY_IDS.actor])
    // unsetting sends null
    await state.choosePersonMetatype('none')
    await flushPromises()
    expect(api.personMetatypePuts).toEqual([VISIBILITY_IDS.actor, null])
  })

  it('keeps the previous person metatype when the server rejects the choice', async () => {
    const { wrapper } = await open({ personMetatype: VISIBILITY_IDS.actor })
    const page = wrapper.findComponent(PropertyVisibilityPage)
    const state = (page.vm as unknown as {
      $: { setupState: { personMetatype: string; choosePersonMetatype: (value: string) => Promise<void> } }
    }).$.setupState
    expect(state.personMetatype).toBe(VISIBILITY_IDS.actor)
    await state.choosePersonMetatype(VISIBILITY_IDS.assignment)
    await flushPromises()
    expect(state.personMetatype).toBe(VISIBILITY_IDS.actor)
    expect(wrapper.get('[data-testid="person-metatype"]').text()).toContain('could not be saved')
  })

  it('warns about connection-based properties without a person metatype', async () => {
    const missing = await open({ connectionScoped: true })
    expect(missing.wrapper.find('[data-testid="person-metatype-missing"]').exists()).toBe(true)
    const set = await open({ connectionScoped: true, personMetatype: VISIBILITY_IDS.actor })
    expect(set.wrapper.find('[data-testid="person-metatype-missing"]').exists()).toBe(false)
    const none = await open()
    expect(none.wrapper.find('[data-testid="person-metatype-missing"]').exists()).toBe(false)
  })

  // "Page without the permission"
  it('says that access is not permitted without visibility:configure', async () => {
    const { wrapper } = await open({ me: ME_METAMODEL_ADMIN })
    expect(wrapper.find('[data-testid="no-visibility-access"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="visibility-table"]').exists()).toBe(false)
  })
})
