import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { reloadMetamodel } from '../../metamodel/store'
import { session } from '../../session/store'
import { IDS, ME_READER } from '../../test/metamodel'
import { mountWithMetamodel } from '../../test/metamodelMount'
import ArchitectureLayersSection from './ArchitectureLayersSection.vue'

// spec metamodel/architecture-layers (change s17-architecture-layers): the section "Layers" of SCR-030

function el<T extends HTMLElement = HTMLElement>(testId: string) {
  return document.querySelector<T>(`[data-testid="${testId}"]`)
}

async function type(testId: string, value: string) {
  const input = el<HTMLInputElement>(testId)!
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

const LAYERS = '/api/v1/metamodel/architecture-layers'

describe('ArchitectureLayersSection', () => {
  beforeEach(() => {
    session.me = undefined
    document.body.innerHTML = ''
  })

  it('lists the layers in their order with origin and number of entity types', async () => {
    const { wrapper } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    const rows = wrapper.findAll('[data-testid="layer-row"]')
    expect(rows.map((row) => row.get('[data-testid="layer-name-shown"]').text())).toEqual(['Business', 'Application', 'Technology', 'Strategy'])
    expect(rows[1]!.get('[data-testid="layer-count"]').text()).toBe('2 entity types')
    expect(rows[0]!.get('[data-testid="layer-count"]').text()).toBe('0 entity types')
    expect(rows[1]!.get('[data-testid="layer-scope"]').text()).toContain('oea-starter-togaf-classic 1.4.0')
    expect(rows[3]!.get('[data-testid="layer-scope"]').text()).toBe('Own')
    expect(wrapper.get('h2').text()).toBe('Layers (4)')
  })

  it('shows the translated name from the label bundle', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    expect(api.state.layers).toHaveLength(4)
    const labels = await import('../../i18n/labels')
    labels.labels.bundle = { [labels.labelKeys.architectureLayer(IDS.layerBusiness)]: 'Geschäft' }
    await flushPromises()
    expect(wrapper.findAll('[data-testid="layer-name-shown"]')[0]!.text()).toBe('Geschäft')
    labels.labels.bundle = {}
  })

  it('offers actions only for organization layers', async () => {
    const { wrapper } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    const rows = wrapper.findAll('[data-testid="layer-row"]')
    for (const imported of rows.slice(0, 3)) {
      expect(imported.find('[data-testid="layer-edit"]').exists()).toBe(false)
      expect(imported.find('[data-testid="layer-delete"]').exists()).toBe(false)
      expect(imported.find('[data-testid="layer-up"]').exists()).toBe(false)
    }
    expect(rows[3]!.find('[data-testid="layer-edit"]').exists()).toBe(true)
    expect(rows[3]!.find('[data-testid="layer-delete"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="layer-imported-hint"]').text()).toContain('cannot be changed')
  })

  it('is read-only without the permission to write', async () => {
    const { wrapper } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: false }, { me: ME_READER })
    expect(wrapper.findAll('[data-testid="layer-row"]')).toHaveLength(4)
    for (const testId of ['layer-new', 'layer-edit', 'layer-delete', 'layer-up', 'layer-down']) {
      expect(wrapper.find(`[data-testid="${testId}"]`).exists()).toBe(false)
    }
  })

  it('creates a layer with the stripped name and lists it last', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    await wrapper.get('[data-testid="layer-new"]').trigger('click')
    await flushPromises()
    await type('layer-name', '  Data ')
    el<HTMLButtonElement>('layer-submit')!.click()
    await flushPromises()
    const post = api.requests.find((request) => request.method === 'POST' && request.path === LAYERS)!
    expect(post.body).toEqual({ name: 'Data' })
    expect(post.ifMatch).toBeUndefined()
    expect(post.csrf).toBe('csrf-metamodel')
    expect(wrapper.findAll('[data-testid="layer-name-shown"]').map((cell) => cell.text()).at(-1)).toBe('Data')
    expect(wrapper.get('h2').text()).toBe('Layers (5)')
  })

  it.each([
    ['n'.repeat(100), true],
    ['n'.repeat(101), false],
    ['n', true],
    ['   ', false],
    ['', false],
  ])('checks the name %#: 1 to 100 characters', async (name, accepted) => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    await wrapper.get('[data-testid="layer-new"]').trigger('click')
    await flushPromises()
    await type('layer-name', name)
    el<HTMLButtonElement>('layer-submit')!.click()
    await flushPromises()
    const posted = api.requests.some((request) => request.method === 'POST' && request.path === LAYERS)
    expect(posted).toBe(accepted)
    if (!accepted) {
      expect(el('layer-form')!.textContent).toContain('Enter a name with 1 to 100 characters.')
    }
  })

  it('shows a name that is taken regardless of case at its field', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    await wrapper.get('[data-testid="layer-new"]').trigger('click')
    await flushPromises()
    await type('layer-name', 'business')
    el<HTMLButtonElement>('layer-submit')!.click()
    await flushPromises()
    expect(el('layer-form')!.textContent).toContain('This name is already used')
    expect(api.state.layers).toHaveLength(4)
  })

  it('renames a layer with the version read', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    await wrapper.get('[data-testid="layer-edit"]').trigger('click')
    await flushPromises()
    expect(el<HTMLInputElement>('layer-name')!.value).toBe('Strategy')
    await type('layer-name', 'Vision')
    el<HTMLButtonElement>('layer-submit')!.click()
    await flushPromises()
    const put = api.requests.find((request) => request.method === 'PUT')!
    expect(put.path).toBe(`${LAYERS}/${IDS.layerStrategy}`)
    expect(put.ifMatch).toBe('"1"')
    expect(put.body).toEqual({ name: 'Vision' })
    expect(wrapper.findAll('[data-testid="layer-name-shown"]').map((cell) => cell.text())).toContain('Vision')
  })

  it('shows the collision warning when the layer changed meanwhile', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    api.state.layers.find((layer) => layer.id === IDS.layerStrategy)!.version = 4
    await wrapper.get('[data-testid="layer-edit"]').trigger('click')
    await flushPromises()
    api.state.layers.find((layer) => layer.id === IDS.layerStrategy)!.version = 5
    await type('layer-name', 'Vision')
    el<HTMLButtonElement>('layer-submit')!.click()
    await flushPromises()
    expect(el('collision-warning')).toBeTruthy()
    expect(el('collision-current')!.textContent).toContain('Strategy')
    expect(api.state.layers.find((layer) => layer.id === IDS.layerStrategy)!.name).toBe('Strategy')
    // overwriting sends the current version
    el<HTMLButtonElement>('collision-overwrite')!.click()
    await flushPromises()
    expect(api.requests.filter((request) => request.method === 'PUT').at(-1)!.ifMatch).toBe('"5"')
    expect(api.state.layers.find((layer) => layer.id === IDS.layerStrategy)!.name).toBe('Vision')
  })

  it('moves a layer up and down with the version read', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    await wrapper.get('[data-testid="layer-up"]').trigger('click')
    await flushPromises()
    const put = api.requests.find((request) => request.method === 'PUT')!
    expect(put.ifMatch).toBe('"1"')
    expect(put.body).toEqual({ name: 'Strategy', sortOrder: 2 })
    expect(wrapper.findAll('[data-testid="layer-name-shown"]').map((cell) => cell.text())).toEqual(['Business', 'Application', 'Strategy', 'Technology'])
    // the last layer cannot move down, a layer in the first place cannot move up
    expect(wrapper.get('[data-testid="layer-down"]').attributes('disabled')).toBeUndefined()
    api.state.layers.forEach((layer) => (layer.scope = 'organization'))
    await reloadMetamodel(api.fetch)
    await flushPromises()
    const rows = wrapper.findAll('[data-testid="layer-row"]')
    expect(rows[0]!.get('[data-testid="layer-up"]').attributes('disabled')).toBeDefined()
    expect(rows[3]!.get('[data-testid="layer-down"]').attributes('disabled')).toBeDefined()
    expect(rows[1]!.get('[data-testid="layer-up"]').attributes('disabled')).toBeUndefined()
  })

  it('reloads and says so when a move meets a parallel change', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    api.state.layers.find((layer) => layer.id === IDS.layerStrategy)!.version = 2
    await wrapper.get('[data-testid="layer-up"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="layer-list-failure"]').text()).toContain('Someone else changed this element meanwhile')
  })

  it('deletes an unused layer with the version read', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    await wrapper.get('[data-testid="layer-delete"]').trigger('click')
    await flushPromises()
    el<HTMLButtonElement>('delete-confirm')!.click()
    await flushPromises()
    const request = api.requests.find((candidate) => candidate.method === 'DELETE')!
    expect(request.path).toBe(`${LAYERS}/${IDS.layerStrategy}`)
    expect(request.ifMatch).toBe('"1"')
    expect(wrapper.findAll('[data-testid="layer-row"]')).toHaveLength(3)
  })

  it.each([
    [1, 'It is assigned to 1 entity type, which must first lose the layer.'],
    [2, 'It is assigned to 2 entity types, which must first lose the layer.'],
  ])('names the %i entity types of a layer in use', async (count, message) => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    for (const id of [IDS.zone, IDS.cloudZone].slice(0, count)) {
      api.state.metatypes.find((metatype) => metatype.id === id)!.architectureLayerId = IDS.layerStrategy
    }
    await wrapper.get('[data-testid="layer-delete"]').trigger('click')
    await flushPromises()
    el<HTMLButtonElement>('delete-confirm')!.click()
    await flushPromises()
    expect(el('delete-failure')!.textContent).toContain(message)
    expect(api.state.layers.some((layer) => layer.id === IDS.layerStrategy)).toBe(true)
  })

  it.each([
    [19, true],
    [20, false],
  ])('with %i layers the new layer is possible: %s', async (count, possible) => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    while (api.state.layers.length < count) {
      api.state.layers.push({ id: `f2000000-0000-4000-8000-${String(api.state.layers.length).padStart(12, '0')}`, name: `Layer ${api.state.layers.length}`, sortOrder: api.state.layers.length, scope: 'organization', sourcePackage: null, version: 1 })
    }
    await reloadMetamodel(api.fetch)
    await flushPromises()
    expect(wrapper.findAll('[data-testid="layer-row"]')).toHaveLength(count)
    expect(wrapper.get('[data-testid="layer-new"]').attributes('disabled') === undefined).toBe(possible)
    expect(wrapper.get('[data-testid="layer-hint"]').text().includes('limit of 20 layers')).toBe(!possible)
  }, 20000)

  it('shows a name with markup literally', async () => {
    const { wrapper, api } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: true })
    api.state.layers.push({ id: 'f2000000-0000-4000-8000-000000000099', name: '<img src=x onerror=alert(1)>', sortOrder: 4, scope: 'organization', sourcePackage: null, version: 1 })
    await reloadMetamodel(api.fetch)
    await flushPromises()
    expect(wrapper.findAll('[data-testid="layer-name-shown"]').at(-1)!.text()).toBe('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('shows no actions of a layer in a locked metamodel', async () => {
    const { wrapper } = await mountWithMetamodel(ArchitectureLayersSection, { canWrite: false }, { locked: true })
    expect(wrapper.findAll('[data-testid="layer-row"]')).toHaveLength(4)
    expect(wrapper.find('[data-testid="layer-new"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="layer-edit"]').exists()).toBe(false)
  })
})
