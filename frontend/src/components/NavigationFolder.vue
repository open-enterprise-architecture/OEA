<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { itemLabel, itemRoute, type TreeNode, type TreeNodeItem } from '../api/navigation'
import { DRAG_STATE, planDrop, planStep, type DropTarget, type Move, type Subject } from '../navigation/moves'

// One folder of the navigation tree (SCR-055; design D4 of us-054-catalog-navigation-tree, D5 of
// us-095-navigation-tree-items): the expand toggle, the folder actions for viewpoint:manage, the
// entries (catalogs, entities and solutions) and the subfolders. With viewpoint:manage folders and
// entries are dragged with native drag and drop; "Move up", "Move down" and "Move to…" do the same
// by keyboard and touch. Names are shown as text only, and links are built from type and ID (D7).

/** An action the tree component carries out. */
export type FolderAction =
  | { kind: 'new-folder'; node: TreeNode }
  | { kind: 'rename'; node: TreeNode }
  | { kind: 'delete'; node: TreeNode }
  | { kind: 'remove-item'; node: TreeNode; item: TreeNodeItem }
  | { kind: 'move'; move: Move }
  | { kind: 'move-to'; subject: Subject }

const props = defineProps<{ node: TreeNode; root: TreeNode; expanded: ReadonlySet<string>; canManage: boolean; depth: number }>()
const emit = defineEmits<{ toggle: [id: string]; action: [action: FolderAction]; navigate: [] }>()
const { t } = useI18n()
const drag = inject(DRAG_STATE, undefined)

const open = computed(() => props.expanded.has(props.node.id))
const isRoot = computed(() => props.node.parentId === null)
const hasContent = computed(() => props.node.children.length > 0 || props.node.items.length > 0)
const folder = computed<Subject>(() => ({ kind: 'folder', node: props.node }))

/** "Move up", "Move down" and "Move to…" of a folder or entry; a step is offered only where it leads somewhere. */
function moveItems(subject: Subject) {
  const up = planStep(props.root, subject, -1)
  const down = planStep(props.root, subject, 1)
  return [
    ...(up ? [{ label: t('navigation.moveUp'), icon: 'i-lucide-arrow-up', onSelect: () => emit('action', { kind: 'move', move: up }) }] : []),
    ...(down ? [{ label: t('navigation.moveDown'), icon: 'i-lucide-arrow-down', onSelect: () => emit('action', { kind: 'move', move: down }) }] : []),
    { label: t('navigation.moveTo'), icon: 'i-lucide-folder-input', onSelect: () => emit('action', { kind: 'move-to', subject }) },
  ]
}

const menu = computed(() => [
  [
    { label: t('navigation.newFolder'), icon: 'i-lucide-folder-plus', onSelect: () => emit('action', { kind: 'new-folder', node: props.node }) },
    { label: t('navigation.rename'), icon: 'i-lucide-pencil', onSelect: () => emit('action', { kind: 'rename', node: props.node }) },
  ],
  ...(isRoot.value ? [] : [moveItems(folder.value)]),
  ...(isRoot.value
    ? []
    : [[{ label: t('navigation.delete'), icon: 'i-lucide-trash-2', color: 'error' as const, onSelect: () => emit('action', { kind: 'delete', node: props.node }) }]]),
])

function itemMenu(item: TreeNodeItem) {
  return [moveItems({ kind: 'item', node: props.node, item })]
}

function itemIcon(item: TreeNodeItem): string {
  if (item.itemType === 'entity') return 'i-lucide-box'
  if (item.itemType === 'solution') return 'i-lucide-milestone'
  return item.scope === 'personal' ? 'i-lucide-user-round' : 'i-lucide-table-2'
}

// drag and drop: the row under the pointer shows whether it takes the drop
const dropState = ref<{ key: string; valid: boolean }>()

function startDrag(event: DragEvent, subject: Subject) {
  if (!props.canManage || !drag) {
    return
  }
  event.stopPropagation()
  drag.dragged = subject
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    // some browsers start a drag only with data; the drop never reads it (D7)
    event.dataTransfer.setData('text/plain', '')
  }
}

function endDrag() {
  if (drag) {
    drag.dragged = undefined
  }
  dropState.value = undefined
}

function dragOver(event: DragEvent, key: string, target: DropTarget) {
  const dragged = drag?.dragged
  if (!props.canManage || !dragged) {
    // a drag from outside the tree is no move
    return
  }
  event.stopPropagation()
  const valid = planDrop(props.root, dragged, target) !== undefined
  dropState.value = { key, valid }
  if (valid) {
    event.preventDefault()
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move'
    }
  } else if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'none'
  }
}

function dragLeave(key: string) {
  if (dropState.value?.key === key) {
    dropState.value = undefined
  }
}

function dropOn(event: DragEvent, target: DropTarget) {
  dropState.value = undefined
  const dragged = drag?.dragged
  if (!props.canManage || !dragged) {
    return
  }
  event.stopPropagation()
  drag.dragged = undefined
  const move = planDrop(props.root, dragged, target)
  if (move) {
    event.preventDefault()
    emit('action', { kind: 'move', move })
  }
}

function dropMarker(key: string): 'valid' | 'invalid' | undefined {
  if (dropState.value?.key !== key) return undefined
  return dropState.value.valid ? 'valid' : 'invalid'
}

const rowClass = (key: string) => {
  const marker = dropMarker(key)
  if (marker === 'valid') return 'ring-2 ring-primary'
  if (marker === 'invalid') return 'ring-2 ring-error cursor-not-allowed'
  return ''
}

const indent = computed(() => ({ paddingInlineStart: `${props.depth * 0.75}rem` }))
const folderKey = computed(() => `folder:${props.node.id}`)
</script>

<template>
  <li data-testid="tree-folder" :data-folder-id="node.id" role="treeitem" :aria-expanded="open" :aria-level="depth + 1">
    <div
      data-testid="tree-folder-row"
      class="group flex items-center gap-1 rounded-md pe-1 hover:bg-elevated"
      :class="rowClass(folderKey)"
      :style="indent"
      :draggable="canManage && !isRoot ? 'true' : undefined"
      :data-drop="dropMarker(folderKey)"
      @dragstart="startDrag($event, folder)"
      @dragend="endDrag"
      @dragover="dragOver($event, folderKey, { kind: 'folder', node })"
      @dragleave="dragLeave(folderKey)"
      @drop="dropOn($event, { kind: 'folder', node })"
    >
      <UButton
        data-testid="tree-toggle"
        size="xs"
        color="neutral"
        variant="ghost"
        :icon="open ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
        :aria-label="t(open ? 'navigation.collapse' : 'navigation.expand', { name: node.name })"
        :class="hasContent ? '' : 'invisible'"
        @click="emit('toggle', node.id)"
      />
      <UIcon :name="open ? 'i-lucide-folder-open' : 'i-lucide-folder'" class="size-4 shrink-0 text-muted" aria-hidden="true" />
      <button type="button" data-testid="tree-folder-name" class="min-w-0 flex-1 truncate py-1 text-start text-sm text-default" @click="emit('toggle', node.id)">
        {{ node.name }}
      </button>
      <span v-if="dropMarker(folderKey) === 'invalid'" data-testid="tree-drop-invalid" class="sr-only">{{ t('navigation.dropInvalid') }}</span>
      <UDropdownMenu v-if="canManage" :items="menu" :modal="false" :content="{ align: 'end' }">
        <UButton
          data-testid="tree-folder-actions"
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-ellipsis"
          :aria-label="t('navigation.folderActions', { name: node.name })"
        />
      </UDropdownMenu>
    </div>
    <ul v-if="open" role="group">
      <NavigationFolder
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :root="root"
        :expanded="expanded"
        :can-manage="canManage"
        :depth="depth + 1"
        @toggle="emit('toggle', $event)"
        @action="emit('action', $event)"
        @navigate="emit('navigate')"
      />
      <li
        v-for="item in node.items"
        :key="item.id"
        data-testid="tree-item"
        :data-item-id="item.id"
        :data-item-type="item.itemType"
        role="treeitem"
        :aria-level="depth + 2"
      >
        <div
          data-testid="tree-item-row"
          class="group flex items-center gap-1 rounded-md pe-1 hover:bg-elevated"
          :class="rowClass(`item:${item.id}`)"
          :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }"
          :draggable="canManage ? 'true' : undefined"
          :data-drop="dropMarker(`item:${item.id}`)"
          @dragstart="startDrag($event, { kind: 'item', node, item })"
          @dragend="endDrag"
          @dragover="dragOver($event, `item:${item.id}`, { kind: 'item', node, item })"
          @dragleave="dragLeave(`item:${item.id}`)"
          @drop="dropOn($event, { kind: 'item', node, item })"
        >
          <UIcon data-testid="tree-item-icon" :name="itemIcon(item)" class="size-4 shrink-0 text-muted" aria-hidden="true" />
          <RouterLink
            v-if="itemRoute(item)"
            data-testid="tree-item-link"
            :to="itemRoute(item)!"
            class="min-w-0 flex-1 truncate py-1 text-sm text-default hover:underline"
            :class="item.deleted ? 'text-muted line-through' : ''"
            active-class="font-semibold text-highlighted"
            @click="emit('navigate')"
          >
            {{ itemLabel(item) }}<span v-if="item.scope === 'personal'" class="sr-only"> ({{ t('navigation.personal') }})</span><span
              v-if="item.deleted"
              class="sr-only"
            > ({{ t('navigation.deleted') }})</span>
          </RouterLink>
          <span v-else data-testid="tree-item-label" class="min-w-0 flex-1 truncate py-1 text-sm text-muted">{{ itemLabel(item) }}</span>
          <UBadge
            v-if="item.deleted"
            data-testid="tree-item-deleted"
            color="warning"
            variant="subtle"
            size="sm"
            icon="i-lucide-triangle-alert"
            :label="t('navigation.deleted')"
            aria-hidden="true"
          />
          <span v-if="dropMarker(`item:${item.id}`) === 'invalid'" data-testid="tree-drop-invalid" class="sr-only">{{ t('navigation.dropInvalid') }}</span>
          <UDropdownMenu v-if="canManage" :items="itemMenu(item)" :modal="false" :content="{ align: 'end' }">
            <UButton
              data-testid="tree-item-actions"
              size="xs"
              color="neutral"
              variant="ghost"
              icon="i-lucide-ellipsis"
              :aria-label="t('navigation.itemActions', { name: itemLabel(item) })"
            />
          </UDropdownMenu>
          <UButton
            v-if="canManage"
            data-testid="tree-item-remove"
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-x"
            :aria-label="t('navigation.removeNamed', { name: itemLabel(item) })"
            @click="emit('action', { kind: 'remove-item', node, item })"
          />
        </div>
      </li>
    </ul>
  </li>
</template>
