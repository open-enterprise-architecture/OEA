<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteViewpoint, fetchViewpoints, type Viewpoint } from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import DeleteDialog from './DeleteDialog.vue'
import ViewpointDialog from './ViewpointDialog.vue'

// SCR-031 "Viewpoints", a section of the metamodel page (US-090, US-093, UC-12; design D4 of
// us-090-viewpoints): the generic viewpoints with their allowed metatypes and connection types.
// Holders of viewpoint:manage create, edit and delete them; deleting asks for confirmation.
const props = defineProps<{ canManage: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const viewpoints = ref<Viewpoint[]>([])
const loaded = ref(false)
const failure = ref<string>()
const editing = ref<{ viewpoint?: Viewpoint }>()
const deleting = ref<Viewpoint>()

async function load() {
  try {
    viewpoints.value = await fetchViewpoints(props.fetchFn)
    failure.value = undefined
  } catch {
    failure.value = t('metamodel.viewpoint.loadFailed')
  } finally {
    loaded.value = true
  }
}

onMounted(load)

function current(id: string | undefined): Viewpoint | undefined {
  return viewpoints.value.find((viewpoint) => viewpoint.id === id)
}

async function saved() {
  editing.value = undefined
  await load()
}
</script>

<template>
  <section class="flex flex-col gap-3" data-testid="viewpoints-section">
    <div class="flex items-center justify-between gap-2">
      <div class="flex flex-col gap-1">
        <h2 class="font-semibold text-highlighted">{{ t('metamodel.viewpoint.list', { count: viewpoints.length }) }}</h2>
        <p class="text-sm text-muted">{{ t('metamodel.viewpoint.intro') }}</p>
      </div>
      <UButton v-if="canManage" data-testid="viewpoint-new" size="sm" icon="i-lucide-plus" :label="t('metamodel.viewpoint.new')" @click="editing = {}" />
    </div>
    <UAlert v-if="failure" data-testid="viewpoints-failure" color="error" variant="subtle" :description="failure" />
    <ul v-else-if="loaded" class="divide-y divide-default rounded-lg border border-default" data-testid="viewpoint-list">
      <li v-for="viewpoint in viewpoints" :key="viewpoint.id" data-testid="viewpoint-row" class="flex items-start gap-3 px-3 py-2">
        <span class="flex min-w-0 flex-1 flex-col gap-1">
          <span class="font-medium text-highlighted">{{ viewpoint.name }}</span>
          <span v-if="viewpoint.description" class="text-sm text-muted">{{ viewpoint.description }}</span>
          <span class="flex flex-wrap gap-1">
            <UBadge
              v-for="metatype in viewpoint.allowedMetatypes"
              :key="metatype.id"
              color="neutral"
              variant="subtle"
              size="sm"
              :label="label(labelKeys.metatype(metatype.id), metatype.name)"
            />
            <UBadge
              v-for="relation in viewpoint.allowedRelations"
              :key="relation.id"
              color="info"
              variant="subtle"
              size="sm"
              icon="i-lucide-arrow-right-left"
              :label="label(labelKeys.metatype(relation.id), relation.name)"
            />
          </span>
        </span>
        <template v-if="canManage">
          <UButton data-testid="viewpoint-edit" size="xs" color="neutral" variant="ghost" icon="i-lucide-pencil" :aria-label="t('metamodel.edit')" @click="editing = { viewpoint }" />
          <UButton data-testid="viewpoint-delete" size="xs" color="error" variant="ghost" icon="i-lucide-trash-2" :aria-label="t('metamodel.delete')" @click="deleting = viewpoint" />
        </template>
      </li>
      <li v-if="viewpoints.length === 0" class="px-3 py-4 text-center text-sm text-muted">{{ t('metamodel.viewpoint.none') }}</li>
    </ul>

    <UModal
      :open="editing !== undefined"
      :title="editing?.viewpoint ? t('metamodel.viewpoint.edit') : t('metamodel.viewpoint.new')"
      @update:open="(open: boolean) => !open && (editing = undefined)"
    >
      <template #body>
        <ViewpointDialog
          v-if="editing"
          :viewpoint="editing.viewpoint"
          :current="current(editing.viewpoint?.id)"
          :fetch-fn="fetchFn"
          @saved="saved"
          @stale="load"
          @cancel="editing = undefined"
        />
      </template>
    </UModal>
    <DeleteDialog
      :open="deleting !== undefined"
      :title="t('metamodel.viewpoint.delete')"
      :description="deleting ? t('metamodel.viewpoint.deleteHint', { name: deleting.name }) : ''"
      :version="deleting?.version"
      :run="(version) => deleteViewpoint(deleting!.id, version, fetchFn)"
      @update:open="(open: boolean) => !open && (deleting = undefined)"
      @stale="load"
      @done="load"
    >
      <template #current>{{ t('metamodel.collision.changed', { name: deleting?.name }) }}</template>
    </DeleteDialog>
  </section>
</template>
