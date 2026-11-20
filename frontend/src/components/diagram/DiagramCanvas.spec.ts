import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CANVAS_LIMITS, type DiagramShape } from '../../api/diagramView'
import { session } from '../../session/store'
import {
  diagramApi,
  diagramView,
  METATYPE_COMPONENT,
  METATYPE_PROCESS,
  ME_DIAGRAM_READER,
  ME_DIAGRAM_WRITER,
  PROP_INVESTMENT,
  PROP_OWNER,
  PROP_STATUS,
  shape,
  type DiagramApiOptions,
} from '../../test/diagrams'
import { testRouter } from '../../test/router'
import DiagramCanvas from './DiagramCanvas.vue'
import EntityShape from './EntityShape.vue'

const SHAPE = 'd1000000-0000-4000-8000-000000000001'

const mounted: VueWrapper[] = []

async function mountCanvas(shapes: DiagramShape[] = [shape()], editable = true, options: Partial<DiagramApiOptions> = {}) {
  const fetchFn = diagramApi({ me: ME_DIAGRAM_WRITER, shapes, ...options })
  const view = diagramView(shapes)
  const wrapper = mount(DiagramCanvas, {
    props: { view, version: view.diagram.version, editable, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  // the canvas library draws the shapes once it is mounted
  await flushPromises()
  return { wrapper, fetchFn, view }
}

/** The shapes the canvas draws, by their data. */
function data(wrapper: VueWrapper) {
  return wrapper.findAllComponents(EntityShape).map((node) => node.props('data'))
}

function node(wrapper: VueWrapper, id: string) {
  return wrapper.get(`[data-testid="shape-${id}"]`)
}

async function selectShape(wrapper: VueWrapper, id: string) {
  node(wrapper, id).element.closest('.vue-flow__node')!.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
  await flushPromises()
}

async function press(wrapper: VueWrapper, key: string, shiftKey = false) {
  await wrapper.get('[data-testid="canvas-surface"]').trigger('keydown', { key, shiftKey })
}

async function place(wrapper: VueWrapper, metatypeId: string, name: string) {
  await wrapper.get(`[data-testid="palette-${metatypeId}"]`).trigger('click')
  const input = wrapper.get('[data-testid="shape-name-input"]')
  await input.setValue(name)
  await input.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

function body(fetchFn: ReturnType<typeof diagramApi>) {
  return fetchFn.state.saves.at(-1)!
}

// spec diagram/diagram-canvas "Canvas editor" (SCR-021): palette, "Add existing", moving, resizing, removing and "Save"
describe('DiagramCanvas', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  it('draws the shapes with name, abbreviation and the visual hint of the viewpoint', async () => {
    const { wrapper } = await mountCanvas()
    expect(node(wrapper, SHAPE).text()).toContain('SAP ERP')
    expect(node(wrapper, SHAPE).text()).toContain('AC')
    expect((node(wrapper, SHAPE).element as HTMLElement).style.borderColor).not.toBe('')
    expect(wrapper.find('[data-testid="shape-unsaved"]').exists()).toBe(false)
  })

  it('places new entities from the palette with the default size of the viewpoint and marks them as unsaved', async () => {
    const { wrapper, fetchFn } = await mountCanvas([])
    await place(wrapper, METATYPE_COMPONENT, 'CRM Cloud')
    await place(wrapper, METATYPE_PROCESS, 'Invoice Approval')
    expect(data(wrapper).map((entry) => entry.name)).toEqual(['CRM Cloud', 'Invoice Approval'])
    expect(wrapper.findAll('[data-testid="shape-unsaved"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    // 120 x 40 pt stored for "Application Component", 90 x 30 pt for "Business Process" (no stored size)
    expect(body(fetchFn).shapes.map((entry) => [entry.width, entry.height])).toEqual([
      [120, 40],
      [90, 30],
    ])
    expect(body(fetchFn).newEntities?.map((entry) => entry.name)).toEqual(['CRM Cloud', 'Invoice Approval'])
  })

  it('places a new entity where a palette entry is dropped', async () => {
    const { wrapper, fetchFn } = await mountCanvas([])
    const surface = wrapper.get('.vue-flow').element
    const drop = new Event('drop', { bubbles: true, cancelable: true })
    Object.assign(drop, { clientX: 400, clientY: 200, dataTransfer: { types: ['application/x-oea-metatype'], getData: () => METATYPE_PROCESS } })
    surface.dispatchEvent(drop)
    await flushPromises()
    await wrapper.get('[data-testid="shape-name-input"]').setValue('Dropped')
    await wrapper.get('[data-testid="shape-name-input"]').trigger('keydown', { key: 'Enter' })
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    // the center of the shape is at the drop position: 400 px and 200 px are 300 pt and 150 pt
    expect(body(fetchFn).shapes[0]).toMatchObject({ x: 255, y: 135, width: 90, height: 30 })
  })

  it('removes a new shape again when its name stays empty', async () => {
    const { wrapper } = await mountCanvas([])
    await wrapper.get(`[data-testid="palette-${METATYPE_PROCESS}"]`).trigger('click')
    expect(data(wrapper)).toHaveLength(1)
    await wrapper.get('[data-testid="shape-name-input"]').setValue('   ')
    await wrapper.get('[data-testid="shape-name-input"]').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(data(wrapper)).toHaveLength(0)
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
  })

  it('removes a new shape when the name input is left with Escape', async () => {
    const { wrapper } = await mountCanvas([])
    await wrapper.get(`[data-testid="palette-${METATYPE_COMPONENT}"]`).trigger('click')
    await wrapper.get('[data-testid="shape-name-input"]').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(data(wrapper)).toHaveLength(0)
  })

  it('searches "Add existing" by name and places the chosen entity with the default size of its metatype', async () => {
    const { wrapper, fetchFn } = await mountCanvas([])
    await wrapper.get('[data-testid="canvas-add-existing"]').trigger('click')
    await flushPromises()
    const query = document.body.querySelector<HTMLInputElement>('[data-testid="add-existing-query"]')!
    query.value = 'sap'
    query.dispatchEvent(new Event('input'))
    await vi.waitFor(() => expect(document.body.querySelectorAll('[data-testid="add-existing-results"] li')).toHaveLength(2))
    expect([...document.body.querySelectorAll('[data-testid="add-existing-results"] li')].map((item) => item.textContent)).toEqual([
      expect.stringContaining('SAP BW'),
      expect.stringContaining('SAP ERP'),
    ])
    document.body.querySelector<HTMLButtonElement>('[data-testid="add-existing-12"]')!.click()
    await flushPromises()
    expect(data(wrapper)).toHaveLength(1)
    expect(data(wrapper)[0]).toMatchObject({ name: 'SAP ERP', unsaved: true })
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(body(fetchFn).shapes[0]).toMatchObject({ entityId: 12, width: 120, height: 40 })
    expect(body(fetchFn).newEntities).toEqual([])
  })

  it('tells that no entity matches and offers more results page by page', async () => {
    const { wrapper } = await mountCanvas([], true, { searchPageSize: 2 })
    await wrapper.get('[data-testid="canvas-add-existing"]').trigger('click')
    await vi.waitFor(() => expect(document.body.querySelectorAll('[data-testid="add-existing-results"] li')).toHaveLength(2))
    document.body.querySelector<HTMLButtonElement>('[data-testid="add-existing-more"]')!.click()
    await vi.waitFor(() => expect(document.body.querySelectorAll('[data-testid="add-existing-results"] li')).toHaveLength(4))
    expect(document.body.querySelector('[data-testid="add-existing-more"]')).toBeNull()
    const query = document.body.querySelector<HTMLInputElement>('[data-testid="add-existing-query"]')!
    query.value = 'nothing like this'
    query.dispatchEvent(new Event('input'))
    await vi.waitFor(() => expect(document.body.querySelector('[data-testid="add-existing-empty"]')).not.toBeNull())
  })

  it('moves a selected shape with the arrow keys by 1 pt, and by 10 pt with Shift', async () => {
    const { wrapper } = await mountCanvas()
    await selectShape(wrapper, SHAPE)
    await press(wrapper, 'ArrowRight')
    await press(wrapper, 'ArrowDown')
    await press(wrapper, 'ArrowDown', true)
    await press(wrapper, 'ArrowLeft', true)
    await press(wrapper, 'ArrowUp')
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    const saved = (wrapper.emitted('saved')?.[0]?.[0] as { shapes: DiagramShape[] }).shapes[0]!
    // 100 + 1 - 10 and 50 + 1 + 10 - 1
    expect(saved).toMatchObject({ x: 91, y: 60 })
  })

  it('does not move a shape with the arrow keys of an input or without a selection', async () => {
    const { wrapper } = await mountCanvas()
    await press(wrapper, 'ArrowRight')
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
  })

  it('moves a shape by dragging and converts the px of the canvas to pt with two decimals', async () => {
    const { wrapper, fetchFn } = await mountCanvas()
    wrapper.findComponent(VueFlow).vm.$emit('node-drag-stop', { node: { id: SHAPE, position: { x: 400.1, y: 200 } } })
    await flushPromises()
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(body(fetchFn).shapes[0]).toMatchObject({ id: SHAPE, x: 300.08, y: 150, width: 120, height: 40 })
  })

  it('resizes a shape in pt', async () => {
    const { wrapper, fetchFn } = await mountCanvas()
    wrapper.findComponent(EntityShape).props('data').resized({ x: 160, y: 80, width: 200, height: 100 })
    await flushPromises()
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(body(fetchFn).shapes[0]).toMatchObject({ x: 120, y: 60, width: 150, height: 75 })
  })

  it('keeps a resize within 1 and 2000 pt', async () => {
    const { wrapper, fetchFn } = await mountCanvas()
    wrapper.findComponent(EntityShape).props('data').resized({ x: 0, y: 0, width: 0.5, height: 5000 })
    await flushPromises()
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(body(fetchFn).shapes[0]).toMatchObject({ width: 1, height: 2000 })
  })

  it('removes a selected shape with Delete, with the toolbar and with the context menu, and leaves the entity', async () => {
    const second = shape({ id: 'd1000000-0000-4000-8000-000000000002', entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT } })
    const third = shape({ id: 'd1000000-0000-4000-8000-000000000003', entity: { id: 11, name: 'SAP BW', metatypeId: METATYPE_COMPONENT } })
    const { wrapper, fetchFn } = await mountCanvas([shape(), second, third])
    expect(wrapper.get('[data-testid="canvas-remove"]').attributes('disabled')).toBeDefined()
    await selectShape(wrapper, SHAPE)
    await press(wrapper, 'Delete')
    expect(data(wrapper).map((entry) => entry.name)).toEqual(['Salesforce', 'SAP BW'])
    await selectShape(wrapper, second.id)
    await wrapper.get('[data-testid="canvas-remove"]').trigger('click')
    expect(data(wrapper).map((entry) => entry.name)).toEqual(['SAP BW'])
    wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id: third.id } })
    await flushPromises()
    await wrapper.get('[data-testid="canvas-menu-remove"]').trigger('click')
    expect(data(wrapper)).toHaveLength(0)
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(body(fetchFn).shapes).toEqual([])
  })

  it('saves all shapes in drawing order and shows the saved view without the unsaved marks', async () => {
    const second = shape({ id: 'd1000000-0000-4000-8000-000000000002', entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT }, x: 5, y: 6 })
    const { wrapper, fetchFn } = await mountCanvas([shape(), second])
    await place(wrapper, METATYPE_PROCESS, 'Invoice Approval')
    expect(wrapper.find('[data-testid="canvas-save"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(body(fetchFn).shapes.map((entry) => entry.entityId ?? 'new')).toEqual([12, 13, 'new'])
    expect(fetchFn.state.saves).toHaveLength(1)
    expect(wrapper.emitted('saved')).toHaveLength(1)
    // the saved view carries the new entity: no unsaved marks, and "Save" is disabled again
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="canvas-save"]').attributes('disabled')).toBeDefined()
  })

  it('marks a new entity with a missing mandatory property as incomplete after the save', async () => {
    const { wrapper } = await mountCanvas([], true, { missingMandatory: { [METATYPE_PROCESS]: ['Process Owner'] } })
    await place(wrapper, METATYPE_PROCESS, 'Invoice Approval')
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="shape-incomplete"]').text()).toContain('Incomplete: Process Owner')
  })

  it('marks the shape of an incomplete entity of the view', async () => {
    const { wrapper } = await mountCanvas([shape({ missingMandatory: ['Process Owner', 'Cost Center'] })])
    expect(wrapper.get('[data-testid="shape-incomplete"]').text()).toBe('Incomplete: Process Owner, Cost Center')
  })

  it('offers a reload that discards the changes when the diagram was changed by someone else (412)', async () => {
    const { wrapper, fetchFn, view } = await mountCanvas()
    fetchFn.state.diagrams.find((entry) => entry.id === view.diagram.id)!.version += 1
    await selectShape(wrapper, SHAPE)
    await press(wrapper, 'ArrowRight')
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('The diagram was changed by someone else')
    expect(wrapper.emitted('saved')).toBeUndefined()
    document.body.querySelector<HTMLButtonElement>('[data-testid="canvas-conflict-reload"]')!.click()
    await flushPromises()
    expect(wrapper.emitted('reload')).toHaveLength(1)
  })

  it('keeps all changes when the dialog of the conflict is closed with "Keep editing"', async () => {
    const { wrapper, fetchFn, view } = await mountCanvas()
    fetchFn.state.diagrams.find((entry) => entry.id === view.diagram.id)!.version += 1
    await selectShape(wrapper, SHAPE)
    await press(wrapper, 'ArrowRight')
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="canvas-conflict-keep"]')!.click()
    await flushPromises()
    expect(wrapper.emitted('reload')).toBeUndefined()
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
  })

  it('marks the shape a 422 names with its reason and keeps all changes', async () => {
    const { wrapper } = await mountCanvas([shape()], true, {
      rejectSave: { status: 422, errors: [{ field: 'shapes[0].entityId', code: 'ENTITY_NOT_FOUND', message: 'The entity does not exist or is deleted.' }] },
    })
    await selectShape(wrapper, SHAPE)
    await press(wrapper, 'ArrowRight')
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="shape-error"]').text()).toBe('The entity does not exist or is deleted.')
    expect(wrapper.find('[data-testid="canvas-rejected"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    // a change of the shape clears its mark
    await selectShape(wrapper, SHAPE)
    await press(wrapper, 'ArrowRight')
    expect(wrapper.find('[data-testid="shape-error"]').exists()).toBe(false)
  })

  it('marks the shape of a new entity a 422 names by the index of the new entity', async () => {
    const { wrapper } = await mountCanvas([], true, {
      rejectSave: { status: 422, errors: [{ field: 'newEntities[0].metatypeId', code: 'METATYPE_NOT_ALLOWED', message: 'The viewpoint does not allow this metatype.' }] },
    })
    await place(wrapper, METATYPE_COMPONENT, 'CRM Cloud')
    await wrapper.get('[data-testid="canvas-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="shape-error"]').text()).toBe('The viewpoint does not allow this metatype.')
    expect(data(wrapper)[0]).toMatchObject({ name: 'CRM Cloud' })
  })

  it('shows a reader the shapes read-only: no palette, no "Add existing", no "Save", no resizing', async () => {
    const { wrapper } = await mountCanvas([shape()], false)
    expect(wrapper.find('[data-testid="canvas-palette"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="canvas-add-existing"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="canvas-save"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="canvas-remove"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="canvas-read-only"]').exists()).toBe(true)
    expect(data(wrapper)[0]!.editable).toBe(false)
    expect(wrapper.find('[data-testid="canvas-zoom-in"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="canvas-fit-view"]').exists()).toBe(true)
  })

  it('does not move or remove a shape of a reader', async () => {
    const { wrapper } = await mountCanvas([shape()], false)
    await selectShape(wrapper, SHAPE)
    await press(wrapper, 'ArrowRight')
    await press(wrapper, 'Delete')
    expect(data(wrapper)).toHaveLength(1)
    expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
  })

  it('announces the editor to a keyboard user', async () => {
    const { wrapper } = await mountCanvas()
    const surface = wrapper.get('[data-testid="canvas-surface"]')
    expect(surface.attributes('role')).toBe('region')
    expect(wrapper.get(`#${surface.attributes('aria-describedby')}`).text()).toContain('arrow keys')
  })

  // limits of a save: 2000 shapes and 500 new entities (spec "Shapes of a diagram"); the real numbers are
  // checked in canvasModel.spec.ts, small ones keep this spec fast
  it('allows the last shape of a diagram and rejects the next one with a notice', async () => {
    const limits = CANVAS_LIMITS as { shapes: number }
    const original = limits.shapes
    limits.shapes = 3
    try {
      const { wrapper } = await mountCanvas([shape(), shape({ id: 'd1000000-0000-4000-8000-000000000002', x: 5 })])
      await place(wrapper, METATYPE_PROCESS, 'The third')
      expect(data(wrapper)).toHaveLength(3)
      await wrapper.get(`[data-testid="palette-${METATYPE_PROCESS}"]`).trigger('click')
      expect(data(wrapper)).toHaveLength(3)
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toContain('3')
    } finally {
      limits.shapes = original
    }
  })

  it('allows the last new entity of a save and rejects the next one with a notice', async () => {
    // the real limit of 500 is checked in canvasModel.spec.ts; a small one keeps this spec fast
    const limits = CANVAS_LIMITS as { newEntities: number }
    const original = limits.newEntities
    limits.newEntities = 2
    try {
      const { wrapper } = await mountCanvas([])
      await place(wrapper, METATYPE_PROCESS, 'New 1')
      await place(wrapper, METATYPE_PROCESS, 'New 2')
      expect(data(wrapper)).toHaveLength(2)
      await wrapper.get(`[data-testid="palette-${METATYPE_PROCESS}"]`).trigger('click')
      expect(data(wrapper)).toHaveLength(2)
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toContain('2')
      // an existing entity may still be added
      await wrapper.get('[data-testid="canvas-add-existing"]').trigger('click')
      await vi.waitFor(() => expect(document.body.querySelector('[data-testid="add-existing-12"]')).not.toBeNull())
      document.body.querySelector<HTMLButtonElement>('[data-testid="add-existing-12"]')!.click()
      await flushPromises()
      expect(data(wrapper)).toHaveLength(3)
    } finally {
      limits.newEntities = original
    }
  })

  it('accepts a name of 255 characters in the input and no more', async () => {
    const { wrapper } = await mountCanvas([])
    await wrapper.get(`[data-testid="palette-${METATYPE_PROCESS}"]`).trigger('click')
    expect(wrapper.get('[data-testid="shape-name-input"]').attributes('maxlength')).toBe(String(CANVAS_LIMITS.entityName))
  })

  // spec diagram/diagram-entity-details "Details panel" and "Change entities with the diagram"
  describe('details panel', () => {
    const APPROVAL = 'd1000000-0000-4000-8000-000000000003'
    const CRM = 'd1000000-0000-4000-8000-000000000002'
    const SHAPES = [
      shape(),
      shape({ id: CRM, entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT }, x: 300 }),
      shape({ id: APPROVAL, entity: { id: 14, name: 'Invoice Approval', metatypeId: METATYPE_PROCESS }, x: 150, y: 150, missingMandatory: ['Process Owner'] }),
    ]

    function panel(wrapper: VueWrapper) {
      return wrapper.find('[data-testid="entity-details"]')
    }

    async function opened(wrapper: VueWrapper, id: string, title: string) {
      await selectShape(wrapper, id)
      await vi.waitFor(() => expect(panel(wrapper).exists() && panel(wrapper).find('[data-testid="details-loading"]').exists()).toBe(false))
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-title"]').text()).toBe(title))
    }

    async function describe_(wrapper: VueWrapper, text: string) {
      const area = wrapper.get('[data-testid="details-description"]')
      await (area.element.tagName === 'TEXTAREA' ? area : area.get('textarea')).setValue(text)
      await flushPromises()
    }

    async function save(wrapper: VueWrapper) {
      await wrapper.get('[data-testid="canvas-save"]').trigger('click')
      await flushPromises()
    }

    it('opens on the selected shape and closes with Escape; it is not there without a selection', async () => {
      const { wrapper } = await mountCanvas(SHAPES)
      expect(panel(wrapper).exists()).toBe(false)
      await opened(wrapper, SHAPE, 'SAP ERP')
      expect(panel(wrapper).exists()).toBe(true)
      await panel(wrapper).trigger('keydown', { key: 'Escape' })
      expect(panel(wrapper).exists()).toBe(false)
      await selectShape(wrapper, CRM)
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-title"]').text()).toBe('Salesforce'))
    })

    it('counts the change of a field as an unsaved change and saves it with the entity version read', async () => {
      const { wrapper, fetchFn } = await mountCanvas(SHAPES)
      await opened(wrapper, SHAPE, 'SAP ERP')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
      await describe_(wrapper, 'Renamed ERP')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      expect(node(wrapper, SHAPE).attributes('data-unsaved')).toBe('true')
      await save(wrapper)
      expect(body(fetchFn).entityUpdates).toEqual([{ entityId: 12, expectedVersion: 4, description: 'Renamed ERP' }])
      expect(fetchFn.state.entities[12]).toMatchObject({ version: 5, description: 'Renamed ERP' })
      // the changes are cleared, and the panel shows the new version
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-meta"]').text()).toContain('Version 5'))
    })

    it('sends only the changed fields: a value changed and one changed back is not sent', async () => {
      const { wrapper, fetchFn } = await mountCanvas(SHAPES)
      await opened(wrapper, SHAPE, 'SAP ERP')
      const status = wrapper.get('[data-testid="details-field-Status"]').findComponent({ name: 'PropertyInput' })
      status.vm.$emit('update:modelValue', 'retiring')
      await flushPromises()
      const investment = wrapper.get('[data-testid="details-field-Investment"]').findComponent({ name: 'PropertyInput' })
      investment.vm.$emit('update:modelValue', '1')
      investment.vm.$emit('update:modelValue', '5000000')
      await flushPromises()
      await save(wrapper)
      expect(body(fetchFn).entityUpdates).toEqual([{ entityId: 12, expectedVersion: 4, properties: [{ propertyDefId: PROP_STATUS, value: 'retiring' }] }])
    })

    it('sends the description and values of a new entity with it', async () => {
      const { wrapper, fetchFn } = await mountCanvas([])
      await place(wrapper, METATYPE_PROCESS, 'Order Entry')
      await vi.waitFor(() => expect(wrapper.find('[data-testid="details-field-Process Owner"]').exists()).toBe(true))
      expect(wrapper.get('[data-testid="details-missing"]').text()).toContain('Process Owner')
      wrapper.get('[data-testid="details-field-Process Owner"]').findComponent({ name: 'PropertyInput' }).vm.$emit('update:modelValue', 'Anna')
      await flushPromises()
      await describe_(wrapper, 'Takes orders')
      await save(wrapper)
      expect(body(fetchFn).newEntities?.[0]).toMatchObject({ name: 'Order Entry', description: 'Takes orders', properties: [{ propertyDefId: PROP_OWNER, value: 'Anna' }] })
      expect(body(fetchFn)).not.toHaveProperty('entityUpdates')
      // the new entity is not incomplete
      expect(wrapper.find('[data-testid="shape-incomplete"]').exists()).toBe(false)
    })

    it('completes an incomplete entity and its shape is no longer marked', async () => {
      const { wrapper, fetchFn } = await mountCanvas(SHAPES)
      expect(wrapper.find('[data-testid="shape-incomplete"]').exists()).toBe(true)
      await opened(wrapper, APPROVAL, 'Invoice Approval')
      expect(wrapper.get('[data-testid="details-missing"]').text()).toContain('Process Owner')
      wrapper.get('[data-testid="details-field-Process Owner"]').findComponent({ name: 'PropertyInput' }).vm.$emit('update:modelValue', 'Anna')
      await flushPromises()
      expect(wrapper.find('[data-testid="details-missing"]').exists()).toBe(false)
      await save(wrapper)
      expect(body(fetchFn).entityUpdates).toEqual([{ entityId: 14, expectedVersion: 1, properties: [{ propertyDefId: PROP_OWNER, value: 'Anna' }] }])
      expect(wrapper.find('[data-testid="shape-incomplete"]').exists()).toBe(false)
    })

    it('marks the field and the shape a 422 names, and keeps the changes', async () => {
      const { wrapper } = await mountCanvas(SHAPES)
      await opened(wrapper, APPROVAL, 'Invoice Approval')
      await describe_(wrapper, 'Only a description')
      await save(wrapper)
      expect(wrapper.get('[data-testid="details-error"]').text()).toContain('Process Owner has no value')
      expect(node(wrapper, APPROVAL).get('[data-testid="shape-error"]').text()).toContain('Process Owner has no value')
      expect(wrapper.find('[data-testid="canvas-rejected"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      // a change of the entity clears the message of its fields
      await describe_(wrapper, 'Another description')
      expect(wrapper.find('[data-testid="details-error"]').exists()).toBe(false)
    })

    it('names the entity changed meanwhile on a 409 and reloads it without losing the other changes', async () => {
      const { wrapper, fetchFn } = await mountCanvas(SHAPES)
      await opened(wrapper, SHAPE, 'SAP ERP')
      await describe_(wrapper, 'My text for ERP')
      await opened(wrapper, CRM, 'Salesforce')
      await describe_(wrapper, 'My text for CRM')
      // someone else changes SAP ERP meanwhile
      fetchFn.state.entities[12]!.version = 5
      fetchFn.state.entities[12]!.description = 'From someone else'
      await save(wrapper)
      const alert = wrapper.get('[data-testid="canvas-entity-conflict"]')
      expect(alert.text()).toContain('SAP ERP was changed by someone else')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      expect(fetchFn.state.entities[13]!.version).toBe(1)
      await wrapper.get('[data-testid="canvas-entity-reload-12"]').trigger('click')
      await flushPromises()
      await vi.waitFor(() => expect(wrapper.get('[data-testid="details-meta"]').text()).toContain('Version 5'))
      expect(wrapper.find('[data-testid="canvas-entity-conflict"]').exists()).toBe(false)
      // only the changes of SAP ERP are discarded: its panel shows the other user's text
      expect((wrapper.get('[data-testid="details-field-description"] textarea').element as HTMLTextAreaElement).value).toBe('From someone else')
      await save(wrapper)
      expect(body(fetchFn).entityUpdates).toEqual([{ entityId: 13, expectedVersion: 1, description: 'My text for CRM' }])
      expect(fetchFn.state.entities[13]).toMatchObject({ version: 2, description: 'My text for CRM' })
    })

    it('names the entity with open issues on a 409 and keeps the changes', async () => {
      const { wrapper } = await mountCanvas(SHAPES, true, { blockedEntities: [12] })
      await opened(wrapper, SHAPE, 'SAP ERP')
      await describe_(wrapper, 'Blocked change')
      await save(wrapper)
      expect(wrapper.get('[data-testid="canvas-entity-blocked"]').text()).toContain('SAP ERP has open issues')
      expect(wrapper.get('[data-testid="canvas-entity-blocked"]').text()).toContain('CRM Replacement')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    })

    it.each([
      [403, 'You may not change the layout of diagrams.'],
      [413, 'The save is too large. Save fewer changes at once.'],
    ])('shows a general message for %i and keeps the changes', async (status, message) => {
      const { wrapper } = await mountCanvas(SHAPES, true, { rejectSave: { status, type: status === 413 ? 'request-too-large' : 'forbidden' } })
      await opened(wrapper, SHAPE, 'SAP ERP')
      await describe_(wrapper, 'Changed')
      await save(wrapper)
      expect(wrapper.get('[data-testid="canvas-failure"]').text()).toBe(message)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
    })

    it('shows a reader the panel read-only and marks nothing as changed', async () => {
      const { wrapper } = await mountCanvas(SHAPES, false, { me: ME_DIAGRAM_READER })
      await opened(wrapper, SHAPE, 'SAP ERP')
      expect(wrapper.find('[data-testid="details-read-only"]').exists()).toBe(true)
      expect(panel(wrapper).find('input, textarea').exists()).toBe(false)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('accepts the change of the last entity of a save and refuses the next one with a notice', async () => {
      // the real limit of 500 is checked in canvasModel.spec.ts; a small one keeps this spec fast
      const limits = CANVAS_LIMITS as { entityUpdates: number }
      const original = limits.entityUpdates
      limits.entityUpdates = 1
      try {
        const { wrapper, fetchFn } = await mountCanvas(SHAPES)
        await opened(wrapper, SHAPE, 'SAP ERP')
        await describe_(wrapper, 'First')
        await opened(wrapper, CRM, 'Salesforce')
        await describe_(wrapper, 'Second')
        expect(wrapper.get('[data-testid="canvas-notice"]').text()).toContain('1')
        await save(wrapper)
        expect(body(fetchFn).entityUpdates?.map((entry) => entry.entityId)).toEqual([12])
        // a further change of the entity that already has changes is fine
        await opened(wrapper, CRM, 'Salesforce')
        await describe_(wrapper, 'Second')
        expect(wrapper.find('[data-testid="canvas-notice"]').exists()).toBe(false)
      } finally {
        limits.entityUpdates = original
      }
    })

    it('shows the restricted property read-only and still saves the other changes', async () => {
      const { wrapper, fetchFn } = await mountCanvas(SHAPES, true, { readOnlyProperties: [PROP_INVESTMENT] })
      await opened(wrapper, SHAPE, 'SAP ERP')
      expect(wrapper.get('[data-testid="details-field-Investment"]').findComponent({ name: 'PropertyInput' }).exists()).toBe(false)
      await describe_(wrapper, 'Changed')
      await save(wrapper)
      expect(body(fetchFn).entityUpdates?.[0]).not.toHaveProperty('properties')
    })

    it('drops the changes of an entity whose shape is removed from the diagram', async () => {
      const { wrapper, fetchFn } = await mountCanvas(SHAPES)
      await opened(wrapper, CRM, 'Salesforce')
      await describe_(wrapper, 'Gone')
      await wrapper.get('[data-testid="canvas-remove"]').trigger('click')
      await flushPromises()
      expect(panel(wrapper).exists()).toBe(false)
      await save(wrapper)
      expect(body(fetchFn)).not.toHaveProperty('entityUpdates')
    })
  })
})
