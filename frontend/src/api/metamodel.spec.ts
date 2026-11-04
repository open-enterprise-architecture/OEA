import { describe, expect, it } from 'vitest'
import { refreshSession } from '../session/store'
import { IDS, metamodelApi } from '../test/metamodel'
import { createEnumValue, deleteMetatype, INSTANCE_CONFIGURATION, updateMetatype } from './metamodel'

// design D2, D4 of scr-030-metamodel-configuration
describe('metamodel writes', () => {
  it('send the version as If-Match and the CSRF token', async () => {
    const api = metamodelApi()
    await refreshSession(api.fetch)
    const outcome = await updateMetatype(
      IDS.zone,
      { name: 'Network Zone', abbreviation: 'NW', sortOrder: 10, isConnection: false, configId: INSTANCE_CONFIGURATION },
      3,
      api.fetch,
    )
    expect(outcome.ok).toBe(true)
    const put = api.requests.find((request) => request.method === 'PUT')!
    expect(put.ifMatch).toBe('"3"')
    expect(put.csrf).toBe('csrf-metamodel')
  })

  it('return a stale version as rejection with the current version', async () => {
    const api = metamodelApi()
    const outcome = await deleteMetatype(IDS.cloudZone, 7, api.fetch)
    expect(outcome).toEqual({ ok: false, rejection: expect.objectContaining({ status: 412, problemType: 'stale-version', currentVersion: 1 }) })
    expect(api.state.metatypes.some((metatype) => metatype.id === IDS.cloudZone)).toBe(true)
  })

  it('send no If-Match without a version', async () => {
    const api = metamodelApi()
    api.state.properties[1]!.dataType = 'enum'
    await createEnumValue(IDS.owner, 'high', undefined, api.fetch)
    expect(api.requests.at(-1)?.ifMatch).toBeUndefined()
  })
})
