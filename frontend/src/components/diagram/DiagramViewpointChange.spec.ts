import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DiagramEdge, DiagramShape } from '../../api/diagramView'
import { session } from '../../session/store'
import {
  diagramApi,
  diagramView,
  edge,
  integrationViewpoint,
  METATYPE_COMPONENT,
  METATYPE_PROCESS,
  ME_DIAGRAM_READER,
  ME_DIAGRAM_WRITER,
  shape,
  VIEWPOINT_INTEGRATION,
  viewpointConfig,
  viewpointConfigs,
  type DiagramApiOptions,
} from '../../test/diagrams'
import { testRouter } from '../../test/router'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityShape from './EntityShape.vue'

const ERP = 'd1000000-0000-4000-8000-000000000001'
const APPROVAL = 'd1000000-0000-4000-8000-000000000003'
const REVIEW = 'd1000000-0000-4000-8000-000000000004'
const EDGE = 'f1000000-0000-4000-8000-000000000001'
const FULL_VIEWPOINT = 'b1000000-0000-4000-8000-000000000009'

const process = (id: number, name: string) => ({ id, name, metatypeId: METATYPE_PROCESS, childCount: 0, parentEntityId: null })

/** "SAP ERP" (component) and the processes "Invoice Approval" and "Review", with a "Flow" from the component to the first process. */
function layout(conflict: boolean): { shapes: DiagramShape[]; edges: DiagramEdge[] } {
  return {
    shapes: [
      shape({ id: ERP, x: 100, y: 50 }),
      shape({ id: APPROVAL, entity: process(14, 'Invoice Approval'), x: 300, y: 50, width: 90, height: 30, conflict }),
      shape({ id: REVIEW, entity: process(15, 'Review'), x: 300, y: 200, width: 90, height: 30, conflict }),
    ],
    edges: [edge({ id: EDGE, sourceShapeId: ERP, targetShapeId: APPROVAL, connection: { id: 21, name: 'SAP ERP → Invoice Approval', metatypeId: 'c1000000-0000-4000-8000-000000000011' }, conflict })],
  }
}

const mounted: VueWrapper[] = []

async function mountCanvas(conflict = false, options: Partial<DiagramApiOptions> = {}, editable = true) {
  const { shapes, edges } = layout(conflict)
  const fetchFn = diagramApi({ me: editable ? ME_DIAGRAM_WRITER : ME_DIAGRAM_READER, shapes, edges, ...options })
  const view = diagramView(shapes, {}, edges)
  const wrapper = mount(DiagramCanvas, {
    props: { view, version: view.diagram.version, editable, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, fetchFn, view }
}

const dialog = (testId: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)

async function click(testId: string) {
  await vi.waitFor(() => expect(dialog(testId)).not.toBeNull())
  dialog(testId)!.click()
  await flushPromises()
}

async function choose(viewpointId: string) {
  await vi.waitFor(() => expect(dialog('viewpoint-change-choice')?.querySelector(`[value="${viewpointId}"]`)).not.toBeNull())
  ;(dialog('viewpoint-change-choice')!.querySelector(`[value="${viewpointId}"]`) as HTMLElement).click()
  await flushPromises()
}

async function openDialog(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="canvas-change-viewpoint"]').trigger('click')
  await flushPromises()
}

async function save(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="canvas-save"]').trigger('click')
  await flushPromises()
}

const shapeNode = (wrapper: VueWrapper, id: string) => wrapper.findAllComponents(EntityShape).find((node) => node.props('id') === id)!

/** The viewpoint "Full View" allows every shown type. */
const FULL = { ...viewpointConfig(), id: FULL_VIEWPOINT, name: 'Full View' }

// spec diagram/diagram-viewpoint-change "Viewpoint change in the editor", "Conflicts of a diagram" and "Saving only without
// conflicts" (SCR-021; design D5 of us-074-diagram-viewpoint-change)
describe('DiagramCanvas viewpoint change', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('the dialog "Change viewpoint"', () => {
    it('lists the viewpoints and names the shapes and edges that become conflicts before the change', async () => {
      const { wrapper } = await mountCanvas(false, { viewpoints: viewpointConfigs() })
      await openDialog(wrapper)
      await choose(VIEWPOINT_INTEGRATION)
      expect(dialog('viewpoint-change-choice')!.textContent).toContain('Process Model (BPMN 2.0)')
      expect(dialog('viewpoint-change-choice')!.textContent).toContain('Integration View')
      expect(dialog('viewpoint-change-count')!.textContent).toContain('2 shapes become conflicts.')
      expect(dialog('viewpoint-change-count')!.textContent).toContain('1 edge becomes a conflict.')
      const names = [...dialog('viewpoint-change-names')!.querySelectorAll('li')].map((entry) => entry.textContent)
      expect(names).toEqual(['Invoice Approval', 'Review', 'SAP ERP → Invoice Approval'])
    })

    it('says that no conflicts arise when the viewpoint allows every shown type', async () => {
      const { wrapper } = await mountCanvas(false, { viewpoints: [...viewpointConfigs(), FULL] })
      await openDialog(wrapper)
      await choose(FULL_VIEWPOINT)
      expect(dialog('viewpoint-change-none')!.textContent).toContain('No conflicts arise.')
      expect(dialog('viewpoint-change-count')).toBeNull()
    })

    it('needs another viewpoint to change', async () => {
      const { wrapper } = await mountCanvas(false, { viewpoints: viewpointConfigs() })
      await openDialog(wrapper)
      await vi.waitFor(() => expect(dialog('viewpoint-change-choice')!.querySelector('[value]')).not.toBeNull())
      expect(dialog('viewpoint-change-submit')!.hasAttribute('disabled')).toBe(true)
      expect(dialog('viewpoint-change-preview')).toBeNull()
    })

    it('changes the viewpoint with the version of the diagram and hands the reloaded view to the page', async () => {
      const recorded: { path: string; init?: RequestInit }[] = []
      const { wrapper } = await mountCanvas(false, { viewpoints: viewpointConfigs(), recorded })
      await openDialog(wrapper)
      await choose(VIEWPOINT_INTEGRATION)
      await click('viewpoint-change-submit')
      const put = recorded.find((request) => request.init?.method === 'PUT' && request.path.endsWith('/viewpoint'))!
      expect((put.init!.headers as Record<string, string>)['If-Match']).toBe('"1"')
      expect(JSON.parse(put.init!.body as string)).toEqual({ viewpointId: VIEWPOINT_INTEGRATION })
      const next = wrapper.emitted('saved')!.at(-1)![0] as { viewpoint: { id: string }; diagram: { version: number; viewpoint: { name: string } }; conflictCount: number; shapes: { conflict: boolean }[] }
      expect(next.viewpoint.id).toBe(VIEWPOINT_INTEGRATION)
      expect(next.diagram.version).toBe(2)
      expect(next.diagram.viewpoint.name).toBe('Integration View')
      expect(next.conflictCount).toBe(3)
      expect(next.shapes.filter((entry) => entry.conflict)).toHaveLength(2)
      expect(dialog('viewpoint-change-form')).toBeNull()
    })

    it('shows the dialog of a change meanwhile for a stale version, and reloads on request', async () => {
      const { wrapper, fetchFn } = await mountCanvas(false, { viewpoints: viewpointConfigs() })
      await openDialog(wrapper)
      await choose(VIEWPOINT_INTEGRATION)
      fetchFn.state.diagrams.find((entry) => entry.id === wrapper.props('view').diagram.id)!.version = 5
      await click('viewpoint-change-submit')
      expect(dialog('viewpoint-change-stale')).not.toBeNull()
      expect(wrapper.emitted('saved')).toBeUndefined()
      await click('viewpoint-change-reload')
      expect(wrapper.emitted('reload')).toHaveLength(1)
    })

    it('explains a viewpoint that no longer exists', async () => {
      const viewpoints = [...viewpointConfigs(), { ...integrationViewpoint(), id: 'b1000000-0000-4000-8000-0000000000ff', name: 'Gone View' }]
      const { wrapper } = await mountCanvas(false, { viewpoints })
      await openDialog(wrapper)
      await choose('b1000000-0000-4000-8000-0000000000ff')
      // deleted meanwhile
      viewpoints.pop()
      await click('viewpoint-change-submit')
      expect(dialog('viewpoint-change-failure')!.textContent).toContain('The viewpoint does not exist.')
    })

    it('asks to save or discard unsaved changes first', async () => {
      const { wrapper } = await mountCanvas()
      expect(wrapper.find('[data-testid="canvas-change-viewpoint-hint"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="canvas-change-viewpoint"]').attributes('disabled')).toBeUndefined()
      await wrapper.get('[data-testid="canvas-palette"] button').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="shape-name-input"]').setValue('New element')
      await wrapper.get('[data-testid="shape-name-input"]').trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(wrapper.get('[data-testid="canvas-change-viewpoint"]').attributes('disabled')).toBeDefined()
      expect(wrapper.get('[data-testid="canvas-change-viewpoint-hint"]').text()).toBe('Save or discard your changes first.')
    })

    it('is offered only to those who may write', async () => {
      const { wrapper } = await mountCanvas(true, {}, false)
      expect(wrapper.find('[data-testid="canvas-change-viewpoint"]').exists()).toBe(false)
    })
  })

  describe('the marks of conflicts', () => {
    it('marks shapes and edges with the text and not only with a color', async () => {
      const { wrapper } = await mountCanvas(true)
      expect(shapeNode(wrapper, ERP).props('data').conflict).toBe(false)
      expect(shapeNode(wrapper, APPROVAL).props('data').conflict).toBe(true)
      expect(wrapper.get(`[data-testid="shape-${APPROVAL}"]`).attributes('data-conflict')).toBe('true')
      expect(wrapper.get(`[data-testid="shape-${APPROVAL}"] [data-testid="shape-conflict"]`).text()).toBe('Conflict: type not allowed in this viewpoint')
      expect(wrapper.find(`[data-testid="shape-${ERP}"] [data-testid="shape-conflict"]`).exists()).toBe(false)
      expect(wrapper.get(`[data-testid="edge-${EDGE}"]`).attributes('data-conflict')).toBe('true')
      expect(wrapper.get(`[data-testid="edge-${EDGE}"] [data-testid="edge-label"]`).text()).toContain('conflict')
      expect(wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).attributes('aria-label')).toContain('Conflict: type not allowed in this viewpoint')
    })

    it('shows no mark and no list for a diagram without conflicts', async () => {
      const { wrapper } = await mountCanvas(false)
      expect(wrapper.find('[data-testid="conflicts-panel"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="shape-conflict"]').exists()).toBe(false)
      expect(wrapper.find(`[data-testid="edge-${EDGE}"]`).attributes('data-conflict')).toBeUndefined()
    })

    it('lists the conflicts with the number, and a selection selects the shape', async () => {
      const { wrapper } = await mountCanvas(true)
      expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (3)')
      expect(wrapper.findAll('[data-testid^="conflict-item-"]').map((entry) => entry.attributes('data-testid'))).toEqual([`conflict-item-${APPROVAL}`, `conflict-item-${REVIEW}`, `conflict-item-${EDGE}`])
      await wrapper.get(`[data-testid="conflict-select-${REVIEW}"]`).trigger('click')
      expect(shapeNode(wrapper, REVIEW).props('selected')).toBe(true)
    })

    it('shows a reader the list without actions', async () => {
      const { wrapper } = await mountCanvas(true, {}, false)
      expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (3)')
      expect(wrapper.findAll('[data-testid^="conflict-item-"]')).toHaveLength(3)
      expect(wrapper.find('[data-testid="conflicts-remove-all"]').exists()).toBe(false)
      expect(wrapper.find(`[data-testid="conflict-remove-${APPROVAL}"]`).exists()).toBe(false)
    })
  })

  describe('resolving conflicts', () => {
    it('removes one conflict from the diagram with its edges and keeps the others', async () => {
      const { wrapper, fetchFn } = await mountCanvas(true)
      await wrapper.get(`[data-testid="conflict-remove-${APPROVAL}"]`).trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (1)')
      expect(wrapper.find(`[data-testid="shape-${APPROVAL}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="edge-${EDGE}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="shape-${REVIEW}"]`).exists()).toBe(true)
      // one conflict is left: the save is refused and names it
      await save(wrapper)
      expect(fetchFn.state.saves).toHaveLength(0)
      expect(wrapper.get('[data-testid="canvas-conflicts-blocked"]').text()).toContain('still has conflicts')
      expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (1)')
    })

    it('removes an edge that is a conflict without its shapes', async () => {
      const { wrapper } = await mountCanvas(true)
      await wrapper.get(`[data-testid="conflict-remove-${EDGE}"]`).trigger('click')
      await flushPromises()
      expect(wrapper.find(`[data-testid="edge-${EDGE}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="shape-${APPROVAL}"]`).exists()).toBe(true)
      expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (2)')
    })

    it('removes all conflicts and then saves', async () => {
      const { wrapper, fetchFn } = await mountCanvas(true)
      await wrapper.get('[data-testid="conflicts-remove-all"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="conflicts-panel"]').exists()).toBe(false)
      expect(wrapper.findAllComponents(EntityShape).map((node) => node.props('data').name)).toEqual(['SAP ERP'])
      await save(wrapper)
      const request = fetchFn.state.saves.at(-1)!
      expect(request.shapes.map((entry) => entry.id)).toEqual([ERP])
      expect(request.edges ?? []).toHaveLength(0)
      expect(wrapper.find('[data-testid="canvas-conflicts-blocked"]').exists()).toBe(false)
      expect((wrapper.emitted('saved')!.at(-1)![0] as { conflictCount: number }).conflictCount).toBe(0)
    })

    it('asks before a container that is a conflict goes with its nested shapes, which are of an allowed type', async () => {
      const container = shape({ id: APPROVAL, entity: process(14, 'Invoice Approval'), x: 300, y: 50, width: 300, height: 200, conflict: true })
      const nested = shape({ id: REVIEW, entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, parentShapeId: APPROVAL, x: 310, y: 60, width: 90, height: 30 })
      const fetchFn = diagramApi({ me: ME_DIAGRAM_WRITER, shapes: [shape({ id: ERP }), container, nested] })
      const view = diagramView([shape({ id: ERP }), container, nested])
      const wrapper = mount(DiagramCanvas, { props: { view, version: 1, editable: true, fetchFn }, global: { plugins: [ui, testRouter()] }, attachTo: document.body })
      mounted.push(wrapper)
      await flushPromises()
      expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (1)')
      await wrapper.get(`[data-testid="conflict-remove-${APPROVAL}"]`).trigger('click')
      await flushPromises()
      await vi.waitFor(() => expect(dialog('remove-container-dialog')).not.toBeNull())
      expect(wrapper.find(`[data-testid="shape-${APPROVAL}"]`).exists()).toBe(true)
      await click('remove-container-confirm')
      expect(wrapper.find(`[data-testid="shape-${APPROVAL}"]`).exists()).toBe(false)
      expect(wrapper.find(`[data-testid="shape-${REVIEW}"]`).exists()).toBe(false)
      expect(wrapper.find('[data-testid="conflicts-panel"]').exists()).toBe(false)
      await save(wrapper)
      expect(fetchFn.state.saves.at(-1)!.shapes.map((entry) => entry.id)).toEqual([ERP])
    })

    it('names the shapes and edges a refused save reports, even when the view did not mark them', async () => {
      const { wrapper } = await mountCanvas(false, {
        rejectSave: { status: 409, type: 'diagram-conflicts', errors: [{ field: 'shapes[1]', code: 'DIAGRAM_CONFLICT', message: 'conflict' }, { field: 'edges[0]', code: 'DIAGRAM_CONFLICT', message: 'conflict' }] },
      })
      await wrapper.get('[data-testid="canvas-palette"] button').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="shape-name-input"]').setValue('New element')
      await wrapper.get('[data-testid="shape-name-input"]').trigger('keydown', { key: 'Enter' })
      await save(wrapper)
      expect(wrapper.find('[data-testid="canvas-conflicts-blocked"]').exists()).toBe(true)
      expect(wrapper.get('[data-testid="conflicts-title"]').text()).toBe('Conflicts (2)')
      expect(shapeNode(wrapper, APPROVAL).props('data').conflict).toBe(true)
      expect(wrapper.get(`[data-testid="edge-${EDGE}"]`).attributes('data-conflict')).toBe('true')
      expect(wrapper.find('[data-testid="canvas-rejected"]').exists()).toBe(false)
    })
  })
})
