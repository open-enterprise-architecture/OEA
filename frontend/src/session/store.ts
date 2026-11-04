import { reactive } from 'vue'
import { fetchMe, logout, type Me } from '../api/session'
import { browserLanguage, effectiveLanguage, setLanguage } from '../i18n'
import { clearLabels, startLabels } from '../i18n/labels'

/** The current principal as known to the pages; refreshed from GET /me. */
export const session = reactive<{ me?: Me; loaded: boolean }>({ loaded: false })

export async function refreshSession(fetchFn?: typeof fetch): Promise<Me | undefined> {
  try {
    session.me = await fetchMe(fetchFn)
  } catch {
    session.me = undefined
  }
  session.loaded = true
  await applyLanguage(fetchFn)
  return session.me
}

/**
 * Applies the effective language of the principal to the static texts and loads the metamodel
 * labels of that language (design D6 and D7 of us-140-dynamic-labels).
 */
export async function applyLanguage(fetchFn?: typeof fetch): Promise<void> {
  const language = effectiveLanguage(session.me?.uiLanguage)
  setLanguage(language)
  if (session.me) {
    await startLabels(language, fetchFn)
  } else {
    clearLabels()
  }
}

export async function endSession(fetchFn?: typeof fetch): Promise<void> {
  try {
    await logout(fetchFn)
  } finally {
    session.me = undefined
    clearLabels()
    setLanguage(browserLanguage())
  }
}
