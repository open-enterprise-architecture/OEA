import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import PropertyVisibilityPage from '../pages/PropertyVisibilityPage.vue'
import { session } from '../session/store'
import { ME_SECURITY_ADMIN, visibilityApi } from '../test/visibility'

// SCR-032 Property visibility (UC-21, US-124, US-125; design D5 of us-124-property-visibility):
// security administration sees the visibility of every property and changes it in a dialog
// (docs/screens/SCREENS.md). The first story is the screen image.

function screen(fetchFn: typeof fetch): Story {
  return {
    render: () => ({
      components: { AppHeader, PropertyVisibilityPage },
      setup: () => {
        session.me = ME_SECURITY_ADMIN as Me
        session.loaded = true
        return { fetchFn }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <PropertyVisibilityPage :fetch-fn="fetchFn" />`,
    }),
  }
}

/** Opens the dialog of a property and chooses a mode. */
async function openDialog(canvasElement: HTMLElement, property: string, mode?: RegExp) {
  const canvas = within(canvasElement)
  await userEvent.click(await canvas.findByRole('button', { name: `Edit the visibility of ${property}` }))
  const body = within(canvasElement.ownerDocument.body)
  if (mode) {
    await userEvent.click(await body.findByRole('radio', { name: mode }))
  }
  return body
}

const meta: Meta<typeof PropertyVisibilityPage> = {
  title: 'Screens/SCR-032 Property Visibility',
  component: PropertyVisibilityPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const List: Story = {
  ...screen(visibilityApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Investment Cost')).toBeInTheDocument()
    await expect(canvas.getByText('Restricted')).toBeInTheDocument()
    await expect(canvas.getByRole('link', { name: 'Property visibility' })).toBeInTheDocument()
  },
}

export const RoleBased: Story = {
  ...screen(visibilityApi().fetch),
  play: async ({ canvasElement }) => {
    const body = await openDialog(canvasElement, 'Security Classification', /Role-based/)
    await expect(await body.findByText('Read roles')).toBeInTheDocument()
    await expect(body.queryByText('Connection type')).toBeNull()
  },
}

export const ConnectionBased: Story = {
  ...screen(visibilityApi().fetch),
  play: async ({ canvasElement }) => {
    const body = await openDialog(canvasElement, 'Security Classification', /Connection-based/)
    await expect(await body.findByText('Connection type')).toBeInTheDocument()
    await expect(body.queryByText('Read roles')).toBeNull()
  },
}

export const InlineError: Story = {
  ...screen(visibilityApi().fetch),
  play: async ({ canvasElement }) => {
    const body = await openDialog(canvasElement, 'Security Classification', /Role-based/)
    await userEvent.click(await body.findByRole('button', { name: 'Save' }))
    await expect(await body.findByText('Choose at least one read role.')).toBeInTheDocument()
  },
}
