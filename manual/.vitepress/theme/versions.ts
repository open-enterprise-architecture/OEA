import { ref, type Ref } from 'vue'

// Design D4 (change s2c-manual-release): the list of published versions lives in versions.json at
// the site root of the branch gh-pages and is read at runtime, so that a manual built earlier also
// offers releases published after it.

export interface Release {
  /** Directory below the site root, e.g. "v0.2" */
  path: string
  /** Tag of the release published in that directory, e.g. "v0.2.1" */
  tag: string
}

export interface Versions {
  latest: string | null
  releases: Release[]
  next: boolean
}

let loaded: Ref<Versions | null> | undefined

/** versions.json of the site; null until loaded or if it is missing (local preview). */
export function useVersions(siteRoot: string): Ref<Versions | null> {
  if (!loaded) {
    loaded = ref(null)
    if (typeof window !== 'undefined') {
      fetch(`${siteRoot}versions.json`, { cache: 'no-cache' })
        .then((response) => (response.ok ? response.json() : null))
        .then((versions: Versions | null) => {
          loaded!.value = versions
        })
        .catch(() => undefined)
    }
  }
  return loaded
}

/** Path of the current page relative to its version directory, e.g. "de/setup/installation". */
export function pagePath(relativePath: string): string {
  return relativePath.replace(/(^|\/)index\.md$/, '$1').replace(/\.md$/, '')
}

export interface Option {
  /** Directory below the site root with trailing slash, e.g. "v0.2/" or "next/" */
  value: string
  label: string
}

/**
 * Entries of the version selector: every release, newest first as listed in versions.json, then the
 * development state; without versions.json only the own version.
 */
export function selectorOptions(
  versions: Versions | null,
  own: { directory: string; version: string },
  labels: { development: string; latest: string },
): { options: Option[]; selected: string } {
  const options: Option[] = []
  for (const release of versions?.releases ?? []) {
    const latest = release.path === versions?.latest ? ` (${labels.latest})` : ''
    options.push({ value: `${release.path}/`, label: `${release.tag}${latest}` })
  }
  if (versions?.next) options.push({ value: 'next/', label: labels.development })

  // The root copy of a release counts as its version directory; the root before the first release
  // and /next/ count as the development state
  let selected = own.directory || 'next/'
  if (own.directory === '' && own.version) {
    selected = options.find((option) => option.label.startsWith(own.version))?.value ?? ''
  }
  if (!options.some((option) => option.value === selected)) {
    options.unshift({ value: selected, label: own.version || labels.development })
  }
  return { options, selected }
}
