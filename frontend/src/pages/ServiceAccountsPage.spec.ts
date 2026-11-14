import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { defineComponent, h } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { session } from '../session/store'
import { ME_ARCHITECT, SECRET, SERVICE_ACCOUNT_IDS, serviceAccountApi, type ServiceAccountApi } from '../test/service-accounts'
import ServiceAccountsPage from './ServiceAccountsPage.vue'

const mounted: VueWrapper[] = []

async function open(options: Parameters<typeof serviceAccountApi>[0] = {}): Promise<{ wrapper: VueWrapper; api: ServiceAccountApi }> {
  const api = serviceAccountApi(options)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/service-accounts', component: ServiceAccountsPage, props: { fetchFn: api.fetch } },
      // the links of the header
      { path: '/:other(.*)*', component: { template: '<div />' } },
    ],
  })
  await router.push('/admin/service-accounts')
  const wrapper = mount(
    defineComponent({ render: () => [h(AppHeader, { fetchFn: api.fetch }), h(RouterView)] }),
    { global: { plugins: [ui, router] }, attachTo: document.body },
  )
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

function row(wrapper: VueWrapper, name: string) {
  return wrapper.findAll('[data-testid="service-account-row"]').find((candidate) => candidate.attributes('data-name') === name)!
}

function body(): HTMLElement {
  return document.body
}

function find(testId: string): HTMLElement | null {
  return body().querySelector(`[data-testid="${testId}"]`)
}

async function click(element: HTMLElement | null) {
  element!.click()
  await flushPromises()
}

async function fill(testId: string, value: string) {
  const input = body().querySelector(`[data-testid="${testId}"] input, input[data-testid="${testId}"]`) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function create(wrapper: VueWrapper, name: string) {
  await wrapper.get('[data-testid="service-account-new"]').trigger('click')
  await flushPromises()
  await fill('service-account-name', name)
  await click(find('service-account-save'))
}

// spec identity/service-accounts "Screen \"Service accounts\"" and "Dialogs of the screen \"Service accounts\""
describe('ServiceAccountsPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
    document.body.innerHTML = ''
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('lists status, roles, last use and expiry, and marks a secret that expires within 14 days', async () => {
    const { wrapper } = await open()
    const scanner = row(wrapper, 'ci-scanner')
    expect(scanner.get('[data-testid="service-account-status"]').text()).toBe('Active')
    expect(scanner.text()).toContain('Integration Writer')
    expect(scanner.get('[data-testid="service-account-last-used"]').text()).toBe('Never')
    // 10 days: expiring soon; 30 days: not
    expect(scanner.find('[data-testid="service-account-expires-soon"]').exists()).toBe(true)
    expect(row(wrapper, 'cmdb-importer').find('[data-testid="service-account-expires-soon"]').exists()).toBe(false)
    expect(row(wrapper, 'legacy-sync').get('[data-testid="service-account-status"]').text()).toBe('Deactivated')
  })

  it('offers only roles assignable to service accounts and sends the chosen roles', async () => {
    const { wrapper, api } = await open()
    await row(wrapper, 'cmdb-importer').get('[data-testid="service-account-edit-roles"]').trigger('click')
    await flushPromises()
    expect(find('service-account-role-viewer')).not.toBeNull()
    expect(find('service-account-role-integration-writer')).not.toBeNull()
    expect(find('service-account-role-system-admin')).toBeNull()
    await click(find('service-account-role-viewer'))
    await click(find('service-account-role-integration-writer'))
    await click(find('service-account-save'))
    expect(api.updates).toEqual([
      { id: SERVICE_ACCOUNT_IDS.importer, body: { name: 'cmdb-importer', description: null, roles: ['integration-writer'] } },
    ])
    expect(row(wrapper, 'cmdb-importer').text()).toContain('Integration Writer')
  })

  it('shows a new secret once with a copy button and forgets it when the dialog closes', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    const { wrapper, api } = await open()
    await create(wrapper, 'backup-agent')
    expect(api.creates).toEqual([{ name: 'backup-agent', description: null, roles: ['integration-writer'], secretValidityDays: 90 }])
    const value = (find('service-account-secret-value') as HTMLInputElement).value
    expect(value).toBe(`${SECRET}-1`)
    await click(find('service-account-secret-copy'))
    expect(writeText).toHaveBeenCalledWith(`${SECRET}-1`)
    await click(find('service-account-secret-close'))
    expect(find('service-account-secret')).toBeNull()
    expect(body().innerHTML).not.toContain(SECRET)
    expect(row(wrapper, 'backup-agent').exists()).toBe(true)
  })

  it('sends no request when the deactivation is cancelled, and shows the account inactive once confirmed', async () => {
    const { wrapper, api } = await open()
    await row(wrapper, 'ci-scanner').get('[data-testid="service-account-deactivate"]').trigger('click')
    await flushPromises()
    await click(find('service-account-confirm-cancel'))
    expect(api.deactivations).toEqual([])
    expect(row(wrapper, 'ci-scanner').get('[data-testid="service-account-status"]').text()).toBe('Active')

    await row(wrapper, 'ci-scanner').get('[data-testid="service-account-deactivate"]').trigger('click')
    await flushPromises()
    await click(find('service-account-confirm-ok'))
    expect(api.deactivations).toEqual([SERVICE_ACCOUNT_IDS.scanner])
    const scanner = row(wrapper, 'ci-scanner')
    expect(scanner.get('[data-testid="service-account-status"]').text()).toBe('Deactivated')
    expect(scanner.find('[data-testid="service-account-deactivate"]').exists()).toBe(false)
    expect(scanner.find('[data-testid="service-account-rotate"]').exists()).toBe(false)
  })

  it('shows the new secret after a confirmed rotation', async () => {
    const { wrapper, api } = await open()
    await row(wrapper, 'cmdb-importer').get('[data-testid="service-account-rotate"]').trigger('click')
    await flushPromises()
    await click(find('service-account-confirm-ok'))
    expect(api.rotations).toEqual([SERVICE_ACCOUNT_IDS.importer])
    expect((find('service-account-secret-value') as HTMLInputElement).value).toBe(`${SECRET}-1`)
  })

  it('shows a role rejection in the dialog and keeps it open', async () => {
    const { wrapper } = await open({ rejectRoleChanges: true })
    await row(wrapper, 'cmdb-importer').get('[data-testid="service-account-edit-roles"]').trigger('click')
    await flushPromises()
    await click(find('service-account-role-auditor'))
    await click(find('service-account-save'))
    expect(find('service-account-failure')!.textContent).toContain('assignable to service accounts')
    expect(find('service-account-dialog')).not.toBeNull()
    expect(row(wrapper, 'cmdb-importer').text()).not.toContain('Auditor')
  })

  it('asks to log in again when the authentication is older than 5 minutes', async () => {
    const { wrapper } = await open({ staleAuthentication: true })
    await create(wrapper, 'late-agent')
    expect(find('service-account-failure')!.textContent).toContain('log in again')
    expect(find('service-account-dialog')).not.toBeNull()
    expect(find('service-account-secret')).toBeNull()
  })

  it('shows a taken name in the dialog', async () => {
    const { wrapper } = await open()
    await create(wrapper, 'CI-Scanner')
    expect(find('service-account-failure')!.textContent).toContain('already exists')
  })

  it('shows the page and its link with service-account:manage only', async () => {
    const admin = await open()
    expect(admin.wrapper.find('[data-testid="header-service-accounts"]').exists()).toBe(true)
    expect(admin.wrapper.find('[data-testid="service-accounts-table"]').exists()).toBe(true)
    admin.wrapper.unmount()
    mounted.splice(0)
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false

    const architect = await open({ me: ME_ARCHITECT })
    expect(architect.wrapper.find('[data-testid="header-service-accounts"]').exists()).toBe(false)
    expect(architect.wrapper.find('[data-testid="no-service-accounts-access"]').exists()).toBe(true)
    expect(architect.wrapper.find('[data-testid="service-accounts-table"]').exists()).toBe(false)
  })
})
