import { describe, expect, it } from 'vitest'
import { browserLanguage, effectiveLanguage, i18n, setLanguage } from '.'

// Design D6 and D7 of us-140-dynamic-labels
describe('language of the user interface', () => {
  it('takes the chosen language, then the browser language, then English', () => {
    expect(effectiveLanguage('fr', ['de-CH'])).toBe('fr')
    expect(effectiveLanguage(null, ['es-ES', 'de-CH', 'en'])).toBe('de')
    expect(effectiveLanguage(undefined, ['es', 'pt'])).toBe('en')
    expect(browserLanguage(['IT-it'])).toBe('it')
    expect(browserLanguage([])).toBe('en')
  })

  it('shows the static texts in German, and in English for French and Italian', () => {
    const { t } = i18n.global
    setLanguage('de')
    expect(t('login.title')).toBe('Bei OEA anmelden')
    expect(document.documentElement.lang).toBe('de')
    setLanguage('fr')
    expect(t('login.title')).toBe('Log in to OEA')
    setLanguage('it')
    expect(t('catalogs.title')).toBe('Catalogs')
  })
})
