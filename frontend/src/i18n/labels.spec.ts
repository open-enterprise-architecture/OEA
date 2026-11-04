import { afterEach, describe, expect, it, vi } from 'vitest'
import { routedFetch, type RecordedRequest } from '../test/fetch'
import { clearLabels, label, labels, loadLabels, REFRESH_INTERVAL_MS, startLabels } from './labels'

const KEY = 'metatype.5a000000-0000-4000-8000-000000000001.name'

function header(request: RecordedRequest, name: string): string | undefined {
  return (request.init?.headers as Record<string, string> | undefined)?.[name]
}

// US-140 AC1 and AC2; design D7 of us-140-dynamic-labels
describe('label store', () => {
  afterEach(() => {
    clearLabels()
    vi.useRealTimers()
  })

  it('loads the bundle of the locale and falls back to the name', async () => {
    const recorded: RecordedRequest[] = []
    const fetchFn = routedFetch({ '/api/v1/i18n/de': { body: { [KEY]: 'Anwendungskomponente' }, headers: { ETag: 'W/"1"' } } }, recorded)
    await loadLabels('de', fetchFn)
    expect(recorded[0]!.path).toBe('/api/v1/i18n/de')
    expect(header(recorded[0]!, 'If-None-Match')).toBeUndefined()
    expect(label(KEY, 'Application Component')).toBe('Anwendungskomponente')
    expect(label('metatype.unknown.name', 'Zone')).toBe('Zone')
    expect(labels.etag).toBe('W/"1"')
  })

  it('sends the known ETag and keeps the labels on 304', async () => {
    await loadLabels('de', routedFetch({ '/api/v1/i18n/de': { body: { [KEY]: 'Anwendungskomponente' }, headers: { ETag: 'W/"1"' } } }))
    const recorded: RecordedRequest[] = []
    const notModified: typeof fetch = async (input, init) => {
      recorded.push({ path: String(input), init })
      return new Response(null, { status: 304 })
    }
    await loadLabels('de', notModified)
    expect(header(recorded[0]!, 'If-None-Match')).toBe('W/"1"')
    expect(label(KEY, 'Application Component')).toBe('Anwendungskomponente')
  })

  it('replaces the labels when the ETag changed', async () => {
    await loadLabels('de', routedFetch({ '/api/v1/i18n/de': { body: { [KEY]: 'Anwendungskomponente' }, headers: { ETag: 'W/"1"' } } }))
    await loadLabels('de', routedFetch({ '/api/v1/i18n/de': { body: { [KEY]: 'Applikation' }, headers: { ETag: 'W/"2"' } } }))
    expect(label(KEY, 'Application Component')).toBe('Applikation')
    expect(labels.etag).toBe('W/"2"')
  })

  it('checks the bundle again every five minutes', async () => {
    vi.useFakeTimers()
    const recorded: RecordedRequest[] = []
    const fetchFn = routedFetch({ '/api/v1/i18n/de': { body: { [KEY]: 'Anwendungskomponente' }, headers: { ETag: 'W/"1"' } } }, recorded)
    await startLabels('de', fetchFn)
    expect(REFRESH_INTERVAL_MS).toBe(300_000)
    await vi.advanceTimersByTimeAsync(REFRESH_INTERVAL_MS - 1)
    expect(recorded).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(recorded).toHaveLength(2)
    expect(header(recorded[1]!, 'If-None-Match')).toBe('W/"1"')
    clearLabels()
    await vi.advanceTimersByTimeAsync(REFRESH_INTERVAL_MS)
    expect(recorded).toHaveLength(2)
  })
})
