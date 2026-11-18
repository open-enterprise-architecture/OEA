/**
 * Test and story data for the metamodel configuration SCR-030 (scr-030-metamodel-configuration):
 * a stateful fake of the metamodel API with versions, `If-Match` and the problem types of the
 * contract, seeded with a part of the starter package and one organization metatype.
 */
import type { components } from '../api/generated/schema'

// the fake changes the read-only fields of the contract (versions, re-import setting)
type Mutable<T> = { -readonly [K in keyof T]: T[K] }
type Metatype = Mutable<components['schemas']['MetatypeDetail']>
type Property = Mutable<components['schemas']['PropertyDefinitionDetail']>
type Category = Mutable<components['schemas']['PropertyCategory']>
type Viewpoint = components['schemas']['ViewpointConfig']
type Mapping = components['schemas']['MetatypePropertyMapping']
type Scope = components['schemas']['MetamodelScope']
type Configuration = Mutable<components['schemas']['MetamodelConfiguration']>
type Change = components['schemas']['MetamodelChange']
type Layer = Mutable<components['schemas']['ArchitectureLayer']>

function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 7, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-metamodel',
  }
}

export const ME_METAMODEL_ADMIN = me('Kurt', ['portal:read', 'entity:read', 'metamodel:write'])
export const ME_READER = me('Franz', ['portal:read', 'entity:read'])
export const ME_NO_ACCESS = me('Max', ['portal:read'])
export const ME_ARCHITECT = me('Kurt', ['portal:read', 'entity:read', 'viewpoint:manage', 'solution:write'])
export const ME_SECURITY_ADMIN = me('Max', ['visibility:configure', 'visibility-scope:assign', 'role:assign', 'person:manage'])

export const IDS = {
  general: 'ca000000-0000-4000-8000-000000000001',
  status: 'fd000000-0000-4000-8000-000000000001',
  planned: 'e0000000-0000-4000-8000-000000000001',
  active: 'e0000000-0000-4000-8000-000000000002',
  owner: 'fd000000-0000-4000-8000-000000000002',
  component: 'a0000000-0000-4000-8000-000000000001',
  service: 'a0000000-0000-4000-8000-000000000002',
  zone: 'a0000000-0000-4000-8000-000000000003',
  cloudZone: 'a0000000-0000-4000-8000-000000000004',
  dataFlow: 'a0000000-0000-4000-8000-000000000005',
  layerBusiness: 'f1000000-0000-4000-8000-000000000001',
  layerApplication: 'f1000000-0000-4000-8000-000000000002',
  layerTechnology: 'f1000000-0000-4000-8000-000000000003',
  layerStrategy: 'f1000000-0000-4000-8000-000000000004',
}

const STARTER = { packageId: 'oea-starter-togaf-classic', version: '1.1.0' }
const STARTER_WITH_VIEWPOINTS = { packageId: 'oea-starter-togaf-classic', version: '1.2.0' }
const STARTER_WITH_LAYERS = { packageId: 'oea-starter-togaf-classic', version: '1.4.0' }

type NotationElement = components['schemas']['NotationElement']

function catalogEntry(key: string, label: string, kind: NotationElement['kind'], width = 120, height = 55): NotationElement {
  const prefix = key.split(':')[0]!
  const notation = ({ archimate: 'archimate3', uml: 'uml', bpmn: 'bpmn2', generic: 'generic' } as const)[prefix as 'archimate']
  return { key, notation, label, kind, defaultWidth: width, defaultHeight: height }
}

/** An excerpt of the shipped element catalog (design D2 of us-091-viewpoint-notations). */
export const NOTATION_ELEMENTS: NotationElement[] = [
  catalogEntry('archimate:ApplicationComponent', 'Application Component', 'element'),
  catalogEntry('archimate:ApplicationService', 'Application Service', 'element'),
  catalogEntry('archimate:BusinessActor', 'Business Actor', 'element'),
  catalogEntry('archimate:DataObject', 'Data Object', 'element'),
  catalogEntry('archimate:Flow', 'Flow', 'relationship', 80, 20),
  catalogEntry('archimate:Serving', 'Serving', 'relationship', 80, 20),
  catalogEntry('bpmn:SequenceFlow', 'Sequence Flow', 'relationship', 80, 20),
  catalogEntry('bpmn:Task', 'Task', 'element', 100, 80),
  catalogEntry('generic:Arrow', 'Arrow', 'relationship', 80, 20),
  catalogEntry('generic:Box', 'Box', 'element'),
  catalogEntry('uml:Association', 'Association', 'relationship', 80, 20),
  catalogEntry('uml:Component', 'Component', 'element'),
]

interface Entry {
  id: string
  metatypeId: string
  propertyDefId: string
  necessity: Mapping['necessity']
  validFrom: string
  scope: Scope
}

interface Stored {
  metatypes: (Omit<Metatype, 'propertyMappings' | 'effectiveReimportPolicy'> & { entities: number })[]
  properties: Property[]
  categories: Category[]
  /** The architecture layers in their order, without the number of metatypes (change s17-architecture-layers). */
  layers: Omit<Layer, 'metatypeCount'>[]
  entries: Entry[]
  globalReimport: 'keep' | 'remove'
  /** The instance configuration with its edit mode (US-035). */
  configuration: Configuration
  /** The metamodel changes of the audit trail, newest first (US-034). */
  changes: Change[]
  labels: Record<string, Record<string, { packageText?: string; organizationText?: string }>>
  /** The viewpoints of the instance (us-090-viewpoints). */
  viewpoints: Viewpoint[]
}

export interface MetamodelApiOptions {
  me?: unknown
  /** Entities without a value that a new mandatory mapping marks. */
  unmarkedEntities?: number
  /** Answers every request after the given path with an error, for failure states. */
  failListing?: boolean
  /** The instance metamodel is locked (`import-only`, US-035). */
  locked?: boolean
  /** The property Owner is role-restricted (us-124-property-visibility). */
  restrictedOwner?: boolean
  /** Every viewpoint write with a mapping is rejected with 422 naming `mappings` (us-091-viewpoint-notations). */
  rejectMappings?: boolean
  /** The instance has no system-defined viewpoints (no package with viewpoints installed). */
  noSystemViewpoints?: boolean
}

/** A request the fake received, with the headers that matter for the metamodel writes. */
export interface MetamodelRequest {
  method: string
  path: string
  ifMatch?: string
  csrf?: string
  body?: unknown
}

export interface MetamodelApi {
  fetch: typeof fetch
  requests: MetamodelRequest[]
  state: Stored
  /** Another administrator changes a metatype: new abbreviation, version + 1. */
  changeElsewhere(metatypeId: string, abbreviation: string): void
  /** Another administrator switches the edit mode of the instance: version + 1. */
  switchElsewhere(editMode: Configuration['editMode']): void
}

function seed(): Stored {
  const today = new Date().toISOString().slice(0, 10)
  const future = `${Number(today.slice(0, 4)) + 1}-01-01`
  const general: Category = { id: IDS.general, name: 'General', sortOrder: 0, scope: 'imported', version: 1 }
  return {
    categories: [general],
    layers: [
      { id: IDS.layerBusiness, name: 'Business', sortOrder: 0, scope: 'imported', sourcePackage: STARTER_WITH_LAYERS, version: 1 },
      { id: IDS.layerApplication, name: 'Application', sortOrder: 1, scope: 'imported', sourcePackage: STARTER_WITH_LAYERS, version: 1 },
      { id: IDS.layerTechnology, name: 'Technology', sortOrder: 2, scope: 'imported', sourcePackage: STARTER_WITH_LAYERS, version: 1 },
      { id: IDS.layerStrategy, name: 'Strategy', sortOrder: 3, scope: 'organization', sourcePackage: null, version: 1 },
    ],
    viewpoints: [
      {
        id: 'b0000000-0000-4000-8000-000000000001',
        name: 'Application Landscape',
        description: 'Applications and the data flows between them.',
        notation: 'archimate3',
        viewpointType: 'user-defined',
        sourcePackage: null,
        mappings: [{ metatypeId: IDS.component, notationElement: 'archimate:ApplicationComponent', defaultWidth: 140, defaultHeight: 60, visualHint: '#1a2b3c' }],
        effectiveMappings: [
          { metatypeId: IDS.component, notationElement: 'archimate:ApplicationComponent', defaultWidth: 140, defaultHeight: 60, visualHint: '#1a2b3c', fallback: false },
          { metatypeId: IDS.service, notationElement: 'generic:Box', defaultWidth: 120, defaultHeight: 55, visualHint: null, fallback: true },
          { metatypeId: IDS.dataFlow, notationElement: 'generic:Arrow', defaultWidth: 80, defaultHeight: 20, visualHint: null, fallback: true },
        ],
        allowedMetatypes: [
          { id: IDS.component, name: 'Application Component', abbreviation: 'AC' },
          { id: IDS.service, name: 'Application Service', abbreviation: 'AS' },
        ],
        allowedRelations: [{ id: IDS.dataFlow, name: 'Data Flow', abbreviation: 'DF' }],
        version: 1,
        createdAt: '2026-11-17T09:00:00Z',
        createdBy: { id: 1, name: 'Kurt' },
      },
      {
        id: 'b0000000-0000-4000-8000-000000000002',
        name: 'Technology Landscape',
        description: 'Technology components and services.',
        notation: 'archimate3',
        viewpointType: 'system-defined',
        sourcePackage: STARTER_WITH_VIEWPOINTS,
        mappings: [{ metatypeId: IDS.zone, notationElement: 'archimate:ApplicationService', defaultWidth: null, defaultHeight: null, visualHint: null }],
        effectiveMappings: [{ metatypeId: IDS.zone, notationElement: 'archimate:ApplicationService', defaultWidth: 120, defaultHeight: 55, visualHint: null, fallback: false }],
        allowedMetatypes: [{ id: IDS.zone, name: 'Network Zone', abbreviation: 'NZ' }],
        allowedRelations: [],
        version: 1,
        createdAt: '2026-11-17T09:00:00Z',
        createdBy: { id: 1, name: 'Kurt' },
      },
    ],
    properties: [
      {
        id: IDS.status,
        name: 'Status',
        dataType: 'enum',
        category: general,
        scope: 'imported',
        sourcePackage: STARTER,
        sqlColumnName: 'status',
        version: 1,
        enumValues: [
          { id: IDS.planned, value: 'planned', sortOrder: 0 },
          { id: IDS.active, value: 'active', sortOrder: 1 },
        ],
      },
      { id: IDS.owner, name: 'Owner', dataType: 'string', scope: 'organization', sqlColumnName: 'owner', version: 1, maxLength: 100, enumValues: [] },
    ],
    metatypes: [
      { id: IDS.component, name: 'Application Component', abbreviation: 'AC', sortOrder: 1, scope: 'imported', sourcePackage: STARTER, isConnection: false, reimportPolicy: 'inherit', version: 1, entities: 12, connectionConstraints: [], architectureLayerId: IDS.layerApplication },
      { id: IDS.service, name: 'Application Service', abbreviation: 'AS', sortOrder: 2, scope: 'imported', sourcePackage: STARTER, isConnection: false, reimportPolicy: 'inherit', version: 1, entities: 0, connectionConstraints: [], architectureLayerId: IDS.layerApplication },
      { id: IDS.zone, name: 'Network Zone', abbreviation: 'NZ', sortOrder: 10, scope: 'organization', isConnection: false, reimportPolicy: 'inherit', version: 3, entities: 5, connectionConstraints: [] },
      { id: IDS.cloudZone, name: 'Cloud Zone', abbreviation: 'CZ', sortOrder: 11, scope: 'organization', isConnection: false, baseMetatypeId: IDS.zone, reimportPolicy: 'inherit', version: 1, entities: 0, connectionConstraints: [] },
      {
        id: IDS.dataFlow,
        name: 'Data Flow',
        abbreviation: 'DF',
        sortOrder: 20,
        scope: 'organization',
        isConnection: true,
        reimportPolicy: 'inherit',
        version: 1,
        entities: 0,
        connectionConstraints: [
          { direction: 'source', allowedMetatype: { id: IDS.component, name: 'Application Component', abbreviation: 'AC' } },
          { direction: 'target', allowedMetatype: { id: IDS.component, name: 'Application Component', abbreviation: 'AC' } },
        ],
      },
    ],
    entries: [
      { id: 'b0000000-0000-4000-8000-000000000001', metatypeId: IDS.component, propertyDefId: IDS.status, necessity: 'optional', validFrom: '2026-01-01', scope: 'imported' },
      { id: 'b0000000-0000-4000-8000-000000000002', metatypeId: IDS.component, propertyDefId: IDS.status, necessity: 'mandatory', validFrom: future, scope: 'organization' },
      { id: 'b0000000-0000-4000-8000-000000000003', metatypeId: IDS.zone, propertyDefId: IDS.owner, necessity: 'warning', validFrom: '2026-02-01', scope: 'organization' },
    ],
    globalReimport: 'keep',
    configuration: {
      id: '00000000-0000-0000-0000-000000000001',
      scope: 'instance',
      parentId: null,
      solutionId: null,
      name: 'Instance metamodel',
      editMode: 'gui-and-import',
      version: 1,
      lastModifiedAt: '2026-11-01T08:00:00Z',
      metatypeCount: 5,
    },
    changes: seedChanges(),
    labels: { de: { [`metatype.${IDS.component}.name`]: { packageText: 'Anwendungskomponente' } } },
  }
}

const KURT = { actorType: 'person' as const, actorRef: '7', actorName: 'Kurt Meier' }
const LUKAS = { actorType: 'person' as const, actorRef: '8', actorName: 'Lukas Mathis' }
export const IMPORT_BATCH = 'c0000000-0000-4000-8000-000000000001'

/** Changes of two administrators, an import batch and a dated mapping that took effect. */
function seedChanges(): Change[] {
  const change = (id: number, minutes: number, eventType: string, kind: Change['kind'], actor: Change['actor'], elementName: string | null, extra: Partial<Change> = {}): Change => ({
    id,
    occurredAt: new Date(Date.UTC(2026, 10, 5, 9, minutes)).toISOString(),
    eventType,
    kind,
    actor,
    targetType: 'metatype',
    targetId: `a0000000-0000-4000-8000-${String(id).padStart(12, '0')}`,
    elementName,
    batchId: null,
    details: {},
    ...extra,
  })
  const batch = { batchId: IMPORT_BATCH }
  return [
    change(7, 50, 'metamodel.mapping-effective', 'mapping', { actorType: 'system', actorRef: null, actorName: null }, 'Application Component'),
    change(6, 40, 'metamodel.metatype-deleted', 'metatype', LUKAS, 'Legacy Zone'),
    change(5, 30, 'metamodel.metatype-updated', 'metatype', KURT, 'Network Zone', { details: { changedFields: ['abbreviation'] } }),
    change(4, 20, 'metamodel.metatype-created', 'metatype', KURT, 'Network Zone'),
    change(3, 10, 'metamodel.package-imported', 'package', KURT, 'oea-starter-togaf-classic', {
      ...batch,
      targetType: 'metamodel-package',
      details: { packageId: 'oea-starter-togaf-classic', version: '1.1.0', batchId: IMPORT_BATCH },
    }),
    change(2, 9, 'metamodel.metatype-imported', 'package', KURT, 'Application Service', batch),
    change(1, 8, 'metamodel.metatype-imported', 'package', KURT, 'Application Component', batch),
    change(8, 5, 'metamodel.layer-created', 'layer', KURT, 'Strategy', { targetType: 'architecture-layer', targetId: IDS.layerStrategy }),
  ]
}

function problem(status: number, type: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ type: `urn:oea:problem:${type}`, title: type, status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function json(body: unknown, status = 200, etag?: number): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...(etag !== undefined ? { ETag: `"${etag}"` } : {}) },
  })
}

let counter = 0
function newId(): string {
  counter += 1
  return `d0000000-0000-4000-8000-${String(counter).padStart(12, '0')}`
}

/** The fake metamodel API; `state` can be inspected and changed by a test. */
export function metamodelApi(options: MetamodelApiOptions = {}): MetamodelApi {
  const state = seed()
  if (options.restrictedOwner) {
    state.properties.find((property) => property.id === IDS.owner)!.visibility = {
      visibilityMode: 'role-restricted',
      readRoles: ['auditor', 'enterprise-architect'],
      writeRoles: ['enterprise-architect'],
      scopingConnectionType: null,
    }
  }
  if (options.locked) {
    state.configuration.editMode = 'import-only'
  }
  if (options.noSystemViewpoints) {
    state.viewpoints = state.viewpoints.filter((viewpoint) => viewpoint.viewpointType !== 'system-defined')
  }
  const requests: MetamodelRequest[] = []
  const today = new Date().toISOString().slice(0, 10)

  const chain = (id: string): string[] => {
    const ids: string[] = []
    let current: string | null | undefined = id
    while (current && !ids.includes(current)) {
      ids.push(current)
      current = state.metatypes.find((metatype) => metatype.id === current)?.baseMetatypeId
    }
    return ids
  }
  const stateOf = (entry: Entry): Mapping['state'] => {
    if (entry.validFrom > today) {
      return 'future'
    }
    const latest = state.entries
      .filter((other) => other.metatypeId === entry.metatypeId && other.propertyDefId === entry.propertyDefId && other.validFrom <= today)
      .map((other) => other.validFrom)
      .sort()
      .at(-1)
    return latest === entry.validFrom ? 'active' : 'past'
  }
  const mappings = (id: string, history: boolean): Mapping[] =>
    chain(id)
      .reverse()
      .flatMap((owner) =>
        state.entries
          .filter((entry) => entry.metatypeId === owner && (history || stateOf(entry) === 'active'))
          .map((entry) => {
            const base = state.metatypes.find((metatype) => metatype.id === owner)!
            return {
              id: entry.id,
              propertyDefinition: state.properties.find((property) => property.id === entry.propertyDefId)!,
              necessity: entry.necessity,
              validFrom: entry.validFrom,
              sortOrder: 0,
              scope: entry.scope,
              state: stateOf(entry),
              inheritedFrom: owner === id ? null : { id: base.id, name: base.name, abbreviation: base.abbreviation },
            }
          }),
      )
  const effective = (id: string): 'keep' | 'remove' => {
    for (const owner of chain(id)) {
      const policy = state.metatypes.find((metatype) => metatype.id === owner)?.reimportPolicy
      if (policy === 'keep' || policy === 'remove') {
        return policy
      }
    }
    return state.globalReimport
  }
  const constraints = (body: Record<string, unknown> | undefined) =>
    (['source', 'target'] as const).flatMap((direction) =>
      ((body?.[direction === 'source' ? 'allowedSourceMetatypeIds' : 'allowedTargetMetatypeIds'] ?? []) as string[]).map((id) => {
        const allowed = state.metatypes.find((metatype) => metatype.id === id)
        return { direction, allowedMetatype: { id, name: allowed?.name ?? id, abbreviation: allowed?.abbreviation ?? '' } }
      }),
    )
  const detail = (id: string): Metatype => {
    const { entities: _entities, ...metatype } = state.metatypes.find((candidate) => candidate.id === id)!
    const layer = state.layers.find((candidate) => candidate.id === metatype.architectureLayerId)
    return {
      ...metatype,
      architectureLayerId: layer?.id ?? null,
      architectureLayerName: layer?.name ?? null,
      propertyMappings: mappings(id, false),
      effectiveReimportPolicy: effective(id),
    }
  }
  // the layers in their order with the number of metatypes that have each
  const layerList = (): Layer[] =>
    [...state.layers]
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((layer, index) => ({ ...layer, sortOrder: index, metatypeCount: state.metatypes.filter((metatype) => metatype.architectureLayerId === layer.id).length }))
  const knownLayer = (layerId: unknown): boolean => layerId === undefined || layerId === null || state.layers.some((layer) => layer.id === layerId)
  const unknownLayer = () => problem(422, 'validation-failed', { invalidFields: ['architectureLayerId'], detail: 'The architecture layer does not exist.' })
  const placeLayer = (layer: Omit<Layer, 'metatypeCount'>, position: unknown) => {
    const others = layerList().filter((candidate) => candidate.id !== layer.id)
    others.splice(Math.min(Number(position), others.length), 0, { ...layer, metatypeCount: 0 })
    state.layers = others.map(({ metatypeCount: _count, ...rest }, index) => ({ ...rest, sortOrder: index }))
  }
  const labelList = (locale: string) => {
    const own = state.labels[locale] ?? {}
    const entry = (key: string, elementType: string, name: string) => ({
      key,
      elementType,
      name,
      packageText: own[key]?.packageText ?? null,
      organizationText: own[key]?.organizationText ?? null,
    })
    return [
      ...state.metatypes.map((metatype) => entry(`metatype.${metatype.id}.name`, 'metatype', metatype.name)),
      ...state.properties.map((property) => entry(`property-def.${property.id}.label`, 'property-def', property.name)),
      ...state.properties.flatMap((property) =>
        (property.enumValues ?? []).map((value) => entry(`enum-value.${value.id}.label`, 'enum-value', value.value)),
      ),
      ...state.categories.map((category) => entry(`property-category.${category.id}.name`, 'property-category', category.name)),
      ...state.layers.map((layer) => entry(`architecture-layer.${layer.id}.name`, 'architecture-layer', layer.name)),
    ]
  }
  const stale = (element: { version?: number }, ifMatch: string | undefined): Response | undefined =>
    ifMatch !== undefined && ifMatch !== '*' && ifMatch !== `"${element.version}"`
      ? problem(412, 'stale-version', { currentVersion: element.version })
      : undefined

  const fetchFn: typeof fetch = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://oea.test')
    const method = init?.method ?? 'GET'
    const headers = new Headers(init?.headers)
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : undefined
    const ifMatch = headers.get('If-Match') ?? undefined
    requests.push({ method, path: url.pathname + url.search, ifMatch, csrf: headers.get('X-XSRF-TOKEN') ?? undefined, body })
    const path = url.pathname.replace(/^\/api\/v1/, '')
    const parts = path.split('/').filter(Boolean)

    if (path === '/me') {
      return json(options.me ?? ME_METAMODEL_ADMIN)
    }
    if (parts[0] === 'i18n') {
      const own = state.labels[parts[1]!] ?? {}
      const bundle = Object.fromEntries(
        labelList(parts[1]!).map((entry) => [entry.key, own[entry.key]?.organizationText ?? own[entry.key]?.packageText ?? entry.name]),
      )
      return json(bundle)
    }
    // the viewpoints (us-090-viewpoints): name unique case-insensitively, at least one element metatype
    if (parts[0] === 'admin' && parts[1] === 'viewpoints') {
      const viewpointId = parts[2]
      const summary = (id: string) => {
        const found = state.metatypes.find((metatype) => metatype.id === id)!
        return { id, name: found.name, abbreviation: found.abbreviation ?? '' }
      }
      // the notation mapping of a write (US-091 AC2): allowed types only, one element per type, of the notation or generic, of the shape of the type
      const checkMappings = () => {
        if (options.rejectMappings && ((body?.mappings ?? []) as unknown[]).length > 0) {
          return problem(422, 'validation-failed', { invalidFields: ['mappings'], detail: 'The mapping does not fit the notation.' })
        }
        const notation = String(body?.notation ?? 'generic')
        const allowed = [...((body?.metatypeIds ?? []) as string[]), ...((body?.connectionTypeIds ?? []) as string[])]
        const seen = new Set<string>()
        for (const mapping of (body?.mappings ?? []) as { metatypeId: string; notationElement: string }[]) {
          const element = NOTATION_ELEMENTS.find((candidate) => candidate.key === mapping.notationElement)
          const connection = state.metatypes.find((metatype) => metatype.id === mapping.metatypeId)?.isConnection
          const fits = element && (element.notation === 'generic' || element.notation === notation) && (element.kind === 'relationship') === Boolean(connection)
          if (!allowed.includes(mapping.metatypeId) || seen.has(mapping.metatypeId) || !fits) {
            return problem(422, 'validation-failed', { invalidFields: ['mappings'], detail: `The mapping of ${mapping.notationElement} does not fit.` })
          }
          seen.add(mapping.metatypeId)
        }
        return undefined
      }
      const validate = (except?: string) => {
        const name = String(body?.name ?? '').trim()
        if (!name) return problem(422, 'validation-failed', { invalidFields: ['name'], detail: 'The name must not be blank.' })
        if (state.viewpoints.some((other) => other.id !== except && other.name.toLowerCase() === name.toLowerCase())) {
          return problem(422, 'validation-failed', { invalidFields: ['name'], detail: `Name '${name}' is already taken` })
        }
        if (((body?.metatypeIds ?? []) as string[]).length === 0) {
          return problem(422, 'validation-failed', { invalidFields: ['metatypeIds'], detail: 'A viewpoint needs at least one metatype.' })
        }
        return checkMappings()
      }
      const fields = () => {
        const mappings = ((body?.mappings ?? []) as { metatypeId: string; notationElement: string; defaultWidth?: number | null; defaultHeight?: number | null; visualHint?: string | null }[]).map(
          (mapping) => ({ defaultWidth: null, defaultHeight: null, visualHint: null, ...mapping }),
        )
        const allowed = [...((body?.metatypeIds ?? []) as string[]), ...((body?.connectionTypeIds ?? []) as string[])]
        return {
          name: String(body?.name).trim(),
          description: (body?.description as string | null | undefined) ?? null,
          notation: (body?.notation ?? 'generic') as Viewpoint['notation'],
          mappings,
          effectiveMappings: allowed.map((id) => {
            const mapped = mappings.find((mapping) => mapping.metatypeId === id)
            const connection = state.metatypes.find((metatype) => metatype.id === id)?.isConnection
            const element = NOTATION_ELEMENTS.find((candidate) => candidate.key === (mapped?.notationElement ?? (connection ? 'generic:Arrow' : 'generic:Box')))!
            return {
              metatypeId: id,
              notationElement: element.key,
              defaultWidth: mapped?.defaultWidth ?? element.defaultWidth,
              defaultHeight: mapped?.defaultHeight ?? element.defaultHeight,
              visualHint: mapped?.visualHint ?? null,
              fallback: !mapped,
            }
          }),
          allowedMetatypes: ((body?.metatypeIds ?? []) as string[]).map(summary),
          allowedRelations: ((body?.connectionTypeIds ?? []) as string[]).map(summary),
        }
      }
      if (viewpointId === 'notations') {
        return json([...NOTATION_ELEMENTS])
      }
      if (!viewpointId) {
        if (method === 'GET') return json([...state.viewpoints].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())))
        const rejected = validate()
        if (rejected) return rejected
        const created = { id: newId(), viewpointType: 'user-defined' as const, sourcePackage: null, ...fields(), version: 1, createdAt: '2026-11-17T10:00:00Z', createdBy: { id: 1, name: 'Kurt' } }
        state.viewpoints.push(created)
        return json(created, 201, 1)
      }
      const stored = state.viewpoints.find((viewpoint) => viewpoint.id === viewpointId)
      if (!stored) return problem(404, 'not-found')
      if (method === 'GET') return json(stored, 200, stored.version)
      if (ifMatch !== `"${stored.version}"`) return problem(412, 'stale-version', { currentVersion: stored.version })
      if (stored.viewpointType === 'system-defined') {
        return problem(422, 'read-only-viewpoint', { detail: 'System-defined viewpoints are read-only' })
      }
      if (method === 'DELETE') {
        state.viewpoints.splice(state.viewpoints.indexOf(stored), 1)
        return json(null, 204)
      }
      const rejected = validate(stored.id)
      if (rejected) return rejected
      Object.assign(stored, fields(), { version: stored.version + 1 })
      return json(stored, 200, stored.version)
    }
    if (parts[0] !== 'metamodel') {
      return problem(404, 'not-found')
    }
    if (options.failListing && method === 'GET') {
      return problem(500, 'internal')
    }
    const [, kind, id, sub, subId] = parts

    if (kind === 'changes') {
      const filtered = state.changes.filter(
        (change) =>
          (!url.searchParams.get('kind') || change.kind === url.searchParams.get('kind')) &&
          (!url.searchParams.get('actorRef') || change.actor.actorRef === url.searchParams.get('actorRef')) &&
          (!url.searchParams.get('batchId') || change.batchId === url.searchParams.get('batchId')),
      )
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('pageSize') ?? 50)
      return json({
        items: filtered.slice(page * size, (page + 1) * size),
        page: { page, pageSize: size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) },
        actors: [KURT, LUKAS, { actorType: 'system', actorRef: null, actorName: null }],
      })
    }
    if (kind === 'configurations') {
      if (method === 'GET') {
        return json([state.configuration])
      }
      if (id !== state.configuration.id) {
        return problem(404, 'not-found')
      }
      const rejected = stale(state.configuration, ifMatch)
      if (rejected) {
        return rejected
      }
      Object.assign(state.configuration, {
        name: String(body?.name),
        editMode: body?.editMode as Configuration['editMode'],
        version: state.configuration.version + 1,
      })
      return json(state.configuration, 200, state.configuration.version)
    }
    // in import-only every content write is rejected; the re-import settings and the setting of
    // parallel changes stay (US-035, US-044)
    const settings = sub === 'reimport-policy' || sub === 'parallel-changes' || kind === 'settings'
    if (state.configuration.editMode === 'import-only' && method !== 'GET' && !settings) {
      return problem(403, 'metamodel-locked')
    }

    if (kind === 'metatypes') {
      const stored = state.metatypes.find((metatype) => metatype.id === id)
      if (!id) {
        if (method === 'GET') {
          return json(state.metatypes.map((metatype) => detail(metatype.id)))
        }
        const name = String(body?.name ?? '')
        if (state.metatypes.some((metatype) => metatype.name.toLowerCase() === name.toLowerCase())) {
          return problem(409, 'name-taken', { invalidFields: ['name'] })
        }
        if (!knownLayer(body?.architectureLayerId)) {
          return unknownLayer()
        }
        const created = {
          id: newId(),
          name,
          architectureLayerId: (body?.architectureLayerId as string | null | undefined) ?? null,
          abbreviation: String(body?.abbreviation ?? ''),
          sortOrder: Number(body?.sortOrder ?? 0),
          scope: 'organization' as Scope,
          isConnection: Boolean(body?.isConnection),
          isComposition: Boolean(body?.isConnection) && Boolean(body?.isComposition),
          baseMetatypeId: (body?.baseMetatypeId as string | null) ?? null,
          reimportPolicy: 'inherit' as const,
          version: 1,
          entities: 0,
          connectionConstraints: constraints(body),
        }
        state.metatypes.push(created)
        return json(detail(created.id), 201, 1)
      }
      if (!stored) {
        return problem(404, 'not-found')
      }
      if (!sub) {
        if (method === 'GET') {
          return json(detail(stored.id), 200, stored.version)
        }
        const rejected = stale(stored, ifMatch)
        if (rejected) {
          return rejected
        }
        if (stored.scope !== 'organization') {
          return problem(409, 'read-only-metatype')
        }
        if (method === 'DELETE') {
          if (stored.entities > 0) {
            return problem(409, 'in-use', { count: stored.entities })
          }
          state.metatypes = state.metatypes.filter((metatype) => metatype.id !== stored.id)
          return json(undefined, 204)
        }
        if (!knownLayer(body?.architectureLayerId)) {
          return unknownLayer()
        }
        Object.assign(stored, {
          name: String(body?.name),
          architectureLayerId: (body?.architectureLayerId as string | null | undefined) ?? null,
          abbreviation: String(body?.abbreviation ?? ''),
          sortOrder: Number(body?.sortOrder ?? 0),
          isComposition: stored.isConnection === true && Boolean(body?.isComposition),
          baseMetatypeId: (body?.baseMetatypeId as string | null) ?? null,
          connectionConstraints: constraints(body),
          version: (stored.version ?? 1) + 1,
        })
        return json(detail(stored.id), 200, stored.version)
      }
      if (sub === 'property-mappings' && method === 'GET') {
        return json(mappings(stored.id, url.searchParams.get('includeHistory') === 'true'))
      }
      const rejected = stale(stored, ifMatch)
      if (rejected) {
        return rejected
      }
      if (sub === 'parallel-changes') {
        stored.showParallelChanges = Boolean(body?.showInBaseline)
        stored.version = (stored.version ?? 1) + 1
        return json(detail(stored.id), 200, stored.version)
      }
      if (sub === 'reimport-policy') {
        stored.reimportPolicy = body?.organizationMappings as 'inherit' | 'keep' | 'remove'
        stored.version = (stored.version ?? 1) + 1
        return json(detail(stored.id), 200, stored.version)
      }
      if (sub === 'property-mappings' && method === 'POST') {
        const propertyDefId = String(body?.propertyDefId)
        const validFrom = String(body?.validFrom ?? today)
        if (state.entries.some((entry) => entry.metatypeId === stored.id && entry.propertyDefId === propertyDefId && entry.validFrom === validFrom)) {
          return problem(409, 'conflict')
        }
        const entry: Entry = { id: newId(), metatypeId: stored.id, propertyDefId, necessity: body?.necessity as Entry['necessity'], validFrom, scope: 'organization' }
        state.entries.push(entry)
        stored.version = (stored.version ?? 1) + 1
        const marked = entry.necessity === 'mandatory' && validFrom <= today ? (options.unmarkedEntities ?? 0) : 0
        return json({ ...mappings(stored.id, true).find((mapping) => mapping.id === entry.id), markedEntityCount: marked }, 201)
      }
      if (sub === 'property-mappings' && method === 'DELETE') {
        state.entries = state.entries.filter((entry) => entry.id !== subId)
        stored.version = (stored.version ?? 1) + 1
        return json(undefined, 204)
      }
    }

    if (kind === 'architecture-layers') {
      const stored = state.layers.find((layer) => layer.id === id)
      if (!id) {
        if (method === 'GET') {
          return json(layerList())
        }
        const name = String(body?.name ?? '').trim()
        if (name === '' || name.length > 100) {
          return problem(422, 'validation-failed', { invalidFields: ['name'] })
        }
        if (state.layers.some((layer) => layer.name.toLowerCase() === name.toLowerCase())) {
          return problem(409, 'name-taken', { invalidFields: ['name'] })
        }
        if (state.layers.length >= 20) {
          return problem(422, 'validation-failed', { invalidFields: ['architectureLayers'] })
        }
        const created = { id: newId(), name, sortOrder: state.layers.length, scope: 'organization' as Scope, sourcePackage: null, version: 1 }
        placeLayer(created, body?.sortOrder ?? state.layers.length)
        return json(layerList().find((layer) => layer.id === created.id), 201, 1)
      }
      if (!stored) {
        return problem(404, 'not-found')
      }
      if (stored.scope !== 'organization') {
        return problem(409, 'read-only-metatype')
      }
      const rejected = stale(stored, ifMatch)
      if (rejected) {
        return rejected
      }
      if (method === 'DELETE') {
        const count = state.metatypes.filter((metatype) => metatype.architectureLayerId === stored.id).length
        if (count > 0) {
          return problem(409, 'in-use', { count })
        }
        state.layers = state.layers.filter((layer) => layer.id !== stored.id)
        return json(undefined, 204)
      }
      const name = String(body?.name ?? '').trim()
      if (name === '' || name.length > 100) {
        return problem(422, 'validation-failed', { invalidFields: ['name'] })
      }
      if (state.layers.some((layer) => layer.id !== stored.id && layer.name.toLowerCase() === name.toLowerCase())) {
        return problem(409, 'name-taken', { invalidFields: ['name'] })
      }
      Object.assign(stored, { name, version: stored.version + 1 })
      if (body?.sortOrder !== undefined) {
        placeLayer(stored, body.sortOrder)
      }
      return json(layerList().find((layer) => layer.id === stored.id), 200, stored.version)
    }

    if (kind === 'property-definitions') {
      const stored = state.properties.find((property) => property.id === id)
      if (!id) {
        if (method === 'GET') {
          return json(state.properties)
        }
        const name = String(body?.name ?? '')
        if (state.properties.some((property) => property.name.toLowerCase() === name.toLowerCase())) {
          return problem(409, 'name-taken', { invalidFields: ['name'] })
        }
        const created: Property = {
          id: newId(),
          name,
          dataType: body?.dataType as Property['dataType'],
          category: state.categories.find((category) => category.id === body?.categoryId) ?? null,
          maxLength: (body?.maxLength as number | null) ?? null,
          scope: 'organization',
          sqlColumnName: name.toLowerCase().replace(/\W+/g, '_'),
          version: 1,
          enumValues: [],
        }
        state.properties.push(created)
        return json(created, 201)
      }
      if (!stored) {
        return problem(404, 'not-found')
      }
      if (sub === 'enum-values' && method === 'GET') {
        return json(stored.enumValues)
      }
      const rejected = stale(stored, ifMatch)
      if (rejected) {
        return rejected
      }
      if (stored.scope !== 'organization') {
        return problem(409, 'read-only-metatype')
      }
      if (sub === 'enum-values') {
        stored.version = (stored.version ?? 1) + 1
        if (method === 'POST') {
          const value = { id: newId(), value: String(body?.value), sortOrder: stored.enumValues?.length ?? 0 }
          stored.enumValues = [...(stored.enumValues ?? []), value]
          return json(value, 201)
        }
        stored.enumValues = (stored.enumValues ?? []).filter((value) => value.id !== subId)
        return json(undefined, 204)
      }
      if (method === 'DELETE') {
        if (state.entries.some((entry) => entry.propertyDefId === stored.id)) {
          return problem(409, 'in-use')
        }
        state.properties = state.properties.filter((property) => property.id !== stored.id)
        return json(undefined, 204)
      }
      Object.assign(stored, {
        name: String(body?.name),
        dataType: body?.dataType,
        category: state.categories.find((category) => category.id === body?.categoryId) ?? null,
        version: (stored.version ?? 1) + 1,
      })
      return json(stored, 200, stored.version)
    }

    if (kind === 'property-categories') {
      const stored = state.categories.find((category) => category.id === id)
      if (!id) {
        if (method === 'GET') {
          return json(state.categories)
        }
        const created: Category = { id: newId(), name: String(body?.name), description: (body?.description as string) ?? null, sortOrder: Number(body?.sortOrder ?? 0), scope: 'organization', version: 1 }
        state.categories.push(created)
        return json(created, 201)
      }
      if (!stored) {
        return problem(404, 'not-found')
      }
      const rejected = stale(stored, ifMatch)
      if (rejected) {
        return rejected
      }
      if (method === 'DELETE') {
        state.categories = state.categories.filter((category) => category.id !== stored.id)
        return json(undefined, 204)
      }
      Object.assign(stored, { name: String(body?.name), description: body?.description ?? null, version: (stored.version ?? 1) + 1 })
      return json(stored, 200, stored.version)
    }

    if (kind === 'settings') {
      if (method === 'PUT') {
        state.globalReimport = body?.organizationMappings as 'keep' | 'remove'
      }
      return json({ organizationMappings: state.globalReimport })
    }

    if (kind === 'labels') {
      const locale = id!
      if (method === 'PUT') {
        const own = (state.labels[locale] ??= {})
        for (const [key, text] of Object.entries((body?.labels ?? {}) as Record<string, string | null>)) {
          own[key] = { ...own[key], organizationText: text ?? undefined }
        }
      }
      return json(labelList(locale))
    }
    return problem(404, 'not-found')
  }

  return {
    fetch: fetchFn,
    requests,
    state,
    changeElsewhere(metatypeId, abbreviation) {
      const stored = state.metatypes.find((metatype) => metatype.id === metatypeId)!
      stored.abbreviation = abbreviation
      stored.version = (stored.version ?? 1) + 1
    },
    switchElsewhere(editMode) {
      state.configuration.editMode = editMode
      state.configuration.version += 1
    },
  }
}
