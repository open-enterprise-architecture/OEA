import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PlaceInNavigation from '../components/PlaceInNavigation.vue'
import { session } from '../session/store'
import { diagram, diagramApi, ME_DIAGRAM_MANAGER, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER } from '../test/diagrams'
import { testRouter } from '../test/router'
import DiagramPage from './DiagramPage.vue'

const ID = diagram().id

const mounted: { unmount: () => void }[] = []

async function mountPage(me: unknown, id = ID) {
  const router = testRouter()
  await router.push(`/diagrams/${id}`)
  const fetchFn = diagramApi({ me, diagrams: [diagram({ description: 'From order to payment.' })] })
  const wrapper = mount(DiagramPage, { props: { id, fetchFn }, global: { plugins: [ui, router] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router, fetchFn }
}

// spec diagram/diagrams "Diagrams page": the page of a diagram shows name, description and viewpoint (SCR-067)
describe('DiagramPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('shows name, description, viewpoint with its notation and the notice that the canvas follows', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER)
    expect(wrapper.get('[data-testid="diagram-title"]').text()).toBe('Order to Cash')
    expect(wrapper.get('[data-testid="diagram-description-text"]').text()).toBe('From order to payment.')
    expect(wrapper.get('[data-testid="diagram-viewpoint-name"]').text()).toContain('Process Model (BPMN 2.0)')
    expect(wrapper.get('[data-testid="diagram-viewpoint-name"]').text()).toContain('bpmn2')
    expect(wrapper.find('[data-testid="diagram-canvas-notice"]').exists()).toBe(true)
  })

  it('offers "Rename" and "Delete" only for entity:write', async () => {
    const writer = await mountPage(ME_DIAGRAM_WRITER)
    expect(writer.wrapper.find('[data-testid="diagram-rename"]').exists()).toBe(true)
    expect(writer.wrapper.find('[data-testid="diagram-delete"]').exists()).toBe(true)
    session.me = undefined
    session.loaded = false
    const reader = await mountPage(ME_DIAGRAM_READER)
    expect(reader.wrapper.find('[data-testid="diagram-rename"]').exists()).toBe(false)
    expect(reader.wrapper.find('[data-testid="diagram-delete"]').exists()).toBe(false)
  })

  it('offers "Place in navigation" only with viewpoint:manage and entity:read', async () => {
    const writer = await mountPage(ME_DIAGRAM_WRITER)
    expect(writer.wrapper.findComponent(PlaceInNavigation).exists()).toBe(false)
    session.me = undefined
    session.loaded = false
    const manager = await mountPage(ME_DIAGRAM_MANAGER)
    expect(manager.wrapper.findComponent(PlaceInNavigation).props('placement')).toEqual({ itemType: 'diagram', referenceId: ID, name: 'Order to Cash' })
  })

  it('shows an unknown or deleted diagram as not found', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER, 'a1000000-0000-4000-8000-0000000000ff')
    expect(wrapper.find('[data-testid="diagram-not-found"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="diagram-title"]').exists()).toBe(false)
  })

  it('renames the diagram and shows the new name', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER)
    await wrapper.get('[data-testid="diagram-rename"]').trigger('click')
    await flushPromises()
    const name = document.body.querySelector<HTMLInputElement>('[data-testid="diagram-name"]')!
    name.value = 'Order to Cash (2026)'
    name.dispatchEvent(new Event('input'))
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-submit"]')!.click()
    await vi.waitFor(() => expect(wrapper.get('[data-testid="diagram-title"]').text()).toBe('Order to Cash (2026)'))
  })

  it('deletes the diagram and returns to the list', async () => {
    const { wrapper, router, fetchFn } = await mountPage(ME_DIAGRAM_WRITER)
    await wrapper.get('[data-testid="diagram-delete"]').trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-delete-confirm"]')!.click()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/diagrams'))
    expect(fetchFn.state.diagrams).toHaveLength(0)
    expect(fetchFn.state.deleted).toHaveLength(1)
  })
})
