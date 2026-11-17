import UModal from '@nuxt/ui/components/Modal.vue'
import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import OpenIssuesDialog from '../components/solutions/OpenIssuesDialog.vue'
import SolutionPage from '../pages/SolutionPage.vue'
import { session } from '../session/store'
import { ME_SOLUTION_ARCHITECT, OPEN_ISSUES, openIssue, SOLUTIONS, solutionApi } from '../test/solutions'

// SCR-064 Resolve open issues (US-153 AC2, AC3, US-154 AC4; design D7 of us-153-open-issues): the
// dialog "Resolve" opens on SCR-061 for the open issues a go-live left. Per issue, grouped by
// entity: the kind, the current or kept value, an input for the new value and, where removing
// resolves the issue, "Remove the value" (docs/screens/SCREENS.md). The first story is the screen image.

const IMPLEMENTED = { ...SOLUTIONS[0]!, status: 'implemented' as const, openIssueCount: 3 }
const ISSUES = [
  ...OPEN_ISSUES,
  openIssue({
    id: '0e000000-0000-4000-8000-000000000003',
    entityId: 1,
    entityName: 'ERP-Core',
    field: 'fd000000-0000-4000-8000-000000000007',
    label: 'Cost Center',
    kind: 'not-in-metamodel',
    keptValue: 'CC-100',
    removable: true,
  }),
]

const meta: Meta<typeof OpenIssuesDialog> = {
  title: 'Screens/SCR-064 Resolve open issues',
  component: OpenIssuesDialog,
}

export default meta
type Story = StoryObj<typeof meta>

export const OpenIssues: Story = {
  render: () => ({
    components: { AppHeader, OpenIssuesDialog, UModal },
    setup: () => {
      session.me = ME_SOLUTION_ARCHITECT as unknown as Me
      session.loaded = true
      const api = solutionApi({ me: ME_SOLUTION_ARCHITECT, solutions: [IMPLEMENTED], openIssues: ISSUES })
      return { fetchFn: api.fetch, id: IMPLEMENTED.id, issues: ISSUES }
    },
    template: `
      <AppHeader :fetch-fn="fetchFn" />
      <UModal :open="true" title="Resolve open issues">
        <template #body>
          <OpenIssuesDialog :solution-id="id" :issues="issues" :fetch-fn="fetchFn" />
        </template>
      </UModal>`,
  }),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText(/Current value: 250/)).toBeInTheDocument()
    await expect(body.getByText(/Kept value: CC-100/)).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Apply' })).toBeDisabled()
  },
}

export const FromTheSolution: Story = {
  render: () => ({
    components: { AppHeader, SolutionPage },
    setup: () => {
      session.me = ME_SOLUTION_ARCHITECT as unknown as Me
      session.loaded = true
      const api = solutionApi({ me: ME_SOLUTION_ARCHITECT, solutions: [IMPLEMENTED], openIssues: ISSUES })
      return { fetchFn: api.fetch, id: IMPLEMENTED.id }
    },
    template: `
      <AppHeader :fetch-fn="fetchFn" />
      <SolutionPage :id="id" :fetch-fn="fetchFn" />`,
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await canvas.findByRole('button', { name: 'Resolve' }))
    await expect(await body.findByText('Resolve open issues')).toBeInTheDocument()
  },
}
