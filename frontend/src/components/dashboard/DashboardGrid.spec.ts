import ui from '@nuxt/ui/vue-plugin'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { WidgetConfig } from '../../api/dashboards'
import { position } from '../../test/dashboards'
import { testRouter } from '../../test/router'
import DashboardGrid from './DashboardGrid.vue'

function widget(id: string, title: string, col: number, row: number, width: number, height: number): WidgetConfig {
  return { id, type: 'text', title, position: position(col, row, width, height), config: { content: title } }
}

const A = widget('a', 'Alpha', 1, 1, 4, 2)
const B = widget('b', 'Beta', 5, 1, 4, 2)

function mountGrid(widgets: WidgetConfig[], editing: boolean) {
  const wrapper = mount(DashboardGrid, {
    props: { widgets, editing },
    slots: { widget: '<template #widget="{ widget }"><span>{{ widget.title }}</span></template>' },
    global: { plugins: [ui, testRouter()] },
    attachTo: document.body,
  })
  // 12 columns of 100 px with a gap of 12 px: a cell is 100 px wide; a row is 124 px high
  vi.spyOn(wrapper.get('[data-testid="dashboard-grid"]').element, 'getBoundingClientRect').mockReturnValue({ width: 1188 } as DOMRect)
  return wrapper
}

function pointer(type: string, x: number, y: number) {
  const event = new MouseEvent(type, { clientX: x, clientY: y, button: 0, bubbles: true, cancelable: true })
  window.dispatchEvent(event)
}

function down(element: Element, x: number, y: number) {
  element.dispatchEvent(new MouseEvent('pointerdown', { clientX: x, clientY: y, button: 0, bubbles: true, cancelable: true }))
}

afterEach(() => {
  document.body.innerHTML = ''
})

// spec dashboard/widgets "Grid layout", "Arrange widgets in the editor" (US-060, REQ-057)
describe('DashboardGrid', () => {
  it('places the widgets at their positions without edit controls in the view mode', () => {
    const wrapper = mountGrid([A, B], false)
    const section = wrapper.get('[data-testid="widget-b"]')
    // the grid area is row start / column start / row span / column span
    expect(section.attributes('style')).toContain('grid-area: 1 / 5 / span 2 / span 4')
    expect(wrapper.find('[data-testid="move-a"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="resize-a"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="edit-a"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Alpha')
  })

  it('sends one layout request for a drag', async () => {
    const wrapper = mountGrid([A, B], true)
    down(wrapper.get('[data-testid="move-a"]').element, 100, 100)
    pointer('pointermove', 100 + 2 * 100, 100 + 124)
    pointer('pointerup', 300, 224)
    expect(wrapper.emitted('layout')).toEqual([[[{ widgetId: 'a', col: 3, row: 2, width: 4, height: 2 }]]])
    expect(wrapper.emitted('invalid')).toBeUndefined()
  })

  it('sends one layout request for a resize at the corner', () => {
    const wrapper = mountGrid([A, B], true)
    down(wrapper.get('[data-testid="resize-a"]').element, 0, 0)
    pointer('pointermove', 100, 124)
    pointer('pointerup', 100, 124)
    expect(wrapper.emitted('layout')).toEqual([[[{ widgetId: 'a', col: 1, row: 1, width: 5, height: 3 }]]])
  })

  it('sends nothing for a drag that ends where it began', () => {
    const wrapper = mountGrid([A], true)
    down(wrapper.get('[data-testid="move-a"]').element, 50, 50)
    pointer('pointermove', 60, 55)
    pointer('pointerup', 60, 55)
    expect(wrapper.emitted('layout')).toBeUndefined()
    expect(wrapper.emitted('invalid')).toBeUndefined()
  })

  // spec "Width beyond the grid in the editor"
  it('names the problem and sends nothing when a drag leaves the grid', () => {
    const wrapper = mountGrid([A, B], true)
    down(wrapper.get('[data-testid="move-b"]').element, 0, 0)
    pointer('pointermove', 100, 0)
    pointer('pointerup', 100, 0)
    // column 5 + 4 = 9 is fine; column 6 + 4 = 10 too; a move by 5 columns gives 10 + 4 = 14
    expect(wrapper.emitted('layout')).toHaveLength(1)
    down(wrapper.get('[data-testid="move-b"]').element, 0, 0)
    pointer('pointermove', 500, 0)
    pointer('pointerup', 500, 0)
    expect(wrapper.emitted('layout')).toHaveLength(1)
    expect(wrapper.emitted('invalid')).toEqual([['exceeds-grid']])
  })

  it('accepts the last column and rejects one more', () => {
    const wrapper = mountGrid([widget('c', 'Gamma', 5, 1, 4, 1)], true)
    // column 9 + width 4 = 13 fits
    down(wrapper.get('[data-testid="move-c"]').element, 0, 0)
    pointer('pointermove', 400, 0)
    pointer('pointerup', 400, 0)
    expect(wrapper.emitted('layout')).toEqual([[[{ widgetId: 'c', col: 9, row: 1, width: 4, height: 1 }]]])
    expect(wrapper.emitted('invalid')).toBeUndefined()
    // column 10 + width 4 = 14 does not
    down(wrapper.get('[data-testid="move-c"]').element, 0, 0)
    pointer('pointermove', 500, 0)
    pointer('pointerup', 500, 0)
    expect(wrapper.emitted('invalid')).toEqual([['exceeds-grid']])
  })

  it('moves and resizes with the arrangement buttons', async () => {
    const wrapper = mountGrid([A, B], true)
    await wrapper.get('[data-testid="move-a"]').trigger('focus')
    await wrapper.get('[data-testid="move-a"]').element.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.vm.$nextTick()
    await wrapper.get('[data-testid="arrange-down-a"]').trigger('click')
    await wrapper.get('[data-testid="arrange-wider-a"]').trigger('click')
    await wrapper.get('[data-testid="arrange-taller-a"]').trigger('click')
    expect(wrapper.emitted('layout')).toEqual([
      [[{ widgetId: 'a', col: 1, row: 2, width: 4, height: 2 }]],
      [[{ widgetId: 'a', col: 1, row: 1, width: 5, height: 2 }]],
      [[{ widgetId: 'a', col: 1, row: 1, width: 4, height: 3 }]],
    ])
  })

  it.each([
    ['arrange-left-a', undefined],
    ['arrange-narrower-a', { widgetId: 'a', col: 1, row: 1, width: 3, height: 2 }],
    ['arrange-shorter-a', { widgetId: 'a', col: 1, row: 1, width: 4, height: 1 }],
    ['arrange-up-a', undefined],
  ])('%s at the border of the grid', async (testid, expected) => {
    const wrapper = mountGrid([A], true)
    wrapper.get('[data-testid="move-a"]').element.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.vm.$nextTick()
    await wrapper.get(`[data-testid="${testid}"]`).trigger('click')
    if (expected) {
      expect(wrapper.emitted('layout')).toEqual([[[expected]]])
    } else {
      expect(wrapper.emitted('layout')).toBeUndefined()
      expect(wrapper.emitted('invalid')).toEqual([['out-of-range']])
    }
  })

  it('rejects a height of 7 and a width of 13 from the buttons', async () => {
    const tall = widget('t', 'Tall', 1, 1, 12, 6)
    const wrapper = mountGrid([tall], true)
    wrapper.get('[data-testid="move-t"]').element.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await wrapper.vm.$nextTick()
    await wrapper.get('[data-testid="arrange-taller-t"]').trigger('click')
    await wrapper.get('[data-testid="arrange-wider-t"]').trigger('click')
    expect(wrapper.emitted('layout')).toBeUndefined()
    expect(wrapper.emitted('invalid')).toEqual([['out-of-range'], ['exceeds-grid']])
  })

  it('moves with the arrow keys and resizes with shift', async () => {
    const wrapper = mountGrid([A], true)
    await wrapper.get('[data-testid="move-a"]').trigger('keydown', { key: 'ArrowRight' })
    await wrapper.get('[data-testid="move-a"]').trigger('keydown', { key: 'ArrowDown', shiftKey: true })
    await wrapper.get('[data-testid="move-a"]').trigger('keydown', { key: 'a' })
    expect(wrapper.emitted('layout')).toEqual([
      [[{ widgetId: 'a', col: 2, row: 1, width: 4, height: 2 }]],
      [[{ widgetId: 'a', col: 1, row: 1, width: 4, height: 3 }]],
    ])
  })

  // spec "Overlap"
  it('marks overlapping widgets and no others', () => {
    const wrapper = mountGrid([A, widget('c', 'Gamma', 3, 1, 4, 2), widget('d', 'Delta', 1, 5, 12, 1)], false)
    expect(wrapper.get('[data-testid="widget-a"]').attributes('data-overlap')).toBe('true')
    expect(wrapper.get('[data-testid="widget-c"]').attributes('data-overlap')).toBe('true')
    expect(wrapper.get('[data-testid="widget-a"]').classes()).toContain('border-error')
    expect(wrapper.get('[data-testid="overlap-a"]').text()).toContain('Overlaps another widget')
    expect(wrapper.get('[data-testid="widget-d"]').attributes('data-overlap')).toBeUndefined()
    expect(wrapper.get('[data-testid="widget-d"]').classes()).not.toContain('border-error')
  })

  it('asks the page to edit and remove a widget', async () => {
    const wrapper = mountGrid([A], true)
    await wrapper.get('[data-testid="edit-a"]').trigger('click')
    await wrapper.get('[data-testid="remove-a"]').trigger('click')
    expect(wrapper.emitted('edit')?.[0]).toEqual([A])
    expect(wrapper.emitted('remove')?.[0]).toEqual([A])
  })
})
