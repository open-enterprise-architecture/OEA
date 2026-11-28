import type { Meta, StoryObj } from '@storybook/vue3-vite'
import UCard from '@nuxt/ui/components/Card.vue'
import { expect, userEvent, waitFor, within } from 'storybook/test'
import { onBeforeMount, ref } from 'vue'
import DeleteDialog from '../components/metamodel/DeleteDialog.vue'
import MetatypeDialog from '../components/metamodel/MetatypeDialog.vue'
import { loadMetamodel, metatypeById } from '../metamodel/store'
import MetamodelPage from '../pages/MetamodelPage.vue'
import { refreshSession } from '../session/store'
import { IDS, ME_READER, metamodelApi } from '../test/metamodel'

// SCR-030 Metamodel configuration (UC-04, US-032, REQ-032; design D6 of
// scr-030-metamodel-configuration): the first story is the screen image, an administrator on the
// detail of an organization entity type, with its re-import setting and the setting of parallel
// changes in the baseline (US-044).
function page(section: string, id?: string, me?: unknown, locked = false, restrictedOwner = false): Story {
  return {
    render: () => ({
      components: { MetamodelPage },
      setup: () => ({ fetchFn: metamodelApi({ me, locked, restrictedOwner }).fetch, section, id }),
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

/** The organization model: which entity types play organizational unit, role and person, and which connections assign them (us-075-lane-assignments). */
export const OrganizationModel: Story = {
  ...page('organization'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByRole('heading', { name: 'Organization model' })).toBeInTheDocument()
    await expect(await canvas.findByTestId('organization-part-orgUnit')).toBeInTheDocument()
    // the save is offered once a part changes
    await expect(await canvas.findByRole('button', { name: 'Save' })).toBeDisabled()
    await userEvent.click(canvas.getByTestId('organization-select-orgUnit'))
    await userEvent.click(await within(canvasElement.ownerDocument.body).findByRole('option', { name: 'Network Zone' }))
    await waitFor(() => expect(canvas.getByRole('button', { name: 'Save' })).toBeEnabled())
    await userEvent.click(canvas.getByRole('button', { name: 'Save' }))
    await expect(await canvas.findByText('The organization model is saved.')).toBeInTheDocument()
  },
}

/** A reader sees the parts and cannot change them. */
export const OrganizationModelReader: Story = {
  ...page('organization', undefined, ME_READER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByTestId('organization-value-orgUnit')).toHaveTextContent('Not set')
    await expect(canvas.queryByRole('button', { name: 'Save' })).toBeNull()
  },
}

export const Properties: Story = page('properties')

/** A role-restricted property: the badge "Restricted", changed only on SCR-032 (us-124-property-visibility). */
export const RestrictedProperty: Story = {
  ...page('properties', undefined, undefined, false, true),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('Restricted')).toBeInTheDocument()
  },
}

export const Translations: Story = page('translations')

/** The starter packages shipped with OEA: TOGAF is imported, BPMN can be imported next to it (us-074-bpmn-process-diagrams). */
export const StarterPackages: Story = {
  ...page('starter'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('BPMN 2.0 process modeling')).toBeInTheDocument()
    await expect(canvas.getByText('Imported')).toBeInTheDocument()
    await userEvent.click(canvas.getByRole('button', { name: 'Import: BPMN 2.0 process modeling' }))
    const body = within(canvasElement.ownerDocument.body)
    await expect(await body.findByText('Import BPMN 2.0 process modeling?')).toBeInTheDocument()
    await userEvent.click(body.getByRole('button', { name: 'Import' }))
    await waitFor(() => expect(canvas.getByText(/imported: 33 elements created/)).toBeInTheDocument())
  },
}

/** The architecture layers: the layers of the starter package and one of the organization (change s17-architecture-layers). */
export const Layers: Story = {
  ...page('layers'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Strategy')).toBeInTheDocument()
    await expect(canvas.getAllByTestId('layer-row')).toHaveLength(4)
    // only the layer of the organization has actions
    await expect(canvas.getAllByTestId('layer-edit')).toHaveLength(1)
  },
}

/** A reader sees the layers and their entity types, nothing changeable. */
export const LayersReader: Story = {
  ...page('layers', undefined, ME_READER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Strategy')).toBeInTheDocument()
    // the notice of a reader appears once the session of the reader is loaded
    await expect(await canvas.findByText(/Changes need the metamodel administrator role/)).toBeInTheDocument()
    await expect(canvas.queryByTestId('layer-new')).toBeNull()
  },
}

/** The warning of deleting a layer that entity types still have: the number of entity types (409 in-use). */
export const LayerInUse: Story = {
  render: () => ({
    components: { MetamodelPage },
    setup: () => {
      const api = metamodelApi()
      api.state.metatypes.find((metatype) => metatype.id === IDS.zone)!.architectureLayerId = IDS.layerStrategy
      return { fetchFn: api.fetch }
    },
    template: '<MetamodelPage section="layers" :fetch-fn="fetchFn" />',
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByTestId('layer-delete'))
    const dialog = within(canvasElement.ownerDocument.body)
    await userEvent.click(await dialog.findByTestId('delete-confirm'))
    await expect(await dialog.findByTestId('delete-failure')).toHaveTextContent('It is assigned to 1 entity type')
  },
}

/** The metamodel is locked (import-only, US-035): a notice and no changing actions. */
export const Locked: Story = {
  ...page('metatypes', IDS.zone, undefined, true),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Metamodel locked')).toBeInTheDocument()
    await expect(canvas.queryByTestId('metatype-new')).toBeNull()
  },
}

/**
 * The setting "Show parallel changes in the baseline" of an imported entity type, switched on
 * while the metamodel is locked (US-044; design D4 of us-044-conflict-warning).
 */
export const ParallelChanges: Story = {
  ...page('metatypes', IDS.component, undefined, true),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const toggle = await canvas.findByRole('switch', { name: 'Show parallel changes in the baseline' })
    await expect(toggle).toHaveAttribute('aria-checked', 'false')
    await userEvent.click(toggle)
    await waitFor(() =>
      expect(canvas.getByRole('switch', { name: 'Show parallel changes in the baseline' })).toHaveAttribute('aria-checked', 'true'),
    )
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

/** The field "Layer" of the entity type dialog, with the layers of the instance and "No layer". */
export const LayerSelect: Story = {
  render: () => ({
    components: { MetatypeDialog, UCard },
    setup: () => {
      const api = metamodelApi()
      const ready = ref(false)
      onBeforeMount(async () => {
        await refreshSession(api.fetch)
        await loadMetamodel(api.fetch)
        ready.value = true
      })
      return { fetchFn: api.fetch, ready, metatype: () => ({ ...metatypeById(IDS.zone)!, architectureLayerId: IDS.layerStrategy }) }
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
    const layer = await canvas.findByTestId('metatype-layer')
    await expect(layer).toHaveTextContent('Strategy')
  },
}

/** The switch "Composition" of a connection metatype: the type of the automatic connection of the hierarchy (US-135). */
export const CompositionSwitch: Story = {
  render: () => ({
    components: { MetatypeDialog, UCard },
    setup: () => {
      const api = metamodelApi()
      const ready = ref(false)
      onBeforeMount(async () => {
        await refreshSession(api.fetch)
        await loadMetamodel(api.fetch)
        ready.value = true
      })
      return { fetchFn: api.fetch, ready, metatype: () => metatypeById(IDS.dataFlow) }
    },
    template: `
      <div class="flex min-h-screen items-start justify-center bg-elevated/50 p-8">
        <UCard class="w-full max-w-lg">
          <template #header><h2 class="text-lg font-semibold text-highlighted">Edit connection type</h2></template>
          <MetatypeDialog v-if="ready" :metatype="metatype()" :fetch-fn="fetchFn" />
        </UCard>
      </div>`,
  }),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    const composition = await canvas.findByRole('switch', { name: 'Composition' })
    await expect(composition).toHaveAttribute('aria-checked', 'false')
    await userEvent.click(composition)
    await expect(composition).toHaveAttribute('aria-checked', 'true')
  },
}

// removing a mapping that active solutions use asks per solution: apply there, or keep a
// snapshot until the go-live (US-154 AC1; design D12 of us-153-open-issues)
export const SolutionDecisions: Story = {
  render: () => ({
    components: { DeleteDialog },
    setup: () => ({
      run: async (_version: number | undefined, decisions: unknown[]) =>
        decisions.length > 0
          ? { ok: true, value: undefined }
          : {
              ok: false,
              rejection: {
                status: 409,
                problemType: 'solution-decision-required',
                invalidFields: [],
                solutions: [
                  {
                    id: '5d000000-0000-4000-8000-000000000001',
                    name: 'CRM Replacement',
                    status: 'draft',
                    deltaValues: 2,
                    extensionMetatypes: 0,
                    properties: [{ metatypeId: IDS.component, propertyDefId: IDS.owner, label: 'Cost Center', enumValue: null }],
                  },
                  {
                    id: '5d000000-0000-4000-8000-000000000002',
                    name: 'Cloud Migration',
                    status: 'in-progress',
                    deltaValues: 0,
                    extensionMetatypes: 1,
                    properties: [{ metatypeId: IDS.component, propertyDefId: IDS.owner, label: 'Cost Center', enumValue: null }],
                  },
                ],
              },
            },
    }),
    template: `
      <DeleteDialog
        :open="true"
        title="Remove the mapping"
        description="Cost Center is removed from Application Component."
        :version="1"
        :run="run"
      />`,
  }),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('button', { name: 'Delete' }))
    await expect(await body.findByText('Active solutions use these values')).toBeInTheDocument()
    await expect(body.getByRole('button', { name: 'Delete' })).toBeDisabled()
  },
}

// the solutions that target a plateau are asked once per plateau (US-156; design D6 of
// us-084-plateaus)
export const SolutionDecisionsByPlateau: Story = {
  render: () => ({
    components: { DeleteDialog },
    setup: () => {
      const affected = (id: string, name: string, plateau: { id: string; name: string } | null) => ({
        id,
        name,
        status: 'draft',
        plateau,
        deltaValues: 1,
        extensionMetatypes: 0,
        properties: [{ metatypeId: IDS.component, propertyDefId: IDS.owner, label: 'Cost Center', enumValue: null }],
      })
      const target = { id: 'b1000000-0000-4000-8000-000000000002', name: 'Target Cloud Migration 2027' }
      return {
        run: async (_version: number | undefined, decisions: unknown[]) =>
          decisions.length > 0
            ? { ok: true, value: undefined }
            : {
                ok: false,
                rejection: {
                  status: 409,
                  problemType: 'solution-decision-required',
                  invalidFields: [],
                  solutions: [
                    affected('5d000000-0000-4000-8000-000000000001', 'CRM Replacement', target),
                    affected('5d000000-0000-4000-8000-000000000002', 'ERP Extension', target),
                    affected('5d000000-0000-4000-8000-000000000003', 'Website Relaunch', null),
                  ],
                },
              },
      }
    },
    template: `
      <DeleteDialog
        :open="true"
        title="Remove the mapping"
        description="Cost Center is removed from Application Component."
        :version="1"
        :run="run"
      />`,
  }),
  play: async ({ canvasElement }) => {
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.click(await body.findByRole('button', { name: 'Delete' }))
    await expect(await body.findByText('Plateau Target Cloud Migration 2027')).toBeInTheDocument()
    await expect(body.getByText(/Applies to all its solutions: CRM Replacement, ERP Extension/)).toBeInTheDocument()
    await expect(body.getByText('Website Relaunch')).toBeInTheDocument()
  },
}
