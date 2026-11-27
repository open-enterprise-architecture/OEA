import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import DiagramCanvas from '../components/diagram/DiagramCanvas.vue'
import PlaceInNavigation from '../components/PlaceInNavigation.vue'
import { session } from '../session/store'
import { diagram, diagramApi, ME_DIAGRAM_MANAGER, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER, shape, VIEWPOINT_INTEGRATION, viewpointConfigs, type DiagramApiOptions } from '../test/diagrams'
import DiagramPage from './DiagramPage.vue'

const ID = diagram().id

const mounted: { unmount: () => void }[] = []

async function mountPage(me: unknown, id = ID, options: Partial<DiagramApiOptions> = {}) {
  const fetchFn = diagramApi({ me, diagrams: [diagram({ description: 'From order to payment.' })], shapes: [shape()], ...options })
  // the page runs in a router view, as in the application (its guard for unsaved changes needs the route)
  const placeholder = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/diagrams', component: placeholder },
      { path: '/diagrams/:id', component: DiagramPage, props: (route) => ({ id: route.params.id, fetchFn }) },
    ],
  })
  await router.push(`/diagrams/${id}`)
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] }, attachTo: document.body })
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

  it('shows name, description, viewpoint with its notation and the canvas with the shapes', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER)
    expect(wrapper.get('[data-testid="diagram-title"]').text()).toBe('Order to Cash')
    expect(wrapper.get('[data-testid="diagram-description-text"]').text()).toBe('From order to payment.')
    expect(wrapper.get('[data-testid="diagram-viewpoint-name"]').text()).toContain('Process Model (BPMN 2.0)')
    expect(wrapper.get('[data-testid="diagram-viewpoint-name"]').text()).toContain('bpmn2')
    expect(wrapper.find('[data-testid="diagram-canvas"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="shape-d1000000-0000-4000-8000-000000000001"]').text()).toContain('SAP ERP')
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

  // spec diagram/diagram-viewpoint-change "Viewpoint changed": the page shows the new viewpoint, the canvas its conflicts
  it('shows the new viewpoint and the conflicts after "Change viewpoint"', async () => {
    const { wrapper, fetchFn } = await mountPage(ME_DIAGRAM_WRITER, ID, { viewpoints: viewpointConfigs(), shapes: [shape(), shape({ id: 'd1000000-0000-4000-8000-000000000002', entity: { id: 14, name: 'Invoice Approval', metatypeId: 'c1000000-0000-4000-8000-000000000002', childCount: 0, parentEntityId: null } })] })
    expect(wrapper.find('[data-testid="conflicts-panel"]').exists()).toBe(false)
    await wrapper.get('[data-testid="canvas-change-viewpoint"]').trigger('click')
    await vi.waitFor(() => expect(document.body.querySelector(`[data-testid="viewpoint-change-choice"] [value="${VIEWPOINT_INTEGRATION}"]`)).not.toBeNull())
    ;(document.body.querySelector(`[data-testid="viewpoint-change-choice"] [value="${VIEWPOINT_INTEGRATION}"]`) as HTMLElement).click()
    await flushPromises()
    ;(document.body.querySelector('[data-testid="viewpoint-change-submit"]') as HTMLElement).click()
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.get('[data-testid="diagram-viewpoint-name"]').text()).toContain('Integration View'))
    expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (1)')
    expect(fetchFn.state.diagrams[0]!.version).toBe(2)
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

  it('shows a reader the canvas read-only', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_READER)
    expect(wrapper.findComponent(DiagramCanvas).props('editable')).toBe(false)
    expect(wrapper.find('[data-testid="canvas-save"]').exists()).toBe(false)
  })

  it('does not show the canvas of a principal without entity:read', async () => {
    const { wrapper } = await mountPage({ ...ME_DIAGRAM_READER, permissions: [] })
    expect(wrapper.find('[data-testid="diagram-canvas"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="no-diagram-access"]').exists()).toBe(true)
  })

  it('saves the layout and uses the new version for the next save', async () => {
    const { wrapper, fetchFn } = await mountPage(ME_DIAGRAM_WRITER)
    const node = wrapper.get('[data-testid="shape-d1000000-0000-4000-8000-000000000001"]').element.closest('.vue-flow__node')!
    node.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(fetchFn.state.diagrams[0]!.version).toBe(2)
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(fetchFn.state.diagrams[0]!.version).toBe(3)
    expect(fetchFn.state.saves).toHaveLength(2)
  })

  it('reloads the diagram after a conflict and drops the changes', async () => {
    const { wrapper, fetchFn } = await mountPage(ME_DIAGRAM_WRITER)
    fetchFn.state.diagrams[0]!.version += 1
    const node = wrapper.get('[data-testid="shape-d1000000-0000-4000-8000-000000000001"]').element.closest('.vue-flow__node')!
    node.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="canvas-conflict-reload"]')!.click()
    await vi.waitFor(() => expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false))
    // the next save uses the version that was read again
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(fetchFn.state.diagrams[0]!.version).toBe(3)
  })

  // spec diagram/diagram-canvas "Unsaved changes"
  it('asks before leaving the page with unsaved changes and stays when asked to', async () => {
    const { wrapper, router } = await mountPage(ME_DIAGRAM_WRITER)
    const node = wrapper.get('[data-testid="shape-d1000000-0000-4000-8000-000000000001"]').element.closest('.vue-flow__node')!
    node.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    const leaving = router.push('/diagrams')
    await vi.waitFor(() => expect(document.body.querySelector('[data-testid="diagram-leave"]')).not.toBeNull())
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-leave-stay"]')!.click()
    await leaving
    expect(router.currentRoute.value.path).toBe(`/diagrams/${ID}`)
  })

  it('leaves the page with unsaved changes when asked to discard them', async () => {
    const { wrapper, router } = await mountPage(ME_DIAGRAM_WRITER)
    const node = wrapper.get('[data-testid="shape-d1000000-0000-4000-8000-000000000001"]').element.closest('.vue-flow__node')!
    node.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    const leaving = router.push('/diagrams')
    await vi.waitFor(() => expect(document.body.querySelector('[data-testid="diagram-leave"]')).not.toBeNull())
    document.body.querySelector<HTMLButtonElement>('[data-testid="diagram-leave-discard"]')!.click()
    await leaving
    expect(router.currentRoute.value.path).toBe('/diagrams')
  })

  it('leaves the page without asking when nothing changed or the changes are saved', async () => {
    const { wrapper, router } = await mountPage(ME_DIAGRAM_WRITER)
    const node = wrapper.get('[data-testid="shape-d1000000-0000-4000-8000-000000000001"]').element.closest('.vue-flow__node')!
    node.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    await router.push('/diagrams')
    expect(document.body.querySelector('[data-testid="diagram-leave"]')).toBeNull()
    expect(router.currentRoute.value.path).toBe('/diagrams')
  })

  it('warns the browser before it closes the page with unsaved changes', async () => {
    const { wrapper } = await mountPage(ME_DIAGRAM_WRITER)
    const clean = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(clean)
    expect(clean.defaultPrevented).toBe(false)
    const node = wrapper.get('[data-testid="shape-d1000000-0000-4000-8000-000000000001"]').element.closest('.vue-flow__node')!
    node.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight' })
    const dirty = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirty)
    expect(dirty.defaultPrevented).toBe(true)
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
