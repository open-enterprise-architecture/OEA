import USlideover from '@nuxt/ui/components/Slideover.vue'
import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, waitFor, within } from 'storybook/test'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import NavigationTree from '../components/NavigationTree.vue'
import type { Me } from '../api/session'
import { clearTree } from '../navigation/store'
import CatalogPage from '../pages/CatalogPage.vue'
import { session } from '../session/store'
import { APPLICATIONS, CATALOG, catalogApi } from '../test/catalogs'
import { LANDSCAPE_ID, ME_TREE_MANAGER, ME_TREE_READER, navigationApi, ROOT_ID } from '../test/navigation'

// SCR-055 Navigation sidebar (UC-06, UC-13, US-054): the navigation tree beside every page of the
// web portal, here beside the catalog "Application Inventory" (docs/screens/SCREENS.md). The first
// story is the screen image.

/** The shell with header, sidebar and the catalog page, for the person `me`. */
function shell(me: unknown, expanded: string[] = [ROOT_ID, LANDSCAPE_ID]): Story {
  return {
    render: () => ({
      components: { AppHeader, AppSidebar, CatalogPage },
      setup: () => {
        session.me = me as Me
        session.loaded = true
        clearTree()
        sessionStorage.setItem('oea.navigation.expanded', JSON.stringify(expanded))
        return { fetchFn: navigationApi({ fallback: catalogApi(APPLICATIONS, { me }) }), id: CATALOG.id }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <div class="flex min-h-[calc(100dvh-3.5rem)]">
          <AppSidebar :fetch-fn="fetchFn" />
          <div class="min-w-0 flex-1"><CatalogPage :id="id" :fetch-fn="fetchFn" /></div>
        </div>`,
    }),
  }
}

const meta: Meta<typeof AppSidebar> = {
  title: 'Screens/SCR-055 Navigation Sidebar',
  component: AppSidebar,
}

export default meta
type Story = StoryObj<typeof meta>

export const Manager: Story = shell(ME_TREE_MANAGER)

export const Reader: Story = {
  ...shell(ME_TREE_READER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await canvas.findByText('IT Landscape')
    await expect(canvas.queryByRole('button', { name: 'Actions for IT Landscape' })).toBeNull()
    await expect(canvas.queryByRole('button', { name: 'Remove Application Inventory from the tree' })).toBeNull()
  },
}

export const FolderActions: Story = {
  ...shell(ME_TREE_MANAGER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Actions for IT Landscape' }))
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByRole('menuitem', { name: 'New folder' })).toBeInTheDocument()
    await expect(body.getByRole('menuitem', { name: 'Rename' })).toBeInTheDocument()
    await expect(body.getByRole('menuitem', { name: 'Delete' })).toBeInTheDocument()
  },
}

export const NewFolder: Story = {
  ...shell(ME_TREE_MANAGER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Actions for Repository' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('menuitem', { name: 'New folder' }))
    await userEvent.type(await body.findByTestId('tree-name-input'), 'Business Architecture')
    await userEvent.click(body.getByTestId('tree-name-submit'))
    await expect(await canvas.findByText('Business Architecture', {}, { timeout: 3000 })).toBeInTheDocument()
  },
}

export const NameTaken: Story = {
  ...shell(ME_TREE_MANAGER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Actions for Repository' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('menuitem', { name: 'New folder' }))
    await userEvent.type(await body.findByTestId('tree-name-input'), 'it landscape')
    await userEvent.click(body.getByTestId('tree-name-submit'))
    await expect(await body.findByText('A folder with this name already exists.')).toBeInTheDocument()
  },
}

export const DeleteFolder: Story = {
  ...shell(ME_TREE_MANAGER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Actions for IT Landscape' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('menuitem', { name: 'Delete' }))
    await expect(await body.findByText('This deletes the folder with 1 subfolders and 2 entries. No catalog is deleted.')).toBeInTheDocument()
    await userEvent.click(body.getByTestId('tree-delete-confirm'))
    await waitFor(() => expect(canvas.queryByText('IT Landscape')).toBeNull())
  },
}

export const RemoveFromTree: Story = {
  ...shell(ME_TREE_MANAGER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Remove My Candidates from the tree' }))
    await waitFor(() => expect(canvas.queryByText('My Candidates')).toBeNull())
    await expect(canvas.getByRole('link', { name: 'Application Inventory' })).toBeVisible()
  },
}

/** The sidebar as the slideover of a narrow screen, opened by the menu button of the header. */
export const NarrowScreen: Story = {
  render: () => ({
    components: { NavigationTree, USlideover },
    setup: () => {
      session.me = ME_TREE_READER as unknown as Me
      session.loaded = true
      clearTree()
      sessionStorage.setItem('oea.navigation.expanded', JSON.stringify([ROOT_ID, LANDSCAPE_ID]))
      return { fetchFn: navigationApi() }
    },
    template: `
      <USlideover :open="true" side="left" title="Navigation" description="Folders and catalogs of the repository">
        <template #body><NavigationTree :can-manage="false" :fetch-fn="fetchFn" /></template>
      </USlideover>`,
  }),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByRole('link', { name: 'Application Inventory' })).toBeVisible()
  },
}
