<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { fetchCatalog, type Catalog } from '../api/catalogs'

// A single catalog (UC-06, SCR-020). The entity table of the catalog follows with US-049.
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()

const catalog = ref<Catalog>()
const state = ref<'loading' | 'loaded' | 'not-found' | 'failed'>('loading')

async function load() {
  state.value = 'loading'
  try {
    catalog.value = await fetchCatalog(props.id, props.fetchFn)
    state.value = catalog.value ? 'loaded' : 'not-found'
  } catch {
    state.value = 'failed'
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<template>
  <main class="mx-auto flex max-w-5xl flex-col gap-6 p-8">
    <UButton to="/catalogs" data-testid="back-to-catalogs" icon="i-lucide-arrow-left" color="neutral" variant="ghost" label="Catalogs" class="self-start" />

    <UAlert
      v-if="state === 'not-found'"
      data-testid="catalog-not-found"
      color="neutral"
      variant="subtle"
      title="Catalog not found"
      description="The catalog does not exist or is not visible to you."
    />
    <UAlert v-if="state === 'failed'" data-testid="catalog-failure" color="error" variant="subtle" description="The catalog could not be loaded." />

    <template v-if="state === 'loaded' && catalog">
      <header class="flex flex-col gap-2">
        <div class="flex items-center gap-3">
          <h1 data-testid="catalog-title" class="text-2xl font-semibold text-highlighted">{{ catalog.name }}</h1>
          <UBadge
            data-testid="catalog-scope"
            :color="catalog.scope === 'personal' ? 'neutral' : 'primary'"
            variant="subtle"
            :label="catalog.scope === 'personal' ? 'Personal' : 'Shared'"
          />
        </div>
        <p data-testid="catalog-type" class="text-muted">{{ catalog.primaryMetatypeName }}</p>
        <p v-if="catalog.description" class="text-default">{{ catalog.description }}</p>
      </header>

      <UEmpty
        data-testid="catalog-table-placeholder"
        icon="i-lucide-table-2"
        title="No entities shown yet"
        :description="`The ${catalog.primaryMetatypeName} entities of this catalog will be listed here.`"
      />
    </template>
  </main>
</template>
