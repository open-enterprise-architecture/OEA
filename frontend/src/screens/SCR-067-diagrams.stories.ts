import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import DiagramsPage from '../pages/DiagramsPage.vue'
import { deletedDiagram, diagramApi, ME_DIAGRAM_READER, ME_DIAGRAM_RESTORER, ME_DIAGRAM_WRITER } from '../test/diagrams'

// SCR-067 Diagrams (UC-10, US-074; design D6 and D8 of us-074-diagrams): the diagrams with their
// viewpoint and notation, the name filter, "Create diagram", and for holders of entity:restore the
// deleted diagrams with "Restore" (docs/screens/SCREENS.md). The first story is the screen image.
const meta: Meta<typeof DiagramsPage> = {
  title: 'Screens/SCR-067 Diagrams',
  component: DiagramsPage,
}

export default meta
type Story = StoryObj<typeof meta>

/** A holder of `entity:write`: the diagrams with "Create diagram", "Rename" and "Delete". */
export const Diagrams: Story = {
  args: { fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('link', { name: /Order to Cash/ })).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Create diagram' })).toBeInTheDocument()
    await expect(canvas.getAllByRole('button', { name: /^Rename / })).toHaveLength(3)
    await expect(canvas.getByText('archimate3')).toBeInTheDocument()
  },
}

/** A reader: the diagrams without "Create diagram", "Rename" and "Delete". */
export const Reader: Story = {
  args: { fetchFn: diagramApi({ me: ME_DIAGRAM_READER }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('link', { name: /Order to Cash/ })).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Create diagram' })).toBeNull()
    await expect(canvas.queryByRole('button', { name: /^Rename / })).toBeNull()
    await expect(canvas.queryByRole('button', { name: /^Delete / })).toBeNull()
  },
}

export const NoDiagrams: Story = {
  args: { fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER, diagrams: [] }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('No diagrams yet.')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Create diagram' })).toBeInTheDocument()
  },
}

/** The dialog: name, description and the viewpoint, which is fixed after the creation. */
export const CreateDialog: Story = {
  args: { fetchFn: diagramApi({ me: ME_DIAGRAM_WRITER }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Create diagram' }))
    const dialog = within(canvasElement.ownerDocument.body)
    await expect(await dialog.findByRole('dialog')).toBeInTheDocument()
    await userEvent.type(await dialog.findByTestId('diagram-name'), 'Lead to Order')
    await expect(dialog.getByText('The notation of the viewpoint is used for the diagram. It can be changed later with “Change viewpoint”.')).toBeInTheDocument()
    await expect(dialog.getByRole('button', { name: 'Create' })).toBeEnabled()
  },
}

/** A holder of `entity:restore`: the switch "Show deleted" lists the deleted diagrams with "Restore". */
export const ShowDeleted: Story = {
  args: { fetchFn: diagramApi({ me: ME_DIAGRAM_RESTORER, deleted: [deletedDiagram()] }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('switch', { name: 'Show deleted' }))
    await expect(await canvas.findByRole('button', { name: 'Restore Lead to Order' })).toBeInTheDocument()
    await expect(canvas.getByText(/Deleted by Kurt/)).toBeInTheDocument()
  },
}
