<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import type { Diagram } from '../api/diagrams'
import { fetchDiagramView, type DiagramView } from '../api/diagramView'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import DiagramCanvas from '../components/diagram/DiagramCanvas.vue'
import DiagramDelete from '../components/diagram/DiagramDelete.vue'
import DiagramForm from '../components/diagram/DiagramForm.vue'
import PlaceInNavigation from '../components/PlaceInNavigation.vue'
import { currentLanguage } from '../i18n'
import { refreshSession, session } from '../session/store'

// One diagram (UC-10, US-074; design D6 of us-074-diagrams and us-074-diagram-canvas): name,
// description and viewpoint, and the canvas with its shapes (SCR-021). Leaving the page with unsaved
// changes of the layout asks first.
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

const diagram = ref<Diagram>()
const view = ref<DiagramView>()
const dirty = ref(false)
const leaving = ref(false)
let decide: ((leave: boolean) => void) | undefined
const loaded = ref(false)
const failure = ref<string>()
const renaming = ref(false)
const deleting = ref(false)

const granted = computed(() => (session.me?.permissions ?? []).map((entry) => entry.permission))
const canRead = computed(() => granted.value.includes('entity:read'))
const canWrite = computed(() => granted.value.includes('entity:write'))
// placing the diagram in the navigation tree needs viewpoint:manage and entity:read (US-095; design D5 of us-074-diagrams)
const canPlace = computed(() => granted.value.includes('viewpoint:manage') && granted.value.includes('entity:read'))

async function load() {
  try {
    view.value = await fetchDiagramView(props.id, props.fetchFn)
    diagram.value = view.value?.diagram
    failure.value = undefined
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('diagrams.loadFailed')
  } finally {
    loaded.value = true
  }
}

function warnBeforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault()
  }
}

onBeforeRouteLeave(() => {
  if (!dirty.value) {
    return true
  }
  return new Promise<boolean>((resolve) => {
    decide = resolve
    leaving.value = true
  })
})

function answer(leave: boolean) {
  leaving.value = false
  decide?.(leave)
  decide = undefined
}

onBeforeUnmount(() => window.removeEventListener('beforeunload', warnBeforeUnload))

onMounted(async () => {
  window.addEventListener('beforeunload', warnBeforeUnload)
  await refreshSession(props.fetchFn)
  if (canRead.value) {
    await load()
  } else {
    loaded.value = true
  }
})

function shown(at: string): string {
  return new Date(at).toLocaleString(currentLanguage(), { dateStyle: 'medium', timeStyle: 'short' })
}

async function renamed(updated: Diagram) {
  renaming.value = false
  diagram.value = updated
}

function saved(next: DiagramView) {
  view.value = next
  diagram.value = next.diagram
}

async function removed() {
  dirty.value = false
  await router.push('/diagrams')
}
</script>

<template>
  <main class="mx-auto flex max-w-6xl flex-col gap-6 p-8">
    <RouterLink to="/diagrams" class="text-sm text-muted hover:underline" data-testid="diagram-back">{{ t('diagrams.back') }}</RouterLink>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert v-else-if="session.me && !canRead" data-testid="no-diagram-access" color="neutral" variant="subtle" :description="t('diagrams.noAccess')" />
    <UAlert v-if="failure" data-testid="diagram-load-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-else-if="loaded && canRead && !diagram" data-testid="diagram-not-found" color="neutral" variant="subtle" :description="t('diagrams.gone')" />

    <template v-if="diagram">
      <div class="flex flex-wrap items-center gap-3">
        <h1 data-testid="diagram-title" class="text-2xl font-semibold text-highlighted">{{ diagram.name }}</h1>
        <div class="ms-auto flex flex-wrap items-center gap-2">
          <PlaceInNavigation
            v-if="canPlace"
            :placement="{ itemType: 'diagram', referenceId: diagram.id, name: diagram.name }"
            :fetch-fn="fetchFn"
          />
          <template v-if="canWrite">
            <UButton data-testid="diagram-rename" color="neutral" variant="outline" icon="i-lucide-pencil" :label="t('diagrams.renameAction')" @click="renaming = true" />
            <UButton data-testid="diagram-delete" color="error" variant="outline" icon="i-lucide-trash-2" :label="t('diagrams.delete')" @click="deleting = true" />
          </template>
        </div>
      </div>
      <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
        <dt class="text-muted">{{ t('diagrams.viewpoint') }}</dt>
        <dd data-testid="diagram-viewpoint-name">
          {{ diagram.viewpoint.name }}
          <UBadge color="neutral" variant="outline" :label="diagram.viewpoint.notation" />
        </dd>
        <dt class="text-muted">{{ t('diagrams.description') }}</dt>
        <dd data-testid="diagram-description-text">{{ diagram.description || t('diagrams.noDescription') }}</dd>
        <dt class="text-muted">{{ t('diagrams.createdBy') }}</dt>
        <dd>{{ diagram.createdBy.displayName }}</dd>
        <dt class="text-muted">{{ t('diagrams.lastChange') }}</dt>
        <dd>{{ shown(diagram.updatedAt) }}</dd>
      </dl>
      <DiagramCanvas
        v-if="view"
        :view="view"
        :version="diagram.version"
        :editable="canWrite"
        :fetch-fn="fetchFn"
        @saved="saved"
        @reload="load"
        @dirty="dirty = $event"
      />
    </template>

    <UModal v-model:open="renaming" :title="t('diagrams.renameTitle')">
      <template #body>
        <DiagramForm v-if="renaming && diagram" :diagram="diagram" :fetch-fn="fetchFn" @saved="renamed" @cancel="renaming = false" />
      </template>
    </UModal>
    <DiagramDelete v-model:open="deleting" :diagram="diagram" :fetch-fn="fetchFn" @done="removed" @stale="load" />
    <UModal :open="leaving" :title="t('diagrams.canvas.leaveTitle')" :description="t('diagrams.canvas.leave')" @update:open="(value: boolean) => !value && answer(false)">
      <template #body>
        <div class="flex justify-end gap-2" data-testid="diagram-leave">
          <UButton data-testid="diagram-leave-stay" color="neutral" variant="ghost" :label="t('diagrams.canvas.keepEditing')" @click="answer(false)" />
          <UButton data-testid="diagram-leave-discard" color="error" :label="t('diagrams.canvas.discard')" @click="answer(true)" />
        </div>
      </template>
    </UModal>
  </main>
</template>
