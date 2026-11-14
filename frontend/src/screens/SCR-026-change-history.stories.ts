import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, waitFor, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import EntityHistoryPage from '../pages/EntityHistoryPage.vue'
import { session } from '../session/store'
import { HISTORY_IDS, historyApi, ME_HISTORY_READER, MICHAEL } from '../test/history'

// SCR-026 Change history (UC-14, US-098; design D5 of us-098-entity-history): the versions of an
// entity newest first with number, local time, person and the changed fields without values; a
// field the reader may not read carries the badge "Restricted"; filters by person and period;
// "Load more" above 50 versions; the empty state of an entity without changes; a version
// expanded to its diff and its complete state, and the comparison of two versions (US-099,
// US-100; design D5 of us-099-version-diff) (docs/screens/SCREENS.md). The first story is the
// screen image.

function screen(id: number = HISTORY_IDS.crm): Story {
  const fetchFn = historyApi({ me: ME_HISTORY_READER }).fetch
  return {
    render: () => ({
      components: { AppHeader, EntityHistoryPage },
      setup: () => {
        session.me = ME_HISTORY_READER as unknown as Me
        session.loaded = true
        return { fetchFn, id: String(id) }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <EntityHistoryPage :id="id" :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof EntityHistoryPage> = {
  title: 'Screens/SCR-026 Change history',
  component: EntityHistoryPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const Timeline: Story = {
  ...screen(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Change history: CRM System')).toBeInTheDocument()
    await expect(canvas.getByText('Created')).toBeInTheDocument()
    await expect(canvas.getByText('Version 5')).toBeInTheDocument()
    await expect(canvas.getByText('5 of 5 versions')).toBeInTheDocument()
  },
}

export const FilteredByPerson: Story = {
  ...screen(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Change history: CRM System')).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('combobox'))
    await userEvent.click(await within(document.body).findByRole('option', { name: MICHAEL.name }))
    await expect(await canvas.findByText('3 of 3 versions')).toBeInTheDocument()
    // the list of persons is closed again before the accessibility check
    await waitFor(() => expect(document.body.querySelector('[role="listbox"]')).toBeNull())
  },
}

export const RestrictedField: Story = {
  ...screen(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Investment Cost · Restricted')).toBeInTheDocument()
  },
}

export const Unchanged: Story = {
  ...screen(HISTORY_IDS.billing),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText(/No changes since creation\. Created on .* by Kurt\./)).toBeInTheDocument()
  },
}

export const LoadMore: Story = {
  ...screen(HISTORY_IDS.large),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('50 of 51 versions')).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Load more' }))
    await expect(await canvas.findByText('51 of 51 versions')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  },
}

export const Diff: Story = {
  ...screen(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Change history: CRM System')).toBeInTheDocument()
    await userEvent.click(canvas.getAllByRole('button', { name: 'Show changes' })[1]!)
    await expect(await canvas.findByText('CRM System', { selector: 'ins' })).toBeInTheDocument()
    await expect(canvas.getByText('CRM', { selector: 'del' })).toBeInTheDocument()
  },
}

export const CompleteState: Story = {
  ...screen(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Change history: CRM System')).toBeInTheDocument()
    await userEvent.click(canvas.getAllByRole('button', { name: 'Show changes' })[1]!)
    await userEvent.click(await canvas.findByRole('button', { name: 'Show complete state v4' }))
    await expect(await canvas.findByText('Complete state of version 4')).toBeInTheDocument()
  },
}

export const Compare: Story = {
  ...screen(),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Change history: CRM System')).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Compare' }))
    await userEvent.click(canvas.getByRole('checkbox', { name: 'Select version 5 for the comparison' }))
    await userEvent.click(canvas.getByRole('checkbox', { name: 'Select version 2 for the comparison' }))
    await expect(await canvas.findByText('Version 2 → Version 5')).toBeInTheDocument()
  },
}
