<script setup lang="ts">
import { computed } from 'vue'
import { useData } from 'vitepress'

const { lang, theme } = useData()

const text = computed(() =>
  lang.value === 'de'
    ? 'Dieses Handbuch beschreibt den unveröffentlichten Entwicklungsstand von OEA (main).'
    : 'This manual describes the unreleased development state of OEA (main).',
)
</script>

<template>
  <div v-if="!theme.manualVersion" class="unreleased-notice" role="note">
    {{ text }}
  </div>
</template>
