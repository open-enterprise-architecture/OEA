import USlideover from '@nuxt/ui/components/Slideover.vue'
import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, fireEvent, userEvent, waitFor, within } from 'storybook/test'
import AppHeader from '../components/AppHeader.vue'
import AppSidebar from '../components/AppSidebar.vue'
import NavigationTree from '../components/NavigationTree.vue'
import type { Me } from '../api/session'
import { clearTree, sidebarTab, type SidebarTab } from '../navigation/store'
import CatalogPage from '../pages/CatalogPage.vue'
import { session } from '../session/store'
import { APPLICATIONS, CATALOG, catalogApi } from '../test/catalogs'
import {
  APPLICATIONS_ID,
  BROWSER,
  BROWSER_ITEMS,
  BROWSER_WITHOUT_PLATEAUS,
  COMPLIANCE_ID,
  HIERARCHY,
  LANDSCAPE_ID,
  ME_HIERARCHY_MANAGER,
  ME_TREE_MANAGER,
  ME_TREE_READER,
  navigationApi,
  ROOT_ID,
  SOLUTIONS,
  TREE_WITH_ENTITIES,
} from '../test/navigation'

// SCR-055 Navigation sidebar (UC-06, UC-13, US-054): the navigation tree beside every page of the
// web portal, here beside the catalog "Application Inventory" (docs/screens/SCREENS.md). The first
// story is the screen image. Entities and solutions are entries too, a deleted entity is marked, and
// folders and entries move by drag and drop and by "Move up", "Move down" and "Move to…"
// (US-095, US-096; design D5 of us-095-navigation-tree-items). The sidebar has two tabs (US-132): "Browser" is
// active when it opens and shows "Current state", the plateaus and the projects with their solutions; the
// stories of the tree choose the tab "Navigation tree" first. The first story is the screen image.

/** The shell with header, sidebar and the catalog page, for the person `me`. */
function shell(
  me: unknown,
  expanded: string[] = [ROOT_ID, LANDSCAPE_ID],
  tree = undefined as typeof TREE_WITH_ENTITIES | undefined,
  extra: { tab?: SidebarTab; browser?: typeof BROWSER; browserItems?: typeof BROWSER_ITEMS; components?: typeof HIERARCHY } = {},
): Story {
  return {
    render: () => ({
      components: { AppHeader, AppSidebar, CatalogPage },
      setup: () => {
        session.me = me as Me
        session.loaded = true
        clearTree()
        sidebarTab.value = extra.tab ?? 'tree'
        sessionStorage.setItem('oea.navigation.expanded', JSON.stringify(expanded))
        return {
          fetchFn: navigationApi({ tree, browser: extra.browser, browserItems: extra.browserItems, components: extra.components, fallback: catalogApi(APPLICATIONS, { me }) }),
          id: CATALOG.id,
        }
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

export const BrowserTab: Story = {
  ...shell(ME_TREE_MANAGER, undefined, undefined, { tab: 'browser' }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('tab', { name: 'Browser' })).toHaveAttribute('aria-selected', 'true')
    const top = (await canvas.findAllByTestId('browser-label')).map((label) => label.textContent?.replace(/\s+/g, ' ').trim())
    await expect(top).toEqual(['Current state', '2027 (1)', 'TO-BE 2030 (0)', 'Projects (1)'])
    await userEvent.click(canvas.getByRole('button', { name: 'Expand Current state' }))
    await userEvent.click(await canvas.findByRole('button', { name: 'Expand Components' }))
    await expect(await canvas.findByRole('link', { name: 'SAP ERP' })).toHaveAttribute('href', '/entities/9101/history')
    await userEvent.click(canvas.getByRole('button', { name: 'Expand 2027' }))
    await userEvent.click(await canvas.findByRole('button', { name: 'Expand Cloud Migration' }))
    // the Components of the current state are expanded, so this is the one of the solution
    await userEvent.click(canvas.getByRole('button', { name: 'Expand Components' }))
    await expect(await canvas.findByText('CRM Cloud')).toBeVisible()
    await expect(canvas.getByText('Retiring')).toBeVisible()
  },
}

/** The category Diagrams of "Current state": the diagrams by name, each with the notation of its viewpoint, linked to its page. */
export const BrowserDiagrams: Story = {
  ...shell(ME_TREE_READER, undefined, undefined, { tab: 'browser' }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Expand Current state' }))
    await userEvent.click(await canvas.findByRole('button', { name: /^Expand Diagrams/ }))
    await expect(await canvas.findByRole('link', { name: 'Order to Cash' })).toHaveAttribute('href', '/diagrams/a1000000-0000-4000-8000-000000000001')
    await expect(canvas.getByText('archimate3')).toBeVisible()
    await expect(canvas.getByText('bpmn2')).toBeVisible()
  },
}

export const BrowserWithoutPlateaus: Story = {
  ...shell(ME_TREE_READER, undefined, undefined, { tab: 'browser', browser: BROWSER_WITHOUT_PLATEAUS }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const top = (await canvas.findAllByTestId('browser-label')).map((label) => label.textContent?.replace(/\s+/g, ' ').trim())
    await expect(top).toEqual(['Current state', 'Projects (0)'])
    await expect(canvas.getByRole('button', { name: /^Projects/ })).toHaveAttribute('data-empty', 'true')
  },
}

export const BrowserEmptyCategory: Story = {
  ...shell(ME_TREE_READER, undefined, undefined, { tab: 'browser', browser: { ...BROWSER, currentState: { components: 3, connections: 0, catalogs: 2, diagrams: 0 } } }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Expand Current state' }))
    const connections = await canvas.findByRole('button', { name: 'Connections (0)' })
    await expect(connections).toHaveAttribute('data-empty', 'true')
    await expect(canvas.getByRole('button', { name: 'Components (3)' })).not.toHaveAttribute('data-empty')
  },
}

/** The components of "Current state" as a hierarchy, for a person who holds `entity:write` and `entity:delete` (US-135, US-136). */
const hierarchy = (components = HIERARCHY): Story => shell(ME_HIERARCHY_MANAGER, undefined, undefined, { tab: 'browser', components })

async function openComponents(canvas: ReturnType<typeof within>) {
  await userEvent.click(await canvas.findByRole('button', { name: 'Expand Current state' }))
  await userEvent.click(await canvas.findByRole('button', { name: /^Expand Components/ }))
}

export const BrowserHierarchy: Story = {
  ...hierarchy(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await openComponents(canvas)
    await expect(await canvas.findByRole('link', { name: 'Finance landscape' })).toBeVisible()
    // the structuring aid shows a folder and expands to its children
    await expect(canvasElement.querySelectorAll('[data-testid="browser-item-folder"]')).toHaveLength(1)
    await userEvent.click(canvas.getByRole('button', { name: 'Expand Finance landscape' }))
    await expect(await canvas.findByRole('link', { name: 'Payroll' })).toBeVisible()
  },
}

export const BrowserRefusedDropTarget: Story = {
  ...hierarchy(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await openComponents(canvas)
    const source = (await canvas.findByRole('link', { name: 'Finance module' })).closest<HTMLElement>('[data-testid="browser-entity-row"]')!
    const target = canvas.getByRole('link', { name: 'SAP S/4HANA' }).closest<HTMLElement>('[data-testid="browser-entity-row"]')!
    // dropped on itself: refused, with the marker for the refused target
    await fireEvent.dragStart(source)
    await fireEvent.dragOver(source)
    await expect(source).toHaveAttribute('data-drop', 'invalid')
    await fireEvent.dragEnd(source)
    await fireEvent.dragStart(source)
    await fireEvent.dragOver(target)
    await expect(target).toHaveAttribute('data-drop', 'valid')
    await fireEvent.dragLeave(target)
    await fireEvent.dragEnd(source)
  },
}

export const BrowserMoveTo: Story = {
  ...hierarchy(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await openComponents(canvas)
    await userEvent.click(await canvas.findByRole('button', { name: 'Actions for Finance module' }))
    await userEvent.click(await within(document.body).findByRole('menuitem', { name: 'Move to…' }))
    const dialog = within(await within(document.body).findByTestId('move-to-dialog'))
    // the dialog fades in
    await waitFor(async () => expect(await dialog.findByRole('button', { name: 'Root level' })).toBeVisible())
    await waitFor(async () => expect(await dialog.findByRole('button', { name: 'Move here: SAP S/4HANA' })).toBeVisible())
  },
}

export const BrowserDeleteStructuringAid: Story = {
  ...hierarchy(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await openComponents(canvas)
    await userEvent.click(await canvas.findByRole('button', { name: 'Actions for Finance landscape' }))
    await userEvent.click(await within(document.body).findByRole('menuitem', { name: 'Delete' }))
    await expect(await within(document.body).findByTestId('delete-aid-warning')).toHaveTextContent('1 child moves to the root level and is not deleted')
  },
}

const MARKUP = '<img src=x onerror=alert(1)>'

export const BrowserNameWithMarkup: Story = {
  ...shell(ME_TREE_READER, undefined, undefined, {
    tab: 'browser',
    browser: { ...BROWSER, projects: [{ id: SOLUTIONS[1]!.id, name: MARKUP, status: 'draft', components: 0 }] },
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Expand Projects' }))
    await expect(await canvas.findByRole('button', { name: MARKUP })).toBeVisible()
    await expect(canvasElement.querySelector('img')).toBeNull()
  },
}

export const SwitchToNavigationTree: Story = {
  ...shell(ME_TREE_MANAGER, undefined, undefined, { tab: 'browser' }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('tab', { name: 'Navigation tree' }))
    await expect(await canvas.findByText('IT Landscape')).toBeVisible()
    await expect(canvas.getByRole('tab', { name: 'Navigation tree' })).toHaveAttribute('aria-selected', 'true')
    await expect(canvas.queryByTestId('solution-browser')).toBeNull()
  },
}

export const Manager: Story = {
  ...shell(ME_TREE_MANAGER, [ROOT_ID, LANDSCAPE_ID], TREE_WITH_ENTITIES),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('link', { name: 'SAP ERP' })).toHaveAttribute('href', '/entities/9101/history')
    await expect(canvas.getByRole('link', { name: 'Cloud Migration' })).toHaveAttribute('href', expect.stringMatching(/^\/solutions\//))
    // the deleted entity keeps its entry, marked
    await expect(canvas.getByRole('link', { name: /Legacy CRM/ })).toHaveAttribute('href', '/entities/9103/history')
    await expect(canvas.getByText('Deleted', { selector: '[data-testid="tree-item-deleted"] *, [data-testid="tree-item-deleted"]' })).toBeVisible()
  },
}

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

const withEntities = (me: unknown = ME_TREE_MANAGER) => shell(me, [ROOT_ID, LANDSCAPE_ID, COMPLIANCE_ID], TREE_WITH_ENTITIES)

const entry = (canvas: ReturnType<typeof within>, name: RegExp | string) => canvas.getByRole('link', { name }).closest('[data-testid="tree-item-row"]') as HTMLElement
const folderRowOf = (canvas: ReturnType<typeof within>, name: string) =>
  canvas.getByRole('button', { name, exact: true }).closest('[data-testid="tree-folder-row"]') as HTMLElement

/** Drag and drop: an entry dropped on another entry goes before it. */
export const DragAndDrop: Story = {
  ...withEntities(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const names = () => canvas.getAllByTestId('tree-item-link').map((link) => link.textContent?.trim().replace(/\s*\(.*\)$/, ''))
    await canvas.findByRole('link', { name: 'SAP ERP' })
    const before = names()
    await fireEvent.dragStart(entry(canvas, 'Cloud Migration'))
    await fireEvent.dragOver(entry(canvas, 'SAP ERP'))
    await fireEvent.drop(entry(canvas, 'SAP ERP'))
    await waitFor(() => expect(names()).not.toEqual(before))
    await waitFor(() => expect(names().indexOf('Cloud Migration')).toBeLessThan(names().indexOf('SAP ERP')))
  },
}

/** A folder cannot be dropped onto itself or a descendant: the target is marked as invalid. */
export const InvalidDropTarget: Story = {
  ...shell(ME_TREE_MANAGER, [ROOT_ID, LANDSCAPE_ID, APPLICATIONS_ID], TREE_WITH_ENTITIES),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await canvas.findByRole('link', { name: 'SAP ERP' })
    const landscape = folderRowOf(canvas, 'IT Landscape')
    const applications = folderRowOf(canvas, 'Applications')
    await fireEvent.dragStart(landscape)
    await fireEvent.dragOver(applications)
    await expect(applications).toHaveAttribute('data-drop', 'invalid')
    await expect(within(applications).getByText('Cannot be dropped here')).toBeInTheDocument()
    await fireEvent.drop(applications)
    // nothing moved
    await expect(canvas.getByRole('link', { name: 'SAP ERP' })).toBeVisible()
  },
}

/** "Move up" and "Move down" are offered only where they lead somewhere. */
export const MoveWithTheKeyboard: Story = {
  ...withEntities(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await canvas.findByRole('link', { name: 'SAP ERP' })
    const body = within(canvasElement.ownerDocument.body)
    // the first entry of the folder: no "Move up"
    await userEvent.click(canvas.getByRole('button', { name: 'Actions for Application Inventory' }))
    await expect(await body.findByRole('menuitem', { name: 'Move down' })).toBeInTheDocument()
    await expect(body.queryByRole('menuitem', { name: 'Move up' })).toBeNull()
    await userEvent.keyboard('{Escape}')
    await userEvent.click(canvas.getByRole('button', { name: 'Actions for SAP ERP' }))
    await userEvent.click(await body.findByRole('menuitem', { name: 'Move up' }))
    await waitFor(() => {
      const links = canvas.getAllByTestId('tree-item-link').map((link) => link.textContent?.trim())
      expect(links.indexOf('SAP ERP')).toBeLessThan(links.indexOf('Application Inventory'))
    })
  },
}

/** "Move to…" offers the folders the element can go to. */
export const MoveTo: Story = {
  ...withEntities(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await canvas.findByRole('link', { name: 'SAP ERP' })
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(canvas.getByRole('button', { name: 'Actions for SAP ERP' }))
    await userEvent.click(await body.findByRole('menuitem', { name: 'Move to…' }))
    await expect(await body.findByTestId('tree-move-form')).toBeInTheDocument()
    await expect(body.getByText('Move SAP ERP')).toBeInTheDocument()
    await userEvent.click(body.getByTestId('tree-move-submit'))
    await waitFor(() => expect(body.queryByTestId('tree-move-form')).toBeNull())
    await expect(canvas.getByRole('link', { name: 'SAP ERP' })).toBeVisible()
  },
}

/** A reader can neither drag nor move entries. */
export const ReaderCannotMove: Story = {
  ...withEntities(ME_TREE_READER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await canvas.findByRole('link', { name: 'SAP ERP' })
    await expect(canvas.queryByRole('button', { name: /^Actions for/ })).toBeNull()
    await expect(entry(canvas, 'SAP ERP')).not.toHaveAttribute('draggable')
  },
}
