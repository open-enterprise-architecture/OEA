<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { sidebarTab, type SidebarTab } from '../navigation/store'
import NavigationTree from './NavigationTree.vue'
import SolutionBrowser from './SolutionBrowser.vue'

// The two tabs of the sidebar (SCR-055; US-132 AC1, design D4 of us-132-solution-browser): "Browser" is
// active when the session starts, "Navigation tree" holds the tree unchanged. The chosen tab is kept for the
// session in `sidebarTab`, so it stays while the person moves between pages.

const props = defineProps<{ canManage: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ navigate: [] }>()
const { t } = useI18n()

const items = computed(() => [
  { label: t('browser.tab'), value: 'browser' },
  { label: t('navigation.tabTree'), value: 'tree' },
])
const active = computed<SidebarTab>({
  get: () => sidebarTab.value,
  set: (value) => {
    sidebarTab.value = value
  },
})
</script>

<template>
  <div class="flex flex-col gap-3">
    <UTabs v-model="active" data-testid="sidebar-tabs" :items="items" :content="false" size="sm" variant="link" color="neutral" class="w-full" />
    <div role="tabpanel" :aria-label="active === 'browser' ? t('browser.tab') : t('navigation.tabTree')">
      <SolutionBrowser v-if="active === 'browser'" :fetch-fn="props.fetchFn" @navigate="emit('navigate')" />
      <NavigationTree v-else :can-manage="canManage" :fetch-fn="props.fetchFn" @navigate="emit('navigate')" />
    </div>
  </div>
</template>
