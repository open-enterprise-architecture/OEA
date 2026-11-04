import type { components } from './generated/schema'

export type LabelBundle = components['schemas']['I18nBundle']

/** Result of reading a label bundle: unchanged (304) or a new bundle with its ETag. */
export type BundleResult = { changed: false } | { changed: true; labels: LabelBundle; etag: string | undefined }

/**
 * GET /i18n/{locale}: the translated metamodel labels (REQ-152); with the known ETag the backend
 * answers 304 when nothing changed (ADR-025).
 */
export async function fetchLabels(
  locale: string,
  etag: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<BundleResult> {
  const response = await fetchFn(`/api/v1/i18n/${encodeURIComponent(locale)}`, {
    credentials: 'same-origin',
    // the store keeps the bundle itself; the browser cache must not answer instead of the backend
    cache: 'no-store',
    headers: { Accept: 'application/json', ...(etag ? { 'If-None-Match': etag } : {}) },
  })
  if (response.status === 304) {
    return { changed: false }
  }
  if (!response.ok) {
    throw new Error(`labels: ${response.status}`)
  }
  return { changed: true, labels: (await response.json()) as LabelBundle, etag: response.headers.get('ETag') ?? undefined }
}
