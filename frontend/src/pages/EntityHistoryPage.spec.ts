import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { session } from '../session/store'
import { HISTORY_IDS, historyApi, KURT, manyVersions, ME_PORTAL_ONLY, MICHAEL } from '../test/history'
import { testRouter } from '../test/router'
import EntityHistoryPage from './EntityHistoryPage.vue'

// spec repository/entity-history "Page \"Change history\"", "Paging and empty state of the page"
// and "Opening the history from the catalog" (US-098 AC1–AC3; design D5 of us-098-entity-history)

async function mountPage(options: { id?: number; from?: string; me?: unknown; entities?: NonNullable<Parameters<typeof historyApi>[0]>['entities'] } = {}) {
  const router = testRouter()
  const id = options.id ?? HISTORY_IDS.crm
  await router.push({ path: `/entities/${id}/history`, query: options.from ? { from: options.from } : {} })
  const api = historyApi({ me: options.me, entities: options.entities })
  const wrapper = mount(EntityHistoryPage, { props: { id: String(id), fetchFn: api.fetch }, global: { plugins: [ui, router] } })
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
})
