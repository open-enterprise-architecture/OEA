import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import MetamodelPage from '../pages/MetamodelPage.vue'
import { session } from '../session/store'
import { ME_ARCHITECT, ME_READER, metamodelApi } from '../test/metamodel'

// SCR-031 Viewpoints (UC-12, US-090, US-093; design D4 of us-090-viewpoints): the section
// "Viewpoints" of the metamodel page with the generic viewpoints and their allowed entity and
// connection types, and the dialog to create or edit one (docs/screens/SCREENS.md). The first story
// is the screen image.
function page(me: unknown, prepare?: (api: ReturnType<typeof metamodelApi>) => void): Story {
  return {
    render: () => ({
      components: { MetamodelPage },
      setup: () => {
        // the session of the previous story must not show its actions
        session.me = me as Me
        session.loaded = true
        const api = metamodelApi({ me })
        prepare?.(api)
        return { fetchFn: api.fetch }
      },
      template: '<MetamodelPage section="viewpoints" :fetch-fn="fetchFn" />',
    }),
  }
}

const meta: Meta<typeof MetamodelPage> = {
  title: 'Screens/SCR-031 Viewpoints',
  component: MetamodelPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const Viewpoints: Story = {
  ...page(ME_ARCHITECT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Application Landscape')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'New viewpoint' })).toBeInTheDocument()
  },
}

export const Reader: Story = {
  ...page(ME_READER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Application Landscape')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'New viewpoint' })).toBeNull()
  },
}

export const NewViewpoint: Story = {
  ...page(ME_ARCHITECT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New viewpoint' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByTestId('viewpoint-name'), 'Integration View')
    await expect(body.getByText('Connection types')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Save' })).toBeDisabled()
  },
}

export const ChangedMeanwhile: Story = {
  ...page(ME_ARCHITECT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Edit' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByTestId('viewpoint-description'), ' (draft)')
    await expect(body.getByRole('button', { name: 'Save' })).toBeEnabled()
  },
}
