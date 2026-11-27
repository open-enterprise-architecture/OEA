import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DiagramShape, DiagramView, DiagramViewWrite } from '../../api/diagramView'
import { session } from '../../session/store'
import { BPMN_TYPES, bpmnEdge, bpmnShape, bpmnView } from '../../test/bpmn'
import { diagramApi, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER } from '../../test/diagrams'
import { testRouter } from '../../test/router'
import { ptToPx } from './canvasModel'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityShape from './EntityShape.vue'

// specs diagram/bpmn-pools-and-lanes at the canvas (SCR-021; us-074-bpmn-pools-and-lanes D1 to D6): stacked and vertical
// lanes, the icons "Insert lane" and collapsed sub-processes

const mounted: VueWrapper[] = []

async function mountCanvas(view: DiagramView, editable = true) {
  const fetchFn = diagramApi({ me: editable ? ME_DIAGRAM_WRITER : ME_DIAGRAM_READER, shapes: view.shapes, edges: view.edges })
  const wrapper = mount(DiagramCanvas, {
    props: { view, version: view.diagram.version, editable, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, fetchFn }
}

const data = (wrapper: VueWrapper, id: string) => wrapper.findAllComponents(EntityShape).find((entry) => entry.props('id') === id)!.props('data')
const style = (wrapper: VueWrapper, id: string) => wrapper.get(`[data-id="${id}"]`).attributes('style') ?? ''
const px = (pt: number) => `${Math.round(ptToPx(pt) * 1000) / 1000}px`

async function click(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-click', { node: { id } })
  await flushPromises()
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

async function contextMenu(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id } })
  await flushPromises()
}

const written = (request: DiagramViewWrite, id: string) => request.shapes.find((entry) => entry.id === id)!

// the pool "Customer" of 600 x 200 with the lanes "Sales" and "Shipping" of 100 pt and a task in each
function process(vertical = false): DiagramShape[] {
  const shapes = [
    bpmnShape('customer', 'Customer', 'pool', 0, 0, 600, 200),
    bpmnShape('sales', 'Sales', 'lane', 30, 0, 570, 100, 'customer'),
    bpmnShape('shipping', 'Shipping', 'lane', 30, 100, 570, 100, 'customer'),
    bpmnShape('check', 'Check order', 'task', 100, 20, 60, 60, 'sales'),
    bpmnShape('ship', 'Ship order', 'task', 100, 120, 60, 60, 'shipping'),
  ]
  return vertical
    ? shapes.map((entry) => ({ ...entry, x: entry.y, y: entry.x, width: entry.height, height: entry.width, ...(entry.id === 'customer' ? { orientation: 'vertical' as const } : {}) }))
    : shapes
}

describe('DiagramCanvas lanes', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('stacking', () => {
    it('stacks lanes that lie unstacked when the diagram loads, without an unsaved change', async () => {
      const view = bpmnView([
        bpmnShape('customer', 'Customer', 'pool', 0, 0, 700, 400),
        bpmnShape('sales', 'Sales', 'lane', 30, 10, 660, 180, 'customer'),
        bpmnShape('shipping', 'Shipping', 'lane', 30, 200, 660, 190, 'customer'),
      ])
      const { wrapper } = await mountCanvas(view)
      expect(style(wrapper, 'sales')).toContain(`height: ${px(180)}`)
      expect(style(wrapper, 'shipping')).toContain(`height: ${px(190)}`)
      expect(style(wrapper, 'customer')).toContain(`height: ${px(370)}`)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="canvas-save"]').attributes('disabled')).toBeDefined()
    })

    it.each([
      ['the lower half of "Sales"', 200, 80, ['sales', 'new', 'shipping'], 300],
      ['the upper half of "Shipping"', 200, 120, ['sales', 'new', 'shipping'], 300],
      ['the upper half of "Sales"', 200, 20, ['new', 'sales', 'shipping'], 300],
      ['the lower half of "Shipping"', 200, 180, ['sales', 'shipping', 'new'], 300],
    ])('a new lane dropped on %s lies between the lanes, and the pool grows by its height', async (_where, x, y, order, height) => {
      const view = bpmnView(process())
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.lane, x, y)
      await nameNew(wrapper, 'Billing')
      const request = await save(wrapper, fetchFn)
      const key = request.newEntities![0]!.key
      const newShape = request.shapes.find((entry) => entry.newEntityKey === key)!
      const lanes = ['sales', 'shipping', 'new']
        .map((id) => (id === 'new' ? newShape : written(request, id)))
        .sort((a, b) => a.y - b.y)
        .map((entry) => (entry === newShape ? 'new' : entry.id))
      expect(lanes).toEqual(order)
      expect(newShape).toMatchObject({ parentShapeId: 'customer', x: 30, width: 570, height: 100 })
      expect(written(request, 'customer')).toMatchObject({ height })
      // the lanes are one below the other without a gap
      const sorted = [written(request, 'sales'), written(request, 'shipping'), newShape].sort((a, b) => a.y - b.y)
      expect(sorted[1]!.y).toBe(sorted[0]!.y + sorted[0]!.height)
      expect(sorted[2]!.y).toBe(sorted[1]!.y + sorted[1]!.height)
      expect(request.newEntities![0]).toMatchObject({ parentEntityId: expect.any(Number) })
    })

    it('a lane dropped on the label band of a lane is nested in it', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await paletteDrop(wrapper, BPMN_TYPES.lane, 40, 50)
      await nameNew(wrapper, 'Inner')
      const request = await save(wrapper, fetchFn)
      const inner = request.shapes.find((entry) => entry.newEntityKey === request.newEntities![0]!.key)!
      expect(inner).toMatchObject({ parentShapeId: 'sales', x: 60, width: 540 })
      expect(written(request, 'sales')).toMatchObject({ height: 100 })
    })

    it('drops the first lane into a pool with the height of the pool', async () => {
      const view = bpmnView([bpmnShape('customer', 'Customer', 'pool', 0, 0, 600, 200)])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.lane, 300, 100)
      await nameNew(wrapper, 'Sales')
      const request = await save(wrapper, fetchFn)
      const lane = request.shapes.find((entry) => entry.newEntityKey)!
      expect(lane).toMatchObject({ x: 30, y: 0, width: 570, height: 200 })
      expect(written(request, 'customer')).toMatchObject({ height: 200 })
    })

    it('reorders a lane by dragging without asking "New parent?", each lane keeping its task', async () => {
      const view = { ...bpmnView(process()), askNewParent: true }
      const { wrapper, fetchFn } = await mountCanvas(view)
      // "Shipping" is dragged by its center onto the upper half of "Sales"
      wrapper.findComponent(VueFlow).vm.$emit('node-drag-stop', {
        node: { id: 'shipping', position: { x: ptToPx(30), y: ptToPx(-20) }, computedPosition: { x: ptToPx(30), y: ptToPx(-20) } },
      })
      await flushPromises()
      expect(document.body.querySelector('[data-testid="new-parent-yes"]')).toBeNull()
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'shipping')).toMatchObject({ y: 0, parentShapeId: 'customer' })
      expect(written(request, 'sales')).toMatchObject({ y: 100 })
      expect(written(request, 'ship')).toMatchObject({ y: 20, parentShapeId: 'shipping' })
      expect(written(request, 'check')).toMatchObject({ y: 120, parentShapeId: 'sales' })
    })

    it('stacks the lanes again when a lane is moved with the keyboard', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      await click(wrapper, 'sales')
      await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowDown', shiftKey: true })
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('a lane resized at its lower edge shifts the lanes below with their content and the pool follows', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      data(wrapper, 'sales').resized({ x: ptToPx(30), y: ptToPx(0), width: ptToPx(570), height: ptToPx(140) })
      await flushPromises()
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'sales')).toMatchObject({ height: 140 })
      expect(written(request, 'shipping')).toMatchObject({ y: 140 })
      expect(written(request, 'ship')).toMatchObject({ y: 160 })
      expect(written(request, 'customer')).toMatchObject({ height: 240 })
    })

    it('a lane is resized at its lower edge only, a pool at its sides and its lower edge', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      await click(wrapper, 'sales')
      expect(wrapper.findAll('[data-testid^="resize-"]').map((entry) => entry.attributes('data-testid'))).toEqual(['resize-bottom'])
      await click(wrapper, 'customer')
      expect(wrapper.findAll('[data-testid^="resize-"]').map((entry) => entry.attributes('data-testid'))).toEqual(['resize-left', 'resize-right', 'resize-bottom'])
    })

    it('a pool made wider makes every lane wider', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      data(wrapper, 'customer').resized({ x: 0, y: 0, width: ptToPx(700), height: ptToPx(200) })
      await flushPromises()
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'customer')).toMatchObject({ width: 700 })
      expect(written(request, 'sales')).toMatchObject({ width: 670 })
      expect(written(request, 'shipping')).toMatchObject({ width: 670 })
    })

    it('leaves a diagram of another notation as it is: its lanes stay where they are', async () => {
      const view = bpmnView(
        [bpmnShape('customer', 'Customer', 'pool', 0, 0, 700, 400), bpmnShape('sales', 'Sales', 'lane', 30, 10, 660, 180, 'customer')],
        [],
        'archimate3',
      )
      const { wrapper, fetchFn } = await mountCanvas(view)
      await click(wrapper, 'sales')
      expect(wrapper.find('[data-testid="lane-insert-after"]').exists()).toBe(false)
      expect(style(wrapper, 'customer')).toContain(`height: ${px(400)}`)
      data(wrapper, 'sales').resized({ x: ptToPx(30), y: ptToPx(10), width: ptToPx(660), height: ptToPx(100) })
      await flushPromises()
      expect(written(await save(wrapper, fetchFn), 'sales')).toMatchObject({ y: 10, height: 100 })
    })
  })

  describe('vertical pools', () => {
    it('draws the label band at the top and the lanes side by side', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process(true)))
      expect(wrapper.get('[data-testid="shape-customer"] [data-testid="symbol-band"]').attributes('data-vertical')).toBe('true')
      expect(wrapper.get('[data-testid="shape-sales"] [data-testid="symbol-band"]').attributes('data-vertical')).toBe('true')
      expect(style(wrapper, 'shipping')).toContain(`width: ${px(100)}`)
    })

    it('switches a pool with the context menu: an unsaved change that keeps the order and the extent of each lane', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await contextMenu(wrapper, 'customer')
      expect(document.body.querySelector('[data-testid="canvas-menu-orientation"]')!.textContent).toContain('Vertical')
      ;(document.body.querySelector('[data-testid="canvas-menu-orientation"]') as HTMLElement).click()
      await flushPromises()
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'customer')).toMatchObject({ orientation: 'vertical', width: 200, height: 600 })
      expect(written(request, 'sales')).toMatchObject({ x: 0, y: 30, width: 100, height: 570 })
      expect(written(request, 'shipping')).toMatchObject({ x: 100, y: 30, width: 100 })
      expect(written(request, 'check')).toMatchObject({ x: 20, y: 100, width: 60, height: 60, parentShapeId: 'sales' })
    })

    it('a lane of a vertical pool is resized at its right edge and the lanes to its right shift', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process(true)))
      await click(wrapper, 'sales')
      expect(wrapper.findAll('[data-testid^="resize-"]').map((entry) => entry.attributes('data-testid'))).toEqual(['resize-right'])
      data(wrapper, 'sales').resized({ x: ptToPx(0), y: ptToPx(30), width: ptToPx(140), height: ptToPx(570) })
      await flushPromises()
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'sales')).toMatchObject({ width: 140 })
      expect(written(request, 'shipping')).toMatchObject({ x: 140 })
      expect(written(request, 'customer')).toMatchObject({ width: 240 })
    })

    it('drops a lane on the left or right half of a lane of a vertical pool beside it', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process(true)))
      // "Sales" lies at x 0 to 100, y 30 to 600
      await paletteDrop(wrapper, BPMN_TYPES.lane, 80, 300)
      await nameNew(wrapper, 'Billing')
      const request = await save(wrapper, fetchFn)
      const billing = request.shapes.find((entry) => entry.newEntityKey)!
      expect(billing).toMatchObject({ parentShapeId: 'customer', x: 100, y: 30, height: 570, width: 100 })
      expect(written(request, 'shipping')).toMatchObject({ x: 200 })
    })
  })

  describe('insert icons', () => {
    it.each([
      ['a horizontal pool', false, 'Insert lane above', 'Insert lane below'],
      ['a vertical pool', true, 'Insert lane left', 'Insert lane right'],
    ])('a selected lane of %s offers the icons with tooltip and label', async (_name, vertical, before, after) => {
      const { wrapper } = await mountCanvas(bpmnView(process(vertical)))
      await click(wrapper, 'sales')
      const first = wrapper.get('[data-testid="lane-insert-before"]')
      const second = wrapper.get('[data-testid="lane-insert-after"]')
      expect([first.attributes('aria-label'), second.attributes('aria-label')]).toEqual([before, after])
      expect([first.attributes('title'), second.attributes('title')]).toEqual([before, after])
    })

    it.each([
      ['below the first lane', 'sales', 'after', ['sales', 'new', 'shipping']],
      ['above the first lane', 'sales', 'before', ['new', 'sales', 'shipping']],
      ['below the last lane', 'shipping', 'after', ['sales', 'shipping', 'new']],
      ['above the last lane', 'shipping', 'before', ['sales', 'new', 'shipping']],
    ])('inserts a lane %s: the lane type, the pool as parent, the default height, name editing, stacked and unsaved', async (_name, lane, side, order) => {
      const view = bpmnView(process())
      const { wrapper, fetchFn } = await mountCanvas(view)
      await click(wrapper, lane)
      await wrapper.get(`[data-testid="lane-insert-${side}"]`).trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="shape-name-input"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      await nameNew(wrapper, 'Billing')
      const request = await save(wrapper, fetchFn)
      const created = request.newEntities![0]!
      expect(created).toMatchObject({ metatypeId: BPMN_TYPES.lane, name: 'Billing' })
      const shape = request.shapes.find((entry) => entry.newEntityKey === created.key)!
      expect(shape).toMatchObject({ parentShapeId: 'customer', height: 100, width: 570 })
      const sorted = [written(request, 'sales'), written(request, 'shipping'), shape].sort((a, b) => a.y - b.y)
      expect(sorted.map((entry) => (entry === shape ? 'new' : entry.id))).toEqual(order)
      expect(written(request, 'customer')).toMatchObject({ height: 300 })
    })

    it('inserts a lane left of a lane of a vertical pool with the default height as its width', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process(true)))
      await click(wrapper, 'shipping')
      await wrapper.get('[data-testid="lane-insert-before"]').trigger('click')
      await flushPromises()
      await nameNew(wrapper, 'Billing')
      const request = await save(wrapper, fetchFn)
      const shape = request.shapes.find((entry) => entry.newEntityKey)!
      expect(shape).toMatchObject({ x: 100, y: 30, width: 100, height: 570 })
      expect(written(request, 'shipping')).toMatchObject({ x: 200 })
    })

    it('inserts a lane in a nested lane, with the lane as parent', async () => {
      const view = bpmnView([
        bpmnShape('customer', 'Customer', 'pool', 0, 0, 600, 200),
        bpmnShape('sales', 'Sales', 'lane', 30, 0, 570, 200, 'customer'),
        bpmnShape('inner', 'Inner', 'lane', 60, 0, 540, 200, 'sales'),
      ])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await click(wrapper, 'inner')
      await wrapper.get('[data-testid="lane-insert-after"]').trigger('click')
      await flushPromises()
      await nameNew(wrapper, 'Second')
      const request = await save(wrapper, fetchFn)
      const shape = request.shapes.find((entry) => entry.newEntityKey)!
      expect(shape).toMatchObject({ parentShapeId: 'sales', x: 60, y: 200, height: 100 })
      expect(written(request, 'sales')).toMatchObject({ height: 300 })
      expect(written(request, 'customer')).toMatchObject({ height: 300 })
    })

    it('offers no icons to a reader, on a pool or a task, or while a lane is not selected', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()), false)
      await click(wrapper, 'sales')
      expect(wrapper.find('[data-testid="lane-insert-after"]').exists()).toBe(false)
      const { wrapper: editor } = await mountCanvas(bpmnView(process()))
      expect(editor.find('[data-testid="lane-insert-after"]').exists()).toBe(false)
      await click(editor, 'customer')
      expect(editor.find('[data-testid="lane-insert-after"]').exists()).toBe(false)
      await click(editor, 'check')
      expect(editor.find('[data-testid="lane-insert-after"]').exists()).toBe(false)
    })
  })

  describe('collapsed sub-processes', () => {
    function sub(): DiagramShape[] {
      return [
        bpmnShape('credit', 'Check credit', 'subProcess', 0, 0, 270, 150),
        bpmnShape('score', 'Check score', 'task', 10, 40, 90, 60, 'credit'),
        bpmnShape('income', 'Check income', 'task', 150, 40, 90, 60, 'credit'),
        bpmnShape('outside', 'Receive order', 'task', 400, 40, 90, 60),
      ]
    }
    const edges = () => [bpmnEdge('inner', 'sequenceFlow', 'score', 'income'), bpmnEdge('inbound', 'sequenceFlow', 'outside', 'score')]

    it('draws a collapsed sub-process at 90 x 60 pt with "+", hides what is inside and keeps the stored size in the save', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(sub(), edges()))
      expect(wrapper.get('[data-testid="shape-toggle"]').text()).toBe('−')
      await wrapper.get('[data-testid="shape-toggle"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="shape-toggle"]').text()).toBe('+')
      expect(style(wrapper, 'credit')).toContain(`width: ${px(90)}`)
      expect(style(wrapper, 'credit')).toContain(`height: ${px(60)}`)
      expect(wrapper.find('[data-testid="shape-score"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="shape-income"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="shape-outside"]').exists()).toBe(true)
      // the flows inside and from outside to inside are hidden
      expect(wrapper.findAll('[data-testid^="edge-"]').length).toBe(0)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'credit')).toMatchObject({ collapsed: true, width: 270, height: 150 })
      // what is inside and the hidden flows are kept
      expect(written(request, 'score')).toMatchObject({ parentShapeId: 'credit' })
      expect(request.edges).toHaveLength(2)
    })

    it('shows everything again after expanding, and a saved expanded state sends no flag', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(sub(), edges()))
      await wrapper.get('[data-testid="shape-toggle"]').trigger('click')
      await wrapper.get('[data-testid="shape-toggle"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="shape-score"]').exists()).toBe(true)
      expect(style(wrapper, 'credit')).toContain(`width: ${px(270)}`)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
      void fetchFn
    })

    it('collapses and expands with the context menu', async () => {
      const { wrapper } = await mountCanvas(bpmnView(sub(), edges()))
      await contextMenu(wrapper, 'credit')
      const item = document.body.querySelector('[data-testid="canvas-menu-collapse"]') as HTMLElement
      expect(item.textContent).toContain('Collapse')
      item.click()
      await flushPromises()
      expect(wrapper.find('[data-testid="shape-score"]').exists()).toBe(false)
      await contextMenu(wrapper, 'credit')
      expect((document.body.querySelector('[data-testid="canvas-menu-collapse"]') as HTMLElement).textContent).toContain('Expand')
    })

    it('is no drop container while collapsed: a task dropped on it lands in the container below', async () => {
      const view = bpmnView([bpmnShape('lane', 'Sales', 'lane', 0, 0, 600, 300), { ...bpmnShape('credit', 'Check credit', 'subProcess', 100, 50, 270, 150, 'lane'), collapsed: true }])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await paletteDrop(wrapper, BPMN_TYPES.task, 130, 70)
      await nameNew(wrapper, 'Task')
      const request = await save(wrapper, fetchFn)
      expect(request.shapes.find((entry) => entry.newEntityKey)!.parentShapeId).not.toBe('credit')
    })

    it('shows the collapsed state to a reader without a toggle', async () => {
      const view = bpmnView([{ ...bpmnShape('credit', 'Check credit', 'subProcess', 0, 0, 270, 150), collapsed: true }, bpmnShape('score', 'Check score', 'task', 10, 40, 90, 60, 'credit')])
      const { wrapper } = await mountCanvas(view, false)
      expect(wrapper.find('[data-testid="shape-toggle"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="shape-collapsed-marker"]').text()).toBe('+')
      expect(wrapper.find('[data-testid="shape-score"]').exists()).toBe(false)
    })

    it('expands the sub-process when a hidden conflict is selected from the list', async () => {
      const view = bpmnView([
        { ...bpmnShape('credit', 'Check credit', 'subProcess', 0, 0, 270, 150), collapsed: true },
        { ...bpmnShape('score', 'Check score', 'task', 10, 40, 90, 60, 'credit'), conflict: true },
      ])
      const { wrapper } = await mountCanvas(view)
      expect(wrapper.find('[data-testid="shape-score"]').exists()).toBe(false)
      await vi.waitFor(() => expect(document.body.querySelector('[data-testid="conflict-select-score"]')).not.toBeNull())
      ;(document.body.querySelector('[data-testid="conflict-select-score"]') as HTMLElement).click()
      await flushPromises()
      expect(wrapper.find('[data-testid="shape-score"]').exists()).toBe(true)
      expect(style(wrapper, 'credit')).toContain(`width: ${px(270)}`)
    })

    it('nests a collapsed sub-process by the center of its drawn box, and the lane grows to its stored size', async () => {
      const view = bpmnView([
        bpmnShape('lane', 'Sales', 'lane', 0, 0, 600, 100),
        { ...bpmnShape('credit', 'Check credit', 'subProcess', 700, 0, 270, 150), collapsed: true },
      ])
      const { wrapper, fetchFn } = await mountCanvas(view)
      // the drawn box of 90 x 60 pt is dropped with its center at (50, 30) in the lane; its stored size reaches to 150
      wrapper.findComponent(VueFlow).vm.$emit('node-drag-stop', {
        node: { id: 'credit', position: { x: ptToPx(5), y: ptToPx(0) }, computedPosition: { x: ptToPx(5), y: ptToPx(0) } },
      })
      await flushPromises()
      const request = await save(wrapper, fetchFn)
      expect(written(request, 'credit')).toMatchObject({ parentShapeId: 'lane', collapsed: true, width: 270, height: 150 })
      expect(written(request, 'lane')).toMatchObject({ height: 150 })
    })

    it('ignores the hidden flows in the warnings', async () => {
      const view = bpmnView(
        [
          bpmnShape('p1', 'P1', 'pool', 0, 0, 600, 200),
          bpmnShape('p2', 'P2', 'pool', 0, 300, 600, 200),
          { ...bpmnShape('credit', 'Check credit', 'subProcess', 40, 10, 270, 150, 'p1'), collapsed: true },
          bpmnShape('score', 'Check score', 'task', 50, 40, 90, 60, 'credit'),
          bpmnShape('other', 'Other', 'task', 100, 340, 90, 60, 'p2'),
        ],
        [bpmnEdge('cross', 'sequenceFlow', 'score', 'other')],
      )
      const { wrapper } = await mountCanvas(view)
      expect(wrapper.find('[data-testid="canvas-bpmn-warnings"]').exists()).toBe(false)
      await wrapper.get('[data-testid="shape-toggle"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="canvas-bpmn-warnings"]').exists()).toBe(true)
    })
  })
})
