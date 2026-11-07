<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { folderPaths, placeCatalog } from '../api/navigation'
import { rateLimitMessage } from '../api/problem'
import { loadTree, navigation, showTree } from '../navigation/store'

// "Place in navigation" of SCR-020 (US-054 AC1, AC5; design D4 of us-054-catalog-navigation-tree):
// the folder as its path from the root and an optional display label.

const props = defineProps<{ catalogId: string; catalogName: string; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const open = ref(false)
const folderId = ref<string>()
const label = ref('')
const error = ref<string>()
const busy = ref(false)
const placed = ref<string>()

const folders = computed(() => (navigation.tree ? folderPaths(navigation.tree) : []))
const items = computed(() => folders.value.map((folder) => ({ label: folder.path, value: folder.id })))

async function start() {
  error.value = undefined
  label.value = ''
  placed.value = undefined
  open.value = true
  await loadTree(props.fetchFn)
  if (!navigation.tree) {
    error.value = t('navigation.loadFailed')
    return
  }
  if (!folders.value.some((folder) => folder.id === folderId.value)) {
    folderId.value = navigation.tree.id
  }
}

async function place() {
  const target = folderId.value
  if (!target) {
    return
  }
  busy.value = true
  error.value = undefined
  try {
    const outcome = await placeCatalog(target, props.catalogId, label.value.trim() || null, props.fetchFn)
    if (outcome.ok) {
      showTree(outcome.value)
      placed.value = t('navigation.placed', { path: folders.value.find((folder) => folder.id === target)?.path ?? '' })
      open.value = false
    } else if (outcome.rejection.status === 422 && outcome.rejection.invalidFields.includes('referenceId')) {
      error.value = t('navigation.alreadyPlaced')
    } else {
      error.value = rateLimitMessage(outcome.rejection) ?? t('navigation.changeFailed')
    }
  } catch {
    error.value = t('navigation.changeFailed')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex items-center gap-2">
    <span v-if="placed" data-testid="place-done" class="text-sm text-muted">{{ placed }}</span>
    <UButton
      data-testid="place-in-navigation"
      icon="i-lucide-folder-tree"
      color="neutral"
      variant="outline"
      :label="t('navigation.place')"
      @click="start"
    />
    <UModal v-model:open="open" :title="t('navigation.placeTitle', { name: catalogName })">
      <template #body>
        <form data-testid="place-form" class="flex flex-col gap-4" @submit.prevent="place">
          <UFormField :label="t('navigation.folder')" required>
            <USelect
              v-model="folderId"
              data-testid="place-folder"
              :items="items"
              :aria-label="t('navigation.folder')"
              :loading="navigation.loading"
              class="w-full"
            />
          </UFormField>
          <UFormField :label="t('navigation.displayLabel')" :help="t('navigation.displayLabelHint')">
            <UInput v-model="label" data-testid="place-label" maxlength="255" class="w-full" />
          </UFormField>
          <p v-if="error" data-testid="place-error" class="text-sm text-error">{{ error }}</p>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
            <UButton data-testid="place-submit" type="submit" :loading="busy" :disabled="!folderId" :label="t('navigation.placeSubmit')" />
          </div>
        </form>
      </template>
    </UModal>
  </div>
</template>
