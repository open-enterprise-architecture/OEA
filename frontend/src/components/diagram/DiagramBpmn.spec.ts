import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DiagramEdge, DiagramShape, DiagramView, DiagramViewWrite } from '../../api/diagramView'
import { session } from '../../session/store'
import { BPMN_TYPES, bpmnEdge, bpmnShape, bpmnView } from '../../test/bpmn'
import { diagramApi, ENTITIES, FINANCE_SUITE, METATYPE_COMPONENT, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER } from '../../test/diagrams'
import { testRouter } from '../../test/router'
import { bpmnSymbol } from './bpmnSymbols'
import { ptToPx, type ShapeData } from './canvasModel'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityShape from './EntityShape.vue'

// specs diagram/bpmn-process-diagrams and metamodel/viewpoints at the canvas (SCR-021; us-074-bpmn-process-diagrams
// D3 to D6): the symbols and lines, the grouped palette, the container rule and the warnings of the pool rules

// the process of the stories: the pool "Customer" with the lanes "Sales" and "Back office" holding "Team", and the pool "Supplier"
function process(): DiagramShape[] {
  return [
    bpmnShape('customer', 'Customer', 'pool', 0, 0, 700, 400),
    bpmnShape('sales', 'Sales', 'lane', 30, 10, 660, 180, 'customer'),
    bpmnShape('office', 'Back office', 'lane', 30, 200, 660, 190, 'customer'),
    bpmnShape('team', 'Team', 'lane', 60, 210, 620, 170, 'office'),
    bpmnShape('check', 'Check order', 'userTask', 100, 40, 90, 60, 'sales'),
    bpmnShape('ship', 'Ship order', 'task', 300, 40, 90, 60, 'sales'),
    bpmnShape('pack', 'Pack order', 'task', 100, 240, 90, 60, 'team'),
    bpmnShape('supplier', 'Supplier', 'pool', 0, 500, 700, 200),
    bpmnShape('deliver', 'Deliver', 'task', 100, 540, 90, 60, 'supplier'),
  ]
}

const mounted: VueWrapper[] = []

async function mountCanvas(view: DiagramView, editable = true, options: Parameters<typeof diagramApi>[0] extends infer T ? Partial<T> : never = {}) {
  const fetchFn = diagramApi({ me: editable ? ME_DIAGRAM_WRITER : ME_DIAGRAM_READER, shapes: view.shapes, edges: view.edges, ...options })
  const wrapper = mount(DiagramCanvas, {
    props: { view, version: view.diagram.version, editable, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, fetchFn }
}

const dialog = (testId: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)

async function click(testId: string) {
  await vi.waitFor(() => expect(dialog(testId)).not.toBeNull())
  dialog(testId)!.click()
  await flushPromises()
}

async function drop(wrapper: VueWrapper, id: string, x: number, y: number) {
  const px = ptToPx
  wrapper.findComponent(VueFlow).vm.$emit('node-drag-stop', { node: { id, position: { x: px(x), y: px(y) }, computedPosition: { x: px(x), y: px(y) } } })
  await flushPromises()
}

/** A palette drop of the type at the point in pt. */
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

async function save(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="canvas-save"]').trigger('click')
  await flushPromises()
}

async function contextMenu(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id } })
  await flushPromises()
}

/** The shape of the first new entity of a save: a nested shape follows its container in the drawing order. */
const shapeOfNew = (request: DiagramViewWrite) => request.shapes.find((entry) => entry.newEntityKey === request.newEntities?.[0]?.key)
const shapeEl = (wrapper: VueWrapper, id: string) => wrapper.get(`[data-testid="shape-${id}"]`)
const entityOf = (view: DiagramView, id: string) => view.shapes.find((entry) => entry.id === id)!.entity.id
const groupTypes = (wrapper: VueWrapper, group: string) =>
  wrapper.findAll(`[data-testid="palette-group-${group}"] button`).map((button) => button.text())

describe('DiagramCanvas BPMN', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('symbols', () => {
    const SYMBOLS: [string, keyof typeof BPMN_TYPES, string, string | undefined][] = [
      ['task', 'task', 'task', undefined],
      ['user', 'userTask', 'task', 'user'],
      ['sub', 'subProcess', 'sub-process', undefined],
      ['start', 'startEvent', 'event', undefined],
      ['message', 'messageStart', 'event', 'message-catch'],
      ['catch', 'catchEvent', 'event', 'message-catch'],
      ['end', 'endEvent', 'event', undefined],
      ['gateway', 'gateway', 'gateway', 'exclusive'],
      ['pool', 'pool', 'pool', undefined],
      ['lane', 'lane', 'lane', undefined],
      ['note', 'textAnnotation', 'annotation', undefined],
      ['group', 'group', 'group', undefined],
    ]
    const SHAPES = SYMBOLS.map(([id, type], index) => bpmnShape(id, `${id} shape`, type, index * 100, 0, 90, 60))

    it.each(SYMBOLS)('draws the shape %s of the type %s as %s with the marker %s', async (id, _type, kind, marker) => {
      const { wrapper } = await mountCanvas(bpmnView(SHAPES))
      const element = shapeEl(wrapper, id)
      expect(element.attributes('data-symbol')).toBe(kind)
      expect(element.find('[data-testid="symbol-marker"]').exists()).toBe(marker !== undefined)
      if (marker) {
        expect(element.get('[data-testid="symbol-marker"]').attributes('data-marker')).toBe(marker)
      }
      expect(element.find('[data-testid="symbol-outline"]').exists()).toBe(true)
      // the abbreviation of the box is not shown: the symbol is the type
      expect(element.text()).not.toMatch(/^[A-Z]{2,3} /)
    })

    it('gives every symbol an aria-label with the type name and the name', async () => {
      const { wrapper } = await mountCanvas(bpmnView(SHAPES))
      expect(shapeEl(wrapper, 'user').attributes('aria-label')).toBe('User Task: user shape')
      expect(shapeEl(wrapper, 'gateway').attributes('aria-label')).toBe('Exclusive Gateway: gateway shape')
      expect(shapeEl(wrapper, 'pool').attributes('role')).toBe('group')
    })

    it('draws an event as a circle and a gateway as a diamond, start thin, end thick, intermediate double', async () => {
      const { wrapper } = await mountCanvas(bpmnView(SHAPES))
      const rings = (id: string) => shapeEl(wrapper, id).findAll('ellipse')
      expect(rings('start')).toHaveLength(1)
      expect(rings('start')[0]!.attributes('stroke-width')).toBe('1.5')
      expect(rings('end')[0]!.attributes('stroke-width')).toBe('4')
      expect(rings('catch')).toHaveLength(2)
      expect(shapeEl(wrapper, 'gateway').find('polygon').attributes('points')).toBe('50,1 99,50 50,99 1,50')
    })

    it('writes the name of a pool and a lane in a label band on the left and the name of an event below it', async () => {
      const { wrapper } = await mountCanvas(bpmnView(SHAPES))
      expect(shapeEl(wrapper, 'pool').get('[data-testid="symbol-band"] [data-testid="shape-name"]').text()).toBe('pool shape')
      expect(shapeEl(wrapper, 'lane').find('[data-testid="symbol-band"]').exists()).toBe(true)
      expect(shapeEl(wrapper, 'start').find('[data-testid="symbol-band"]').exists()).toBe(false)
      expect(shapeEl(wrapper, 'start').get('[data-testid="shape-name"]').text()).toBe('start shape')
    })

    it.each([
      ['annotation', 'Pays within 14 days', 'Pays within 14 days'],
      ['annotation', undefined, 'Note'],
      ['annotation', '', 'Note'],
      ['task', 'Pays within 14 days', 'Note'],
    ] as const)('shows in a %s with the description %j the text %j', (kind, description, shown) => {
      const symbol = bpmnSymbol('bpmn2', kind === 'annotation' ? 'bpmn:TextAnnotation' : 'bpmn:Task')
      const data: ShapeData = {
        name: 'Note',
        description,
        symbol,
        editable: false,
        connectable: false,
        editing: false,
        unsaved: false,
        missingMandatory: [],
        conflict: false,
        metatypeName: 'Text Annotation',
        container: false,
        rename: () => undefined,
        resized: () => undefined,
      }
      const wrapper = mount(EntityShape, { props: { id: 'x', data }, global: { plugins: [ui] } })
      expect(wrapper.get('[data-testid="shape-name"]').text()).toBe(shown)
    })

    it('draws a type without a mapping as the generic box', async () => {
      const view = bpmnView([bpmnShape('proc', 'Order handling', 'process', 0, 0, 90, 30)])
      const { wrapper } = await mountCanvas(view)
      expect(shapeEl(wrapper, 'proc').attributes('data-symbol')).toBeUndefined()
      expect(shapeEl(wrapper, 'proc').text()).toContain('PR')
      expect(shapeEl(wrapper, 'proc').find('[data-testid="symbol-outline"]').exists()).toBe(false)
    })

    it.each(['archimate3', 'generic', 'uml'] as const)('keeps the box in a diagram of the notation %s', async (notation) => {
      const { wrapper } = await mountCanvas(bpmnView(SHAPES, [], notation))
      for (const [id] of SYMBOLS) {
        expect(shapeEl(wrapper, id).attributes('data-symbol')).toBeUndefined()
      }
      expect(shapeEl(wrapper, 'user').text()).toContain('UT')
    })

    it('marks an unsaved, incomplete or conflicting symbol as the box does', async () => {
      const view = bpmnView([{ ...bpmnShape('t', 'Check', 'userTask', 0, 0, 90, 60), missingMandatory: ['Owner'], conflict: true }])
      const { wrapper } = await mountCanvas(view)
      expect(shapeEl(wrapper, 't').attributes('data-incomplete')).toBe('true')
      expect(shapeEl(wrapper, 't').attributes('data-conflict')).toBe('true')
      expect(shapeEl(wrapper, 't').get('[data-testid="shape-incomplete"]').text()).toContain('Owner')
      expect(shapeEl(wrapper, 't').find('[data-testid="shape-conflict"]').exists()).toBe(true)
    })
  })

  describe('lines of the flows', () => {
    const SHAPES = [bpmnShape('a', 'Check', 'task', 0, 0, 90, 60), bpmnShape('b', 'Ship', 'task', 300, 0, 90, 60)]
    const EDGES: [string, keyof typeof BPMN_TYPES][] = [
      ['seq', 'sequenceFlow'],
      ['msg', 'messageFlow'],
      ['asc', 'association'],
      ['call', 'calls'],
    ]

    async function edges(notation: 'bpmn2' | 'generic' = 'bpmn2') {
      const list: DiagramEdge[] = EDGES.map(([id, type]) => bpmnEdge(id, type, 'a', 'b'))
      return mountCanvas(bpmnView(SHAPES, list, notation))
    }

    it('draws a sequence flow solid with a filled arrow', async () => {
      const { wrapper } = await edges()
      const edge = wrapper.get('[data-testid="edge-seq"]')
      expect(edge.attributes('data-flow')).toBe('sequence')
      expect(edge.get('path').attributes('stroke-dasharray')).toBeUndefined()
      expect(edge.find('polygon').exists()).toBe(true)
      expect(edge.find('[data-testid="edge-source-circle"]').exists()).toBe(false)
    })

    it('draws a message flow dashed with an open circle at the source and an open arrow at the target', async () => {
      const { wrapper } = await edges()
      const edge = wrapper.get('[data-testid="edge-msg"]')
      expect(edge.attributes('data-flow')).toBe('message')
      expect(edge.get('path').attributes('stroke-dasharray')).toBe('8 4')
      expect(edge.get('[data-testid="edge-source-circle"]').attributes('fill')).toBe('var(--ui-bg)')
      expect(edge.find('[data-testid="edge-open-arrow"]').exists()).toBe(true)
      expect(edge.find('polygon').exists()).toBe(false)
    })

    it('draws an association dotted without an arrow', async () => {
      const { wrapper } = await edges()
      const edge = wrapper.get('[data-testid="edge-asc"]')
      expect(edge.attributes('data-flow')).toBe('association')
      expect(edge.get('path').attributes('stroke-dasharray')).toBe('2 3')
      expect(edge.find('polygon').exists()).toBe(false)
      expect(edge.find('polyline').exists()).toBe(false)
    })

    it('keeps the line of a type without a BPMN element', async () => {
      const { wrapper } = await edges()
      const edge = wrapper.get('[data-testid="edge-call"]')
      expect(edge.attributes('data-flow')).toBeUndefined()
      expect(edge.find('polygon').exists()).toBe(true)
      expect(edge.get('path').attributes('stroke-dasharray')).toBeUndefined()
    })

    it('keeps the default line in the other notations', async () => {
      const { wrapper } = await edges('generic')
      for (const [id] of EDGES) {
        expect(wrapper.get(`[data-testid="edge-${id}"]`).attributes('data-flow')).toBeUndefined()
        expect(wrapper.get(`[data-testid="edge-${id}"]`).find('polygon').exists()).toBe(true)
      }
    })
  })

  describe('palette', () => {
    it('groups the types by their notation element in the order of the groups and keeps the order within them', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      const groups = wrapper.findAll('[data-testid^="palette-group-"]').map((group) => group.attributes('data-testid')!.replace('palette-group-', ''))
      expect(groups).toEqual(['pools', 'activities', 'events', 'gateways', 'artifacts', 'other'])
      expect(groupTypes(wrapper, 'pools')).toEqual(['Lane', 'Pool'])
      expect(groupTypes(wrapper, 'activities')).toEqual(['Approval Task', 'Sub-Process', 'Task', 'User Task'])
      expect(groupTypes(wrapper, 'events')).toEqual(['End Event', 'Intermediate Catch Event', 'Message Start Event', 'Start Event'])
      expect(groupTypes(wrapper, 'gateways')).toEqual(['Exclusive Gateway'])
      expect(groupTypes(wrapper, 'artifacts')).toEqual(['Group', 'Text Annotation'])
      expect(groupTypes(wrapper, 'other')).toEqual(['Process'])
    })

    it('labels the groups', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      expect(wrapper.get('[data-testid="palette-group-pools"]').attributes('aria-label')).toBe('Pools and lanes')
      expect(wrapper.get('[data-testid="palette-group-activities"]').text()).toContain('Activities')
    })

    it('lists a company subtype under the group of its mapping', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      expect(groupTypes(wrapper, 'activities')).toContain('Approval Task')
      await wrapper.get(`[data-testid="palette-${BPMN_TYPES.approvalTask}"]`).trigger('click')
      expect(wrapper.find('[data-testid="shape-name-input"]').exists()).toBe(true)
    })

    it('hides empty groups', async () => {
      const view = bpmnView(process())
      const only = new Set<string>([BPMN_TYPES.pool, BPMN_TYPES.task])
      view.viewpoint.allowedMetatypes = view.viewpoint.allowedMetatypes.filter((type) => only.has(type.id))
      const { wrapper } = await mountCanvas(view)
      expect(wrapper.findAll('[data-testid^="palette-group-"]')).toHaveLength(2)
      expect(wrapper.find('[data-testid="palette-group-events"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="palette-group-other"]').exists()).toBe(false)
    })

    it.each(['archimate3', 'generic', 'uml'] as const)('stays flat in a diagram of the notation %s', async (notation) => {
      const { wrapper } = await mountCanvas(bpmnView(process(), [], notation))
      expect(wrapper.find('[data-testid^="palette-group-"]').exists()).toBe(false)
      expect(wrapper.findAll('[data-testid="canvas-palette"] button')).toHaveLength(14)
    })
  })

  describe('containers', () => {
    it('nests a new task dropped into a lane in the lane and creates it as a child of the lane', async () => {
      const view = bpmnView(process())
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.task, 400, 150)
      await nameNew(wrapper, 'Approve order')
      await save(wrapper)
      const request = fetchFn.state.saves.at(-1)!
      expect(shapeOfNew(request)).toMatchObject({ parentShapeId: 'sales' })
      expect(request.newEntities).toEqual([expect.objectContaining({ name: 'Approve order', parentEntityId: entityOf(view, 'sales') })])
    })

    it('nests a new lane dropped into a pool in the pool', async () => {
      const view = bpmnView([bpmnShape('customer', 'Customer', 'pool', 0, 0, 700, 400)])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.lane, 300, 200)
      await nameNew(wrapper, 'Sales')
      await save(wrapper)
      const request = fetchFn.state.saves.at(-1)!
      expect(shapeOfNew(request)).toMatchObject({ parentShapeId: 'customer' })
      expect(request.newEntities![0]).toMatchObject({ name: 'Sales', parentEntityId: entityOf(view, 'customer') })
    })

    it('does not nest a new event dropped onto a task, but in the lane below the task', async () => {
      const view = bpmnView(process())
      const { wrapper, fetchFn } = await mountCanvas(view)
      // the task "Check order" lies at 100..190 x 40..100 in the lane "Sales"
      await paletteDrop(wrapper, BPMN_TYPES.startEvent, 145, 70)
      await nameNew(wrapper, 'Order received')
      await save(wrapper)
      const request = fetchFn.state.saves.at(-1)!
      expect(shapeOfNew(request)).toMatchObject({ parentShapeId: 'sales' })
      expect(shapeOfNew(request)?.parentShapeId).not.toBe('check')
      expect(request.newEntities![0]).toMatchObject({ parentEntityId: entityOf(view, 'sales') })
    })

    it('nests a new event dropped onto a task outside every container in none', async () => {
      const view = bpmnView([bpmnShape('loose', 'Loose task', 'task', 0, 0, 90, 60)])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.startEvent, 45, 30)
      await nameNew(wrapper, 'Order received')
      await save(wrapper)
      const request = fetchFn.state.saves.at(-1)!
      expect(shapeOfNew(request)?.parentShapeId).toBeUndefined()
      expect(request.newEntities![0]!.parentEntityId).toBeUndefined()
    })

    it.each([
      ['a group', 'group'],
      ['a text annotation', 'textAnnotation'],
    ] as const)('does not nest a shape dropped onto %s', async (_name, type) => {
      const view = bpmnView([bpmnShape('note', 'Note', type, 0, 0, 180, 120)])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.startEvent, 90, 60)
      await nameNew(wrapper, 'Order received')
      await save(wrapper)
      expect(shapeOfNew(fetchFn.state.saves.at(-1)!)?.parentShapeId).toBeUndefined()
    })

    it('nests a shape dropped onto a sub-process in the sub-process', async () => {
      const view = bpmnView([bpmnShape('sub', 'Pick items', 'subProcess', 0, 0, 270, 150)])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.task, 130, 70)
      await nameNew(wrapper, 'Scan item')
      await save(wrapper)
      expect(shapeOfNew(fetchFn.state.saves.at(-1)!)).toMatchObject({ parentShapeId: 'sub' })
    })

    it('keeps a task in its lane when it is moved onto another task and asks nothing', async () => {
      const view = bpmnView(process())
      const { wrapper, fetchFn } = await mountCanvas(view)
      // the center of "Ship order" at 155, 75 lies on "Check order" and in "Sales"
      await drop(wrapper, 'ship', 110, 45)
      expect(dialog('new-parent-dialog')).toBeNull()
      await save(wrapper)
      expect(fetchFn.state.saves.at(-1)!.shapes.find((entry) => entry.id === 'ship')).toMatchObject({ parentShapeId: 'sales', x: 110, y: 45 })
    })

    it('asks "New parent?" naming the lane when a task is moved onto a task of another lane', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      // the center at 155, 280 lies on "Pack order" and in the lane "Team"
      await drop(wrapper, 'ship', 110, 250)
      expect(dialog('new-parent-dialog')).not.toBeNull()
      expect(document.body.textContent).toContain('Make Team the parent of Ship order?')
      expect(document.body.textContent).not.toContain('Pack order the parent')
    })

    it('offers "Show children" for a pool and a lane, and not for a task with children', async () => {
      const shapes = process().map((entry) =>
        entry.id === 'customer' || entry.id === 'check' ? { ...entry, entity: { ...entry.entity, childCount: 2 } } : entry,
      )
      const { wrapper } = await mountCanvas(bpmnView(shapes))
      await contextMenu(wrapper, 'customer')
      expect(wrapper.find('[data-testid="canvas-menu-children"]').exists()).toBe(true)
      await contextMenu(wrapper, 'check')
      expect(wrapper.find('[data-testid="canvas-menu-children"]').exists()).toBe(false)
    })

    it.each(['archimate3', 'generic', 'uml'] as const)('offers "Show children" for every shape with children in the notation %s', async (notation) => {
      const shapes = process().map((entry) => (entry.id === 'check' ? { ...entry, entity: { ...entry.entity, childCount: 2 } } : entry))
      const { wrapper } = await mountCanvas(bpmnView(shapes, [], notation))
      await contextMenu(wrapper, 'check')
      expect(wrapper.find('[data-testid="canvas-menu-children"]').exists()).toBe(true)
    })

    async function pickFinanceSuite(element: string) {
      const view = bpmnView([])
      view.viewpoint.effectiveMappings = view.viewpoint.effectiveMappings.map((mapping) =>
        mapping.metatypeId === BPMN_TYPES.task ? { ...mapping, metatypeId: METATYPE_COMPONENT, notationElement: element } : mapping,
      )
      view.viewpoint.allowedMetatypes = [...view.viewpoint.allowedMetatypes, { id: METATYPE_COMPONENT, name: 'Application Component', abbreviation: 'AC' }]
      const { wrapper } = await mountCanvas(view, true, { entities: [...ENTITIES, FINANCE_SUITE] })
      await wrapper.get('[data-testid="canvas-add-existing"]').trigger('click')
      await vi.waitFor(() => expect(dialog('add-existing-15')).not.toBeNull())
      dialog('add-existing-15')!.click()
      await flushPromises()
      return wrapper
    }

    it('asks whether the children of a placed task are shown only when it is drawn as a container', async () => {
      await pickFinanceSuite('bpmn:Task')
      expect(dialog('children-dialog')).toBeNull()
    })

    it('asks whether the children of a placed pool are shown', async () => {
      await pickFinanceSuite('bpmn:Pool')
      expect(dialog('children-dialog')).not.toBeNull()
    })
  })

  describe('warnings of the pool rules', () => {
    const warned = () => [...process(), bpmnShape('loose', 'Loose', 'task', 800, 40, 90, 60)]

    it('marks a sequence flow across pools, shows the count and selects the flow with the button', async () => {
      const { wrapper } = await mountCanvas(bpmnView(warned(), [bpmnEdge('cross', 'sequenceFlow', 'check', 'deliver', 'Check order → Deliver')]))
      const edge = wrapper.get('[data-testid="edge-cross"]')
      expect(edge.attributes('data-warning')).toBe('true')
      expect(edge.get('[data-testid="edge-warning"]').text()).toContain('Sequence flow crosses a pool boundary')
      expect(edge.get('title').text()).toBe('Sequence flow crosses a pool boundary')
      expect(wrapper.get('[data-testid="canvas-bpmn-warnings"]').text()).toBe('1 BPMN warning')
      await wrapper.get('[data-testid="canvas-bpmn-warnings"]').trigger('click')
      expect(wrapper.get('[data-testid="edge-cross"]').attributes('data-selected')).toBe('true')
    })

    it('marks a sequence flow from a task in a pool to a shape without a pool', async () => {
      const { wrapper } = await mountCanvas(bpmnView(warned(), [bpmnEdge('out', 'sequenceFlow', 'check', 'loose')]))
      expect(wrapper.get('[data-testid="edge-out"]').attributes('data-warning')).toBe('true')
    })

    it('marks a message flow within one pool', async () => {
      const { wrapper } = await mountCanvas(bpmnView(warned(), [bpmnEdge('inner', 'messageFlow', 'check', 'pack')]))
      expect(wrapper.get('[data-testid="edge-inner"] [data-testid="edge-warning"]').text()).toContain('Message flow within one pool')
    })

    it.each([
      ['a sequence flow across lanes of one pool', 'sequenceFlow', 'check', 'pack'],
      ['a sequence flow in one lane', 'sequenceFlow', 'check', 'ship'],
      ['a sequence flow between shapes without a pool', 'sequenceFlow', 'loose', 'loose'],
      ['a message flow between two pools', 'messageFlow', 'customer', 'supplier'],
      ['a message flow from a pool to a task of another pool', 'messageFlow', 'customer', 'deliver'],
      ['an association across pools', 'association', 'check', 'deliver'],
    ] as const)('does not mark %s', async (_name, type, source, target) => {
      const { wrapper } = await mountCanvas(bpmnView(warned(), [bpmnEdge('flow', type, source, target)]))
      expect(wrapper.get('[data-testid="edge-flow"]').attributes('data-warning')).toBeUndefined()
      expect(wrapper.find('[data-testid="canvas-bpmn-warnings"]').exists()).toBe(false)
    })

    it('counts several warnings', async () => {
      const edges = [bpmnEdge('one', 'sequenceFlow', 'check', 'deliver'), bpmnEdge('two', 'messageFlow', 'check', 'pack'), bpmnEdge('ok', 'sequenceFlow', 'check', 'ship')]
      const { wrapper } = await mountCanvas(bpmnView(warned(), edges))
      expect(wrapper.get('[data-testid="canvas-bpmn-warnings"]').text()).toBe('2 BPMN warnings')
    })

    it('shows no warnings in the other notations', async () => {
      const { wrapper } = await mountCanvas(bpmnView(warned(), [bpmnEdge('cross', 'sequenceFlow', 'check', 'deliver')], 'generic'))
      expect(wrapper.find('[data-testid="canvas-bpmn-warnings"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="edge-cross"]').attributes('data-warning')).toBeUndefined()
    })

    it('shows the warnings on a read-only canvas too', async () => {
      const { wrapper } = await mountCanvas(bpmnView(warned(), [bpmnEdge('cross', 'sequenceFlow', 'check', 'deliver')]), false)
      expect(wrapper.get('[data-testid="canvas-bpmn-warnings"]').text()).toBe('1 BPMN warning')
    })

    it('drops the warning when the target task is moved into the pool of its source', async () => {
      const { wrapper } = await mountCanvas(bpmnView(warned(), [bpmnEdge('cross', 'sequenceFlow', 'check', 'deliver')]))
      // the center of "Deliver" moves to 345, 150 in the lane "Sales" of "Customer"
      await drop(wrapper, 'deliver', 300, 120)
      await click('new-parent-yes')
      expect(wrapper.find('[data-testid="canvas-bpmn-warnings"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="edge-cross"]').attributes('data-warning')).toBeUndefined()
    })

    it('saves with warnings and sends none of them', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(warned(), [bpmnEdge('cross', 'sequenceFlow', 'check', 'deliver')]))
      // an own move that leaves the target in its pool
      await drop(wrapper, 'deliver', 110, 550)
      await save(wrapper)
      const request = fetchFn.state.saves.at(-1)!
      expect(request.edges).toHaveLength(1)
      expect(JSON.stringify(request)).not.toMatch(/warning/i)
      expect(wrapper.find('[data-testid="canvas-failure"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="canvas-rejected"]').exists()).toBe(false)
    })
  })
})
