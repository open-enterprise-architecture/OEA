import { describe, expect, it } from 'vitest'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { createCatalog, fetchCatalog, queryCatalog, type Catalog, type CatalogQueryResult } from './catalogs'

const CATALOG: Catalog = {
  id: '6f1c2d3e-0000-4000-8000-000000000001',
  name: 'Application Inventory',
  primaryMetatypeId: '6f1c2d3e-0000-4000-8000-0000000000aa',
  primaryMetatypeName: 'Application Component',
  scope: 'instance',
  createdBy: { principalType: 'person', id: 7, displayName: 'Kurt' },
  createdAt: '2026-10-19T10:00:00Z',
  description: '',
  defaultJoinMode: 'aggregate',
  lastModifiedAt: '2026-10-19T10:00:00Z',
  columns: [],
  joinDefinitions: [],
  savedFilters: [],
  savedViews: [],
}

const REQUEST = {
  name: 'Application Inventory',
  primaryMetatypeId: CATALOG.primaryMetatypeId,
  scope: 'instance' as const,
  defaultJoinMode: 'aggregate' as const,
}

function problem(type: string, invalidFields: string[]) {
  return {
    status: 422,
    contentType: 'application/problem+json',
    body: { type: `urn:oea:problem:${type}`, title: 'Rejected', status: 422, invalidFields },
  }
}

describe('catalogs API', () => {
  it('returns the created catalog on 201 and sends the request as JSON', async () => {
    const recorded: RecordedRequest[] = []
    const outcome = await createCatalog(
      REQUEST,
      routedFetch({ '/api/v1/catalogs': { status: 201, body: CATALOG } }, recorded),
    )
    expect(outcome).toEqual({ ok: true, value: CATALOG })
    expect(recorded[0]?.init?.method).toBe('POST')
    expect(JSON.parse(String(recorded[0]?.init?.body))).toEqual(REQUEST)
  })

  it('reports a taken name as catalog-name-taken at the name field', async () => {
    const outcome = await createCatalog(
      REQUEST,
      routedFetch({ '/api/v1/catalogs': problem('catalog-name-taken', ['name']) }),
    )
    expect(outcome).toMatchObject({
      ok: false,
      rejection: { status: 422, problemType: 'catalog-name-taken', invalidFields: ['name'] },
    })
  })

  it('reports other invalid input as validation-failed with the fields', async () => {
    const outcome = await createCatalog(
      REQUEST,
      routedFetch({ '/api/v1/catalogs': problem('validation-failed', ['primaryMetatypeId']) }),
    )
    expect(outcome).toMatchObject({
      ok: false,
      rejection: { status: 422, problemType: 'validation-failed', invalidFields: ['primaryMetatypeId'] },
    })
  })

  it('reads a catalog and answers undefined for 404', async () => {
    const fetchFn = routedFetch({
      [`/api/v1/catalogs/${CATALOG.id}`]: { body: CATALOG },
      '/api/v1/catalogs/unknown': { status: 404, body: { type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 } },
    })
    expect(await fetchCatalog(CATALOG.id, fetchFn)).toEqual(CATALOG)
    expect(await fetchCatalog('unknown', fetchFn)).toBeUndefined()
  })

  describe('queryCatalog', () => {
    const RESULT: CatalogQueryResult = {
      columns: [{ key: 'name', label: 'Name', sortable: true }],
      rows: [{ entityId: 1, values: { name: 'CRM System' } }],
      page: { page: 0, pageSize: 50, totalElements: 1, totalPages: 1 },
      sort: null,
    }
    const PATH = `/api/v1/catalogs/${CATALOG.id}/query`

    it('sends no parameters for the default view', async () => {
      const recorded: RecordedRequest[] = []
      const outcome = await queryCatalog(CATALOG.id, {}, routedFetch({ [PATH]: { body: RESULT } }, recorded))
      expect(outcome).toEqual({ ok: true, value: RESULT })
      expect(recorded[0]?.path).toBe(PATH)
    })

    it('sends page, page size and sort as query string', async () => {
      const recorded: RecordedRequest[] = []
      const path = `${PATH}?page=2&pageSize=20&sort=name%3Adesc`
      await queryCatalog(
        CATALOG.id,
        { page: 2, pageSize: 20, sort: { column: 'name', direction: 'desc' } },
        routedFetch({ [path]: { body: RESULT } }, recorded),
      )
      expect(recorded[0]?.path).toBe(path)
    })

    it('reports 404 for a catalog that is not visible', async () => {
      const outcome = await queryCatalog(
        'foreign',
        {},
        routedFetch({
          '/api/v1/catalogs/foreign/query': {
            status: 404,
            contentType: 'application/problem+json',
            body: { type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 },
          },
        }),
      )
      expect(outcome).toMatchObject({ ok: false, rejection: { status: 404, problemType: 'not-found' } })
    })

    it('reports an invalid sort with the rejected parameter', async () => {
      const path = `${PATH}?sort=owner%3Aasc`
      const outcome = await queryCatalog(
        CATALOG.id,
        { sort: { column: 'owner', direction: 'asc' } },
        routedFetch({
          [path]: {
            status: 400,
            contentType: 'application/problem+json',
            body: { type: 'urn:oea:problem:validation-failed', title: 'Invalid query', status: 400, invalidFields: ['sort'] },
          },
        }),
      )
      expect(outcome).toMatchObject({ ok: false, rejection: { status: 400, invalidFields: ['sort'] } })
    })
  })
})
