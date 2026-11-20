import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { diagramApi, diagramView, ME_DIAGRAM_READER, ME_DIAGRAM_WRITER, METATYPE_PROCESS, PROP_HOSTED_ON, PROP_INVESTMENT, PROP_OWNER, PROP_STATUS, shape, type DiagramApiOptions } from '../../test/diagrams'
import { testRouter } from '../../test/router'
import PropertyInput from '../PropertyInput.vue'
import { fromView, placeNew, type CanvasShape, type EntityEdit } from './canvasModel'
import EntityDetailsPanel from './EntityDetailsPanel.vue'

// spec diagram/diagram-entity-details "Details panel" (SCR-021, US-074 step 6)
const ERP = fromView(shape())
const APPROVAL = fromView(shape({ id: 'd1000000-0000-4000-8000-000000000003', entity: { id: 14, name: 'Invoice Approval', metatypeId: METATYPE_PROCESS }, missingMandatory: ['Process Owner'] }))

const mounted: VueWrapper[] = []

async function mountPanel(
  props: { shape?: CanvasShape; editable?: boolean; edit?: EntityEdit; errors?: Map<string, string> } = {},
  options: Partial<DiagramApiOptions> = {},
) {
  const target = props.shape ?? ERP
  const fetchFn = diagramApi({ me: ME_DIAGRAM_WRITER, shapes: [shape(), shape({ id: APPROVAL.id, entity: { id: 14, name: 'Invoice Approval', metatypeId: METATYPE_PROCESS } })], ...options })
  const wrapper = mount(EntityDetailsPanel, {
    props: { diagramId: diagramView().diagram.id, shape: target, editable: props.editable ?? true, edit: props.edit, errors: props.errors, fetchFn },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, fetchFn }
}

function field(wrapper: VueWrapper, name: string) {
  return wrapper.get(`[data-testid="details-field-${name}"]`)
}

describe('EntityDetailsPanel', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
  })

  it('shows name, metatype, version and the values in the form of the metatype', async () => {
    const { wrapper } = await mountPanel()
    expect(wrapper.get('[data-testid="details-title"]').text()).toBe('SAP ERP')
    expect(wrapper.get('[data-testid="details-meta"]').text()).toContain('Application Component')
    expect(wrapper.get('[data-testid="details-meta"]').text()).toContain('Version 4')
    expect(wrapper.findAllComponents(PropertyInput)).toHaveLength(3)
    expect(wrapper.find('[data-testid="details-missing"]').exists()).toBe(false)
  })

  it('shows a reader the values without input fields, and a value the reader may not read as "Restricted"', async () => {
    const { wrapper } = await mountPanel({ editable: false }, { me: ME_DIAGRAM_READER, redactedProperties: [PROP_INVESTMENT] })
    expect(wrapper.findAllComponents(PropertyInput)).toHaveLength(0)
    expect(wrapper.find('input, textarea').exists()).toBe(false)
    expect(wrapper.find('[data-testid="details-read-only"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="details-value-Status"]').text()).toBe('active')
    expect(field(wrapper, 'Investment').get('[data-testid="details-restricted"]').text()).toBe('Restricted')
    expect(wrapper.text()).not.toContain('5000000')
  })

  it('offers an input only where the server marks the property as writable', async () => {
    const { wrapper } = await mountPanel({}, { readOnlyProperties: [PROP_INVESTMENT] })
    expect(field(wrapper, 'Status').findComponent(PropertyInput).exists()).toBe(true)
    expect(field(wrapper, 'Investment').findComponent(PropertyInput).exists()).toBe(false)
    expect(field(wrapper, 'Investment').text()).toContain('5000000')
  })

  it('marks required values and names the missing ones', async () => {
    const { wrapper } = await mountPanel({ shape: APPROVAL })
    expect(wrapper.get('[data-testid="details-missing"]').text()).toContain('Process Owner')
    expect(field(wrapper, 'Process Owner').text()).toContain('Required value missing')
    // a value entered clears the mark
    await wrapper.setProps({ edit: { version: 1, properties: { [PROP_OWNER]: 'Anna' } } })
    expect(wrapper.find('[data-testid="details-missing"]').exists()).toBe(false)
  })

  it('says that a required property the caller may not change is required but not changeable', async () => {
    const { wrapper } = await mountPanel({ shape: APPROVAL }, { readOnlyProperties: [PROP_OWNER] })
    expect(field(wrapper, 'Process Owner').findComponent(PropertyInput).exists()).toBe(false)
    expect(field(wrapper, 'Process Owner').text()).toContain('you may not change it')
    expect(wrapper.get('[data-testid="details-missing"]').text()).toContain('Process Owner')
  })

  it('emits the change of a field with the version read, and nothing but the removal when it is back at the value read', async () => {
    const { wrapper } = await mountPanel()
    const textarea = wrapper.get('[data-testid="details-description"]')
    await (textarea.element.tagName === 'TEXTAREA' ? textarea : textarea.get('textarea')).setValue('Renamed ERP')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ field: 'description', value: 'Renamed ERP' }, 4])
    await (textarea.element.tagName === 'TEXTAREA' ? textarea : textarea.get('textarea')).setValue('Core ERP for finance and logistics')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ field: 'description', value: undefined }, 4])
    const input = field(wrapper, 'Status').findComponent(PropertyInput)
    input.vm.$emit('update:modelValue', 'retiring')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ field: 'property', propertyDefId: PROP_STATUS, value: 'retiring' }, 4])
    input.vm.$emit('update:modelValue', 'active')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ field: 'property', propertyDefId: PROP_STATUS, value: undefined }, 4])
  })

  it('shows what was entered over the value read', async () => {
    const { wrapper } = await mountPanel({ edit: { version: 4, name: 'SAP S/4', description: 'New text', properties: { [PROP_STATUS]: 'retiring' } } })
    expect(wrapper.get('[data-testid="details-title"]').text()).toBe('SAP S/4')
    expect(field(wrapper, 'Status').findComponent(PropertyInput).props('modelValue')).toBe('retiring')
    expect((wrapper.get('[data-testid="details-field-name"] input').element as HTMLInputElement).value).toBe('SAP S/4')
    expect((wrapper.get('[data-testid="details-field-description"] textarea').element as HTMLTextAreaElement).value).toBe('New text')
  })

  it('chooses a reference through the reference search of the diagram', async () => {
    const recorded: { path: string }[] = []
    const { wrapper } = await mountPanel({}, { recorded })
    const input = field(wrapper, 'Hosted on').findComponent(PropertyInput)
    const found = await (input.props('searchReferences') as (term: string) => Promise<{ label: string; value: string }[]>)('sap')
    expect(found).toEqual([
      { label: 'SAP BW', value: '11' },
      { label: 'SAP ERP', value: '12' },
    ])
    expect(recorded.some((entry) => entry.path.includes(`/reference-targets?propertyDefId=${PROP_HOSTED_ON}`) && entry.path.includes('q=sap'))).toBe(true)
    input.vm.$emit('update:modelValue', '11')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ field: 'property', propertyDefId: PROP_HOSTED_ON, value: '11' }, 4])
  })

  it('shows the messages of a rejected save at the fields and at the entity', async () => {
    const errors = new Map([
      ['14|', 'The mandatory property Process Owner has no value.'],
      [`14|${PROP_OWNER}`, 'Not a valid value.'],
    ])
    const { wrapper } = await mountPanel({ shape: APPROVAL, errors })
    expect(wrapper.get('[data-testid="details-error"]').text()).toContain('Process Owner has no value')
    expect(field(wrapper, 'Process Owner').text()).toContain('Not a valid value.')
  })

  it('reads only the metatype for a new entity and offers every field but the connection-scoped ones', async () => {
    const recorded: { path: string }[] = []
    const fresh = { ...placeNew(diagramView(), METATYPE_PROCESS, { x: 0, y: 0 }), name: 'Order Entry', editing: false }
    const { wrapper } = await mountPanel({ shape: fresh }, { recorded })
    expect(recorded.some((entry) => entry.path.includes('/entities/'))).toBe(false)
    expect(recorded.some((entry) => entry.path.endsWith(`/metamodel/metatypes/${METATYPE_PROCESS}`))).toBe(true)
    expect(wrapper.get('[data-testid="details-meta"]').text()).toContain('New entity')
    expect(field(wrapper, 'Process Owner').findComponent(PropertyInput).exists()).toBe(true)
    expect(wrapper.get('[data-testid="details-missing"]').text()).toContain('Process Owner')
    field(wrapper, 'Process Owner').findComponent(PropertyInput).vm.$emit('update:modelValue', 'Anna')
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ field: 'property', propertyDefId: PROP_OWNER, value: 'Anna' }, 0])
  })

  it('closes with Escape and with the close button', async () => {
    const { wrapper } = await mountPanel()
    await wrapper.get('[data-testid="entity-details"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toHaveLength(1)
    await wrapper.get('[data-testid="details-close"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(2)
  })

  it('says so when the details cannot be loaded and when the entity is gone', async () => {
    const failing = await mountPanel({}, { entityStates: {} })
    expect(failing.wrapper.find('[data-testid="details-gone"]').exists()).toBe(true)
    const unreachable = mount(EntityDetailsPanel, {
      props: { diagramId: diagramView().diagram.id, shape: ERP, editable: true, fetchFn: vi.fn(async () => { throw new Error('offline') }) as unknown as typeof fetch },
      global: { plugins: [ui, testRouter()] },
    })
    mounted.push(unreachable)
    await flushPromises()
    expect(unreachable.find('[data-testid="details-failure"]').exists()).toBe(true)
  })
})
