import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import { onBeforeMount } from 'vue'
import { useRouter } from 'vue-router'
import CatalogPage from '../pages/CatalogPage.vue'
import CatalogsPage from '../pages/CatalogsPage.vue'
import { setLanguage } from '../i18n'
import { loadLabels } from '../i18n/labels'
import {
  ACTIVE_SYSTEMS,
  APPLICATIONS,
  CATALOG,
  CATALOGS,
  catalogApi,
  COMPACT_VIEW,
  FLOW_CATALOG,
  FLOWS,
  FULL_VIEW,
  INTERFACES_JOIN,
  manyApplications,
  METATYPES,
  METATYPES_WITH_FLOWS,
  SAP_ONLY,
  ME_ARCHITECT,
  ME_VIEWER,
  STATUS_DEFINITION,
  VENDOR_DEFINITION,
  type Application,
} from '../test/catalogs'
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
function catalog(
  applications: Application[],
  query: Record<string, string | string[]> = {},
  options: { catalog?: { id: string; [key: string]: unknown }; metatypes?: unknown[] } = {},
): Story {
  return {
    render: () => ({
      components: { CatalogPage },
      setup: () => {
        const router = useRouter()
        onBeforeMount(() => router.replace({ query }))
        return { fetchFn: catalogApi(applications, options), id: options.catalog?.id ?? CATALOG.id }
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

// US-140 AC1: the catalog in German with the labels of the starter package
export const GermanLabels: Story = {
  render: () => ({
    components: { CatalogPage },
    setup: () => {
      const fetchFn = catalogApi(APPLICATIONS, { me: { ...ME_ARCHITECT, uiLanguage: 'de' } })
      setLanguage('de')
      void loadLabels('de', fetchFn)
      return { fetchFn, id: CATALOG.id }
    },
    template: '<CatalogPage :id="id" :fetch-fn="fetchFn" />',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Anwendungskomponente')).toBeInTheDocument()
    await expect(await canvas.findAllByText('Aktiv')).toHaveLength(2)
  },
}

// US-047: the dialog "Configure columns" with the status first under its own header
export const ColumnConfiguration: Story = {
  ...catalog(APPLICATIONS),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Configure columns' }))
    const dialog = within(canvasElement.ownerDocument.body)
    await expect(await dialog.findByText('External ID')).toBeInTheDocument()
    await userEvent.click(dialog.getByRole('button', { name: 'Move Status up' }))
    await expect(dialog.getAllByTestId('column-name')[5]).toHaveTextContent('Status')
  },
}

// US-047: a configured catalog with its own headers
export const ConfiguredColumns: Story = {
  render: () => ({
    components: { CatalogPage },
    setup: () => ({
      fetchFn: catalogApi(APPLICATIONS, {
        catalog: {
          ...CATALOG,
          columns: [
            { propertyDefId: STATUS_DEFINITION.id, displayLabel: 'Operational Status' },
            { field: 'name' },
            { propertyDefId: VENDOR_DEFINITION.id },
            { field: 'description' },
          ],
        },
      }),
      id: CATALOG.id,
    }),
    template: '<CatalogPage :id="id" :fetch-fn="fetchFn" />',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Operational Status')).toBeInTheDocument()
    await expect(await canvas.findByText('Salesforce')).toBeInTheDocument()
  },
}


// US-048: the dialog "Joins" with the join "Interfaces" and the steps of a new join
export const Joins: Story = {
  ...catalog(APPLICATIONS, {}, { catalog: { ...CATALOG, joinDefinitions: [INTERFACES_JOIN] } }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('REST API')).toBeInTheDocument()
    await userEvent.click(await canvas.findByRole('button', { name: 'Joins' }))
    const dialog = within(canvasElement.ownerDocument.body)
    await expect(await dialog.findByTestId('join-name')).toHaveTextContent('Interfaces')
    await userEvent.click(dialog.getByTestId('join-add'))
    await expect(await dialog.findByText('Step 1 of 4')).toBeInTheDocument()
  },
}

// US-050: the join "Interfaces" in rows mode, one row per interface
export const JoinRows: Story = {
  ...catalog(APPLICATIONS, { mode: `${INTERFACES_JOIN.id}:rows` }, { catalog: { ...CATALOG, joinDefinitions: [INTERFACES_JOIN] } }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('SFTP Export')).toBeInTheDocument()
    const rows = canvasElement.querySelectorAll('[data-testid="catalog-table"] tbody tr')
    await expect(rows).toHaveLength(4)
  },
}

// US-080: a catalog of information flows with the source and the target of each flow
export const ConnectionCatalog: Story = {
  ...catalog(FLOWS, {}, { catalog: FLOW_CATALOG, metatypes: METATYPES_WITH_FLOWS }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Invoices')).toBeInTheDocument()
    await expect(canvas.getByTestId('sort-source.name')).toHaveTextContent('Source')
    await expect(canvas.getByTestId('sort-target.name')).toHaveTextContent('Target')
  },
}

// US-051: the filter bar with a filter on the status and one on a join column, combined with OR
export const Filters: Story = {
  ...catalog(
    APPLICATIONS,
    { filter: ['status:eq:planned', `${INTERFACES_JOIN.id}.protocol:eq:REST`], filterMode: 'or' },
    { catalog: { ...CATALOG, joinDefinitions: [INTERFACES_JOIN] } },
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Status equals planned')).toBeInTheDocument()
    await expect(canvas.getByText('Interfaces · Protocol equals REST')).toBeInTheDocument()
    const rows = canvasElement.querySelectorAll('[data-testid="catalog-table"] tbody tr')
    await expect(rows).toHaveLength(2)
    await userEvent.click(canvas.getByRole('button', { name: 'Add filter' }))
    await expect(await within(canvasElement.ownerDocument.body).findByTestId('filter-form')).toBeInTheDocument()
  },
}

// US-052: the quick selection of the saved filters, one of them no longer available
export const SavedFilters: Story = {
  ...catalog(APPLICATIONS, { filter: 'status:eq:active' }, { catalog: { ...CATALOG, savedFilters: [ACTIVE_SYSTEMS, SAP_ONLY] } }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Status equals active')).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Save filter' }))
    const dialog = within(canvasElement.ownerDocument.body)
    await expect(await dialog.findByTestId('filter-save-form')).toBeInTheDocument()
  },
}

// US-053: the default view "Compact view" applied on opening, with the view selection
export const Views: Story = {
  ...catalog(APPLICATIONS, {}, { catalog: { ...CATALOG, savedViews: [{ ...COMPACT_VIEW, isDefault: true }, FULL_VIEW] } }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Status equals active')).toBeInTheDocument()
    const rows = canvasElement.querySelectorAll('[data-testid="catalog-table"] tbody tr')
    await expect(rows).toHaveLength(2)
    await userEvent.click(canvas.getByRole('button', { name: 'Views' }))
    await expect(await within(canvasElement.ownerDocument.body).findByText('Make default')).toBeInTheDocument()
  },
}

// US-053: "Save view" with the columns of the table, filters, join modes and sort
export const SaveView: Story = {
  ...catalog(APPLICATIONS, { filter: 'status:eq:active', sort: 'name:desc' }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Save view' }))
    const dialog = within(canvasElement.ownerDocument.body)
    await expect(await dialog.findByTestId('view-save-form')).toBeInTheDocument()
    await expect(dialog.getAllByTestId('view-column')).toHaveLength(2)
  },
}

