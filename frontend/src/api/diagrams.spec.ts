import { describe, expect, it } from 'vitest'
import { createDiagram, deleteDiagram, fetchDeletedDiagrams, fetchDiagram, fetchDiagrams, restoreDiagram, updateDiagram } from './diagrams'
import { diagram, diagramApi, ME_DIAGRAM_RESTORER } from '../test/diagrams'
import type { RecordedRequest } from '../test/fetch'

// spec diagram/diagrams: the requests of the client (US-074)
describe('the diagram client', () => {
  it('sends the filter, the page and the cursor of a list request, and leaves a blank filter out', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = diagramApi({ me: ME_DIAGRAM_RESTORER, recorded })
    await fetchDiagrams({ q: 'to p', page: 1, pageSize: 50, cursor: 'abc' }, fetchFn)
    await fetchDiagrams({ q: '   ' }, fetchFn)
    await fetchDeletedDiagrams({}, fetchFn)
    expect(recorded.map((request) => request.path)).toEqual(['/api/v1/diagrams?q=to+p&page=1&pageSize=50&cursor=abc', '/api/v1/diagrams', '/api/v1/diagrams/deleted'])
  })

  it('reads a diagram and answers undefined for an unknown one', async () => {
    const fetchFn = diagramApi({ me: ME_DIAGRAM_RESTORER })
    expect((await fetchDiagram(diagram().id, fetchFn))?.name).toBe('Order to Cash')
    expect(await fetchDiagram('a1000000-0000-4000-8000-0000000000ff', fetchFn)).toBeUndefined()
  })

  it('sends If-Match with the version on the writes', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = diagramApi({ me: ME_DIAGRAM_RESTORER, recorded })
    const id = diagram().id
    expect((await updateDiagram(id, { name: 'Order to Cash 2', description: null }, 1, fetchFn)).ok).toBe(true)
    expect((await deleteDiagram(id, 2, fetchFn)).ok).toBe(true)
    expect((await restoreDiagram(id, 3, fetchFn)).ok).toBe(true)
    const matches = recorded.filter((request) => request.init?.method).map((request) => (request.init!.headers as Record<string, string>)['If-Match'])
    expect(matches).toEqual(['"1"', '"2"', '"3"'])
  })

  it('reports a stale version and a taken name as rejections', async () => {
    const fetchFn = diagramApi({ me: ME_DIAGRAM_RESTORER })
    const stale = await updateDiagram(diagram().id, { name: 'Other', description: null }, 7, fetchFn)
    expect(stale.ok).toBe(false)
    expect(!stale.ok && stale.rejection.status).toBe(412)
    const taken = await createDiagram({ name: 'order to cash', description: null, viewpointId: 'b1000000-0000-4000-8000-000000000001' }, fetchFn)
    expect(!taken.ok && taken.rejection.problemType).toBe('diagram-name-taken')
    expect(!taken.ok && taken.rejection.invalidFields).toEqual(['name'])
  })
})
