<script setup lang="ts">
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import { pagePath } from './versions'

// Visible switch to the same page in the other language; the language menu of the default theme
// is an unlabelled icon that readers easily miss (owner feedback of 2026-10-27)

const { lang, page } = useData()

const target = computed(() => {
  const path = pagePath(page.value.relativePath)
  return lang.value === 'de'
    ? { href: withBase(`/${path.replace(/^de\//, '')}`), label: 'English', hreflang: 'en' }
    : { href: withBase(`/de/${path}`), label: 'Deutsch', hreflang: 'de' }
})
</script>

<template>
  <a class="language-switch" :href="target.href" :hreflang="target.hreflang" :lang="target.hreflang">
    <span class="vpi-languages" aria-hidden="true" />
    {{ target.label }}
  </a>
</template>
