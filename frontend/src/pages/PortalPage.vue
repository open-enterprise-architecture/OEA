<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CatalogSummary } from '../api/catalogs'
import type { DashboardSummary } from '../api/dashboards'
import { fetchPortalCatalogs, fetchPortalDashboards } from '../api/portal'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// The portal home page (SCR-050; design D7 of s9-portal-catalogs): the shared catalogs for every
// reader with portal:read. Personal catalogs never appear here. The section "Dashboards" lists the
// shared dashboards and the reader's own personal ones (design D4 of us-061-portal-dashboards);
// both lists load independently, so a failure of one does not hide the other.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const catalogs = ref<CatalogSummary[]>([])
const loaded = ref(false)
const failure = ref<string>()
const dashboards = ref<DashboardSummary[]>([])
const dashboardsLoaded = ref(false)
const dashboardsFailure = ref<string>()

const canRead = computed(() => session.me?.permissions.some((entry) => entry.permission === 'portal:read') ?? false)

async function loadCatalogs() {
  try {
    catalogs.value = await fetchPortalCatalogs(props.fetchFn)
  } catch (error) {
    failure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('portal.loadFailed')
  } finally {
    loaded.value = true
  }
}

async function loadDashboards() {
  try {
    dashboards.value = await fetchPortalDashboards(props.fetchFn)
  } catch (error) {
    dashboardsFailure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('portal.dashboardsLoadFailed')
  } finally {
    dashboardsLoaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    dashboardsLoaded.value = true
    return
  }
  await Promise.all([loadCatalogs(), loadDashboards()])
})
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <div class="flex flex-col gap-1">
      <h1 class="text-2xl font-semibold text-highlighted">{{ t('portal.title') }}</h1>
      <p class="text-muted">{{ t('portal.intro') }}</p>
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
      data-testid="no-portal-access"
      color="neutral"
      variant="subtle"
      :description="t('portal.noAccess')"
    />
    <section v-if="canRead && loaded" class="flex flex-col gap-3" aria-labelledby="portal-catalogs">
      <h2 id="portal-catalogs" class="text-lg font-semibold text-highlighted">{{ t('portal.catalogs') }}</h2>
      <UAlert v-if="failure" data-testid="portal-failure" color="error" variant="subtle" :description="failure" />
      <template v-else>
        <ul v-if="catalogs.length > 0" data-testid="portal-catalog-list" class="divide-y divide-default rounded-lg border border-default">
          <li v-for="catalog in catalogs" :key="catalog.id">
            <RouterLink
              :to="`/portal/catalogs/${catalog.id}`"
              data-testid="portal-catalog-item"
              class="flex flex-col px-4 py-3 hover:bg-elevated"
            >
              <span class="font-medium text-highlighted">{{ catalog.name }}</span>
              <span class="text-sm text-muted">{{
                label(labelKeys.metatype(catalog.primaryMetatypeId), catalog.primaryMetatypeName)
              }}</span>
            </RouterLink>
          </li>
        </ul>
        <UEmpty
          v-else
          data-testid="portal-empty"
          icon="i-lucide-table-2"
          :title="t('portal.empty')"
          :description="t('portal.emptyHint')"
        />
      </template>
    </section>

    <section v-if="canRead && dashboardsLoaded" class="flex flex-col gap-3" aria-labelledby="portal-dashboards">
      <h2 id="portal-dashboards" class="text-lg font-semibold text-highlighted">{{ t('portal.dashboards') }}</h2>
      <UAlert v-if="dashboardsFailure" data-testid="portal-dashboards-failure" color="error" variant="subtle" :description="dashboardsFailure" />
      <template v-else>
        <ul v-if="dashboards.length > 0" data-testid="portal-dashboard-list" class="divide-y divide-default rounded-lg border border-default">
          <li v-for="dashboard in dashboards" :key="dashboard.id">
            <RouterLink
              :to="`/portal/dashboards/${dashboard.id}`"
              data-testid="portal-dashboard-item"
              class="flex items-center justify-between gap-4 px-4 py-3 hover:bg-elevated"
            >
              <span class="flex flex-col">
                <span class="font-medium text-highlighted">{{ dashboard.name }}</span>
                <span v-if="dashboard.description" class="text-sm text-muted">{{ dashboard.description }}</span>
                <span class="text-sm text-muted" data-testid="portal-dashboard-widget-count">{{ t('dashboards.widgetCount', dashboard.widgetCount) }}</span>
              </span>
              <UBadge
                v-if="dashboard.scope === 'personal'"
                data-testid="portal-dashboard-badge"
                color="neutral"
                variant="subtle"
                :label="t('dashboard.personal')"
              />
            </RouterLink>
          </li>
        </ul>
        <UEmpty
          v-else
          data-testid="portal-dashboards-empty"
          icon="i-lucide-layout-dashboard"
          :title="t('portal.dashboardsEmpty')"
          :description="t('portal.dashboardsEmptyHint')"
        />
      </template>
    </section>
  </main>
</template>
