<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { fetchDashboards, type DashboardConfig, type DashboardSummary } from '../api/dashboards'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import CreateDashboardForm from '../components/dashboard/CreateDashboardForm.vue'
import { refreshSession, session } from '../session/store'

// Dashboard overview (UC-07, US-055, US-062, SCR-056): the shared dashboards and the own personal
// ones; administrators also see the personal dashboards of others, marked by their owner.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

const dashboards = ref<DashboardSummary[]>([])
const loaded = ref(false)
const failure = ref<string>()
const creating = ref(false)

const permissions = computed(() => new Set(session.me?.permissions.map((entry) => entry.permission) ?? []))
const canRead = computed(() => permissions.value.has('entity:read'))
const canManage = computed(() => permissions.value.has('dashboard:manage'))

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  try {
    dashboards.value = await fetchDashboards(props.fetchFn)
  } catch (error) {
    failure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('dashboards.loadFailed')
  } finally {
    loaded.value = true
  }
})

async function onCreated(dashboard: DashboardConfig) {
  creating.value = false
  await router.push(`/dashboards/${dashboard.id}`)
}
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <div class="flex items-center justify-between gap-4">
      <h1 class="text-2xl font-semibold text-highlighted">{{ t('dashboards.title') }}</h1>
      <UButton
        v-if="canRead"
        data-testid="new-dashboard"
        icon="i-lucide-plus"
        :label="t('dashboards.new')"
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
      data-testid="no-dashboard-access"
      color="neutral"
      variant="subtle"
      :description="t('dashboards.noAccess')"
    />
    <UAlert v-if="failure" data-testid="dashboards-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="canRead && loaded && !failure">
      <ul v-if="dashboards.length > 0" data-testid="dashboard-list" class="divide-y divide-default rounded-lg border border-default">
        <li v-for="dashboard in dashboards" :key="dashboard.id">
          <RouterLink
            :to="`/dashboards/${dashboard.id}`"
            data-testid="dashboard-item"
            class="flex items-center justify-between gap-4 px-4 py-3 hover:bg-elevated"
          >
            <span class="flex flex-col">
              <span class="font-medium text-highlighted">{{ dashboard.name }}</span>
              <span class="text-sm text-muted">
                {{ t('dashboards.owner', { name: dashboard.owner.displayName }) }} ·
                <span data-testid="dashboard-widget-count">{{ t('dashboards.widgetCount', dashboard.widgetCount) }}</span>
              </span>
              <span v-if="dashboard.description" class="text-sm text-muted">{{ dashboard.description }}</span>
            </span>
            <span class="flex items-center gap-2">
              <UBadge
                :color="dashboard.scope === 'personal' ? 'neutral' : 'primary'"
                variant="subtle"
                :label="dashboard.scope === 'personal' ? t('dashboards.personal') : t('dashboards.shared')"
              />
            </span>
          </RouterLink>
        </li>
      </ul>
      <UEmpty
        v-else
        data-testid="dashboards-empty"
        icon="i-lucide-layout-dashboard"
        :title="t('dashboards.empty')"
        :description="t('dashboards.emptyHint')"
      />
    </template>

    <UModal v-model:open="creating" :title="t('dashboards.new')" :description="t('dashboards.newHint')">
      <template #body>
        <CreateDashboardForm :fetch-fn="props.fetchFn" :can-manage="canManage" @created="onCreated" @cancel="creating = false" />
      </template>
    </UModal>
  </main>
</template>
