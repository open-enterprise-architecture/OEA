<script setup lang="ts">
import { computed, onMounted, provide, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BrowserItem } from '../api/navigation'
import { rateLimitMessage, type Rejection } from '../api/problem'
import { browserNodes, type BrowserNode } from '../navigation/browserNodes'
import { HIERARCHY_CONTEXT, type HierarchyContext } from '../navigation/hierarchyContext'
import { addStructuringAid, browser, loadBrowser, loadCategory, moveComponent, removeStructuringAid } from '../navigation/store'
import { session } from '../session/store'
import BrowserNodeRow from './BrowserNodeRow.vue'
import DeleteAidDialog from './browser/DeleteAidDialog.vue'
import MoveToDialog from './browser/MoveToDialog.vue'
import NewAidDialog from './browser/NewAidDialog.vue'

// The browser tab of the sidebar (SCR-055; US-132, US-133, REQ-138 to REQ-140; design D4 of us-132-solution-browser):
// "Current state" first, then the plateaus and the group "Projects" with their active solutions, each with its
// categories and the number of their objects. A category loads its objects when it is expanded first.

const props = defineProps<{ fetchFn?: typeof fetch }>()
const emit = defineEmits<{ navigate: [] }>()
const { t } = useI18n()

const permissions = computed(() => new Set((session.me?.permissions ?? []).map((entry) => entry.permission)))

// the dialogs of the hierarchy (US-135, US-136): "Move to…", "New structuring aid" and the warning before deleting an aid
const moving = ref<BrowserItem | null>(null)
const moveOpen = ref(false)
const aidParent = ref<BrowserItem | null>(null)
const aidOpen = ref(false)
const deleting = ref<BrowserItem | null>(null)
const deleteOpen = ref(false)
// the message of a refused drop or move, shown until the next change; the tree stays as it was
const changeError = ref<string>()

function describe(rejection: Rejection): string {
  if (rejection.status === 422) {
    return t('browser.moveInvalid')
  }
  if (rejection.status === 409) {
    return t('browser.moveBlocked')
  }
  if (rejection.status === 412) {
    return t('browser.stale')
  }
  return rateLimitMessage(rejection) ?? t('browser.changeFailed')
}

async function move(item: BrowserItem, parentId: number | null) {
  changeError.value = undefined
  const rejection = await moveComponent(item, parentId, props.fetchFn)
  if (rejection) {
    changeError.value = describe(rejection)
  }
}

const context = reactive<HierarchyContext>({
  canWrite: computed(() => permissions.value.has('entity:write')) as unknown as boolean,
  canDelete: computed(() => permissions.value.has('entity:write') && permissions.value.has('entity:delete')) as unknown as boolean,
  moveTo(item) {
    moving.value = item
    moveOpen.value = true
  },
  newAid(parent) {
    aidParent.value = parent
    aidOpen.value = true
  },
  deleteAid(item) {
    deleting.value = item
    deleteOpen.value = true
  },
  drop(dragged, target) {
    void move(dragged, target === null ? null : Number(target.id))
  },
  fetchFn: props.fetchFn,
})
provide(HIERARCHY_CONTEXT, context)

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
      <BrowserNodeRow v-for="node in nodes" :key="node.key" :node="node" :depth="0" :fetch-fn="fetchFn" @toggle="toggle" @more="more" @navigate="emit('navigate')" />
    </ul>
    <p v-if="changeError" data-testid="browser-change-error" role="alert" class="flex items-start justify-between gap-2 rounded-md bg-error/10 p-2 text-sm text-error">
      {{ changeError }}
      <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-x" :aria-label="t('browser.dismiss')" @click="changeError = undefined" />
    </p>
    <MoveToDialog v-model:open="moveOpen" :item="moving" :fetch-fn="fetchFn" @choose="moving && move(moving, $event)" />
    <NewAidDialog
      v-model:open="aidOpen"
      :parent-name="aidParent?.name ?? null"
      :create="(name, description) => addStructuringAid({ name, description, parentEntityId: aidParent ? Number(aidParent.id) : null }, fetchFn)"
    />
    <DeleteAidDialog v-model:open="deleteOpen" :item="deleting" :remove="(item) => removeStructuringAid(item, fetchFn)" />
  </nav>
</template>
