import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { DiagramEntityChild, DiagramShape, DiagramView, DiagramViewWrite } from '../../api/diagramView'
import { session } from '../../session/store'
import {
  allowedDataConnections,
  assignment,
  assignmentTargets,
  BPMN_TYPES,
  bpmnEdge,
  bpmnShape,
  bpmnView,
  entityStatesOf,
  ORG_TYPES,
  orgShape,
  organizationMetatypes,
  organizationModel,
  organizationViewpoint,
  withAssignments,
} from '../../test/bpmn'
import { diagramApi, ME_DIAGRAM_ARCHITECT, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER, type DiagramApiOptions } from '../../test/diagrams'
import { testRouter } from '../../test/router'
import { ptToPx } from './canvasModel'
import { COLLECTION_ID, REALIZED_BY_ID } from './dataObjects'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityDetailsPanel from './EntityDetailsPanel.vue'
import EntityShape from './EntityShape.vue'

// specs diagram/bpmn-data-objects, diagram/bpmn-process-diagrams and diagram/bpmn-assignments (data shapes) at the canvas (SCR-021;
// us-083-bpmn-data-objects D2, D4, D5, D8): the symbols, the offered data association, data objects that do not nest, and
// "Realized by" of a data store

const mounted: VueWrapper[] = []

interface MountOptions {
  me?: unknown
  canDelete?: boolean
  editable?: boolean
  viewpoint?: DiagramView['viewpoint']
  organization?: boolean
  metatypes?: unknown[]
  api?: Partial<DiagramApiOptions>
}

async function mountCanvas(view: DiagramView, options: MountOptions = {}) {
  const editable = options.editable ?? true
  const fetchFn = diagramApi({
    me: options.me ?? (editable ? ME_DIAGRAM_WRITER : ME_DIAGRAM_READER),
    shapes: view.shapes,
    edges: view.edges,
    viewpoint: options.viewpoint ?? view.viewpoint,
    organization: organizationModel(),
    metatypes: options.metatypes ?? organizationMetatypes(),
    assignmentTargets: { ...assignmentTargets(), realizer: [software(801, 'PostgreSQL'), software(802, 'Redis')] },
    entityStates: entityStatesOf(view.shapes),
    allowed: allowedDataConnections,
    ...options.api,
  })
  const wrapper = mount(DiagramCanvas, {
    props: { view, version: view.diagram.version, editable, canDelete: options.canDelete ?? false, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, fetchFn }
}

const software = (id: number, name: string) => ({ id, name, metatype: { id: BPMN_TYPES.systemSoftware, name: 'System Software', abbreviation: 'SS' }, childCount: 0, parentEntityId: null })
const data = (wrapper: VueWrapper, id: string) => wrapper.findAllComponents(EntityShape).find((entry) => entry.props('id') === id)!.props('data')
const body = (selector: string) => document.body.querySelector<HTMLElement>(selector)
const written = (request: DiagramViewWrite, id: string) => request.shapes.find((entry) => entry.id === id)!
const entityOf = (shapes: DiagramShape[], id: string) => shapes.find((entry) => entry.id === id)!.entity.id

async function click(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-click', { node: { id } })
  await flushPromises()
}

async function contextMenu(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id } })
  await flushPromises()
}

async function connect(wrapper: VueWrapper, source: string, target: string) {
  wrapper.findComponent(VueFlow).vm.$emit('connect', { source, target })
  await flushPromises()
}

function drag(wrapper: VueWrapper, id: string, x: number, y: number) {
  wrapper.findComponent(VueFlow).vm.$emit('node-drag-stop', { node: { id, position: { x: ptToPx(x), y: ptToPx(y) }, computedPosition: { x: ptToPx(x), y: ptToPx(y) } } })
  return flushPromises()
}

async function paletteDrop(wrapper: VueWrapper, type: string, x: number, y: number) {
  const event = new Event('drop', { bubbles: true, cancelable: true })
  Object.assign(event, { clientX: ptToPx(x), clientY: ptToPx(y), dataTransfer: { types: ['application/x-oea-metatype'], getData: () => type } })
  wrapper.get('.vue-flow').element.dispatchEvent(event)
  await flushPromises()
}

async function nameNew(wrapper: VueWrapper, name: string) {
  const input = wrapper.get('[data-testid="shape-name-input"]')
  await input.setValue(name)
  await input.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

async function save(wrapper: VueWrapper, fetchFn: ReturnType<typeof diagramApi>): Promise<DiagramViewWrite> {
  await wrapper.get('[data-testid="canvas-save"]').trigger('click')
  await flushPromises()
  return fetchFn.state.saves.at(-1)!
}

/** the lane "Purchasing" in the pool, a task, a data object, a data store and a TOGAF data object lying outside */
function process(): DiagramShape[] {
  return [
    bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200),
    bpmnShape('lane', 'Purchasing', 'lane', 30, 0, 570, 100, 'pool'),
    bpmnShape('review', 'Review order', 'userTask', 100, 20, 90, 60, 'lane'),
    bpmnShape('order', 'Order', 'dataObject', 700, 20, 36, 50),
    bpmnShape('orderDb', 'Order DB', 'dataStore', 700, 120, 50, 50),
    bpmnShape('invoice', 'Invoice', 'togafData', 700, 220, 36, 50),
    bpmnShape('check', 'Check order', 'task', 700, 320, 90, 60),
  ]
}

describe('data objects on the canvas', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('symbols', () => {
    it('draws a data object as a document, a data store as a cylinder and the TOGAF data object of the viewpoint as a document', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      expect(data(wrapper, 'order').symbol?.kind).toBe('data-object')
      expect(data(wrapper, 'invoice').symbol?.kind).toBe('data-object')
      expect(data(wrapper, 'orderDb').symbol?.kind).toBe('data-store')
      expect(wrapper.get('[data-testid="shape-order"]').attributes('data-symbol')).toBe('data-object')
      expect(wrapper.get('[data-testid="shape-orderDb"]').attributes('data-symbol')).toBe('data-store')
    })

    it('draws the collection marker only for a data object whose view says "Collection"', async () => {
      const shapes = process().map((entry) => (entry.id === 'order' ? { ...entry, collection: true } : entry))
      const { wrapper } = await mountCanvas(bpmnView(shapes))
      expect(data(wrapper, 'order').collection).toBe(true)
      expect(wrapper.find('[data-testid="shape-order"] [data-testid="collection-marker"]').exists()).toBe(true)
      expect(data(wrapper, 'invoice').collection).toBe(false)
      expect(wrapper.find('[data-testid="shape-invoice"] [data-testid="collection-marker"]').exists()).toBe(false)
      expect(data(wrapper, 'orderDb').collection).toBeUndefined()
      expect(wrapper.find('[data-testid="shape-orderDb"] [data-testid="collection-marker"]').exists()).toBe(false)
    })

    it('draws the marker from the pending edit of the value and not any more when it is set back', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      await click(wrapper, 'order')
      const panel = wrapper.findComponent(EntityDetailsPanel)
      panel.vm.$emit('change', { field: 'property', propertyDefId: COLLECTION_ID, value: 'true' }, 1)
      await flushPromises()
      expect(data(wrapper, 'order').collection).toBe(true)
      expect(wrapper.find('[data-testid="shape-order"] [data-testid="collection-marker"]').exists()).toBe(true)
      panel.vm.$emit('change', { field: 'property', propertyDefId: COLLECTION_ID, value: undefined }, 1)
      await flushPromises()
      expect(data(wrapper, 'order').collection).toBe(false)
    })

    it('draws a data association dashed with an open arrow and no circle', async () => {
      const view = bpmnView(process(), [bpmnEdge('in', 'dataInput', 'order', 'review', 'Order → Review order'), bpmnEdge('seq', 'sequenceFlow', 'review', 'check', 'Flow')])
      const { wrapper } = await mountCanvas(view)
      const edge = wrapper.get('[data-testid="edge-in"]')
      expect(edge.attributes('data-flow')).toBe('data')
      expect(edge.get('path').attributes('stroke-dasharray')).toBeDefined()
      expect(edge.find('[data-testid="edge-open-arrow"]').exists()).toBe(true)
      expect(edge.find('[data-testid="edge-source-circle"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="edge-seq"]').find('[data-testid="edge-open-arrow"]').exists()).toBe(false)
    })
  })

  describe('drawing a data association', () => {
    it('draws the input association from a data object to a task and saves it with the object as its source', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await connect(wrapper, 'order', 'review')
      expect(body('[data-testid="connection-type-dialog"]')).toBeNull()
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections?.map((connection) => connection.metatypeId)).toEqual([BPMN_TYPES.dataInput])
      expect(request.edges).toHaveLength(1)
      expect(request.edges![0]).toMatchObject({ sourceShapeId: 'order', targetShapeId: 'review' })
    })

    it('offers the output association and not the input association when the line leads from a task to a data object', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await connect(wrapper, 'review', 'order')
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections?.map((connection) => connection.metatypeId)).toEqual([BPMN_TYPES.dataOutput])
      expect(request.edges![0]).toMatchObject({ sourceShapeId: 'review', targetShapeId: 'order' })
    })

    it('offers no data association between a task and a task, and none between a gateway and a task', async () => {
      const shapes = [...process(), bpmnShape('gateway', 'Valid?', 'gateway', 700, 420, 38, 38)]
      const { wrapper } = await mountCanvas(bpmnView(shapes))
      await connect(wrapper, 'gateway', 'review')
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toBe('No connection is allowed here.')
      expect(body('[data-testid="connection-type-dialog"]')).toBeNull()
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('lets the choice between the input association and "Realized By" for a data store and a task', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      await connect(wrapper, 'orderDb', 'review')
      await new Promise((resolve) => setTimeout(resolve))
      expect(document.body.textContent).toContain('Data Input Association')
      expect(document.body.textContent).toContain('Realized By')
      expect(document.body.textContent).not.toContain('Data Output Association')
    })

    it('draws "Realized By" from a data store to another element and not from a data object', async () => {
      const shapes = [...process(), bpmnShape('postgres', 'PostgreSQL', 'systemSoftware', 900, 120, 90, 30)]
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(shapes))
      await connect(wrapper, 'order', 'postgres')
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toBe('No connection is allowed here.')
      await connect(wrapper, 'orderDb', 'postgres')
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections?.map((connection) => connection.metatypeId)).toEqual([REALIZED_BY_ID])
      expect(request.edges![0]).toMatchObject({ sourceShapeId: 'orderDb', targetShapeId: 'postgres' })
    })

    it('shows a rejected direction on the shape the save names', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()), {
        api: { rejectSave: { status: 422, errors: [{ field: 'newConnections[0]', code: 'DATA_ASSOCIATION_DIRECTION', message: 'A "Realized By" starts at a data store.' }] } },
      })
      await contextMenu(wrapper, 'orderDb')
      await wrapper.get('[data-testid="canvas-menu-assign-realizer"]').trigger('click')
      await flushPromises()
      body('[data-testid="assignment-801"]')!.click()
      await flushPromises()
      await save(wrapper, fetchFn)
      expect(data(wrapper, 'orderDb').error).toBe('A "Realized By" starts at a data store.')
    })
  })

  describe('data objects do not nest', () => {
    const view = () => ({ ...bpmnView(process()), askNewParent: true })

    it('lets a data object move into a lane without a parent change and without "New parent?"', async () => {
      const { wrapper, fetchFn } = await mountCanvas(view())
      await drag(wrapper, 'order', 300, 30)
      expect(body('[data-testid="new-parent-yes"]')).toBeNull()
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'order')).toMatchObject({ x: 300, y: 30, parentShapeId: 'lane' })
      expect(request.parentChanges).toBeUndefined()
    })

    it('asks "New parent?" for a task that moves into the lane, as before', async () => {
      const { wrapper } = await mountCanvas(view())
      await drag(wrapper, 'check', 300, 30)
      await new Promise((resolve) => setTimeout(resolve))
      expect(body('[data-testid="new-parent-yes"]')).not.toBeNull()
    })

    it('creates a new data object in a lane without a parent and a new task in the same lane as its child', async () => {
      const shapes = process()
      const { wrapper, fetchFn } = await mountCanvas({ ...bpmnView(shapes), askNewParent: true })
      await paletteDrop(wrapper, BPMN_TYPES.dataObject, 400, 50)
      await nameNew(wrapper, 'Invoice draft')
      await paletteDrop(wrapper, BPMN_TYPES.task, 450, 50)
      await nameNew(wrapper, 'Pay invoice')
      const request = await save(wrapper, fetchFn)
      const object = request.newEntities!.find((entity) => entity.name === 'Invoice draft')!
      const task = request.newEntities!.find((entity) => entity.name === 'Pay invoice')!
      expect(object.parentEntityId).toBeUndefined()
      expect(object.parentNewEntityKey).toBeUndefined()
      expect(task.parentEntityId).toBe(entityOf(shapes, 'lane'))
      const shape = request.shapes.find((entry) => entry.newEntityKey === object.key)!
      expect(shape.parentShapeId).toBe('lane')
    })

    /** the pool "Order handling" with the role lane "Approver", and a data object, a data store and a task beside it */
    function roleLaneView(): DiagramView {
      const shapes = [
        orgShape('unit', 'Order handling', 'orgUnit', 0, 0, 600, 300),
        orgShape('role', 'Approver', 'role', 30, 0, 570, 150, 'unit'),
        bpmnShape('order', 'Order', 'dataObject', 700, 20, 36, 50),
        bpmnShape('orderDb', 'Order DB', 'dataStore', 700, 120, 50, 50),
        bpmnShape('check', 'Check order', 'task', 700, 220, 90, 60),
      ]
      return { ...bpmnView(shapes), viewpoint: organizationViewpoint() }
    }

    it('gives a data object and a data store dropped into a role lane no assignment', async () => {
      const { wrapper, fetchFn } = await mountCanvas(roleLaneView())
      await drag(wrapper, 'order', 200, 30)
      await drag(wrapper, 'orderDb', 300, 30)
      expect(data(wrapper, 'order').assignments).toBeUndefined()
      expect(data(wrapper, 'orderDb').assignments?.roles).toEqual([])
      expect(data(wrapper, 'orderDb').assignments?.realizers).toEqual([])
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'order').parentShapeId).toBe('role')
      expect(written(request, 'orderDb').parentShapeId).toBe('role')
      expect(request.newConnections ?? []).toEqual([])
      expect(request.removedConnections ?? []).toEqual([])
    })

    it('assigns a task dropped into the same role lane, as before', async () => {
      const { wrapper, fetchFn } = await mountCanvas(roleLaneView())
      await drag(wrapper, 'check', 400, 30)
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections?.map((connection) => connection.metatypeId)).toContain(ORG_TYPES.performsRole)
    })

    it('does not place a child drawn as data object with "Show children", names it in the notice and nests the others', async () => {
      const shapes = process().map((entry) => (entry.id === 'lane' ? { ...entry, entity: { ...entry.entity, childCount: 2 } } : entry))
      const laneId = entityOf(shapes, 'lane')
      const child = (id: number, name: string, type: keyof typeof BPMN_TYPES): DiagramEntityChild => ({
        entity: { id, name, metatypeId: BPMN_TYPES[type], childCount: 0, parentEntityId: laneId },
        allowed: true,
        compositionConnection: null,
      })
      const { wrapper } = await mountCanvas(bpmnView(shapes), {
        api: { children: { [laneId]: [child(901, 'Check invoice', 'task'), child(902, 'Delivery note', 'dataObject'), child(903, 'Stock DB', 'dataStore')] } },
      })
      await contextMenu(wrapper, 'lane')
      await wrapper.get('[data-testid="canvas-menu-children"]').trigger('click')
      await flushPromises()
      const names = wrapper.findAllComponents(EntityShape).map((node) => node.props('data').name)
      expect(names).toContain('Check invoice')
      expect(names).not.toContain('Delivery note')
      expect(names).not.toContain('Stock DB')
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toContain('Delivery note, Stock DB')
    })
  })

  describe('realized by on a data store', () => {
    it('offers "Realized by…" on a data store to a writer, and not on a data object, a task, a lane or a reader', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      await contextMenu(wrapper, 'orderDb')
      expect(wrapper.get('[data-testid="canvas-menu-assign-realizer"]').text()).toBe('Realized by…')
      for (const id of ['order', 'invoice', 'review', 'lane', 'pool']) {
        await contextMenu(wrapper, id)
        expect(wrapper.find('[data-testid="canvas-menu-assign-realizer"]').exists()).toBe(false)
      }
      const reader = await mountCanvas(bpmnView(process()), { editable: false })
      await contextMenu(reader.wrapper, 'orderDb')
      expect(reader.wrapper.find('[data-testid="canvas-menu-assign-realizer"]').exists()).toBe(false)
    })

    it('offers it only when the metamodel has the type "Realized By"', async () => {
      const without = organizationMetatypes().filter((type) => type.id !== REALIZED_BY_ID)
      const { wrapper } = await mountCanvas(bpmnView(process()), { metatypes: without })
      await contextMenu(wrapper, 'orderDb')
      expect(wrapper.find('[data-testid="canvas-menu-assign-realizer"]').exists()).toBe(false)
    })

    it('searches without "Create new", adds a pending badge that counts as an unsaved change and saves the connection without an edge', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await contextMenu(wrapper, 'orderDb')
      await wrapper.get('[data-testid="canvas-menu-assign-realizer"]').trigger('click')
      await flushPromises()
      expect(body('[data-testid="assignment-dialog"]')).not.toBeNull()
      expect(body('[data-testid="assignment-create"]')).toBeNull()
      body('[data-testid="assignment-801"]')!.click()
      await flushPromises()
      expect(wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').text()).toContain('PostgreSQL')
      expect(wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').attributes('data-pending')).toBe('true')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections).toEqual([{ key: expect.any(String), metatypeId: REALIZED_BY_ID, sourceShapeId: 'orderDb', targetEntityId: 801 }])
      expect(request.edges ?? []).toEqual([])
      expect(wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').text()).toContain('PostgreSQL')
      expect(wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').attributes('data-pending')).toBeUndefined()
    })

    it('searches with part=realizer', async () => {
      const recorded: { path: string }[] = []
      const { wrapper } = await mountCanvas(bpmnView(process()), { api: { recorded: recorded as never } })
      await contextMenu(wrapper, 'orderDb')
      await wrapper.get('[data-testid="canvas-menu-assign-realizer"]').trigger('click')
      await flushPromises()
      expect(recorded.some((entry) => entry.path.includes('reference-targets') && entry.path.includes('part=realizer'))).toBe(true)
    })

    // the connection of the saved element is made once: an assignment gets a new connection ID each time
    const connection = assignment('PostgreSQL', 801, 3)
    const stored = () => process().map((entry) => (entry.id === 'orderDb' ? withAssignments(entry, { realizedBy: [connection] }) : entry))

    it('shows the saved element as a badge and offers "×" with entity:delete only', async () => {
      const writer = await mountCanvas(bpmnView(stored()), { canDelete: false })
      expect(writer.wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').text()).toContain('PostgreSQL')
      expect(writer.wrapper.find('[data-testid="shape-orderDb"] [data-testid="realizer-remove"]').exists()).toBe(false)
      const architect = await mountCanvas(bpmnView(stored()), { canDelete: true, me: ME_DIAGRAM_ARCHITECT })
      expect(architect.wrapper.find('[data-testid="shape-orderDb"] [data-testid="realizer-remove"]').exists()).toBe(true)
    })

    it('removes a saved element with "×" and sends the connection and the version read', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(stored()), { canDelete: true, me: ME_DIAGRAM_ARCHITECT })
      await wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-remove"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').exists()).toBe(false)
      const request = await save(wrapper, fetchFn)
      expect(request.removedConnections).toEqual([{ connectionId: connection.connectionId, expectedVersion: 3 }])
    })

    it('offers "×" for a pending element without entity:delete and drops it', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await contextMenu(wrapper, 'orderDb')
      await wrapper.get('[data-testid="canvas-menu-assign-realizer"]').trigger('click')
      await flushPromises()
      body('[data-testid="assignment-802"]')!.click()
      await flushPromises()
      await wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-remove"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
      expect(fetchFn.state.saves).toHaveLength(0)
    })

    it('offers no "×" for a connection that an edge of the diagram shows', async () => {
      const software = bpmnShape('postgres', 'PostgreSQL', 'systemSoftware', 900, 120, 90, 30)
      const shapes = [...stored(), software]
      const shown = bpmnEdge('rb', 'realizedBy', 'orderDb', 'postgres', 'Order DB → PostgreSQL')
      shown.connection.id = connection.connectionId
      const { wrapper } = await mountCanvas(bpmnView(shapes, [shown]), { canDelete: true, me: ME_DIAGRAM_ARCHITECT })
      expect(wrapper.get('[data-testid="shape-orderDb"] [data-testid="realizer-badge"]').text()).toContain('PostgreSQL')
      expect(wrapper.find('[data-testid="shape-orderDb"] [data-testid="realizer-remove"]').exists()).toBe(false)
    })

    it('has no badge on a data object, a task or any other shape', async () => {
      const { wrapper } = await mountCanvas(bpmnView(stored()))
      for (const id of ['order', 'review', 'lane', 'invoice']) {
        expect(wrapper.find(`[data-testid="shape-${id}"] [data-testid="realizer-badge"]`).exists()).toBe(false)
      }
    })
  })
})
