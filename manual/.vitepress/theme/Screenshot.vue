<script setup lang="ts">
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'

// Design D3: a screenshot of the walkthrough tests, referenced by name only. The page shows the
// screenshot in its own language if the walkthrough produced one, the English one otherwise
// (REQ-196 AC5), and a labelled placeholder in builds without screenshots (pull requests, preview).

const props = defineProps<{ name: string; alt: string }>()
const { lang, theme } = useData()

const source = computed(() => {
  const names: Record<string, string[]> = theme.value.screenshots?.names ?? {}
  for (const candidate of [lang.value, 'en']) {
    if (names[candidate]?.includes(props.name)) return withBase(`/screenshots/${candidate}/${props.name}.png`)
  }
  return ''
})

const placeholder = computed(() =>
  lang.value === 'de'
    ? `Screenshot „${props.name}“ – wird von den Walkthrough-Tests erzeugt`
    : `Screenshot "${props.name}" – produced by the walkthrough tests`,
)
</script>

<template>
  <figure class="screenshot">
    <img v-if="source" :src="source" :alt="alt" width="1280" height="800" loading="lazy" />
    <div v-else class="screenshot-placeholder" role="img" :aria-label="alt">{{ placeholder }}</div>
    <figcaption>{{ alt }}</figcaption>
  </figure>
</template>
