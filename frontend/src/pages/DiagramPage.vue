<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { fetchDiagram, type Diagram } from '../api/diagrams'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import DiagramDelete from '../components/diagram/DiagramDelete.vue'
import DiagramForm from '../components/diagram/DiagramForm.vue'
import PlaceInNavigation from '../components/PlaceInNavigation.vue'
import { currentLanguage } from '../i18n'
import { refreshSession, session } from '../session/store'

// One diagram (UC-10, US-074; design D6 of us-074-diagrams): name, description and viewpoint. The
// canvas with the shapes follows with SCR-021 (change us-074-diagram-canvas).
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

const diagram = ref<Diagram>()
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
    diagram.value = await fetchDiagram(props.id, props.fetchFn)
    failure.value = undefined
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('diagrams.loadFailed')
  } finally {
    loaded.value = true
  }
}

onMounted(async () => {
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

async function removed() {
  await router.push('/diagrams')
}
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
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
      <UAlert data-testid="diagram-canvas-notice" color="neutral" variant="subtle" icon="i-lucide-info" :description="t('diagrams.canvasFollows')" />
    </template>

    <UModal v-model:open="renaming" :title="t('diagrams.renameTitle')">
      <template #body>
        <DiagramForm v-if="renaming && diagram" :diagram="diagram" :fetch-fn="fetchFn" @saved="renamed" @cancel="renaming = false" />
      </template>
    </UModal>
    <DiagramDelete v-model:open="deleting" :diagram="diagram" :fetch-fn="fetchFn" @done="removed" @stale="load" />
  </main>
</template>
