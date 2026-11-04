import { reactive } from 'vue'
import { fetchLabels, type LabelBundle } from '../api/labels'

/** How often the portal checks the bundle again (US-140 AC2, `Cache-Control: max-age=300`). */
export const REFRESH_INTERVAL_MS = 5 * 60 * 1000

/**
 * Translated metamodel labels of the effective language (ADR-025 layer 2, design D7 of
 * us-140-dynamic-labels): loaded after login, checked every five minutes with `If-None-Match`.
 */
export const labels = reactive<{ locale?: string; etag?: string; bundle: LabelBundle }>({ bundle: {} })

let timer: ReturnType<typeof setInterval> | undefined

let timerLocale: string | undefined

/** Loads the bundle of the locale; on 304 the known bundle stays. Failures keep the shown labels. */
export async function loadLabels(locale: string, fetchFn: typeof fetch = fetch): Promise<void> {
  const etag = labels.locale === locale ? labels.etag : undefined
  try {
    const result = await fetchLabels(locale, etag, fetchFn)
    if (result.changed) {
      labels.locale = locale
      labels.etag = result.etag
      labels.bundle = result.labels
    }
  } catch {
    // the next check tries again; names in the corporate language remain visible meanwhile
  }
}

/** Loads the bundle now and checks it again every five minutes until {@link stopLabels}. */
export async function startLabels(locale: string, fetchFn: typeof fetch = fetch): Promise<void> {
  if (timer === undefined || timerLocale !== locale) {
    stopLabels()
    timerLocale = locale
    timer = setInterval(() => void loadLabels(locale, fetchFn), REFRESH_INTERVAL_MS)
  }
  await loadLabels(locale, fetchFn)
}

/** Stops the periodic check. */
export function stopLabels(): void {
  if (timer !== undefined) {
    clearInterval(timer)
    timer = undefined
    timerLocale = undefined
  }
}

/** Forgets the bundle, e.g. after logout. */
export function clearLabels(): void {
  stopLabels()
  labels.locale = undefined
  labels.etag = undefined
  labels.bundle = {}
}

/** The label of the key, or the fallback (the name in the corporate language) while none is known. */
export function label(key: string, fallback: string): string {
  return labels.bundle[key] ?? fallback
}

/** Keys of the label bundle (ADR-025, design D1). */
export const labelKeys = {
  metatype: (id: string) => `metatype.${id}.name`,
  enumValue: (id: string) => `enum-value.${id}.label`,
  property: (id: string) => `property-def.${id}.label`,
}
