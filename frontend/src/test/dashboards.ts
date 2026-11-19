import type { DashboardConfig, DashboardSummary, WidgetConfig, WidgetData, WidgetSeriesEntry, WidgetWrite } from '../api/dashboards'
import type { MetatypeDetail, PropertyDefinitionDetail } from '../api/metamodel'
import { CATALOG, CATALOGS, COMPACT_VIEW } from './catalogs'
import type { RecordedRequest } from './fetch'

/** Test and story data and a fake backend for the dashboard screens (US-055, US-058 to US-060, US-062, SCR-024, SCR-056). */
function me(displayName: string, id: number, permissions: string[]) {
  return {
    principal: { principalType: 'person', id, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-dashboards',
  }
}

/** Holds `dashboard:manage`: creates and changes shared dashboards. */
export const ME_MANAGER = me('Kurt', 7, ['portal:read', 'entity:read', 'entity:write', 'catalog:manage', 'dashboard:manage'])
/** A reader without `dashboard:manage`: personal dashboards only. */
export const ME_READER = me('Franz', 8, ['portal:read', 'entity:read'])
/** An administrator who reads personal dashboards of others, read-only. */
export const ME_ADMIN = me('Sandra', 9, ['portal:read', 'entity:read', 'settings:manage'])

const OWNER = { id: 7, displayName: 'Kurt' }

export const GRID_DASHBOARD_ID = 'd0000000-0000-4000-8000-000000000001'

/** The page size of the fake table data. */
const PAGE_SIZE = 10
const ENTITIES = 47

export function position(col: number, row: number, width: number, height: number) {
  return { col, row, width, height }
}

export const TEXT_WIDGET: WidgetConfig = {
  id: 'a0000000-0000-4000-8000-000000000001',
  type: 'text',
  title: 'Methodological Notes',
  position: position(1, 1, 4, 2),
  config: { content: '**Method:** the figures come from the repository.\n\n- applications\n- interfaces\n\nSee [the guide](https://example.org/guide).' },
}

export const TABLE_WIDGET: WidgetConfig = {
  id: 'a0000000-0000-4000-8000-000000000002',
  type: 'table',
  title: 'Application Overview',
  position: position(5, 1, 8, 3),
  config: { catalogId: CATALOG.id, savedViewId: COMPACT_VIEW.id, pageSize: PAGE_SIZE },
}

export const BROKEN_WIDGET: WidgetConfig = {
  id: 'a0000000-0000-4000-8000-000000000003',
  type: 'table',
  title: 'Retired Inventory',
  position: position(1, 3, 4, 2),
  config: { catalogId: 'c0000000-0000-4000-8000-0000000000ff', pageSize: PAGE_SIZE },
}

export const APPLICATION_ID = 'e0000000-0000-4000-8000-000000000001'
export const INVESTMENT_ID = 'f0000000-0000-4000-8000-000000000001'
export const PLATEAU_ID = 'f0000000-0000-4000-8000-000000000002'
export const VENDOR_ID = 'f0000000-0000-4000-8000-000000000003'
export const GO_LIVE_ID = 'f0000000-0000-4000-8000-000000000004'
export const CRITICAL_ID = 'f0000000-0000-4000-8000-000000000005'
const P1_ID = '10000000-0000-4000-8000-000000000001'
const P2_ID = '10000000-0000-4000-8000-000000000002'
const P3_ID = '10000000-0000-4000-8000-000000000003'

function property(id: string, name: string, dataType: PropertyDefinitionDetail['dataType'], extra: Partial<PropertyDefinitionDetail> = {}): PropertyDefinitionDetail {
  return { id, name, dataType, ...extra }
}

const PROPERTIES: PropertyDefinitionDetail[] = [
  property(INVESTMENT_ID, 'Investment', 'integer'),
  property(PLATEAU_ID, 'Plateau', 'enum', {
    enumValues: [
      { id: P1_ID, value: 'P1', sortOrder: 1 },
      { id: P2_ID, value: 'P2', sortOrder: 2 },
      { id: P3_ID, value: 'P3', sortOrder: 3 },
    ],
  }),
  property(VENDOR_ID, 'Vendor', 'string'),
  property(GO_LIVE_ID, 'Go live', 'date'),
  property(CRITICAL_ID, 'Critical', 'boolean'),
]

/** The element types of the fake metamodel: applications with five mapped properties, a data object, and a connection type. */
export const METATYPES: MetatypeDetail[] = [
  {
    id: APPLICATION_ID,
    name: 'Application Component',
    abbreviation: 'AC',
    isConnection: false,
    propertyMappings: PROPERTIES.map((propertyDefinition, index) => ({
      id: `20000000-0000-4000-8000-00000000000${index + 1}`,
      propertyDefinition,
      necessity: 'optional' as const,
      validFrom: '2026-01-01',
      sortOrder: index,
    })),
  },
  { id: 'e0000000-0000-4000-8000-000000000002', name: 'Data Object', abbreviation: 'DO', isConnection: false, propertyMappings: [] },
  { id: 'e0000000-0000-4000-8000-000000000003', name: 'Information Flow', abbreviation: 'IF', isConnection: true, propertyMappings: [] },
]

const AGGREGATION_SUM = { metatypeId: APPLICATION_ID, propertyDefId: INVESTMENT_ID, function: 'sum' as const }

export const KPI_WIDGET: WidgetConfig = {
  id: 'a0000000-0000-4000-8000-000000000011',
  type: 'kpi',
  title: 'Total Investment',
  position: position(1, 1, 4, 2),
  config: { dataSource: 'property-aggregation', aggregation: AGGREGATION_SUM, unit: 'CHF', comparison: 'vs-target', targetValue: 50000000 },
}

export const KPI_NO_DATA_WIDGET: WidgetConfig = {
  id: 'a0000000-0000-4000-8000-000000000012',
  type: 'kpi',
  title: 'Average Investment',
  position: position(5, 1, 4, 2),
  config: { dataSource: 'property-aggregation', aggregation: { ...AGGREGATION_SUM, function: 'avg' }, unit: 'CHF', comparison: 'none' },
}

function previousKpi(suffix: string, title: string): WidgetConfig {
  return {
    id: `a0000000-0000-4000-8000-0000000001${suffix}`,
    type: 'kpi',
    title,
    position: position(1, 1, 4, 2),
    config: { dataSource: 'property-aggregation', aggregation: AGGREGATION_SUM, unit: 'CHF', comparison: 'vs-previous-value' },
  }
}

/** KPIs compared with the value 30 days ago (us-056-kpi-previous-value). */
export const KPI_INCREASED_WIDGET = previousKpi('21', 'Investment (increased)')
export const KPI_DECREASED_WIDGET = previousKpi('22', 'Investment (decreased)')
export const KPI_EQUAL_WIDGET = previousKpi('23', 'Investment (equal)')
export const KPI_NO_COMPARISON_WIDGET = previousKpi('24', 'Investment (no comparison value)')
export const KPI_PREVIOUS_ZERO_WIDGET = previousKpi('25', 'Investment (from zero)')

export const PREVIOUS_AT = '2026-10-20T12:00:00Z'

export const BAR_WIDGET: WidgetConfig = {
  id: 'a0000000-0000-4000-8000-000000000013',
  type: 'chart',
  title: 'Investment per Plateau',
  position: position(1, 3, 6, 3),
  config: {
    dataSource: 'property-aggregation',
    aggregation: { ...AGGREGATION_SUM, groupBy: { kind: 'property', propertyDefId: PLATEAU_ID } },
    chartType: 'bar',
    unit: 'CHF',
    showLegend: true,
    showDataLabels: true,
  },
}

export const PIE_WIDGET: WidgetConfig = {
  id: 'a0000000-0000-4000-8000-000000000014',
  type: 'chart',
  title: 'Entities per Layer',
  position: position(7, 3, 6, 3),
  config: {
    dataSource: 'property-aggregation',
    aggregation: { metatypeId: null, propertyDefId: null, function: 'count', groupBy: { kind: 'layer' } },
    chartType: 'pie',
    showLegend: true,
    showDataLabels: false,
  },
}

export const TREEMAP_WIDGET: WidgetConfig = {
  ...PIE_WIDGET,
  id: 'a0000000-0000-4000-8000-000000000015',
  title: 'Applications per Vendor',
  config: {
    dataSource: 'property-aggregation',
    aggregation: { metatypeId: APPLICATION_ID, propertyDefId: null, function: 'count', groupBy: { kind: 'property', propertyDefId: VENDOR_ID } },
    chartType: 'treemap',
    showLegend: false,
    showDataLabels: true,
  },
}

export const DONUT_WIDGET: WidgetConfig = {
  ...PIE_WIDGET,
  id: 'a0000000-0000-4000-8000-000000000016',
  title: 'Critical Applications',
  config: {
    dataSource: 'property-aggregation',
    aggregation: { metatypeId: APPLICATION_ID, propertyDefId: null, function: 'count', groupBy: { kind: 'property', propertyDefId: CRITICAL_ID } },
    chartType: 'donut',
    showLegend: true,
    showDataLabels: true,
  },
}

export const RESTRICTED_WIDGET: WidgetConfig = {
  ...KPI_WIDGET,
  id: 'a0000000-0000-4000-8000-000000000017',
  title: 'Investment (restricted)',
  position: position(9, 1, 4, 2),
  config: { ...KPI_WIDGET.config, comparison: 'none' },
}

export const INVALID_WIDGET: WidgetConfig = {
  ...BAR_WIDGET,
  id: 'a0000000-0000-4000-8000-000000000018',
  title: 'Investment per Vendor',
  position: position(1, 6, 6, 3),
}

const PLATEAU_SERIES: WidgetSeriesEntry[] = [
  { kind: 'value', key: 'P3', label: 'P3', labelKey: `enum-value.${P3_ID}.label`, value: 25000000 },
  { kind: 'value', key: 'P2', label: 'P2', labelKey: `enum-value.${P2_ID}.label`, value: 12000000 },
  { kind: 'value', key: 'P1', label: 'P1', labelKey: `enum-value.${P1_ID}.label`, value: 5000000 },
  { kind: 'none', value: 1000000 },
]

const LAYER_SERIES: WidgetSeriesEntry[] = [
  { kind: 'value', key: 'l1', label: 'Application', labelKey: 'architecture-layer.l1.name', value: 20 },
  { kind: 'value', key: 'l2', label: 'Business', labelKey: 'architecture-layer.l2.name', value: 20 },
  { kind: 'value', key: 'l3', label: 'Technology', labelKey: 'architecture-layer.l3.name', value: 10 },
]

const VENDOR_SERIES: WidgetSeriesEntry[] = [
  { kind: 'value', key: 'SAP', label: 'SAP', value: 9 },
  { kind: 'value', key: 'Oracle', label: 'Oracle', value: 6 },
  { kind: 'value', key: 'Microsoft', label: 'Microsoft', value: 4 },
  { kind: 'none', value: 3 },
  { kind: 'other', value: 2 },
]

const CRITICAL_SERIES: WidgetSeriesEntry[] = [
  { kind: 'true', value: 7 },
  { kind: 'false', value: 15 },
]

/** The data of a KPI or chart widget of the fakes; `restricted` shows the error of a property the reader may not aggregate. */
function aggregationData(widget: WidgetConfig, restricted: boolean): WidgetData {
  const base = { widgetId: widget.id, type: widget.type }
  if (widget.id === INVALID_WIDGET.id) {
    return { ...base, status: 'error', error: { code: 'invalid-configuration', message: 'The configuration no longer fits the metamodel' } }
  }
  if (widget.id === RESTRICTED_WIDGET.id || (restricted && widget.config.aggregation?.propertyDefId === INVESTMENT_ID)) {
    return { ...base, status: 'error', error: { code: 'restricted', message: 'The values are not readable for every entity' } }
  }
  switch (widget.id) {
    case KPI_WIDGET.id:
      return { ...base, status: 'ok', value: 42000000, target: 50000000 }
    case KPI_INCREASED_WIDGET.id:
      return { ...base, status: 'ok', value: 42000000, previousValue: 35000000, previousAt: PREVIOUS_AT }
    case KPI_DECREASED_WIDGET.id:
      return { ...base, status: 'ok', value: 42000000, previousValue: 50000000, previousAt: PREVIOUS_AT }
    case KPI_EQUAL_WIDGET.id:
      return { ...base, status: 'ok', value: 42000000, previousValue: 42000000, previousAt: PREVIOUS_AT }
    case KPI_NO_COMPARISON_WIDGET.id:
      return { ...base, status: 'ok', value: 42000000, previousValue: null, previousAt: PREVIOUS_AT }
    case KPI_PREVIOUS_ZERO_WIDGET.id:
      return { ...base, status: 'ok', value: 42000000, previousValue: 0, previousAt: PREVIOUS_AT }
    case KPI_NO_DATA_WIDGET.id:
      return { ...base, status: 'ok', value: null }
    case BAR_WIDGET.id:
      return { ...base, status: 'ok', series: PLATEAU_SERIES }
    case PIE_WIDGET.id:
      return { ...base, status: 'ok', series: LAYER_SERIES }
    case TREEMAP_WIDGET.id:
      return { ...base, status: 'ok', series: VENDOR_SERIES }
    case DONUT_WIDGET.id:
      return { ...base, status: 'ok', series: CRITICAL_SERIES }
    default:
      return widget.type === 'kpi' ? { ...base, status: 'ok', value: 0 } : { ...base, status: 'ok', series: [] }
  }
}

export function dashboard(overrides: Partial<DashboardConfig> = {}): DashboardConfig {
  return {
    id: GRID_DASHBOARD_ID,
    name: 'IT Investment Planning 2026–2030',
    description: 'Planning figures of the IT portfolio',
    scope: 'instance',
    owner: OWNER,
    version: 1,
    canEdit: true,
    widgets: [],
    ...overrides,
  }
}

export const EMPTY_DASHBOARD = dashboard()
export const FILLED_DASHBOARD = dashboard({ version: 4, widgets: [TEXT_WIDGET, TABLE_WIDGET] })
export const DASHBOARD_WITH_ERROR = dashboard({ version: 5, widgets: [TEXT_WIDGET, TABLE_WIDGET, BROKEN_WIDGET] })
export const OVERLAPPING_DASHBOARD = dashboard({
  version: 6,
  widgets: [TEXT_WIDGET, { ...TABLE_WIDGET, position: position(3, 1, 8, 3) }],
})
export const KPI_AND_CHARTS_DASHBOARD = dashboard({
  version: 8,
  widgets: [
    KPI_WIDGET,
    KPI_NO_DATA_WIDGET,
    RESTRICTED_WIDGET,
    BAR_WIDGET,
    PIE_WIDGET,
    { ...TREEMAP_WIDGET, position: position(1, 6, 6, 3) },
    { ...DONUT_WIDGET, position: position(7, 6, 6, 3) },
    { ...INVALID_WIDGET, position: position(1, 9, 6, 2) },
  ],
})
export const PERSONAL_DASHBOARD = dashboard({
  id: 'd0000000-0000-4000-8000-000000000002',
  name: 'My analysis',
  description: '',
  scope: 'personal',
  owner: { id: 8, displayName: 'Franz' },
  widgets: [TEXT_WIDGET],
})

function summary(config: DashboardConfig, canEdit = config.canEdit): DashboardSummary {
  return {
    id: config.id,
    name: config.name,
    description: config.description,
    scope: config.scope,
    owner: config.owner,
    widgetCount: config.widgets.length,
    canEdit,
  }
}

export const SUMMARIES: DashboardSummary[] = [
  summary(FILLED_DASHBOARD),
  summary(dashboard({ id: 'd0000000-0000-4000-8000-000000000003', name: 'Security Posture', description: '', widgets: [TEXT_WIDGET] })),
  summary(PERSONAL_DASHBOARD, false),
]

/** A page of the fake table widget: 47 entities, `PAGE_SIZE` rows per page. */
export function tablePage(page: number, restricted = false): WidgetData['table'] {
  const first = page * PAGE_SIZE
  const count = Math.max(Math.min(PAGE_SIZE, ENTITIES - first), 0)
  return {
    columns: [
      { key: 'name', label: 'Name', sortable: true, filterType: 'text', readable: true },
      { key: 'status', label: 'Status', sortable: true, filterType: 'text', readable: !restricted },
    ],
    rows: Array.from({ length: count }, (_, index) => ({
      entityId: first + index + 1,
      openIssues: false,
      values: { name: `Application ${String(first + index + 1).padStart(2, '0')}`, status: restricted ? null : 'active' },
    })),
    page: { page, pageSize: PAGE_SIZE, totalElements: ENTITIES, totalPages: Math.ceil(ENTITIES / PAGE_SIZE) },
  }
}

export function widgetData(widget: WidgetConfig, page = 0, restricted = false): WidgetData {
  if (widget.type === 'text') {
    return { widgetId: widget.id, type: 'text', status: 'static' }
  }
  if (widget.type === 'kpi' || widget.type === 'chart') {
    return aggregationData(widget, restricted)
  }
  if (widget.id === BROKEN_WIDGET.id) {
    return { widgetId: widget.id, type: 'table', status: 'error', error: { code: 'catalog-not-found', message: 'Catalog not found' } }
  }
  return { widgetId: widget.id, type: 'table', status: 'ok', table: tablePage(page, restricted) }
}

export const PROBLEM_HEADERS = { contentType: 'application/problem+json' }

export function problem(status: number, type: string, extra: Record<string, unknown> = {}) {
  return {
    status,
    contentType: 'application/problem+json',
    body: { type: `urn:oea:problem:${type}`, title: type, status, ...extra },
  }
}

export interface DashboardApiOptions {
  me: unknown
  /** the dashboards of the backend; the first is the one of the page */
  dashboards?: DashboardConfig[]
  /** records every request with its method and headers */
  recorded?: RecordedRequest[]
  /** the table widget shows its restricted column redacted */
  restricted?: boolean
  /** the next write finds that someone else changed the dashboard meanwhile (412) */
  conflict?: boolean
  /** answers a write of a widget or the layout with this problem instead */
  reject?: { status: number; type: string; invalidFields?: string[] }
}

function respond(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  const noBody = status === 204
  return new Response(noBody ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json', ...headers },
  })
}

/**
 * A fake backend of the dashboard API with state: writes check `If-Match` against the version,
 * raise it and change the dashboards, so that a page can be driven through whole flows.
 */
export function dashboardApi(options: DashboardApiOptions): typeof fetch & { state: { dashboards: DashboardConfig[]; conflict: boolean } } {
  const state = {
    dashboards: (options.dashboards ?? [FILLED_DASHBOARD]).map((config) => structuredClone(config)),
    conflict: options.conflict ?? false,
  }
  let counter = 0
  const api = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost')
    const path = url.pathname
    const method = (init?.method ?? 'GET').toUpperCase()
    options.recorded?.push({ path: path + url.search, init })
    const headers = (init?.headers ?? {}) as Record<string, string>
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : undefined

    if (path === '/api/v1/me') {
      return respond(200, options.me)
    }
    if (path === '/api/v1/metamodel/metatypes' && method === 'GET') {
      return respond(200, METATYPES)
    }
    if (path === '/api/v1/catalogs' && method === 'GET') {
      return respond(200, CATALOGS)
    }
    const catalog = /^\/api\/v1\/catalogs\/([^/]+)$/.exec(path)
    if (catalog && method === 'GET') {
      return respond(200, { ...CATALOG, id: catalog[1], savedViews: [COMPACT_VIEW] })
    }
    if (path === '/api/v1/dashboards' && method === 'GET') {
      return respond(200, state.dashboards.map((config) => summary(config)))
    }
    if (path === '/api/v1/dashboards' && method === 'POST') {
      const name = String(body?.name ?? '')
      if (state.dashboards.some((config) => config.scope === body?.scope && config.name.toLowerCase() === name.toLowerCase())) {
        const taken = problem(422, 'dashboard-name-taken', { invalidFields: ['name'], detail: 'A dashboard with this name already exists' })
        return respond(taken.status, taken.body)
      }
      counter += 1
      const created = dashboard({
        id: `d1000000-0000-4000-8000-${String(counter).padStart(12, '0')}`,
        name,
        description: String(body?.description ?? ''),
        scope: body?.scope as DashboardConfig['scope'],
        widgets: [],
      })
      state.dashboards.push(created)
      return respond(201, created, { ETag: '"1"' })
    }

    const viewMatch = /^\/api\/v1\/views\/dashboards\/([^/]+)(?:\/(data)|\/widgets\/([^/]+)\/data)?$/.exec(path)
    if (viewMatch) {
      const found = state.dashboards.find((config) => config.id === viewMatch[1])
      if (!found) {
        return respond(404, problem(404, 'not-found').body)
      }
      if (viewMatch[2]) {
        return respond(200, {
          dashboardId: found.id,
          generatedAt: '2026-11-18T10:00:00Z',
          widgets: found.widgets.map((widget) => widgetData(widget, 0, options.restricted)),
        })
      }
      if (viewMatch[3]) {
        const widget = found.widgets.find((candidate) => candidate.id === viewMatch[3])
        return widget
          ? respond(200, widgetData(widget, Number(url.searchParams.get('page') ?? 0), options.restricted))
          : respond(404, problem(404, 'not-found').body)
      }
      return respond(200, found, { ETag: `"${found.version}"` })
    }

    const writeMatch = /^\/api\/v1\/dashboards\/([^/]+)(?:\/(widgets|layout)(?:\/([^/]+))?)?$/.exec(path)
    if (writeMatch) {
      const found = state.dashboards.find((config) => config.id === writeMatch[1])
      if (!found) {
        return respond(404, problem(404, 'not-found').body)
      }
      if (state.conflict) {
        state.conflict = false
        found.version += 1
      }
      const ifMatch = headers['If-Match']
      if (ifMatch === undefined) {
        return respond(428, problem(428, 'precondition-required').body)
      }
      if (ifMatch !== `"${found.version}"`) {
        return respond(412, problem(412, 'stale-version', { currentVersion: found.version }).body)
      }
      if (options.reject && writeMatch[2]) {
        const rejected = problem(options.reject.status, options.reject.type, { invalidFields: options.reject.invalidFields ?? [] })
        return respond(rejected.status, rejected.body)
      }
      if (!writeMatch[2]) {
        if (method === 'DELETE') {
          state.dashboards = state.dashboards.filter((config) => config !== found)
          return respond(204, undefined)
        }
        const name = String(body?.name ?? '')
        if (state.dashboards.some((config) => config !== found && config.scope === body?.scope && config.name.toLowerCase() === name.toLowerCase())) {
          const taken = problem(422, 'dashboard-name-taken', { invalidFields: ['name'] })
          return respond(taken.status, taken.body)
        }
        Object.assign(found, { name, description: String(body?.description ?? ''), scope: body?.scope })
      } else if (writeMatch[2] === 'layout') {
        for (const item of (body?.positions ?? []) as { widgetId: string; col: number; row: number; width: number; height: number }[]) {
          const widget = found.widgets.find((candidate) => candidate.id === item.widgetId)
          if (widget) {
            widget.position = position(item.col, item.row, item.width, item.height)
          }
        }
      } else if (method === 'POST') {
        counter += 1
        const write = body as unknown as WidgetWrite
        found.widgets.push({ id: `a1000000-0000-4000-8000-${String(counter).padStart(12, '0')}`, ...write } as WidgetConfig)
      } else if (method === 'PUT') {
        const write = body as unknown as WidgetWrite
        found.widgets = found.widgets.map((widget) => (widget.id === writeMatch[3] ? ({ ...widget, ...write } as WidgetConfig) : widget))
      } else if (method === 'DELETE') {
        found.widgets = found.widgets.filter((widget) => widget.id !== writeMatch[3])
      }
      found.version += 1
      return respond(200, found, { ETag: `"${found.version}"` })
    }
    throw new TypeError(`no route for ${method} ${path}`)
  }) as typeof fetch & { state: typeof state }
  api.state = state
  return api
}
