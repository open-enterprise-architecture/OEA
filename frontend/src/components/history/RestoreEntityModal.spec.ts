import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { HISTORY_IDS, historyApi, OWNER_MISSING, PREVIEW_NOTHING, PREVIEW_WITH_KEPT } from '../../test/history'
import RestoreEntityModal from './RestoreEntityModal.vue'

// spec repository/entity-restore "Restore modal" (US-101 AC2, AC3, REQ-101 AC2–AC5; design D5 of
// us-101-entity-restore)

const mounted: VueWrapper[] = []

async function mountModal(options: Parameters<typeof historyApi>[0] = {}, targetVersion = 3) {
  const api = historyApi(options)
  const wrapper = mount(RestoreEntityModal, {
    props: { open: true, entityId: HISTORY_IDS.crm, targetVersion, fetchFn: api.fetch },
    global: { plugins: [ui] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

function find(testId: string): HTMLElement | null {
  return document.body.querySelector(`[data-testid="${testId}"]`)
}

function reasonField(): HTMLTextAreaElement {
  const element = find('restore-reason')!
  return (element.tagName === 'TEXTAREA' ? element : element.querySelector('textarea')) as HTMLTextAreaElement
}

function confirmButton(): HTMLButtonElement {
  return find('restore-confirm') as HTMLButtonElement
}

async function click(element: HTMLElement | null) {
  element!.click()
  await flushPromises()
}

describe('RestoreEntityModal', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('lists the changes from the current value to the value of the target version', async () => {
    const { api } = await mountModal()
    expect(api.previews).toEqual([3])
    const name = find('restore-field-name')!
    expect(name.querySelector('[data-testid="restore-current"]')!.textContent).toContain('CRM System')
    expect(name.querySelector('[data-testid="restore-target"]')!.textContent).toContain('CRM')
    expect(find(`restore-field-${HISTORY_IDS.status}`)!.textContent).toContain('Active')
    expect(find('restore-notices')!.textContent).toContain('ID, type and connection endpoints are not changed.')
    expect(confirmButton().disabled).toBe(false)
  })

  it('lists the fields that are not restored with their reason', async () => {
    await mountModal({ preview: { fields: PREVIEW_WITH_KEPT } })
    expect(find(`restore-kept-${HISTORY_IDS.cost}`)!.textContent).toContain('Not restored: insufficient permission')
    expect(find(`restore-kept-${HISTORY_IDS.goLive}`)!.textContent).toContain('Not restored: the value is no longer valid')
    // a restricted field is never shown with values
    expect(find(`restore-kept-${HISTORY_IDS.cost}`)!.textContent).not.toContain('null')
    expect(find(`restore-field-${HISTORY_IDS.cost}`)).toBeNull()
  })

  it('shows the warnings of empty mandatory properties', async () => {
    await mountModal({ preview: { fields: PREVIEW_WITH_KEPT, warnings: OWNER_MISSING } })
    expect(find('restore-warning')!.textContent).toContain('The mandatory property Owner will have no value.')
  })

  it('disables "Restore" when nothing can be restored', async () => {
    await mountModal({ preview: { fields: PREVIEW_NOTHING } })
    expect(find('restore-nothing')).not.toBeNull()
    expect(confirmButton().disabled).toBe(true)
  })

  it('limits the reason to 1000 characters', async () => {
    await mountModal()
    expect(reasonField().getAttribute('maxlength')).toBe('1000')
  })

  it('sends nothing when canceled', async () => {
    const { wrapper, api } = await mountModal()
    await click(find('restore-cancel'))
    expect(api.restores).toEqual([])
    expect(wrapper.emitted('update:open')).toEqual([[false]])
  })

  it('restores with If-Match of the current version and the reason', async () => {
    const { wrapper, api } = await mountModal()
    const reason = reasonField()
    reason.value = '  Wrong lifecycle status  '
    reason.dispatchEvent(new Event('input'))
    await flushPromises()
    await click(confirmButton())
    expect(api.restores).toEqual([{ ifMatch: '"5"', body: { targetVersion: 3, reason: 'Wrong lifecycle status' } }])
    expect(wrapper.emitted('restored')![0]![0]).toMatchObject({ version: 6, restoredFromVersion: 3 })
    expect(wrapper.emitted('update:open')).toEqual([[false]])
  })

  it('sends no reason when it is empty', async () => {
    const { api } = await mountModal()
    await click(confirmButton())
    expect(api.restores[0]!.body).toEqual({ targetVersion: 3 })
  })

  it('reloads the preview when the entity was changed meanwhile', async () => {
    const { wrapper, api } = await mountModal({ changedMeanwhileTo: 6 })
    await click(confirmButton())
    expect(find('restore-changed-meanwhile')).not.toBeNull()
    expect(api.previews).toEqual([3, 3])
    expect(wrapper.emitted('restored')).toBeUndefined()
    // the next restore names the new current version
    await click(confirmButton())
    expect(api.restores.map((restore) => restore.ifMatch)).toEqual(['"5"', '"6"'])
    expect(wrapper.emitted('restored')).toHaveLength(1)
  })
})
