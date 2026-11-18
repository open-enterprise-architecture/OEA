<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { browserItemRoute, type BrowserItem } from '../../api/navigation'
import { HIERARCHY_CONTEXT } from '../../navigation/hierarchyContext'
import { browser, canDropOn, childrenKey, childrenQuery, drag, entityKey, loadCategory } from '../../navigation/store'

// One entity of the browser (SCR-055; US-135, US-136; design D5 of us-135-component-hierarchy): an element with its
// children below it, expanded on demand, or a connection. A structuring aid shows a folder. With `entity:write` a
// component of "Current state" is dragged onto another component or the root level, and has the menu "Move to…",
// "New structuring aid" and, for an aid with `entity:delete`, "Delete". Names are shown as text only.

const props = defineProps<{ item: BrowserItem; depth: number; hierarchy: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ navigate: [] }>()
const { t } = useI18n()
const context = inject(HIERARCHY_CONTEXT, undefined)

const key = computed(() => entityKey(props.item.id))
const state = computed(() => browser.categories[childrenKey(props.item.id)])
const open = computed(() => browser.expanded.has(key.value))
const expandable = computed(() => props.hierarchy && props.item.childCount > 0)
const writable = computed(() => props.hierarchy && context?.canWrite === true)

function toggle() {
  if (!expandable.value) {
    return
  }
  if (open.value) {
    browser.expanded.delete(key.value)
    return
  }
  browser.expanded.add(key.value)
  void loadCategory(childrenKey(props.item.id), childrenQuery(Number(props.item.id)), props.fetchFn)
}

function more() {
  void loadCategory(childrenKey(props.item.id), childrenQuery(Number(props.item.id)), props.fetchFn, true)
}

const menu = computed(() => {
  if (!context || !writable.value) {
    return []
  }
  const aid = props.item.structuringAid
  return [
    [
      { label: t('browser.moveTo'), icon: 'i-lucide-folder-input', onSelect: () => context.moveTo(props.item) },
      { label: t('browser.newAid'), icon: 'i-lucide-folder-plus', onSelect: () => context.newAid(props.item) },
    ],
    ...(aid && context.canDelete
      ? [[{ label: t('browser.deleteAid'), icon: 'i-lucide-trash-2', color: 'error' as const, onSelect: () => context.deleteAid(props.item) }]]
      : []),
  ]
})

// drag and drop: the row under the pointer shows whether it takes the drop
const marker = ref<'valid' | 'invalid'>()

function dragStart(event: DragEvent) {
  if (!writable.value) {
    return
  }
  event.stopPropagation()
  drag.item = props.item
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    // some browsers start a drag only with data; the drop never reads it
    event.dataTransfer.setData('text/plain', '')
  }
}

function dragEnd() {
  drag.item = undefined
  marker.value = undefined
}

function dragOver(event: DragEvent) {
  const dragged = drag.item
  if (!writable.value || !dragged) {
    return
  }
  event.stopPropagation()
  const valid = canDropOn(dragged, props.item)
  marker.value = valid ? 'valid' : 'invalid'
  if (valid) {
    event.preventDefault()
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move'
    }
  } else if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'none'
  }
}

function dragLeave() {
  marker.value = undefined
}

function drop(event: DragEvent) {
  marker.value = undefined
  const dragged = drag.item
  if (!writable.value || !dragged) {
    return
  }
  event.stopPropagation()
  drag.item = undefined
  if (canDropOn(dragged, props.item)) {
    event.preventDefault()
    context?.drop(dragged, props.item)
  }
}

const indent = computed(() => ({ paddingInlineStart: `${props.depth * 0.75 + (props.hierarchy ? 0 : 1.5)}rem` }))
</script>

<template>
  <li
    data-testid="browser-item"
    data-item-kind="entity"
    :data-item-id="item.id"
    role="treeitem"
    :aria-level="depth + 1"
    :aria-expanded="expandable ? open : undefined"
  >
    <div
      data-testid="browser-entity-row"
      class="flex items-center gap-1 rounded-md pe-1 hover:bg-elevated"
      :class="marker === 'valid' ? 'ring-2 ring-primary' : marker === 'invalid' ? 'ring-2 ring-error cursor-not-allowed' : ''"
      :style="indent"
      :draggable="writable ? 'true' : undefined"
      :data-drop="marker"
      @dragstart="dragStart"
      @dragend="dragEnd"
      @dragover="dragOver"
      @dragleave="dragLeave"
      @drop="drop"
    >
      <UButton
        v-if="hierarchy"
        data-testid="browser-item-toggle"
        size="xs"
        color="neutral"
        variant="ghost"
        :icon="open ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
        :aria-label="t(open ? 'navigation.collapse' : 'navigation.expand', { name: item.name })"
        :class="expandable ? '' : 'invisible'"
        :tabindex="expandable ? undefined : -1"
        @click="toggle"
      />
      <UIcon
        :name="item.structuringAid ? 'i-lucide-folder' : 'i-lucide-box'"
        :data-testid="item.structuringAid ? 'browser-item-folder' : undefined"
        class="size-4 shrink-0 text-muted"
        aria-hidden="true"
      />
      <RouterLink
        data-testid="browser-item-link"
        :to="browserItemRoute(item)!"
        class="min-w-0 flex-1 truncate py-1 text-sm text-default hover:underline"
        active-class="font-semibold text-highlighted"
        @click="emit('navigate')"
      >
        {{ item.name }}
      </RouterLink>
      <UBadge v-if="item.metatypeAbbreviation" data-testid="browser-item-abbreviation" color="neutral" variant="outline" size="sm" :label="item.metatypeAbbreviation" />
      <span v-if="marker === 'invalid'" data-testid="browser-drop-invalid" class="sr-only">{{ t('navigation.dropInvalid') }}</span>
      <UDropdownMenu v-if="menu.length" :items="menu" :modal="false" :content="{ align: 'end' }">
        <UButton data-testid="browser-item-actions" size="xs" color="neutral" variant="ghost" icon="i-lucide-ellipsis" :aria-label="t('browser.itemActions', { name: item.name })" />
      </UDropdownMenu>
    </div>
    <ul v-if="open && expandable" role="group">
      <BrowserEntityRow v-for="child in state?.items ?? []" :key="child.id" :item="child" :depth="depth + 1" hierarchy :fetch-fn="fetchFn" @navigate="emit('navigate')" />
      <li v-if="state?.failed" data-testid="browser-category-failed" class="flex flex-col items-start gap-1 py-1 text-sm text-error" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
        {{ t('browser.loadFailed') }}
        <UButton size="xs" color="neutral" variant="outline" :label="t('navigation.retry')" @click="more" />
      </li>
      <li v-else-if="state?.loading" data-testid="browser-category-loading" class="py-1 text-sm text-muted" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
        {{ t('browser.loading') }}
      </li>
      <li v-else-if="state && state.items.length < state.total" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
        <UButton data-testid="browser-load-more" size="xs" color="neutral" variant="link" :label="t('browser.loadMore')" @click="more" />
      </li>
    </ul>
  </li>
</template>
