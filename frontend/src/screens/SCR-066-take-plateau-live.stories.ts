import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import PlateausPage from '../pages/PlateausPage.vue'
import { session } from '../session/store'
import { goLivePreview, ME_ENTERPRISE_ARCHITECT, plateauApi } from '../test/plateaus'
import { goLiveConflict } from '../test/solutions'

// SCR-066 Take plateau live (UC-11, US-086, US-087, US-088, US-157; design D7 of
// us-086-plateau-go-live): opened by "Go live" on SCR-065 for the plateau that succeeds the
// baseline. The summary of the transition, the solutions that go live with the plateau, the
// warnings that do not block it, the notice that the go-live cannot be undone, and the
// confirmation by the exact name (docs/screens/SCREENS.md). The first story is the screen image.

function screen(fetchFn: typeof fetch, typed?: string): Story {
  return {
    render: () => ({
      components: { AppHeader, PlateausPage },
      setup: () => {
        session.me = ME_ENTERPRISE_ARCHITECT as unknown as Me
        session.loaded = true
        return { fetchFn }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <PlateausPage :fetch-fn="fetchFn" />`,
    }),
    play: async ({ canvasElement }) => {
      const canvas = within(canvasElement)
      const buttons = await canvas.findAllByRole('button', { name: 'Go live' })
      await userEvent.click(buttons[1]!)
      const body = within(canvasElement.ownerDocument.body)
      await expect(await body.findByTestId('plateau-go-live-summary')).toHaveTextContent(/becomes the new baseline/)
      if (typed) {
        await userEvent.type(await body.findByTestId('plateau-go-live-name'), typed)
      }
    },
  }
}

const meta: Meta<typeof PlateausPage> = {
  title: 'Screens/SCR-066 Take plateau live',
  component: PlateausPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const TakePlateauLive: Story = screen(plateauApi().fetch)

export const Warnings: Story = {
  ...screen(plateauApi().fetch),
  play: async (context) => {
    await screen(plateauApi().fetch).play!(context)
    const body = within(context.canvasElement.ownerDocument.body)
    await expect(await body.findByText(/One solution is not yet approved: CRM Replacement/)).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Confirm go-live' })).toBeDisabled()
  },
}

export const NoWarnings: Story = {
  ...screen(plateauApi({ goLive: goLivePreview({ warnings: false }) }).fetch),
  play: async (context) => {
    await screen(plateauApi({ goLive: goLivePreview({ warnings: false }) }).fetch).play!(context)
    const body = within(context.canvasElement.ownerDocument.body)
    await expect(await body.findByText('This action cannot be undone.')).toBeInTheDocument()
    await expect(body.queryByText(/do not block the go-live/)).toBeNull()
  },
}

export const ExactName: Story = {
  ...screen(plateauApi().fetch),
  play: async (context) => {
    await screen(plateauApi().fetch, 'Target Cloud Migration 2027').play!(context)
    const body = within(context.canvasElement.ownerDocument.body)
    await expect(body.getByRole('button', { name: 'Confirm go-live' })).toBeEnabled()
  },
}

export const WrongName: Story = {
  ...screen(plateauApi().fetch),
  play: async (context) => {
    await screen(plateauApi().fetch, 'target cloud migration 2027').play!(context)
    const body = within(context.canvasElement.ownerDocument.body)
    await expect(body.getByRole('button', { name: 'Confirm go-live' })).toBeDisabled()
  },
}

export const Conflicts: Story = {
  ...screen(plateauApi({ goLive: goLivePreview({ conflicts: [goLiveConflict('ae000000-0000-4000-8000-0000000000c9')] }) }).fetch),
  play: async (context) => {
    const fetchFn = plateauApi({ goLive: goLivePreview({ conflicts: [goLiveConflict('ae000000-0000-4000-8000-0000000000c9')] }) }).fetch
    await screen(fetchFn).play!(context)
    const body = within(context.canvasElement.ownerDocument.body)
    await expect(await body.findByText('one conflict')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Resolve' })).toBeInTheDocument()
  },
}
