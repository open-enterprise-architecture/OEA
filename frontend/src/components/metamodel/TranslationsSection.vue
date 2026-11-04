<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchMetamodelLabels, updateMetamodelLabels, type MetamodelLabel } from '../../api/metamodel'
import { currentLanguage, LANGUAGES, type Language } from '../../i18n'
import { loadLabels } from '../../i18n/labels'
import { rejectionMessage } from './messages'

// Translated labels of the metamodel per language in SCR-030 (US-140, REQ-152; design D5 of
// scr-030-metamodel-configuration): only changed keys are saved, an empty text removes the
// organization translation; afterwards the portal reloads its labels.
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const locale = ref<Language>(currentLanguage() === 'en' ? 'de' : currentLanguage())
const filter = ref('')
const entries = ref<MetamodelLabel[]>([])
const texts = reactive<Record<string, string>>({})
const failure = ref<string>()
const notice = ref<string>()
const saving = ref(false)
const loading = ref(false)

const languageItems = computed(() => LANGUAGES.map((value) => ({ label: t(`languages.${value}`), value })))
const shown = computed(() => {
  const query = filter.value.trim().toLowerCase()
  return entries.value.filter(
    (entry) =>
      query === '' ||
      entry.name.toLowerCase().includes(query) ||
      (texts[entry.key] ?? '').toLowerCase().includes(query) ||
      (entry.packageText ?? '').toLowerCase().includes(query),
  )
})
const changed = computed(() =>
  entries.value.filter((entry) => (texts[entry.key] ?? '').trim() !== (entry.organizationText ?? '')),
)

async function load() {
  loading.value = true
  failure.value = undefined
  try {
    entries.value = await fetchMetamodelLabels(locale.value, props.fetchFn)
    for (const key of Object.keys(texts)) {
      delete texts[key]
    }
    for (const entry of entries.value) {
      texts[entry.key] = entry.organizationText ?? ''
    }
  } catch {
    failure.value = t('metamodel.loadFailed')
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(locale, () => {
  notice.value = undefined
  void load()
})

async function save() {
  failure.value = undefined
  notice.value = undefined
  const update: Record<string, string | null> = {}
  for (const entry of changed.value) {
    const text = (texts[entry.key] ?? '').trim()
    update[entry.key] = text === '' ? null : text
  }
  saving.value = true
  try {
    const outcome = await updateMetamodelLabels(locale.value, update, props.fetchFn)
    if (!outcome.ok) {
      failure.value = rejectionMessage(outcome.rejection)
      return
    }
    notice.value = t('metamodel.translation.saved', Object.keys(update).length)
    await load()
    if (locale.value === currentLanguage()) {
      await loadLabels(locale.value, props.fetchFn)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="flex flex-col gap-3">
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h2 class="font-semibold text-highlighted">{{ t('metamodel.translation.title') }}</h2>
        <p class="text-sm text-muted">{{ t('metamodel.translation.hint') }}</p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <USelect v-model="locale" data-testid="translation-language" :items="languageItems" class="w-40" :aria-label="t('metamodel.translation.language')" />
        <UInput
          v-model="filter"
          data-testid="translation-filter"
          icon="i-lucide-search"
          :placeholder="t('metamodel.search')"
          :aria-label="t('metamodel.search')"
          class="w-56"
        />
      </div>
    </div>
    <UAlert v-if="failure" data-testid="translation-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="translation-notice" color="success" variant="subtle" :description="notice" />
    <div class="overflow-x-auto rounded-lg border border-default">
      <table class="w-full text-left text-sm" data-testid="translation-table">
        <thead class="bg-elevated text-toned">
          <tr>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.translation.element') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.translation.corporateName') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.translation.text') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-default">
          <tr v-for="entry in shown" :key="entry.key" data-testid="translation-row">
            <td class="px-3 py-2 text-muted">{{ t(`metamodel.translation.type.${entry.elementType}`) }}</td>
            <td class="px-3 py-2 font-medium">{{ entry.name }}</td>
            <td class="px-3 py-2">
              <UInput
                v-if="canWrite"
                v-model="texts[entry.key]"
                :data-testid="`translation-input-${entry.name}`"
                :placeholder="entry.packageText ?? entry.name"
                :aria-label="t('metamodel.translation.textFor', { name: entry.name })"
                class="w-full"
              />
              <span v-else>{{ entry.organizationText ?? entry.packageText ?? '–' }}</span>
            </td>
          </tr>
          <tr v-if="!loading && shown.length === 0">
            <td colspan="3" class="px-3 py-4 text-center text-muted">{{ t('metamodel.noMatch') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="canWrite" class="flex items-center justify-end gap-3">
      <span class="text-sm text-muted">{{ t('metamodel.translation.changed', changed.length) }}</span>
      <UButton data-testid="translation-save" :disabled="changed.length === 0" :loading="saving" :label="t('metamodel.save')" @click="save" />
    </div>
  </section>
</template>
