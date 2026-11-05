import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { setLanguage } from '../i18n'
import { loadLabels } from '../i18n/labels'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import {
  APPLICATIONS,
  CATALOG,
  CATALOGS,
  catalogApi,
  DATA_FLOW,
  INTERFACE,
  INTERFACES_JOIN,
  manyApplications,
  ME_ARCHITECT,
  ME_VIEWER,
  PROTOCOL_DEFINITION,
  STATUS_DEFINITION,
  VENDOR_DEFINITION,
  type Application,
  type CatalogRequest,
} from '../test/catalogs'
import CatalogJoinsDialog from '../components/CatalogJoinsDialog.vue'
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
  options: { me?: unknown; catalog?: unknown } = {},
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
