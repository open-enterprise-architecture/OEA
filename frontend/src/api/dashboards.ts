import type { components } from './generated/schema'
import { readRejection, RequestRejected } from './problem'
import { currentCsrfToken, type Outcome } from './session'

export type DashboardConfig = components['schemas']['DashboardConfig']
export type DashboardSummary = components['schemas']['DashboardSummary']
export type DashboardScope = components['schemas']['DashboardScope']
export type DashboardData = components['schemas']['DashboardData']
export type WidgetConfig = components['schemas']['WidgetConfig']
export type WidgetData = components['schemas']['WidgetData']
export type WidgetWrite = components['schemas']['WidgetWrite']
export type WidgetType = components['schemas']['WidgetType']
export type WidgetSettings = components['schemas']['WidgetSettings']
export type WidgetAggregation = components['schemas']['WidgetAggregation']
export type WidgetGroupBy = components['schemas']['WidgetGroupBy']
export type WidgetSeriesEntry = components['schemas']['WidgetSeriesEntry']
export type ChartType = NonNullable<WidgetSettings['chartType']>
export type GridPosition = components['schemas']['GridPosition']
export type LayoutPosition = components['schemas']['LayoutPosition']

/** The columns of the grid (US-060, REQ-057). */
export const GRID_COLUMNS = 12
/** The last row a widget may start in. */
export const MAX_ROW = 500
/** The tallest widget, in rows. */
export const MAX_HEIGHT = 6
/** The input limits of the contract (design D6 of us-055-dashboards). */
export const LIMITS = { name: 200, description: 1000, title: 150, content: 10000, minPageSize: 5, maxPageSize: 100, defaultPageSize: 10, unit: 20, maxGroups: 50 }

const JSON_HEADERS = { Accept: 'application/json, application/problem+json' }

/** Why a position is not allowed: the widget leaves the grid to the right, or another limit is exceeded. */
export type PositionProblem = 'exceeds-grid' | 'out-of-range'

/**
 * Checks a position against the grid before it is sent (US-060 AC2): `col + width` is at most 13,
 * the row is 1 to 500 and the height 1 to 6. `undefined` if the position is allowed.
 */
export function positionProblem(position: GridPosition): PositionProblem | undefined {
  const { col, row, width, height } = position
  if (col < 1 || col > GRID_COLUMNS || width < 1 || row < 1 || row > MAX_ROW || height < 1 || height > MAX_HEIGHT) {
    return 'out-of-range'
  }
  return col + width > GRID_COLUMNS + 1 ? 'exceeds-grid' : undefined
}

/** Whether two positions share a cell of the grid. */
export function overlap(a: GridPosition, b: GridPosition): boolean {
  return a.col < b.col + b.width && b.col < a.col + a.width && a.row < b.row + b.height && b.row < a.row + a.height
}

/** The IDs of the widgets that overlap another widget (US-060 AC4). */
export function overlappingIds(widgets: Pick<WidgetConfig, 'id' | 'position'>[]): Set<string> {
  const found = new Set<string>()
  for (let i = 0; i < widgets.length; i++) {
    for (let j = i + 1; j < widgets.length; j++) {
      if (overlap(widgets[i]!.position, widgets[j]!.position)) {
        found.add(widgets[i]!.id)
        found.add(widgets[j]!.id)
      }
    }
  }
  return found
}

/** The row below all widgets, where a new widget is placed. */
export function nextFreeRow(widgets: Pick<WidgetConfig, 'position'>[]): number {
  return widgets.reduce((row, widget) => Math.max(row, widget.position.row + widget.position.height), 1)
}

function etag(version: number): string {
  return `"${version}"`
}

function writeHeaders(version: number | undefined, body: boolean): Record<string, string> {
  const csrfToken = currentCsrfToken()
  return {
    ...JSON_HEADERS,
    ...(body ? { 'Content-Type': 'application/json' } : {}),
    ...(version !== undefined ? { 'If-Match': etag(version) } : {}),
    ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
  }
}

/** GET /dashboards: the shared dashboards and the caller's own, for administrators also others' personal ones (US-055). */
export async function fetchDashboards(fetchFn: typeof fetch = fetch): Promise<DashboardSummary[]> {
  const response = await fetchFn('/api/v1/dashboards', { credentials: 'same-origin', headers: JSON_HEADERS })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DashboardSummary[]
}

/** GET /views/dashboards/{id}: `undefined` if the dashboard does not exist or is not visible (404). */
export async function fetchDashboard(id: string, fetchFn: typeof fetch = fetch): Promise<DashboardConfig | undefined> {
  const response = await fetchFn(`/api/v1/views/dashboards/${encodeURIComponent(id)}`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (response.status === 404) {
    return undefined
  }
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DashboardConfig
}

/** GET /views/dashboards/{id}/data: the data of all widgets, computed with the reader's visibility (REQ-055). */
export async function fetchDashboardData(id: string, fetchFn: typeof fetch = fetch): Promise<DashboardData> {
  const response = await fetchFn(`/api/v1/views/dashboards/${encodeURIComponent(id)}/data`, {
    credentials: 'same-origin',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new RequestRejected(await readRejection(response))
  }
  return (await response.json()) as DashboardData
}

/** GET /views/dashboards/{id}/widgets/{widgetId}/data: another page of a table widget (`page` is 0-based). */
export async function fetchWidgetData(id: string, widgetId: string, page: number, fetchFn: typeof fetch = fetch): Promise<Outcome<WidgetData>> {
  const response = await fetchFn(
    `/api/v1/views/dashboards/${encodeURIComponent(id)}/widgets/${encodeURIComponent(widgetId)}/data?page=${page}`,
    { credentials: 'same-origin', headers: JSON_HEADERS },
  )
  if (response.ok) {
    return { ok: true, value: (await response.json()) as WidgetData }
  }
  return { ok: false, rejection: await readRejection(response) }
}

async function dashboardOutcome(response: Response, status = 200): Promise<Outcome<DashboardConfig>> {
  if (response.status === status) {
    return { ok: true, value: (await response.json()) as DashboardConfig }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/**
 * POST /dashboards. A taken name is the rejection `dashboard-name-taken`, other invalid input
 * `validation-failed`, both 422 with `invalidFields`; a shared dashboard without `dashboard:manage` is 403.
 */
export async function createDashboard(
  request: { name: string; description: string; scope: DashboardScope },
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<DashboardConfig>> {
  const response = await fetchFn('/api/v1/dashboards', {
    method: 'POST',
    credentials: 'same-origin',
    headers: writeHeaders(undefined, true),
    body: JSON.stringify(request),
  })
  return dashboardOutcome(response, 201)
}

/** PUT /dashboards/{id} with `If-Match`: name, description and scope (US-062 AC4). */
export async function updateDashboard(
  id: string,
  request: { name: string; description: string; scope: DashboardScope },
  version: number,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<DashboardConfig>> {
  const response = await fetchFn(`/api/v1/dashboards/${encodeURIComponent(id)}`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: writeHeaders(version, true),
    body: JSON.stringify(request),
  })
  return dashboardOutcome(response)
}

/** DELETE /dashboards/{id} with `If-Match`: the dashboard and its widgets. */
export async function deleteDashboard(id: string, version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<undefined>> {
  const response = await fetchFn(`/api/v1/dashboards/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: writeHeaders(version, false),
  })
  if (response.status === 204) {
    return { ok: true, value: undefined }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /dashboards/{id}/widgets with `If-Match` (US-058, US-059). */
export async function addWidget(id: string, widget: WidgetWrite, version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<DashboardConfig>> {
  const response = await fetchFn(`/api/v1/dashboards/${encodeURIComponent(id)}/widgets`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: writeHeaders(version, true),
    body: JSON.stringify(widget),
  })
  return dashboardOutcome(response)
}

/** PUT /dashboards/{id}/widgets/{widgetId} with `If-Match`; the type does not change. */
export async function updateWidget(
  id: string,
  widgetId: string,
  widget: WidgetWrite,
  version: number,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<DashboardConfig>> {
  const response = await fetchFn(`/api/v1/dashboards/${encodeURIComponent(id)}/widgets/${encodeURIComponent(widgetId)}`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: writeHeaders(version, true),
    body: JSON.stringify(widget),
  })
  return dashboardOutcome(response)
}

/** DELETE /dashboards/{id}/widgets/{widgetId} with `If-Match`. */
export async function removeWidget(id: string, widgetId: string, version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<DashboardConfig>> {
  const response = await fetchFn(`/api/v1/dashboards/${encodeURIComponent(id)}/widgets/${encodeURIComponent(widgetId)}`, {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: writeHeaders(version, false),
  })
  return dashboardOutcome(response)
}

/** PUT /dashboards/{id}/layout with `If-Match`: the positions of 1 to 50 widgets in one request (US-060). */
export async function updateLayout(id: string, positions: LayoutPosition[], version: number, fetchFn: typeof fetch = fetch): Promise<Outcome<DashboardConfig>> {
  const response = await fetchFn(`/api/v1/dashboards/${encodeURIComponent(id)}/layout`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers: writeHeaders(version, true),
    body: JSON.stringify({ positions }),
  })
  return dashboardOutcome(response)
}
