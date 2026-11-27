<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CANVAS_LIMITS, searchAssignmentTargets, type DiagramEntity } from '../../api/diagramView'
import { rateLimitMessage } from '../../api/problem'
import { SEARCH_PART, type Part } from './assignments'

// The search of an assignment (SCR-021, US-075 AC1 to AC4, US-076 AC1; design D6 of us-075-lane-assignments): the active
// organizational units, roles or persons by name, and "Create new" with the typed name and the type of the part. The
// choice goes to the canvas, which keeps it with the unsaved changes.
const props = defineProps<{
  diagramId: string
  part: Part
  /** the title: "Assign organizational unit", "Set participant", "Assign role" or "Add person" */
  title: string
  /** the metatype of the part and its subtypes, for "Create new" */
  types: { id: string; name: string }[]
  /** the entities that are assigned already, by ID, are marked */
  assigned?: number[]
  fetchFn?: typeof fetch
}>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ pick: [entity: { entityId: number; name: string }]; create: [created: { metatypeId: string; name: string }] }>()
const { t } = useI18n()

const DEBOUNCE_MS = 300

const query = ref('')
const entities = ref<DiagramEntity[]>([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const searched = ref(false)
const failure = ref<string>()
const typeId = ref<string>()
let timer: ReturnType<typeof setTimeout> | undefined
let generation = 0

const name = computed(() => query.value.trim())
const chosenType = computed(() => typeId.value ?? props.types[0]?.id)

async function run(next: number) {
  const current = ++generation
  loading.value = true
  failure.value = undefined
  try {
    const outcome = await searchAssignmentTargets(props.diagramId, SEARCH_PART[props.part], query.value, props.fetchFn)
    if (current !== generation) {
      return
    }
    if (outcome.ok) {
      entities.value = next === 0 ? outcome.value.items : [...entities.value, ...outcome.value.items]
      page.value = next
      totalPages.value = outcome.value.page.totalPages
      searched.value = true
    } else {
      failure.value = rateLimitMessage(outcome.rejection) ?? t('diagrams.assignments.searchFailed')
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
    typeId.value = undefined
    void run(0)
  } else {
    clearTimeout(timer)
  }
})

onBeforeUnmount(() => clearTimeout(timer))

function pick(entity: DiagramEntity) {
  open.value = false
  emit('pick', { entityId: entity.id, name: entity.name })
}

function create() {
  if (!chosenType.value || name.value.length === 0) {
    return
  }
  open.value = false
  emit('create', { metatypeId: chosenType.value, name: name.value })
}
</script>

<template>
  <UModal v-model:open="open" :title="title" :description="t('diagrams.assignments.hint')">
    <template #body>
      <div class="flex flex-col gap-3" data-testid="assignment-dialog">
        <UFormField :label="t('diagrams.canvas.searchLabel')">
          <UInput v-model="query" class="w-full" :maxlength="CANVAS_LIMITS.entityName" icon="i-lucide-search" data-testid="assignment-query" @update:model-value="schedule" />
        </UFormField>
        <UAlert v-if="failure" data-testid="assignment-failure" color="error" variant="subtle" :description="failure" />
        <p v-else-if="searched && entities.length === 0" class="text-sm text-muted" data-testid="assignment-empty">{{ t('diagrams.canvas.noMatches') }}</p>
        <ul v-if="entities.length > 0" class="flex max-h-60 flex-col overflow-y-auto" data-testid="assignment-results">
          <li v-for="entity in entities" :key="entity.id">
            <button
              type="button"
              class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-start text-sm hover:bg-elevated focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50"
              :disabled="assigned?.includes(entity.id)"
              :data-testid="`assignment-${entity.id}`"
              @click="pick(entity)"
            >
              <span class="rounded-sm bg-elevated px-1 font-mono text-xs text-muted">{{ entity.metatype.abbreviation }}</span>
              <span class="min-w-0 flex-1 truncate">{{ entity.name }}</span>
              <span v-if="assigned?.includes(entity.id)" class="text-xs text-muted">{{ t('diagrams.assignments.assigned') }}</span>
              <span v-else class="text-xs text-muted">{{ entity.metatype.name }}</span>
            </button>
          </li>
        </ul>
        <UButton v-if="page + 1 < totalPages" data-testid="assignment-more" color="neutral" variant="outline" :loading="loading" :label="t('diagrams.loadMore')" @click="run(page + 1)" />
        <div class="flex flex-col gap-2 rounded-md border border-default p-2" data-testid="assignment-create">
          <USelect v-if="types.length > 1" v-model="typeId" :items="types.map((type) => ({ label: type.name, value: type.id }))" :placeholder="types[0]?.name" class="w-full" :aria-label="t('diagrams.assignments.type')" data-testid="assignment-create-type" />
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            :disabled="name.length === 0"
            :label="name.length > 0 ? t('diagrams.assignments.createNamed', { name }) : t('diagrams.assignments.create')"
            data-testid="assignment-create-button"
            @click="create"
          />
        </div>
        <UButton class="ms-auto" data-testid="assignment-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
      </div>
    </template>
  </UModal>
</template>
