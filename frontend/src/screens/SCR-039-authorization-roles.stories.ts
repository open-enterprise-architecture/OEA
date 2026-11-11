import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import AuthorizationRolesPage from '../pages/AuthorizationRolesPage.vue'
import { session } from '../session/store'
import { ME_SECURITY_ADMIN, visibilityApi } from '../test/visibility'

// SCR-039 Authorization roles (UC-23 A5, US-151; design D5 of us-128-override-role-list): the
// built-in and custom roles with their permissions and markers, and the dialog "New role"
// (docs/screens/SCREENS.md). The first story is the screen image.

function screen(fetchFn: typeof fetch): Story {
  return {
    render: () => ({
      components: { AppHeader, AuthorizationRolesPage },
      setup: () => {
        session.me = ME_SECURITY_ADMIN as Me
        session.loaded = true
        return { fetchFn }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <AuthorizationRolesPage :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof AuthorizationRolesPage> = {
  title: 'Screens/SCR-039 Authorization Roles',
  component: AuthorizationRolesPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const List: Story = {
  ...screen(visibilityApi({ customRoles: true, overrideRoles: ['migration-support'] }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Migration support')).toBeInTheDocument()
    await expect(canvas.getAllByText('Built-in')).toHaveLength(3)
    await expect(canvas.getByText('Override role')).toBeInTheDocument()
    await expect(canvas.getByRole('link', { name: 'Authorization roles' })).toBeInTheDocument()
  },
}

export const NewRole: Story = {
  ...screen(visibilityApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New role' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByRole('textbox', { name: /ID/ }), 'iso')
    await userEvent.type(body.getByRole('textbox', { name: /Name/ }), 'Information Security Officer')
    await userEvent.click(body.getByRole('button', { name: 'Create role' }))
    await expect(await canvas.findByText('Information Security Officer')).toBeInTheDocument()
  },
}

export const Rejected: Story = {
  ...screen(visibilityApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New role' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByRole('textbox', { name: /ID/ }), 'combined')
    await userEvent.type(body.getByRole('textbox', { name: /Name/ }), 'Combined')
    await userEvent.click(body.getByTestId('role-permission-metamodel:write'))
    await userEvent.click(body.getByTestId('role-permission-visibility:configure'))
    await userEvent.click(body.getByRole('button', { name: 'Create role' }))
    await expect(await body.findByText(/combines metamodel administration/)).toBeInTheDocument()
  },
}
