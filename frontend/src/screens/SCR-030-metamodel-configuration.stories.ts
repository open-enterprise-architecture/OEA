import type { Meta, StoryObj } from '@storybook/vue3-vite'
import UCard from '@nuxt/ui/components/Card.vue'
import { expect, userEvent, within } from 'storybook/test'
import { onBeforeMount, ref } from 'vue'
import MetatypeDialog from '../components/metamodel/MetatypeDialog.vue'
import { loadMetamodel, metatypeById } from '../metamodel/store'
import MetamodelPage from '../pages/MetamodelPage.vue'
import { refreshSession } from '../session/store'
import { IDS, ME_READER, metamodelApi } from '../test/metamodel'

// SCR-030 Metamodel configuration (UC-04, US-032, REQ-032; design D6 of
// scr-030-metamodel-configuration): the first story is the screen image, an administrator on the
// detail of an organization entity type.
function page(section: string, id?: string, me?: unknown, locked = false): Story {
  return {
    render: () => ({
      components: { MetamodelPage },
      setup: () => ({ fetchFn: metamodelApi({ me, locked }).fetch, section, id }),
      template: '<MetamodelPage :section="section" :id="id" :fetch-fn="fetchFn" />',
    }),
  }
}

const meta: Meta<typeof MetamodelPage> = {
  title: 'Screens/SCR-030 Metamodel Configuration',
  component: MetamodelPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const EntityTypeDetail: Story = page('metatypes', IDS.zone)

/** A reader with entity:read: everything visible, nothing changeable. */
export const Reader: Story = page('metatypes', IDS.component, ME_READER)

/** An imported entity type with a dated organization entry on a package mapping (ADR-049). */
export const ImportedEntityType: Story = page('metatypes', IDS.component)

/** A connection metatype with its allowed sources and targets (US-036). */
export const ConnectionType: Story = page('metatypes', IDS.dataFlow)

export const Properties: Story = page('properties')

export const Translations: Story = page('translations')

/** The metamodel is locked (import-only, US-035): a notice and no changing actions. */
export const Locked: Story = {
  ...page('metatypes', IDS.zone, undefined, true),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Metamodel locked')).toBeInTheDocument()
    await expect(canvas.queryByTestId('metatype-new')).toBeNull()
  },
}

/** Who changed the metamodel: two administrators, an import batch and the system (US-034). */
export const Changes: Story = {
  ...page('changes'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Legacy Zone')).toBeInTheDocument()
    await expect(canvas.getAllByText('Import batch').length).toBeGreaterThan(0)
  },
}

/** The settings with the lock switch and the global re-import setting. */
export const Settings: Story = page('settings')

/** Someone else changed the entity type meanwhile: warning with the current state (UC-04 A5). */
export const Collision: Story = {
  render: () => ({
    components: { MetatypeDialog, UCard },
    setup: () => {
      const api = metamodelApi()
      const ready = ref(false)
      onBeforeMount(async () => {
        await refreshSession(api.fetch)
        await loadMetamodel(api.fetch)
        api.changeElsewhere(IDS.zone, 'NET')
        ready.value = true
      })
      return { fetchFn: api.fetch, ready, metatype: () => metatypeById(IDS.zone) }
    },
    template: `
      <div class="flex min-h-screen items-start justify-center bg-elevated/50 p-8">
        <UCard class="w-full max-w-lg">
          <template #header><h2 class="text-lg font-semibold text-highlighted">Edit entity type</h2></template>
          <MetatypeDialog v-if="ready" :metatype="metatype()" :fetch-fn="fetchFn" />
        </UCard>
      </div>`,
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const abbreviation = await canvas.findByLabelText(/Abbreviation/)
    await userEvent.clear(abbreviation)
    await userEvent.type(abbreviation, 'ZN')
    await userEvent.click(canvas.getByRole('button', { name: 'Save' }))
    await expect(await canvas.findByText('Changed by someone else meanwhile')).toBeInTheDocument()
    await expect(canvas.getByTestId('collision-abbreviation')).toHaveTextContent('NET')
  },
}
