import type { components } from './generated/schema'
import { readRejection } from './problem'
import { currentCsrfToken } from './session'
import type { WriteOutcome } from './organization'

// The lineage model of the instance (change us-065-lineage-model-and-query; SCR-030): which metatypes play data object,
// data flow, carries-data, data input and data output.

export type LineageModel = components['schemas']['LineageModel']
export type LineageModelWrite = components['schemas']['LineageModelWrite']

/** The parts of the lineage model, as the API names them. */
export const LINEAGE_PARTS = ['dataObject', 'dataFlow', 'carriesData', 'dataInput', 'dataOutput'] as const
export type LineagePart = (typeof LINEAGE_PARTS)[number]

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }
const MODEL = '/api/v1/metamodel/lineage-model'



/** The organization model with its version (the `ETag`), read by every entity reader. */
export async function fetchLineageModel(fetchFn: typeof fetch = fetch): Promise<LineageModel> {
  const response = await fetchFn(MODEL, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`${MODEL} not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as LineageModel
}

/** PUT /metamodel/lineage-model with `If-Match`: a part left out is unset; 412 for a stale version, 422 naming the part. */
export async function updateLineageModel(
  body: LineageModelWrite,
  version: number,
  fetchFn: typeof fetch = fetch,
): Promise<WriteOutcome<LineageModel>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(MODEL, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      'Content-Type': 'application/json',
      'If-Match': `"${version}"`,
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (await response.json()) as LineageModel }
}

