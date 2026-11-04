<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import CategoriesSection from '../components/metamodel/CategoriesSection.vue'
import MetatypesSection from '../components/metamodel/MetatypesSection.vue'
import PropertiesSection from '../components/metamodel/PropertiesSection.vue'
import SettingsSection from '../components/metamodel/SettingsSection.vue'
import TranslationsSection from '../components/metamodel/TranslationsSection.vue'
import { loadMetamodel, metamodel } from '../metamodel/store'
import { refreshSession, session } from '../session/store'

// Metamodel configuration SCR-030 (UC-04, US-032, REQ-032; design D3 of
// scr-030-metamodel-configuration): readable with entity:read, changeable with metamodel:write.
const props = defineProps<{ fetchFn?: typeof fetch; section?: string; id?: string }>()
const router = useRouter()
const { t } = useI18n()

const SECTIONS = ['metatypes', 'properties', 'categories', 'translations', 'settings'] as const
type Section = (typeof SECTIONS)[number]

const permissions = computed(() => new Set(session.me?.permissions.map((entry) => entry.permission) ?? []))
const canWrite = computed(() => permissions.value.has('metamodel:write'))
const canRead = computed(() => canWrite.value || permissions.value.has('entity:read'))
const active = computed<Section>(() =>
  (SECTIONS as readonly string[]).includes(props.section ?? '') ? (props.section as Section) : 'metatypes',
)
const tabs = computed(() =>
  SECTIONS.filter((section) => section !== 'settings' || canWrite.value).map((section) => ({
    label: t(`metamodel.section.${section}`),
    value: section,
  })),
)

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (canRead.value) {
    await loadMetamodel(props.fetchFn)
  }
})

async function select(section: string | number) {
  await router.push(`/metamodel/${section}`)
}
</script>

<template>
  <main class="mx-auto flex max-w-6xl flex-col gap-6 p-4 sm:p-8">
    <div class="flex flex-col gap-1">
      <h1 class="text-2xl font-semibold text-highlighted">{{ t('metamodel.title') }}</h1>
      <p class="text-sm text-muted">{{ canWrite ? t('metamodel.hintWrite') : t('metamodel.hintRead') }}</p>
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
      data-testid="no-metamodel-access"
      color="neutral"
      variant="subtle"
      :description="t('metamodel.noAccess')"
    />
    <UAlert v-if="metamodel.failed" data-testid="metamodel-failure" color="error" variant="subtle" :description="t('metamodel.loadFailed')" />

    <template v-if="canRead && metamodel.loaded && !metamodel.failed">
      <UTabs
        data-testid="metamodel-sections"
        :items="tabs"
        :model-value="active"
        :content="false"
        variant="link"
        class="w-full overflow-x-auto"
        @update:model-value="select"
      />
      <MetatypesSection v-if="active === 'metatypes'" :selected-id="id" :can-write="canWrite" :fetch-fn="fetchFn" />
      <PropertiesSection v-else-if="active === 'properties'" :can-write="canWrite" :fetch-fn="fetchFn" />
      <CategoriesSection v-else-if="active === 'categories'" :can-write="canWrite" :fetch-fn="fetchFn" />
      <TranslationsSection v-else-if="active === 'translations'" :can-write="canWrite" :fetch-fn="fetchFn" />
      <SettingsSection v-else-if="active === 'settings' && canWrite" :can-write="canWrite" :fetch-fn="fetchFn" />
    </template>
  </main>
</template>
