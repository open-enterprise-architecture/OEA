<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { fetchSolution, type Solution } from '../api/solutions'
import SolutionBaseline from '../components/solutions/SolutionBaseline.vue'
import { currentLanguage } from '../i18n'
import { refreshSession, session } from '../session/store'

// SCR-061 "Solution" (UC-05, US-038, US-039): name, status, owner, creation time and the
// architecture vision of one solution (design D4 of us-038-create-solution), and its baseline
// (design D5 of us-039-solution-baseline). The deltas follow in S11.
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const solution = ref<Solution>()
const loaded = ref(false)
const notFound = ref(false)
const failure = ref<string>()

const canRead = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:read'))

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  try {
    solution.value = await fetchSolution(props.id, props.fetchFn)
  } catch (error) {
    const rejection = error instanceof RequestRejected ? error.rejection : undefined
    if (rejection?.status === 404) {
      notFound.value = true
    } else {
      failure.value = rateLimitMessage(rejection) ?? t('solutions.loadOneFailed')
    }
  } finally {
    loaded.value = true
  }
})

const createdAt = computed(() =>
  solution.value ? new Date(solution.value.createdAt).toLocaleString(currentLanguage(), { dateStyle: 'medium', timeStyle: 'short' }) : '',
)
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <RouterLink to="/solutions" data-testid="solution-back" class="text-sm text-muted hover:text-default">
      ← {{ t('solutions.title') }}
    </RouterLink>

    <UAlert
      v-if="session.me && !canRead"
      data-testid="solutions-no-access"
      color="neutral"
      variant="subtle"
      :description="t('solutions.noAccess')"
    />
    <UAlert v-if="failure" data-testid="solution-failure" color="error" variant="subtle" :description="failure" />
    <UEmpty
      v-if="notFound"
      data-testid="solution-not-found"
      icon="i-lucide-search-x"
      :title="t('solutions.notFound')"
      :description="t('solutions.notFoundHint')"
    />

    <template v-if="solution">
      <div class="flex flex-wrap items-center gap-3">
        <h1 data-testid="solution-title" class="text-2xl font-semibold text-highlighted">{{ solution.name }}</h1>
        <UBadge
          data-testid="solution-status"
          :color="solution.status === 'draft' ? 'neutral' : 'primary'"
          variant="subtle"
          :label="t(`solutions.status.${solution.status}`)"
        />
      </div>
      <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
        <dt class="text-muted">{{ t('solutions.owner') }}</dt>
        <dd data-testid="solution-owner">{{ solution.ownerName }}</dd>
        <dt class="text-muted">{{ t('solutions.createdAt') }}</dt>
        <dd data-testid="solution-created">{{ createdAt }}</dd>
        <dt class="text-muted">{{ t('solutions.mode') }}</dt>
        <dd>{{ t('solutions.projectMode') }}</dd>
      </dl>
      <section class="flex flex-col gap-2">
        <h2 class="text-lg font-semibold text-highlighted">{{ t('solutions.vision') }}</h2>
        <p v-if="solution.description" data-testid="solution-vision" class="whitespace-pre-line">{{ solution.description }}</p>
        <p v-else data-testid="solution-no-vision" class="text-muted">{{ t('solutions.noVision') }}</p>
      </section>
      <SolutionBaseline :solution-id="solution.id" :fetch-fn="props.fetchFn" />
    </template>
  </main>
</template>
