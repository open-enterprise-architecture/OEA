import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CANVAS_LIMITS, type DiagramShape } from '../../api/diagramView'
import { session } from '../../session/store'
import {
  allowedConnections,
  diagramApi,
  diagramView,
  edge,
  METATYPE_ASSOCIATION,
  METATYPE_COMPONENT,
  METATYPE_FLOW,
  METATYPE_PROCESS,
  ME_DIAGRAM_READER,
  ME_DIAGRAM_WRITER,
  PROP_PROTOCOL,
  shape,
  type DiagramApiOptions,
} from '../../test/diagrams'
import { testRouter } from '../../test/router'
import ConnectionEdges from './ConnectionEdges.vue'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityShape from './EntityShape.vue'
import type { DiagramEdge } from '../../api/diagramView'

const ERP = 'd1000000-0000-4000-8000-000000000001'
const CRM = 'd1000000-0000-4000-8000-000000000002'
const APPROVAL = 'd1000000-0000-4000-8000-000000000003'
const REVIEW = 'd1000000-0000-4000-8000-000000000004'
const EDGE = 'f1000000-0000-4000-8000-000000000001'

const SHAPES: DiagramShape[] = [
  shape({ id: ERP, x: 100, y: 50 }),
  shape({ id: CRM, entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, x: 400, y: 50 }),
  shape({ id: APPROVAL, entity: { id: 14, name: 'Invoice Approval', metatypeId: METATYPE_PROCESS, childCount: 0, parentEntityId: null }, x: 250, y: 200, width: 90, height: 30 }),
  shape({ id: REVIEW, entity: { id: 15, name: 'Review', metatypeId: METATYPE_PROCESS, childCount: 0, parentEntityId: null }, x: 450, y: 200, width: 90, height: 30 }),
]

const mounted: VueWrapper[] = []

async function mountCanvas(options: Partial<DiagramApiOptions> = {}, edges: DiagramEdge[] = [edge()], editable = true, shapes: DiagramShape[] = SHAPES) {
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

function drawn(wrapper: VueWrapper) {
  return wrapper.findAll('[data-testid^="edge-hit-"]').map((hit) => hit.attributes('data-testid')!.replace('edge-hit-', ''))
}

function lastSave(fetchFn: ReturnType<typeof diagramApi>) {
  return fetchFn.state.saves.at(-1)!
}

async function connect(wrapper: VueWrapper, source: string, target: string) {
  wrapper.findComponent(VueFlow).vm.$emit('connect', { source, target })
  await flushPromises()
}

async function save(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="canvas-save"]').trigger('click')
  await flushPromises()
}

async function selectShape(wrapper: VueWrapper, id: string) {
  wrapper.get(`[data-testid="shape-${id}"]`).element.closest('.vue-flow__node')!.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
  await flushPromises()
}

async function pickEntity(wrapper: VueWrapper, id: number) {
  await wrapper.get('[data-testid="canvas-add-existing"]').trigger('click')
  await vi.waitFor(() => expect(document.body.querySelector(`[data-testid="add-existing-${id}"]`)).not.toBeNull())
  document.body.querySelector<HTMLButtonElement>(`[data-testid="add-existing-${id}"]`)!.click()
  await flushPromises()
}

// spec diagram/diagram-connections: edges, drawing with the type choice, bend points, the offer and "Add connected element" (SCR-021)
describe('DiagramCanvas connections', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('edges', () => {
    it('draws the edge with the name and the color of its connection type, an arrow and no mark when it is saved', async () => {
      const { wrapper } = await mountCanvas()
      expect(drawn(wrapper)).toEqual([EDGE])
      expect(wrapper.get('[data-testid="edge-label"]').text()).toBe('SAP ERP → Salesforce')
      expect(wrapper.get(`[data-testid="edge-${EDGE}"] path`).attributes('stroke')).toBe('#16a34a')
      expect(wrapper.find(`[data-testid="edge-${EDGE}"] polygon`).exists()).toBe(true)
      expect(wrapper.get(`[data-testid="edge-${EDGE}"]`).attributes('data-unsaved')).toBeUndefined()
      expect(wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).attributes('aria-label')).toBe('Connection SAP ERP → Salesforce')
      // the handles of a shape start a connection
      expect(wrapper.findAll('.vue-flow__handle').length).toBeGreaterThan(0)
    })

    it('draws a connection that has several edges, and one of a shape to itself', async () => {
      const second = edge({ id: 'f1000000-0000-4000-8000-000000000002', sourceShapeId: ERP, targetShapeId: ERP })
      const { wrapper } = await mountCanvas({}, [edge(), second])
      expect(drawn(wrapper)).toEqual([EDGE, second.id])
    })

    it('moves with its shapes and skips an edge whose shape is gone', async () => {
      const lost = edge({ id: 'f1000000-0000-4000-8000-000000000002', targetShapeId: 'd1000000-0000-4000-8000-0000000000ff' })
      const { wrapper } = await mountCanvas({}, [edge(), lost])
      expect(drawn(wrapper)).toEqual([EDGE])
    })

    it('marks an edge of an incomplete connection', async () => {
      const { wrapper } = await mountCanvas({}, [edge({ missingMandatory: ['Protocol'] })])
      expect(wrapper.get(`[data-testid="edge-${EDGE}"] title`).text()).toBe('Incomplete: Protocol')
    })
  })

  describe('drawing a connection', () => {
    it('says that no connection is allowed and draws no edge', async () => {
      const { wrapper } = await mountCanvas({}, [])
      await connect(wrapper, APPROVAL, REVIEW)
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toBe('No connection is allowed here.')
      expect(drawn(wrapper)).toEqual([])
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('uses the one allowed type without asking, selects the new edge and opens its details', async () => {
      const { wrapper, fetchFn } = await mountCanvas({}, [])
      await connect(wrapper, ERP, APPROVAL)
      expect(drawn(wrapper)).toHaveLength(1)
      expect(wrapper.find('[data-testid="connection-type-dialog"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      const id = drawn(wrapper)[0]!
      expect(wrapper.get(`[data-testid="edge-${id}"]`).attributes('data-unsaved')).toBe('true')
      expect(wrapper.get(`[data-testid="edge-${id}"]`).attributes('data-selected')).toBe('true')
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-title"]').text()).toBe('SAP ERP → Invoice Approval'))
      await save(wrapper)
      const request = lastSave(fetchFn)
      expect(request.newConnections).toEqual([{ key: expect.any(String), metatypeId: METATYPE_FLOW }])
      expect(request.edges).toEqual([
        { id, newConnectionKey: request.newConnections![0]!.key, sourceShapeId: ERP, targetShapeId: APPROVAL, points: [] },
      ])
      expect(fetchFn.state.edges[0]!.connection.name).toBe('SAP ERP → Invoice Approval')
    })

    it('asks for the type when several are allowed, and draws nothing without a choice', async () => {
      const { wrapper, fetchFn } = await mountCanvas({}, [])
      await connect(wrapper, ERP, CRM)
      expect(document.body.querySelector('[data-testid="connection-type-dialog"]')).not.toBeNull()
      expect(drawn(wrapper)).toEqual([])
      document.body.querySelector<HTMLButtonElement>('[data-testid="connection-type-cancel"]')!.click()
      await flushPromises()
      expect(drawn(wrapper)).toEqual([])
      await connect(wrapper, ERP, CRM)
      document.body.querySelector<HTMLButtonElement>(`[data-testid="connection-type-${METATYPE_ASSOCIATION}"]`)!.click()
      await flushPromises()
      expect(drawn(wrapper)).toHaveLength(1)
      await save(wrapper)
      expect(lastSave(fetchFn).newConnections).toEqual([{ key: expect.any(String), metatypeId: METATYPE_ASSOCIATION }])
    })

    it('draws a type that runs only from the target to the source with the shapes swapped', async () => {
      const { wrapper, fetchFn } = await mountCanvas({}, [])
      // "Flow" runs from a component to a process: dragged from the process, the edge runs from the component
      await connect(wrapper, APPROVAL, ERP)
      await save(wrapper)
      expect(lastSave(fetchFn).edges).toEqual([expect.objectContaining({ sourceShapeId: ERP, targetShapeId: APPROVAL })])
    })

    it('draws nothing for a reader, and nothing from a shape that is still being named', async () => {
      const reader = await mountCanvas({}, [], false)
      await connect(reader.wrapper, ERP, APPROVAL)
      expect(drawn(reader.wrapper)).toEqual([])
      expect(reader.wrapper.findAll('.vue-flow__handle')).toHaveLength(0)
    })

    it('shows a message when the allowed connections cannot be read', async () => {
      const { wrapper } = await mountCanvas({}, [])
      const failing = (async () => new Response('{}', { status: 500, headers: { 'Content-Type': 'application/problem+json' } })) as typeof fetch
      await wrapper.setProps({ fetchFn: failing })
      await connect(wrapper, ERP, APPROVAL)
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toBe('The allowed connections could not be loaded.')
    })

    it('accepts the 4000th edge and blocks the 4001st with a notice', async () => {
      // 3999 edges of shapes that are not there are not drawn, which keeps the spec fast; they count towards the limit
      const many = Array.from({ length: CANVAS_LIMITS.edges - 1 }, (_, index) => edge({ id: `f1000000-0000-4000-8000-${String(index + 100).padStart(12, '0')}`, sourceShapeId: 'gone', targetShapeId: 'gone' }))
      const { wrapper } = await mountCanvas({}, many)
      await connect(wrapper, ERP, APPROVAL)
      expect(drawn(wrapper)).toHaveLength(1)
      expect(wrapper.find('[data-testid="canvas-notice"]').exists()).toBe(false)
      await connect(wrapper, ERP, APPROVAL)
      expect(drawn(wrapper)).toHaveLength(1)
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toBe('A diagram holds at most 4000 edges.')
    })
  })

  describe('bend points', () => {
    async function selectEdge(wrapper: VueWrapper) {
      await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('click')
      await flushPromises()
    }

    it('adds a bend point with a double click, moves it by dragging and removes it with a double click', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await selectEdge(wrapper)
      expect(wrapper.find(`[data-testid="edge-bend-${EDGE}-0"]`).exists()).toBe(false)
      // 300 px is 225 pt
      await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('dblclick', { clientX: 400, clientY: 300 })
      expect(wrapper.find(`[data-testid="edge-bend-${EDGE}-0"]`).exists()).toBe(true)
      await wrapper.get(`[data-testid="edge-bend-${EDGE}-0"]`).trigger('pointerdown')
      window.dispatchEvent(new MouseEvent('pointermove', { clientX: 600, clientY: 360 }))
      window.dispatchEvent(new MouseEvent('pointerup'))
      await flushPromises()
      await save(wrapper)
      expect(lastSave(fetchFn).edges![0]!.points).toEqual([{ x: 450, y: 270 }])
      // the page passes the version of the save on
      await wrapper.setProps({ version: 2 })
      await selectEdge(wrapper)
      await wrapper.get(`[data-testid="edge-bend-${EDGE}-0"]`).trigger('dblclick')
      await save(wrapper)
      expect(lastSave(fetchFn).edges![0]!.points).toEqual([])
    })

    it('accepts the 50th bend point and blocks the 51st with a notice', async () => {
      const { wrapper } = await mountCanvas()
      const edges = wrapper.findComponent(ConnectionEdges)
      for (let index = 0; index < CANVAS_LIMITS.bendPoints; index += 1) {
        edges.vm.$emit('bendAdded', EDGE, { x: 200 + index, y: 100 })
      }
      await flushPromises()
      expect(wrapper.find('[data-testid="canvas-notice"]').exists()).toBe(false)
      expect(wrapper.findAll(`[data-testid^="edge-bend-${EDGE}-"]`)).toHaveLength(0)
      edges.vm.$emit('bendAdded', EDGE, { x: 999, y: 100 })
      await flushPromises()
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toBe('An edge has at most 50 bend points.')
      await selectEdge(wrapper)
      expect(wrapper.findAll(`[data-testid^="edge-bend-${EDGE}-"]`)).toHaveLength(CANVAS_LIMITS.bendPoints)
    })

    it('does not add or show bend points for a reader', async () => {
      const { wrapper } = await mountCanvas({}, [edge({ points: [{ x: 300, y: 100 }] })], false)
      await selectEdge(wrapper)
      await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('dblclick', { clientX: 400, clientY: 300 })
      expect(wrapper.findAll(`[data-testid^="edge-bend-${EDGE}-"]`)).toHaveLength(0)
    })
  })

  describe('selecting and removing an edge', () => {
    it('reaches an edge with the keyboard, shows its details and removes only the edge with Delete', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      const hit = wrapper.get(`[data-testid="edge-hit-${EDGE}"]`)
      expect(hit.attributes('tabindex')).toBe('0')
      await hit.trigger('focus')
      await flushPromises()
      expect(wrapper.get(`[data-testid="edge-${EDGE}"]`).attributes('data-selected')).toBe('true')
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-title"]').text()).toBe('SAP ERP → Salesforce'))
      await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'Delete' })
      expect(drawn(wrapper)).toEqual([])
      expect(wrapper.findAll('[data-testid^="shape-"]').length).toBeGreaterThan(0)
      await save(wrapper)
      expect(lastSave(fetchFn).edges).toEqual([])
      expect(lastSave(fetchFn).shapes).toHaveLength(SHAPES.length)
    })

    it('selects with Enter and removes with "Remove from diagram", also from the context menu of the edge', async () => {
      const { wrapper } = await mountCanvas()
      await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('keydown', { key: 'Enter' })
      expect(wrapper.get(`[data-testid="edge-${EDGE}"]`).attributes('data-selected')).toBe('true')
      await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('contextmenu', { clientX: 5, clientY: 5 })
      expect(wrapper.find('[data-testid="canvas-menu-details"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="canvas-menu-connected"]').exists()).toBe(false)
      await wrapper.get('[data-testid="canvas-menu-remove"]').trigger('click')
      expect(drawn(wrapper)).toEqual([])
    })

    it('removes the edges of a removed shape and keeps the others', async () => {
      const other = edge({ id: 'f1000000-0000-4000-8000-000000000002', sourceShapeId: APPROVAL, targetShapeId: REVIEW, connection: { id: 22, name: 'Approval to Review', metatypeId: METATYPE_FLOW } })
      const { wrapper, fetchFn } = await mountCanvas({}, [edge(), other])
      await selectShape(wrapper, CRM)
      await wrapper.get('[data-testid="canvas-remove"]').trigger('click')
      expect(drawn(wrapper)).toEqual([other.id])
      await save(wrapper)
      expect(lastSave(fetchFn).edges!.map((entry) => entry.id)).toEqual([other.id])
    })

    it('drops a new connection without an edge and discards the changes of a connection that no edge shows', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await connect(wrapper, ERP, APPROVAL)
      await selectShape(wrapper, APPROVAL)
      await wrapper.get('[data-testid="canvas-remove"]').trigger('click')
      expect(drawn(wrapper)).toEqual([EDGE])
      // a change of the connection of the saved edge is discarded when its edge goes
      await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('click')
      await vi.waitFor(() => expect(wrapper.find('[data-testid="details-description"]').exists()).toBe(true))
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-title"]').text()).toBe('SAP ERP → Salesforce'))
      const area = wrapper.get('[data-testid="details-description"]')
      await (area.element.tagName === 'TEXTAREA' ? area : area.get('textarea')).setValue('Changed')
      await flushPromises()
      await wrapper.get('[data-testid="canvas-remove"]').trigger('click')
      await save(wrapper)
      const request = lastSave(fetchFn)
      expect(request.newConnections).toEqual([])
      expect(request.entityUpdates).toBeUndefined()
      expect(request.edges).toEqual([])
    })
  })

  describe('details of a connection', () => {
    async function openDetails(wrapper: VueWrapper) {
      await wrapper.get(`[data-testid="edge-hit-${EDGE}"]`).trigger('click')
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-title"]').text()).toBe('SAP ERP → Salesforce'))
      await vi.waitFor(() => expect(wrapper.find('[data-testid="details-loading"]').exists()).toBe(false))
    }

    it('shows the connection with its metatype, version and values, editable for a writer', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await openDetails(wrapper)
      expect(wrapper.get('[data-testid="details-meta"]').text()).toContain('Flow')
      expect(wrapper.get('[data-testid="details-meta"]').text()).toContain('Version 2')
      expect((wrapper.get('[data-testid="details-name"] input, input[data-testid="details-name"]').element as HTMLInputElement).value).toBe('SAP ERP → Salesforce')
      const area = wrapper.get('[data-testid="details-description"]')
      await (area.element.tagName === 'TEXTAREA' ? area : area.get('textarea')).setValue('Orders and invoices')
      await flushPromises()
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      expect(wrapper.get(`[data-testid="edge-${EDGE}"]`).attributes('data-unsaved')).toBe('true')
      await save(wrapper)
      expect(lastSave(fetchFn).entityUpdates).toEqual([{ entityId: 21, expectedVersion: 2, description: 'Orders and invoices' }])
      expect(fetchFn.state.entities[21]!.version).toBe(3)
    })

    it('shows a reader the connection read-only', async () => {
      const { wrapper } = await mountCanvas({}, [edge()], false)
      await openDetails(wrapper)
      expect(wrapper.find('[data-testid="details-description"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="details-read-only"]').exists()).toBe(true)
    })

    it('marks the required value of a new connection, and saves the value entered', async () => {
      const { wrapper, fetchFn } = await mountCanvas({}, [])
      await connect(wrapper, ERP, CRM)
      document.body.querySelector<HTMLButtonElement>(`[data-testid="connection-type-${METATYPE_FLOW}"]`)!.click()
      await flushPromises()
      await vi.waitFor(() => expect(wrapper.find('[data-testid="details-missing"]').exists()).toBe(true))
      expect(wrapper.get('[data-testid="details-missing"]').text()).toBe('Required values missing: Protocol')
      const protocol = wrapper.get('[data-testid="details-field-Protocol"] input')
      await protocol.setValue('REST')
      await flushPromises()
      expect(wrapper.find('[data-testid="details-missing"]').exists()).toBe(false)
      await save(wrapper)
      expect(lastSave(fetchFn).newConnections![0]).toMatchObject({ metatypeId: METATYPE_FLOW, properties: [{ propertyDefId: PROP_PROTOCOL, value: 'REST' }] })
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('creates a new connection incomplete and marks its edge', async () => {
      const { wrapper } = await mountCanvas({}, [])
      await connect(wrapper, ERP, CRM)
      document.body.querySelector<HTMLButtonElement>(`[data-testid="connection-type-${METATYPE_FLOW}"]`)!.click()
      await flushPromises()
      await save(wrapper)
      const id = drawn(wrapper)[0]!
      expect(wrapper.get(`[data-testid="edge-${id}"] title`).text()).toBe('Incomplete: Protocol')
    })
  })

  describe('the offer of existing connections', () => {
    const OFFER = { connection: { id: 21, name: 'SAP ERP → Salesforce', metatypeId: METATYPE_FLOW }, sourceEntityId: 12, targetEntityId: 13 }
    const CRM_ONLY = [shape({ id: CRM, entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, x: 400, y: 50 })]

    it('offers the connections when the second end is placed and adds no edge by itself', async () => {
      const { wrapper, fetchFn } = await mountCanvas({ offers: [OFFER] }, [], true, CRM_ONLY)
      await pickEntity(wrapper, 12)
      await vi.waitFor(() => expect(wrapper.find('[data-testid="canvas-offer"]').exists()).toBe(true))
      expect(wrapper.get('[data-testid="canvas-offer-text"]').text()).toBe('1 connection available')
      expect(fetchFn.state.queries).toEqual([{ entityIds: [12], shownEntityIds: [13] }])
      expect(drawn(wrapper)).toEqual([])
    })

    it('shows all offered connections as unsaved edges between the placed and the shown shape', async () => {
      const { wrapper, fetchFn } = await mountCanvas({ offers: [OFFER, { ...OFFER, connection: { ...OFFER.connection, id: 22, name: 'Second' } }] }, [], true, CRM_ONLY)
      await pickEntity(wrapper, 12)
      await vi.waitFor(() => expect(wrapper.get('[data-testid="canvas-offer-text"]').text()).toBe('2 connections available'))
      await wrapper.get('[data-testid="offer-show-all"]').trigger('click')
      expect(drawn(wrapper)).toHaveLength(2)
      expect(wrapper.find('[data-testid="canvas-offer"]').exists()).toBe(false)
      expect(wrapper.findAll('[data-unsaved="true"]').length).toBeGreaterThanOrEqual(2)
      await save(wrapper)
      expect(lastSave(fetchFn).edges).toEqual([
        expect.objectContaining({ connectionId: 21, targetShapeId: CRM }),
        expect.objectContaining({ connectionId: 22, targetShapeId: CRM }),
      ])
      expect(lastSave(fetchFn).newConnections).toEqual([])
    })

    it('adds the chosen connections only with "Choose…"', async () => {
      const { wrapper } = await mountCanvas({ offers: [OFFER, { ...OFFER, connection: { ...OFFER.connection, id: 22, name: 'Second' } }] }, [], true, CRM_ONLY)
      await pickEntity(wrapper, 12)
      await vi.waitFor(() => expect(wrapper.find('[data-testid="offer-choose"]').exists()).toBe(true))
      await wrapper.get('[data-testid="offer-choose"]').trigger('click')
      await vi.waitFor(() => expect(document.body.querySelector('[data-testid="offer-dialog"]')).not.toBeNull())
      expect(document.body.querySelectorAll('[data-testid^="offer-2"]').length).toBeGreaterThanOrEqual(2)
      document.body.querySelector<HTMLElement>('[data-testid="offer-22"]')!.click()
      await flushPromises()
      document.body.querySelector<HTMLButtonElement>('[data-testid="offer-add"]')!.click()
      await flushPromises()
      expect(drawn(wrapper)).toHaveLength(1)
    })

    it('adds nothing when the offer is dismissed', async () => {
      const { wrapper } = await mountCanvas({ offers: [OFFER] }, [], true, CRM_ONLY)
      await pickEntity(wrapper, 12)
      await vi.waitFor(() => expect(wrapper.find('[data-testid="canvas-offer"]').exists()).toBe(true))
      await wrapper.get('[data-testid="offer-dismiss"]').trigger('click')
      expect(wrapper.find('[data-testid="canvas-offer"]').exists()).toBe(false)
      expect(drawn(wrapper)).toEqual([])
    })

    it('offers nothing for a new entity and nothing without a connection to the shown entities', async () => {
      const { wrapper, fetchFn } = await mountCanvas({ offers: [OFFER] }, [], true, CRM_ONLY)
      await wrapper.get(`[data-testid="palette-${METATYPE_COMPONENT}"]`).trigger('click')
      await wrapper.get('[data-testid="shape-name-input"]').setValue('New')
      await wrapper.get('[data-testid="shape-name-input"]').trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(fetchFn.state.queries).toEqual([])
      expect(wrapper.find('[data-testid="canvas-offer"]').exists()).toBe(false)
      await pickEntity(wrapper, 11)
      await flushPromises()
      expect(fetchFn.state.queries).toHaveLength(1)
      expect(wrapper.find('[data-testid="canvas-offer"]').exists()).toBe(false)
    })

    it('does not offer a connection that already has an edge between the shapes, and not twice', async () => {
      const twice = [OFFER, OFFER]
      const { wrapper } = await mountCanvas({ offers: twice }, [], true, CRM_ONLY)
      await pickEntity(wrapper, 12)
      await vi.waitFor(() => expect(wrapper.get('[data-testid="canvas-offer-text"]').text()).toBe('1 connection available'))
    })
  })

  describe('Add connected element', () => {
    const only = (connection: string, target: string) => (source: string, to: string) =>
      allowedConnections(source, to).options.some((option) => option.connectionMetatype.id === connection) && to === target
        ? { allowed: true, options: allowedConnections(source, to).options.filter((option) => option.connectionMetatype.id === connection) }
        : { allowed: false, options: [] }

    async function addConnected(wrapper: VueWrapper) {
      await selectShape(wrapper, ERP)
      await wrapper.get('[data-testid="canvas-add-connected"]').trigger('click')
      await vi.waitFor(() => expect(document.body.querySelector('[data-testid="connected-add"]:not([disabled])')).not.toBeNull())
      document.body.querySelector<HTMLButtonElement>('[data-testid="connected-add"]')!.click()
      await flushPromises()
    }

    async function name(wrapper: VueWrapper, text: string) {
      await wrapper.get('[data-testid="shape-name-input"]').setValue(text)
      await wrapper.get('[data-testid="shape-name-input"]').trigger('keydown', { key: 'Enter' })
      await flushPromises()
    }

    it('places the new element at the stored length of the connection type, vertically centered, with its connection', async () => {
      const { wrapper, fetchFn } = await mountCanvas({ allowed: only(METATYPE_FLOW, METATYPE_PROCESS) }, [])
      await addConnected(wrapper)
      await name(wrapper, 'Approve')
      await save(wrapper)
      const request = lastSave(fetchFn)
      // ERP: x 100, width 120, 150 pt gap; the process is 90 x 30 pt, the centers at y 70
      expect(request.shapes.at(-1)).toMatchObject({ x: 370, y: 55, width: 90, height: 30 })
      expect(request.newEntities).toEqual([expect.objectContaining({ metatypeId: METATYPE_PROCESS, name: 'Approve' })])
      expect(request.newConnections).toEqual([{ key: expect.any(String), metatypeId: METATYPE_FLOW }])
      expect(request.edges).toEqual([expect.objectContaining({ sourceShapeId: ERP, targetShapeId: request.shapes.at(-1)!.id })])
    })

    it('places it 90 pt from the source when the connection type has no stored length', async () => {
      const { wrapper, fetchFn } = await mountCanvas({ allowed: only(METATYPE_ASSOCIATION, METATYPE_COMPONENT) }, [])
      await addConnected(wrapper)
      await name(wrapper, 'Partner')
      await save(wrapper)
      expect(lastSave(fetchFn).shapes.at(-1)).toMatchObject({ x: 310, y: 50, width: 120, height: 40 })
    })

    it('removes the new element and its connection again when its name stays empty', async () => {
      const { wrapper } = await mountCanvas({ allowed: only(METATYPE_FLOW, METATYPE_PROCESS) }, [])
      await addConnected(wrapper)
      expect(drawn(wrapper)).toHaveLength(1)
      await name(wrapper, '   ')
      expect(drawn(wrapper)).toEqual([])
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('tells that no connection is allowed from the element, and places nothing', async () => {
      const { wrapper } = await mountCanvas({ allowed: () => ({ allowed: false, options: [] }) }, [])
      await selectShape(wrapper, ERP)
      await wrapper.get('[data-testid="canvas-add-connected"]').trigger('click')
      await vi.waitFor(() => expect(document.body.querySelector('[data-testid="connected-none"]')).not.toBeNull())
      expect(document.body.querySelector('[data-testid="connected-add"]')!.hasAttribute('disabled')).toBe(true)
      expect(drawn(wrapper)).toEqual([])
    })

    it('needs a selected shape and cannot be used by a reader', async () => {
      const { wrapper } = await mountCanvas({}, [])
      expect(wrapper.get('[data-testid="canvas-add-connected"]').attributes('disabled')).toBeDefined()
      const reader = await mountCanvas({}, [], false)
      expect(reader.wrapper.find('[data-testid="canvas-add-connected"]').exists()).toBe(false)
    })
  })

  describe('a rejected or forbidden save', () => {
    it('marks the rejected edge with the reason and keeps all changes', async () => {
      const { wrapper } = await mountCanvas({ rejectSave: { status: 422, errors: [{ field: 'edges[0].sourceShapeId', code: 'SOURCE_SHAPE_MISMATCH', message: 'The source shape does not show the source entity.' }] } }, [])
      await connect(wrapper, ERP, APPROVAL)
      await save(wrapper)
      const id = drawn(wrapper)[0]!
      expect(wrapper.get(`[data-testid="edge-${id}"]`).attributes('data-error')).toBe('true')
      expect(wrapper.get(`[data-testid="edge-${id}"] title`).text()).toBe('The source shape does not show the source entity.')
      expect(wrapper.find('[data-testid="canvas-rejected"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    })

    it('names the missing permission when a new connection is forbidden', async () => {
      const { wrapper } = await mountCanvas({ rejectSave: { status: 403 } }, [])
      await connect(wrapper, ERP, APPROVAL)
      await save(wrapper)
      expect(wrapper.get('[data-testid="canvas-failure"]').text()).toContain('scoping type')
    })
  })

  it('draws the shapes as before', async () => {
    const { wrapper } = await mountCanvas()
    expect(wrapper.findAllComponents(EntityShape)).toHaveLength(SHAPES.length)
  })
})
