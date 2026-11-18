<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchBrowserItems, type BrowserItem } from '../../api/navigation'

// "Move to…" of a component (US-135 AC1; design D5 of us-135-component-hierarchy): for keyboard and touch, where
// dragging is not possible. The dialog lists the components level by level, as the browser does, with a filter over the
// loaded names, "Load more" and "Root level" as the first choice. It offers neither the component itself nor its
// children; whether the target is valid below it is decided by the server.

const props = defineProps<{ item: BrowserItem | null; fetchFn?: typeof fetch }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ choose: [parentId: number | null] }>()
const { t } = useI18n()

const path = ref<{ id: number; name: string }[]>([])
const items = ref<BrowserItem[]>([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const failed = ref(false)
const filter = ref('')

const level = computed(() => path.value[path.value.length - 1])
const visible = computed(() => {
  const needle = filter.value.trim().toLowerCase()
  return items.value.filter((candidate) => candidate.id !== props.item?.id && (!needle || candidate.name.toLowerCase().includes(needle)))
})

async function load(reset: boolean) {
  if (reset) {
    items.value = []
    total.value = 0
    page.value = 0
  }
  loading.value = true
  failed.value = false
  try {
    const result = await fetchBrowserItems({ scope: 'current-state', category: 'components', page: page.value, ...(level.value ? { parentEntityId: level.value.id } : {}) }, props.fetchFn)
    items.value.push(...result.items)
    total.value = result.page.totalElements
    page.value += 1
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

watch(open, (value) => {
  if (value) {
    path.value = []
    filter.value = ''
    void load(true)
  }
})

function enter(child: BrowserItem) {
  path.value.push({ id: Number(child.id), name: child.name })
  filter.value = ''
  void load(true)
}

function back(index: number) {
  path.value = path.value.slice(0, index)
  filter.value = ''
  void load(true)
}

function choose(parentId: number | null) {
  open.value = false
  emit('choose', parentId)
}
</script>

<template>
  <UModal v-model:open="open" :title="t('browser.moveToTitle', { name: item?.name ?? '' })">
    <template #body>
      <div data-testid="move-to-dialog" class="flex flex-col gap-3">
        <UButton data-testid="move-to-root" color="neutral" variant="outline" icon="i-lucide-layers" :label="t('browser.rootLevel')" @click="choose(null)" />
        <nav :aria-label="t('browser.moveToPath')" class="flex flex-wrap items-center gap-1 text-sm">
          <UButton data-testid="move-to-crumb" size="xs" color="neutral" variant="link" :label="t('browser.components')" @click="back(0)" />
          <template v-for="(step, index) in path" :key="step.id">
            <span aria-hidden="true">/</span>
            <UButton data-testid="move-to-crumb" size="xs" color="neutral" variant="link" :label="step.name" @click="back(index + 1)" />
          </template>
        </nav>
        <UInput v-model="filter" data-testid="move-to-filter" :aria-label="t('browser.moveToFilter')" :placeholder="t('browser.moveToFilter')" icon="i-lucide-search" class="w-full" />
        <p v-if="failed" data-testid="move-to-failed" class="text-sm text-error">{{ t('browser.loadFailed') }}</p>
        <ul class="flex max-h-72 flex-col gap-1 overflow-y-auto">
          <li v-for="child in visible" :key="child.id" data-testid="move-to-row" class="flex items-center gap-1">
            <UIcon :name="child.structuringAid ? 'i-lucide-folder' : 'i-lucide-box'" class="size-4 shrink-0 text-muted" aria-hidden="true" />
            <span class="min-w-0 flex-1 truncate text-sm">{{ child.name }}</span>
            <UButton v-if="child.childCount > 0" data-testid="move-to-open" size="xs" color="neutral" variant="ghost" icon="i-lucide-chevron-right" :aria-label="t('browser.moveToOpen', { name: child.name })" @click="enter(child)" />
            <UButton data-testid="move-to-choose" size="xs" color="neutral" variant="outline" :label="t('browser.moveToChoose')" :aria-label="t('browser.moveToChooseName', { name: child.name })" @click="choose(Number(child.id))" />
          </li>
        </ul>
        <p v-if="loading" class="text-sm text-muted">{{ t('browser.loading') }}</p>
        <UButton v-else-if="items.length < total" data-testid="move-to-more" size="xs" color="neutral" variant="link" :label="t('browser.loadMore')" @click="load(false)" />
        <div class="flex justify-end">
          <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
        </div>
      </div>
    </template>
  </UModal>
</template>
