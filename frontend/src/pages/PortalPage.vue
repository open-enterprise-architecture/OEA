<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CatalogSummary } from '../api/catalogs'
import { fetchPortalCatalogs } from '../api/portal'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// The portal home page (SCR-050; design D7 of s9-portal-catalogs): the shared catalogs for every
// reader with portal:read. Personal catalogs never appear here.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const catalogs = ref<CatalogSummary[]>([])
const loaded = ref(false)
const failure = ref<string>()

const canRead = computed(() => session.me?.permissions.some((entry) => entry.permission === 'portal:read') ?? false)

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  try {
    catalogs.value = await fetchPortalCatalogs(props.fetchFn)
  } catch (error) {
    failure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('portal.loadFailed')
  } finally {
    loaded.value = true
  }
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
    <UAlert v-if="failure" data-testid="portal-failure" color="error" variant="subtle" :description="failure" />

    <section v-if="canRead && loaded && !failure" class="flex flex-col gap-3" aria-labelledby="portal-catalogs">
      <h2 id="portal-catalogs" class="text-lg font-semibold text-highlighted">{{ t('portal.catalogs') }}</h2>
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
    </section>
  </main>
</template>
