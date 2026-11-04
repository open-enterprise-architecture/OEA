import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { deleteMetatype, deletePropertyDefinition } from '../../api/metamodel'
import { session } from '../../session/store'
import { IDS } from '../../test/metamodel'
import { mountWithMetamodel } from '../../test/metamodelMount'
import DeleteDialog from './DeleteDialog.vue'

function confirmButton() {
  return document.querySelector<HTMLButtonElement>('[data-testid="delete-confirm"]')!
}

// spec metamodel/configuration-screen "Editing metatypes", "Editing properties and categories"
describe('DeleteDialog', () => {
  beforeEach(() => {
    session.me = undefined
    document.body.innerHTML = ''
  })

  // "Deleting a metatype in use"
  it('names the entities of a metatype in use', async () => {
    const { wrapper, api } = await mountWithMetamodel(DeleteDialog, {
      open: true,
      title: 'Delete',
      description: 'Delete it?',
      version: 3,
      run: (version: number | undefined) => deleteMetatype(IDS.zone, version, api.fetch),
    })
    void wrapper
    confirmButton().click()
    await flushPromises()
    expect(document.querySelector('[data-testid="delete-failure"]')?.textContent).toContain(
      '5 entities of this type must first be deleted.',
    )
    expect(api.state.metatypes.some((metatype) => metatype.id === IDS.zone)).toBe(true)
  })

  // "Property in use"
  it('says that a mapped property is in use', async () => {
    const { api } = await mountWithMetamodel(DeleteDialog, {
      open: true,
      title: 'Delete',
      description: 'Delete it?',
      version: 1,
      run: (version: number | undefined) => deletePropertyDefinition(IDS.owner, version, api.fetch),
    })
    confirmButton().click()
    await flushPromises()
    expect(document.querySelector('[data-testid="delete-failure"]')?.textContent).toContain('It is still in use')
    expect(api.state.properties.some((property) => property.id === IDS.owner)).toBe(true)
  })

  it('asks again with the current version after a collision', async () => {
    const { wrapper, api } = await mountWithMetamodel(DeleteDialog, {
      open: true,
      title: 'Delete',
      description: 'Delete it?',
      version: 1,
      run: (version: number | undefined) => deleteMetatype(IDS.cloudZone, version, api.fetch),
    })
    api.changeElsewhere(IDS.cloudZone, 'CL')
    confirmButton().click()
    await flushPromises()
    expect(wrapper.emitted('stale')).toHaveLength(1)
    document.querySelector<HTMLButtonElement>('[data-testid="collision-overwrite"]')!.click()
    await flushPromises()
    expect(api.requests.filter((request) => request.method === 'DELETE').map((request) => request.ifMatch)).toEqual(['"1"', '"2"'])
    expect(wrapper.emitted('done')).toHaveLength(1)
  })
})
