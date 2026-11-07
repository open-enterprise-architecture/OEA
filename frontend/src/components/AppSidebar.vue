<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { session } from '../session/store'
import { sidebar } from '../navigation/store'
import NavigationTree from './NavigationTree.vue'

// The sidebar of the web portal (SCR-055; design D4 of us-054-catalog-navigation-tree): the
// navigation tree on every page of a session with entity:read, fixed on wide screens and a
// slideover from the menu button of the header on narrow ones.

const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const permissions = computed(() => new Set((session.me?.permissions ?? []).map((entry) => entry.permission)))
const visible = computed(() => permissions.value.has('entity:read'))
const canManage = computed(() => permissions.value.has('viewpoint:manage'))
</script>

<template>
  <template v-if="visible">
    <aside data-testid="app-sidebar" class="hidden w-72 shrink-0 border-e border-default lg:block">
      <div class="sticky top-0 max-h-dvh overflow-y-auto p-3">
        <NavigationTree :can-manage="canManage" :fetch-fn="props.fetchFn" />
      </div>
    </aside>
    <USlideover v-model:open="sidebar.open" side="left" :title="t('navigation.title')" class="lg:hidden">
      <template #body>
        <NavigationTree :can-manage="canManage" :fetch-fn="props.fetchFn" @navigate="sidebar.open = false" />
      </template>
    </USlideover>
  </template>
</template>
