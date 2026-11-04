import type { components } from './generated/schema'
import { readRejection, type Rejection } from './problem'
import { currentCsrfToken } from './session'

export type StarterPackage = components['schemas']['StarterPackage']
export type ImportResult = components['schemas']['ImportResult']
export type MetatypeDetail = components['schemas']['MetatypeDetail']
export type PropertyDefinitionDetail = components['schemas']['PropertyDefinitionDetail']
export type PropertyCategory = components['schemas']['PropertyCategory']
export type PropertyMapping = components['schemas']['MetatypePropertyMapping']
export type MetatypeWrite = components['schemas']['MetatypeWrite']
export type PropertyDefinitionWrite = components['schemas']['PropertyDefinitionWrite']
export type PropertyCategoryWrite = components['schemas']['PropertyCategoryWrite']
export type PropertyMappingWrite = components['schemas']['MetatypePropertyMappingWrite']
export type MetamodelLabel = components['schemas']['MetamodelLabel']
export type ReimportSetting = components['schemas']['MetatypeReimportPolicy']['organizationMappings']
export type GlobalReimportSetting = components['schemas']['ReimportPolicy']['organizationMappings']

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** GET /metamodel/metatypes: the metatypes of the instance (US-033, design D8). */
export async function fetchMetatypes(fetchFn: typeof fetch = fetch): Promise<MetatypeDetail[]> {
  const response = await fetchFn('/api/v1/metamodel/metatypes', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`metatypes not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as MetatypeDetail[]
}

/** GET /metamodel/property-definitions: the property definitions with their enum values (US-032). */
export async function fetchPropertyDefinitions(fetchFn: typeof fetch = fetch): Promise<PropertyDefinitionDetail[]> {
  const response = await fetchFn('/api/v1/metamodel/property-definitions', {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new Error(`property definitions not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as PropertyDefinitionDetail[]
}

/** GET /admin/metamodel/starter-packages: the shipped packages with summary (ADR-005, REQ-112). */
export async function fetchStarterPackages(fetchFn: typeof fetch = fetch): Promise<StarterPackage[]> {
  const response = await fetchFn('/api/v1/admin/metamodel/starter-packages', {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new Error(`starter packages not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as StarterPackage[]
}

/** POST /admin/metamodel/starter-packages/{packageId}/import with the CSRF token of the session. */
export async function importStarterPackage(
  packageId: string,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; result: ImportResult } | { ok: false; rejection: Rejection; result?: ImportResult }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`/api/v1/admin/metamodel/starter-packages/${encodeURIComponent(packageId)}/import`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
  })
  if (response.ok) {
    return { ok: true, result: (await response.json()) as ImportResult }
  }
  if (response.status === 409) {
    const result = (await response.json()) as ImportResult
    return { ok: false, rejection: { status: 409, problemType: 'conflict', invalidFields: [] }, result }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** The outcome of a metamodel write: the answer, or the rejection with its problem type. */
export type WriteOutcome<T> = { ok: true; value: T } | { ok: false; rejection: Rejection }

/** The instance configuration, the only one so far (`configId` of a metatype). */
export const INSTANCE_CONFIGURATION = '00000000-0000-0000-0000-000000000001'

async function read<T>(path: string, fetchFn: typeof fetch): Promise<T> {
  const response = await fetchFn(path, { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`${path} not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as T
}

/**
 * A write with the CSRF token and, with a version, `If-Match` (design D2, D4 of
 * scr-030-metamodel-configuration): a stale version is rejected with 412 `stale-version`.
 */
async function write<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body: unknown,
  version: number | undefined,
  fetchFn: typeof fetch,
): Promise<WriteOutcome<T>> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(path, {
    method,
    credentials: 'same-origin',
    headers: {
      ...JSON_HEADERS,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
      ...(version !== undefined ? { 'If-Match': `"${version}"` } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })
  if (!response.ok) {
    return { ok: false, rejection: await readRejection(response) }
  }
  return { ok: true, value: (response.status === 204 ? undefined : await response.json()) as T }
}

const METAMODEL = '/api/v1/metamodel'

function at(id: string): string {
  return encodeURIComponent(id)
}

/** GET /metamodel/property-categories (US-032). */
export function fetchCategories(fetchFn: typeof fetch = fetch): Promise<PropertyCategory[]> {
  return read(`${METAMODEL}/property-categories`, fetchFn)
}

/** GET /metamodel/metatypes/{id}/property-mappings with every dated entry (US-137). */
export function fetchMappingHistory(metatypeId: string, fetchFn: typeof fetch = fetch): Promise<PropertyMapping[]> {
  return read(`${METAMODEL}/metatypes/${at(metatypeId)}/property-mappings?includeHistory=true`, fetchFn)
}

/** GET /metamodel/settings/reimport-policy: the global re-import setting. */
export async function fetchGlobalReimportSetting(fetchFn: typeof fetch = fetch): Promise<GlobalReimportSetting> {
  return (await read<components['schemas']['ReimportPolicy']>(`${METAMODEL}/settings/reimport-policy`, fetchFn))
    .organizationMappings
}

/** GET /metamodel/labels/{locale}: every label key with its texts (US-140). */
export function fetchMetamodelLabels(locale: string, fetchFn: typeof fetch = fetch): Promise<MetamodelLabel[]> {
  return read(`${METAMODEL}/labels/${at(locale)}`, fetchFn)
}

export function createMetatype(body: MetatypeWrite, fetchFn: typeof fetch = fetch) {
  return write<MetatypeDetail>('POST', `${METAMODEL}/metatypes`, body, undefined, fetchFn)
}

export function updateMetatype(id: string, body: MetatypeWrite, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<MetatypeDetail>('PUT', `${METAMODEL}/metatypes/${at(id)}`, body, version, fetchFn)
}

export function deleteMetatype(id: string, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<void>('DELETE', `${METAMODEL}/metatypes/${at(id)}`, undefined, version, fetchFn)
}

export function createMapping(
  metatypeId: string,
  body: PropertyMappingWrite,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
) {
  return write<PropertyMapping>('POST', `${METAMODEL}/metatypes/${at(metatypeId)}/property-mappings`, body, version, fetchFn)
}

export function deleteMapping(metatypeId: string, mappingId: string, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<void>(
    'DELETE',
    `${METAMODEL}/metatypes/${at(metatypeId)}/property-mappings/${at(mappingId)}`,
    undefined,
    version,
    fetchFn,
  )
}

export function updateMetatypeReimportSetting(
  metatypeId: string,
  setting: ReimportSetting,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
) {
  return write<MetatypeDetail>(
    'PUT',
    `${METAMODEL}/metatypes/${at(metatypeId)}/reimport-policy`,
    { organizationMappings: setting },
    version,
    fetchFn,
  )
}

export function updateGlobalReimportSetting(setting: GlobalReimportSetting, fetchFn: typeof fetch = fetch) {
  return write<components['schemas']['ReimportPolicy']>(
    'PUT',
    `${METAMODEL}/settings/reimport-policy`,
    { organizationMappings: setting },
    undefined,
    fetchFn,
  )
}

export function createPropertyDefinition(body: PropertyDefinitionWrite, fetchFn: typeof fetch = fetch) {
  return write<PropertyDefinitionDetail>('POST', `${METAMODEL}/property-definitions`, body, undefined, fetchFn)
}

export function updatePropertyDefinition(
  id: string,
  body: PropertyDefinitionWrite,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
) {
  return write<PropertyDefinitionDetail>('PUT', `${METAMODEL}/property-definitions/${at(id)}`, body, version, fetchFn)
}

export function deletePropertyDefinition(id: string, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<void>('DELETE', `${METAMODEL}/property-definitions/${at(id)}`, undefined, version, fetchFn)
}

export function createEnumValue(propertyId: string, value: string, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<components['schemas']['PropertyEnumValue']>(
    'POST',
    `${METAMODEL}/property-definitions/${at(propertyId)}/enum-values`,
    { value },
    version,
    fetchFn,
  )
}

export function deleteEnumValue(propertyId: string, valueId: string, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<void>(
    'DELETE',
    `${METAMODEL}/property-definitions/${at(propertyId)}/enum-values/${at(valueId)}`,
    undefined,
    version,
    fetchFn,
  )
}

export function createCategory(body: PropertyCategoryWrite, fetchFn: typeof fetch = fetch) {
  return write<PropertyCategory>('POST', `${METAMODEL}/property-categories`, body, undefined, fetchFn)
}

export function updateCategory(id: string, body: PropertyCategoryWrite, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<PropertyCategory>('PUT', `${METAMODEL}/property-categories/${at(id)}`, body, version, fetchFn)
}

export function deleteCategory(id: string, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<void>('DELETE', `${METAMODEL}/property-categories/${at(id)}`, undefined, version, fetchFn)
}

/** PUT /metamodel/labels/{locale}: sets the given keys; `null` removes a translation. */
export function updateMetamodelLabels(
  locale: string,
  labels: Record<string, string | null>,
  fetchFn: typeof fetch = fetch,
) {
  return write<MetamodelLabel[]>('PUT', `${METAMODEL}/labels/${at(locale)}`, { labels }, undefined, fetchFn)
}
