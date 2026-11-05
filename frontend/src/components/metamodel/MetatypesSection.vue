<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel, metatypeById, reloadMetamodel } from '../../metamodel/store'
import MetatypeDetail from './MetatypeDetail.vue'
import MetatypeDialog from './MetatypeDialog.vue'
import { scopeLabel } from './messages'

// Metatype list and detail of SCR-030 (UC-04 basic flow steps 1–2, US-032, REQ-032 AC5; design D3
// of scr-030-metamodel-configuration). Below `lg` the list and the detail are shown one at a time.
// canConfigure: the re-import setting, which stays changeable in a locked metamodel (US-035)
const props = withDefaults(defineProps<{ selectedId?: string; canWrite: boolean; canConfigure?: boolean; fetchFn?: typeof fetch }>(), {
  // absent means like canWrite, not false
  canConfigure: undefined,
})
const router = useRouter()
const { t } = useI18n()

const search = ref('')
const creating = ref(false)

const selected = computed(() => metatypeById(props.selectedId))
const shown = computed(() => {
  const query = search.value.trim().toLowerCase()
  return metamodel.metatypes.filter(
    (metatype) =>
      query === '' ||
      label(labelKeys.metatype(metatype.id), metatype.name).toLowerCase().includes(query) ||
      metatype.name.toLowerCase().includes(query) ||
      (metatype.abbreviation ?? '').toLowerCase().includes(query),
  )
})

async function created(id: string) {
  creating.value = false
  await reloadMetamodel(props.fetchFn)
  await router.push(`/metamodel/metatypes/${id}`)
}

async function deleted() {
  await reloadMetamodel(props.fetchFn)
  await router.push('/metamodel/metatypes')
}
</script>

<template>
  <div class="grid grid-cols-1 gap-6 lg:grid-cols-[20rem_1fr]">
    <aside :class="selectedId ? 'hidden lg:flex' : 'flex'" class="flex-col gap-3" data-testid="metatype-list-panel">
      <div class="flex items-center justify-between gap-2">
        <h2 class="font-semibold text-highlighted">{{ t('metamodel.metatype.list', { count: metamodel.metatypes.length }) }}</h2>
        <UButton v-if="canWrite" data-testid="metatype-new" size="sm" icon="i-lucide-plus" :label="t('metamodel.new')" @click="creating = true" />
      </div>
      <UInput
        v-model="search"
        data-testid="metatype-search"
        icon="i-lucide-search"
        :placeholder="t('metamodel.search')"
        :aria-label="t('metamodel.search')"
        class="w-full"
      />
      <ul class="divide-y divide-default rounded-lg border border-default" data-testid="metatype-list">
        <li v-for="metatype in shown" :key="metatype.id">
          <RouterLink
            :to="`/metamodel/metatypes/${metatype.id}`"
            data-testid="metatype-item"
            :aria-current="metatype.id === selectedId ? 'page' : undefined"
            class="flex items-center gap-3 px-3 py-2 hover:bg-elevated"
            :class="metatype.id === selectedId ? 'ring-2 ring-inset ring-primary' : ''"
          >
            <span class="w-12 shrink-0 font-mono text-xs text-muted">{{ metatype.abbreviation }}</span>
            <span class="flex min-w-0 flex-1 flex-col">
              <span class="truncate font-medium text-highlighted">{{ label(labelKeys.metatype(metatype.id), metatype.name) }}</span>
              <span class="flex items-center gap-1 truncate text-xs text-muted">
                <UBadge
                  v-if="metatype.isConnection"
                  data-testid="metatype-connection-badge"
                  size="sm"
                  color="info"
                  variant="subtle"
                  :label="t('metamodel.connection.badge')"
                />
                {{ scopeLabel(metatype.scope, metatype.sourcePackage) }}
              </span>
            </span>
            <span class="text-xs text-muted">{{ t('metamodel.metatype.propertyCount', { count: metatype.propertyMappings?.length ?? 0 }) }}</span>
          </RouterLink>
        </li>
        <li v-if="shown.length === 0" class="px-3 py-4 text-center text-sm text-muted">{{ t('metamodel.noMatch') }}</li>
      </ul>
    </aside>

    <div :class="selectedId ? 'block' : 'hidden lg:block'">
      <UButton
        v-if="selectedId"
        class="mb-4 lg:hidden"
        data-testid="metatype-back"
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        to="/metamodel/metatypes"
        :label="t('metamodel.metatype.back')"
      />
      <MetatypeDetail
        v-if="selected"
        :key="selected.id"
        :metatype="selected"
        :can-write="canWrite"
        :can-configure="canConfigure ?? canWrite"
        :fetch-fn="fetchFn"
        @deleted="deleted"
      />
      <UAlert v-else-if="selectedId" data-testid="metatype-missing" color="neutral" variant="subtle" :description="t('metamodel.metatype.missing')" />
      <UEmpty v-else icon="i-lucide-shapes" :title="t('metamodel.metatype.choose')" :description="t('metamodel.metatype.chooseHint')" />
    </div>

    <UModal v-model:open="creating" :title="t('metamodel.metatype.new')" :description="t('metamodel.metatype.newHint')">
      <template #body>
        <MetatypeDialog :fetch-fn="fetchFn" @saved="created" @cancel="creating = false" />
      </template>
    </UModal>
  </div>
</template>
