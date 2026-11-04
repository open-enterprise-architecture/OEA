<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchMetatypes, fetchStarterPackages, importStarterPackage, type StarterPackage } from '../api/metamodel'
import { fetchMe } from '../api/session'

// Starter configuration of a new instance (ADR-005, US-033): offered to persons with
// metamodel:write while the instance has no metatype. No screen story yet (change 6).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const packages = ref<StarterPackage[]>([])
const visible = ref(false)
const importing = ref<string>()
const imported = ref<{ name: string; created: number }>()
const failure = ref<string>()

onMounted(async () => {
  try {
    const me = await fetchMe(props.fetchFn)
    if (!me?.permissions.some((entry) => entry.permission === 'metamodel:write')) {
      return
    }
    if ((await fetchMetatypes(props.fetchFn)).length > 0) {
      return
    }
    packages.value = await fetchStarterPackages(props.fetchFn)
    visible.value = packages.value.length > 0
  } catch {
    visible.value = false
  }
})

async function importPackage(starter: StarterPackage) {
  failure.value = undefined
  importing.value = starter.packageId
  try {
    const outcome = await importStarterPackage(starter.packageId, props.fetchFn)
    if (outcome.ok) {
      imported.value = { name: starter.name, created: outcome.result.created }
    } else if (outcome.result?.conflictList?.length) {
      failure.value = t('starter.conflicts', { elements: outcome.result.conflictList.map((c) => c.element).join(', ') })
    } else {
      failure.value = t('starter.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    importing.value = undefined
  }
}
</script>

<template>
  <section v-if="visible" data-testid="starter-configuration" class="flex flex-col gap-4 rounded-lg border border-default p-6">
    <h2 class="text-xl font-semibold text-highlighted">{{ t('starter.title') }}</h2>

    <UAlert
      v-if="imported"
      data-testid="starter-imported"
      color="success"
      variant="subtle"
      :title="t('starter.imported', { name: imported.name })"
      :description="t('starter.available', { count: imported.created })"
    />

    <template v-else>
      <p class="text-muted">{{ t('starter.intro') }}</p>
      <UAlert v-if="failure" data-testid="starter-failure" color="error" variant="subtle" :description="failure" />
      <article
        v-for="starter in packages"
        :key="starter.packageId"
        :data-testid="`starter-${starter.packageId}`"
        class="flex flex-col gap-2"
      >
        <h3 class="font-semibold">{{ starter.name }} <span class="text-muted">{{ starter.version }}</span></h3>
        <p class="text-sm text-muted">{{ starter.description }}</p>
        <p class="text-sm">{{ starter.metatypeNames.join(', ') }}</p>
        <UButton
          :data-testid="`import-${starter.packageId}`"
          :loading="importing === starter.packageId"
          :disabled="starter.imported"
          :label="starter.imported ? t('starter.importedButton') : t('starter.import')"
          class="self-start"
          @click="importPackage(starter)"
        />
      </article>
    </template>
  </section>
</template>
