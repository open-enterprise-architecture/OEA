import { config } from '@vue/test-utils'
import { afterEach, beforeEach } from 'vitest'
import { i18n, setLanguage } from '../i18n'
import { clearLabels } from '../i18n/labels'

// Every spec mounts with the static texts in English unless it switches the language itself
config.global.plugins.push(i18n)

beforeEach(() => {
  setLanguage('en')
})

afterEach(() => {
  clearLabels()
})
