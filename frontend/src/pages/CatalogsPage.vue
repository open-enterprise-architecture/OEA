<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchCatalogs, type Catalog, type CatalogSummary } from '../api/catalogs'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import CreateCatalogForm from '../components/CreateCatalogForm.vue'
import { refreshSession, session } from '../session/store'

// Catalog overview (UC-06, US-046, SCR-020): shared catalogs and the own personal ones.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()

const catalogs = ref<CatalogSummary[]>([])
const loaded = ref(false)
const failure = ref<string>()
const creating = ref(false)

const permissions = computed(() => new Set(session.me?.permissions.map((entry) => entry.permission) ?? []))
const canRead = computed(() => permissions.value.has('entity:read'))
const canManage = computed(() => permissions.value.has('catalog:manage'))

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  try {
    catalogs.value = await fetchCatalogs(props.fetchFn)
  } catch (error) {
    failure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? 'The catalogs could not be loaded.'
  } finally {
    loaded.value = true
  }
})

async function onCreated(catalog: Catalog) {
  creating.value = false
  await router.push(`/catalogs/${catalog.id}`)
}
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <div class="flex items-center justify-between gap-4">
      <h1 class="text-2xl font-semibold text-highlighted">Catalogs</h1>
      <UButton v-if="canRead" data-testid="new-catalog" icon="i-lucide-plus" label="New catalog" @click="creating = true" />
    </div>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      description="You are not logged in."
      :actions="[{ label: 'Log in', to: '/login' }]"
    />
    <UAlert
      v-else-if="session.me && !canRead"
      data-testid="no-catalog-access"
      color="neutral"
      variant="subtle"
      description="You have no access to catalogs. Ask an administrator for a role."
    />
    <UAlert v-if="failure" data-testid="catalogs-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="canRead && loaded && !failure">
      <ul v-if="catalogs.length > 0" data-testid="catalog-list" class="divide-y divide-default rounded-lg border border-default">
        <li v-for="catalog in catalogs" :key="catalog.id">
          <RouterLink
            :to="`/catalogs/${catalog.id}`"
            data-testid="catalog-item"
            class="flex items-center justify-between gap-4 px-4 py-3 hover:bg-elevated"
          >
            <span class="flex flex-col">
              <span class="font-medium text-highlighted">{{ catalog.name }}</span>
              <span class="text-sm text-muted">{{ catalog.primaryMetatypeName }}</span>
            </span>
            <UBadge
              :color="catalog.scope === 'personal' ? 'neutral' : 'primary'"
              variant="subtle"
              :label="catalog.scope === 'personal' ? 'Personal' : 'Shared'"
            />
          </RouterLink>
        </li>
      </ul>
      <UEmpty
        v-else
        data-testid="catalogs-empty"
        icon="i-lucide-table-2"
        title="No catalogs yet"
        description="A catalog is a reusable table view of the architecture repository."
      />
    </template>

    <UModal v-model:open="creating" title="New catalog" description="Create a table view of the repository.">
      <template #body>
        <CreateCatalogForm :fetch-fn="props.fetchFn" :can-manage="canManage" @created="onCreated" @cancel="creating = false" />
      </template>
    </UModal>
  </main>
</template>
