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
export type MetamodelConfiguration = components['schemas']['MetamodelConfiguration']
export type MetamodelEditMode = components['schemas']['MetamodelEditMode']
export type MetamodelChange = components['schemas']['MetamodelChange']
export type MetamodelChangePage = components['schemas']['MetamodelChangePage']
export type MetamodelChangeKind = components['schemas']['MetamodelChangeKind']
export type ArchitectureLayer = components['schemas']['ArchitectureLayer']
export type ArchitectureLayerWrite = components['schemas']['ArchitectureLayerWrite']

/** The limits of the architecture layers (change s17-architecture-layers): name length and number of layers. */
export const LAYER_NAME_MAX = 100
export const LAYER_LIMIT = 20

/** The filters of the metamodel changes; an empty filter is left out. */
export interface ChangeFilter {
  kind?: MetamodelChangeKind
  actorRef?: string
  from?: string
  to?: string
  batchId?: string
}

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** GET /metamodel/metatypes: the metatypes of the instance (US-033, design D8). */
export async function fetchMetatypes(fetchFn: typeof fetch = fetch): Promise<MetatypeDetail[]> {
  const response = await fetchFn('/api/v1/metamodel/metatypes', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`metatypes not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as MetatypeDetail[]
}

/** GET /metamodel/metatypes/{id}: one metatype with its mappings, inherited ones included. */
export function fetchMetatype(id: string, fetchFn: typeof fetch = fetch): Promise<MetatypeDetail> {
  return read(`${METAMODEL}/metatypes/${at(id)}`, fetchFn)
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

/** GET /metamodel/starter-packages: the shipped packages with summary (ADR-005, REQ-112). */
export async function fetchStarterPackages(fetchFn: typeof fetch = fetch): Promise<StarterPackage[]> {
  const response = await fetchFn('/api/v1/metamodel/starter-packages', {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new Error(`starter packages not readable (HTTP ${response.status})`)
  }
  return (await response.json()) as StarterPackage[]
}

/** POST /metamodel/starter-packages/{packageId}/import with the CSRF token of the session. */
/** A conflicting element of a refused package import: its type and name, and why it conflicts. */
export interface PackageConflict {
  element: string
  reason: 'other-package' | 'organization' | 'built-in'
}

export async function importStarterPackage(
  packageId: string,
  fetchFn: typeof fetch = fetch,
): Promise<{ ok: true; result: ImportResult } | { ok: false; rejection: Rejection; conflicts?: PackageConflict[] }> {
  const csrfToken = currentCsrfToken()
  const response = await fetchFn(`/api/v1/metamodel/starter-packages/${encodeURIComponent(packageId)}/import`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
  })
  if (response.ok) {
    return { ok: true, result: (await response.json()) as ImportResult }
  }
  if (response.status === 409) {
    // 409 package-conflict with the conflicts in `conflictList` (issue #262 API-3)
    const problem = (await response.clone().json()) as { conflictList?: PackageConflict[] }
    return { ok: false, rejection: await readRejection(response), conflicts: problem.conflictList ?? [] }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** The outcome of a metamodel write: the answer, or the rejection with its problem type. */
export type WriteOutcome<T> = { ok: true; value: T } | { ok: false; rejection: Rejection }

/** The instance configuration (`configId` of its metatypes); extensions of solutions have their own (US-037). */
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

/** GET /metamodel/architecture-layers: the layers of the instance in their order (REQ-053 BR-04). */
export function fetchArchitectureLayers(fetchFn: typeof fetch = fetch): Promise<ArchitectureLayer[]> {
  return read(`${METAMODEL}/architecture-layers`, fetchFn)
}

/** POST /metamodel/architecture-layers: a new organization layer, last or at the position (422 names `name`). */
export function createArchitectureLayer(body: ArchitectureLayerWrite, fetchFn: typeof fetch = fetch) {
  return write<ArchitectureLayer>('POST', `${METAMODEL}/architecture-layers`, body, undefined, fetchFn)
}

/** PUT /metamodel/architecture-layers/{id}: renames and, with `sortOrder`, moves the layer; needs the version. */
export function updateArchitectureLayer(
  id: string,
  body: ArchitectureLayerWrite,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
) {
  return write<ArchitectureLayer>('PUT', `${METAMODEL}/architecture-layers/${at(id)}`, body, version, fetchFn)
}

/** DELETE /metamodel/architecture-layers/{id}: 409 `in-use` with the number of metatypes that have it. */
export function deleteArchitectureLayer(id: string, version: number | undefined, fetchFn: typeof fetch = fetch) {
  return write<void>('DELETE', `${METAMODEL}/architecture-layers/${at(id)}`, undefined, version, fetchFn)
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

/** GET /metamodel/configurations: the instance configuration and the extensions (US-035, US-037). */
export function fetchConfigurations(fetchFn: typeof fetch = fetch): Promise<MetamodelConfiguration[]> {
  return read(`${METAMODEL}/configurations`, fetchFn)
}

/** GET /metamodel/changes: the metamodel changes from the audit trail, newest first (US-034). */
export function fetchChanges(
  filter: ChangeFilter,
  page: number,
  pageSize: number,
  fetchFn: typeof fetch = fetch,
): Promise<MetamodelChangePage> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filter)) {
    if (value) {
      query.set(key, value)
    }
  }
  query.set('page', String(page))
  query.set('pageSize', String(pageSize))
  return read(`${METAMODEL}/changes?${query.toString()}`, fetchFn)
}

/** PUT /metamodel/configurations/{id}: renames a configuration or switches its edit mode (US-035). */
export function updateConfiguration(
  id: string,
  body: components['schemas']['MetamodelConfigurationUpdate'],
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
) {
  return write<MetamodelConfiguration>('PUT', `${METAMODEL}/configurations/${at(id)}`, body, version, fetchFn)
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

/** A decision for an active solution a metamodel change affects (US-154). */
export type SolutionDecision = components['schemas']['SolutionDecision']

/**
 * The decisions as the repeated query parameter `decision=<solutionId>:apply|keep`, or
 * `decision=plateau:<plateauId>:apply|keep` for the solutions of a plateau (US-156).
 */
function decisionQuery(decisions: SolutionDecision[]): string {
  const query = decisions
    .map((entry) => {
      const target = entry.plateauId ? `plateau:${entry.plateauId}` : entry.solutionId
      return `decision=${encodeURIComponent(`${target}:${entry.decision}`)}`
    })
    .join('&')
  return query === '' ? '' : `?${query}`
}

/**
 * DELETE /metamodel/metatypes/{id}/property-mappings/{mappingId}; affected active solutions need a
 * decision each, else 409 `solution-decision-required` lists them (US-154).
 */
export function deleteMapping(
  metatypeId: string,
  mappingId: string,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
  decisions: SolutionDecision[] = [],
) {
  return write<void>(
    'DELETE',
    `${METAMODEL}/metatypes/${at(metatypeId)}/property-mappings/${at(mappingId)}${decisionQuery(decisions)}`,
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

/** PUT /metamodel/metatypes/{id}/parallel-changes (US-044): whether the baseline shows parallel changes. */
export function updateMetatypeParallelChanges(
  metatypeId: string,
  showInBaseline: boolean,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
) {
  return write<MetatypeDetail>('PUT', `${METAMODEL}/metatypes/${at(metatypeId)}/parallel-changes`, { showInBaseline }, version, fetchFn)
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

/** DELETE of an enum value; affected active solutions need a decision each (US-154). */
export function deleteEnumValue(
  propertyId: string,
  valueId: string,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
  decisions: SolutionDecision[] = [],
) {
  return write<void>(
    'DELETE',
    `${METAMODEL}/property-definitions/${at(propertyId)}/enum-values/${at(valueId)}${decisionQuery(decisions)}`,
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

/**
 * A viewpoint: the element and connection metatypes of a view, its notation and the optional
 * notation mapping; a system-defined one comes from a package and is read-only (US-090 to US-092;
 * us-090-viewpoints, us-091-viewpoint-notations).
 */
export type Viewpoint = components['schemas']['ViewpointConfig']
export type ViewpointWrite = components['schemas']['ViewpointWrite']
export type ViewpointNotation = components['schemas']['ViewpointNotation']
export type ViewpointMapping = components['schemas']['ViewpointMapping']
export type NotationElement = components['schemas']['NotationElement']

/** The notations of a viewpoint in the order the dialog offers them. */
export const VIEWPOINT_NOTATIONS: ViewpointNotation[] = ['generic', 'archimate3', 'uml', 'bpmn2']

/** The key prefix of each notation's elements; `generic:` elements fit every notation (REQ-091). */
export const NOTATION_PREFIX: Record<ViewpointNotation, string> = { generic: 'generic:', archimate3: 'archimate:', uml: 'uml:', bpmn2: 'bpmn:' }

/** The size limits of a mapping, in pixels (REQ-091). */
export const MAPPING_SIZE_MIN = 1
export const MAPPING_SIZE_MAX = 2000

const VIEWPOINTS = '/api/v1/admin/viewpoints'

/** GET /admin/viewpoints/notations: the shipped element catalog, read with `entity:read` or `viewpoint:manage`. */
export function fetchNotationElements(fetchFn: typeof fetch = fetch): Promise<NotationElement[]> {
  return read(`${VIEWPOINTS}/notations`, fetchFn)
}

/** Whether the catalog element may be used by a viewpoint of the notation: its own or a generic one. */
export function elementFits(notation: ViewpointNotation, key: string): boolean {
  return key.startsWith(NOTATION_PREFIX.generic) || key.startsWith(NOTATION_PREFIX[notation])
}

/** GET /admin/viewpoints, ordered by name. */
export function fetchViewpoints(fetchFn: typeof fetch = fetch): Promise<Viewpoint[]> {
  return read(VIEWPOINTS, fetchFn)
}

/** POST /admin/viewpoints: 422 names `name`, `metatypeIds`, `connectionTypeIds` or `mappings`. */
export function createViewpoint(viewpoint: ViewpointWrite, fetchFn: typeof fetch = fetch): Promise<WriteOutcome<Viewpoint>> {
  return write('POST', VIEWPOINTS, viewpoint, undefined, fetchFn)
}

/** PUT /admin/viewpoints/{id} with `If-Match`: 412 `stale-version` when changed meanwhile. */
export function updateViewpoint(
  id: string,
  viewpoint: ViewpointWrite,
  version: number | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<WriteOutcome<Viewpoint>> {
  return write('PUT', `${VIEWPOINTS}/${at(id)}`, viewpoint, version, fetchFn)
}

/** DELETE /admin/viewpoints/{id} with `If-Match`. */
export function deleteViewpoint(id: string, version: number | undefined, fetchFn: typeof fetch = fetch): Promise<WriteOutcome<void>> {
  return write('DELETE', `${VIEWPOINTS}/${at(id)}`, undefined, version, fetchFn)
}
