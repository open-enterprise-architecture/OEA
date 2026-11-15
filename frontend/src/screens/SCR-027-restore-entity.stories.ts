import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import RestoreEntityModal from '../components/history/RestoreEntityModal.vue'
import EntityHistoryPage from '../pages/EntityHistoryPage.vue'
import { session } from '../session/store'
import { HISTORY_IDS, historyApi, ME_RESTORER, OWNER_MISSING, PREVIEW_NOTHING, PREVIEW_WITH_KEPT } from '../test/history'

// SCR-027 Restore entity (UC-15, US-101, US-102, REQ-101; design D5 of us-101-entity-restore):
// the modal opened by "Restore this version" on SCR-026, with the changes from the current value
// to the value of the target version, the fields that are not restored with their reason, the
// warnings of empty mandatory properties, the notices of UC-15, the optional reason, and the
// message when the entity was changed meanwhile (docs/screens/SCREENS.md). The first story is the
// screen image.

function screen(api: ReturnType<typeof historyApi>): Story {
  return {
    render: () => ({
      components: { AppHeader, EntityHistoryPage, RestoreEntityModal },
      setup: () => {
        session.me = ME_RESTORER as unknown as Me
        session.loaded = true
        return { fetchFn: api.fetch, id: String(HISTORY_IDS.crm), entityId: HISTORY_IDS.crm }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <EntityHistoryPage :id="id" :fetch-fn="fetchFn" />
        <RestoreEntityModal :open="true" :entity-id="entityId" :target-version="3" :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof RestoreEntityModal> = {
  title: 'Screens/SCR-027 Restore entity',
  component: RestoreEntityModal,
}

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {
  ...screen(historyApi({ me: ME_RESTORER })),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Restore version 3')).toBeInTheDocument()
    await expect(await body.findByText('CRM', { selector: 'ins' })).toBeInTheDocument()
    await expect(body.getByText('The current state stays in the history and can be retrieved at any time.')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Restore' })).toBeEnabled()
  },
}

export const NotRestored: Story = {
  ...screen(historyApi({ me: ME_RESTORER, preview: { fields: PREVIEW_WITH_KEPT } })),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Not restored: insufficient permission')).toBeInTheDocument()
    await expect(body.getByText('Not restored: the value is no longer valid')).toBeInTheDocument()
  },
}

export const Warnings: Story = {
  ...screen(historyApi({ me: ME_RESTORER, preview: { fields: PREVIEW_WITH_KEPT, warnings: OWNER_MISSING } })),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('The mandatory property Owner will have no value.')).toBeInTheDocument()
  },
}

export const NothingToRestore: Story = {
  ...screen(historyApi({ me: ME_RESTORER, preview: { fields: PREVIEW_NOTHING } })),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Nothing can be restored: every differing field is kept.')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Restore' })).toBeDisabled()
  },
}

export const ChangedMeanwhile: Story = {
  ...screen(historyApi({ me: ME_RESTORER, changedMeanwhileTo: 6 })),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('CRM', { selector: 'ins' })).toBeInTheDocument()
    await userEvent.click(body.getByRole('button', { name: 'Restore' }))
    await expect(await body.findByText('This entity was changed meanwhile. The preview shows its current state.')).toBeInTheDocument()
  },
}
