import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import {
  APPLICATIONS,
  CATALOG,
  catalogApi,
  LEGACY_CRM,
  ME_ARCHITECT,
  ME_RESTORER,
  type Application,
  type CatalogRequest,
} from '../test/catalogs'
import CatalogPage from './CatalogPage.vue'

const CATALOG_PATH = `/catalogs/${CATALOG.id}`
const opened: VueWrapper[] = []

async function open(location: string, applications: Application[], options: Parameters<typeof catalogApi>[1] = {}) {
  const recorded: CatalogRequest[] = []
  const fetchFn = catalogApi(applications, { recorded, ...options })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/catalogs', component: { render: () => h('div') } },
      { path: '/catalogs/:id', component: CatalogPage, props: (route) => ({ id: route.params.id, fetchFn }) },
      { path: '/entities/:id/history', component: { render: () => h('div') } },
    ],
  })
  await router.push(location)
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] }, attachTo: document.body })
  opened.push(wrapper)
  await flushPromises()
  return { wrapper, router, recorded }
}

afterEach(() => {
  opened.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.innerHTML = ''
})

const rows = (wrapper: VueWrapper) => wrapper.findAll('[data-testid="catalog-table"] tbody tr')
const queries = (recorded: CatalogRequest[]) => recorded.filter((request) => request.path.endsWith('/query'))
const body = (testid: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testid}"]`)

async function confirmRestore(wrapper: VueWrapper, row: string) {
  await wrapper.get(`[data-testid="${row}"]`).trigger('click')
  await vi.waitFor(() => expect(body('restore-entity-dialog')).not.toBeNull())
  body('restore-entity-confirm')!.click()
  await flushPromises()
}

// spec catalog/catalog-query "Show deleted rows" (SCR-020)
describe('CatalogPage with deleted rows', () => {
  const withLegacy = [...APPLICATIONS, LEGACY_CRM]
  const legacyId = withLegacy.length

  it('offers the switch "Show deleted" to a holder of entity:restore only', async () => {
    expect((await open(CATALOG_PATH, withLegacy, { me: ME_RESTORER })).wrapper.find('[data-testid="show-deleted"]').exists()).toBe(true)
    expect((await open(CATALOG_PATH, withLegacy, { me: ME_ARCHITECT })).wrapper.find('[data-testid="show-deleted"]').exists()).toBe(false)
  })

  it('shows deleted rows marked with their time and the action "Restore" when the switch is on', async () => {
    const { wrapper, recorded, router } = await open(CATALOG_PATH, withLegacy, { me: ME_RESTORER })
    expect(rows(wrapper)).toHaveLength(3)
    expect(wrapper.find('[data-testid="catalog-title"]').exists()).toBe(true)
    expect(queries(recorded).at(-1)!.query.includeDeleted).toBeUndefined()
    await wrapper.get('[data-testid="show-deleted"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.deleted).toBe('1')
    expect(queries(recorded).at(-1)!.query.includeDeleted).toBe('true')
    expect(rows(wrapper)).toHaveLength(4)
    const deleted = rows(wrapper)[3]!
    expect(deleted.text()).toContain('Legacy CRM')
    // the marker is text, not colour only
    expect(deleted.get(`[data-testid="deleted-${legacyId}"]`).text()).toBe('Deleted')
    expect(deleted.get(`[data-testid="deleted-at-${legacyId}"]`).text()).toMatch(/^Deleted /)
    expect(deleted.get(`[data-testid="restore-${legacyId}"]`).attributes('aria-label')).toBe('Restore Legacy CRM')
    // active rows have no marker and no restore action; a deleted one has no history link
    expect(rows(wrapper)[0]!.find('[data-testid^="deleted-"]').exists()).toBe(false)
    expect(rows(wrapper)[0]!.find('[data-testid^="restore-"]').exists()).toBe(false)
    expect(deleted.find(`[data-testid="history-${legacyId}"]`).exists()).toBe(false)
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('4 entries')
  })

  it('starts with the switch on from the URL and keeps it on reload', async () => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?deleted=1`, withLegacy, { me: ME_RESTORER })
    expect(queries(recorded).at(-1)!.query.includeDeleted).toBe('true')
    expect(rows(wrapper)).toHaveLength(4)
    expect(wrapper.get('[data-testid="show-deleted"]').attributes('aria-checked')).toBe('true')
  })

  it('drops the URL value without entity:restore and shows active rows only', async () => {
    const { wrapper, recorded, router } = await open(`${CATALOG_PATH}?deleted=1`, withLegacy, { me: ME_ARCHITECT })
    await vi.waitFor(() => expect(router.currentRoute.value.query.deleted).toBeUndefined())
    await flushPromises()
    expect(queries(recorded).every((request) => request.query.includeDeleted === undefined)).toBe(true)
    expect(rows(wrapper)).toHaveLength(3)
  })

  it('drops another value of the parameter', async () => {
    const { router, recorded } = await open(`${CATALOG_PATH}?deleted=yes`, withLegacy, { me: ME_RESTORER })
    await vi.waitFor(() => expect(router.currentRoute.value.query.deleted).toBeUndefined())
    expect(queries(recorded).every((request) => request.query.includeDeleted === undefined)).toBe(true)
  })

  it('shows the first page without a cursor when the switch changes', async () => {
    const many = [...Array.from({ length: 60 }, (_, index) => ({ name: `Application ${String(index + 1).padStart(3, '0')}` })), LEGACY_CRM]
    const { wrapper, router, recorded } = await open(`${CATALOG_PATH}?page=1`, many, { me: ME_RESTORER })
    expect(router.currentRoute.value.query.page).toBe('1')
    await wrapper.get('[data-testid="show-deleted"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBeUndefined()
    const last = queries(recorded).at(-1)!
    expect(last.query.includeDeleted).toBe('true')
    expect(last.query.cursor).toBeUndefined()
    expect(last.query.page).toBeUndefined()
  })

  it('restores a row with the version of the row and shows it as an active row', async () => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?deleted=1`, withLegacy, { me: ME_RESTORER })
    await confirmRestore(wrapper, `restore-${legacyId}`)
    const request = recorded.find((entry) => entry.path === `/api/v1/entities/${legacyId}/undelete`)!
    expect(request.method).toBe('POST')
    expect(wrapper.get('[data-testid="restore-notice"]').text()).toContain('Restored: Legacy CRM.')
    expect(rows(wrapper)).toHaveLength(4)
    expect(wrapper.find(`[data-testid="deleted-${legacyId}"]`).exists()).toBe(false)
    expect(wrapper.find(`[data-testid="history-${legacyId}"]`).exists()).toBe(true)
  })

  it('names what stayed deleted after a restore', async () => {
    const { wrapper } = await open(`${CATALOG_PATH}?deleted=1`, withLegacy, {
      me: ME_RESTORER,
      undeleteResult: { notRestored: [{ entityId: 1, reason: 'endpoint-deleted' }] },
    })
    await confirmRestore(wrapper, `restore-${legacyId}`)
    expect(wrapper.get('[data-testid="restore-notice-stayed"]').text()).toBe('Stayed deleted: CRM System (the other endpoint is deleted).')
  })

  it.each([
    [412, 'stale-version', 'restore-notice-stale'],
    [409, 'entity-not-deleted', 'restore-notice-stale'],
    [409, 'connection-not-restorable', 'restore-notice-endpoint'],
  ])('answers %i %s with a notice and refreshes the rows', async (status, type, notice) => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?deleted=1`, withLegacy, { me: ME_RESTORER, undeleteFails: { status, type } })
    const before = queries(recorded).length
    await confirmRestore(wrapper, `restore-${legacyId}`)
    expect(wrapper.find(`[data-testid="${notice}"]`).exists()).toBe(true)
    expect(queries(recorded).length).toBe(before + 1)
  })

  // spec catalog/catalog-query "Not in a saved view"
  it('does not keep the switch in a saved view and opening a view shows active rows only', async () => {
    const view = { id: 'f2000000-0000-4000-8000-000000000001', name: 'Compact view', isDefault: false, columnOrder: ['name'], available: true }
    const { wrapper, router, recorded } = await open(`${CATALOG_PATH}?deleted=1`, withLegacy, { me: ME_RESTORER, catalog: { ...CATALOG, savedViews: [view] } })
    expect(rows(wrapper)).toHaveLength(4)
    await wrapper.get('[data-testid="view-save"]').trigger('click')
    await vi.waitFor(() => expect(body('view-save-name')).not.toBeNull())
    const input = document.body.querySelector<HTMLInputElement>('[data-testid="view-save-name"] input, input[data-testid="view-save-name"]')!
    input.value = 'Inventory view'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    body('view-save-submit')!.click()
    await flushPromises()
    const post = recorded.find((request) => request.method === 'POST' && request.path.endsWith('/views'))!
    expect(Object.keys(post.body as object).some((key) => /deleted/i.test(key))).toBe(false)
    expect(JSON.stringify(post.body)).not.toMatch(/deleted/i)
    // opening a view leaves the switch
    const select = wrapper.findAllComponents({ name: 'Select' }).find((component) => component.vm.$attrs['data-testid'] === 'view-select')!
    select.vm.$emit('update:modelValue', view.id)
    await flushPromises()
    expect(router.currentRoute.value.query.deleted).toBeUndefined()
    expect(router.currentRoute.value.query.view).toBe(view.id)
    expect(queries(recorded).at(-1)!.query.includeDeleted).toBeUndefined()
    expect(rows(wrapper)).toHaveLength(3)
  })
})
