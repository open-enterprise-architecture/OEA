import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import PlateausPage from '../pages/PlateausPage.vue'
import { session } from '../session/store'
import { ME_ENTERPRISE_ARCHITECT, ME_PLATEAU_READER, plateauApi, PLATEAUS } from '../test/plateaus'

// SCR-065 Plateaus (UC-11, US-084, US-085, US-089, US-155; design D8 of us-084-plateaus): the
// baseline highlighted, the target and transition plateaus with their planned date, predecessor,
// basis and number of solutions, the realized plateaus collapsed, and the dialog "New plateau"
// with predecessor and basis (docs/screens/SCREENS.md). The first story is the screen image.

function screen(fetchFn: typeof fetch, me: unknown = ME_ENTERPRISE_ARCHITECT): Story {
  return {
    render: () => ({
      components: { AppHeader, PlateausPage },
      setup: () => {
        session.me = me as Me
        session.loaded = true
        return { fetchFn }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <PlateausPage :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof PlateausPage> = {
  title: 'Screens/SCR-065 Plateaus',
  component: PlateausPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const Grouped: Story = {
  ...screen(plateauApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Baseline 2026-Q2')).toBeInTheDocument()
    await expect(canvas.getByText('Target Cloud Migration 2027')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: /Realized plateaus \(1\)/ })).toBeInTheDocument()
    await expect(canvas.getByRole('link', { name: 'Plateaus' })).toBeInTheDocument()
    // "Go live" on the plateaus that succeed the baseline (us-086-plateau-go-live)
    await expect(canvas.getAllByRole('button', { name: 'Go live' })).toHaveLength(2)
  },
}

export const NoTarget: Story = {
  ...screen(plateauApi({ plateaus: [PLATEAUS[0]!] }).fetch),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('No target plateau planned yet')).toBeInTheDocument()
  },
}

export const NoBaseline: Story = {
  ...screen(plateauApi({ plateaus: [] }).fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText(/No baseline plateau exists yet/)).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Create initial baseline' })).toBeInTheDocument()
  },
}

export const NewPlateau: Story = {
  ...screen(plateauApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New plateau' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByTestId('plateau-name'), 'Target 2029')
    await expect(body.getByText('Content of the predecessor')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Create plateau' })).toBeEnabled()
  },
}

export const ReadOnly: Story = {
  ...screen(plateauApi({ me: ME_PLATEAU_READER }).fetch, ME_PLATEAU_READER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Target 2028')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'New plateau' })).toBeNull()
    await expect(canvas.queryByRole('button', { name: 'Edit' })).toBeNull()
  },
}
