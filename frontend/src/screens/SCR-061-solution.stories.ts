import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { SolutionLandscapeEntry } from '../api/solutions'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import SolutionPage from '../pages/SolutionPage.vue'
import { session } from '../session/store'
import { ME_SOLUTION_ARCHITECT, solutionApi } from '../test/solutions'

// SCR-061 Solution (UC-05, US-038, US-039): name, status, owner, creation time and the
// architecture vision of one solution (design D4 of us-038-create-solution), and its baseline
// with search, metatype filter and paging (design D5 of us-039-solution-baseline)
// (docs/screens/SCREENS.md). The first story is the screen image.

function screen(id: string, landscape?: SolutionLandscapeEntry[]): Story {
  const fetchFn = solutionApi({ landscape }).fetch
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
    await expect(await canvas.findByText('CRM-Legacy')).toBeInTheDocument()
    await expect(canvas.getByText('Page 1 of 1 · 7 entities')).toBeInTheDocument()
  },
}

export const Baseline: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Message Broker')).toBeInTheDocument()
    await userEvent.type(canvas.getByPlaceholderText('Search by name'), 'erp')
    await expect(await canvas.findByText('Page 1 of 1 · 1 entities')).toBeInTheDocument()
    await expect(canvas.getByText('ERP-Core')).toBeInTheDocument()
    await expect(canvas.queryByText('Message Broker')).not.toBeInTheDocument()
  },
}

export const EmptyLandscape: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', []),
  play: async ({ canvasElement }) => {
    await expect(
      await within(canvasElement).findByText('No entities in the current landscape – you can only create new entities'),
    ).toBeInTheDocument()
  },
}

export const NoMatch: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('CRM-Legacy')).toBeInTheDocument()
    await userEvent.type(canvas.getByPlaceholderText('Search by name'), 'no such system')
    await expect(await canvas.findByText('No entity matches the search and the filter.')).toBeInTheDocument()
    await expect(canvas.queryByText(/No entities in the current landscape/)).not.toBeInTheDocument()
  },
}

export const NotFound: Story = {
  ...screen('5d000000-0000-4000-8000-0000000000ff'),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('Solution not found')).toBeInTheDocument()
  },
}
