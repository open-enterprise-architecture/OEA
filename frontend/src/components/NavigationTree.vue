<script setup lang="ts">
import { computed, onMounted, provide, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  contentsOf,
  createFolder,
  deleteFolder,
  itemLabel,
  moveTreeNode,
  moveTreeNodeItem,
  removeTreeItem,
  renameFolder,
  type TreeNode,
} from '../api/navigation'
import type { Outcome } from '../api/session'
import { rateLimitMessage, type Rejection } from '../api/problem'
import { DRAG_STATE, moveTargets, planMoveTo, type DragState, type Move, type Subject } from '../navigation/moves'
import { loadTree, navigation, showTree } from '../navigation/store'
import NavigationFolder, { type FolderAction } from './NavigationFolder.vue'

// The navigation tree of SCR-055 (US-054, US-095, US-096; design D4 of
// us-054-catalog-navigation-tree, D5 of us-095-navigation-tree-items): folders to expand, catalog,
// entity and solution entries as links, and for viewpoint:manage with entity:read the folder
// actions, "Remove from tree" and the moves by drag and drop, "Move up", "Move down" and "Move
// to…". The expand state is kept per session.

const props = defineProps<{ canManage: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ navigate: [] }>()
const { t } = useI18n()

// the element being dragged, shared by the folders of this tree only
provide(DRAG_STATE, reactive<DragState>({}))

const STORAGE_KEY = 'oea.navigation.expanded'

function storedExpanded(): string[] | undefined {
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY)
    return stored ? (JSON.parse(stored) as string[]) : undefined
  } catch {
    return undefined
  }
}

const initial = storedExpanded()
const expanded = reactive(new Set<string>(initial ?? []))

watch(
  () => navigation.tree?.id,
  (rootId) => {
    // the root starts expanded
    if (rootId && initial === undefined) {
      expanded.add(rootId)
    }
  },
  { immediate: true },
)

function toggle(id: string) {
  if (expanded.has(id)) {
    expanded.delete(id)
  } else {
    expanded.add(id)
  }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify([...expanded]))
  } catch {
    // the state is a convenience; without storage it lasts until the page is reloaded
  }
}

onMounted(() => {
  void loadTree(props.fetchFn)
})

const changeError = ref<string>()

/** Applies the tree of a write; returns the rejection, if any. */
async function change(write: () => Promise<Outcome<TreeNode>>) {
  changeError.value = undefined
  try {
    const outcome = await write()
    if (outcome.ok) {
      showTree(outcome.value)
      return undefined
    }
    return outcome.rejection
  } catch {
    return { status: 0, problemType: 'unknown', invalidFields: [] }
  }
}

// the dialog of "New folder" and "Rename"
const nameDialog = reactive<{ open: boolean; mode: 'new-folder' | 'rename'; node?: TreeNode; name: string; error?: string; busy: boolean }>({
  open: false,
  mode: 'new-folder',
  name: '',
  busy: false,
})
const nameTitle = computed(() =>
  nameDialog.mode === 'new-folder'
    ? t('navigation.newFolderTitle', { parent: nameDialog.node?.name ?? '' })
    : t('navigation.renameTitle', { name: nameDialog.node?.name ?? '' }),
)

async function submitName() {
  const node = nameDialog.node
  const name = nameDialog.name.trim()
  if (!node) {
    return
  }
  if (!name) {
    nameDialog.error = t('navigation.nameRequired')
    return
  }
  nameDialog.busy = true
  nameDialog.error = undefined
  const rejection = await change(() =>
    nameDialog.mode === 'new-folder' ? createFolder(node.id, name, props.fetchFn) : renameFolder(node.id, name, props.fetchFn),
  )
  nameDialog.busy = false
  if (!rejection) {
    nameDialog.open = false
    if (nameDialog.mode === 'new-folder' && !expanded.has(node.id)) {
      toggle(node.id)
    }
  } else if (rejection.status === 422 && rejection.invalidFields.includes('name')) {
    nameDialog.error = t('navigation.nameTaken')
  } else {
    nameDialog.error = rateLimitMessage(rejection) ?? t('navigation.changeFailed')
  }
}

// the confirmation of "Delete"
const deleteDialog = reactive<{ open: boolean; node?: TreeNode; error?: string; busy: boolean }>({ open: false, busy: false })
const deleteContents = computed(() => (deleteDialog.node ? contentsOf(deleteDialog.node) : { folders: 0, items: 0 }))

async function confirmDelete() {
  const node = deleteDialog.node
  if (!node) {
    return
  }
  deleteDialog.busy = true
  deleteDialog.error = undefined
  const rejection = await change(() => deleteFolder(node.id, props.fetchFn))
  deleteDialog.busy = false
  if (!rejection) {
    deleteDialog.open = false
  } else {
    deleteDialog.error =
      rateLimitMessage(rejection) ?? (rejection.status === 422 ? t('navigation.rootProtected') : t('navigation.changeFailed'))
  }
}

/** The message of a rejected move: a target the element cannot go to, else the generic failure. */
function moveFailure(rejection: Rejection): string {
  const limited = rateLimitMessage(rejection)
  if (limited) return limited
  if (rejection.status === 422) return t('navigation.moveRejected')
  return t('navigation.changeFailed')
}

/** Sends the move; a 404 means the tree changed meanwhile, so it is read again. */
async function move(request: Move): Promise<Rejection | undefined> {
  const rejection = await change(() =>
    request.kind === 'folder'
      ? moveTreeNode(request.nodeId, request.parentId, request.position, props.fetchFn)
      : moveTreeNodeItem(request.nodeId, request.itemId, request.targetNodeId, request.position, props.fetchFn),
  )
  if (rejection?.status === 404) {
    void loadTree(props.fetchFn, true)
  }
  return rejection
}

// the dialog of "Move to…"
const moveDialog = reactive<{ open: boolean; subject?: Subject; targetId?: string; error?: string; busy: boolean }>({ open: false, busy: false })
const moveName = computed(() => {
  const subject = moveDialog.subject
  if (!subject) return ''
  return subject.kind === 'folder' ? subject.node.name : itemLabel(subject.item)
})
const moveOptions = computed(() =>
  navigation.tree && moveDialog.subject
    ? moveTargets(navigation.tree, moveDialog.subject).map((folder) => ({ label: folder.path, value: folder.id }))
    : [],
)

async function submitMoveTo() {
  const subject = moveDialog.subject
  const root = navigation.tree
  if (!subject || !root || !moveDialog.targetId) {
    return
  }
  const request = planMoveTo(root, subject, moveDialog.targetId)
  if (!request) {
    moveDialog.error = t('navigation.moveRejected')
    return
  }
  moveDialog.busy = true
  moveDialog.error = undefined
  const rejection = await move(request)
  moveDialog.busy = false
  if (!rejection) {
    moveDialog.open = false
    if (!expanded.has(moveDialog.targetId)) {
      toggle(moveDialog.targetId)
    }
  } else {
    moveDialog.error = moveFailure(rejection)
  }
}

async function act(action: FolderAction) {
  if (action.kind === 'move') {
    const rejection = await move(action.move)
    if (rejection) {
      changeError.value = moveFailure(rejection)
    } else if (action.move.kind === 'item' && !expanded.has(action.move.targetNodeId)) {
      toggle(action.move.targetNodeId)
    } else if (action.move.kind === 'folder' && !expanded.has(action.move.parentId)) {
      toggle(action.move.parentId)
    }
    return
  }
  if (action.kind === 'move-to') {
    // the first folder is chosen, as in "Place in navigation"
    const first = navigation.tree ? moveTargets(navigation.tree, action.subject)[0] : undefined
    Object.assign(moveDialog, { open: true, subject: action.subject, targetId: first?.id, error: undefined })
    return
  }
  if (action.kind === 'new-folder' || action.kind === 'rename') {
    Object.assign(nameDialog, { open: true, mode: action.kind, node: action.node, name: action.kind === 'rename' ? action.node.name : '', error: undefined })
  } else if (action.kind === 'delete') {
    Object.assign(deleteDialog, { open: true, node: action.node, error: undefined })
  } else {
    const { node, item } = action
    const rejection = await change(() => removeTreeItem(node.id, item.id, props.fetchFn))
    if (rejection) {
      changeError.value = rateLimitMessage(rejection) ?? t('navigation.changeFailed')
    }
  }
}
</script>

<template>
  <nav data-testid="navigation-tree" :aria-label="t('navigation.title')" class="flex flex-col gap-2">
    <p v-if="navigation.failed && !navigation.tree" data-testid="tree-load-failed" class="flex flex-col items-start gap-2 text-sm text-error">
      {{ t('navigation.loadFailed') }}
      <UButton size="xs" color="neutral" variant="outline" :label="t('navigation.retry')" @click="loadTree(fetchFn, true)" />
    </p>
    <ul v-else-if="navigation.tree" role="tree" :aria-label="t('navigation.title')">
      <NavigationFolder
        :node="navigation.tree"
        :root="navigation.tree"
        :expanded="expanded"
        :can-manage="canManage"
        :depth="0"
        @toggle="toggle"
        @action="act"
        @navigate="emit('navigate')"
      />
    </ul>
    <p
      v-if="navigation.tree && navigation.tree.children.length === 0 && navigation.tree.items.length === 0"
      data-testid="tree-empty"
      class="px-2 text-sm text-muted"
    >
      {{ t('navigation.empty') }}
    </p>
    <p v-if="changeError" data-testid="tree-change-error" class="px-2 text-sm text-error">{{ changeError }}</p>

    <UModal v-model:open="nameDialog.open" :title="nameTitle">
      <template #body>
        <form data-testid="tree-name-form" class="flex flex-col gap-4" @submit.prevent="submitName">
          <UFormField :label="t('navigation.folderName')" required>
            <UInput v-model="nameDialog.name" data-testid="tree-name-input" maxlength="255" class="w-full" autofocus />
          </UFormField>
          <p v-if="nameDialog.error" data-testid="tree-name-error" class="text-sm text-error">{{ nameDialog.error }}</p>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="nameDialog.open = false" />
            <UButton
              data-testid="tree-name-submit"
              type="submit"
              :loading="nameDialog.busy"
              :label="nameDialog.mode === 'new-folder' ? t('navigation.create') : t('navigation.save')"
            />
          </div>
        </form>
      </template>
    </UModal>

    <UModal v-model:open="moveDialog.open" :title="t('navigation.moveTitle', { name: moveName })">
      <template #body>
        <form data-testid="tree-move-form" class="flex flex-col gap-4" @submit.prevent="submitMoveTo">
          <p v-if="moveOptions.length === 0" data-testid="tree-move-none" class="text-sm text-muted">{{ t('navigation.moveNoTarget') }}</p>
          <UFormField v-else :label="t('navigation.folder')" required>
            <USelect
              v-model="moveDialog.targetId"
              data-testid="tree-move-folder"
              :items="moveOptions"
              :aria-label="t('navigation.folder')"
              class="w-full"
            />
          </UFormField>
          <p v-if="moveDialog.error" data-testid="tree-move-error" class="text-sm text-error">{{ moveDialog.error }}</p>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="moveDialog.open = false" />
            <UButton
              data-testid="tree-move-submit"
              type="submit"
              :loading="moveDialog.busy"
              :disabled="!moveDialog.targetId"
              :label="t('navigation.moveSubmit')"
            />
          </div>
        </form>
      </template>
    </UModal>

    <UModal v-model:open="deleteDialog.open" :title="t('navigation.deleteTitle', { name: deleteDialog.node?.name ?? '' })">
      <template #body>
        <div data-testid="tree-delete-dialog" class="flex flex-col gap-4">
          <p data-testid="tree-delete-hint" class="text-sm text-default">
            {{
              deleteContents.folders + deleteContents.items === 0
                ? t('navigation.deleteEmptyHint')
                : t('navigation.deleteHint', { folders: deleteContents.folders, items: deleteContents.items })
            }}
          </p>
          <p v-if="deleteDialog.error" data-testid="tree-delete-error" class="text-sm text-error">{{ deleteDialog.error }}</p>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="deleteDialog.open = false" />
            <UButton data-testid="tree-delete-confirm" color="error" :loading="deleteDialog.busy" :label="t('navigation.deleteConfirm')" @click="confirmDelete" />
          </div>
        </div>
      </template>
    </UModal>
  </nav>
</template>
