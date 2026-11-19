import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DiagramForm from '../components/diagram/DiagramForm.vue'
import { session } from '../session/store'
import { ME_WITHOUT_ROLE } from '../test/catalogs'
import { deletedDiagram, diagram, diagramApi, DIAGRAMS, ME_DIAGRAM_READER, ME_DIAGRAM_RESTORER, ME_DIAGRAM_WRITER, VIEWPOINT_BPMN } from '../test/diagrams'
import type { RecordedRequest } from '../test/fetch'
import { testRouter } from '../test/router'
import DiagramsPage from './DiagramsPage.vue'

const mounted: { unmount: () => void }[] = []

async function mountPage(me: unknown, options: { diagrams?: typeof DIAGRAMS; deleted?: ReturnType<typeof deletedDiagram>[]; pageSize?: number } = {}) {
  const router = testRouter()
  await router.push('/diagrams')
  const recorded: RecordedRequest[] = []
  const fetchFn = diagramApi({ me, recorded, ...options })
  const wrapper = mount(DiagramsPage, { props: { fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router, fetchFn, recorded }
}

const names = (wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper']) => wrapper.findAll('[data-testid="diagram-link"]').map((link) => link.find('span').text())

// spec diagram/diagrams "Diagrams page" (SCR-067)
describe('DiagramsPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('lists the diagrams by name with viewpoint, notation and link', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER)
    expect(names(wrapper)).toEqual(['Hire to Retire', 'Order to Cash', 'Procure to Pay'])
    const items = wrapper.findAll('[data-testid="diagram-item"]')
    expect(items[1]!.text()).toContain('Process Model (BPMN 2.0)')
    expect(items[2]!.get('[data-testid="diagram-notation"]').text()).toBe('archimate3')
    expect(items[1]!.get('[data-testid="diagram-link"]').attributes('href')).toBe('/diagrams/a1000000-0000-4000-8000-000000000001')
  })

  it('filters by name and loads the next page with the cursor', async () => {
    vi.useFakeTimers()
    try {
      const many = Array.from({ length: 3 }, (_, index) => diagram({ id: `a1000000-0000-4000-8000-00000000010${index}`, name: `Paged ${index}` }))
      const { wrapper, recorded } = await mountPage(ME_DIAGRAM_WRITER, { diagrams: many, pageSize: 2 })
      vi.useRealTimers()
      expect(names(wrapper)).toEqual(['Paged 0', 'Paged 1'])
      await wrapper.get('[data-testid="diagrams-load-more"]').trigger('click')
      await flushPromises()
      expect(names(wrapper)).toEqual(['Paged 0', 'Paged 1', 'Paged 2'])
      expect(wrapper.find('[data-testid="diagrams-load-more"]').exists()).toBe(false)
      expect(recorded.at(-1)!.path).toContain('cursor=cursor-1')
      expect(recorded.at(-1)!.path).toContain('page=1')
    } finally {
      vi.useRealTimers()
    }
  })

  it('filters the list by the name part', async () => {
    const { wrapper, recorded } = await mountPage(ME_DIAGRAM_WRITER)
    await wrapper.get('[data-testid="diagram-filter"]').setValue('to p')
    await vi.waitFor(() => expect(names(wrapper)).toEqual(['Procure to Pay']))
    expect(recorded.at(-1)!.path).toContain('q=to+p')
  })

  it('shows "No diagrams yet." with "Create diagram" for a writer', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER, { diagrams: [] })
    expect(wrapper.get('[data-testid="diagrams-empty"]').text()).toContain('No diagrams yet.')
    expect(wrapper.find('[data-testid="new-diagram"]').exists()).toBe(true)
  })

  it('offers a reader no write actions', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_READER)
    expect(wrapper.findAll('[data-testid="diagram-item"]')).toHaveLength(3)
    for (const id of ['new-diagram', 'diagram-rename', 'diagram-delete', 'show-deleted']) {
      expect(wrapper.find(`[data-testid="${id}"]`).exists()).toBe(false)
    }
  })

  it('refuses a person without entity:read', async () => {
    const { wrapper } = await mountPage(ME_WITHOUT_ROLE)
    expect(wrapper.find('[data-testid="no-diagram-access"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="diagram-list"]').exists()).toBe(false)
  })

  it('creates a diagram with a viewpoint and opens its page', async () => {
    const { wrapper, router, fetchFn } = await mountPage(ME_DIAGRAM_WRITER)
    await wrapper.get('[data-testid="new-diagram"]').trigger('click')
    await flushPromises()
    const body = document.body
    const name = body.querySelector<HTMLInputElement>('[data-testid="diagram-name"]')!
    name.value = 'Lead to Order'
    name.dispatchEvent(new Event('input'))
    // no viewpoint chosen: the form names the field and sends nothing
    body.querySelector<HTMLButtonElement>('[data-testid="diagram-submit"]')!.click()
    await flushPromises()
    expect(body.textContent).toContain('Choose a viewpoint.')
    expect(fetchFn.state.diagrams).toHaveLength(3)
    // with a viewpoint the diagram is created and its page opens
    wrapper.findComponent(DiagramForm).findComponent({ name: 'Select' }).vm.$emit('update:modelValue', VIEWPOINT_BPMN)
    await flushPromises()
    body.querySelector<HTMLButtonElement>('[data-testid="diagram-submit"]')!.click()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toMatch(/^\/diagrams\/a1000000/))
    expect(fetchFn.state.diagrams.find((entry) => entry.name === 'Lead to Order')?.viewpoint.notation).toBe('bpmn2')
  })

  it('names a taken name in the form', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER)
    await wrapper.get('[data-testid="diagram-rename"]').trigger('click')
    await flushPromises()
    const name = document.body.querySelector<HTMLInputElement>('[data-testid="diagram-name"]')!
    name.value = 'order to cash'
    name.dispatchEvent(new Event('input'))
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-submit"]')!.click()
    await flushPromises()
    expect(document.body.textContent).toContain('A diagram with this name already exists.')
  })

  it('renames a diagram with its version', async () => {
    const { wrapper, fetchFn } = await mountPage(ME_DIAGRAM_WRITER)
    await wrapper.findAll('[data-testid="diagram-rename"]')[1]!.trigger('click')
    await flushPromises()
    const name = document.body.querySelector<HTMLInputElement>('[data-testid="diagram-name"]')!
    expect(name.value).toBe('Order to Cash')
    name.value = 'Order to Cash (2026)'
    name.dispatchEvent(new Event('input'))
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-submit"]')!.click()
    await vi.waitFor(() => expect(fetchFn.state.diagrams.map((entry) => entry.name)).toContain('Order to Cash (2026)'))
    await vi.waitFor(() => expect(names(wrapper)).toContain('Order to Cash (2026)'))
  })

  it('deletes a diagram after the confirmation, and keeps it on cancel', async () => {
    const { wrapper, fetchFn } = await mountPage(ME_DIAGRAM_WRITER)
    await wrapper.findAll('[data-testid="diagram-delete"]')[0]!.trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-delete-cancel"]')!.click()
    await flushPromises()
    expect(fetchFn.state.diagrams).toHaveLength(3)
    await wrapper.findAll('[data-testid="diagram-delete"]')[0]!.trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-delete-confirm"]')!.click()
    await vi.waitFor(() => expect(fetchFn.state.diagrams).toHaveLength(2))
    await vi.waitFor(() => expect(names(wrapper)).toEqual(['Order to Cash', 'Procure to Pay']))
  })

  it('shows the deleted diagrams with "Restore" for entity:restore and restores one', async () => {
    const { wrapper, fetchFn } = await mountPage(ME_DIAGRAM_RESTORER, { deleted: [deletedDiagram()] })
    await wrapper.get('[data-testid="show-deleted"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('[data-testid="deleted-item"]')).toHaveLength(1))
    const row = wrapper.get('[data-testid="deleted-item"]')
    expect(row.text()).toContain('Lead to Order')
    expect(row.get('[data-testid="deleted-by"]').text()).toContain('Deleted by Kurt')
    await row.get('[data-testid="diagram-restore"]').trigger('click')
    await vi.waitFor(() => expect(fetchFn.state.deleted).toHaveLength(0))
    await vi.waitFor(() => expect(wrapper.find('[data-testid="diagrams-empty"]').exists()).toBe(true))
    expect(fetchFn.state.diagrams.map((entry) => entry.name)).toContain('Lead to Order')
  })

  it('names a taken name when a restore is refused', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_RESTORER, { deleted: [deletedDiagram({ name: 'Order to Cash' })] })
    await wrapper.get('[data-testid="show-deleted"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('[data-testid="deleted-item"]')).toHaveLength(1))
    await wrapper.get('[data-testid="diagram-restore"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.get('[data-testid="restore-failure"]').text()).toContain('another diagram has this name'))
  })

  it('leaves out "Show deleted" without entity:restore', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER)
    expect(wrapper.find('[data-testid="show-deleted"]').exists()).toBe(false)
  })

  it('uses the viewpoint of the contract in the list', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER, { diagrams: [diagram({ viewpoint: { id: VIEWPOINT_BPMN, name: 'Process Model (BPMN 2.0)', notation: 'bpmn2' } })] })
    expect(wrapper.get('[data-testid="diagram-notation"]').text()).toBe('bpmn2')
  })
})
