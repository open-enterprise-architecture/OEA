import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import SolutionPage from '../pages/SolutionPage.vue'
import { session } from '../session/store'
import { ME_SOLUTION_ARCHITECT, solutionApi } from '../test/solutions'

// SCR-061 Solution (UC-05, US-038; design D4 of us-038-create-solution): name, status, owner,
// creation time and the architecture vision of one solution (docs/screens/SCREENS.md). The
// first story is the screen image.

function screen(id: string): Story {
  const fetchFn = solutionApi().fetch
  return {
    render: () => ({
      components: { AppHeader, SolutionPage },
      setup: () => {
        session.me = ME_SOLUTION_ARCHITECT as Me
        session.loaded = true
        return { fetchFn, id }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <SolutionPage :id="id" :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof SolutionPage> = {
  title: 'Screens/SCR-061 Solution',
  component: SolutionPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const Solution: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('ERP Extension Logistics')).toBeInTheDocument()
    await expect(canvas.getByText(/Goal: warehouse logistics/)).toBeInTheDocument()
    await expect(canvas.getByText('Project mode (without plateaus)')).toBeInTheDocument()
  },
}

export const NotFound: Story = {
  ...screen('5d000000-0000-4000-8000-0000000000ff'),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('Solution not found')).toBeInTheDocument()
  },
}
