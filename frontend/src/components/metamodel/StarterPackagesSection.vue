<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchStarterPackages, importStarterPackage, type StarterPackage } from '../../api/metamodel'
import { reloadMetamodel } from '../../metamodel/store'

// The section "Starter packages" of SCR-030 (design D7 of us-074-bpmn-process-diagrams): the packages shipped
// with OEA with their summary and "Import" for those not yet imported, also in an instance that has entity
// types already (the BPMN package next to the TOGAF types), and "Update to <version>" for a package imported in
// another version (design D7 of us-083-bpmn-data-objects). It is shown to holders of metamodel:write; the
// lock mode does not block it, as for every package import. A 409 lists the conflicting names.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const packages = ref<StarterPackage[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const confirming = ref<StarterPackage>()
const confirmOpen = ref(false)
const importing = ref(false)
const result = ref<{ name: string; created: number }>()
const failure = ref<string>()

async function load() {
  try {
    packages.value = await fetchStarterPackages(props.fetchFn)
    loadFailed.value = false
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** An imported package whose last import has another version than the shipped one: the update is offered. */
function outdated(starter: StarterPackage): boolean {
  return starter.imported && !!starter.importedVersion && starter.importedVersion !== starter.version
}

function ask(starter: StarterPackage) {
  result.value = undefined
  failure.value = undefined
  confirming.value = starter
  confirmOpen.value = true
}

async function confirm() {
  const starter = confirming.value
  if (!starter) {
    return
  }
  importing.value = true
  failure.value = undefined
  try {
    const outcome = await importStarterPackage(starter.packageId, props.fetchFn)
    if (outcome.ok) {
      result.value = { name: starter.name, created: outcome.result.created }
      await reloadMetamodel(props.fetchFn)
      await load()
    } else if (outcome.conflicts?.length) {
      failure.value = t('metamodel.starter.conflicts', { elements: outcome.conflicts.map((conflict) => conflict.element).join(', ') })
    } else {
      failure.value = t('metamodel.starter.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    importing.value = false
    confirmOpen.value = false
    confirming.value = undefined
  }
}
</script>

<template>
  <section class="flex flex-col gap-4" data-testid="starter-packages-section">
    <div class="flex flex-col gap-1">
      <h2 class="text-lg font-semibold text-highlighted">{{ t('metamodel.starter.title') }}</h2>
      <p class="text-sm text-muted">{{ t('metamodel.starter.intro') }}</p>
    </div>

    <UAlert v-if="result" data-testid="starter-result" color="success" variant="subtle" role="status" :description="t('metamodel.starter.result', { name: result.name, created: result.created })" />
    <UAlert v-if="failure" data-testid="starter-failure" color="error" variant="subtle" role="alert" :description="failure" />
    <UAlert v-if="loadFailed" data-testid="starter-load-failed" color="error" variant="subtle" :description="t('metamodel.starter.loadFailed')" />
    <p v-else-if="!loading && packages.length === 0" class="text-sm text-muted" data-testid="starter-none">{{ t('metamodel.starter.none') }}</p>

    <article
      v-for="starter in packages"
      :key="starter.packageId"
      class="flex flex-col gap-2 rounded-lg border border-default p-4"
      :data-testid="`starter-package-${starter.packageId}`"
    >
      <div class="flex flex-wrap items-center gap-2">
        <h3 class="font-semibold">{{ starter.name }} <span class="font-normal text-muted">{{ starter.version }}</span></h3>
        <UBadge v-if="starter.imported" color="success" variant="subtle" :label="t('metamodel.starter.imported')" data-testid="starter-imported-badge" />
        <span v-if="starter.imported && starter.importedVersion" class="text-sm text-muted" data-testid="starter-imported-version">{{ t('metamodel.starter.importedVersion', { version: starter.importedVersion }) }}</span>
      </div>
      <p class="text-sm text-muted">{{ starter.description }}</p>
      <p class="text-sm" data-testid="starter-counts">{{ t('metamodel.starter.counts', { types: starter.metatypeCount, properties: starter.propertyDefinitionCount }) }}</p>
      <p class="text-sm text-muted">{{ starter.metatypeNames.join(', ') }}</p>
      <UButton
        v-if="!starter.imported"
        :data-testid="`starter-import-${starter.packageId}`"
        class="self-start"
        :label="t('metamodel.starter.import')"
        :aria-label="`${t('metamodel.starter.import')}: ${starter.name}`"
        @click="ask(starter)"
      />
      <UButton
        v-else-if="outdated(starter)"
        :data-testid="`starter-update-${starter.packageId}`"
        class="self-start"
        :label="t('metamodel.starter.update', { version: starter.version })"
        :aria-label="`${t('metamodel.starter.update', { version: starter.version })}: ${starter.name}`"
        @click="ask(starter)"
      />
    </article>

    <UModal v-model:open="confirmOpen" :title="confirming && outdated(confirming) ? t('metamodel.starter.updateTitle', { name: confirming.name, version: confirming.version }) : t('metamodel.starter.confirmTitle', { name: confirming?.name ?? '' })" :description="t('metamodel.starter.confirmText', { types: confirming?.metatypeCount ?? 0, properties: confirming?.propertyDefinitionCount ?? 0 })">
      <template #body>
        <div class="flex justify-end gap-2" data-testid="starter-confirm">
          <UButton color="neutral" variant="ghost" data-testid="starter-cancel" :label="t('common.cancel')" @click="confirmOpen = false" />
          <UButton data-testid="starter-confirm-import" :loading="importing" :label="confirming && outdated(confirming) ? t('metamodel.starter.update', { version: confirming.version }) : t('metamodel.starter.confirm')" @click="confirm" />
        </div>
      </template>
    </UModal>
  </section>
</template>
