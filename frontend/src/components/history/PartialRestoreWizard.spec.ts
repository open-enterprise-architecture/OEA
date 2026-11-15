import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { CRM_CONNECTIONS, CRM_VERSIONS, HISTORY_IDS, historyApi, OWNER_MISSING, PARTIAL_PREVIEW, PROPERTIES_ONLY } from '../../test/history'
import { testRouter } from '../../test/router'
import PartialRestoreWizard from './PartialRestoreWizard.vue'

// spec repository/entity-restore "Partial restore wizard", "Steps of the wizard" and "Field
// selection in the wizard" (US-104 AC1–AC3, US-105 AC1–AC3, REQ-104, REQ-105; design D6 of
// us-104-partial-restore)

const mounted: VueWrapper[] = []

async function mountWizard(options: Parameters<typeof historyApi>[0] = {}, sourceVersion = 4) {
  const api = historyApi({ preview: { fields: PARTIAL_PREVIEW, connections: CRM_CONNECTIONS }, ...options })
  const router = testRouter()
  const wrapper = mount(PartialRestoreWizard, {
    props: { open: true, entityId: HISTORY_IDS.crm, sourceVersion, versions: CRM_VERSIONS, currentVersion: 5, fetchFn: api.fetch },
    global: { plugins: [ui, router] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

function find(testId: string): HTMLElement | null {
  return document.body.querySelector(`[data-testid="${testId}"]`)
}

function checkbox(field: string): HTMLButtonElement {
  return find(`partial-restore-select-${field}`) as HTMLButtonElement
}

async function click(element: HTMLElement | null) {
  element!.click()
  await flushPromises()
}

function currentStep(): string | undefined {
  return document.body.querySelector('[data-testid^="partial-restore-step-"][aria-current="step"]')?.getAttribute('data-testid')?.replace('partial-restore-step-', '')
}

describe('PartialRestoreWizard', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('opens with the version as source on the step "General"', async () => {
    const { api } = await mountWizard()
    expect(api.previews).toEqual([4])
    expect(find('partial-restore-source-info')!.textContent).toContain('Version 4')
    expect(currentStep()).toBe('general')
  })

  it('grays out identical fields and offers the differing ones', async () => {
    await mountWizard()
    expect(checkbox('name').disabled).toBe(true)
    expect(find('partial-restore-row-name')!.textContent).toContain('Identical')
    expect(checkbox('description').disabled).toBe(false)
  })

  it('shows the source value of a selected field', async () => {
    await mountWizard()
    expect(find('partial-restore-after-description')).toBeNull()
    await click(checkbox('description'))
    expect(find('partial-restore-after-description')!.textContent).toContain('Legacy CRM')
  })

  it('disables fields that cannot be restored and shows the reason, without values when redacted', async () => {
    await mountWizard()
    await click(find('partial-restore-next'))
    expect(currentStep()).toBe('properties')
    expect(checkbox(HISTORY_IDS.cost).disabled).toBe(true)
    expect(find(`partial-restore-row-${HISTORY_IDS.cost}`)!.textContent).toContain('Restricted')
    expect(find(`partial-restore-row-${HISTORY_IDS.cost}`)!.textContent).toContain('Not restored: insufficient permission')
    expect(checkbox(HISTORY_IDS.goLive).disabled).toBe(true)
    expect(find(`partial-restore-row-${HISTORY_IDS.goLive}`)!.textContent).toContain('(not present in source version)')
    expect(checkbox(HISTORY_IDS.owner).disabled).toBe(false)
  })

  it('skips a step without differences with a note', async () => {
    await mountWizard({ preview: { fields: PROPERTIES_ONLY } })
    expect(currentStep()).toBe('properties')
    expect(find('partial-restore-skipped')!.textContent).toContain('No differences in this step: General')
  })

  it('"Skip" drops the selection of the step', async () => {
    await mountWizard()
    await click(checkbox('description'))
    await click(find('partial-restore-skip'))
    await click(find('partial-restore-next'))
    await click(find('partial-restore-next'))
    expect(currentStep()).toBe('summary')
    expect(find('partial-restore-nothing-selected')).not.toBeNull()
    expect((find('partial-restore-confirm') as HTMLButtonElement).disabled).toBe(true)
    await click(find('partial-restore-back'))
    expect(currentStep()).toBe('connections')
  })

  it('lists the connections with a link to the history of a changed one', async () => {
    await mountWizard()
    await click(find('partial-restore-next'))
    await click(find('partial-restore-next'))
    expect(currentStep()).toBe('connections')
    expect(find('partial-restore-connection-201')!.textContent).toContain('Added since')
    expect(find('partial-restore-connection-history-201')).toBeNull()
    expect(find('partial-restore-connection-history-202')!.getAttribute('href')).toBe('/entities/202/history')
  })

  it('sends the selected fields with If-Match and shows their warnings', async () => {
    const { wrapper, api } = await mountWizard({ preview: { fields: PARTIAL_PREVIEW, warnings: OWNER_MISSING, connections: CRM_CONNECTIONS } })
    await click(checkbox('description'))
    await click(find('partial-restore-next'))
    await click(checkbox(HISTORY_IDS.owner))
    await click(find('partial-restore-next'))
    await click(find('partial-restore-next'))
    expect(currentStep()).toBe('summary')
    expect(find('partial-restore-selection')!.textContent).toContain('Owner')
    expect(find('partial-restore-warnings')!.textContent).toContain('The mandatory property Owner will have no value.')
    await click(find('partial-restore-confirm'))
    expect(api.restores).toEqual([{ ifMatch: '"5"', body: { targetVersion: 4, fields: ['description', HISTORY_IDS.owner] } }])
    expect(wrapper.emitted('restored')![0]![0]).toMatchObject({ version: 6, restoredFromVersion: 4 })
  })

  it('reloads and clears the selection when the source version changes', async () => {
    const { wrapper, api } = await mountWizard()
    await click(checkbox('description'))
    ;(wrapper.vm as unknown as { source: number }).source = 2
    await flushPromises()
    expect(api.previews).toEqual([4, 2])
    expect(checkbox('description').getAttribute('aria-checked')).toBe('false')
  })

  it('reloads the preview when the entity was changed meanwhile', async () => {
    const { wrapper, api } = await mountWizard({ changedMeanwhileTo: 6 })
    await click(checkbox('description'))
    for (let index = 0; index < 3; index++) await click(find('partial-restore-next'))
    await click(find('partial-restore-confirm'))
    expect(find('partial-restore-changed-meanwhile')).not.toBeNull()
    expect(api.previews).toEqual([4, 4])
    expect(wrapper.emitted('restored')).toBeUndefined()
    // the selection is kept, and the next restore names the new current version
    await click(find('partial-restore-confirm'))
    expect(api.restores.map((restore) => restore.ifMatch)).toEqual(['"5"', '"6"'])
  })
})
