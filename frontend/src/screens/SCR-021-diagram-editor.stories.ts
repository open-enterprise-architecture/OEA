import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, waitFor, within } from 'storybook/test'
import NewParentDialog from '../components/diagram/NewParentDialog.vue'
import DiagramPage from '../pages/DiagramPage.vue'
import { diagram, diagramApi, edge, ENTITIES, FINANCE_SUITE, METATYPE_COMPONENT, METATYPE_FLOW, METATYPE_PROCESS, ME_DIAGRAM_ARCHITECT, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER, PROP_INVESTMENT, shape, type DiagramApi } from '../test/diagrams'

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
  args: { id: ID, fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, shapes: [], entities: [...ENTITIES, FINANCE_SUITE] }) },
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
