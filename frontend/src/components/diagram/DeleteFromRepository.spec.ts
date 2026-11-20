import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { session } from '../../session/store'
import {
  diagramApi,
  diagramView,
  edge,
  ME_DIAGRAM_ARCHITECT,
  METATYPE_COMPONENT,
  shape,
  type DiagramApiOptions,
} from '../../test/diagrams'
import { testRouter } from '../../test/router'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityShape from './EntityShape.vue'

const ERP = 'd1000000-0000-4000-8000-000000000001'
const CRM = 'd1000000-0000-4000-8000-000000000002'
const OTHER = 'd1000000-0000-4000-8000-000000000003'
const EDGE = 'f1000000-0000-4000-8000-000000000001'

const mounted: VueWrapper[] = []

const SHAPES = () => [
  shape(),
  shape({ id: CRM, entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT }, x: 400, y: 50 }),
  shape({ id: OTHER, entity: { id: 14, name: 'Invoice Approval', metatypeId: METATYPE_COMPONENT }, x: 400, y: 300 }),
]

async function mountCanvas(canDelete: boolean, options: Partial<DiagramApiOptions> = {}) {
  const shapes = SHAPES()
  const edges = [edge()]
  const fetchFn = diagramApi({ me: ME_DIAGRAM_ARCHITECT, shapes, edges, ...options })
  const view = diagramView(shapes, {}, edges)
  const wrapper = mount(DiagramCanvas, {
    props: { view, version: view.diagram.version, editable: true, canDelete, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, fetchFn }
}

function names(wrapper: VueWrapper) {
  return wrapper.findAllComponents(EntityShape).map((node) => node.props('data').name)
}

async function select(wrapper: VueWrapper, id: string) {
  wrapper.get(`[data-testid="shape-${id}"]`).element.closest('.vue-flow__node')!.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
  await flushPromises()
}

async function menu(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id } })
  await flushPromises()
}

const body = (testid: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testid}"]`)

async function openDialog(wrapper: VueWrapper, id = ERP) {
  await menu(wrapper, id)
  await wrapper.get('[data-testid="canvas-menu-delete-repository"]').trigger('click')
  await vi.waitFor(() => expect(body('repository-delete-dialog')).not.toBeNull())
  // the preview is read before the deletion can be confirmed
  await vi.waitFor(() => expect(body('repository-delete-counts') ?? body('repository-delete-failure')).not.toBeNull())
}

// spec diagram/diagram-canvas "Delete from repository on the canvas" (REQ-146)
describe('Delete from repository', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  it('offers the entry in the context menu of a stored shape and of an edge to a holder of entity:delete', async () => {
    const { wrapper } = await mountCanvas(true)
    await menu(wrapper, ERP)
    expect(wrapper.find('[data-testid="canvas-menu-delete-repository"]').exists()).toBe(true)
    wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id: ERP } })
    await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('contextmenu', { clientX: 5, clientY: 5 })
    expect(wrapper.find('[data-testid="canvas-menu-delete-repository"]').exists()).toBe(true)
  })

  it('offers nothing without entity:delete, and nothing for a new, unsaved entity', async () => {
    const { wrapper } = await mountCanvas(false)
    await menu(wrapper, ERP)
    expect(wrapper.find('[data-testid="canvas-menu-delete-repository"]').exists()).toBe(false)
    await select(wrapper, ERP)
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'Delete', ctrlKey: true })
    expect(body('repository-delete-dialog')).toBeNull()
  })

  it('offers nothing for a new, unsaved entity', async () => {
    const { wrapper: allowed } = await mountCanvas(true)
    await allowed.get('[data-testid="palette-' + METATYPE_COMPONENT + '"]').trigger('click')
    const input = allowed.get('[data-testid="shape-name-input"]')
    await input.setValue('New one')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    await allowed.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'Delete', ctrlKey: true })
    expect(body('repository-delete-dialog')).toBeNull()
    await menu(allowed, allowed.findAllComponents(EntityShape).at(-1)!.props('data') ? allowed.findAll('.vue-flow__node').at(-1)!.attributes('data-id')! : '')
    expect(allowed.find('[data-testid="canvas-menu-delete-repository"]').exists()).toBe(false)
  })

  it('opens the confirmation with Ctrl+Delete and shows children, connections and active solutions', async () => {
    const solutions = [{ id: 's1', name: 'Finance Upgrade', deltaCount: 2 }]
    const { wrapper } = await mountCanvas(true, { deletion: { previews: { 12: { childCount: 3, connectionCount: 4, solutions } } } })
    await select(wrapper, ERP)
    await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'Delete', ctrlKey: true })
    await vi.waitFor(() => expect(body('repository-delete-counts')).not.toBeNull())
    expect(body('repository-delete-counts')!.textContent).toContain('3 children')
    expect(body('repository-delete-counts')!.textContent).toContain('4 connections')
    expect(body('repository-delete-solutions')!.textContent).toContain('Finance Upgrade: 2 changes')
  })

  it('deletes at once with the version of the preview and removes the shapes and edges, keeping other unsaved changes', async () => {
    const recorded: { path: string; init?: RequestInit }[] = []
    const { wrapper, fetchFn } = await mountCanvas(true, { recorded, deletion: { previews: { 12: { childCount: 0, connectionCount: 1, cascade: [21] } } } })
    // an unsaved move of another shape
    wrapper.findAllComponents(EntityShape).find((node) => node.props('data').name === 'Invoice Approval')!.props('data').resized({ x: 0, y: 0, width: 200, height: 80 })
    await flushPromises()
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    await openDialog(wrapper)
    body('repository-delete-confirm')!.click()
    await vi.waitFor(() => expect(names(wrapper)).toEqual(['Salesforce', 'Invoice Approval']))
    const deletion = recorded.find((entry) => entry.init?.method === 'DELETE')!
    expect(deletion.path).toBe('/api/v1/entities/12')
    expect((deletion.init!.headers as Record<string, string>)['If-Match']).toBe('"4"')
    expect(fetchFn.state.saves).toHaveLength(0)
    expect(wrapper.find(`[data-testid="edge-hit-${EDGE}"]`).exists()).toBe(false)
    // the move of the other shape stays unsaved and the removed shape is not part of the next save
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(fetchFn.state.saves.at(-1)!.shapes.map((entry) => entry.entityId)).toEqual([13, 14])
  })

  it('leaves no unsaved changes after a deletion on a clean canvas', async () => {
    const { wrapper } = await mountCanvas(true)
    await openDialog(wrapper)
    body('repository-delete-confirm')!.click()
    await vi.waitFor(() => expect(names(wrapper)).toHaveLength(2))
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
  })

  it('shows the message of a rejected preview and offers no deletion', async () => {
    const { wrapper } = await mountCanvas(true, { deletion: { rejectPreview: { status: 422 } } })
    await openDialog(wrapper)
    await vi.waitFor(() => expect(body('repository-delete-failure')).not.toBeNull())
    expect(body('repository-delete-failure')!.textContent).toContain('cannot be deleted here')
    expect(body('repository-delete-confirm')!.hasAttribute('disabled')).toBe(true)
  })

  it('says that the entity was changed on a 412 and reads the preview again', async () => {
    const recorded: { path: string; init?: RequestInit }[] = []
    const { wrapper } = await mountCanvas(true, { recorded, deletion: { rejectDelete: { status: 412, type: 'stale-version' } } })
    await openDialog(wrapper)
    await vi.waitFor(() => expect(body('repository-delete-confirm')!.hasAttribute('disabled')).toBe(false))
    body('repository-delete-confirm')!.click()
    await vi.waitFor(() => expect(body('repository-delete-failure')).not.toBeNull())
    expect(body('repository-delete-failure')!.textContent).toContain('was changed')
    expect(names(wrapper)).toHaveLength(3)
    const reads = () => recorded.filter((entry) => entry.path.endsWith('/deletion')).length
    expect(reads()).toBe(1)
    body('repository-delete-again')!.click()
    await vi.waitFor(() => expect(reads()).toBe(2))
  })

  it('shows 409 entity-blocked and 403 and keeps the shapes', async () => {
    const blocked = { entities: [{ entityId: 12, entityName: 'SAP ERP', issues: [] }] }
    const { wrapper } = await mountCanvas(true, { deletion: { rejectDelete: { status: 409, type: 'entity-blocked', extra: blocked } } })
    await openDialog(wrapper)
    await vi.waitFor(() => expect(body('repository-delete-confirm')!.hasAttribute('disabled')).toBe(false))
    body('repository-delete-confirm')!.click()
    await vi.waitFor(() => expect(body('repository-delete-failure')!.textContent).toContain('SAP ERP has open issues'))
    expect(names(wrapper)).toHaveLength(3)
  })

  it('shows the message of a 403', async () => {
    const { wrapper } = await mountCanvas(true, { deletion: { rejectDelete: { status: 403, type: 'forbidden' } } })
    await openDialog(wrapper)
    await vi.waitFor(() => expect(body('repository-delete-confirm')!.hasAttribute('disabled')).toBe(false))
    body('repository-delete-confirm')!.click()
    await vi.waitFor(() => expect(body('repository-delete-failure')!.textContent).toContain('You may not delete'))
    expect(names(wrapper)).toHaveLength(3)
  })
})
