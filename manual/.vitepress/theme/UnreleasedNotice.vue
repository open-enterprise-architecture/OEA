<script setup lang="ts">
import { computed } from 'vue'
import { useData } from 'vitepress'
import { pagePath, useVersions } from './versions'

// Design D5 (s2c-manual-site) and D6 (s2c-manual-release): builds without MANUAL_VERSION describe
// main; once a release is published, the notice links to the same page of the latest release.

const { lang, page, theme } = useData()
const versions = useVersions(theme.value.manualSiteRoot)

const text = computed(() =>
  lang.value === 'de'
    ? 'Dieses Handbuch beschreibt den unveröffentlichten Entwicklungsstand von OEA (main).'
    : 'This manual describes the unreleased development state of OEA (main).',
)

const latest = computed(() => {
  const list = versions.value
  const release = list?.releases.find((candidate) => candidate.path === list.latest)
  if (!release) return undefined
  return {
    href: `${theme.value.manualSiteRoot}${release.path}/${pagePath(page.value.relativePath)}`,
    label: lang.value === 'de' ? `Zum Handbuch des Release ${release.tag}` : `Go to the manual of release ${release.tag}`,
  }
})
</script>

<template>
  <div v-if="!theme.manualVersion" class="unreleased-notice" role="note">
    {{ text }}
    <a v-if="latest" :href="latest.href">{{ latest.label }}</a>
  </div>
</template>
