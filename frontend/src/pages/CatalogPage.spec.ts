import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { setLanguage } from '../i18n'
import { loadLabels } from '../i18n/labels'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import {
  ACTIVE_SYSTEMS,
  APPLICATIONS,
  CATALOG,
  CATALOGS,
  catalogApi,
  COMPACT_VIEW,
  DATA_FLOW,
  FLOW_CATALOG,
  FLOWS,
  FULL_VIEW,
  INTERFACE,
  INTERFACES_JOIN,
  manyApplications,
  ME_ARCHITECT,
  ME_VIEWER,
  METATYPES_WITH_FLOWS,
  PROTOCOL_DEFINITION,
  SAP_ONLY,
  STATUS_DEFINITION,
  VENDOR_DEFINITION,
  type Application,
  type CatalogRequest,
} from '../test/catalogs'
import CatalogJoinsDialog from '../components/CatalogJoinsDialog.vue'
import { clearTree } from '../navigation/store'
import { LANDSCAPE_ID, navigationApi, ROOT_ID, type TreeRequest } from '../test/navigation'
import CatalogPage from './CatalogPage.vue'

const CATALOG_PATH = `/catalogs/${CATALOG.id}`

interface Mounted {
  wrapper: VueWrapper
  router: Router
  recorded: CatalogRequest[]
}

/** The page behind a router, so that URL, back navigation and reload act as in the app. */
async function open(
  location: string,
  applications: Application[] = APPLICATIONS,
  throttledFor?: number,
  options: { me?: unknown; catalog?: unknown; metatypes?: unknown[] } = {},
): Promise<Mounted> {
  const recorded: CatalogRequest[] = []
  const fetchFn = catalogApi(applications, { recorded, throttledFor, ...options })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/catalogs', component: { render: () => h('div', { 'data-testid': 'overview' }) } },
      { path: '/catalogs/:id', component: CatalogPage, props: (route) => ({ id: route.params.id, fetchFn }) },
    ],
  })
  await router.push(location)
  const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] } })
  await flushPromises()
  return { wrapper, router, recorded }
}

function names(wrapper: VueWrapper): string[] {
  return wrapper.findAll('[data-testid="catalog-table"] tbody tr').map((row) => row.findAll('td')[0]!.text())
}

function header(wrapper: VueWrapper, key: string) {
  return wrapper.get(`[data-testid="sort-${key}"]`)
}

async function click(wrapper: VueWrapper, selector: string) {
  await wrapper.get(selector).trigger('click')
  await flushPromises()
}

describe('CatalogPage', () => {
  it('shows name, entity type and scope of the catalog', async () => {
    const { wrapper } = await open(CATALOG_PATH)
    expect(wrapper.get('[data-testid="catalog-title"]').text()).toBe('Application Inventory')
    expect(wrapper.get('[data-testid="catalog-type"]').text()).toBe('Application Component')
    expect(wrapper.get('[data-testid="catalog-scope"]').text()).toBe('Shared')
  })

  it('shows "Catalog not found" for 404', async () => {
    const { wrapper } = await open('/catalogs/foreign')
    expect(wrapper.get('[data-testid="catalog-not-found"]').text()).toContain('Catalog not found')
  })

  // spec platform/request-limits "Principal above the limit"
  it('shows the waiting time when the request limit is reached', async () => {
    const { wrapper } = await open(CATALOG_PATH, APPLICATIONS, 17)
    expect(wrapper.get('[data-testid="catalog-failure"]').text()).toContain('Too many requests. Try again in 17 seconds.')
  })

  // Requirement: Rows of the primary metatype

  it('shows a catalog with three applications', async () => {
    const { wrapper } = await open(CATALOG_PATH)
    expect(wrapper.findAll('[data-testid="catalog-table"] thead th').map((th) => th.text())).toEqual(['Name', 'Status'])
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 1')
  })

  it('shows the hint of an empty repository', async () => {
    const { wrapper } = await open(CATALOG_PATH, [])
    expect(wrapper.find('[data-testid="catalog-table"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="catalog-empty"]').text()).toContain(
      'No entries found. Entities can be captured as a delta in a solution.',
    )
  })

  // Requirement: Sorting

  it('sorts ascending on a click on the header and shows ↑', async () => {
    const { wrapper, router } = await open(CATALOG_PATH)
    await click(wrapper, '[data-testid="sort-status"]')
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(header(wrapper, 'status').text()).toContain('↑')
    expect(wrapper.get('[data-testid="catalog-table"] thead th:nth-child(2)').attributes('aria-sort')).toBe('ascending')
    expect(router.currentRoute.value.query).toEqual({ sort: 'status:asc' })
  })

  it('switches to descending and back to the default order', async () => {
    const { wrapper, router } = await open(CATALOG_PATH)
    await click(wrapper, '[data-testid="sort-name"]')
    expect(header(wrapper, 'name').text()).toContain('↑')
    await click(wrapper, '[data-testid="sort-name"]')
    expect(names(wrapper)).toEqual(['Intranet', 'ERP', 'CRM System'])
    expect(header(wrapper, 'name').text()).toContain('↓')
    await click(wrapper, '[data-testid="sort-name"]')
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(header(wrapper, 'name').text()).not.toMatch(/[↑↓]/)
    expect(router.currentRoute.value.query).toEqual({})
  })

  // Requirement: Pagination

  it('shows the first of three pages and moves to page 2', async () => {
    const { wrapper } = await open(CATALOG_PATH, manyApplications(120))
    expect(names(wrapper)).toHaveLength(50)
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 3')
    const pageTwo = wrapper.get('[data-testid="catalog-pagination"]').findAll('button').find((button) => button.text() === '2')
    await pageTwo!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 2 of 3')
    expect(names(wrapper)[0]).toBe('Application 051')
  })

  it('resets to the first page when the sort changes', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?page=2`, manyApplications(120))
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 3 of 3')
    await click(wrapper, '[data-testid="sort-name"]')
    expect(router.currentRoute.value.query).toEqual({ sort: 'name:asc' })
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 3')
  })

  // Requirement: View state survives navigation (ADR-044)

  it('restores sort and page when navigating back to the catalog', async () => {
    const { wrapper, router } = await open('/catalogs', manyApplications(120))
    await router.push(CATALOG_PATH)
    await flushPromises()
    await click(wrapper, '[data-testid="sort-name"]')
    await click(wrapper, '[data-testid="sort-name"]')
    const pageTwo = wrapper.get('[data-testid="catalog-pagination"]').findAll('button').find((button) => button.text() === '2')
    await pageTwo!.trigger('click')
    await flushPromises()
    await router.push('/catalogs')
    await flushPromises()
    expect(wrapper.find('[data-testid="overview"]').exists()).toBe(true)

    router.back()
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ sort: 'name:desc', page: '1' })
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 2 of 3')
    expect(header(wrapper, 'name').text()).toContain('↓')
    expect(names(wrapper)[0]).toBe('Application 070')
  })

  it('adds no history entry per sort or page change', async () => {
    const { wrapper, router } = await open('/catalogs', manyApplications(120))
    await router.push(CATALOG_PATH)
    await flushPromises()
    await click(wrapper, '[data-testid="sort-name"]')
    await click(wrapper, '[data-testid="sort-status"]')
    router.back()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/catalogs')
  })

  it('keeps the sort after a reload', async () => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?sort=status:asc`)
    expect(recorded.at(-1)?.query).toEqual({ sort: 'status:asc' })
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(header(wrapper, 'status').text()).toContain('↑')
  })

  it.each([
    ['an unknown sort column', '?sort=owner:asc'],
    ['a malformed sort', '?sort=name'],
    ['an invalid page', '?page=abc'],
    ['a page beyond the last one', '?page=9'],
  ])('falls back to the default view for %s', async (_, query) => {
    const { wrapper, router } = await open(`${CATALOG_PATH}${query}`)
    expect(router.currentRoute.value.query).toEqual({})
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Page 1 of 1')
  })

  // US-140 AC1: metatype names, column headers and enum values in the language of the user
  it('shows the metamodel labels in German', async () => {
    setLanguage('de')
    await loadLabels('de', catalogApi(APPLICATIONS))
    // the page loads the session, whose language applies
    const { wrapper } = await open(CATALOG_PATH, APPLICATIONS, undefined, { me: { ...ME_ARCHITECT, uiLanguage: 'de' } })
    expect(wrapper.get('[data-testid="catalog-type"]').text()).toBe('Anwendungskomponente')
    expect(wrapper.get('[data-testid="catalog-scope"]').text()).toBe('Geteilt')
    expect(header(wrapper, 'status').text()).toContain('Status')
    const statuses = wrapper.findAll('[data-testid="catalog-table"] tbody tr').map((row) => row.findAll('td')[1]!.text())
    expect(statuses).toEqual(['Aktiv', 'Aktiv', 'Geplant'])
    expect(wrapper.get('[data-testid="catalog-page-info"]').text()).toContain('Seite 1 von 1')
  })
})

// spec catalog/catalog-columns of us-047-configure-columns
describe('CatalogPage columns', () => {
  function headers(wrapper: VueWrapper): string[] {
    return wrapper.findAll('[data-testid="catalog-table"] thead th').map((th) => th.text())
  }

  async function configure(wrapper: VueWrapper) {
    await click(wrapper, '[data-testid="configure-columns"]')
    return document.body
  }

  function rows(): HTMLElement[] {
    return [...document.body.querySelectorAll<HTMLElement>('[data-testid="column-row"]')]
  }

  function row(name: string): HTMLElement {
    const found = rows().find((candidate) => candidate.querySelector('[data-testid="column-name"]')?.textContent === name)
    if (!found) {
      throw new Error(`no column ${name}`)
    }
    return found
  }

  async function press(element: Element | null) {
    ;(element as HTMLElement).click()
    await flushPromises()
  }

  async function toggle(name: string, testId: string) {
    await press(row(name).querySelector(`[data-testid="${testId}"]`))
  }

  async function type(name: string, value: string) {
    const input = row(name).querySelector<HTMLInputElement>('[data-testid="column-label"] input, input[data-testid="column-label"]')!
    input.value = value
    input.dispatchEvent(new Event('input'))
    await flushPromises()
  }

  // "Built-in and custom attributes"
  it('offers every system field and every mapped property', async () => {
    const { wrapper } = await open(CATALOG_PATH)
    await configure(wrapper)
    expect(rows().map((candidate) => candidate.querySelector('[data-testid="column-name"]')!.textContent)).toEqual([
      'Name',
      'Description',
      'External ID',
      'Created',
      'Updated',
      'Version',
      'Status',
      'Vendor',
    ])
    wrapper.unmount()
  })

  // "Status before name" and "Own label"
  it('saves the order and the header and shows them in the table', async () => {
    const { wrapper, recorded } = await open(CATALOG_PATH)
    await configure(wrapper)
    await press(row('Status').querySelector('[data-testid="column-up"]'))
    for (let i = 0; i < 5; i++) {
      await press(row('Status').querySelector('[data-testid="column-up"]'))
    }
    await type('Status', 'Operational Status')
    await press(document.body.querySelector('[data-testid="columns-save"]'))
    const put = recorded.find((request) => request.method === 'PUT')
    const columns = (put?.body as { columns: { field?: string; propertyDefId?: string; displayLabel?: string | null; visible: boolean }[] }).columns
    expect(columns[0]).toMatchObject({ propertyDefId: STATUS_DEFINITION.id, displayLabel: 'Operational Status', visible: true })
    expect(columns[1]).toMatchObject({ field: 'name', visible: true })
    expect(columns.find((column) => column.propertyDefId === VENDOR_DEFINITION.id)?.visible).toBe(false)
    expect(headers(wrapper)).toEqual(['Operational Status', 'Name'])
    wrapper.unmount()
  })

  // "Hidden column"
  it('hides a column but keeps it in the configuration', async () => {
    const catalog = { ...CATALOG, columns: [{ field: 'name' }, { field: 'description', visible: false }, { propertyDefId: VENDOR_DEFINITION.id }] }
    const { wrapper } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog })
    expect(headers(wrapper)).toEqual(['Name', 'Vendor'])
    await configure(wrapper)
    const description = row('Description').querySelector<HTMLButtonElement>('[data-testid="column-visible"] button, button[data-testid="column-visible"]')
    expect(description?.getAttribute('aria-checked') ?? description?.getAttribute('data-state')).toMatch(/false|unchecked/)
    wrapper.unmount()
  })

  // "No visible column"
  it('blocks saving while no column is visible', async () => {
    const catalog = { ...CATALOG, columns: [{ field: 'name' }] }
    const { wrapper, recorded } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog })
    await configure(wrapper)
    await toggle('Name', 'column-visible')
    expect(document.body.querySelector('[data-testid="columns-none-visible"]')?.textContent).toContain('At least one column must be visible')
    expect(document.body.querySelector<HTMLButtonElement>('[data-testid="columns-save"]')?.disabled).toBe(true)
    expect(recorded.some((request) => request.method === 'PUT')).toBe(false)
    wrapper.unmount()
  })

  // "Reader without catalog management" and "Own personal catalog"
  it('offers the configuration for an instance catalog only with catalog management', async () => {
    const { wrapper } = await open(CATALOG_PATH, APPLICATIONS, undefined, { me: ME_VIEWER })
    expect(wrapper.find('[data-testid="configure-columns"]').exists()).toBe(false)
    const personal = { ...CATALOG, ...CATALOGS[2], id: CATALOG.id }
    const { wrapper: own } = await open(CATALOG_PATH, APPLICATIONS, undefined, { me: ME_VIEWER, catalog: personal })
    expect(own.find('[data-testid="configure-columns"]').exists()).toBe(true)
  })
})


describe('CatalogPage joins', () => {
  const WITH_JOIN = { ...CATALOG, joinDefinitions: [INTERFACES_JOIN] }

  function select(wrapper: VueWrapper, testId: string) {
    const found = wrapper
      .findComponent(CatalogJoinsDialog)
      .findAllComponents({ name: 'Select' })
      .find((component) => component.vm.$attrs['data-testid'] === testId)
    if (!found) {
      throw new Error(`no select ${testId}`)
    }
    return found
  }

  function itemLabels(wrapper: VueWrapper, testId: string): string[] {
    return (select(wrapper, testId).props('items') as { label: string }[]).map((item) => item.label)
  }

  async function choose(wrapper: VueWrapper, testId: string, value: string) {
    select(wrapper, testId).vm.$emit('update:modelValue', value)
    await flushPromises()
  }

  async function press(testId: string) {
    document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click()
    await flushPromises()
  }

  function joinCells(wrapper: VueWrapper): string[][] {
    return wrapper.findAll('[data-testid="join-cell"]').map((cell) => cell.findAll('li').map((item) => item.text()))
  }

  // catalog/catalog-joins "Interfaces of an application" and "Only connections and reachable targets offered"
  it('adds a join in steps with only connections and reachable targets', async () => {
    const { wrapper, recorded } = await open(CATALOG_PATH)
    await click(wrapper, '[data-testid="configure-joins"]')
    expect(document.body.querySelector('[data-testid="joins-empty"]')?.textContent).toContain('no joins')
    await press('join-add')
    expect(itemLabels(wrapper, 'join-connection')).toEqual(['Data Flow'])
    await choose(wrapper, 'join-connection', DATA_FLOW.id)
    expect(itemLabels(wrapper, 'join-direction')).toEqual(['Outgoing', 'Both directions'])
    await choose(wrapper, 'join-direction', 'outbound')
    expect(itemLabels(wrapper, 'join-target')).toEqual(['Interface'])
    await choose(wrapper, 'join-target', INTERFACE.id)
    const columns = [...document.body.querySelectorAll('[data-testid="join-column-name"]')].map((name) => name.textContent)
    expect(columns).toContain('Protocol')
    const protocol = [...document.body.querySelectorAll<HTMLElement>('[data-testid="join-column"]')].find(
      (row) => row.querySelector('[data-testid="join-column-name"]')?.textContent === 'Protocol',
    )!
    protocol.querySelector<HTMLElement>('[data-testid="join-column-visible"] button, button[data-testid="join-column-visible"]')!.click()
    await flushPromises()
    await press('join-save')
    const post = recorded.find((request) => request.method === 'POST')!
    expect(post.body).toMatchObject({
      name: 'Interface',
      connectionMetatypeId: DATA_FLOW.id,
      joinDirection: 'outbound',
      targetMetatypeId: INTERFACE.id,
      defaultJoinMode: null,
    })
    const sent = (post.body as { targetColumns: { field?: string; propertyDefId?: string; visible: boolean }[] }).targetColumns
    expect(sent.filter((column) => column.visible)).toEqual([
      expect.objectContaining({ field: 'name' }),
      expect.objectContaining({ propertyDefId: PROTOCOL_DEFINITION.id }),
    ])
    expect(document.body.querySelector('[data-testid="join-name"]')?.textContent).toBe('Interface')
    expect(joinCells(wrapper)[0]).toEqual(['REST API', 'SFTP Export'])
    wrapper.unmount()
  })

  // catalog/catalog-query "Aggregated interfaces", "Application without interfaces", "Switch to rows" and "Back to compact"
  it('switches a join to rows and back without changing the catalog', async () => {
    const { wrapper, router, recorded } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog: WITH_JOIN })
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(joinCells(wrapper).filter((_, index) => index % 2 === 0)).toEqual([['REST API', 'SFTP Export'], [], ['LDAP']])
    await click(wrapper, '[data-testid="join-mode-rows"]')
    expect(router.currentRoute.value.query.mode).toEqual([`${INTERFACES_JOIN.id}:rows`])
    expect(names(wrapper)).toEqual(['CRM System', 'CRM System', 'ERP', 'Intranet'])
    expect(recorded.at(-1)?.query[`joinMode[${INTERFACES_JOIN.id}]`]).toBe('rows')
    await click(wrapper, '[data-testid="join-mode-aggregate"]')
    expect(router.currentRoute.value.query.mode).toBeUndefined()
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    expect(recorded.some((request) => request.method !== 'GET' && request.method !== undefined)).toBe(false)
    wrapper.unmount()
  })

  // catalog/catalog-query "Sort by a join column"
  it('sorts by a join column in rows mode only', async () => {
    const { wrapper, router } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog: WITH_JOIN })
    const key = `${INTERFACES_JOIN.id}.protocol`
    expect(wrapper.find(`[data-testid="sort-${key}"]`).exists()).toBe(false)
    await click(wrapper, '[data-testid="join-mode-rows"]')
    await click(wrapper, `[data-testid="sort-${key}"]`)
    expect(router.currentRoute.value.query.sort).toBe(`${key}:asc`)
    expect(names(wrapper)).toEqual(['Intranet', 'CRM System', 'CRM System', 'ERP'])
    // compact again: the sort by the join ends
    await click(wrapper, '[data-testid="join-mode-aggregate"]')
    expect(router.currentRoute.value.query.sort).toBeUndefined()
    wrapper.unmount()
  })

  // catalog/catalog-joins "Remove a join"
  it('removes a join', async () => {
    const { wrapper, recorded } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog: WITH_JOIN })
    await click(wrapper, '[data-testid="configure-joins"]')
    await press('join-remove')
    expect(recorded.some((request) => request.method === 'DELETE' && request.path.endsWith(`/joins/${INTERFACES_JOIN.id}`))).toBe(true)
    expect(wrapper.find(`[data-testid="join-header-${INTERFACES_JOIN.id}"]`).exists()).toBe(false)
    wrapper.unmount()
  })

  // catalog/catalog-query "Unknown join": an old link with a removed join
  it('drops an unknown join from the URL', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?mode=f0000000-0000-4000-8000-00000000dead:rows`)
    expect(router.currentRoute.value.query.mode).toBeUndefined()
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    wrapper.unmount()
  })

  // catalog/catalog-joins "Who configures the joins"
  it('offers no joins configuration to a reader of an instance catalog', async () => {
    const { wrapper } = await open(CATALOG_PATH, APPLICATIONS, undefined, { me: ME_VIEWER, catalog: WITH_JOIN })
    expect(wrapper.find('[data-testid="configure-joins"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="join-mode-rows"]').exists()).toBe(true)
    wrapper.unmount()
  })
})

describe('CatalogPage connection catalogs', () => {
  const FLOW_PATH = `/catalogs/${FLOW_CATALOG.id}`

  function headers(wrapper: VueWrapper): string[] {
    return wrapper.findAll('[data-testid="catalog-table"] thead th').map((th) => th.text())
  }

  function cells(wrapper: VueWrapper, column: number): string[] {
    return wrapper.findAll('[data-testid="catalog-table"] tbody tr').map((row) => row.findAll('td')[column]!.text())
  }

  function columnNames(): string[] {
    return [...document.body.querySelectorAll('[data-testid="column-row"] [data-testid="column-name"]')].map((name) => name.textContent ?? '')
  }

  async function openFlows(catalog: unknown = FLOW_CATALOG) {
    return open(FLOW_PATH, FLOWS, undefined, { catalog, metatypes: METATYPES_WITH_FLOWS })
  }

  // catalog/catalog-query "Data flow catalog" and "Default columns of a connection catalog"
  it('lists the flows with their source and target', async () => {
    const { wrapper } = await openFlows()
    expect(headers(wrapper)).toEqual(['Source', 'Name', 'Target'])
    expect(cells(wrapper, 1)).toEqual(['Invoices', 'Leads', 'Orders'])
    expect(cells(wrapper, 0)).toEqual(['ERP', 'Intranet', 'CRM System'])
    expect(cells(wrapper, 2)).toEqual(['Accounting', 'CRM System', 'ERP'])
    wrapper.unmount()
  })

  // catalog/catalog-query "Sort by source"
  it('sorts by the source and keeps the sort in the URL', async () => {
    const { wrapper, router, recorded } = await openFlows()
    await click(wrapper, '[data-testid="sort-source.name"]')
    expect(router.currentRoute.value.query.sort).toBe('source.name:asc')
    expect(recorded.at(-1)?.query.sort).toBe('source.name:asc')
    expect(cells(wrapper, 0)).toEqual(['CRM System', 'ERP', 'Intranet'])
    wrapper.unmount()
  })

  // catalog/catalog-columns "Attributes of source and target"
  it('offers and saves the attributes of the source and the target', async () => {
    const { wrapper, recorded } = await openFlows()
    await click(wrapper, '[data-testid="configure-columns"]')
    const names = columnNames()
    expect(names.slice(0, 3)).toEqual(['Source: Name', 'Name', 'Target: Name'])
    expect(names).toContain('Source: Vendor')
    expect(names).toContain('Target: Vendor')
    expect(names).toContain('Target: Description')
    const vendor = [...document.body.querySelectorAll<HTMLElement>('[data-testid="column-row"]')].find(
      (row) => row.querySelector('[data-testid="column-name"]')?.textContent === 'Target: Vendor',
    )!
    vendor.querySelector<HTMLElement>('[data-testid="column-visible"] button, button[data-testid="column-visible"]')!.click()
    await flushPromises()
    document.body.querySelector<HTMLElement>('[data-testid="columns-save"]')!.click()
    await flushPromises()
    const put = recorded.find((request) => request.method === 'PUT')
    const columns = (put?.body as { columns: { endpoint?: string; field?: string; propertyDefId?: string; visible: boolean }[] }).columns
    expect(columns[0]).toMatchObject({ endpoint: 'source', field: 'name', visible: true })
    expect(columns[1]).toMatchObject({ field: 'name', visible: true })
    expect(columns[1]!.endpoint).toBeUndefined()
    expect(columns.find((column) => column.endpoint === 'target' && column.propertyDefId === VENDOR_DEFINITION.id)?.visible).toBe(true)
    expect(headers(wrapper)).toEqual(['Source', 'Name', 'Target', 'Target: Vendor'])
    expect(cells(wrapper, 3)).toEqual(['', 'Salesforce', 'SAP'])
    wrapper.unmount()
  })

  // catalog/catalog-joins "Connection catalog without joins"
  it('explains that no join starts at the connections', async () => {
    const { wrapper } = await openFlows()
    await click(wrapper, '[data-testid="configure-joins"]')
    expect(document.body.querySelector('[data-testid="joins-connection-catalog"]')?.textContent).toContain(
      'This catalog lists connections. No connection type starts or ends at them',
    )
    wrapper.unmount()
    const { wrapper: elements } = await open(CATALOG_PATH)
    await click(elements, '[data-testid="configure-joins"]')
    expect(document.body.querySelector('[data-testid="joins-connection-catalog"]')).toBeNull()
    elements.unmount()
  })
})

describe('CatalogPage filters', () => {
  const WITH_JOIN = { ...CATALOG, joinDefinitions: [INTERFACES_JOIN] }

  function select(wrapper: VueWrapper, testId: string) {
    const found = wrapper.findAllComponents({ name: 'Select' }).find((component) => component.vm.$attrs['data-testid'] === testId)
    if (!found) {
      throw new Error(`no select ${testId}`)
    }
    return found
  }

  function itemLabels(wrapper: VueWrapper, testId: string): string[] {
    return (select(wrapper, testId).props('items') as ({ label: string } | { label: string }[])[])
      .flat()
      .map((item) => item.label)
  }

  async function choose(wrapper: VueWrapper, testId: string, value: string) {
    select(wrapper, testId).vm.$emit('update:modelValue', value)
    await flushPromises()
  }

  async function press(testId: string) {
    document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click()
    await flushPromises()
  }

  async function type(testId: string, value: string) {
    const input = document.body.querySelector<HTMLInputElement>(`[data-testid="${testId}"] input, input[data-testid="${testId}"]`)!
    input.value = value
    input.dispatchEvent(new Event('input'))
    await flushPromises()
  }

  function chips(wrapper: VueWrapper): string[] {
    return wrapper.findAll('[data-testid="filter-chip-label"]').map((chip) => chip.text())
  }

  function joinCells(wrapper: VueWrapper): string[][] {
    return wrapper.findAll('[data-testid="join-cell"]').map((cell) => cell.findAll('li').map((item) => item.text()))
  }

  // catalog/catalog-filters "Active applications only"
  it('adds a filter in three steps and shows only the matching rows', async () => {
    const { wrapper, router, recorded } = await open(CATALOG_PATH)
    await click(wrapper, '[data-testid="filter-add"]')
    await choose(wrapper, 'filter-column', 'status')
    await choose(wrapper, 'filter-operator', 'eq')
    expect(itemLabels(wrapper, 'filter-value-choice')).toEqual(['planned', 'active'])
    await choose(wrapper, 'filter-value-choice', 'active')
    await press('filter-apply')
    expect(router.currentRoute.value.query.filter).toEqual(['status:eq:active'])
    expect(recorded.at(-1)?.filters).toEqual(['status:eq:active'])
    expect(names(wrapper)).toEqual(['CRM System', 'ERP'])
    expect(chips(wrapper)).toEqual(['Status equals active'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Remove a filter"
  it('removes a filter chip and shows all rows again', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?filter=status:eq:active`)
    expect(names(wrapper)).toEqual(['CRM System', 'ERP'])
    await click(wrapper, '[data-testid="filter-chip-remove"]')
    expect(router.currentRoute.value.query.filter).toBeUndefined()
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Operators for the name"
  it('offers the operators of the column type', async () => {
    const { wrapper } = await open(CATALOG_PATH)
    await click(wrapper, '[data-testid="filter-add"]')
    await choose(wrapper, 'filter-column', 'name')
    expect(itemLabels(wrapper, 'filter-operator')).toEqual(
      expect.arrayContaining(['equals', 'not equals', 'contains', 'starts with', 'is empty', 'is not empty']),
    )
    expect(itemLabels(wrapper, 'filter-operator')).not.toContain('greater than')
    await choose(wrapper, 'filter-column', 'status')
    expect(itemLabels(wrapper, 'filter-operator')).toEqual(['equals', 'not equals', 'is one of', 'is none of', 'is empty', 'is not empty'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Applications with a REST interface"
  it('filters by a join column and narrows its results', async () => {
    const { wrapper } = await open(`${CATALOG_PATH}?filter=${INTERFACES_JOIN.id}.protocol:eq:rest`, APPLICATIONS, undefined, {
      catalog: WITH_JOIN,
    })
    expect(names(wrapper)).toEqual(['CRM System'])
    expect(joinCells(wrapper)[0]).toEqual(['REST API'])
    expect(chips(wrapper)).toEqual(['Interfaces · Protocol equals rest'])
    await click(wrapper, '[data-testid="filter-add"]')
    expect(itemLabels(wrapper, 'filter-column')).toEqual(['Name', 'Status', 'Interfaces', 'Name', 'Protocol'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Reload keeps the filter"
  it('keeps the filter after a reload', async () => {
    const first = await open(`${CATALOG_PATH}?filter=status:eq:active`)
    const location = first.router.currentRoute.value.fullPath
    first.wrapper.unmount()
    const { wrapper, recorded } = await open(location)
    expect(chips(wrapper)).toEqual(['Status equals active'])
    expect(names(wrapper)).toEqual(['CRM System', 'ERP'])
    expect(recorded.find((request) => request.path.endsWith('/query'))?.filters).toEqual(['status:eq:active'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Both conditions" and "Either condition"
  it('combines filters with AND or OR', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?filter=status:eq:planned&filter=name:startsWith:crm`)
    expect(names(wrapper)).toEqual([])
    await choose(wrapper, 'filter-mode', 'or')
    expect(router.currentRoute.value.query.filterMode).toBe('or')
    expect(names(wrapper)).toEqual(['CRM System', 'Intranet'])
    await choose(wrapper, 'filter-mode', 'and')
    expect(router.currentRoute.value.query.filterMode).toBeUndefined()
    wrapper.unmount()
  })

  // catalog/catalog-filters "Filters in the URL": an unknown column answers 400
  it('drops filters the server rejects', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?filter=nonexistent:eq:x&sort=name:desc`)
    expect(router.currentRoute.value.query.filter).toBeUndefined()
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Active systems only"
  it('saves the filters of the bar as a saved filter', async () => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?filter=status:eq:active`)
    await click(wrapper, '[data-testid="filter-save"]')
    await type('filter-save-name', 'Active systems only')
    await press('filter-save-submit')
    const post = recorded.find((request) => request.method === 'POST')!
    expect(post.path).toBe(`/api/v1/catalogs/${CATALOG.id}/filters`)
    expect(post.body).toEqual({
      name: 'Active systems only',
      description: null,
      logicalOperator: 'and',
      expressions: [{ attributePath: 'status', operator: 'eq', value: 'active' }],
    })
    expect(itemLabels(wrapper, 'saved-filters')).toEqual(['Active systems only'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Save a filter": a taken name
  it('reports a taken name', async () => {
    const { wrapper } = await open(`${CATALOG_PATH}?filter=status:eq:active`, APPLICATIONS, undefined, {
      catalog: { ...CATALOG, savedFilters: [ACTIVE_SYSTEMS] },
    })
    await click(wrapper, '[data-testid="filter-save"]')
    await type('filter-save-name', 'active systems ONLY')
    await press('filter-save-submit')
    expect(document.body.textContent).toContain('already has a saved filter with this name')
    wrapper.unmount()
  })

  // catalog/catalog-filters "Activate a saved filter"
  it('loads a saved filter into the bar', async () => {
    const { wrapper, router } = await open(CATALOG_PATH, APPLICATIONS, undefined, {
      catalog: { ...CATALOG, savedFilters: [ACTIVE_SYSTEMS, SAP_ONLY] },
    })
    const items = select(wrapper, 'saved-filters').props('items') as { label: string; disabled: boolean }[]
    expect(items.map((item) => [item.label, item.disabled])).toEqual([
      ['Active systems only', false],
      ['SAP only (not available)', true],
    ])
    await choose(wrapper, 'saved-filters', ACTIVE_SYSTEMS.id)
    expect(router.currentRoute.value.query.filter).toEqual(['status:eq:active'])
    expect(chips(wrapper)).toEqual(['Status equals active'])
    expect(names(wrapper)).toEqual(['CRM System', 'ERP'])
    wrapper.unmount()
  })

  // catalog/catalog-filters "Reader of an instance catalog"
  it('offers no saving to a reader without the right to configure', async () => {
    const { wrapper } = await open(`${CATALOG_PATH}?filter=status:eq:active`, APPLICATIONS, undefined, {
      me: ME_VIEWER,
      catalog: { ...CATALOG, savedFilters: [ACTIVE_SYSTEMS] },
    })
    expect(wrapper.find('[data-testid="filter-save"]').exists()).toBe(false)
    expect(itemLabels(wrapper, 'saved-filters')).toEqual(['Active systems only'])
    wrapper.unmount()
  })
})

describe('CatalogPage views', () => {
  function select(wrapper: VueWrapper, testId: string) {
    const found = wrapper.findAllComponents({ name: 'Select' }).find((component) => component.vm.$attrs['data-testid'] === testId)
    if (!found) {
      throw new Error(`no select ${testId}`)
    }
    return found
  }

  function itemLabels(wrapper: VueWrapper, testId: string): string[] {
    return (select(wrapper, testId).props('items') as { label: string }[]).map((item) => item.label)
  }

  async function choose(wrapper: VueWrapper, testId: string, value: string) {
    select(wrapper, testId).vm.$emit('update:modelValue', value)
    await flushPromises()
  }

  async function press(testId: string) {
    document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click()
    await flushPromises()
  }

  async function type(testId: string, value: string) {
    const input = document.body.querySelector<HTMLInputElement>(`[data-testid="${testId}"] input, input[data-testid="${testId}"]`)!
    input.value = value
    input.dispatchEvent(new Event('input'))
    await flushPromises()
  }

  function headers(wrapper: VueWrapper): string[] {
    return wrapper.findAll('[data-testid="catalog-table"] thead th').map((th) => th.text())
  }

  const WITH_VIEWS = { ...CATALOG, savedViews: [COMPACT_VIEW, FULL_VIEW] }
  const WITH_DEFAULT = { ...CATALOG, savedViews: [{ ...COMPACT_VIEW, isDefault: true }, FULL_VIEW] }

  // catalog/catalog-views "Switch to the full view"
  it('switches between views and keeps the view in the URL', async () => {
    const { wrapper, router, recorded } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog: WITH_VIEWS })
    expect(itemLabels(wrapper, 'view-select')).toEqual(['All columns', 'Compact view', 'Full view'])
    await choose(wrapper, 'view-select', FULL_VIEW.id)
    expect(router.currentRoute.value.query).toMatchObject({ view: FULL_VIEW.id, sort: 'name:desc' })
    expect(names(wrapper)).toEqual(['Intranet', 'ERP', 'CRM System'])
    await choose(wrapper, 'view-select', COMPACT_VIEW.id)
    expect(router.currentRoute.value.query).toMatchObject({ view: COMPACT_VIEW.id, filter: ['status:eq:active'] })
    expect(router.currentRoute.value.query.sort).toBeUndefined()
    expect(recorded.at(-1)?.query.view).toBe(COMPACT_VIEW.id)
    expect(headers(wrapper)[0]).toContain('Status')
    expect(wrapper.findAll('[data-testid="catalog-table"] tbody tr')).toHaveLength(2)
    await choose(wrapper, 'view-select', '__all__')
    expect(router.currentRoute.value.query.view).toBeUndefined()
    expect(router.currentRoute.value.query.filter).toEqual(['status:eq:active'])
    wrapper.unmount()
  })

  // catalog/catalog-views "Default view applied"
  it('applies the default view when the catalog opens without view state', async () => {
    const { wrapper, router } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog: WITH_DEFAULT })
    expect(router.currentRoute.value.query).toMatchObject({ view: COMPACT_VIEW.id, filter: ['status:eq:active'] })
    expect(headers(wrapper).map((header) => header.replace(/[↑↓]/g, '').trim())).toEqual(['Status', 'Name'])
    expect(wrapper.findAll('[data-testid="filter-chip-label"]').map((chip) => chip.text())).toEqual(['Status equals active'])
    expect(itemLabels(wrapper, 'view-select')).toContain('Compact view (default)')
    wrapper.unmount()
  })

  // catalog/catalog-views "Shared link keeps its state"
  it('keeps the state of a link instead of the default view', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?sort=name:desc`, APPLICATIONS, undefined, { catalog: WITH_DEFAULT })
    expect(router.currentRoute.value.query.view).toBeUndefined()
    expect(names(wrapper)).toEqual(['Intranet', 'ERP', 'CRM System'])
    wrapper.unmount()
  })

  // catalog/catalog-views "No default view"
  it('shows the configured columns without a default view', async () => {
    const { wrapper, router } = await open(CATALOG_PATH, APPLICATIONS, undefined, { catalog: WITH_VIEWS })
    expect(router.currentRoute.value.query).toEqual({})
    expect(names(wrapper)).toEqual(['CRM System', 'ERP', 'Intranet'])
    wrapper.unmount()
  })

  // catalog/catalog-views "Use a view": reload and a changed view
  it('restores a view after a reload and marks a changed view', async () => {
    const { wrapper } = await open(`${CATALOG_PATH}?view=${FULL_VIEW.id}&sort=name:desc`, APPLICATIONS, undefined, { catalog: WITH_VIEWS })
    expect(select(wrapper, 'view-select').props('modelValue')).toBe(FULL_VIEW.id)
    expect(wrapper.find('[data-testid="view-changed"]').exists()).toBe(false)
    await click(wrapper, '[data-testid="sort-name"]')
    expect(wrapper.find('[data-testid="view-changed"]').exists()).toBe(true)
    wrapper.unmount()
  })

  // catalog/catalog-views "Unknown view"
  it('drops a view the catalog does not have and keeps the rest', async () => {
    const { wrapper, router } = await open(`${CATALOG_PATH}?view=f2000000-0000-4000-8000-00000000ffff&sort=name:desc`, APPLICATIONS, undefined, {
      catalog: WITH_VIEWS,
    })
    expect(router.currentRoute.value.query.view).toBeUndefined()
    expect(router.currentRoute.value.query.sort).toBe('name:desc')
    expect(names(wrapper)).toEqual(['Intranet', 'ERP', 'CRM System'])
    wrapper.unmount()
  })

  // catalog/catalog-views "Compact view"
  it('saves the current table as a view', async () => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?filter=status:eq:active&sort=name:desc`)
    await click(wrapper, '[data-testid="view-save"]')
    await type('view-save-name', 'Compact view')
    await press('view-save-submit')
    const post = recorded.find((request) => request.method === 'POST')!
    expect(post.path).toBe(`/api/v1/catalogs/${CATALOG.id}/views`)
    expect(post.body).toEqual({
      name: 'Compact view',
      description: null,
      columnOrder: ['name', 'status'],
      filters: [{ attributePath: 'status', operator: 'eq', value: 'active' }],
      filterMode: 'and',
      joinModeOverrides: [],
      sort: { column: 'name', direction: 'desc' },
      isDefault: false,
    })
    expect(itemLabels(wrapper, 'view-select')).toEqual(['All columns', 'Compact view'])
    wrapper.unmount()
  })

  // catalog/catalog-views "Default switch"
  it('makes another view the default', async () => {
    const { wrapper, recorded } = await open(`${CATALOG_PATH}?sort=name:asc`, APPLICATIONS, undefined, { catalog: WITH_DEFAULT })
    await click(wrapper, '[data-testid="view-manage"]')
    await press('view-make-default')
    const put = recorded.find((request) => request.method === 'PUT')!
    expect(put.path).toBe(`/api/v1/catalogs/${CATALOG.id}/views/${FULL_VIEW.id}`)
    expect(put.body).toMatchObject({ name: 'Full view', isDefault: true })
    expect(itemLabels(wrapper, 'view-select')).toEqual(['All columns', 'Compact view', 'Full view (default)'])
    await press('view-remove')
    expect(recorded.find((request) => request.method === 'DELETE')?.path).toBe(`/api/v1/catalogs/${CATALOG.id}/views/${COMPACT_VIEW.id}`)
    wrapper.unmount()
  })

  // catalog/catalog-views "Who changes views"
  it('offers no saving or managing to a reader without the right to configure', async () => {
    const { wrapper } = await open(CATALOG_PATH, APPLICATIONS, undefined, { me: ME_VIEWER, catalog: WITH_VIEWS })
    expect(wrapper.find('[data-testid="view-save"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="view-manage"]').exists()).toBe(false)
    expect(itemLabels(wrapper, 'view-select')).toEqual(['All columns', 'Compact view', 'Full view'])
    wrapper.unmount()
  })
})


// navigation/navigation-tree "Place a catalog" and "Navigation sidebar" (US-054 AC1, AC5)
describe('CatalogPage navigation', () => {
  beforeEach(() => {
    clearTree()
  })

  async function placing(me: unknown = ME_ARCHITECT) {
    const recorded: TreeRequest[] = []
    const fetchFn = navigationApi({ recorded, fallback: catalogApi(APPLICATIONS, { me }) })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/catalogs/:id', component: CatalogPage, props: (route) => ({ id: route.params.id, fetchFn }) }],
    })
    await router.push(CATALOG_PATH)
    const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [ui, router] }, attachTo: document.body })
    await flushPromises()
    return { wrapper, recorded }
  }

  function folderSelect(wrapper: VueWrapper) {
    const found = wrapper.findAllComponents({ name: 'Select' }).find((component) => component.vm.$attrs['data-testid'] === 'place-folder')
    if (!found) {
      throw new Error('no folder select')
    }
    return found
  }

  async function press(testId: string) {
    document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click()
    await flushPromises()
  }

  async function label(value: string) {
    const input = document.body.querySelector<HTMLInputElement>('[data-testid="place-label"]')!
    input.value = value
    input.dispatchEvent(new Event('input'))
    await flushPromises()
  }

  // "Place under a folder"
  it('places the catalog in a folder chosen by its path', async () => {
    const { wrapper, recorded } = await placing()
    await click(wrapper, '[data-testid="place-in-navigation"]')
    expect((folderSelect(wrapper).props('items') as { label: string }[]).map((item) => item.label)).toEqual([
      'Repository',
      'Repository / Compliance',
      'Repository / IT Landscape',
      'Repository / IT Landscape / Applications',
    ])
    expect(folderSelect(wrapper).props('modelValue')).toBe(ROOT_ID)
    folderSelect(wrapper).vm.$emit('update:modelValue', LANDSCAPE_ID)
    await flushPromises()
    // the inventory is in "IT Landscape" already; "Compliance Overview" is not the page's catalog
    await press('place-submit')
    expect(recorded.at(-1)).toEqual({
      path: `/api/v1/tree-nodes/${LANDSCAPE_ID}/items`,
      method: 'POST',
      body: { itemType: 'catalog', referenceId: CATALOG.id, displayLabel: null },
    })
    expect(document.body.querySelector('[data-testid="place-error"]')?.textContent).toBe('The catalog is already in this folder.')
    wrapper.unmount()
  })

  // "Display label"
  it('places the catalog with a display label', async () => {
    const { wrapper, recorded } = await placing()
    await click(wrapper, '[data-testid="place-in-navigation"]')
    await label('  System Inventory ')
    await press('place-submit')
    expect(recorded.at(-1)).toEqual({
      path: `/api/v1/tree-nodes/${ROOT_ID}/items`,
      method: 'POST',
      body: { itemType: 'catalog', referenceId: CATALOG.id, displayLabel: 'System Inventory' },
    })
    expect(wrapper.get('[data-testid="place-done"]').text()).toBe('Placed in Repository.')
    expect(document.body.querySelector('[data-testid="place-form"]')).toBeNull()
    wrapper.unmount()
  })

  // "Reader without the right sees no actions"
  it('offers no placing without viewpoint:manage', async () => {
    const { wrapper } = await placing(ME_VIEWER)
    expect(wrapper.find('[data-testid="place-in-navigation"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
