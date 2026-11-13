import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import type { SolutionDelta, SolutionDeltaConflict } from '../api/solutions'
import AppHeader from '../components/AppHeader.vue'
import SolutionDiffPage from '../pages/SolutionDiffPage.vue'
import { session } from '../session/store'
import { CONFLICT_KURT, CONFLICT_MARIA, DIFF_DELTAS, ME_SOLUTION_VIEWER, manyDeltas, solutionApi } from '../test/solutions'

// SCR-062 Solution comparison (UC-05, US-043; design D3 of us-043-diff-view): per delta, the
// current state of the entity in the catalog next to its target state after the solution, with
// the markings of new, modified and retiring entries, changed fields and drift since capture;
// "Refresh" and "Load more"; the conflicts with other active solutions on a card (design D4 of
// us-044-conflict-warning) (docs/screens/SCREENS.md). The columns stack below the `md`
// breakpoint. The first story is the screen image.

const ERP_SOLUTION = '5d000000-0000-4000-8000-000000000002'

function screen(deltas: SolutionDelta[] = DIFF_DELTAS, conflicts: Record<number, SolutionDeltaConflict[]> = {}): Story {
  const fetchFn = solutionApi({ deltas, me: ME_SOLUTION_VIEWER, conflicts }).fetch
  return {
    render: () => ({
      components: { AppHeader, SolutionDiffPage },
      setup: () => {
        session.me = ME_SOLUTION_VIEWER as unknown as Me
        session.loaded = true
        return { fetchFn, id: ERP_SOLUTION }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <SolutionDiffPage :id="id" :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof SolutionDiffPage> = {
  title: 'Screens/SCR-062 Solution comparison',
  component: SolutionDiffPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const Comparison: Story = {
  ...screen(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Comparison: ERP Extension Logistics')).toBeInTheDocument()
    await expect(await canvas.findByText('2 new · 2 modified · 1 retiring')).toBeInTheDocument()
    await expect(canvas.getAllByText('Does not exist yet')).toHaveLength(2)
    await expect(canvas.getByText('Out of service')).toBeInTheDocument()
    await expect(canvas.getByText('Changed in the catalog since capture')).toBeInTheDocument()
    await expect(canvas.getAllByRole('heading', { name: 'Current state' })).toHaveLength(5)
  },
}

// ERP-Core (102) is also modified by another solution, CRM-Legacy (101) retired by two
export const Conflicts: Story = {
  ...screen(DIFF_DELTAS, { 102: [CONFLICT_KURT], 101: [CONFLICT_MARIA, CONFLICT_KURT] }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Comparison: ERP Extension Logistics')).toBeInTheDocument()
    await expect((await canvas.findAllByTestId('delta-conflict')).map((line) => line.textContent?.trim())).toEqual([
      "Also changed in 'Integration Platform' (owner: Kurt)",
      "Also changed in 'Solution A' (owner: Maria)",
      "Also changed in 'Integration Platform' (owner: Kurt)",
    ])
  },
}

export const Empty: Story = {
  ...screen([]),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('No changes captured yet')).toBeInTheDocument()
    await expect(await canvas.findByText('0 new · 0 modified · 0 retiring')).toBeInTheDocument()
  },
}

export const LoadMore: Story = {
  ...screen(manyDeltas(60)),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('50 of 60 entries')).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Load more' }))
    await expect(await canvas.findByText('60 of 60 entries')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  },
}
