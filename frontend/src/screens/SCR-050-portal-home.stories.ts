import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import PortalCatalogPage from '../pages/PortalCatalogPage.vue'
import PortalPage from '../pages/PortalPage.vue'
import { session } from '../session/store'
import { ME_PORTAL_READER, INVENTORY_ID, portalApi } from '../test/portal'

// SCR-050 Portal home page (UC-06; s9-portal-catalogs): the shared catalogs for a reader with
// only portal:read, and a shared catalog opened read-only (docs/screens/SCREENS.md). The first
// story is the screen image.

/** The header with the portal page `page` for Petra, a portal reader. */
function screen(page: 'home' | 'catalog', fetchFn: typeof fetch, id = INVENTORY_ID): Story {
  return {
    render: () => ({
      components: { AppHeader, PortalPage, PortalCatalogPage },
      setup: () => {
        session.me = ME_PORTAL_READER as Me
        session.loaded = true
        return { fetchFn, id, page }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <PortalPage v-if="page === 'home'" :fetch-fn="fetchFn" />
        <PortalCatalogPage v-else :id="id" :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof PortalPage> = {
  title: 'Screens/SCR-050 Portal Home',
  component: PortalPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const Overview: Story = {
  ...screen('home', portalApi()),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('link', { name: /Application Inventory/ })).toBeInTheDocument()
    await expect(canvas.getByRole('link', { name: /Compliance Overview/ })).toBeInTheDocument()
    await expect(canvas.queryByText('My Integration Candidates')).toBeNull()
    await expect(canvas.getByRole('link', { name: 'Portal' })).toBeInTheDocument()
    await expect(canvas.queryByRole('link', { name: 'Catalogs' })).toBeNull()
  },
}

export const Empty: Story = {
  ...screen('home', portalApi(undefined, { catalogs: [] })),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('No shared catalogs yet')).toBeInTheDocument()
  },
}

export const Catalog: Story = {
  ...screen('catalog', portalApi()),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('heading', { name: 'Application Inventory' })).toBeInTheDocument()
    await expect(await canvas.findByText('CRM System')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: /Configure/ })).toBeNull()
  },
}

export const NotFound: Story = {
  ...screen('catalog', portalApi(), 'c0000000-0000-4000-8000-000000000003'),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('Catalog not found')).toBeInTheDocument()
  },
}
