<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchPlateauGoLivePreview, goLivePlateau, type Plateau, type PlateauGoLivePreview } from '../../api/plateaus'
import { rateLimitMessage } from '../../api/problem'
import DeltaMergeDialog from './DeltaMergeDialog.vue'

// SCR-066 "Take plateau live" (US-086, US-087, US-088, US-157; design D7 of
// us-086-plateau-go-live): the summary of the transition, the solutions that go live with the
// plateau, the warnings that do not block it, and the confirmation by the exact name of the
// plateau. Field conflicts of a solution stop the go-live; "Resolve" opens SCR-063 for that
// solution, and the preview is read again afterwards.
const props = defineProps<{ plateau: Plateau; baseline: Plateau; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ done: []; cancel: [] }>()
const { t } = useI18n()

const preview = ref<PlateauGoLivePreview>()
const failure = ref<string>()
const name = ref('')
const busy = ref(false)
const merging = ref<string>()

const conflicted = computed(() => (preview.value?.solutions ?? []).filter((solution) => solution.conflicts.length > 0))
const warned = computed(
  () => (preview.value?.warnings.notApproved.length ?? 0) > 0 || (preview.value?.warnings.openIssues ?? 0) > 0,
)
const confirmed = computed(() => name.value === props.plateau.name)
const mergingConflicts = computed(() => conflicted.value.find((solution) => solution.id === merging.value)?.conflicts ?? [])

async function load() {
  const outcome = await fetchPlateauGoLivePreview(props.plateau.id, props.fetchFn)
  if (outcome.ok) {
    preview.value = outcome.value
    failure.value = undefined
  } else {
    failure.value = rateLimitMessage(outcome.rejection) ?? t('plateaus.goLive.loadFailed')
  }
}

onMounted(() => void load().catch(() => (failure.value = t('common.backendUnreachable'))))

async function confirm() {
  failure.value = undefined
  busy.value = true
  try {
    const outcome = await goLivePlateau(props.plateau.id, name.value, props.fetchFn)
    if (outcome.ok) {
      emit('done')
      return
    }
    const rejection = outcome.rejection
    if (rejection.problemType === 'merge-required') {
      // the conflicts are listed per solution in the preview
      await load()
      failure.value = t('plateaus.goLive.conflicts')
    } else if (rejection.problemType === 'entity-blocked') {
      failure.value = t('plateaus.goLive.blocked')
    } else if (rejection.problemType === 'plateau-not-next') {
      failure.value = t('plateaus.goLive.notNext')
    } else if (rejection.status === 403) {
      failure.value = t('plateaus.goLive.forbidden')
    } else {
      failure.value = rateLimitMessage(rejection) ?? t('plateaus.goLive.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    busy.value = false
  }
}

async function onMerged() {
  merging.value = undefined
  await load()
}
</script>

<template>
  <div class="flex flex-col gap-4" data-testid="plateau-go-live-dialog">
    <template v-if="merging">
      <DeltaMergeDialog :solution-id="merging" :conflicts="mergingConflicts" :fetch-fn="props.fetchFn" @resolved="onMerged" @cancel="merging = undefined" />
    </template>
    <template v-else>
      <p data-testid="plateau-go-live-summary" class="text-sm">
        {{ t('plateaus.goLive.summary', { plateau: props.plateau.name, baseline: props.baseline.name }) }}
      </p>
      <UAlert v-if="failure" data-testid="plateau-go-live-failure" color="error" variant="subtle" :description="failure" />
      <template v-if="preview">
        <p data-testid="plateau-go-live-counts" class="text-sm text-muted">
          {{
            t('plateaus.goLive.counts', {
              created: preview.counts.created,
              modified: preview.counts.modified,
              retired: preview.counts.retired,
            })
          }}
        </p>
        <section class="flex flex-col gap-1" data-testid="plateau-go-live-solutions">
          <h3 class="text-sm font-medium">{{ t('plateaus.goLive.solutions', { n: preview.solutions.length }, preview.solutions.length) }}</h3>
          <ul class="flex flex-col gap-1 text-sm">
            <li v-for="solution in preview.solutions" :key="solution.id" class="flex flex-wrap items-center gap-2" data-testid="plateau-go-live-solution">
              <span>{{ solution.name }}</span>
              <UBadge color="neutral" variant="subtle" size="sm" :label="t(`solutions.status.${solution.status}`)" />
              <template v-if="solution.conflicts.length > 0">
                <UBadge color="error" variant="subtle" size="sm" :label="t('plateaus.goLive.conflictCount', { n: solution.conflicts.length }, solution.conflicts.length)" />
                <UButton
                  data-testid="plateau-go-live-resolve"
                  size="xs"
                  color="neutral"
                  variant="outline"
                  :label="t('plateaus.goLive.resolve')"
                  @click="merging = solution.id"
                />
              </template>
            </li>
          </ul>
        </section>
        <UAlert
          v-if="warned"
          data-testid="plateau-go-live-warnings"
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          :title="t('plateaus.goLive.warnings')"
        >
          <template #description>
            <ul class="list-disc ps-4">
              <li v-if="preview.warnings.notApproved.length > 0">
                {{
                  t(
                    'plateaus.goLive.notApproved',
                    { n: preview.warnings.notApproved.length, names: preview.warnings.notApproved.join(', ') },
                    preview.warnings.notApproved.length,
                  )
                }}
              </li>
              <li v-if="preview.warnings.openIssues > 0">
                {{ t('plateaus.goLive.openIssues', { n: preview.warnings.openIssues }, preview.warnings.openIssues) }}
              </li>
            </ul>
          </template>
        </UAlert>
        <UAlert color="error" variant="outline" icon="i-lucide-octagon-alert" :description="t('plateaus.goLive.irreversible')" />
        <UFormField :label="t('plateaus.goLive.nameLabel', { name: props.plateau.name })">
          <UInput v-model="name" data-testid="plateau-go-live-name" class="w-full" autocomplete="off" />
        </UFormField>
      </template>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
        <UButton
          data-testid="plateau-go-live-confirm"
          color="error"
          icon="i-lucide-rocket"
          :disabled="!preview || !confirmed || conflicted.length > 0"
          :loading="busy"
          :label="t('plateaus.goLive.confirm')"
          @click="confirm"
        />
      </div>
    </template>
  </div>
</template>
