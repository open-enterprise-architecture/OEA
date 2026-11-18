<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../../dashboard/markdown'

// A text widget (US-059): the Markdown content as sanitized HTML. v-html receives nothing but
// the output of renderMarkdown, which removes scripts, event handlers, unknown tags and every URL
// scheme except http, https and mailto (design D4 of us-055-dashboards).
const props = defineProps<{ content: string }>()
const html = computed(() => renderMarkdown(props.content))
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html -->
  <div data-testid="text-widget" class="widget-text text-sm text-default" v-html="html" />
</template>

<style scoped>
.widget-text :deep(p) { margin-block: 0.5rem; }
.widget-text :deep(ul) { list-style: disc; padding-inline-start: 1.25rem; margin-block: 0.5rem; }
.widget-text :deep(ol) { list-style: decimal; padding-inline-start: 1.25rem; margin-block: 0.5rem; }
.widget-text :deep(a) { color: var(--ui-primary); text-decoration: underline; }
.widget-text :deep(h1), .widget-text :deep(h2), .widget-text :deep(h3) { font-weight: 600; margin-block: 0.5rem; }
.widget-text :deep(code) { font-family: ui-monospace, monospace; background: var(--ui-bg-elevated); padding-inline: 0.25rem; border-radius: 0.25rem; }
.widget-text :deep(blockquote) { border-inline-start: 3px solid var(--ui-border); padding-inline-start: 0.75rem; color: var(--ui-text-muted); }
</style>
