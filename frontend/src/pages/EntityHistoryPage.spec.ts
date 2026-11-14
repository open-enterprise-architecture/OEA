import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { HISTORY_IDS, historyApi, KURT, manyVersions, ME_PORTAL_ONLY, MICHAEL } from '../test/history'
import { testRouter } from '../test/router'
import EntityHistoryPage from './EntityHistoryPage.vue'

// spec repository/entity-history "Page \"Change history\"", "Paging and empty state of the page"
// and "Opening the history from the catalog" (US-098 AC1–AC3; design D5 of us-098-entity-history),
// and spec repository/entity-versions "Diff and state on the page \"Change history\"" and "Compare
// mode on the page \"Change history\"" (US-099, US-100; design D5 of us-099-version-diff)

async function mountPage(
  options: {
    id?: number
    from?: string
    me?: unknown
    entities?: NonNullable<Parameters<typeof historyApi>[0]>['entities']
    wrap?: (fetchFn: typeof fetch) => typeof fetch
  } = {},
) {
  const router = testRouter()
  const id = options.id ?? HISTORY_IDS.crm
  await router.push({ path: `/entities/${id}/history`, query: options.from ? { from: options.from } : {} })
  const api = historyApi({ me: options.me, entities: options.entities })
  const fetchFn = options.wrap ? options.wrap(api.fetch) : api.fetch
  const wrapper = mount(EntityHistoryPage, { props: { id: String(id), fetchFn }, global: { plugins: [ui, router] } })
  await flushPromises()
  return Object.assign(wrapper, { api, router })
}

function numbers(wrapper: Awaited<ReturnType<typeof mountPage>>): number[] {
  return wrapper.findAll('[data-testid^="history-version-"]').map((article) => Number(article.attributes('data-testid')!.replace('history-version-', '')))
}

describe('EntityHistoryPage', () => {
  beforeEach(() => {
    session.me = undefined
    session.loaded = false
  })

  it('shows the versions newest first, with version 1 as "Created"', async () => {
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="history-title"]').text()).toBe('Change history: CRM System')
    expect(numbers(wrapper)).toEqual([5, 4, 3, 2, 1])
    expect(wrapper.get('[data-testid="history-version-1"] h2').text()).toBe('Created')
    expect(wrapper.get('[data-testid="history-version-4"] h2').text()).toBe('Version 4')
    expect(wrapper.get('[data-testid="history-version-4"] [data-testid="history-actor"]').text()).toBe('Michael')
    expect(wrapper.get('[data-testid="history-version-4"] [data-testid="history-change-name"]').text()).toBe('Name')
  })

  it('shows a restricted field with the badge "Restricted" and no value', async () => {
    const wrapper = await mountPage()
    const restricted = wrapper.get(`[data-testid="history-version-4"] [data-testid="history-change-${HISTORY_IDS.cost}"]`)
    expect(restricted.get('[data-testid="history-restricted"]').text()).toBe('Investment Cost · Restricted')
    expect(wrapper.findAll('[data-testid="history-restricted"]')).toHaveLength(1)
  })

  it('filters by person and by period', async () => {
    const wrapper = await mountPage()
    wrapper.getComponent({ name: 'Select' }).vm.$emit('update:modelValue', String(MICHAEL.id))
    await flushPromises()
    expect(wrapper.api.queries.at(-1)!.get('actorId')).toBe(String(MICHAEL.id))
    expect(numbers(wrapper)).toEqual([5, 4, 2])

    await wrapper.get('[data-testid="history-from"]').setValue('2026-11-02')
    await wrapper.get('[data-testid="history-to"]').setValue('2026-11-02')
    await flushPromises()
    const query = wrapper.api.queries.at(-1)!
    expect(query.get('from')).toBe(new Date('2026-11-02T00:00:00').toISOString())
    expect(query.get('to')).toBe(new Date('2026-11-02T23:59:59.999').toISOString())
  })

  it('does not send a period whose start is after its end', async () => {
    const wrapper = await mountPage()
    const before = wrapper.api.queries.length
    await wrapper.get('[data-testid="history-from"]').setValue('2026-11-03')
    await wrapper.get('[data-testid="history-to"]').setValue('2026-11-02')
    await flushPromises()
    expect(wrapper.text()).toContain('The start must not be after the end.')
    // only the change of the start was sent, while the end was still empty
    expect(wrapper.api.queries.length).toBe(before + 1)
  })

  it('shows the empty state of an entity without changes', async () => {
    const wrapper = await mountPage({ id: HISTORY_IDS.billing })
    const created = new Date('2026-11-02T09:00:00Z')
    const day = `${created.getFullYear()}-${String(created.getMonth() + 1).padStart(2, '0')}-${String(created.getDate()).padStart(2, '0')}`
    expect(wrapper.get('[data-testid="history-unchanged"]').text()).toContain(`No changes since creation. Created on ${day} by ${KURT.name}.`)
    expect(wrapper.find('[data-testid="history-versions"]').exists()).toBe(false)
  })

  it.each([
    [51, 50, true],
    [50, 50, false],
  ])('with %i versions shows %i and "Load more" %s', async (count, shown, more) => {
    const wrapper = await mountPage({ id: 7, entities: { 7: { name: 'Large System', versions: manyVersions(count) } } })
    expect(numbers(wrapper)).toHaveLength(shown)
    expect(wrapper.find('[data-testid="history-more"]').exists()).toBe(more)
    if (more) {
      await wrapper.get('[data-testid="history-more"]').trigger('click')
      await flushPromises()
      expect(numbers(wrapper).at(-1)).toBe(1)
      expect(wrapper.api.queries.at(-1)!.get('cursor')).toBe('hcursor-1')
      expect(wrapper.find('[data-testid="history-more"]').exists()).toBe(false)
    }
  })

  it('links back to the catalog it was opened from', async () => {
    const wrapper = await mountPage({ from: '/catalogs/c0000000-0000-4000-8000-000000000001?page=2' })
    expect(wrapper.get('[data-testid="history-back"]').attributes('href')).toBe('/catalogs/c0000000-0000-4000-8000-000000000001?page=2')
  })

  it.each(['https://evil.example.org', '//evil.example.org', undefined])('falls back to the catalogs for the back link %s', async (from) => {
    const wrapper = await mountPage({ from })
    expect(wrapper.get('[data-testid="history-back"]').attributes('href')).toBe('/catalogs')
  })

  it('shows "Entity not found" for an unknown entity', async () => {
    const wrapper = await mountPage({ id: 999 })
    expect(wrapper.get('[data-testid="history-not-found"]').text()).toContain('Entity not found')
  })

  it('reads nothing without entity:read', async () => {
    const wrapper = await mountPage({ me: ME_PORTAL_ONLY })
    expect(wrapper.find('[data-testid="history-no-access"]').exists()).toBe(true)
    expect(wrapper.api.queries).toHaveLength(0)
  })

  describe('values of the versions', () => {
    async function select(wrapper: Awaited<ReturnType<typeof mountPage>>, version: number) {
      const root = wrapper.get(`[data-testid="history-select-${version}"]`)
      const box = root.attributes('role') === 'checkbox' ? root : root.get('[role="checkbox"]')
      await box.trigger('click')
      await flushPromises()
    }

    it('expands a version to its diff, loading it once', async () => {
      const wrapper = await mountPage()
      expect(wrapper.find('[data-testid="history-details-5"]').exists()).toBe(false)
      const toggle = wrapper.get('[data-testid="history-toggle-5"]')
      expect(toggle.attributes('aria-expanded')).toBe('false')
      await toggle.trigger('click')
      await flushPromises()
      expect(toggle.attributes('aria-expanded')).toBe('true')
      const row = wrapper.get(`[data-testid="history-details-5"] [data-testid="history-diff-${HISTORY_IDS.status}"]`)
      expect(row.text()).toContain('Lifecycle Status')
      expect(row.get('[data-testid="history-diff-old"]').text()).toBe('before: Active')
      expect(row.get('[data-testid="history-diff-old"]').classes()).toContain('line-through')
      expect(row.get('[data-testid="history-diff-new"]').text()).toBe('after: Retiring')
      expect(row.get('[data-testid="history-diff-new"]').classes()).toContain('text-success')
      expect(wrapper.findAll('[data-testid="history-details-5"] [data-testid^="history-diff-fd"]')).toHaveLength(1)
      // collapse and expand again: no second request
      await toggle.trigger('click')
      await toggle.trigger('click')
      await flushPromises()
      expect(wrapper.api.diffs).toEqual(['from=4&to=5'])
    })

    it('shows a restricted field of the diff without values', async () => {
      const wrapper = await mountPage()
      await wrapper.get('[data-testid="history-toggle-4"]').trigger('click')
      await flushPromises()
      const row = wrapper.get(`[data-testid="history-details-4"] [data-testid="history-diff-${HISTORY_IDS.cost}"]`)
      expect(row.find('[data-testid="history-diff-restricted"]').text()).toBe('Restricted')
      expect(row.find('[data-testid="history-diff-old"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="history-details-4"] [data-testid="history-diff-name"]').text()).toContain('CRM System')
    })

    it('offers the complete state on "Created", without a diff', async () => {
      const wrapper = await mountPage()
      await wrapper.get('[data-testid="history-toggle-1"]').trigger('click')
      await flushPromises()
      expect(wrapper.api.diffs).toEqual([])
      expect(wrapper.find('[data-testid="history-details-1"] [data-testid="history-diff"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="history-show-state-1"]').text()).toBe('Show complete state v1')
    })

    it('shows the complete state with "—" for an empty property', async () => {
      const wrapper = await mountPage()
      await wrapper.get('[data-testid="history-toggle-4"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="history-show-state-4"]').trigger('click')
      await flushPromises()
      const state = wrapper.get('[data-testid="history-state-4"]')
      expect(state.text()).toContain('Complete state of version 4')
      expect(state.get(`[data-testid="history-state-${HISTORY_IDS.owner}"]`).text()).toBe('—')
      expect(state.get(`[data-testid="history-state-${HISTORY_IDS.status}"]`).text()).toBe('Active')
      expect(state.get('[data-testid="history-state-isLogical"]').text()).toBe('Yes')
      expect(state.get(`[data-testid="history-state-${HISTORY_IDS.cost}"]`).text()).toBe('Restricted')
      expect(wrapper.get('[data-testid="history-show-state-4"]').text()).toBe('Hide complete state')
      expect(wrapper.api.states).toEqual([4])
    })

    it('compares two selected versions from the earlier to the later one', async () => {
      const wrapper = await mountPage()
      expect(wrapper.find('[data-testid="history-select-5"]').exists()).toBe(false)
      await wrapper.get('[data-testid="history-compare"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="history-compare-hint"]').text()).toBe('Select two versions to compare.')
      await select(wrapper, 5)
      await select(wrapper, 2)
      expect(wrapper.api.diffs).toEqual(['from=2&to=5'])
      const panel = wrapper.get('[data-testid="history-compare-panel"]')
      expect(panel.text()).toContain('Version 2 → Version 5')
      expect(panel.find('[data-testid="history-diff-name"]').exists()).toBe(true)
      expect(panel.find('[data-testid="history-diff-description"]').exists()).toBe(true)
    })

    it('replaces the version selected first with a third one', async () => {
      const wrapper = await mountPage()
      await wrapper.get('[data-testid="history-compare"]').trigger('click')
      await flushPromises()
      await select(wrapper, 5)
      await select(wrapper, 2)
      await select(wrapper, 3)
      expect(wrapper.api.diffs.at(-1)).toBe('from=2&to=3')
      expect(wrapper.get('[data-testid="history-compare-panel"]').text()).toContain('Version 2 → Version 3')
    })

    it('closes the panel when leaving the compare mode', async () => {
      const wrapper = await mountPage()
      await wrapper.get('[data-testid="history-compare"]').trigger('click')
      await flushPromises()
      await select(wrapper, 5)
      await select(wrapper, 4)
      await wrapper.get('[data-testid="history-compare"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="history-compare-panel"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="history-select-5"]').exists()).toBe(false)
      // entering again starts without a selection
      await wrapper.get('[data-testid="history-compare"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('[data-testid="history-compare-hint"]').exists()).toBe(true)
    })

    it('shows an error when the diff of a version cannot be loaded', async () => {
      const wrapper = await mountPage({
        wrap: (fetchFn) => async (input, init) => {
          const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
          if (url.includes('/diff')) {
            return new Response(JSON.stringify({ type: 'urn:oea:problem:not-found', title: 'Not found', status: 404 }), {
              status: 404,
              headers: { 'Content-Type': 'application/problem+json' },
            })
          }
          return fetchFn(input, init)
        },
      })
      await wrapper.get('[data-testid="history-toggle-3"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="history-details-3"] [data-testid="history-detail-failure"]').text()).toContain(
        'The values of this version could not be loaded.',
      )
    })
  })
})
