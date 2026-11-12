<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { fetchSolutions, type Solution, type SolutionSummary } from '../api/solutions'
import SolutionCreateDialog from '../components/solutions/SolutionCreateDialog.vue'
import { currentLanguage } from '../i18n'
import { refreshSession, session } from '../session/store'

// SCR-060 "Solutions" (UC-05, US-038; design D4 of us-038-create-solution): every solution with
// status, owner and creation time; with solution:write a solution is created in a dialog, and
// its page opens.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

const solutions = ref<SolutionSummary[]>([])
const loaded = ref(false)
const failure = ref<string>()
const creating = ref(false)

const permissions = computed(() => new Set(session.me?.permissions.map((entry) => entry.permission) ?? []))
const canRead = computed(() => permissions.value.has('entity:read'))
const canWrite = computed(() => permissions.value.has('solution:write'))

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  try {
    solutions.value = await fetchSolutions(props.fetchFn)
  } catch (error) {
    failure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('solutions.loadFailed')
  } finally {
    loaded.value = true
  }
})

function created(at: string): string {
  return new Date(at).toLocaleDateString(currentLanguage(), { dateStyle: 'medium' })
}

async function onCreated(solution: Solution) {
  creating.value = false
  await router.push(`/solutions/${solution.id}`)
}
</script>

<template>
  <main class="mx-auto flex max-w-4xl flex-col gap-6 p-8">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex flex-col gap-1">
        <h1 class="text-2xl font-semibold text-highlighted">{{ t('solutions.title') }}</h1>
        <p class="text-sm text-muted">{{ t('solutions.intro') }}</p>
      </div>
      <UButton
        v-if="canWrite && loaded && !failure"
        data-testid="solution-new"
        icon="i-lucide-plus"
        :label="t('solutions.new')"
        @click="creating = true"
      />
    </div>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert
      v-else-if="session.me && !canRead"
      data-testid="solutions-no-access"
      color="neutral"
      variant="subtle"
      :description="t('solutions.noAccess')"
    />
    <UAlert v-if="failure" data-testid="solutions-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="canRead && loaded && !failure">
      <ul v-if="solutions.length > 0" data-testid="solution-list" class="divide-y divide-default rounded-lg border border-default">
        <li v-for="solution in solutions" :key="solution.id">
          <RouterLink
            :to="`/solutions/${solution.id}`"
            data-testid="solution-item"
            class="flex items-center justify-between gap-4 px-4 py-3 hover:bg-elevated"
          >
            <span class="flex min-w-0 flex-col">
              <span class="truncate font-medium text-highlighted">{{ solution.name }}</span>
              <span class="text-sm text-muted">{{
                t('solutions.ownedBy', { owner: solution.ownerName, date: created(solution.createdAt) })
              }}</span>
            </span>
            <UBadge
              data-testid="solution-status"
              :color="solution.status === 'draft' ? 'neutral' : 'primary'"
              variant="subtle"
              :label="t(`solutions.status.${solution.status}`)"
            />
          </RouterLink>
        </li>
      </ul>
      <UEmpty
        v-else
        data-testid="solutions-empty"
        icon="i-lucide-milestone"
        :title="t('solutions.empty')"
        :description="t('solutions.emptyHint')"
      />
    </template>

    <UModal v-model:open="creating" :title="t('solutions.new')" :description="t('solutions.newHint')">
      <template #body>
        <SolutionCreateDialog :fetch-fn="props.fetchFn" @created="onCreated" @cancel="creating = false" />
      </template>
    </UModal>
  </main>
</template>
