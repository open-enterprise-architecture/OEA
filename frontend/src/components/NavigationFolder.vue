<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { itemLabel, type TreeNode, type TreeNodeItem } from '../api/navigation'

// One folder of the navigation tree (SCR-055; design D4 of us-054-catalog-navigation-tree): the
// expand toggle, the folder actions for viewpoint:manage, the catalog entries and the subfolders.

/** An action the tree component carries out. */
export type FolderAction =
  | { kind: 'new-folder'; node: TreeNode }
  | { kind: 'rename'; node: TreeNode }
  | { kind: 'delete'; node: TreeNode }
  | { kind: 'remove-item'; node: TreeNode; item: TreeNodeItem }

const props = defineProps<{ node: TreeNode; expanded: ReadonlySet<string>; canManage: boolean; depth: number }>()
const emit = defineEmits<{ toggle: [id: string]; action: [action: FolderAction]; navigate: [] }>()
const { t } = useI18n()

const open = computed(() => props.expanded.has(props.node.id))
const isRoot = computed(() => props.node.parentId === null)
const hasContent = computed(() => props.node.children.length > 0 || props.node.items.length > 0)

const menu = computed(() => [
  [
    { label: t('navigation.newFolder'), icon: 'i-lucide-folder-plus', onSelect: () => emit('action', { kind: 'new-folder', node: props.node }) },
    { label: t('navigation.rename'), icon: 'i-lucide-pencil', onSelect: () => emit('action', { kind: 'rename', node: props.node }) },
    ...(isRoot.value
      ? []
      : [{ label: t('navigation.delete'), icon: 'i-lucide-trash-2', color: 'error' as const, onSelect: () => emit('action', { kind: 'delete', node: props.node }) }]),
  ],
])

const indent = computed(() => ({ paddingInlineStart: `${props.depth * 0.75}rem` }))
</script>

<template>
  <li data-testid="tree-folder" :data-folder-id="node.id" role="treeitem" :aria-expanded="open" :aria-level="depth + 1">
    <div class="group flex items-center gap-1 rounded-md pe-1 hover:bg-elevated" :style="indent">
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
        :expanded="expanded"
        :can-manage="canManage"
        :depth="depth + 1"
        @toggle="emit('toggle', $event)"
        @action="emit('action', $event)"
        @navigate="emit('navigate')"
      />
      <li v-for="item in node.items" :key="item.id" data-testid="tree-item" role="treeitem" :aria-level="depth + 2">
        <div class="group flex items-center gap-1 rounded-md pe-1 hover:bg-elevated" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
          <UIcon :name="item.scope === 'personal' ? 'i-lucide-user-round' : 'i-lucide-table-2'" class="size-4 shrink-0 text-muted" aria-hidden="true" />
          <RouterLink
            data-testid="tree-item-link"
            :to="`/catalogs/${item.referenceId}`"
            class="min-w-0 flex-1 truncate py-1 text-sm text-default hover:underline"
            active-class="font-semibold text-highlighted"
            @click="emit('navigate')"
          >
            {{ itemLabel(item) }}<span v-if="item.scope === 'personal'" class="sr-only"> ({{ t('navigation.personal') }})</span>
          </RouterLink>
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
