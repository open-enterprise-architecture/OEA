import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import ServiceAccountsPage from '../pages/ServiceAccountsPage.vue'
import { session } from '../session/store'
import { ME_SYSTEM_ADMIN, serviceAccountApi } from '../test/service-accounts'

// SCR-041 Service accounts (UC-02, UC-23, US-142, US-149; design D8 of us-142-service-accounts):
// the accounts with status, roles, last use and secret expiry, the dialogs "New service account"
// with the secret shown once, "Edit roles" with only assignable roles, and the confirmation of a
// deactivation (docs/screens/SCREENS.md). The first story is the screen image.

function screen(fetchFn: typeof fetch): Story {
  return {
    render: () => ({
      components: { AppHeader, ServiceAccountsPage },
      setup: () => {
        session.me = ME_SYSTEM_ADMIN as Me
        session.loaded = true
        return { fetchFn }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <ServiceAccountsPage :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof ServiceAccountsPage> = {
  title: 'Screens/SCR-041 Service Accounts',
  component: ServiceAccountsPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const List: Story = {
  ...screen(serviceAccountApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('ci-scanner')).toBeInTheDocument()
    await expect(canvas.getByText('Expires soon')).toBeInTheDocument()
    await expect(canvas.getByText('Deactivated')).toBeInTheDocument()
    await expect(canvas.getByRole('link', { name: 'Service accounts' })).toBeInTheDocument()
  },
}

export const Create: Story = {
  ...screen(serviceAccountApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New service account' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByRole('textbox', { name: /Name/ }), 'backup-agent')
    await expect(body.getByTestId('service-account-role-integration-writer')).toBeInTheDocument()
    await expect(body.queryByTestId('service-account-role-system-admin')).not.toBeInTheDocument()
  },
}

export const Secret: Story = {
  ...screen(serviceAccountApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New service account' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByRole('textbox', { name: /Name/ }), 'backup-agent')
    await userEvent.click(body.getByRole('button', { name: 'Create service account' }))
    await expect(await body.findByText(/It is shown only once/)).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Copy' })).toBeInTheDocument()
  },
}

export const EditRoles: Story = {
  ...screen(serviceAccountApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('cmdb-importer')).toBeInTheDocument()
    await userEvent.click(canvas.getAllByRole('button', { name: 'Edit roles' })[1]!)
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Roles of cmdb-importer')).toBeInTheDocument()
    await expect(body.queryByTestId('service-account-role-system-admin')).not.toBeInTheDocument()
  },
}

export const Deactivate: Story = {
  ...screen(serviceAccountApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('ci-scanner')).toBeInTheDocument()
    await userEvent.click(canvas.getAllByRole('button', { name: 'Deactivate' })[0]!)
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText(/cannot be reactivated/)).toBeInTheDocument()
  },
}
