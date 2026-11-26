import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CANVAS_LIMITS, type DiagramEntityChild, type DiagramHierarchyLine, type DiagramShape } from '../../api/diagramView'
import { session } from '../../session/store'
import {
  diagramApi,
  diagramView,
  ENTITIES,
  financeChildren,
  FINANCE_SUITE,
  METATYPE_COMPONENT,
  METATYPE_FLOW,
  ME_DIAGRAM_READER,
  ME_DIAGRAM_WRITER,
  shape,
  type DiagramApiOptions,
} from '../../test/diagrams'
import { testRouter } from '../../test/router'
import { ptToPx } from './canvasModel'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityShape from './EntityShape.vue'

const SUITE = 'd1000000-0000-4000-8000-000000000015'
const BILLING = 'd1000000-0000-4000-8000-000000000016'
const ERP = 'd1000000-0000-4000-8000-000000000012'

const entity = (id: number, name: string, childCount = 0, parentEntityId: number | null = null) => ({ id, name, metatypeId: METATYPE_COMPONENT, childCount, parentEntityId })

/** "Finance Suite" holds "Billing" (a child of it); "SAP ERP" lies beside. Positions in pt. */
const NESTED: DiagramShape[] = [
  shape({ id: SUITE, entity: entity(15, 'Finance Suite', 2), x: 0, y: 0, width: 400, height: 200 }),
  shape({ id: BILLING, entity: entity(16, 'Billing', 0, 15), parentShapeId: SUITE, x: 10, y: 30, width: 120, height: 40 }),
  shape({ id: ERP, entity: entity(12, 'SAP ERP'), x: 600, y: 50, width: 120, height: 40 }),
]

const mounted: VueWrapper[] = []

async function mountCanvas(shapes: DiagramShape[] = NESTED, options: Partial<DiagramApiOptions> = {}, editable = true, lines: DiagramHierarchyLine[] = []) {
  const fetchFn = diagramApi({ me: editable ? ME_DIAGRAM_WRITER : ME_DIAGRAM_READER, shapes, lines, entities: [...ENTITIES, FINANCE_SUITE], ...options })
  const view = diagramView(shapes, {}, [], lines, options.askNewParent ?? true)
  const wrapper = mount(DiagramCanvas, {
    props: { view, version: view.diagram.version, editable, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, fetchFn, view }
}

const px = ptToPx

/** The canvas reports where a shape was dropped: its absolute position in pt. */
async function drop(wrapper: VueWrapper, id: string, x: number, y: number) {
  wrapper.findComponent(VueFlow).vm.$emit('node-drag-stop', { node: { id, position: { x: px(x), y: px(y) }, computedPosition: { x: px(x), y: px(y) } } })
  await flushPromises()
}

async function save(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="canvas-save"]').trigger('click')
  await flushPromises()
}

function lastSave(fetchFn: ReturnType<typeof diagramApi>) {
  return fetchFn.state.saves.at(-1)!
}

const dialog = (testId: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)

async function click(testId: string) {
  await vi.waitFor(() => expect(dialog(testId)).not.toBeNull())
  dialog(testId)!.click()
  await flushPromises()
}

async function pickEntity(wrapper: VueWrapper, id: number) {
  await wrapper.get('[data-testid="canvas-add-existing"]').trigger('click')
  await vi.waitFor(() => expect(dialog(`add-existing-${id}`)).not.toBeNull())
  dialog(`add-existing-${id}`)!.click()
  await flushPromises()
}

async function contextMenu(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id } })
  await flushPromises()
}

const names = (wrapper: VueWrapper) => wrapper.findAllComponents(EntityShape).map((node) => node.props('data').name)

async function paletteDrop(wrapper: VueWrapper, x: number, y: number) {
  const surface = wrapper.get('.vue-flow').element
  const event = new Event('drop', { bubbles: true, cancelable: true })
  Object.assign(event, { clientX: x, clientY: y, dataTransfer: { types: ['application/x-oea-metatype'], getData: () => METATYPE_COMPONENT } })
  surface.dispatchEvent(event)
  await flushPromises()
}

async function nameNew(wrapper: VueWrapper, name: string) {
  const input = wrapper.get('[data-testid="shape-name-input"]')
  await input.setValue(name)
  await input.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

// spec diagram/diagram-nesting (SCR-021): containers, the question "Show children", hierarchy lines, "New parent?" and new
// elements in containers (design D4 of us-074-diagram-nesting)
describe('DiagramCanvas nesting', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('nested shapes', () => {
    it('draws a nested shape in its container with the position relative to it, and the container as such', async () => {
      const { wrapper } = await mountCanvas()
      const nodes = wrapper.findComponent(VueFlow).props('nodes') as { id: string; parentNode?: string; position: { x: number; y: number } }[]
      const billing = nodes.find((node) => node.id === BILLING)!
      expect(billing.parentNode).toBe(SUITE)
      expect(billing.position).toEqual({ x: px(10), y: px(30) })
      expect(nodes.find((node) => node.id === SUITE)!.parentNode).toBeUndefined()
      expect(wrapper.get(`[data-testid="shape-${SUITE}"]`).attributes('data-container')).toBe('true')
      expect(wrapper.get(`[data-testid="shape-${BILLING}"]`).attributes('data-container')).toBeUndefined()
    })

    it('saves the absolute positions, the container and keeps the order of the drawing', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await drop(wrapper, SUITE, 50, 20)
      await save(wrapper)
      // the container moved by 50 and 20 pt, and what is nested in it with it; nothing else changed
      expect(lastSave(fetchFn).shapes.map((entry) => [entry.id, entry.x, entry.y, entry.parentShapeId])).toEqual([
        [SUITE, 50, 20, undefined],
        [BILLING, 60, 50, SUITE],
        [ERP, 600, 50, undefined],
      ])
      expect(lastSave(fetchFn).parentChanges).toBeUndefined()
    })

    it('grows the container when a shape is moved beyond its edge and keeps its center inside', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      // 330 + 120 is 450: 50 pt beyond the right edge of 400, the center at 390 stays inside
      await drop(wrapper, BILLING, 330, 30)
      await save(wrapper)
      expect(lastSave(fetchFn).shapes[0]).toMatchObject({ id: SUITE, x: 0, y: 0, width: 450, height: 200 })
      expect(lastSave(fetchFn).shapes[1]).toMatchObject({ id: BILLING, x: 330, parentShapeId: SUITE })
    })

    it('never shrinks a container below what is nested in it when it is resized', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      const container = wrapper.findAllComponents(EntityShape).find((node) => node.props('id') === SUITE)!
      container.props('data').resized({ x: 0, y: 0, width: px(50), height: px(20) })
      await flushPromises()
      await save(wrapper)
      // the box holds "Billing" at 10..130 and 30..70 again
      expect(lastSave(fetchFn).shapes[0]).toMatchObject({ x: 0, y: 0, width: 130, height: 70 })
    })

    it('moves a nested shape with the arrow keys and grows its container', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      wrapper.get(`[data-testid="shape-${BILLING}"]`).element.closest('.vue-flow__node')!.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
      await flushPromises()
      for (let index = 0; index < 28; index += 1) {
        await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key: 'ArrowRight', shiftKey: true })
      }
      await save(wrapper)
      // 10 + 280 + 120 = 410: 10 pt more than the container
      expect(lastSave(fetchFn).shapes[0]).toMatchObject({ width: 410 })
    })
  })

  describe('children of a placed entity', () => {
    it('asks whether the children are shown and nests them in a grid when the answer is "Show children"', async () => {
      const { wrapper, fetchFn } = await mountCanvas([])
      await pickEntity(wrapper, 15)
      expect(dialog('children-dialog')).not.toBeNull()
      expect(document.body.textContent).toContain('Finance Suite has 2 children')
      await click('children-show')
      expect(names(wrapper)).toEqual(['Finance Suite', 'Billing', 'Invoicing'])
      await save(wrapper)
      const [suite, billing, invoicing] = lastSave(fetchFn).shapes
      expect(billing).toMatchObject({ entityId: 16, parentShapeId: suite!.id, width: 120, height: 40 })
      expect(invoicing).toMatchObject({ entityId: 17, parentShapeId: suite!.id })
      // 2 children in 2 columns of 120 x 40 pt, 10 pt padding and gap, 24 pt for the name: the container holds both
      expect(suite!.width).toBeGreaterThanOrEqual(10 + 120 + 10 + 120)
      expect(suite!.height).toBeGreaterThanOrEqual(24 + 40)
      for (const child of [billing!, invoicing!]) {
        expect(child.x).toBeGreaterThanOrEqual(suite!.x)
        expect(child.y).toBeGreaterThanOrEqual(suite!.y)
        expect(child.x + child.width).toBeLessThanOrEqual(suite!.x + suite!.width)
        expect(child.y + child.height).toBeLessThanOrEqual(suite!.y + suite!.height)
      }
      expect(lastSave(fetchFn).parentChanges).toBeUndefined()
    })

    it('names the children whose type the viewpoint does not allow and does not place them', async () => {
      const { wrapper } = await mountCanvas([])
      await pickEntity(wrapper, 15)
      await click('children-show')
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toContain('Ledger')
      expect(names(wrapper)).not.toContain('Ledger')
    })

    it('places only the entity when the answer is "Only this entity"', async () => {
      const { wrapper } = await mountCanvas([])
      await pickEntity(wrapper, 15)
      await click('children-only')
      expect(names(wrapper)).toEqual(['Finance Suite'])
      expect(dialog('children-dialog')).toBeNull()
    })

    it('asks nothing for an entity without children', async () => {
      const { wrapper } = await mountCanvas([])
      await pickEntity(wrapper, 12)
      expect(names(wrapper)).toEqual(['SAP ERP'])
      expect(dialog('children-dialog')).toBeNull()
    })

    it('shows the children of a placed shape from its context menu, only those not shown yet', async () => {
      const { wrapper } = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 2), x: 0, y: 0, width: 400, height: 200 })])
      await contextMenu(wrapper, SUITE)
      await wrapper.get('[data-testid="canvas-menu-children"]').trigger('click')
      await flushPromises()
      expect(names(wrapper)).toEqual(['Finance Suite', 'Billing', 'Invoicing'])
      await contextMenu(wrapper, SUITE)
      await wrapper.get('[data-testid="canvas-menu-children"]').trigger('click')
      await flushPromises()
      expect(names(wrapper)).toEqual(['Finance Suite', 'Billing', 'Invoicing'])
    })

    it('offers "Show children" only for a shape with children', async () => {
      const { wrapper } = await mountCanvas()
      await contextMenu(wrapper, ERP)
      expect(wrapper.find('[data-testid="canvas-menu-children"]').exists()).toBe(false)
      await contextMenu(wrapper, SUITE)
      expect(wrapper.find('[data-testid="canvas-menu-children"]').exists()).toBe(true)
    })

    it('nests no second shape of a child that is shown elsewhere and draws a hierarchy line to it', async () => {
      const elsewhere = shape({ id: 'd1000000-0000-4000-8000-0000000000aa', entity: entity(16, 'Billing', 0, 15), x: 600, y: 300, width: 120, height: 40 })
      const { wrapper, fetchFn } = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 2), x: 0, y: 0, width: 400, height: 200 }), elsewhere])
      await contextMenu(wrapper, SUITE)
      await wrapper.get('[data-testid="canvas-menu-children"]').trigger('click')
      await flushPromises()
      // "Invoicing" is nested, "Billing" stays where it is: one shape of it
      expect([...names(wrapper)].sort()).toEqual(['Billing', 'Finance Suite', 'Invoicing'])
      const lines = wrapper.findAll('[data-testid="canvas-edges"] g').filter((node) => node.find('polygon').exists() === false)
      expect(lines).toHaveLength(1)
      expect(lines[0]!.find('path').attributes('stroke-dasharray')).toBeDefined()
      await save(wrapper)
      expect(lastSave(fetchFn).hierarchyLines).toEqual([{ id: expect.any(String), parentShapeId: SUITE, childShapeId: elsewhere.id }])
      expect(lastSave(fetchFn).edges).toEqual([])
    })

    it('shows the composition connection of the pair as an edge instead of a line where the viewpoint allows its type', async () => {
      const children: DiagramEntityChild[] = [
        { entity: entity(16, 'Billing', 0, 15), allowed: true, compositionConnection: { connection: { id: 40, name: 'Finance Suite → Billing', metatypeId: METATYPE_FLOW }, allowed: true } },
      ]
      const elsewhere = shape({ id: 'd1000000-0000-4000-8000-0000000000aa', entity: entity(16, 'Billing', 0, 15), x: 600, y: 300, width: 120, height: 40 })
      const { wrapper, fetchFn } = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 1), x: 0, y: 0, width: 400, height: 200 }), elsewhere], { children: { 15: children } })
      await contextMenu(wrapper, SUITE)
      await wrapper.get('[data-testid="canvas-menu-children"]').trigger('click')
      await flushPromises()
      await save(wrapper)
      expect(lastSave(fetchFn).hierarchyLines).toBeUndefined()
      expect(lastSave(fetchFn).edges).toEqual([expect.objectContaining({ connectionId: 40, sourceShapeId: SUITE, targetShapeId: elsewhere.id })])
    })

    it('draws a connection of a type the viewpoint does not allow as a hierarchy line', async () => {
      const children: DiagramEntityChild[] = [
        { entity: entity(16, 'Billing', 0, 15), allowed: true, compositionConnection: { connection: { id: 40, name: 'Finance Suite → Billing', metatypeId: METATYPE_FLOW }, allowed: false } },
      ]
      const elsewhere = shape({ id: 'd1000000-0000-4000-8000-0000000000aa', entity: entity(16, 'Billing', 0, 15), x: 600, y: 300, width: 120, height: 40 })
      const { wrapper, fetchFn } = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 1), x: 0, y: 0, width: 400, height: 200 }), elsewhere], { children: { 15: children } })
      await contextMenu(wrapper, SUITE)
      await wrapper.get('[data-testid="canvas-menu-children"]').trigger('click')
      await flushPromises()
      await save(wrapper)
      expect(lastSave(fetchFn).hierarchyLines).toHaveLength(1)
      expect(lastSave(fetchFn).edges).toEqual([])
    })

    it('says when only the first 500 children were considered and when the children could not be loaded', async () => {
      const api = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 2), x: 0, y: 0, width: 400, height: 200 })], { rejectChildren: 500 })
      await contextMenu(api.wrapper, SUITE)
      await api.wrapper.get('[data-testid="canvas-menu-children"]').trigger('click')
      await flushPromises()
      expect(api.wrapper.get('[data-testid="canvas-notice"]').text()).toContain('could not be loaded')
    })

    it('shows a hierarchy line of the view, selects and removes it without touching the shapes', async () => {
      const lines: DiagramHierarchyLine[] = [{ id: 'a0000000-0000-4000-8000-000000000001', parentShapeId: SUITE, childShapeId: ERP }]
      const { wrapper, fetchFn } = await mountCanvas(NESTED, {}, true, lines)
      const hit = wrapper.get('[data-testid="edge-hit-a0000000-0000-4000-8000-000000000001"]')
      expect(hit.attributes('aria-label')).toBe('Hierarchy line Finance Suite → SAP ERP')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
      await hit.trigger('click')
      await wrapper.get('[data-testid="canvas-remove"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="edge-hit-a0000000-0000-4000-8000-000000000001"]').exists()).toBe(false)
      await save(wrapper)
      expect(lastSave(fetchFn).hierarchyLines).toBeUndefined()
      expect(lastSave(fetchFn).shapes).toHaveLength(3)
    })
  })

  describe('"New parent?"', () => {
    it('asks when a shape is moved into a container and saves the parent change after "Yes"', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await drop(wrapper, ERP, 200, 100)
      expect(dialog('new-parent-dialog')).not.toBeNull()
      expect(document.body.textContent).toContain('Make Finance Suite the parent of SAP ERP?')
      await click('new-parent-yes')
      await save(wrapper)
      expect(lastSave(fetchFn).parentChanges).toEqual([{ entityId: 12, expectedVersion: 4, parentEntityId: 15 }])
      expect(lastSave(fetchFn).shapes.find((entry) => entry.id === ERP)).toMatchObject({ parentShapeId: SUITE, x: 200, y: 100 })
      expect(fetchFn.state.entities[12]!.parentEntityId).toBe(15)
    })

    it('changes only the picture after "No" and after closing the dialog', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await drop(wrapper, ERP, 200, 100)
      await click('new-parent-no')
      await save(wrapper)
      expect(lastSave(fetchFn).parentChanges).toBeUndefined()
      expect(lastSave(fetchFn).shapes.find((entry) => entry.id === ERP)).toMatchObject({ parentShapeId: SUITE })
      expect(fetchFn.state.entities[12]!.parentEntityId).toBeUndefined()
    })

    it('asks when a shape is moved out of its container and saves that the entity has no parent', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await drop(wrapper, BILLING, 700, 400)
      expect(document.body.textContent).toContain('Remove the parent of Billing')
      await click('new-parent-yes')
      await save(wrapper)
      expect(lastSave(fetchFn).parentChanges).toEqual([{ entityId: 16, expectedVersion: 3 }])
      const billing = lastSave(fetchFn).shapes.find((entry) => entry.id === BILLING)!
      expect(billing.parentShapeId).toBeUndefined()
      // the container did not grow towards the shape that left
      expect(lastSave(fetchFn).shapes[0]).toMatchObject({ width: 400, height: 200 })
    })

    it('asks nothing when the setting does not ask and still nests the shape', async () => {
      const { wrapper, fetchFn } = await mountCanvas(NESTED, { askNewParent: false })
      await drop(wrapper, ERP, 200, 100)
      expect(dialog('new-parent-dialog')).toBeNull()
      await save(wrapper)
      expect(lastSave(fetchFn).parentChanges).toBeUndefined()
      expect(lastSave(fetchFn).shapes.find((entry) => entry.id === ERP)).toMatchObject({ parentShapeId: SUITE })
    })

    it('asks nothing when a shape moves within its container or into the container of its parent', async () => {
      const { wrapper } = await mountCanvas()
      await drop(wrapper, BILLING, 100, 100)
      expect(dialog('new-parent-dialog')).toBeNull()
      // out and back into "Finance Suite", the entity of its parent: the second move asks nothing
      await drop(wrapper, BILLING, 700, 400)
      await click('new-parent-no')
      await drop(wrapper, BILLING, 100, 100)
      expect(dialog('new-parent-dialog')).toBeNull()
    })

    it('takes the parent change back when the shape returns to the parent the entity had', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await drop(wrapper, BILLING, 700, 400)
      await click('new-parent-yes')
      await drop(wrapper, BILLING, 100, 100)
      expect(document.body.textContent).toContain('Make Finance Suite the parent of Billing?')
      await click('new-parent-yes')
      await save(wrapper)
      expect(lastSave(fetchFn).parentChanges).toBeUndefined()
    })

    it('refuses to nest deeper than 10 levels', async () => {
      const chain = Array.from({ length: CANVAS_LIMITS.nesting }, (_, index) =>
        shape({
          id: `d1000000-0000-4000-8000-0000000001${String(index).padStart(2, '0')}`,
          entity: entity(100 + index, `Level ${index + 1}`),
          ...(index > 0 ? { parentShapeId: `d1000000-0000-4000-8000-0000000001${String(index - 1).padStart(2, '0')}` } : {}),
          x: index * 5,
          y: index * 5,
          width: 1000 - index * 10,
          height: 1000 - index * 10,
        }),
      )
      const extra = shape({ id: ERP, entity: entity(12, 'SAP ERP'), x: 2000, y: 2000, width: 40, height: 20 })
      const { wrapper, fetchFn } = await mountCanvas([...chain, extra])
      // the center lies in the shape of level 10
      await drop(wrapper, ERP, 60, 60)
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toContain('at most 10 levels')
      expect(dialog('new-parent-dialog')).toBeNull()
      await save(wrapper)
      expect(lastSave(fetchFn).shapes.find((entry) => entry.id === ERP)?.parentShapeId).toBeUndefined()
    })

    it('shows the entity of a new shape the question too and keeps its parent in the new entity', async () => {
      const { wrapper, fetchFn } = await mountCanvas([NESTED[0]!])
      await wrapper.get(`[data-testid="palette-${METATYPE_COMPONENT}"]`).trigger('click')
      await nameNew(wrapper, 'Payments')
      const created = wrapper.findAllComponents(EntityShape).find((node) => node.props('data').name === 'Payments')!
      await drop(wrapper, created.props('id') as string, 200, 100)
      expect(document.body.textContent).toContain('Make Finance Suite the parent of Payments?')
      await click('new-parent-yes')
      await save(wrapper)
      expect(lastSave(fetchFn).newEntities?.[0]).toMatchObject({ name: 'Payments', parentEntityId: 15 })
      expect(lastSave(fetchFn).parentChanges).toBeUndefined()
    })

    it('shows the 409 of a stale parent change with the entity and discards the change with "Reload"', async () => {
      const { wrapper, fetchFn } = await mountCanvas()
      await drop(wrapper, ERP, 200, 100)
      await click('new-parent-yes')
      fetchFn.state.entities[12]!.version += 1
      await save(wrapper)
      expect(wrapper.get('[data-testid="canvas-entity-conflict"]').text()).toContain('SAP ERP')
      await wrapper.get('[data-testid="canvas-entity-reload-12"]').trigger('click')
      await flushPromises()
      await save(wrapper)
      expect(lastSave(fetchFn).parentChanges).toBeUndefined()
    })

    it('marks the shape a 422 of a parent change names', async () => {
      const { wrapper } = await mountCanvas(NESTED, {
        rejectSave: { status: 422, errors: [{ field: 'parentChanges[0].parentEntityId', code: 'PARENT_INVALID', message: 'The parent is the entity itself or one of its descendants.' }] },
      })
      await drop(wrapper, ERP, 200, 100)
      await click('new-parent-yes')
      await save(wrapper)
      expect(wrapper.find('[data-testid="canvas-rejected"]').exists()).toBe(true)
      expect(wrapper.get(`[data-testid="shape-${ERP}"] [data-testid="shape-error"]`).text()).toContain('descendants')
    })

    it('shows the unsaved parent change as unsaved changes', async () => {
      const { wrapper } = await mountCanvas()
      await drop(wrapper, ERP, 200, 100)
      await click('new-parent-yes')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    })
  })

  describe('new element in a container', () => {
    it('nests a new element dropped from the palette into a container and creates it as a child of its entity', async () => {
      const { wrapper, fetchFn } = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 2), x: 200, y: 100, width: 300, height: 200 })])
      // 400 px and 200 px are 300 pt and 150 pt: inside the container
      await paletteDrop(wrapper, 400, 200)
      await nameNew(wrapper, 'Payments')
      await save(wrapper)
      expect(lastSave(fetchFn).shapes[1]).toMatchObject({ parentShapeId: SUITE })
      expect(lastSave(fetchFn).newEntities).toEqual([expect.objectContaining({ name: 'Payments', parentEntityId: 15 })])
      expect(fetchFn.state.entities[Object.keys(fetchFn.state.entities).map(Number).at(-1)!]).toMatchObject({ name: 'Payments', parentEntityId: 15 })
    })

    it('asks nothing for a new element dropped into a container, whatever the setting says', async () => {
      const { wrapper } = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 2), x: 200, y: 100, width: 300, height: 200 })], { askNewParent: true })
      await paletteDrop(wrapper, 400, 200)
      await nameNew(wrapper, 'Payments')
      expect(dialog('new-parent-dialog')).toBeNull()
    })

    it('creates a new element dropped onto a new container as a child of the new entity', async () => {
      const { wrapper, fetchFn } = await mountCanvas([])
      await wrapper.get(`[data-testid="palette-${METATYPE_COMPONENT}"]`).trigger('click')
      await nameNew(wrapper, 'Shop')
      // the new container is at the center of the canvas, its center is 0, 0
      await paletteDrop(wrapper, 0, 0)
      await nameNew(wrapper, 'Cart')
      await save(wrapper)
      const request = lastSave(fetchFn)
      const shop = request.newEntities!.find((entry) => entry.name === 'Shop')!
      const cart = request.newEntities!.find((entry) => entry.name === 'Cart')!
      expect(cart.parentNewEntityKey).toBe(shop.key)
      expect(cart.parentEntityId).toBeUndefined()
      expect(shop.parentNewEntityKey).toBeUndefined()
      const shopShape = request.shapes.find((entry) => entry.newEntityKey === shop.key)!
      expect(request.shapes.find((entry) => entry.newEntityKey === cart.key)?.parentShapeId).toBe(shopShape.id)
    })

    it('nests a dropped element in no container outside every shape', async () => {
      const { wrapper, fetchFn } = await mountCanvas([shape({ id: SUITE, entity: entity(15, 'Finance Suite', 2), x: 200, y: 100, width: 300, height: 200 })])
      await paletteDrop(wrapper, 4000, 2000)
      await nameNew(wrapper, 'Payments')
      await save(wrapper)
      expect(lastSave(fetchFn).shapes[1]!.parentShapeId).toBeUndefined()
      expect(lastSave(fetchFn).newEntities?.[0]?.parentEntityId).toBeUndefined()
    })
  })

  describe('removing a container', () => {
    it('asks for confirmation naming the number of nested shapes and removes them with the container', async () => {
      const second = shape({ id: 'd1000000-0000-4000-8000-000000000017', entity: entity(17, 'Invoicing', 0, 15), parentShapeId: SUITE, x: 150, y: 30, width: 120, height: 40 })
      const { wrapper, fetchFn } = await mountCanvas([NESTED[0]!, NESTED[1]!, second, NESTED[2]!])
      await contextMenu(wrapper, SUITE)
      await wrapper.get('[data-testid="canvas-menu-remove"]').trigger('click')
      await flushPromises()
      expect(document.body.textContent).toContain('Finance Suite contains 2 shapes')
      await click('remove-container-confirm')
      expect(names(wrapper)).toEqual(['SAP ERP'])
      await save(wrapper)
      expect(lastSave(fetchFn).shapes.map((entry) => entry.id)).toEqual([ERP])
    })

    it('keeps everything when the confirmation is cancelled', async () => {
      const { wrapper } = await mountCanvas()
      await contextMenu(wrapper, SUITE)
      await wrapper.get('[data-testid="canvas-menu-remove"]').trigger('click')
      await flushPromises()
      await click('remove-container-cancel')
      expect(names(wrapper)).toEqual(['Finance Suite', 'Billing', 'SAP ERP'])
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('removes a shape without nested shapes at once and also the lines of a removed container', async () => {
      const lines: DiagramHierarchyLine[] = [{ id: 'a0000000-0000-4000-8000-000000000001', parentShapeId: SUITE, childShapeId: ERP }]
      const { wrapper, fetchFn } = await mountCanvas(NESTED, {}, true, lines)
      await contextMenu(wrapper, ERP)
      await wrapper.get('[data-testid="canvas-menu-remove"]').trigger('click')
      await flushPromises()
      expect(dialog('remove-container-dialog')).toBeNull()
      await save(wrapper)
      expect(lastSave(fetchFn).shapes.map((entry) => entry.id)).toEqual([SUITE, BILLING])
      expect(lastSave(fetchFn).hierarchyLines).toBeUndefined()
    })
  })

  describe('read-only canvas', () => {
    it('offers neither the questions nor "Show children" and lets nothing be moved', async () => {
      const { wrapper } = await mountCanvas(NESTED, {}, false)
      expect(wrapper.find('[data-testid="canvas-save"]').exists()).toBe(false)
      await contextMenu(wrapper, SUITE)
      expect(wrapper.find('[data-testid="canvas-menu-children"]').exists()).toBe(false)
      expect(wrapper.findComponent(VueFlow).props('nodesDraggable')).toBe(false)
      expect(wrapper.find('[data-testid="canvas-read-only"]').exists()).toBe(true)
    })
  })

  it('keeps the fake children of the fixture: two allowed and one not allowed', () => {
    expect(financeChildren().map((child) => child.allowed)).toEqual([true, true, false])
  })
})
