import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import PartialRestoreWizard from '../components/history/PartialRestoreWizard.vue'
import EntityHistoryPage from '../pages/EntityHistoryPage.vue'
import { session } from '../session/store'
import { CRM_CONNECTIONS, CRM_VERSIONS, HISTORY_IDS, historyApi, ME_RESTORER, OWNER_MISSING, PARTIAL_PREVIEW } from '../test/history'

// SCR-029 Partially restore (UC-16, US-104 to US-106, REQ-104 to REQ-106; design D6 of
// us-104-partial-restore): the wizard opened by "Partially restore" on SCR-026, with the source
// version, the steps General, Properties, Connections and Summary, identical fields grayed out,
// fields that cannot be restored with their reason, the connections linked to their history, and
// the summary with the reason (docs/screens/SCREENS.md). The first story is the screen image.

function screen(api: ReturnType<typeof historyApi>): Story {
  return {
    render: () => ({
      components: { AppHeader, EntityHistoryPage, PartialRestoreWizard },
      setup: () => {
        session.me = ME_RESTORER as unknown as Me
        session.loaded = true
        return { fetchFn: api.fetch, id: String(HISTORY_IDS.crm), entityId: HISTORY_IDS.crm, versions: CRM_VERSIONS }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <EntityHistoryPage :id="id" :fetch-fn="fetchFn" />
        <PartialRestoreWizard :open="true" :entity-id="entityId" :source-version="4" :versions="versions" :current-version="5" :fetch-fn="fetchFn" />`,
    }),
  }
}

const api = () => historyApi({ me: ME_RESTORER, preview: { fields: PARTIAL_PREVIEW, warnings: OWNER_MISSING, connections: CRM_CONNECTIONS } })

const meta: Meta<typeof PartialRestoreWizard> = {
  title: 'Screens/SCR-029 Partially restore',
  component: PartialRestoreWizard,
}

export default meta
type Story = StoryObj<typeof meta>

export const General: Story = {
  ...screen(api()),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Value in v4')).toBeInTheDocument()
    await expect(body.getByRole('checkbox', { name: 'Restore Name' })).toBeDisabled()
    await expect(body.getByRole('checkbox', { name: 'Restore Description' })).toBeEnabled()
  },
}

export const Properties: Story = {
  ...screen(api()),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('button', { name: 'Next' }))
    await expect(await body.findByText('(not present in source version)')).toBeInTheDocument()
    await expect(body.getByText('Not restored: insufficient permission')).toBeInTheDocument()
    await userEvent.click(body.getByRole('checkbox', { name: 'Restore Owner' }))
    await expect(await body.findByText('After the restore: Kurt')).toBeInTheDocument()
  },
}

export const Connections: Story = {
  ...screen(api()),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('button', { name: 'Next' }))
    await userEvent.click(body.getByRole('button', { name: 'Next' }))
    await expect(await body.findByText('ERP → CRM')).toBeInTheDocument()
    await expect(body.getByRole('link', { name: 'Open history' })).toBeInTheDocument()
  },
}

export const Summary: Story = {
  ...screen(api()),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('checkbox', { name: 'Restore Description' }))
    await userEvent.click(body.getByRole('button', { name: 'Next' }))
    await userEvent.click(body.getByRole('checkbox', { name: 'Restore Owner' }))
    await userEvent.click(body.getByRole('button', { name: 'Next' }))
    await userEvent.click(body.getByRole('button', { name: 'Next' }))
    await expect(await body.findByText('Every field that is not selected stays unchanged.')).toBeInTheDocument()
    await expect(body.getByText('The mandatory property Owner will have no value.')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Partially restore' })).toBeEnabled()
  },
}

export const NothingSelected: Story = {
  ...screen(api()),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    for (let index = 0; index < 3; index++) await userEvent.click(await body.findByRole('button', { name: 'Skip' }))
    await expect(await body.findByText('No field is selected. Go back and select the fields to restore.')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Partially restore' })).toBeDisabled()
  },
}
