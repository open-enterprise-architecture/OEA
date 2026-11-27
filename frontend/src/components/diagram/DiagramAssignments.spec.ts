import ui from '@nuxt/ui/vue-plugin'
import { VueFlow } from '@vue-flow/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { DiagramShape, DiagramView, DiagramViewWrite } from '../../api/diagramView'
import { session } from '../../session/store'
import { assignment, assignmentTargets, BPMN_TYPES, entityStatesOf, bpmnShape, bpmnView, ORG_TYPES, orgShape, organizationMetatypes, organizationModel, organizationViewpoint, withAssignments } from '../../test/bpmn'
import { diagramApi, ME_DIAGRAM_ARCHITECT, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER } from '../../test/diagrams'
import { testRouter } from '../../test/router'
import { ptToPx } from './canvasModel'
import DiagramCanvas from './DiagramCanvas.vue'

// specs diagram/bpmn-assignments and diagram/diagram-connections (assignments in the save) at the canvas (SCR-021;
// us-075-lane-assignments D3, D4, D6, D8): badges, chips and the avatar, the assignment search with "Create new", "×", the
// fields of the details panel, and the lanes of organizational units and roles that assign instead of nest

const mounted: VueWrapper[] = []

async function mountCanvas(view: DiagramView, options: { me?: unknown; canDelete?: boolean; editable?: boolean; unset?: Parameters<typeof organizationModel>[0] } = {}) {
  const editable = options.editable ?? true
  const fetchFn = diagramApi({
    me: options.me ?? (editable ? ME_DIAGRAM_WRITER : ME_DIAGRAM_READER),
    shapes: view.shapes,
    edges: view.edges,
    viewpoint: view.viewpoint,
    organization: organizationModel(options.unset),
    metatypes: organizationMetatypes(),
    assignmentTargets: assignmentTargets(),
    entityStates: entityStatesOf(view.shapes),
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

async function click(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-click', { node: { id } })
  await flushPromises()
}

async function contextMenu(wrapper: VueWrapper, id: string) {
  wrapper.findComponent(VueFlow).vm.$emit('node-context-menu', { event: new MouseEvent('contextmenu', { clientX: 5, clientY: 6 }), node: { id } })
  await flushPromises()
}

async function save(wrapper: VueWrapper, fetchFn: ReturnType<typeof diagramApi>): Promise<DiagramViewWrite> {
  await wrapper.get('[data-testid="canvas-save"]').trigger('click')
  await flushPromises()
  return fetchFn.state.saves.at(-1)!
}

const menuEntries = (wrapper: VueWrapper) => wrapper.findAll('[data-testid^="canvas-menu-assign-"]').map((entry) => entry.text())
const body = (selector: string) => document.body.querySelector<HTMLElement>(selector)

async function chooseAssignment(wrapper: VueWrapper, part: 'orgUnit' | 'role' | 'person', id: string, target: number) {
  await contextMenu(wrapper, id)
  await wrapper.get(`[data-testid="canvas-menu-assign-${part}"]`).trigger('click')
  await flushPromises()
  body(`[data-testid="assignment-${target}"]`)!.click()
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

function drag(wrapper: VueWrapper, id: string, x: number, y: number) {
  wrapper.findComponent(VueFlow).vm.$emit('node-drag-stop', { node: { id, position: { x: ptToPx(x), y: ptToPx(y) }, computedPosition: { x: ptToPx(x), y: ptToPx(y) } } })
  return flushPromises()
}

/** the lane "Purchasing" with the unit and the roles "Approver" and "Reviewer", the pool "Customer" without, and the tasks */
function process(): DiagramShape[] {
  return [
    bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200),
    withAssignments(bpmnShape('lane', 'Purchasing', 'lane', 30, 0, 570, 100, 'pool'), {
      orgUnits: [assignment('Purchasing Department', 701)],
      roles: [assignment('Approver', 711), assignment('Reviewer', 712)],
    }),
    withAssignments(bpmnShape('user', 'Approve order', 'userTask', 100, 20, 90, 60, 'lane'), { persons: [assignment('Maria Müller', 721)] }),
    bpmnShape('plain', 'Check order', 'task', 300, 20, 90, 60, 'lane'),
  ]
}

describe('assignments on the canvas', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    session.me = undefined
    session.loaded = false
  })

  describe('what a shape shows', () => {
    it('shows the badge of the organizational unit and the chips of the roles on a lane, and the avatar on a user task', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      const lane = wrapper.get('[data-testid="shape-lane"]')
      expect(lane.get('[data-testid="assignment-badge"]').text()).toContain('Purchasing Department')
      expect(lane.findAll('[data-testid="assignment-chip"]').map((chip) => chip.text())).toEqual(['Approver', 'Reviewer'])
      expect(lane.find('[data-testid="assignment-more"]').exists()).toBe(false)
      const avatar = wrapper.get('[data-testid="shape-user"] [data-testid="assignment-avatar"]')
      expect(avatar.text()).toBe('MM')
      expect(avatar.attributes('title')).toBe('Maria Müller')
      expect(wrapper.find('[data-testid="shape-plain"] [data-testid="assignment-avatar"]').exists()).toBe(false)
    })

    it.each([
      [3, 3, false],
      [4, 3, true],
    ])('shows %i roles as %i chips and the rest as "+N"', async (count, chips, more) => {
      const roles = Array.from({ length: count }, (_, index) => assignment(`Role ${index + 1}`, 800 + index))
      const view = bpmnView([bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200), withAssignments(bpmnShape('lane', 'Purchasing', 'lane', 30, 0, 570, 100, 'pool'), { roles })])
      const { wrapper } = await mountCanvas(view)
      expect(wrapper.findAll('[data-testid="shape-lane"] [data-testid="assignment-chip"]')).toHaveLength(chips)
      expect(wrapper.find('[data-testid="assignment-more"]').exists()).toBe(more)
      if (more) expect(wrapper.get('[data-testid="assignment-more"]').text()).toBe('+1')
    })

    it('shows a reader the badges, chips and the avatar without "×"', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()), { editable: false })
      expect(wrapper.find('[data-testid="assignment-badge"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="assignment-avatar"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="assignment-remove"]').exists()).toBe(false)
    })
  })

  describe('the context menu', () => {
    it('offers "Assign organizational unit" and "Assign role" on a lane and "Set participant" on a pool', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()), { canDelete: true })
      await contextMenu(wrapper, 'lane')
      expect(menuEntries(wrapper)).toEqual(['Assign organizational unit', 'Assign role'])
      await contextMenu(wrapper, 'pool')
      expect(menuEntries(wrapper)).toEqual(['Set participant'])
      await contextMenu(wrapper, 'plain')
      expect(menuEntries(wrapper)).toEqual([])
    })

    it('offers no entry to a reader and none for a part that is unset', async () => {
      const reader = await mountCanvas(bpmnView(process()), { editable: false })
      await contextMenu(reader.wrapper, 'lane')
      expect(reader.wrapper.find('[data-testid="canvas-menu"]').exists()).toBe(false)
      const { wrapper } = await mountCanvas(bpmnView(process()), { unset: ['role', 'performsRole'], canDelete: true })
      await contextMenu(wrapper, 'lane')
      expect(menuEntries(wrapper)).toEqual(['Assign organizational unit'])
    })

    it('does not offer the replacement of a saved unit without entity:delete', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      await contextMenu(wrapper, 'lane')
      expect(menuEntries(wrapper)).toEqual(['Assign role'])
    })

    it('offers no entry for the own part of an assignment lane', async () => {
      const view = { ...bpmnView([orgShape('role-lane', 'Approver', 'role', 0, 0, 570, 100)]), viewpoint: organizationViewpoint() }
      const { wrapper } = await mountCanvas(view)
      await contextMenu(wrapper, 'role-lane')
      expect(menuEntries(wrapper)).toEqual(['Assign organizational unit'])
    })
  })

  describe('assigning', () => {
    it('assigns an organizational unit to a lane and saves it as a connection without an edge', async () => {
      const view = bpmnView([bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200), bpmnShape('lane', 'Sales', 'lane', 30, 0, 570, 100, 'pool')])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await chooseAssignment(wrapper, 'orgUnit', 'lane', 702)
      expect(wrapper.get('[data-testid="shape-lane"] [data-testid="assignment-badge"]').text()).toContain('Sales Division')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(true)
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections).toEqual([{ key: expect.any(String), metatypeId: ORG_TYPES.belongsTo, sourceShapeId: 'lane', targetEntityId: 702 }])
      expect(request.edges).toEqual([])
      expect(request.removedConnections).toBeUndefined()
      expect(wrapper.get('[data-testid="shape-lane"] [data-testid="assignment-badge"]').text()).toContain('Sales Division')
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })

    it('sets the participant of a pool', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView([bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200)]))
      await contextMenu(wrapper, 'pool')
      expect(menuEntries(wrapper)).toEqual(['Set participant'])
      await chooseAssignment(wrapper, 'orgUnit', 'pool', 702)
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections![0]).toMatchObject({ metatypeId: ORG_TYPES.belongsTo, sourceShapeId: 'pool', targetEntityId: 702 })
    })

    it('assigns two roles to a lane', async () => {
      const view = bpmnView([bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200), bpmnShape('lane', 'Sales', 'lane', 30, 0, 570, 100, 'pool')])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await chooseAssignment(wrapper, 'role', 'lane', 711)
      await chooseAssignment(wrapper, 'role', 'lane', 712)
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections!.map((connection) => connection.targetEntityId)).toEqual([711, 712])
      expect(request.newConnections!.every((connection) => connection.metatypeId === ORG_TYPES.performsRole)).toBe(true)
    })

    it('replaces the saved unit with entity:delete: the old connection is removed with its version, the new one added', async () => {
      const shapes = process()
      const lane = shapes.find((entry) => entry.id === 'lane')!
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(shapes), { canDelete: true, me: ME_DIAGRAM_ARCHITECT })
      const oldConnection = lane.assignments!.orgUnits[0]!
      await chooseAssignment(wrapper, 'orgUnit', 'lane', 702)
      expect(wrapper.findAll('[data-testid="shape-lane"] [data-testid="assignment-badge"]')).toHaveLength(1)
      const request = await save(wrapper, fetchFn)
      expect(request.removedConnections).toEqual([{ connectionId: oldConnection.connectionId, expectedVersion: 1 }])
      expect(request.newConnections).toHaveLength(1)
      expect(request.newConnections![0]).toMatchObject({ targetEntityId: 702 })
    })

    it('creates a new organizational unit with the typed name in the same save, without a shape', async () => {
      const view = bpmnView([bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200), bpmnShape('lane', 'Sales', 'lane', 30, 0, 570, 100, 'pool')])
      const { wrapper, fetchFn } = await mountCanvas(view)
      await contextMenu(wrapper, 'lane')
      await wrapper.get('[data-testid="canvas-menu-assign-orgUnit"]').trigger('click')
      await flushPromises()
      const query = body('[data-testid="assignment-query"] input') ?? (body('[data-testid="assignment-query"]') as HTMLInputElement)
      ;(query as HTMLInputElement).value = 'Logistics'
      query.dispatchEvent(new Event('input', { bubbles: true }))
      await flushPromises()
      body('[data-testid="assignment-create-button"]')!.click()
      await flushPromises()
      const request = await save(wrapper, fetchFn)
      const entity = request.newEntities!.find((candidate) => candidate.name === 'Logistics')!
      expect(entity).toMatchObject({ metatypeId: ORG_TYPES.orgUnit })
      expect(request.shapes.some((entry) => entry.newEntityKey === entity.key)).toBe(false)
      expect(request.newConnections![0]).toMatchObject({ sourceShapeId: 'lane', targetNewEntityKey: entity.key })
    })
  })

  describe('removing', () => {
    it('removes a saved assignment with "×" for a holder of entity:delete and saves the version read', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()), { canDelete: true, me: ME_DIAGRAM_ARCHITECT })
      const roles = wrapper.findAll('[data-testid="shape-lane"] [data-testid="assignment-chip"]')
      expect(roles).toHaveLength(2)
      await roles[0]!.get('[data-testid="assignment-remove"]').trigger('click')
      expect(wrapper.findAll('[data-testid="shape-lane"] [data-testid="assignment-chip"]')).toHaveLength(1)
      const request = await save(wrapper, fetchFn)
      expect(request.removedConnections).toHaveLength(1)
      expect(request.removedConnections![0]).toMatchObject({ expectedVersion: 1 })
    })

    it('offers no "×" for a saved assignment without entity:delete, but for a pending one', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      expect(wrapper.find('[data-testid="shape-lane"] [data-testid="assignment-remove"]').exists()).toBe(false)
      await chooseAssignment(wrapper, 'role', 'lane', 711).catch(() => undefined)
    })

    it('drops a pending assignment with "×" and the save no longer has it', async () => {
      const view = bpmnView([bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200), bpmnShape('lane', 'Sales', 'lane', 30, 0, 570, 100, 'pool')])
      const { wrapper } = await mountCanvas(view)
      await chooseAssignment(wrapper, 'role', 'lane', 711)
      await wrapper.get('[data-testid="shape-lane"] [data-testid="assignment-chip"] [data-testid="assignment-remove"]').trigger('click')
      expect(wrapper.find('[data-testid="shape-lane"] [data-testid="assignment-chip"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
    })
  })

  describe('the details panel', () => {
    it('has the field "Required role" on a task and also "Responsible person" on a user task', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()))
      await click(wrapper, 'plain')
      expect(wrapper.find('[data-testid="details-assignments-role"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="details-assignments-person"]').exists()).toBe(false)
      await click(wrapper, 'user')
      expect(wrapper.find('[data-testid="details-assignments-role"]').exists()).toBe(true)
      expect(wrapper.get('[data-testid="details-assignments-person"]').text()).toContain('Maria Müller')
      await click(wrapper, 'pool')
      expect(wrapper.find('[data-testid="details-assignments-role"]').exists()).toBe(false)
    })

    it('hides the field of a part that is unset', async () => {
      const { wrapper } = await mountCanvas(bpmnView(process()), { unset: ['person'] })
      await click(wrapper, 'user')
      expect(wrapper.find('[data-testid="details-assignments-person"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="details-assignments-role"]').exists()).toBe(true)
    })

    it('assigns a required role to a plain task from the panel and saves it', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await click(wrapper, 'plain')
      await wrapper.get('[data-testid="details-assignment-add-role"]').trigger('click')
      await flushPromises()
      body('[data-testid="assignment-711"]')!.click()
      await flushPromises()
      expect(wrapper.get('[data-testid="details-assignments-role"]').text()).toContain('Approver')
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections![0]).toMatchObject({ metatypeId: ORG_TYPES.performsRole, sourceShapeId: 'plain', targetEntityId: 711 })
    })

    it('warns about a second responsible person and still saves it', async () => {
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(process()))
      await click(wrapper, 'user')
      expect(wrapper.find('[data-testid="details-assignment-warning"]').exists()).toBe(false)
      await wrapper.get('[data-testid="details-assignment-add-person"]').trigger('click')
      await flushPromises()
      body('[data-testid="assignment-722"]')!.click()
      await flushPromises()
      expect(wrapper.get('[data-testid="details-assignment-warning"]').text()).toContain('More than one responsible person')
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections![0]).toMatchObject({ metatypeId: ORG_TYPES.responsible, targetEntityId: 722 })
    })
  })

  describe('lanes of organizational units and roles (design D8)', () => {
    /** the viewpoint maps "Role" to a lane and "Organizational Unit" to a pool; the pool "Order handling" is an entity of the type "Pool" */
    function roleLanes(): DiagramView {
      const shapes = [
        bpmnShape('order', 'Order handling', 'pool', 0, 0, 600, 300),
        orgShape('approver', 'Approver', 'role', 30, 0, 570, 150, 'order'),
        orgShape('clerk', 'Clerk', 'role', 30, 150, 570, 150, 'order'),
        withAssignments(bpmnShape('task', 'Approve order', 'userTask', 100, 30, 90, 60, 'approver'), { roles: [assignment('Approver', 0)] }),
      ]
      return { ...bpmnView(shapes), viewpoint: organizationViewpoint() }
    }

    it('assigns a new task dropped into a role lane instead of nesting it: performs-role, parent from the pool, no question', async () => {
      const view = roleLanes()
      const { wrapper, fetchFn } = await mountCanvas({ ...view, shapes: view.shapes.map((entry) => (entry.id === 'task' ? { ...entry, assignments: null } : entry)) })
      await paletteDrop(wrapper, BPMN_TYPES.task, 400, 200)
      await nameNew(wrapper, 'Check invoice')
      expect(document.body.querySelector('[data-testid="new-parent-yes"]')).toBeNull()
      const request = await save(wrapper, fetchFn)
      const created = request.newEntities!.find((entity) => entity.name === 'Check invoice')!
      const clerk = view.shapes.find((entry) => entry.id === 'clerk')!
      const pool = view.shapes.find((entry) => entry.id === 'order')!
      expect(created.parentEntityId).toBe(pool.entity.id)
      expect(request.newConnections).toHaveLength(1)
      expect(request.newConnections![0]).toMatchObject({ metatypeId: ORG_TYPES.performsRole, targetEntityId: clerk.entity.id })
      expect(request.shapes.find((entry) => entry.newEntityKey === created.key)).toMatchObject({ parentShapeId: 'clerk' })
    })

    it('moves a task between two role lanes: the connection to the first is removed, one to the second added, the parent unchanged', async () => {
      const view = roleLanes()
      const approver = view.shapes.find((entry) => entry.id === 'approver')!
      const clerk = view.shapes.find((entry) => entry.id === 'clerk')!
      const shapes = view.shapes.map((entry) =>
        entry.id === 'task' ? withAssignments(entry, { roles: [{ ...assignment('Approver', approver.entity.id), connectionVersion: 3 }] }) : entry,
      )
      const { wrapper, fetchFn } = await mountCanvas({ ...view, shapes }, { canDelete: true, me: ME_DIAGRAM_ARCHITECT })
      await drag(wrapper, 'task', 100, 180)
      expect(document.body.querySelector('[data-testid="new-parent-yes"]')).toBeNull()
      const request = await save(wrapper, fetchFn)
      expect(request.removedConnections).toEqual([{ connectionId: shapes.find((entry) => entry.id === 'task')!.assignments!.roles[0]!.connectionId, expectedVersion: 3 }])
      expect(request.newConnections).toHaveLength(1)
      expect(request.newConnections![0]).toMatchObject({ metatypeId: ORG_TYPES.performsRole, sourceShapeId: 'task', targetEntityId: clerk.entity.id })
      expect(request.parentChanges).toBeUndefined()
    })

    it('refuses to move a task out of its role lane without entity:delete and leaves everything as it was', async () => {
      const view = roleLanes()
      const approver = view.shapes.find((entry) => entry.id === 'approver')!
      const shapes = view.shapes.map((entry) => (entry.id === 'task' ? withAssignments(entry, { roles: [assignment('Approver', approver.entity.id)] }) : entry))
      const { wrapper } = await mountCanvas({ ...view, shapes })
      await drag(wrapper, 'task', 100, 180)
      expect(wrapper.find('[data-testid="canvas-unsaved"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="canvas-notice"]').text()).toContain('needs the permission to delete')
    })

    it('does not send a connection that exists already when the task moves within the lane', async () => {
      const view = roleLanes()
      const approver = view.shapes.find((entry) => entry.id === 'approver')!
      const shapes = view.shapes.map((entry) => (entry.id === 'task' ? withAssignments(entry, { roles: [assignment('Approver', approver.entity.id)] }) : entry))
      const { wrapper, fetchFn } = await mountCanvas({ ...view, shapes })
      await drag(wrapper, 'task', 120, 40)
      const request = await save(wrapper, fetchFn)
      expect(request.newConnections).toEqual([])
      expect(request.removedConnections).toBeUndefined()
    })

    it('nests a shape in a lane of another type as before: the lane is its parent and nothing is assigned', async () => {
      const shapes = [bpmnShape('pool', 'Customer', 'pool', 0, 0, 600, 200), bpmnShape('lane', 'Sales', 'lane', 30, 0, 570, 100, 'pool'), bpmnShape('free', 'Check order', 'task', 100, 130, 90, 60, 'pool')]
      const { wrapper, fetchFn } = await mountCanvas(bpmnView(shapes))
      await drag(wrapper, 'free', 100, 20)
      await flushPromises()
      const request = await save(wrapper, fetchFn).catch(() => undefined)
      expect(document.body.querySelector('[data-testid="new-parent-yes"]') !== null || request !== undefined).toBe(true)
      expect(request?.newConnections ?? []).toEqual([])
    })
  })
})
