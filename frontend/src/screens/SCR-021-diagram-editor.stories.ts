import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, waitFor, within } from 'storybook/test'
import NewParentDialog from '../components/diagram/NewParentDialog.vue'
import DiagramPage from '../pages/DiagramPage.vue'
import { assignment, assignmentTargets, bpmnEdge, entityStatesOf, bpmnShape, bpmnViewpoint, orgShape, organizationMetatypes, organizationModel, organizationViewpoint, withAssignments } from '../test/bpmn'
import { diagram, diagramApi, edge, ENTITIES, FINANCE_SUITE, METATYPE_COMPONENT, METATYPE_FLOW, METATYPE_PROCESS, ME_DIAGRAM_ARCHITECT, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER, PROP_INVESTMENT, shape, viewpointConfigs, type DiagramApi } from '../test/diagrams'

// SCR-021 Diagram editor (UC-10, US-074; design D6 of us-074-diagram-canvas and D3 of
// us-074-diagram-entity-details and D5 of us-074-diagram-connections): the canvas of a diagram with the
// palette of its viewpoint, "Add existing", moving, resizing and removing shapes, the edges between
// them, the offer of existing connections, "Add connected element", "Save", the details panel of the
// selected shape or edge and the read-only view of a reader (docs/screens/SCREENS.md). The first story
// is the screen image.
const meta: Meta<typeof DiagramPage> = {
  title: 'Screens/SCR-021 Diagram editor',
  component: DiagramPage,
}

export default meta
type Story = StoryObj<typeof meta>

const ID = diagram().id

const SHAPES = [
  shape({ id: 'd1000000-0000-4000-8000-000000000001', entity: { id: 12, name: 'SAP ERP', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, x: 60, y: 60 }),
  shape({ id: 'd1000000-0000-4000-8000-000000000002', entity: { id: 13, name: 'Salesforce', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, x: 300, y: 60 }),
  shape({ id: 'd1000000-0000-4000-8000-000000000003', entity: { id: 14, name: 'Invoice Approval', metatypeId: METATYPE_PROCESS, childCount: 0, parentEntityId: null }, x: 180, y: 180, width: 90, height: 30, missingMandatory: ['Process Owner'] }),
]

/** The connection "SAP ERP → Salesforce" with a bend point, shown as an edge between the two shapes. */
const EDGES = [edge({ points: [{ x: 200, y: 120 }] })]

/** A holder of `entity:write`: the palette, "Add existing", "Remove from diagram" and "Save" around the shapes and edges. */
export const Canvas: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES, edges: EDGES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('SAP ERP')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Add Application Component' })).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Add existing' })).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Save' })).toBeDisabled()
    await expect(canvas.getByRole('button', { name: 'Connection SAP ERP → Salesforce' })).toBeInTheDocument()
    await expect(canvas.getByText('Incomplete: Process Owner')).toBeInTheDocument()
    // the details panel of the selected shape names the missing value
    await userEvent.click(canvas.getByText('Invoice Approval'))
    await expect(await canvas.findByText('Required values missing: Process Owner')).toBeInTheDocument()
  },
}

/** A diagram without shapes: the palette places the first ones. */
export const Empty: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: [] }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('button', { name: 'Add Business Process' })).toBeInTheDocument()
    await expect(canvas.getByRole('region', { name: 'Diagram canvas' })).toBeInTheDocument()
  },
}

/** A new shape is named in the shape itself and is marked as unsaved until "Save". */
export const Unsaved: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Add Application Component' }))
    await userEvent.type(await canvas.findByRole('textbox', { name: 'Name of the new Application Component' }), 'CRM Cloud{Enter}')
    // the name is in the shape and in the details panel of the new entity
    await expect((await canvas.findAllByText('CRM Cloud')).length).toBeGreaterThan(0)
    await expect(canvas.getByText('Unsaved changes')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Save' })).toBeEnabled()
  },
}

/** A save the server rejects marks the shape with the reason and keeps all changes. */
export const RejectedShape: Story = {
  args: {
    id: ID,
    fetchFn: diagramApi({
      me: ME_DIAGRAM_WRITER,
      shapes: SHAPES,
      rejectSave: { status: 422, errors: [{ field: 'shapes[1].entityId', code: 'ENTITY_NOT_FOUND', message: 'The entity does not exist or is deleted.' }] },
    }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Add Business Process' }))
    await userEvent.type(await canvas.findByRole('textbox', { name: 'Name of the new Business Process' }), 'Order Entry{Enter}')
    await userEvent.click(canvas.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(canvas.getByText('The entity does not exist or is deleted.')).toBeInTheDocument())
    await expect(canvas.getByText(/Some shapes were rejected/)).toBeInTheDocument()
  },
}

/** A reader sees the shapes, can pan and zoom, and has no palette, no "Add existing" and no "Save". */
export const ReadOnly: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_READER, shapes: SHAPES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('SAP ERP')).toBeInTheDocument()
    await expect(canvas.getByText('Read-only')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Add existing' })).toBeNull()
    await expect(canvas.queryByRole('button', { name: 'Save' })).toBeNull()
    await expect(canvas.getByRole('button', { name: 'Zoom in' })).toBeInTheDocument()
  },
}

/** The details panel of a shape shows the values in the form of the metatype; changing one is an unsaved change. */
export const DetailsEditing: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByText('SAP ERP'))
    await expect(await canvas.findByRole('complementary', { name: 'Details of the selected entity' })).toBeInTheDocument()
    await expect(await canvas.findByText(/Version 4/)).toBeInTheDocument()
    const description = await canvas.findByDisplayValue('Core ERP for finance and logistics')
    await userEvent.clear(description)
    await userEvent.type(description, 'Core ERP, retiring in 2028')
    await expect(canvas.getByText('Unsaved changes')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Save' })).toBeEnabled()
  },
}

/** An incomplete entity: the required value is marked and named, and entering it removes the mark. */
export const DetailsIncomplete: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByText('Invoice Approval'))
    await expect(await canvas.findByText('Required values missing: Process Owner')).toBeInTheDocument()
    await userEvent.type(await canvas.findByLabelText(/Process Owner/), 'Anna')
    await waitFor(() => expect(canvas.queryByText('Required values missing: Process Owner')).toBeNull())
    await userEvent.click(canvas.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(canvas.queryByText('Incomplete: Process Owner')).toBeNull())
  },
}

/** A reader sees the values without input fields, and a value the reader may not read as "Restricted". */
export const DetailsReadOnly: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_READER, shapes: SHAPES, redactedProperties: [PROP_INVESTMENT] }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByText('SAP ERP'))
    await expect(await canvas.findByText('You may view the values, but not change them.')).toBeInTheDocument()
    await expect(canvas.getByText('Restricted')).toBeInTheDocument()
    await expect(canvas.queryByDisplayValue('Core ERP for finance and logistics')).toBeNull()
  },
}

/** Someone else changed the entity meanwhile: the save names it, keeps the changes and offers to reload it. */
export const VersionConflict: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES }) },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByText('SAP ERP'))
    const description = await canvas.findByDisplayValue('Core ERP for finance and logistics')
    await userEvent.type(description, ' (mine)')
    const api = args.fetchFn as DiagramApi
    api.state.entities[12]!.version = 5
    await userEvent.click(canvas.getByRole('button', { name: 'Save' }))
    await expect(await canvas.findByText(/SAP ERP was changed by someone else/)).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Reload SAP ERP and discard my changes of it' })).toBeInTheDocument()
    await expect(canvas.getByText('Unsaved changes')).toBeInTheDocument()
  },
}

/** An edge is reached with Tab or a click; its details show the connection with its type, version and values. */
export const EdgeDetails: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES, edges: EDGES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Connection SAP ERP → Salesforce' }))
    await expect(await canvas.findByRole('complementary', { name: 'Details of the selected entity' })).toBeInTheDocument()
    await expect(await canvas.findByText(/Version 2/)).toBeInTheDocument()
    await expect(await canvas.findByDisplayValue('Orders to the CRM')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Remove from diagram' })).toBeEnabled()
  },
}

/** Placing the second end of a connection offers it; nothing is added until "Show all" or "Choose…". */
export const OfferedConnections: Story = {
  args: {
    id: ID,
    fetchFn: diagramApi({
      me: ME_DIAGRAM_WRITER,
      shapes: [SHAPES[1]!],
      offers: [{ connection: { id: 21, name: 'SAP ERP → Salesforce', metatypeId: METATYPE_FLOW }, sourceEntityId: 12, targetEntityId: 13 }],
    }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Add existing' }))
    const dialog = within(await within(document.body).findByTestId('add-existing-dialog'))
    await userEvent.type(await dialog.findByRole('textbox'), 'SAP ERP')
    await userEvent.click(await dialog.findByRole('button', { name: /SAP ERP/ }, { timeout: 4000 }))
    await expect(await canvas.findByText('1 connection available')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Connection SAP ERP → Salesforce' })).toBeNull()
    await userEvent.click(await canvas.findByRole('button', { name: 'Show all' }))
    await expect(await canvas.findByRole('button', { name: 'Connection SAP ERP → Salesforce' })).toBeInTheDocument()
    await expect(canvas.getByText('Unsaved changes')).toBeInTheDocument()
  },
}

/** "Add connected element" places a new element at the default length of the connection type, joined by a new connection. */
export const ConnectedElement: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES, edges: EDGES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByText('SAP ERP'))
    await userEvent.click(canvas.getByRole('button', { name: 'Add connected element' }))
    const dialog = within(await within(document.body).findByTestId('connected-dialog'))
    await waitFor(() => expect(dialog.getByRole('button', { name: 'Add' })).toBeEnabled())
    await userEvent.click(dialog.getByRole('button', { name: 'Add' }))
    await userEvent.type(await canvas.findByRole('textbox', { name: /Name of the new/ }), 'Billing{Enter}')
    await expect(await canvas.findByRole('button', { name: 'Connection SAP ERP → Billing' })).toBeInTheDocument()
    await expect(canvas.getByText('Unsaved changes')).toBeInTheDocument()
  },
}

/** A reader sees the edges and their details, but cannot draw, move or remove them. */
export const EdgesReadOnly: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_READER, shapes: SHAPES, edges: EDGES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Connection SAP ERP → Salesforce' }))
    await expect(await canvas.findByText('You may view the values, but not change them.')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Add connected element' })).toBeNull()
  },
}

/** "Delete from repository" shows what the deletion takes along: children, connections and the active solutions (s17-entity-delete). */
export const DeleteFromRepository: Story = {
  args: {
    id: ID,
    fetchFn: diagramApi({
      me: ME_DIAGRAM_ARCHITECT,
      shapes: SHAPES,
      edges: EDGES,
      deletion: { previews: { 12: { childCount: 3, connectionCount: 4, solutions: [{ id: 's1', name: 'Finance Upgrade', deltaCount: 2 }] } } },
    }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByText('SAP ERP'))
    await userEvent.keyboard('{Control>}{Delete}{/Control}')
    const dialog = within(await within(document.body).findByTestId('repository-delete-dialog'))
    await expect(await dialog.findByText('3 children are deleted with it, on all levels.')).toBeInTheDocument()
    await expect(dialog.getByText('4 connections are deleted with it.')).toBeInTheDocument()
    await expect(dialog.getByText('Finance Upgrade: 2 changes')).toBeInTheDocument()
    await userEvent.click(dialog.getByRole('button', { name: 'Delete from repository' }))
    await waitFor(() => expect(canvas.queryByText('SAP ERP')).toBeNull())
  },
}

/** "Finance Suite" holds its children "Billing" and "Invoicing" as nested shapes: the container grew with them (US-074 nesting). */
const NESTED = [
  shape({ id: 'd1000000-0000-4000-8000-000000000015', entity: { id: 15, name: 'Finance Suite', metatypeId: METATYPE_COMPONENT, childCount: 2, parentEntityId: null }, x: 40, y: 40, width: 330, height: 120 }),
  shape({ id: 'd1000000-0000-4000-8000-000000000016', entity: { id: 16, name: 'Billing', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: 15 }, parentShapeId: 'd1000000-0000-4000-8000-000000000015', x: 50, y: 74, width: 120, height: 40 }),
  shape({ id: 'd1000000-0000-4000-8000-000000000017', entity: { id: 17, name: 'Invoicing', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: 15 }, parentShapeId: 'd1000000-0000-4000-8000-000000000015', x: 180, y: 74, width: 120, height: 40 }),
  shape({ id: 'd1000000-0000-4000-8000-000000000012', entity: { id: 12, name: 'SAP ERP', metatypeId: METATYPE_COMPONENT, childCount: 0, parentEntityId: null }, x: 460, y: 74 }),
]

/** Nested shapes: the children lie in their container, which holds them and carries its name above them. */
export const NestedShapes: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: NESTED, entities: [...ENTITIES, FINANCE_SUITE] }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Finance Suite')).toBeInTheDocument()
    await expect(canvas.getByText('Billing')).toBeInTheDocument()
    await expect(canvas.getByText('Invoicing')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Save' })).toBeDisabled()
  },
}

/** Placing an entity with children asks whether they are shown; "Show children" nests them in a grid and grows the entity. */
export const ShowChildren: Story = {
  // every shape takes children in a viewpoint of another notation than `bpmn2` (design D5 of us-074-bpmn-process-diagrams)
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: [], entities: [...ENTITIES, FINANCE_SUITE], viewpoint: { ...viewpointConfigs()[0]!, notation: 'generic' } }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Add existing' }))
    const dialog = within(await within(document.body).findByTestId('add-existing-dialog'))
    await userEvent.type(await dialog.findByRole('textbox'), 'Finance')
    await userEvent.click(await dialog.findByRole('button', { name: /Finance Suite/ }, { timeout: 4000 }))
    const question = within(await within(document.body).findByTestId('children-dialog'))
    await userEvent.click(question.getByRole('button', { name: 'Show children' }))
    await expect(await canvas.findByText('Billing')).toBeInTheDocument()
    await expect(canvas.getByText('Invoicing')).toBeInTheDocument()
    // a child of a type the viewpoint does not allow is named, not placed
    await expect(canvas.getByText(/Not placed, because the viewpoint does not allow their type: Ledger/)).toBeInTheDocument()
    await expect(canvas.getByText('Unsaved changes')).toBeInTheDocument()
  },
}

/** A child shown elsewhere is not nested twice: a hierarchy line runs from its parent to it. */
export const HierarchyLine: Story = {
  args: {
    id: ID,
    fetchFn: diagramApi({
      me: ME_DIAGRAM_WRITER,
      shapes: [NESTED[0]!, NESTED[3]!],
      lines: [{ id: 'a0000000-0000-4000-8000-000000000001', parentShapeId: NESTED[0]!.id, childShapeId: NESTED[3]!.id }],
      entities: [...ENTITIES, FINANCE_SUITE],
    }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('button', { name: 'Hierarchy line Finance Suite → SAP ERP' })).toBeInTheDocument()
    await expect(canvas.getByText('SAP ERP')).toBeInTheDocument()
  },
}

/** Moving a shape into a container or out of it asks "New parent?"; "No" changes only the picture. */
export const NewParentQuestion: Story = {
  render: () => ({
    components: { NewParentDialog },
    template: '<NewParentDialog :open="true" name="SAP ERP" parent="Finance Suite" />',
  }),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Make Finance Suite the parent of SAP ERP?')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Yes' })).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'No' })).toBeInTheDocument()
  },
}

/** After a viewpoint change: the shape and edge of types the viewpoint does not allow are conflicts, marked with a text, listed, and removed from the diagram. */
const CONFLICT_SHAPES = [SHAPES[0]!, SHAPES[1]!, { ...SHAPES[2]!, missingMandatory: [], conflict: true }]
const CONFLICT_EDGES = [edge({ points: [{ x: 200, y: 120 }], conflict: true })]

export const Conflicts: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: CONFLICT_SHAPES, edges: CONFLICT_EDGES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Conflicts (2)')).toBeInTheDocument()
    await expect(canvas.getAllByText('Conflict: type not allowed in this viewpoint').length).toBeGreaterThan(0)
    await expect(canvas.getByRole('button', { name: 'Select shape Invoice Approval' })).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Remove from diagram: Invoice Approval' })).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Remove all conflicts' }))
    await waitFor(() => expect(canvas.queryByText(/^Conflicts \(/)).toBeNull())
    await expect(canvas.getByRole('button', { name: 'Save' })).toBeEnabled()
  },
}

/** A save with conflicts left is refused: the list stays and says what to do. */
export const ConflictsBlockSave: Story = {
  args: {
    id: ID,
    fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: CONFLICT_SHAPES, edges: CONFLICT_EDGES }),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Remove from diagram: Invoice Approval' }))
    await userEvent.click(canvas.getByRole('button', { name: 'Save' }))
    await expect(await canvas.findByText(/The diagram was not saved: it still has conflicts/)).toBeInTheDocument()
    await expect(canvas.getByText('Conflicts (1)')).toBeInTheDocument()
  },
}

/** A reader sees the marks and the list of conflicts without the actions. */
export const ConflictsReadOnly: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_READER, shapes: CONFLICT_SHAPES, edges: CONFLICT_EDGES }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Conflicts (2)')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Remove all conflicts' })).toBeNull()
    await expect(canvas.queryByRole('button', { name: /^Remove from diagram: / })).toBeNull()
  },
}

/** "Change viewpoint": the viewpoints to choose from, and before the change the shapes and edges that become conflicts. */
export const ChangeViewpoint: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: SHAPES, edges: EDGES, viewpoints: viewpointConfigs() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Change viewpoint' }))
    const body = within(canvasElement.ownerDocument.body)
    const dialog = within(await body.findByTestId('viewpoint-change-form'))
    await userEvent.click(await dialog.findByRole('radio', { name: /Integration View/ }))
    await expect(await dialog.findByText('1 shape becomes a conflict.')).toBeInTheDocument()
    await expect(dialog.getByText('1 edge becomes a conflict.')).toBeInTheDocument()
    await expect(dialog.getByRole('button', { name: 'Change viewpoint' })).toBeEnabled()
  },
}

/**
 * A BPMN process (us-074-bpmn-process-diagrams): two pools, the first with the lanes "Sales" and "Back office",
 * tasks, events, gateway, sub-process, text annotation and group drawn with their BPMN symbols, the flows with
 * their lines, and the sequence flow from "Ship order" to "Deliver goods" across the pool boundary marked with a
 * warning. The palette is grouped.
 */
const PROCESS = [
  bpmnShape('p-customer', 'Customer', 'pool', 0, 0, 820, 360),
  bpmnShape('p-sales', 'Sales', 'lane', 30, 0, 790, 180, 'p-customer'),
  bpmnShape('p-office', 'Back office', 'lane', 30, 180, 790, 180, 'p-customer'),
  bpmnShape('p-start', 'Order received', 'messageStart', 60, 70, 27, 27, 'p-sales'),
  bpmnShape('p-check', 'Check order', 'userTask', 140, 50, 90, 60, 'p-sales'),
  bpmnShape('p-valid', 'Valid?', 'gateway', 280, 60, 38, 38, 'p-sales'),
  bpmnShape('p-ship', 'Ship order', 'task', 380, 50, 90, 60, 'p-sales'),
  bpmnShape('p-note', 'Pays within 14 days', 'textAnnotation', 600, 50, 120, 45, 'p-sales'),
  bpmnShape('p-sub', 'Pack items', 'subProcess', 100, 200, 270, 140, 'p-office'),
  bpmnShape('p-pack', 'Scan item', 'task', 120, 245, 90, 60, 'p-sub'),
  bpmnShape('p-wait', 'Payment received', 'catchEvent', 420, 250, 27, 27, 'p-office'),
  bpmnShape('p-end', 'Order closed', 'endEvent', 540, 250, 27, 27, 'p-office'),
  bpmnShape('p-group', 'Billing steps', 'group', 620, 200, 170, 140, 'p-office'),
  bpmnShape('p-supplier', 'Supplier', 'pool', 0, 440, 820, 160),
  bpmnShape('p-deliver', 'Deliver goods', 'task', 380, 490, 90, 60, 'p-supplier'),
]
const PROCESS_EDGES = [
  bpmnEdge('f-1', 'sequenceFlow', 'p-start', 'p-check', 'Order received → Check order'),
  bpmnEdge('f-2', 'sequenceFlow', 'p-check', 'p-valid', 'Check order → Valid?'),
  bpmnEdge('f-3', 'sequenceFlow', 'p-valid', 'p-ship', 'Valid? → Ship order'),
  bpmnEdge('f-4', 'messageFlow', 'p-customer', 'p-supplier', 'Customer → Supplier'),
  bpmnEdge('f-5', 'sequenceFlow', 'p-ship', 'p-deliver', 'Ship order → Deliver goods'),
  bpmnEdge('f-6', 'association', 'p-note', 'p-check', 'Pays within 14 days → Check order'),
]

export const BpmnProcess: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: PROCESS, edges: PROCESS_EDGES, viewpoint: bpmnViewpoint() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('group', { name: 'User Task: Check order' })).toBeInTheDocument()
    await expect(canvas.getByRole('group', { name: 'Exclusive Gateway: Valid?' })).toBeInTheDocument()
    await expect(canvas.getByRole('group', { name: 'Pool: Customer' })).toBeInTheDocument()
    // the palette is grouped
    await expect(canvas.getByRole('group', { name: 'Pools and lanes' })).toBeInTheDocument()
    await expect(canvas.getByRole('group', { name: 'Activities' })).toBeInTheDocument()
    // the flow across the pool boundary is marked, the others are not
    await expect(canvas.getByRole('button', { name: '1 BPMN warning' })).toBeInTheDocument()
    await expect(canvas.getAllByText(/Sequence flow crosses a pool boundary/).length).toBeGreaterThan(0)
    await expect(canvas.getByRole('button', { name: 'Connection Order received → Check order' })).toBeInTheDocument()
  },
}

// lanes that stack in a pool, nested lanes and a collapsed sub-process (us-074-bpmn-pools-and-lanes)
const LANES = [
  bpmnShape('l-pool', 'Customer', 'pool', 0, 0, 760, 400),
  bpmnShape('l-sales', 'Sales', 'lane', 30, 0, 730, 160, 'l-pool'),
  bpmnShape('l-office', 'Back office', 'lane', 30, 160, 730, 240, 'l-pool'),
  bpmnShape('l-billing', 'Billing', 'lane', 60, 160, 700, 110, 'l-office'),
  bpmnShape('l-shipping', 'Shipping', 'lane', 60, 270, 700, 130, 'l-office'),
  bpmnShape('l-check', 'Check order', 'userTask', 100, 50, 90, 60, 'l-sales'),
  bpmnShape('l-invoice', 'Send invoice', 'task', 120, 190, 90, 60, 'l-billing'),
  { ...bpmnShape('l-pack', 'Pack items', 'subProcess', 120, 290, 270, 100, 'l-shipping'), collapsed: true },
  bpmnShape('l-scan', 'Scan item', 'task', 140, 320, 90, 60, 'l-pack'),
]

/** Lanes stack in a pool, nested lanes stack in their lane, and a collapsed sub-process hides its content. */
export const StackedLanes: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: LANES, viewpoint: bpmnViewpoint() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('group', { name: 'Lane: Billing' })).toBeInTheDocument()
    await expect(canvas.getByRole('group', { name: 'Lane: Shipping' })).toBeInTheDocument()
    // the sub-process is collapsed: its task is hidden and the button expands it
    await expect(canvas.queryByRole('group', { name: 'Task: Scan item' })).toBeNull()
    await userEvent.click(canvas.getByRole('button', { name: 'Expand sub-process Pack items' }))
    await waitFor(() => expect(canvas.getByRole('button', { name: 'Collapse sub-process Pack items' })).toBeInTheDocument())
    await expect(canvas.getByText('Scan item')).toBeInTheDocument()
    // and collapses again: real pointer clicks reach the toggle although Vue Flow selects the node on the same click
    await userEvent.click(canvas.getByRole('button', { name: 'Collapse sub-process Pack items' }))
    await waitFor(() => expect(canvas.getByRole('button', { name: 'Expand sub-process Pack items' })).toBeInTheDocument())
    await expect(canvas.queryByText('Scan item')).toBeNull()
  },
}

/** A selected lane offers the icons to insert a lane next to it; a reader sees none. */
export const InsertLane: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: LANES, viewpoint: bpmnViewpoint() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('group', { name: 'Lane: Sales' }))
    await expect(await canvas.findByRole('button', { name: 'Insert lane above' })).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Insert lane below' }))
    await expect(await canvas.findByRole('textbox', { name: /Name of the new/ })).toBeInTheDocument()
  },
}

export const InsertLaneReadOnly: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_READER, shapes: LANES, viewpoint: bpmnViewpoint() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('group', { name: 'Lane: Sales' }))
    await expect(canvas.queryByRole('button', { name: 'Insert lane below' })).toBeNull()
  },
}

// a vertical pool: the label band at the top and the lanes side by side
const VERTICAL = [
  { ...bpmnShape('v-pool', 'Customer', 'pool', 0, 0, 420, 420), orientation: 'vertical' as const },
  bpmnShape('v-sales', 'Sales', 'lane', 0, 30, 200, 390, 'v-pool'),
  bpmnShape('v-office', 'Back office', 'lane', 200, 30, 220, 390, 'v-pool'),
  bpmnShape('v-check', 'Check order', 'userTask', 50, 90, 90, 60, 'v-sales'),
  bpmnShape('v-ship', 'Ship order', 'task', 250, 200, 90, 60, 'v-office'),
]

/** A vertical pool with lanes side by side; its lane offers the icons "Insert lane left" and "Insert lane right". */
export const VerticalPool: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: VERTICAL, viewpoint: bpmnViewpoint() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('group', { name: 'Lane: Sales' }))
    await expect(await canvas.findByRole('button', { name: 'Insert lane left' })).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Insert lane right' })).toBeInTheDocument()
  },
}

// assignments of lanes, pools and tasks (us-075-lane-assignments): the badge and chips of a lane, the avatar of a user task,
// the assignment search and the lanes of roles that assign instead of nest
const ASSIGNED = [
  bpmnShape('a-pool', 'Customer', 'pool', 0, 0, 760, 300),
  withAssignments(bpmnShape('a-lane', 'Purchasing', 'lane', 30, 0, 730, 150, 'a-pool'), { orgUnits: [assignment('Purchasing Department', 701)], roles: [assignment('Approver', 711), assignment('Reviewer', 712)] }),
  bpmnShape('a-clerk', 'Sales', 'lane', 30, 150, 730, 150, 'a-pool'),
  withAssignments(bpmnShape('a-task', 'Approve order', 'userTask', 120, 40, 90, 60, 'a-lane'), { persons: [assignment('Maria Müller', 721)] }),
  bpmnShape('a-plain', 'Check order', 'task', 300, 40, 90, 60, 'a-lane'),
]

const ASSIGNMENT_ARGS = {
  organization: organizationModel(),
  metatypes: organizationMetatypes(),
  assignmentTargets: assignmentTargets(),
  viewpoint: bpmnViewpoint(),
}

/** A lane shows the badge of its organizational unit and the chips of its roles, a user task the avatar of its person. */
export const LaneAssignments: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: ASSIGNED, ...ASSIGNMENT_ARGS }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Purchasing Department')).toBeInTheDocument()
    await expect(canvas.getByText('Approver')).toBeInTheDocument()
    await expect(canvas.getByText('Reviewer')).toBeInTheDocument()
    // a real right click opens the context menu of the lane without an entry for the saved unit (replacing it needs entity:delete)
    const sales = await canvas.findByRole('group', { name: 'Lane: Sales' })
    await userEvent.pointer({ keys: '[MouseRight]', target: sales })
    await userEvent.click(await canvas.findByRole('menuitem', { name: 'Assign organizational unit' }))
    const dialog = within(await within(document.body).findByRole('dialog'))
    await userEvent.click(await dialog.findByRole('button', { name: /Sales Division/ }))
    await waitFor(() => expect(canvas.getByText('Sales Division')).toBeInTheDocument())
    await expect(canvas.getByText('Unsaved changes')).toBeInTheDocument()
  },
}

/** The details panel of a user task lists its responsible person; "Add" searches the persons. */
export const UserTaskPerson: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: ASSIGNED, entityStates: entityStatesOf(ASSIGNED), ...ASSIGNMENT_ARGS }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByTitle('Maria Müller')).toHaveTextContent('MM')
    await userEvent.click(await canvas.findByRole('group', { name: 'User Task: Approve order' }))
    await expect(await canvas.findByText('Responsible person')).toBeInTheDocument()
    await userEvent.click(await canvas.findByTestId('details-assignment-add-person'))
    const dialog = within(await within(document.body).findByRole('dialog'))
    await userEvent.click(await dialog.findByRole('button', { name: /Kurt Weber/ }))
    await expect(await canvas.findByText('More than one responsible person')).toBeInTheDocument()
  },
}

/** A reader sees the badge, chips and avatar without a menu entry or "×". */
export const LaneAssignmentsReadOnly: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_READER, shapes: ASSIGNED, ...ASSIGNMENT_ARGS }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Purchasing Department')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: /^Remove / })).toBeNull()
    await userEvent.pointer({ keys: '[MouseRight]', target: await canvas.findByRole('group', { name: 'Lane: Purchasing' }) })
    await expect(canvas.queryByRole('menuitem', { name: 'Assign role' })).toBeNull()
  },
}

// the viewpoint maps "Role" to a lane and "Organizational Unit" to a pool: a task in a role lane is assigned to the role
const ROLE_LANES = [
  bpmnShape('r-pool', 'Order handling', 'pool', 0, 0, 760, 320),
  orgShape('r-approver', 'Approver', 'role', 30, 0, 730, 160, 'r-pool'),
  orgShape('r-clerk', 'Clerk', 'role', 30, 160, 730, 160, 'r-pool'),
  withAssignments(bpmnShape('r-task', 'Approve order', 'userTask', 120, 40, 90, 60, 'r-approver'), { roles: [assignment('Approver', 0)] }),
]

/** A task dragged from one role lane to the other is assigned to the new role instead of nested; the question "New parent?" does not come. */
export const RoleLanes: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_ARCHITECT, shapes: ROLE_LANES, ...ASSIGNMENT_ARGS, viewpoint: organizationViewpoint() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const task = await canvas.findByRole('group', { name: 'User Task: Approve order' })
    const box = task.getBoundingClientRect()
    const from = { clientX: box.x + box.width / 2, clientY: box.y + box.height / 2 }
    // a real pointer drag: press, move down by a lane, release
    await userEvent.pointer([
      { keys: '[MouseLeft>]', target: task, coords: from },
      { target: task, coords: { clientX: from.clientX, clientY: from.clientY + 60 } },
      { target: task, coords: { clientX: from.clientX, clientY: from.clientY + 140 } },
      { keys: '[/MouseLeft]', target: task, coords: { clientX: from.clientX, clientY: from.clientY + 140 } },
    ])
    await waitFor(() => expect(canvas.getByText('Unsaved changes')).toBeInTheDocument())
    await expect(within(document.body).queryByRole('button', { name: 'Yes' })).toBeNull()
  },
}

export const RoleLanesReadOnly: Story = {
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_READER, shapes: ROLE_LANES, ...ASSIGNMENT_ARGS, viewpoint: organizationViewpoint() }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('group', { name: 'Role: Approver' })).toBeInTheDocument()
    await expect(canvas.queryByText('Unsaved changes')).toBeNull()
  },
}
