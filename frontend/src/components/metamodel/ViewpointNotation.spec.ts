import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { elementFits } from '../../api/metamodel'
import { session } from '../../session/store'
import { IDS, ME_ARCHITECT, ME_READER } from '../../test/metamodel'
import { choose, mountWithMetamodel, select } from '../../test/metamodelMount'
import ViewpointsSection from './ViewpointsSection.vue'

// spec metamodel/viewpoints "Notation and system viewpoints on SCR-031" (US-091 AC1 to AC3, US-092
// AC2; design D6 of us-091-viewpoint-notations)

const el = <T extends HTMLElement = HTMLElement>(testId: string) => document.querySelector<T>(`[data-testid="${testId}"]`)

async function type(testId: string, value: string) {
  const input = el<HTMLInputElement>(testId)!
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function check(group: string, value: string) {
  const box = [...document.querySelectorAll<HTMLElement>(`[data-testid="${group}"] button[role="checkbox"], [data-testid="${group}"] input[type="checkbox"]`)].find(
    (candidate) => candidate.getAttribute('value') === value || candidate.id.endsWith(value),
  )
  box!.click()
  await flushPromises()
}

const submit = async () => {
  el<HTMLButtonElement>('viewpoint-submit')!.click()
  await flushPromises()
}

const labelsOf = (items: unknown) => (items as { value: string; label: string }[]).map((item) => item.value)

async function openEdit(me = ME_ARCHITECT) {
  const mounted = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me })
  await mounted.wrapper.find('[data-testid="viewpoint-list"] [data-testid="viewpoint-edit"]').trigger('click')
  await flushPromises()
  return mounted
}

async function openNew() {
  const mounted = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
  await mounted.wrapper.find('[data-testid="viewpoint-new"]').trigger('click')
  await flushPromises()
  return mounted
}

describe('Viewpoints with notations', () => {
  beforeEach(() => {
    session.me = undefined
    document.body.innerHTML = ''
  })

  // "System-defined viewpoint in the list"
  it('groups the system-defined viewpoints with a lock and without actions, and shows the notations', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    const system = wrapper.find('[data-testid="viewpoint-group-system"]')
    expect(system.text()).toContain('System-defined')
    expect(system.text()).toContain('Technology Landscape')
    expect(system.text()).toContain('Package oea-starter-togaf-classic 1.2.0')
    expect(system.find('[data-testid="viewpoint-lock"]').text()).toContain('System-defined, read-only')
    expect(system.find('[data-testid="viewpoint-edit"]').exists()).toBe(false)
    expect(system.find('[data-testid="viewpoint-delete"]').exists()).toBe(false)
    expect(system.find('[data-testid="viewpoint-notation-badge"]').text()).toBe('ArchiMate 3')
    const organization = wrapper.find('[data-testid="viewpoint-group-organization"]')
    expect(organization.text()).toContain('Application Landscape')
    expect(organization.find('[data-testid="viewpoint-edit"]').exists()).toBe(true)
    expect(organization.find('[data-testid="viewpoint-notation-badge"]').text()).toBe('ArchiMate 3')
  })

  it('shows no system-defined group without such viewpoints', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, noSystemViewpoints: true })
    expect(wrapper.find('[data-testid="viewpoint-group-system"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="viewpoint-group-organization"]').text()).toContain('Application Landscape')
  })

  it('offers a reader no action on any viewpoint', async () => {
    const { wrapper } = await mountWithMetamodel(ViewpointsSection, { canManage: false }, { me: ME_READER })
    expect(wrapper.findAll('[data-testid="viewpoint-row"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="viewpoint-edit"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="viewpoint-delete"]').exists()).toBe(false)
  })

  // "Filtered choice"
  it.each([
    ['archimate3', ['none', 'archimate:ApplicationComponent', 'archimate:ApplicationService', 'archimate:BusinessActor', 'archimate:DataObject', 'generic:Box'], ['none', 'archimate:Flow', 'archimate:Serving', 'generic:Arrow']],
    ['uml', ['none', 'uml:Component', 'generic:Box'], ['none', 'uml:Association', 'generic:Arrow']],
    ['bpmn2', ['none', 'bpmn:Task', 'generic:Box'], ['none', 'bpmn:SequenceFlow', 'generic:Arrow']],
    ['generic', ['none', 'generic:Box'], ['none', 'generic:Arrow']],
  ])('offers for the notation %s the elements of it and the generic ones, by kind', async (notation, elements, relationships) => {
    const { wrapper } = await openNew()
    await choose(wrapper, 'viewpoint-notation', notation)
    await check('viewpoint-metatypes', IDS.component)
    await check('viewpoint-connections', IDS.dataFlow)
    expect(labelsOf(select(wrapper, `mapping-element-${IDS.component}`)!.props('items'))).toEqual(elements)
    expect(labelsOf(select(wrapper, `mapping-element-${IDS.dataFlow}`)!.props('items'))).toEqual(relationships)
  })

  it('offers the notations in the dialog and starts with generic', async () => {
    const { wrapper } = await openNew()
    expect(select(wrapper, 'viewpoint-notation')!.props('modelValue')).toBe('generic')
    expect(labelsOf(select(wrapper, 'viewpoint-notation')!.props('items'))).toEqual(['generic', 'archimate3', 'uml', 'bpmn2'])
  })

  // "Notation changed"
  it('clears the elements that no longer fit when the notation changes, and keeps the generic ones', async () => {
    const { wrapper, api } = await openEdit()
    // "Application Component" is mapped to an ArchiMate element, with a size and a color
    expect(select(wrapper, `mapping-element-${IDS.component}`)!.props('modelValue')).toBe('archimate:ApplicationComponent')
    expect(el<HTMLInputElement>(`mapping-width-${IDS.component}`)!.value).toBe('140')
    expect(el<HTMLInputElement>(`mapping-color-${IDS.component}`)!.value).toBe('#1a2b3c')
    await choose(wrapper, `mapping-element-${IDS.service}`, 'generic:Box')
    await choose(wrapper, 'viewpoint-notation', 'bpmn2')
    expect(select(wrapper, `mapping-element-${IDS.component}`)!.props('modelValue')).toBe('none')
    expect(el<HTMLInputElement>(`mapping-width-${IDS.component}`)!.value).toBe('')
    expect(el(`mapping-color-clear-${IDS.component}`)).toBeNull()
    expect(select(wrapper, `mapping-element-${IDS.service}`)!.props('modelValue')).toBe('generic:Box')
    await submit()
    const put = api.requests.find((request) => request.method === 'PUT')
    // only the generic element is sent
    expect((put?.body as { notation: string; mappings: { notationElement: string }[] }).notation).toBe('bpmn2')
    expect((put?.body as { mappings: { notationElement: string }[] }).mappings.map((mapping) => mapping.notationElement)).toEqual(['generic:Box'])
  })

  it('keeps the elements that still fit when the notation stays', async () => {
    const { wrapper } = await openEdit()
    await choose(wrapper, 'viewpoint-notation', 'archimate3')
    expect(select(wrapper, `mapping-element-${IDS.component}`)!.props('modelValue')).toBe('archimate:ApplicationComponent')
  })

  // the request
  it('sends the notation and the mappings of the selected types', async () => {
    const { wrapper, api } = await openNew()
    await type('viewpoint-name', 'Integration View')
    await choose(wrapper, 'viewpoint-notation', 'uml')
    await check('viewpoint-metatypes', IDS.component)
    await check('viewpoint-metatypes', IDS.service)
    await check('viewpoint-connections', IDS.dataFlow)
    await choose(wrapper, `mapping-element-${IDS.component}`, 'uml:Component')
    await type(`mapping-width-${IDS.component}`, '140')
    await type(`mapping-height-${IDS.component}`, '60')
    const color = el<HTMLInputElement>(`mapping-color-${IDS.component}`)!
    color.value = '#a1b2c3'
    color.dispatchEvent(new Event('input'))
    await choose(wrapper, `mapping-element-${IDS.dataFlow}`, 'uml:Association')
    await submit()
    const post = api.requests.find((request) => request.method === 'POST' && request.path === '/api/v1/admin/viewpoints')
    expect(post?.body).toMatchObject({
      name: 'Integration View',
      notation: 'uml',
      metatypeIds: [IDS.component, IDS.service],
      connectionTypeIds: [IDS.dataFlow],
      mappings: [
        { metatypeId: IDS.component, notationElement: 'uml:Component', defaultWidth: 140, defaultHeight: 60, visualHint: '#a1b2c3' },
        { metatypeId: IDS.dataFlow, notationElement: 'uml:Association', defaultWidth: null, defaultHeight: null, visualHint: null },
      ],
    })
    // the type without an element has no mapping
    expect((post?.body as { mappings: unknown[] }).mappings).toHaveLength(2)
  })

  it('sends no mapping for a type that was deselected', async () => {
    const { api } = await openEdit()
    await check('viewpoint-metatypes', IDS.component)
    await submit()
    const put = api.requests.find((request) => request.method === 'PUT')
    expect(put?.body).toMatchObject({ notation: 'archimate3', metatypeIds: [IDS.service], mappings: [] })
  })

  it('clears the color of a mapping', async () => {
    const { api } = await openEdit()
    el<HTMLButtonElement>(`mapping-color-clear-${IDS.component}`)!.click()
    await flushPromises()
    await submit()
    const put = api.requests.find((request) => request.method === 'PUT')
    expect((put?.body as { mappings: { visualHint: string | null }[] }).mappings[0]!.visualHint).toBeNull()
  })

  // both sides of the size boundary (REQ-091: 1 to 2000)
  it.each([
    ['1', true],
    ['2000', true],
    ['', true],
    ['0', false],
    ['2001', false],
    ['-5', false],
    ['1.5', false],
  ])('a width of "%s" is accepted: %s', async (width, accepted) => {
    await openEdit()
    await type(`mapping-width-${IDS.component}`, width)
    expect(el<HTMLButtonElement>('viewpoint-submit')!.disabled).toBe(!accepted)
    expect(el('mapping-size-error') !== null).toBe(!accepted)
    // the height has the same limits
    await type(`mapping-width-${IDS.component}`, '100')
    await type(`mapping-height-${IDS.component}`, width)
    expect(el<HTMLButtonElement>('viewpoint-submit')!.disabled).toBe(!accepted)
  })

  it('does not ask for a size while a type has no element', async () => {
    const { wrapper } = await openEdit()
    await type(`mapping-width-${IDS.component}`, '0')
    expect(el<HTMLButtonElement>('viewpoint-submit')!.disabled).toBe(true)
    await choose(wrapper, `mapping-element-${IDS.component}`, 'none')
    expect(el<HTMLButtonElement>('viewpoint-submit')!.disabled).toBe(false)
    expect(el<HTMLInputElement>(`mapping-width-${IDS.component}`)!.disabled).toBe(true)
  })

  // "a 422 shows at the mapping"
  it('shows a rejected mapping at the mapping table', async () => {
    const mounted = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT, rejectMappings: true })
    await mounted.wrapper.find('[data-testid="viewpoint-list"] [data-testid="viewpoint-edit"]').trigger('click')
    await flushPromises()
    await submit()
    const text = el('viewpoint-form')!.textContent ?? ''
    expect(text).toContain('The mapping does not fit the notation.')
    // next to the mapping table, not as a failure of the whole form
    expect(el('viewpoint-failure')).toBeNull()
    expect(el('viewpoint-mappings')!.closest('[data-slot="root"]')!.textContent).toContain('The mapping does not fit the notation.')
  })

  it('shows a failed catalog next to the mapping, and still saves without mappings', async () => {
    const { wrapper, api } = await mountWithMetamodel(ViewpointsSection, { canManage: true }, { me: ME_ARCHITECT })
    // the catalog of a section whose read failed
    const failing = ((original) => async (input: RequestInfo | URL, init?: RequestInit) =>
      String(input).endsWith('/viewpoints/notations') ? new Response('{}', { status: 500 }) : original(input, init))(api.fetch)
    wrapper.unmount()
    document.body.innerHTML = ''
    const again = await mountWithMetamodel(ViewpointsSection, { canManage: true, fetchFn: failing }, { me: ME_ARCHITECT })
    await again.wrapper.find('[data-testid="viewpoint-new"]').trigger('click')
    await flushPromises()
    await type('viewpoint-name', 'Plain')
    await check('viewpoint-metatypes', IDS.component)
    expect(el('viewpoint-catalog-failed')?.textContent).toContain('The notation elements could not be loaded.')
    expect(el<HTMLButtonElement>('viewpoint-submit')!.disabled).toBe(false)
  })
})

describe('elementFits', () => {
  it.each([
    ['archimate3', 'archimate:Node', true],
    ['archimate3', 'generic:Box', true],
    ['archimate3', 'generic:Arrow', true],
    ['archimate3', 'uml:Component', false],
    ['archimate3', 'bpmn:Task', false],
    ['uml', 'uml:Class', true],
    ['uml', 'archimate:Node', false],
    ['bpmn2', 'bpmn:Task', true],
    ['bpmn2', 'uml:Class', false],
    ['generic', 'generic:Box', true],
    ['generic', 'archimate:Node', false],
  ] as const)('the notation %s with %s: %s', (notation, key, fits) => {
    expect(elementFits(notation, key)).toBe(fits)
  })
})
