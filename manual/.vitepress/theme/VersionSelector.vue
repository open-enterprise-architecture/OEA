<script setup lang="ts">
import { computed } from 'vue'
import { useData } from 'vitepress'
import { pagePath, selectorOptions, useVersions } from './versions'

// Design D4: version selector in the navigation bar. It lists the releases of versions.json and the
// development state and opens the same page in the chosen version.

const { lang, page, theme } = useData()
const versions = useVersions(theme.value.manualSiteRoot)

const labels = computed(() =>
  lang.value === 'de'
    ? { selector: 'Version', development: 'Entwicklung (main)', latest: 'aktuell' }
    : { selector: 'Version', development: 'Development (main)', latest: 'latest' },
)

const selector = computed(() =>
  selectorOptions(
    versions.value,
    { directory: theme.value.manualDirectory, version: theme.value.manualVersion },
    labels.value,
  ),
)

function open(event: Event) {
  const target = (event.target as HTMLSelectElement).value
  window.location.assign(`${theme.value.manualSiteRoot}${target}${pagePath(page.value.relativePath)}`)
}
</script>

<template>
  <label class="version-selector">
    <select :value="selector.selected" :aria-label="labels.selector" @change="open">
      <option v-for="option in selector.options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
  </label>
</template>
