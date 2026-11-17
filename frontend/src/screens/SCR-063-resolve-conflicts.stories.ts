import UModal from '@nuxt/ui/components/Modal.vue'
import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import DeltaMergeDialog from '../components/solutions/DeltaMergeDialog.vue'
import SolutionPage from '../pages/SolutionPage.vue'
import { session } from '../session/store'
import { DELTAS, goLiveConflict, ME_SOLUTION_ARCHITECT, MODIFIED_ERP, SOLUTIONS, solutionApi } from '../test/solutions'

// SCR-063 Resolve conflicts (US-152 AC2; design D6 of us-152-solution-go-live): the merge dialog a
// go-live opens when fields a solution changes were also changed in the catalog after the
// capture. Per field: the value when captured, and the choice between the current value, the value
// of the solution and an own value (docs/screens/SCREENS.md). The first story is the screen image.

const CONFLICTS = [
  goLiveConflict(MODIFIED_ERP.id),
  goLiveConflict(MODIFIED_ERP.id, {
    field: 'fd000000-0000-4000-8000-000000000001',
    label: 'Owner',
    dataType: 'string',
    before: 'IT',
    current: 'Finance',
    after: 'Sales',
  }),
]

const meta: Meta<typeof DeltaMergeDialog> = {
  title: 'Screens/SCR-063 Resolve conflicts',
  component: DeltaMergeDialog,
}

export default meta
type Story = StoryObj<typeof meta>

export const Conflicts: Story = {
  render: () => ({
    components: { AppHeader, DeltaMergeDialog, UModal },
    setup: () => {
      session.me = ME_SOLUTION_ARCHITECT as unknown as Me
      session.loaded = true
      const api = solutionApi({ me: ME_SOLUTION_ARCHITECT, deltas: [...DELTAS, MODIFIED_ERP], goLiveConflicts: CONFLICTS })
      return { fetchFn: api.fetch, id: SOLUTIONS[0]!.id, conflicts: CONFLICTS }
    },
    template: `
      <AppHeader :fetch-fn="fetchFn" />
      <UModal :open="true" title="Resolve conflicts">
        <template #body>
          <DeltaMergeDialog :solution-id="id" :conflicts="conflicts" :fetch-fn="fetchFn" />
        </template>
      </UModal>`,
  }),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Keep the current value: 15')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Apply' })).toBeDisabled()
  },
}

export const FromTheGoLive: Story = {
  render: () => ({
    components: { AppHeader, SolutionPage },
    setup: () => {
      session.me = ME_SOLUTION_ARCHITECT as unknown as Me
      session.loaded = true
      const api = solutionApi({ me: ME_SOLUTION_ARCHITECT, deltas: [...DELTAS, MODIFIED_ERP], goLiveConflicts: CONFLICTS })
      return { fetchFn: api.fetch, id: SOLUTIONS[0]!.id }
    },
    template: `
      <AppHeader :fetch-fn="fetchFn" />
      <SolutionPage :id="id" :fetch-fn="fetchFn" />`,
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await canvas.findByRole('button', { name: 'Go live' }))
    await expect(await body.findByText('Resolve conflicts')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Apply' })).toBeDisabled()
  },
}

// a value only a snapshot of the solution keeps: "Discard the value" or "Resolve later" (US-154 AC4)
const KEPT = goLiveConflict(MODIFIED_ERP.id, {
  kind: 'not-in-metamodel',
  field: 'fd000000-0000-4000-8000-000000000007',
  label: 'Cost Center',
  dataType: 'string',
  before: null,
  current: null,
  after: 'CC-100',
})

export const SnapshotValue: Story = {
  render: () => ({
    components: { AppHeader, DeltaMergeDialog, UModal },
    setup: () => {
      session.me = ME_SOLUTION_ARCHITECT as unknown as Me
      session.loaded = true
      const api = solutionApi({ me: ME_SOLUTION_ARCHITECT, deltas: [...DELTAS, MODIFIED_ERP], goLiveConflicts: [KEPT] })
      return { fetchFn: api.fetch, id: SOLUTIONS[0]!.id, conflicts: [KEPT] }
    },
    template: `
      <AppHeader :fetch-fn="fetchFn" />
      <UModal :open="true" title="Resolve conflicts">
        <template #body>
          <DeltaMergeDialog :solution-id="id" :conflicts="conflicts" :fetch-fn="fetchFn" />
        </template>
      </UModal>`,
  }),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Resolve later as an open issue')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Apply' })).toBeEnabled()
  },
}
