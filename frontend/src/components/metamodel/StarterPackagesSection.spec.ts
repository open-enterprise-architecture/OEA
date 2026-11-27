import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { metamodel } from '../../metamodel/store'
import { session } from '../../session/store'
import { mountWithMetamodel } from '../../test/metamodelMount'
import StarterPackagesSection from './StarterPackagesSection.vue'

// spec metamodel/starter-packages "Starter packages on the metamodel configuration" (SCR-030; design D7 of us-074-bpmn-process-diagrams)

const dialog = (testId: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)

async function click(testId: string) {
  await vi.waitFor(() => expect(dialog(testId)).not.toBeNull())
  dialog(testId)!.click()
  await flushPromises()
}

describe('StarterPackagesSection', () => {
  beforeEach(() => {
    session.me = undefined
    document.body.innerHTML = ''
  })

  it('lists the packages with name, version, description, counts and the state, sorted by identifier', async () => {
    const { wrapper } = await mountWithMetamodel(StarterPackagesSection)
    const articles = wrapper.findAll('[data-testid^="starter-package-"]')
    expect(articles.map((entry) => entry.attributes('data-testid'))).toEqual(['starter-package-oea-starter-bpmn', 'starter-package-oea-starter-togaf-classic'])
    const bpmn = wrapper.get('[data-testid="starter-package-oea-starter-bpmn"]')
    expect(bpmn.text()).toContain('BPMN 2.0 process modeling')
    expect(bpmn.text()).toContain('1.0.0')
    expect(bpmn.get('[data-testid="starter-counts"]').text()).toBe('31 entity types, 2 properties')
    expect(bpmn.text()).toContain('Pool, Lane, User Task, Sequence Flow')
    expect(bpmn.find('[data-testid="starter-imported-badge"]').exists()).toBe(false)
  })

  it('offers "Import" for a package that is not imported and none for an imported one', async () => {
    const { wrapper } = await mountWithMetamodel(StarterPackagesSection)
    expect(wrapper.find('[data-testid="starter-import-oea-starter-bpmn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="starter-import-oea-starter-togaf-classic"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="starter-package-oea-starter-togaf-classic"] [data-testid="starter-imported-badge"]').text()).toBe('Imported')
  })

  it('asks for confirmation and imports nothing when it is cancelled', async () => {
    const { wrapper, api } = await mountWithMetamodel(StarterPackagesSection)
    await wrapper.get('[data-testid="starter-import-oea-starter-bpmn"]').trigger('click')
    await vi.waitFor(() => expect(dialog('starter-confirm')).not.toBeNull())
    expect(document.body.textContent).toContain('Import BPMN 2.0 process modeling?')
    expect(document.body.textContent).toContain('The package creates 31 entity types and 2 properties.')
    await click('starter-cancel')
    expect(api.requests.filter((request) => request.method === 'POST')).toHaveLength(0)
    expect(wrapper.find('[data-testid="starter-import-oea-starter-bpmn"]').exists()).toBe(true)
  })

  it('imports after the confirmation, shows the number created and lists the package as imported', async () => {
    const { wrapper, api } = await mountWithMetamodel(StarterPackagesSection)
    await wrapper.get('[data-testid="starter-import-oea-starter-bpmn"]').trigger('click')
    await click('starter-confirm-import')
    await flushPromises()
    const post = api.requests.find((request) => request.method === 'POST')!
    expect(post.path).toBe('/api/v1/metamodel/starter-packages/oea-starter-bpmn/import')
    expect(post.csrf).toBeDefined()
    expect(wrapper.get('[data-testid="starter-result"]').text()).toContain('BPMN 2.0 process modeling imported: 33 elements created.')
    expect(wrapper.find('[data-testid="starter-import-oea-starter-bpmn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="starter-package-oea-starter-bpmn"] [data-testid="starter-imported-badge"]').exists()).toBe(true)
    // the metamodel is read again after the import
    expect(api.requests.filter((request) => request.path.includes('/metamodel/metatypes')).length).toBeGreaterThan(1)
    expect(metamodel.loaded).toBe(true)
  })

  it('lists the conflicting names when the import is rejected and keeps "Import"', async () => {
    const { wrapper } = await mountWithMetamodel(StarterPackagesSection, {}, { starterConflicts: ['metatype Task', 'metatype Pool'] })
    await wrapper.get('[data-testid="starter-import-oea-starter-bpmn"]').trigger('click')
    await click('starter-confirm-import')
    await flushPromises()
    expect(wrapper.get('[data-testid="starter-failure"]').text()).toBe('The package conflicts with existing names: metatype Task, metatype Pool. Nothing was imported.')
    expect(wrapper.find('[data-testid="starter-result"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="starter-import-oea-starter-bpmn"]').exists()).toBe(true)
  })

  it('stays available while the metamodel is locked', async () => {
    const { wrapper } = await mountWithMetamodel(StarterPackagesSection, {}, { locked: true })
    expect(wrapper.get('[data-testid="starter-import-oea-starter-bpmn"]').attributes('disabled')).toBeUndefined()
  })

  it('shows a notice when the packages cannot be read', async () => {
    const { wrapper } = await mountWithMetamodel(StarterPackagesSection, {}, { failListing: true })
    expect(wrapper.find('[data-testid="starter-load-failed"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="starter-none"]').exists()).toBe(false)
  })
})
