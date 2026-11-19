<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DashboardConfig, DashboardData } from '../api/dashboards'
import { fetchPortalDashboard, fetchPortalDashboardData, fetchPortalWidgetData } from '../api/portal'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import ChartWidget from '../components/dashboard/ChartWidget.vue'
import DashboardGrid from '../components/dashboard/DashboardGrid.vue'
import KpiWidget from '../components/dashboard/KpiWidget.vue'
import TableWidget from '../components/dashboard/TableWidget.vue'
import TextWidget from '../components/dashboard/TextWidget.vue'
import { refreshSession, session } from '../session/store'

// A dashboard read-only in the portal (SCR-053, US-061, US-060 AC5; design D4 of
// us-061-portal-dashboards): the widgets in their saved grid positions, without edit mode, drag and
// drop or resizing. The configuration and the data are read in parallel; "Refresh" reads the data
// again, there is no polling.
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const { t, locale } = useI18n()

const config = ref<DashboardConfig>()
const data = ref<DashboardData>()
const state = ref<'loading' | 'loaded' | 'not-found' | 'failed'>('loading')
const failure = ref<string>()
const refreshing = ref(false)
const refreshFailure = ref<string>()

const canRead = computed(() => session.me?.permissions.some((entry) => entry.permission === 'portal:read') ?? false)
const dataOf = computed(() => new Map((data.value?.widgets ?? []).map((widget) => [widget.widgetId, widget])))
const generatedAt = computed(() =>
  data.value ? new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(data.value.generatedAt)) : undefined,
)

function message(error: unknown, fallback: string): string {
  return rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? fallback
}

async function load() {
  state.value = 'loading'
  failure.value = undefined
  refreshFailure.value = undefined
  try {
    const [loadedConfig, loadedData] = await Promise.all([
      fetchPortalDashboard(props.id, props.fetchFn),
      fetchPortalDashboardData(props.id, props.fetchFn).catch((error: unknown) => {
        // an unknown dashboard answers 404 on both reads; the configuration decides
        if (error instanceof RequestRejected && error.rejection.status === 404) {
          return undefined
        }
        throw error
      }),
    ])
    config.value = loadedConfig
    data.value = loadedData
    state.value = loadedConfig ? 'loaded' : 'not-found'
  } catch (error) {
    failure.value = message(error, t('portal.dashboardLoadFailed'))
    state.value = 'failed'
  }
}

async function refresh() {
  refreshing.value = true
  refreshFailure.value = undefined
  try {
    data.value = await fetchPortalDashboardData(props.id, props.fetchFn)
  } catch (error) {
    refreshFailure.value = message(error, t('portal.refreshFailed'))
  } finally {
    refreshing.value = false
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    state.value = 'loaded'
    return
  }
  await load()
})
watch(
  () => props.id,
  () => {
    if (canRead.value) {
      void load()
    }
  },
)
</script>

<template>
  <main class="mx-auto flex max-w-7xl flex-col gap-4 p-8">
    <RouterLink to="/portal" data-testid="back-to-portal" class="inline-flex items-center gap-1 text-sm text-muted hover:text-default">
      <UIcon name="i-lucide-arrow-left" class="size-4" aria-hidden="true" />
      {{ t('portal.back') }}
    </RouterLink>

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
      data-testid="no-portal-access"
      color="neutral"
      variant="subtle"
      :description="t('portal.noAccess')"
    />
    <UEmpty
      v-else-if="state === 'not-found'"
      data-testid="portal-dashboard-not-found"
      icon="i-lucide-search-x"
      :title="t('portal.dashboardNotFound')"
      :description="t('portal.dashboardNotFoundHint')"
      :actions="[{ label: t('portal.back'), to: '/portal' }]"
    />
    <UAlert v-else-if="state === 'failed'" data-testid="portal-dashboard-failure" color="error" variant="subtle" :description="failure" />

    <template v-else-if="config && state === 'loaded'">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div class="flex flex-col gap-1">
          <div class="flex items-center gap-3">
            <h1 data-testid="portal-dashboard-title" class="text-2xl font-semibold text-highlighted">{{ config.name }}</h1>
            <UBadge
              v-if="config.scope === 'personal'"
              data-testid="portal-dashboard-personal"
              color="neutral"
              variant="subtle"
              :label="t('dashboard.personal')"
            />
          </div>
          <p v-if="config.description" class="text-sm text-muted">{{ config.description }}</p>
          <p v-if="generatedAt" data-testid="portal-dashboard-generated" class="text-sm text-muted">{{ t('portal.dataAsOf', { time: generatedAt }) }}</p>
        </div>
        <UButton
          data-testid="portal-dashboard-refresh"
          color="neutral"
          variant="outline"
          icon="i-lucide-refresh-cw"
          :label="t('portal.refresh')"
          :loading="refreshing"
          @click="refresh"
        />
      </div>
      <UAlert v-if="refreshFailure" data-testid="portal-dashboard-refresh-failure" color="error" variant="subtle" :description="refreshFailure" />

      <UEmpty v-if="config.widgets.length === 0" data-testid="dashboard-empty" icon="i-lucide-layout-dashboard" :title="t('dashboard.empty')" />
      <DashboardGrid v-else :widgets="config.widgets" :editing="false">
        <template #widget="{ widget }">
          <TextWidget v-if="widget.type === 'text'" :content="widget.config.content ?? ''" />
          <KpiWidget v-else-if="widget.type === 'kpi'" :data="dataOf.get(widget.id)" :config="widget.config" />
          <ChartWidget v-else-if="widget.type === 'chart'" :data="dataOf.get(widget.id)" :config="widget.config" :title="widget.title" />
          <TableWidget
            v-else-if="widget.type === 'table'"
            :data="dataOf.get(widget.id)"
            :load-page="(page) => fetchPortalWidgetData(config!.id, widget.id, page, props.fetchFn)"
          />
        </template>
      </DashboardGrid>
    </template>
  </main>
</template>
