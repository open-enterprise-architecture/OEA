import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import MetamodelPage from '../pages/MetamodelPage.vue'
import { session } from '../session/store'
import { ME_ARCHITECT, ME_READER, metamodelApi } from '../test/metamodel'

// SCR-031 Viewpoints (UC-12, US-090 to US-093; design D4 of us-090-viewpoints, D6 of
// us-091-viewpoint-notations): the section "Viewpoints" of the metamodel page with the viewpoints in
// the groups "System-defined" (read-only, from packages) and "Organization", their notations and
// allowed entity and connection types, and the dialog to create or edit one with the notation and
// the mapping of each type to an element (docs/screens/SCREENS.md). The first story is the screen
// image.
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
    await expect(canvas.getByRole('heading', { name: 'System-defined' })).toBeInTheDocument()
    await expect(canvas.getByRole('heading', { name: 'Organization' })).toBeInTheDocument()
  },
}

/** A system-defined viewpoint of a package: a lock, its package, and no "Edit" and "Delete" (US-092 AC2). */
export const SystemDefined: Story = {
  ...page(ME_ARCHITECT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const group = within(await canvas.findByTestId('viewpoint-group-system'))
    await expect(await group.findByText('Technology Landscape')).toBeInTheDocument()
    await expect(group.getByText('Package oea-starter-togaf-classic 1.2.0')).toBeInTheDocument()
    await expect(group.getByText('System-defined, read-only')).toBeInTheDocument()
    await expect(group.queryByRole('button', { name: 'Edit' })).toBeNull()
    await expect(group.queryByRole('button', { name: 'Delete' })).toBeNull()
    // the organization's viewpoint keeps its actions
    await expect(within(canvas.getByTestId('viewpoint-group-organization')).getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  },
}

/** The dialog with the notation and the mapping of each selected type to an element of it. */
export const NotationMapping: Story = {
  ...page(ME_ARCHITECT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Edit' }))
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByRole('combobox', { name: 'Notation' })).toHaveTextContent('ArchiMate 3')
    const table = within(await body.findByTestId('viewpoint-mappings'))
    await expect(table.getByRole('combobox', { name: 'Element of Application Component' })).toHaveTextContent('Application Component')
    await expect(table.getByRole('combobox', { name: 'Element of Application Service' })).toHaveTextContent('None (generic)')
    await expect(table.getByRole('combobox', { name: 'Element of Data Flow' })).toHaveTextContent('None (generic)')
  },
}

/** Changing the notation clears the elements of the old one (US-091 AC3). */
export const NotationChange: Story = {
  ...page(ME_ARCHITECT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Edit' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('combobox', { name: 'Notation' }))
    await userEvent.click(await body.findByRole('option', { name: 'BPMN 2.0' }))
    const table = within(await body.findByTestId('viewpoint-mappings'))
    // the select closes and the dialog is hidden from assistive technology for a moment
    await expect(await table.findByRole('combobox', { name: 'Element of Application Component' })).toHaveTextContent('None (generic)')
    await expect(table.getByLabelText('Width: Application Component')).toBeDisabled()
  },
}

/** A mapping the server rejects is shown at the mapping. */
export const MappingRejected: Story = {
  render: () => ({
    components: { MetamodelPage },
    setup: () => {
      session.me = ME_ARCHITECT as unknown as Me
      session.loaded = true
      return { fetchFn: metamodelApi({ me: ME_ARCHITECT, rejectMappings: true }).fetch }
    },
    template: '<MetamodelPage section="viewpoints" :fetch-fn="fetchFn" />',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Edit' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('button', { name: 'Save' }))
    await expect(await body.findByText('The mapping does not fit the notation.')).toBeInTheDocument()
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
