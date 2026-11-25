import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../session/store'
import { ME_WITHOUT_ROLE, METATYPES } from '../test/catalogs'
import { deletedEntitiesApi, deletedEntity, ME_DELETED_RESTORER, ME_DELETED_WRITER, type DeletedEntitiesApiOptions } from '../test/deletedEntities'
import type { RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import DeletedEntitiesPage from './DeletedEntitiesPage.vue'

const mounted: VueWrapper[] = []

async function mountPage(me: unknown, options: Partial<DeletedEntitiesApiOptions> = {}) {
  const router = testRouter()
  await router.push('/')
  const recorded: RecordedRequest[] = []
  const fetchFn = deletedEntitiesApi({ me, recorded, ...options })
  const wrapper = mount(DeletedEntitiesPage, { props: { fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, recorded }
}

const body = (testid: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testid}"]`)

const names = (wrapper: VueWrapper) => wrapper.findAll('[data-testid="deleted-entity"]').map((item) => item.find('span span').text().replace(/\s+/g, ' ').trim())

async function restoreEntry(wrapper: VueWrapper, index: number) {
  await wrapper.findAll('[data-testid="deleted-entity-restore"]')[index]!.trigger('click')
  await vi.waitFor(() => expect(body('restore-entity-dialog')).not.toBeNull())
  body('restore-entity-confirm')!.click()
  await flushPromises()
}

// spec repository/deleted-entities "Page Deleted entities" (SCR-068)
describe('DeletedEntitiesPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
  })

  it('lists the deleted entities with actor, time, cascade origin and the endpoints of a connection', async () => {
    const { wrapper, recorded } = await mountPage(ME_DELETED_RESTORER)
    expect(names(wrapper)).toEqual(['ERP', 'ERP Finance', 'ERP to CRM (ERP → CRM)'])
    const items = wrapper.findAll('[data-testid="deleted-entity"]')
    expect(items[0]!.get('[data-testid="deleted-entity-by"]').text()).toContain('Deleted by Kurt')
    expect(items[0]!.find('[data-testid="deleted-entity-with"]').exists()).toBe(false)
    expect(items[1]!.get('[data-testid="deleted-entity-with"]').text()).toBe('Deleted with ERP')
    expect(items[2]!.text()).toContain('Connection')
    expect(recorded.find((request) => request.path.startsWith('/api/v1/entities/deleted'))!.path).toBe('/api/v1/entities/deleted?pageSize=50')
  })

  it('shows an entry without a recorded actor with the time only', async () => {
    const { wrapper } = await mountPage(ME_DELETED_RESTORER, { entries: [deletedEntity({ id: 1, name: 'Orphan', deletedBy: undefined })] })
    expect(wrapper.get('[data-testid="deleted-entity-by"]').text()).toMatch(/Deleted /)
    expect(wrapper.get('[data-testid="deleted-entity-by"]').text()).not.toContain('Deleted by')
  })

  it('shows "No deleted entities." for an empty list', async () => {
    const { wrapper } = await mountPage(ME_DELETED_RESTORER, { entries: [] })
    expect(wrapper.get('[data-testid="deleted-entities-empty"]').text()).toContain('No deleted entities.')
    expect(wrapper.find('[data-testid="deleted-entities-list"]').exists()).toBe(false)
  })

  it('filters by the name part', async () => {
    const { wrapper, recorded } = await mountPage(ME_DELETED_RESTORER)
    await wrapper.get('[data-testid="deleted-entities-filter"]').setValue('fin')
    await vi.waitFor(() => expect(names(wrapper)).toEqual(['ERP Finance']))
    expect(recorded.at(-1)!.path).toContain('q=fin')
  })

  it('limits the name filter to 255 characters', async () => {
    const { wrapper } = await mountPage(ME_DELETED_RESTORER)
    expect(wrapper.get('[data-testid="deleted-entities-filter"]').attributes('maxlength')).toBe('255')
  })

  it('filters by type', async () => {
    const { wrapper, recorded } = await mountPage(ME_DELETED_RESTORER)
    wrapper.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', METATYPES[1]!.id)
    await vi.waitFor(() => expect(recorded.at(-1)!.path).toContain(`metatypeId=${METATYPES[1]!.id}`))
    await flushPromises()
    expect(names(wrapper)).toEqual(['ERP', 'ERP Finance'])
  })

  it('loads the next page with the cursor', async () => {
    const { wrapper, recorded } = await mountPage(ME_DELETED_RESTORER, { pageSize: 2 })
    expect(names(wrapper)).toHaveLength(2)
    await wrapper.get('[data-testid="deleted-entities-load-more"]').trigger('click')
    await flushPromises()
    expect(names(wrapper)).toHaveLength(3)
    expect(wrapper.find('[data-testid="deleted-entities-load-more"]').exists()).toBe(false)
    expect(recorded.at(-1)!.path).toContain('cursor=cursor-1')
    expect(recorded.at(-1)!.path).toContain('page=1')
  })

  it('restores an entry after a confirmation and names what was restored and what stayed deleted', async () => {
    const { wrapper, recorded } = await mountPage(ME_DELETED_RESTORER, { notRestored: [{ entityId: 13, reason: 'endpoint-deleted' }] })
    await restoreEntry(wrapper, 0)
    const request = recorded.find((entry) => entry.path === '/api/v1/entities/11/undelete')!
    expect((request.init!.headers as Record<string, string>)['If-Match']).toBe('"1"')
    expect(request.init!.method).toBe('POST')
    const notice = wrapper.get('[data-testid="restore-notice"]')
    expect(notice.text()).toContain('Restored: ERP, ERP Finance.')
    expect(wrapper.get('[data-testid="restore-notice-stayed"]').text()).toBe('Stayed deleted: ERP to CRM (the other endpoint is deleted).')
    // the list is reloaded: the restored entries left it, the connection stayed
    expect(names(wrapper)).toEqual(['ERP to CRM (ERP → CRM)'])
  })

  it('does not restore when the confirmation is cancelled', async () => {
    const { wrapper, recorded } = await mountPage(ME_DELETED_RESTORER)
    await wrapper.findAll('[data-testid="deleted-entity-restore"]')[0]!.trigger('click')
    await vi.waitFor(() => expect(body('restore-entity-dialog')).not.toBeNull())
    body('restore-entity-cancel')!.click()
    await flushPromises()
    expect(recorded.some((entry) => entry.path.includes('/undelete'))).toBe(false)
    expect(names(wrapper)).toHaveLength(3)
  })

  it('names the origin in the confirmation of an entry taken along', async () => {
    const { wrapper } = await mountPage(ME_DELETED_RESTORER)
    await wrapper.findAll('[data-testid="deleted-entity-restore"]')[1]!.trigger('click')
    await vi.waitFor(() => expect(body('restore-entity-with')).not.toBeNull())
    expect(body('restore-entity-with')!.textContent).toContain('deleted with “ERP”')
  })

  it.each([
    [412, 'stale-version', 'restore-notice-stale'],
    [409, 'entity-not-deleted', 'restore-notice-stale'],
    [404, 'not-found', 'restore-notice-stale'],
    [409, 'connection-not-restorable', 'restore-notice-endpoint'],
  ])('answers %i %s with a notice and reloads the list', async (status, type, notice) => {
    const { wrapper, recorded } = await mountPage(ME_DELETED_RESTORER, { restoreFails: { status, type } })
    const loads = () => recorded.filter((entry) => entry.path.startsWith('/api/v1/entities/deleted')).length
    expect(loads()).toBe(1)
    await restoreEntry(wrapper, 2)
    expect(wrapper.find(`[data-testid="${notice}"]`).exists()).toBe(true)
    expect(loads()).toBe(2)
  })

  it('keeps the dialog open with a message when the restore is forbidden', async () => {
    const { wrapper } = await mountPage(ME_DELETED_RESTORER, { restoreFails: { status: 403, type: 'forbidden' } })
    await restoreEntry(wrapper, 0)
    expect(body('restore-entity-failure')!.textContent).toContain('You may not restore entities.')
    expect(wrapper.find('[data-testid="restore-notice"]').exists()).toBe(false)
  })

  it('shows "No permission" without entity:restore and requests no list', async () => {
    for (const me of [ME_DELETED_WRITER, ME_WITHOUT_ROLE]) {
      session.me = undefined
      session.loaded = false
      const { wrapper, recorded } = await mountPage(me)
      expect(wrapper.get('[data-testid="deleted-entities-no-permission"]').text()).toContain('No permission')
      expect(wrapper.find('[data-testid="deleted-entities-list"]').exists()).toBe(false)
      expect(recorded.some((entry) => entry.path.startsWith('/api/v1/entities/deleted'))).toBe(false)
    }
  })
})
