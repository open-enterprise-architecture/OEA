import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import DeletedEntitiesPage from '../pages/DeletedEntitiesPage.vue'
import { deletedEntitiesApi, ME_DELETED_RESTORER, ME_DELETED_WRITER } from '../test/deletedEntities'

// SCR-068 Deleted entities (REQ-159, ADR-019; design D3 of s17-deleted-entities): the deleted
// entities and connections with who deleted them and with which entity, the name and type filters,
// "Restore" with a confirmation and the notice after it (docs/screens/SCREENS.md). The first story
// is the screen image.
const meta: Meta<typeof DeletedEntitiesPage> = {
  title: 'Screens/SCR-068 Deleted entities',
  component: DeletedEntitiesPage,
}

export default meta
type Story = StoryObj<typeof meta>

/** The deleted entities: "ERP" with what its deletion took along, and the connection with its endpoints. */
export const DeletedEntities: Story = {
  args: { fetchFn: deletedEntitiesApi({ me: ME_DELETED_RESTORER }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('ERP Finance')).toBeInTheDocument()
    await expect(canvas.getAllByText('Deleted with ERP')).toHaveLength(2)
    await expect(canvas.getByRole('button', { name: 'Restore ERP' })).toBeInTheDocument()
    await expect(canvas.getByText('Connection')).toBeInTheDocument()
  },
}

export const Empty: Story = {
  args: { fetchFn: deletedEntitiesApi({ me: ME_DELETED_RESTORER, entries: [] }) },
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('No deleted entities.')).toBeInTheDocument()
  },
}

/** After "Restore" and the confirmation: the notice names what came back and what stayed deleted. */
export const AfterRestore: Story = {
  args: { fetchFn: deletedEntitiesApi({ me: ME_DELETED_RESTORER, notRestored: [{ entityId: 13, reason: 'endpoint-deleted' }] }) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Restore ERP' }))
    const dialog = within(canvasElement.ownerDocument.body)
    await expect(await dialog.findByRole('dialog')).toBeInTheDocument()
    await userEvent.click(await dialog.findByTestId('restore-entity-confirm'))
    await expect(await canvas.findByText(/Restored: ERP, ERP Finance\./)).toBeInTheDocument()
    await expect(canvas.getByText(/Stayed deleted: ERP to CRM \(the other endpoint is deleted\)\./)).toBeInTheDocument()
  },
}

/** Without `entity:restore` the page shows "No permission". */
export const NoPermission: Story = {
  args: { fetchFn: deletedEntitiesApi({ me: ME_DELETED_WRITER }) },
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText(/No permission/)).toBeInTheDocument()
  },
}
