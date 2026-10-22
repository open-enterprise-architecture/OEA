import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import { onBeforeMount } from 'vue'
import { useRouter } from 'vue-router'
import CatalogPage from '../pages/CatalogPage.vue'
import CatalogsPage from '../pages/CatalogsPage.vue'
import { APPLICATIONS, CATALOG, CATALOGS, catalogApi, manyApplications, METATYPES, ME_ARCHITECT, ME_VIEWER, type Application } from '../test/catalogs'
import { routedFetch } from '../test/fetch'

// SCR-020 Catalog Browser (UC-06, US-046, US-049): a catalog with its entity table and the
// catalog overview (docs/screens/SCREENS.md). The first story is the screen image.
function overviewApi(me: unknown, catalogs: unknown[]): typeof fetch {
  return routedFetch({
    '/api/v1/me': { body: me },
    '/api/v1/catalogs': { body: catalogs },
    '/api/v1/metamodel/metatypes': { body: METATYPES },
  })
}

/** The catalog page with the applications; `query` is the view state in the URL (ADR-044). */
function catalog(applications: Application[], query: Record<string, string> = {}): Story {
  return {
    render: () => ({
      components: { CatalogPage },
      setup: () => {
        const router = useRouter()
        onBeforeMount(() => router.replace({ query }))
        return { fetchFn: catalogApi(applications), id: CATALOG.id }
      },
      template: '<CatalogPage :id="id" :fetch-fn="fetchFn" />',
    }),
  }
}

const meta: Meta<typeof CatalogsPage> = {
  title: 'Screens/SCR-020 Catalog Browser',
  component: CatalogsPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const CatalogTable: Story = catalog(APPLICATIONS)

export const SortedDescending: Story = catalog(APPLICATIONS, { sort: 'name:desc' })

export const SeveralPages: Story = catalog(manyApplications(120), { page: '1' })

export const SortByClicking: Story = {
  ...catalog(APPLICATIONS),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const name = await canvas.findByTestId('sort-name')
    await userEvent.click(name)
    await expect(await canvas.findByText('↑')).toBeInTheDocument()
    await userEvent.click(name)
    await expect(await canvas.findByText('↓')).toBeInTheDocument()
    const rows = canvasElement.querySelectorAll('[data-testid="catalog-table"] tbody tr')
    await expect(rows[0]?.textContent).toContain('Intranet')
  },
}

export const EmptyCatalog: Story = catalog([])

export const CatalogList: Story = { args: { fetchFn: overviewApi(ME_ARCHITECT, CATALOGS) } }

export const EmptyOverview: Story = { args: { fetchFn: overviewApi(ME_VIEWER, []) } }
