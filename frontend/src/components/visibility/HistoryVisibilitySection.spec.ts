import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { ROLES, VISIBILITY_IDS, visibilityApi } from '../../test/visibility'
import HistoryVisibilitySection from './HistoryVisibilitySection.vue'

// spec metamodel/history-visibility "Section \"Change history\" on the page \"Property
// visibility\"" (design D5 of us-098-entity-history)

const METATYPES = [
  { id: VISIBILITY_IDS.application, name: 'Application Component' },
  { id: VISIBILITY_IDS.actor, name: 'Business Actor' },
]

const mounted: VueWrapper[] = []

async function mountSection(options: Parameters<typeof visibilityApi>[0] = {}) {
  const api = visibilityApi(options)
  const wrapper = mount(HistoryVisibilitySection, {
    props: { roles: ROLES, metatypes: METATYPES, fetchFn: api.fetch },
    global: { plugins: [ui] },
    attachTo: document.body,
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, api }
}

function mode(wrapper: VueWrapper, role: string): string | undefined {
  const checked = wrapper.find(`[data-testid="history-visibility-mode-${role}"] [aria-checked="true"], [data-testid="history-visibility-mode-${role}"] [data-state="checked"]`)
  return checked.exists() ? (checked.attributes('value') ?? checked.element.closest('[value]')?.getAttribute('value') ?? undefined) : undefined
}

async function choose(wrapper: VueWrapper, role: string, value: string) {
  ;(wrapper.element.querySelector(`[data-testid="history-visibility-mode-${role}"] [value="${value}"]`) as HTMLElement).click()
  await flushPromises()
}

describe('HistoryVisibilitySection', () => {
  afterEach(() => {
    mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('shows every role as "Restricted" for a metatype without entries', async () => {
    const { wrapper } = await mountSection()
    for (const role of ROLES) {
      expect(mode(wrapper, role.id)).toBe('show-restricted')
    }
    expect(wrapper.text()).toContain('the more open setting applies')
    expect(wrapper.text()).toContain('Values are never shown')
  })

  it('shows a stored entry as "Hidden" for its metatype only', async () => {
    const { wrapper } = await mountSection({ historyVisibility: [{ roleId: 'viewer', metatypeId: VISIBILITY_IDS.actor, mode: 'hide' }] })
    expect(mode(wrapper, 'viewer')).toBe('show-restricted')
    wrapper.getComponent({ name: 'Select' }).vm.$emit('update:modelValue', VISIBILITY_IDS.actor)
    await flushPromises()
    expect(mode(wrapper, 'viewer')).toBe('hide')
  })

  it('saves only the changed role with one PUT', async () => {
    const { wrapper, api } = await mountSection()
    expect(wrapper.get('[data-testid="history-visibility-save"]').attributes('disabled')).toBeDefined()
    await choose(wrapper, 'viewer', 'hide')
    expect(wrapper.get('[data-testid="history-visibility-save"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="history-visibility-save"]').trigger('click')
    await flushPromises()
    expect(api.historyPuts).toEqual([[{ roleId: 'viewer', metatypeId: VISIBILITY_IDS.application, mode: 'hide' }]])
    expect(wrapper.find('[data-testid="history-visibility-saved"]').exists()).toBe(true)
    expect(mode(wrapper, 'viewer')).toBe('hide')
    // nothing changed since saving
    expect(wrapper.get('[data-testid="history-visibility-save"]').attributes('disabled')).toBeDefined()
  })

  it('shows a rejected entry at its row', async () => {
    const { wrapper } = await mountSection({ rejectHistory: ['entries[0].roleId'] })
    await choose(wrapper, 'auditor', 'hide')
    await wrapper.get('[data-testid="history-visibility-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="history-visibility-auditor"]').text()).toContain('This entry was rejected.')
    expect(wrapper.get('[data-testid="history-visibility-viewer"]').text()).not.toContain('This entry was rejected.')
    expect(wrapper.find('[data-testid="history-visibility-failure"]').exists()).toBe(true)
  })
})
