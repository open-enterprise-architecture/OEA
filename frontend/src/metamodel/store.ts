import { reactive } from 'vue'
import {
  fetchCategories,
  fetchGlobalReimportSetting,
  fetchMetatypes,
  fetchPropertyDefinitions,
  type GlobalReimportSetting,
  type MetatypeDetail,
  type PropertyCategory,
  type PropertyDefinitionDetail,
} from '../api/metamodel'
import { currentLanguage } from '../i18n'
import { loadLabels } from '../i18n/labels'

/**
 * The metamodel as shown by the configuration SCR-030 (design D4 of
 * scr-030-metamodel-configuration): reloaded completely after every write, since a write can
 * change other elements as well (inherited mappings, removed values, labels).
 */
export const metamodel = reactive<{
  metatypes: MetatypeDetail[]
  properties: PropertyDefinitionDetail[]
  categories: PropertyCategory[]
  globalReimportSetting?: GlobalReimportSetting
  loaded: boolean
  failed: boolean
}>({ metatypes: [], properties: [], categories: [], loaded: false, failed: false })

/** Loads metatypes, property definitions, categories and the global re-import setting. */
export async function loadMetamodel(fetchFn: typeof fetch = fetch): Promise<void> {
  try {
    const [metatypes, properties, categories] = await Promise.all([
      fetchMetatypes(fetchFn),
      fetchPropertyDefinitions(fetchFn),
      fetchCategories(fetchFn),
    ])
    metamodel.metatypes = [...metatypes].sort(
      (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.name.localeCompare(b.name),
    )
    metamodel.properties = [...properties].sort((a, b) => a.name.localeCompare(b.name))
    metamodel.categories = [...categories].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name))
    metamodel.failed = false
  } catch {
    metamodel.failed = true
  }
  try {
    metamodel.globalReimportSetting = await fetchGlobalReimportSetting(fetchFn)
  } catch {
    metamodel.globalReimportSetting = undefined
  }
  metamodel.loaded = true
}

/** Reloads after a write, together with the labels, whose fallback is the changed name. */
export async function reloadMetamodel(fetchFn: typeof fetch = fetch): Promise<void> {
  await Promise.all([loadMetamodel(fetchFn), loadLabels(currentLanguage(), fetchFn)])
}

export function metatypeById(id: string | undefined): MetatypeDetail | undefined {
  return metamodel.metatypes.find((metatype) => metatype.id === id)
}

export function propertyById(id: string | undefined): PropertyDefinitionDetail | undefined {
  return metamodel.properties.find((property) => property.id === id)
}

export function categoryById(id: string | undefined): PropertyCategory | undefined {
  return metamodel.categories.find((category) => category.id === id)
}
