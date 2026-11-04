import { createI18n } from 'vue-i18n'
import de from './de.json'
import en from './en.json'

/** Languages a person may choose (US-140); static texts exist in English and German only. */
export const LANGUAGES = ['de', 'en', 'fr', 'it'] as const

export type Language = (typeof LANGUAGES)[number]

function isLanguage(value: string): value is Language {
  return (LANGUAGES as readonly string[]).includes(value)
}

/**
 * Static texts of the web portal (ADR-025 layer 1, design D7 of us-140-dynamic-labels); `fr` and
 * `it` fall back to English.
 */
export const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  missingWarn: false,
  fallbackWarn: false,
  messages: { en, de },
})

/** The first language of the browser that OEA offers, else English (design D6). */
export function browserLanguage(languages: readonly string[] = globalThis.navigator?.languages ?? []): Language {
  for (const tag of languages) {
    const primary = tag.toLowerCase().split('-')[0]
    if (isLanguage(primary)) {
      return primary
    }
  }
  return 'en'
}

/** The chosen language of the person, else the language of the browser (design D6). */
export function effectiveLanguage(chosen: string | null | undefined, languages?: readonly string[]): Language {
  return chosen && isLanguage(chosen) ? chosen : browserLanguage(languages)
}

/** Switches the static texts to the language. */
export function setLanguage(language: Language): void {
  // fr and it have no messages of their own; the locale type lists only the message locales
  ;(i18n.global.locale as { value: string }).value = language
  if (typeof document !== 'undefined') {
    document.documentElement.lang = language
  }
}

/** The current language of the static texts. */
export function currentLanguage(): Language {
  return i18n.global.locale.value as Language
}
