<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteViewpoint, fetchNotationElements, fetchViewpoints, type NotationElement, type Viewpoint } from '../../api/metamodel'
import DeleteDialog from './DeleteDialog.vue'
import ViewpointDialog from './ViewpointDialog.vue'
import ViewpointSummary from './ViewpointSummary.vue'

// SCR-031 "Viewpoints", a section of the metamodel page (US-090 to US-093, UC-12; design D4 of
// us-090-viewpoints, D6 of us-091-viewpoint-notations): the viewpoints in two groups, the
// system-defined ones of metamodel packages (with a lock, read-only, without actions) and the ones
// of the organization, each with its notation and allowed metatypes and connection types. Holders
// of viewpoint:manage create, edit and delete the organization's viewpoints; deleting asks for
// confirmation. The element catalog of the notations is read once for the dialog.
const props = defineProps<{ canManage: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const viewpoints = ref<Viewpoint[]>([])
const catalog = ref<NotationElement[]>([])
const catalogFailed = ref(false)
const systemDefined = computed(() => viewpoints.value.filter((viewpoint) => viewpoint.viewpointType === 'system-defined'))
const organization = computed(() => viewpoints.value.filter((viewpoint) => viewpoint.viewpointType !== 'system-defined'))
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

async function loadCatalog() {
  try {
    catalog.value = await fetchNotationElements(props.fetchFn)
    catalogFailed.value = false
  } catch {
    catalogFailed.value = true
  }
}

onMounted(() => {
  void load()
  void loadCatalog()
})

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
    <template v-else-if="loaded">
      <section v-if="systemDefined.length > 0" class="flex flex-col gap-1" data-testid="viewpoint-group-system" aria-labelledby="viewpoint-group-system-title">
        <h3 id="viewpoint-group-system-title" class="flex items-center gap-1.5 text-sm font-semibold text-highlighted">
          <UIcon name="i-lucide-lock" class="size-4 text-muted" aria-hidden="true" />
          {{ t('metamodel.viewpoint.groupSystem') }}
        </h3>
        <p class="text-sm text-muted">{{ t('metamodel.viewpoint.systemHint') }}</p>
        <ul class="divide-y divide-default rounded-lg border border-default" data-testid="viewpoint-list-system">
          <li v-for="viewpoint in systemDefined" :key="viewpoint.id" data-testid="viewpoint-row" data-viewpoint-type="system-defined" class="flex items-start gap-3 px-3 py-2">
            <ViewpointSummary :viewpoint="viewpoint" />
            <span class="flex items-center gap-1 text-xs text-muted" data-testid="viewpoint-lock">
              <UIcon name="i-lucide-lock" class="size-4" aria-hidden="true" />
              <span class="sr-only">{{ t('metamodel.viewpoint.systemLock') }}</span>
              <span v-if="viewpoint.sourcePackage">{{ t('metamodel.viewpoint.fromPackage', { id: viewpoint.sourcePackage.packageId, version: viewpoint.sourcePackage.version }) }}</span>
            </span>
          </li>
        </ul>
      </section>
      <section class="flex flex-col gap-1" data-testid="viewpoint-group-organization" aria-labelledby="viewpoint-group-organization-title">
        <h3 id="viewpoint-group-organization-title" class="text-sm font-semibold text-highlighted">{{ t('metamodel.viewpoint.groupOrganization') }}</h3>
        <ul class="divide-y divide-default rounded-lg border border-default" data-testid="viewpoint-list">
          <li v-for="viewpoint in organization" :key="viewpoint.id" data-testid="viewpoint-row" data-viewpoint-type="user-defined" class="flex items-start gap-3 px-3 py-2">
            <ViewpointSummary :viewpoint="viewpoint" />
            <template v-if="canManage">
              <UButton data-testid="viewpoint-edit" size="xs" color="neutral" variant="ghost" icon="i-lucide-pencil" :aria-label="t('metamodel.edit')" @click="editing = { viewpoint }" />
              <UButton data-testid="viewpoint-delete" size="xs" color="error" variant="ghost" icon="i-lucide-trash-2" :aria-label="t('metamodel.delete')" @click="deleting = viewpoint" />
            </template>
          </li>
          <li v-if="organization.length === 0" class="px-3 py-4 text-center text-sm text-muted">{{ t('metamodel.viewpoint.none') }}</li>
        </ul>
      </section>
    </template>

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
          :catalog="catalog"
          :catalog-failed="catalogFailed"
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
      :warning="deleting && deleting.diagramCount > 0 ? t('metamodel.viewpoint.deleteDiagrams', { count: deleting.diagramCount }, deleting.diagramCount) : undefined"
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
