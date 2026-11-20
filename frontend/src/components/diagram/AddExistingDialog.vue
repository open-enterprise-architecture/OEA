<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CANVAS_LIMITS, searchDiagramEntities, type DiagramEntity } from '../../api/diagramView'
import { rateLimitMessage } from '../../api/problem'

// "Add existing" (SCR-021, US-074 AC1; design D6 of us-074-diagram-canvas): searches the entities
// the viewpoint of the diagram allows by name and hands the chosen one to the canvas.
const props = defineProps<{ diagramId: string; fetchFn?: typeof fetch }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ pick: [entity: DiagramEntity] }>()
const { t } = useI18n()

/** The wait after the last keystroke before the search runs, in ms. */
const DEBOUNCE_MS = 300

const query = ref('')
const entities = ref<DiagramEntity[]>([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const searched = ref(false)
const failure = ref<string>()
let timer: ReturnType<typeof setTimeout> | undefined
let generation = 0

async function run(next: number) {
  const current = ++generation
  loading.value = true
  failure.value = undefined
  try {
    const outcome = await searchDiagramEntities(props.diagramId, { q: query.value, page: next }, props.fetchFn)
    if (current !== generation) {
      return
    }
    if (outcome.ok) {
      entities.value = next === 0 ? outcome.value.items : [...entities.value, ...outcome.value.items]
      page.value = next
      totalPages.value = outcome.value.page.totalPages
      searched.value = true
    } else {
      failure.value = rateLimitMessage(outcome.rejection) ?? t('diagrams.canvas.searchFailed')
    }
  } catch {
    if (current === generation) {
      failure.value = t('common.backendUnreachable')
    }
  } finally {
    if (current === generation) {
      loading.value = false
    }
  }
}

function schedule() {
  clearTimeout(timer)
  timer = setTimeout(() => run(0), DEBOUNCE_MS)
}

watch(open, (value) => {
  if (value) {
    query.value = ''
    entities.value = []
    searched.value = false
    void run(0)
  } else {
    clearTimeout(timer)
  }
})

onBeforeUnmount(() => clearTimeout(timer))

function pick(entity: DiagramEntity) {
  open.value = false
  emit('pick', entity)
}
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.canvas.addExistingTitle')" :description="t('diagrams.canvas.addExistingHint')">
    <template #body>
      <div class="flex flex-col gap-3" data-testid="add-existing-dialog">
        <UFormField :label="t('diagrams.canvas.searchLabel')">
          <UInput
            v-model="query"
            class="w-full"
            :maxlength="CANVAS_LIMITS.search"
            icon="i-lucide-search"
            data-testid="add-existing-query"
            @update:model-value="schedule"
          />
        </UFormField>
        <UAlert v-if="failure" data-testid="add-existing-failure" color="error" variant="subtle" :description="failure" />
        <p v-else-if="searched && entities.length === 0" class="text-sm text-muted" data-testid="add-existing-empty">{{ t('diagrams.canvas.noMatches') }}</p>
        <ul v-if="entities.length > 0" class="flex max-h-72 flex-col overflow-y-auto" data-testid="add-existing-results">
          <li v-for="entity in entities" :key="entity.id">
            <button
              type="button"
              class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-start text-sm hover:bg-elevated focus-visible:outline-2 focus-visible:outline-primary"
              :data-testid="`add-existing-${entity.id}`"
              @click="pick(entity)"
            >
              <span class="rounded-sm bg-elevated px-1 font-mono text-xs text-muted">{{ entity.metatype.abbreviation }}</span>
              <span class="min-w-0 flex-1 truncate">{{ entity.name }}</span>
              <span class="text-xs text-muted">{{ entity.metatype.name }}</span>
            </button>
          </li>
        </ul>
        <div class="flex justify-between gap-2">
          <UButton v-if="page + 1 < totalPages" data-testid="add-existing-more" color="neutral" variant="outline" :loading="loading" :label="t('diagrams.loadMore')" @click="run(page + 1)" />
          <UButton class="ms-auto" data-testid="add-existing-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
        </div>
      </div>
    </template>
  </UModal>
</template>
