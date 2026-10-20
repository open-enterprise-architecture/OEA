import type { Meta, StoryObj } from '@storybook/vue3-vite'
import UCard from '@nuxt/ui/components/Card.vue'
import { expect, userEvent, within } from 'storybook/test'
import CreateCatalogForm from '../components/CreateCatalogForm.vue'
import { CATALOG, METATYPES, NAME_TAKEN } from '../test/catalogs'
import { routedFetch } from '../test/fetch'

// SCR-028 Create catalog dialog (UC-06, US-046): the content of the "New catalog" modal of
// SCR-020, shown in a dialog frame (docs/screens/SCREENS.md).
function api(created: { status?: number; body: unknown; contentType?: string }): typeof fetch {
  return routedFetch({ '/api/v1/metamodel/metatypes': { body: METATYPES }, '/api/v1/catalogs': created })
}

const meta: Meta<typeof CreateCatalogForm> = {
  title: 'Screens/SCR-028 Create Catalog',
  component: CreateCatalogForm,
  render: (args) => ({
    // runtime templates do not see the auto-imported components
    components: { CreateCatalogForm, UCard },
    setup: () => ({ args }),
    template: `
      <div class="flex min-h-screen items-start justify-center bg-elevated/50 p-8">
        <UCard class="w-full max-w-lg" :ui="{ header: 'flex flex-col gap-1' }">
          <template #header>
            <h2 class="text-lg font-semibold text-highlighted">New catalog</h2>
            <p class="text-sm text-muted">Create a table view of the repository.</p>
          </template>
          <CreateCatalogForm v-bind="args" />
        </UCard>
      </div>`,
  }),
}

export default meta
type Story = StoryObj<typeof meta>

/** An enterprise architect: shared catalogs allowed. */
export const Default: Story = { args: { canManage: true, fetchFn: api({ status: 201, body: CATALOG }) } }

/** A viewer: only a personal catalog can be created. */
export const Viewer: Story = { args: { canManage: false, fetchFn: api({ status: 201, body: CATALOG }) } }

/** The name is taken: inline message at the name field. */
export const NameCollision: Story = {
  args: { canManage: true, fetchFn: api(NAME_TAKEN) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.type(await canvas.findByLabelText(/Name/), 'Application Inventory')
    await userEvent.click(canvas.getByRole('button', { name: 'Create catalog' }))
    await expect(await canvas.findByText('A catalog with this name already exists')).toBeInTheDocument()
  },
}
