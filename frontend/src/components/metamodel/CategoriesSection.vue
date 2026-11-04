<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteCategory, type PropertyCategory } from '../../api/metamodel'
import { metamodel, reloadMetamodel } from '../../metamodel/store'
import CategoryDialog from './CategoryDialog.vue'
import DeleteDialog from './DeleteDialog.vue'
import { scopeLabel } from './messages'

// Property categories of SCR-030 (US-032; design D3 of scr-030-metamodel-configuration).
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const editing = ref<{ category?: PropertyCategory }>()
const deleting = ref<PropertyCategory>()

function scopeOf(category: PropertyCategory): string {
  return category.scope ?? 'organization'
}

async function saved() {
  editing.value = undefined
  await reloadMetamodel(props.fetchFn)
}
</script>

<template>
  <section class="flex flex-col gap-3">
    <div class="flex items-center justify-between gap-2">
      <h2 class="font-semibold text-highlighted">{{ t('metamodel.category.list', { count: metamodel.categories.length }) }}</h2>
      <UButton v-if="canWrite" data-testid="category-new" size="sm" icon="i-lucide-plus" :label="t('metamodel.new')" @click="editing = {}" />
    </div>
    <ul class="divide-y divide-default rounded-lg border border-default" data-testid="category-list">
      <li v-for="category in metamodel.categories" :key="category.id" data-testid="category-row" class="flex items-center gap-3 px-3 py-2">
        <span class="flex min-w-0 flex-1 flex-col">
          <span class="font-medium text-highlighted">{{ category.name }}</span>
          <span v-if="category.description" class="text-sm text-muted">{{ category.description }}</span>
        </span>
        <span class="text-xs text-muted">{{ scopeLabel(scopeOf(category)) }}</span>
        <template v-if="canWrite && scopeOf(category) === 'organization'">
          <UButton data-testid="category-edit" size="xs" color="neutral" variant="ghost" icon="i-lucide-pencil" :aria-label="t('metamodel.edit')" @click="editing = { category }" />
          <UButton data-testid="category-delete" size="xs" color="error" variant="ghost" icon="i-lucide-trash-2" :aria-label="t('metamodel.delete')" @click="deleting = category" />
        </template>
      </li>
      <li v-if="metamodel.categories.length === 0" class="px-3 py-4 text-center text-sm text-muted">{{ t('metamodel.category.none') }}</li>
    </ul>

    <UModal
      :open="editing !== undefined"
      :title="editing?.category ? t('metamodel.category.edit') : t('metamodel.category.new')"
      @update:open="(open: boolean) => !open && (editing = undefined)"
    >
      <template #body>
        <CategoryDialog v-if="editing" :category="editing.category" :fetch-fn="fetchFn" @saved="saved" @cancel="editing = undefined" />
      </template>
    </UModal>
    <DeleteDialog
      :open="deleting !== undefined"
      :title="t('metamodel.category.delete')"
      :description="deleting ? t('metamodel.category.deleteHint', { name: deleting.name }) : ''"
      :version="deleting?.version"
      :run="(version) => deleteCategory(deleting!.id, version, fetchFn)"
      @update:open="(open: boolean) => !open && (deleting = undefined)"
      @stale="reloadMetamodel(fetchFn)"
      @done="reloadMetamodel(fetchFn)"
    >
      <template #current>{{ t('metamodel.collision.changed', { name: deleting?.name }) }}</template>
    </DeleteDialog>
  </section>
</template>
