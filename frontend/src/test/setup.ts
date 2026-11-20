import { config } from '@vue/test-utils'
import { afterEach, beforeEach } from 'vitest'
import { i18n, setLanguage } from '../i18n'
import { clearLabels } from '../i18n/labels'

// jsdom has no layout: the canvas library (Vue Flow, SCR-021) observes the size of its elements and reads the
// transform of its viewport, which stay at their start values here
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub
globalThis.DOMMatrixReadOnly ??= class {
  m22 = 1
} as unknown as typeof DOMMatrixReadOnly

// Every spec mounts with the static texts in English unless it switches the language itself
config.global.plugins.push(i18n)

beforeEach(() => {
  setLanguage('en')
})

afterEach(() => {
  clearLabels()
})
