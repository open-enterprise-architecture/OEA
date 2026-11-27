<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { changeDiagramViewpoint } from '../../api/diagrams'
import { fetchDiagramView, type DiagramView } from '../../api/diagramView'
import { fetchViewpoints, type Viewpoint } from '../../api/metamodel'
import { rateLimitMessage } from '../../api/problem'
import { previewConflicts } from './canvasModel'

// "Change viewpoint" (SCR-021, US-074, UC-10; design D5 of us-074-diagram-viewpoint-change): the viewpoints to
// choose from and, before the change, the shapes and edges of the diagram that the chosen viewpoint does not
// allow: they become conflicts. The change is saved at once; the canvas then reloads with the new viewpoint.
const props = defineProps<{ view: DiagramView; fetchFn?: typeof fetch }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ changed: [view: DiagramView]; stale: [] }>()
const { t } = useI18n()

const viewpoints = ref<Viewpoint[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const chosen = ref<string>()
const submitting = ref(false)
const failure = ref<string>()
const stale = ref(false)

watch(open, async (value) => {
  if (!value) {
    return
  }
  chosen.value = props.view.viewpoint.id
  failure.value = undefined
  stale.value = false
  loadFailed.value = false
  loading.value = true
  try {
    viewpoints.value = await fetchViewpoints(props.fetchFn)
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
})

const items = computed(() =>
  viewpoints.value.map((viewpoint) => ({
    label: `${viewpoint.name} (${viewpoint.notation})`,
    value: viewpoint.id,
    description: viewpoint.id === props.view.viewpoint.id ? t('diagrams.viewpointChange.current') : undefined,
  })),
)
const target = computed(() => viewpoints.value.find((viewpoint) => viewpoint.id === chosen.value))
const changing = computed(() => target.value !== undefined && target.value.id !== props.view.viewpoint.id)
/** The shapes and edges the chosen viewpoint does not allow (exact type, as the save checks it). */
const preview = computed(() => (target.value ? previewConflicts(props.view, target.value) : { shapes: [], edges: [] }))

async function submit() {
  if (!changing.value || !target.value) {
    return
  }
  submitting.value = true
  failure.value = undefined
  try {
    const outcome = await changeDiagramViewpoint(props.view.diagram.id, target.value.id, props.view.diagram.version, props.fetchFn)
    if (outcome.ok) {
      const next = await fetchDiagramView(props.view.diagram.id, props.fetchFn)
      open.value = false
      if (next) {
        emit('changed', next)
      } else {
        emit('stale')
      }
      return
    }
    const rejection = outcome.rejection
    if (rejection.status === 412) {
      stale.value = true
    } else if (rejection.status === 422) {
      failure.value = t('diagrams.viewpointChange.unknown')
    } else if (rejection.status === 404) {
      failure.value = t('diagrams.gone')
    } else if (rejection.status === 403) {
      failure.value = t('diagrams.forbidden')
    } else {
      failure.value = rateLimitMessage(rejection) ?? t('diagrams.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}

function reload() {
  open.value = false
  emit('stale')
}
</script>

<template>
  <UModal v-model:open="open" :title="t('diagrams.viewpointChange.title')" :description="t('diagrams.viewpointChange.hint')">
    <template #body>
      <form class="flex flex-col gap-4" novalidate data-testid="viewpoint-change-form" @submit.prevent="submit">
        <UAlert v-if="loadFailed" data-testid="viewpoint-change-load-failed" color="warning" variant="subtle" :description="t('diagrams.viewpointsFailed')" />
        <UAlert v-if="failure" data-testid="viewpoint-change-failure" color="error" variant="subtle" :description="failure" />
        <UAlert v-if="stale" data-testid="viewpoint-change-stale" color="error" variant="subtle" :description="t('diagrams.stale')">
          <template #actions>
            <UButton data-testid="viewpoint-change-reload" size="xs" color="neutral" variant="outline" :label="t('diagrams.canvas.reload')" @click="reload" />
          </template>
        </UAlert>
        <URadioGroup v-model="chosen" data-testid="viewpoint-change-choice" :legend="t('diagrams.viewpoint')" :items="items" :disabled="loading" />
        <div v-if="changing" data-testid="viewpoint-change-preview" role="status" class="text-sm">
          <p v-if="preview.shapes.length + preview.edges.length === 0" data-testid="viewpoint-change-none">{{ t('diagrams.viewpointChange.none') }}</p>
          <template v-else>
            <div data-testid="viewpoint-change-count" class="font-medium text-highlighted">
              <p v-if="preview.shapes.length > 0">{{ t('diagrams.viewpointChange.willConflictShapes', { count: preview.shapes.length }, preview.shapes.length) }}</p>
              <p v-if="preview.edges.length > 0">{{ t('diagrams.viewpointChange.willConflictEdges', { count: preview.edges.length }, preview.edges.length) }}</p>
            </div>
            <ul class="mt-1 list-disc ps-5 text-muted" data-testid="viewpoint-change-names">
              <li v-for="(name, index) in [...preview.shapes, ...preview.edges]" :key="index">{{ name }}</li>
            </ul>
          </template>
        </div>
        <div class="flex justify-end gap-2">
          <UButton data-testid="viewpoint-change-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton data-testid="viewpoint-change-submit" type="submit" :loading="submitting" :disabled="!changing || loading" :label="t('diagrams.viewpointChange.submit')" />
        </div>
      </form>
    </template>
  </UModal>
</template>
