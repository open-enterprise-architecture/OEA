import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import OverrideRolesPage from '../pages/OverrideRolesPage.vue'
import { session } from '../session/store'
import { ME_SECURITY_ADMIN, visibilityApi } from '../test/visibility'

// SCR-040 Override roles (UC-21, US-128; design D5 of us-128-override-role-list): security
// administration chooses the roles whose holders read every property; every such read is audited
// (docs/screens/SCREENS.md). The first story is the screen image.

function screen(fetchFn: typeof fetch): Story {
  return {
    render: () => ({
      components: { AppHeader, OverrideRolesPage },
      setup: () => {
        session.me = ME_SECURITY_ADMIN as Me
        session.loaded = true
        return { fetchFn }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <OverrideRolesPage :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof OverrideRolesPage> = {
  title: 'Screens/SCR-040 Override Roles',
  component: OverrideRolesPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const List: Story = {
  ...screen(visibilityApi({ customRoles: true, overrideRoles: ['migration-support'] }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByTestId('override-role-migration-support')).toBeChecked()
    await expect(canvas.getByText(/is written to the audit log/)).toBeInTheDocument()
    await expect(canvas.queryByTestId('override-empty')).toBeNull()
  },
}

export const Empty: Story = {
  ...screen(visibilityApi({ customRoles: true }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByTestId('override-empty')).toBeInTheDocument()
  },
}

export const Save: Story = {
  ...screen(visibilityApi({ customRoles: true }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByTestId('override-role-migration-support'))
    await userEvent.click(canvas.getByRole('button', { name: 'Save' }))
    await expect(await canvas.findByText('The override roles are saved.')).toBeInTheDocument()
    await expect(canvas.queryByTestId('override-empty')).toBeNull()
  },
}
