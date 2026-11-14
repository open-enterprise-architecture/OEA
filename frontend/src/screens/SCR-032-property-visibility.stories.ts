import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import PropertyVisibilityPage from '../pages/PropertyVisibilityPage.vue'
import { session } from '../session/store'
import { ME_SECURITY_ADMIN, VISIBILITY_IDS, visibilityApi } from '../test/visibility'

// SCR-032 Property visibility (UC-21, US-124, US-125; design D5 of us-124-property-visibility):
// security administration sees the visibility of every property and changes it in a dialog
// (docs/screens/SCREENS.md). Above the list it chooses the entity type of persons (design D8 of
// us-127-connection-scoped-visibility). Below the list, the section "Change history" sets per role
// and metatype whether changes to fields the role may not read appear as "Restricted" or hidden
// (design D5 of us-098-entity-history). The first story is the screen image.

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
  ...screen(visibilityApi({ personMetatype: VISIBILITY_IDS.actor, connectionScoped: true }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Investment Cost')).toBeInTheDocument()
    await expect(canvas.getByText('Entity type of persons')).toBeInTheDocument()
    await expect(canvas.queryByTestId('person-metatype-missing')).toBeNull()
    await expect(within(canvas.getByTestId('visibility-table')).getAllByText('Restricted')).toHaveLength(2)
    await expect(canvas.getByRole('link', { name: 'Property visibility' })).toBeInTheDocument()
  },
}

export const PersonMetatypeMissing: Story = {
  ...screen(visibilityApi({ connectionScoped: true }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByTestId('person-metatype-missing')).toBeInTheDocument()
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

export const ChangeHistory: Story = {
  ...screen(visibilityApi({ personMetatype: VISIBILITY_IDS.actor }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const section = within(await canvas.findByTestId('history-visibility'))
    await expect(section.getByText(/the more open setting applies/)).toBeInTheDocument()
    await userEvent.click(within(section.getByTestId('history-visibility-mode-viewer')).getByRole('radio', { name: 'Hidden' }))
    await userEvent.click(section.getByRole('button', { name: 'Save' }))
    await expect(await section.findByText('The change history settings are saved.')).toBeInTheDocument()
  },
}
