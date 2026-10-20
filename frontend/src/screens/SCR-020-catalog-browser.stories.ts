import type { Meta, StoryObj } from '@storybook/vue3-vite'
import CatalogPage from '../pages/CatalogPage.vue'
import CatalogsPage from '../pages/CatalogsPage.vue'
import { CATALOG, CATALOGS, METATYPES, ME_ARCHITECT, ME_VIEWER } from '../test/catalogs'
import { routedFetch } from '../test/fetch'

// SCR-020 Catalog Browser (UC-06, US-046): the catalog overview and a catalog without its
// entity table; the table is added to this story by US-049 (docs/screens/SCREENS.md).
function api(me: unknown, catalogs: unknown[]): typeof fetch {
  return routedFetch({
    '/api/v1/me': { body: me },
    '/api/v1/catalogs': { body: catalogs },
    '/api/v1/metamodel/metatypes': { body: METATYPES },
    [`/api/v1/catalogs/${CATALOG.id}`]: { body: CATALOG },
  })
}

const meta: Meta<typeof CatalogsPage> = {
  title: 'Screens/SCR-020 Catalog Browser',
  component: CatalogsPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const CatalogList: Story = { args: { fetchFn: api(ME_ARCHITECT, CATALOGS) } }

export const EmptyOverview: Story = { args: { fetchFn: api(ME_VIEWER, []) } }

export const EmptyCatalog: Story = {
  render: () => ({
    components: { CatalogPage },
    setup: () => ({ fetchFn: api(ME_ARCHITECT, CATALOGS), id: CATALOG.id }),
    template: '<CatalogPage :id="id" :fetch-fn="fetchFn" />',
  }),
}
