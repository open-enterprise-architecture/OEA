<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { browserNodes, type BrowserNode } from '../navigation/browserNodes'
import { browser, loadBrowser, loadCategory } from '../navigation/store'
import BrowserNodeRow from './BrowserNodeRow.vue'

// The browser tab of the sidebar (SCR-055; US-132, US-133, REQ-138 to REQ-140; design D4 of us-132-solution-browser):
// "Current state" first, then the plateaus and the group "Projects" with their active solutions, each with its
// categories and the number of their objects. A category loads its objects when it is expanded first.

const props = defineProps<{ fetchFn?: typeof fetch }>()
const emit = defineEmits<{ navigate: [] }>()
const { t } = useI18n()

const nodes = computed(() => (browser.view ? browserNodes(browser.view, (key) => t(key)) : []))

onMounted(() => {
  void loadBrowser(props.fetchFn)
})

function toggle(node: BrowserNode) {
  // an empty node has nothing to show
  if (node.count === 0) {
    return
  }
  if (browser.expanded.has(node.key)) {
    browser.expanded.delete(node.key)
    return
  }
  browser.expanded.add(node.key)
  if (node.query) {
    void loadCategory(node.key, node.query, props.fetchFn)
  }
}

function more(node: BrowserNode) {
  if (node.query) {
    void loadCategory(node.key, node.query, props.fetchFn, true)
  }
}
</script>

<template>
  <nav data-testid="solution-browser" :aria-label="t('browser.title')" class="flex flex-col gap-2">
    <p v-if="browser.failed && !browser.view" data-testid="browser-load-failed" class="flex flex-col items-start gap-2 text-sm text-error">
      {{ t('browser.loadFailed') }}
      <UButton size="xs" color="neutral" variant="outline" :label="t('navigation.retry')" @click="loadBrowser(fetchFn, true)" />
    </p>
    <ul v-else-if="browser.view" role="tree" :aria-label="t('browser.title')">
      <BrowserNodeRow v-for="node in nodes" :key="node.key" :node="node" :depth="0" @toggle="toggle" @more="more" @navigate="emit('navigate')" />
    </ul>
  </nav>
</template>
