<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { contentsOf, createFolder, deleteFolder, removeTreeItem, renameFolder, type TreeNode } from '../api/navigation'
import type { Outcome } from '../api/session'
import { rateLimitMessage } from '../api/problem'
import { loadTree, navigation, showTree } from '../navigation/store'
import NavigationFolder, { type FolderAction } from './NavigationFolder.vue'

// The navigation tree of SCR-055 (US-054; design D4 of us-054-catalog-navigation-tree): folders
// to expand, catalog entries as links, and for viewpoint:manage the folder actions and "Remove
// from tree". The expand state is kept per session.

const props = defineProps<{ canManage: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ navigate: [] }>()
const { t } = useI18n()

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

async function act(action: FolderAction) {
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
