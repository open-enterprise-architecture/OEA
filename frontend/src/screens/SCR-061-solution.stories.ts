import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { SolutionDelta, SolutionDeltaConflict, SolutionLandscapeEntry } from '../api/solutions'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import SolutionPage from '../pages/SolutionPage.vue'
import { session } from '../session/store'
import {
  APPLICATION,
  CONFLICT_KURT,
  CONFLICT_MARIA,
  DELTAS,
  DELTAS_WITH_MODIFIED,
  ME_SOLUTION_ARCHITECT,
  ME_SOLUTION_MANAGER,
  ME_SOLUTION_VIEWER,
  manyDeltas,
  MODIFIED_ERP,
  OPEN_ISSUES,
  SOLUTIONS,
  solutionApi,
} from '../test/solutions'
import { clearTree } from '../navigation/store'
import { navigationApi } from '../test/navigation'
import { plateauApi, TARGET_2027_ID, TARGET_2028_ID, withPlateaus } from '../test/plateaus'

// SCR-061 Solution (UC-05, US-038, US-039): name, status, owner, creation time and the
// architecture vision of one solution (design D4 of us-038-create-solution), and its baseline
// with search, metatype filter and paging (design D5 of us-039-solution-baseline), and its
// changes: new and retiring entities, the dialog "New entity" and "Retire" in the baseline (design
// D9 of us-040-entity-deltas), and modifications with "Modify" and "Edit" (design D6 of
// us-041-modified-delta); the changes load page by page with "Load more", and "Compare" opens
// SCR-062 (design D0, D3 of us-043-diff-view); the warnings of conflicts with other active
// solutions on the entries, in the header and in the baseline (design D4 of
// us-044-conflict-warning), and "Go live" with its confirmation and the open issues after it
// (US-152; design D6 of us-152-solution-go-live) (docs/screens/SCREENS.md). The first story is the
// screen image.

// Warehouse Management (103) and ERP-Core (102) are also changed elsewhere; CRM-Legacy (101) is
// changed by another solution, and applications show parallel changes in the baseline.
const CONFLICTS: Record<number, SolutionDeltaConflict[]> = { 103: [CONFLICT_MARIA], 102: [CONFLICT_KURT], 101: [CONFLICT_MARIA, CONFLICT_KURT] }

function screen(
  id: string,
  landscape?: SolutionLandscapeEntry[],
  deltas: SolutionDelta[] = DELTAS,
  me: unknown = ME_SOLUTION_ARCHITECT,
  conflicts: Record<number, SolutionDeltaConflict[]> = {},
): Story {
  const fetchFn = navigationApi({ fallback: solutionApi({ landscape, deltas, me, conflicts, parallelChangeMetatypes: [APPLICATION] }).fetch })
  return {
    render: () => ({
      components: { AppHeader, SolutionPage },
      setup: () => {
        session.me = me as Me
        session.loaded = true
        clearTree()
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
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, DELTAS_WITH_MODIFIED, ME_SOLUTION_MANAGER, CONFLICTS),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('ERP Extension Logistics')).toBeInTheDocument()
    await expect(canvas.getByText(/Goal: warehouse logistics/)).toBeInTheDocument()
    await expect(canvas.getByText('Project mode (without plateaus)')).toBeInTheDocument()
    await expect(await canvas.findByText('CRM-Legacy')).toBeInTheDocument()
    await expect(canvas.getByText('Page 1 of 1 · 7 entities')).toBeInTheDocument()
    await expect(await canvas.findByText('Warehouse Cloud')).toBeInTheDocument()
    await expect(canvas.getAllByText('Retiring')).toHaveLength(2)
    await expect(canvas.getAllByText('Modified')).toHaveLength(2)
    await expect(canvas.getByText('2 changes')).toBeInTheDocument()
    await expect(canvas.getByRole('link', { name: 'Compare' })).toHaveAttribute('href', '/solutions/5d000000-0000-4000-8000-000000000002/diff')
    await expect(canvas.getByRole('button', { name: 'Place in navigation' })).toBeInTheDocument()
  },
}

/** "Place in navigation" (US-097): the folder as a path and the display label, as for catalogs. */
export const PlaceInNavigation: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, DELTAS_WITH_MODIFIED, ME_SOLUTION_MANAGER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'Place in navigation' }))
    const dialog = within(canvasElement.ownerDocument.body)
    await expect(await dialog.findByText('Place ERP Extension Logistics in the navigation')).toBeInTheDocument()
    await userEvent.click(dialog.getByTestId('place-submit'))
    await expect(await canvas.findByText('Placed in Repository.')).toBeVisible()
  },
}

/** A reader without `viewpoint:manage` is not offered "Place in navigation" (design D7 of us-095). */
export const NoPlacementForReader: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, DELTAS, ME_SOLUTION_VIEWER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('ERP Extension Logistics')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Place in navigation' })).toBeNull()
  },
}

export const Conflicts: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, DELTAS_WITH_MODIFIED, ME_SOLUTION_ARCHITECT, CONFLICTS),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(
      await canvas.findByText('2 changes also concern other active solutions. Please coordinate with their owners.'),
    ).toBeInTheDocument()
    await expect(canvas.getAllByTestId('delta-conflict').map((line) => line.textContent?.trim())).toEqual([
      "Also changed in 'Integration Platform' (owner: Kurt)",
      "Also changed in 'Solution A' (owner: Maria)",
    ])
    await expect(await canvas.findByText('Changed in 2 other solutions')).toBeInTheDocument()
    await expect(canvas.getAllByText('Changed in 1 other solution')).toHaveLength(2)
    // retiring CRM-Legacy saves the delta and names the other solutions
    const crm = (await canvas.findAllByRole('row')).find((row) => row.textContent?.includes('CRM-Legacy') && row.textContent.includes('Retire'))!
    await userEvent.click(within(crm).getByRole('button', { name: 'Retire' }))
    await expect(
      await canvas.findByText('Saved. CRM-Legacy is also changed in: Solution A, Integration Platform. Please coordinate with their owners.'),
    ).toBeInTheDocument()
  },
}

export const ManyChanges: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, manyDeltas(60)),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('50 of 60 changes')).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Load more' }))
    await expect(await canvas.findByText('60 of 60 changes')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  },
}

export const ModifyEntityDialog: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, DELTAS),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const row = (await canvas.findByText('ERP-Core')).closest('tr')!
    await userEvent.click(within(row as HTMLElement).getByRole('button', { name: 'Modify' }))
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Current: 250')).toBeInTheDocument()
    await expect(body.getByText('Current: hidden')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Save modification' })).toBeEnabled()
  },
}

export const Changes: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, []),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('No changes yet.')).toBeInTheDocument()
    const row = (await canvas.findByText('CRM-Legacy')).closest('tr')!
    await userEvent.click(within(row as HTMLElement).getByRole('button', { name: 'Retire' }))
    await expect(await within(row as HTMLElement).findByText('Retiring')).toBeInTheDocument()
    await expect(canvas.queryByText('No changes yet.')).not.toBeInTheDocument()
  },
}

export const NewEntityDialog: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New entity' }))
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('An entity this initiative introduces. It enters the catalog with the go-live.')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Add to the solution' })).toBeDisabled()
  },
}

export const ReadOnly: Story = {
  ...screen('5d000000-0000-4000-8000-000000000002', undefined, DELTAS, ME_SOLUTION_VIEWER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Warehouse Cloud')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'New entity' })).not.toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Retire' })).not.toBeInTheDocument()
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

function goLiveScreen(api: ReturnType<typeof solutionApi>): Story {
  return {
    render: () => ({
      components: { AppHeader, SolutionPage },
      setup: () => {
        session.me = ME_SOLUTION_ARCHITECT as unknown as Me
        session.loaded = true
        return { fetchFn: api.fetch, id: SOLUTIONS[0]!.id }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <SolutionPage :id="id" :fetch-fn="fetchFn" />`,
    }),
  }
}

export const GoLive: Story = {
  ...goLiveScreen(solutionApi({ me: ME_SOLUTION_ARCHITECT, deltas: [...DELTAS, MODIFIED_ERP], openIssues: OPEN_ISSUES })),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await canvas.findByRole('button', { name: 'Go live' }))
    await expect(await body.findByText('The go-live cannot be undone.')).toBeInTheDocument()
    await expect(body.getByText(/1 entity is changed/)).toBeInTheDocument()
  },
}

export const Implemented: Story = {
  ...goLiveScreen(
    solutionApi({
      me: ME_SOLUTION_ARCHITECT,
      deltas: [...DELTAS, MODIFIED_ERP],
      openIssues: OPEN_ISSUES,
      solutions: [{ ...SOLUTIONS[0]!, status: 'implemented', openIssueCount: 2 }, ...SOLUTIONS.slice(1)],
    }),
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Open issues (2)')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Go live' })).not.toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'Resolve' })).toBeInTheDocument()
  },
}

// a solution of the plateau Target 2028 with the basis "predecessor": the header names the
// plateaus, the baseline its basis, and the entries a solution of Target 2027 retires or proposes
// (US-155 AC2; design D8 of us-084-plateaus)
const CRM_PLATEAU = { solutionId: '5d000000-0000-4000-8000-0000000000aa', name: 'CRM Replacement' }
export const PlateauBasis: Story = {
  render: () => ({
    components: { AppHeader, SolutionPage },
    setup: () => {
      session.me = ME_SOLUTION_ARCHITECT as Me
      session.loaded = true
      const solution = { ...SOLUTIONS[0]!, fromPlateauId: TARGET_2027_ID, toPlateauId: TARGET_2028_ID }
      const landscape: SolutionLandscapeEntry[] = [
        { id: 101, name: 'CRM-Legacy', metatypeId: APPLICATION, metatypeName: 'Application', version: 3, parallelChanges: [], lifecycle: 'retiring', changedBy: CRM_PLATEAU, deltaId: null },
        { id: 102, name: 'ERP-Core', metatypeId: APPLICATION, metatypeName: 'Application', version: 4, parallelChanges: [], lifecycle: 'active', changedBy: null, deltaId: null },
        { id: null, name: 'Salesforce', metatypeId: APPLICATION, metatypeName: 'Application', version: 0, parallelChanges: [], lifecycle: 'proposed', changedBy: CRM_PLATEAU, deltaId: 'ae000000-0000-4000-8000-0000000000aa' },
      ]
      const fetchFn = withPlateaus(solutionApi({ me: ME_SOLUTION_ARCHITECT, solutions: [solution], landscape, deltas: [] }).fetch, plateauApi().fetch)
      return { fetchFn, id: solution.id }
    },
    template: `
      <AppHeader :fetch-fn="fetchFn" />
      <SolutionPage :id="id" :fetch-fn="fetchFn" />`,
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Plateau mode: from Target Cloud Migration 2027 to Target 2028')).toBeInTheDocument()
    await expect(await canvas.findByText('Basis: content of Target Cloud Migration 2027')).toBeInTheDocument()
    await expect(await canvas.findByText('Proposed by CRM Replacement')).toBeInTheDocument()
    await expect(canvas.getByText('Retiring by CRM Replacement')).toBeInTheDocument()
    // a solution of a plateau goes live with its plateau (us-086-plateau-go-live)
    await expect(canvas.getByRole('link', { name: 'Goes live with plateau Target 2028' })).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Go live' })).toBeNull()
  },
}
