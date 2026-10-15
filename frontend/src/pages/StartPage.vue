<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchHealth, type HealthStatus } from '../api/health'

const props = defineProps<{ fetchFn?: typeof fetch }>()
const health = ref<HealthStatus | 'CHECKING'>('CHECKING')

onMounted(async () => {
  health.value = await fetchHealth(props.fetchFn)
})
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-3xl flex-col justify-center gap-6 p-8">
    <h1 class="text-3xl font-semibold text-highlighted">OEA – Open Enterprise Architecture</h1>
    <p class="text-muted">Walking skeleton: the catalog browser follows with the next changes.</p>
    <div class="flex items-center gap-2">
      <span>Backend</span>
      <UBadge
        data-testid="health"
        :color="health === 'UP' ? 'success' : health === 'DOWN' ? 'error' : 'neutral'"
        variant="subtle"
        :icon="health === 'UP' ? 'i-lucide-circle-check' : 'i-lucide-circle-alert'"
        :label="health"
      />
    </div>
  </main>
</template>
