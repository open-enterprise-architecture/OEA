import { describe, expect, it } from 'vitest'
import {
  addWidget,
  createDashboard,
  deleteDashboard,
  fetchDashboard,
  nextFreeRow,
  overlap,
  overlappingIds,
  positionProblem,
  updateDashboard,
  updateLayout,
  type GridPosition,
} from './dashboards'
import { routedFetch, type RecordedRequest } from '../test/fetch'

const at = (col: number, row: number, width: number, height: number): GridPosition => ({ col, row, width, height })

// spec dashboard/widgets "Grid layout" (US-060, REQ-057)
describe('positionProblem', () => {
  it.each([
    [at(1, 1, 1, 1), undefined],
    [at(1, 1, 12, 1), undefined],
    [at(12, 1, 1, 1), undefined],
    [at(5, 1, 8, 1), undefined],
    [at(12, 500, 1, 6), undefined],
    [at(5, 1, 9, 1), 'exceeds-grid'],
    [at(12, 1, 2, 1), 'exceeds-grid'],
    [at(1, 1, 13, 1), 'exceeds-grid'],
    [at(0, 1, 3, 1), 'out-of-range'],
    [at(13, 1, 1, 1), 'out-of-range'],
    [at(1, 1, 0, 1), 'out-of-range'],
    [at(1, 0, 3, 1), 'out-of-range'],
    [at(1, 501, 3, 1), 'out-of-range'],
    [at(1, 1, 3, 0), 'out-of-range'],
    [at(1, 1, 3, 6), undefined],
    [at(1, 1, 3, 7), 'out-of-range'],
  ])('%j gives %s', (position, expected) => {
    expect(positionProblem(position)).toBe(expected)
  })
})

describe('overlap', () => {
  it.each([
    [at(1, 1, 6, 2), at(4, 1, 6, 2), true],
    [at(1, 1, 3, 1), at(4, 1, 9, 3), false],
    [at(1, 1, 3, 2), at(1, 3, 3, 2), false],
    [at(1, 1, 3, 3), at(1, 3, 3, 2), true],
  ])('%j and %j: %s', (a, b, expected) => {
    expect(overlap(a, b)).toBe(expected)
    expect(overlap(b, a)).toBe(expected)
  })

  it('collects the widgets that overlap another one', () => {
    const widgets = [
      { id: 'a', position: at(1, 1, 6, 2) },
      { id: 'b', position: at(4, 1, 6, 2) },
      { id: 'c', position: at(1, 5, 12, 1) },
    ]
    expect([...overlappingIds(widgets)].sort()).toEqual(['a', 'b'])
    expect(overlappingIds([widgets[0]!, widgets[2]!]).size).toBe(0)
  })

  it('places a new widget below all others', () => {
    expect(nextFreeRow([])).toBe(1)
    expect(nextFreeRow([{ position: at(1, 1, 3, 2) }, { position: at(4, 2, 3, 3) }])).toBe(5)
  })
})

describe('dashboard requests', () => {
  function api(recorded: RecordedRequest[], status = 200, body: unknown = {}) {
    return routedFetch(
      {
        '/api/v1/dashboards': { status: 201, body },
        '/api/v1/dashboards/d1': { status, body },
        '/api/v1/dashboards/d1/widgets': { status, body },
        '/api/v1/dashboards/d1/layout': { status, body },
        '/api/v1/views/dashboards/d1': { status, body },
      },
      recorded,
    )
  }

  function header(request: RecordedRequest | undefined, name: string): string | undefined {
    return (request?.init?.headers as Record<string, string> | undefined)?.[name]
  }

  it('sends If-Match with the version on every write', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = api(recorded)
    await updateDashboard('d1', { name: 'N', description: '', scope: 'instance' }, 3, fetchFn)
    await deleteDashboard('d1', 4, fetchFn)
    await addWidget('d1', { type: 'text', title: 'T', position: at(1, 1, 3, 1), config: { content: '' } }, 5, fetchFn)
    await updateLayout('d1', [{ widgetId: 'w', ...at(1, 1, 3, 1) }], 6, fetchFn)
    expect(recorded.map((request) => [request.init?.method, header(request, 'If-Match')])).toEqual([
      ['PUT', '"3"'],
      ['DELETE', '"4"'],
      ['POST', '"5"'],
      ['PUT', '"6"'],
    ])
  })

  it('creates without If-Match and reports a taken name as a rejection', async () => {
    const recorded: RecordedRequest[] = []
    const created = await createDashboard({ name: 'N', description: '', scope: 'personal' }, api(recorded))
    expect(created.ok).toBe(true)
    expect(header(recorded[0], 'If-Match')).toBeUndefined()
    const taken = routedFetch({
      '/api/v1/dashboards': {
        status: 422,
        contentType: 'application/problem+json',
        body: { type: 'urn:oea:problem:dashboard-name-taken', invalidFields: ['name'] },
      },
    })
    const outcome = await createDashboard({ name: 'N', description: '', scope: 'instance' }, taken)
    expect(outcome.ok).toBe(false)
    expect(!outcome.ok && outcome.rejection.problemType).toBe('dashboard-name-taken')
  })

  it('reads a missing dashboard as undefined and a stale write as a 412 rejection', async () => {
    expect(await fetchDashboard('d1', api([], 404))).toBeUndefined()
    const stale = await updateLayout('d1', [], 1, api([], 412, { type: 'urn:oea:problem:stale-version', currentVersion: 2 }))
    expect(!stale.ok && stale.rejection.status).toBe(412)
    expect(!stale.ok && stale.rejection.currentVersion).toBe(2)
  })
})
