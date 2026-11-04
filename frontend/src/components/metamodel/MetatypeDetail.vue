<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  deleteMapping,
  deleteMetatype,
  fetchMappingHistory,
  updateMetatypeReimportSetting,
  type MetatypeDetail,
  type PropertyDefinitionDetail,
  type PropertyMapping,
  type ReimportSetting,
} from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metatypeById, reloadMetamodel } from '../../metamodel/store'
import DeleteDialog from './DeleteDialog.vue'
import MappingDialog from './MappingDialog.vue'
import MetatypeDialog from './MetatypeDialog.vue'
import { rejectionMessage, scopeLabel } from './messages'

// Detail of a metatype in SCR-030: base type, own and inherited mappings with their dated entries,
// re-import setting (US-032, US-137, REQ-032 AC5, ADR-049; design D3 of
// scr-030-metamodel-configuration). Built-in and imported metatypes offer no changes of their own.
const props = defineProps<{ metatype: MetatypeDetail; canWrite: boolean; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ deleted: [] }>()
const { t } = useI18n()

const entries = ref<PropertyMapping[]>([])
const showHistory = ref(false)
const editing = ref(false)
const deleting = ref(false)
const mappingFor = ref<{ property?: PropertyDefinitionDetail }>()
const removing = ref<PropertyMapping>()
const notice = ref<string>()
const failure = ref<string>()
const reimportSetting = ref<ReimportSetting>(props.metatype.reimportPolicy ?? 'inherit')

const own = computed(() => props.metatype.scope === 'organization')
const base = computed(() => metatypeById(props.metatype.baseMetatypeId ?? undefined))
const shown = computed(() => entries.value.filter((entry) => showHistory.value || entry.state !== 'past'))
const hasHistory = computed(() => entries.value.some((entry) => entry.state === 'past'))
const reimportItems = computed(() =>
  (['inherit', 'keep', 'remove'] as const).map((value) => ({ label: t(`metamodel.reimport.${value}`), value })),
)

async function loadEntries() {
  try {
    entries.value = await fetchMappingHistory(props.metatype.id, props.fetchFn)
  } catch {
    failure.value = t('metamodel.loadFailed')
  }
}

watch(
  () => [props.metatype.id, props.metatype.version],
  () => {
    reimportSetting.value = props.metatype.reimportPolicy ?? 'inherit'
    void loadEntries()
  },
  { immediate: true },
)

async function changed() {
  await reloadMetamodel(props.fetchFn)
}

async function metatypeSaved() {
  editing.value = false
  await changed()
}

async function mappingSaved(marked: number) {
  mappingFor.value = undefined
  notice.value = marked > 0 ? t('metamodel.mapping.marked', marked) : t('metamodel.mapping.saved')
  await changed()
}

async function changeReimportSetting(setting: ReimportSetting) {
  failure.value = undefined
  notice.value = undefined
  const outcome = await updateMetatypeReimportSetting(props.metatype.id, setting, props.metatype.version, props.fetchFn)
  if (!outcome.ok) {
    failure.value =
      outcome.rejection.problemType === 'stale-version' ? t('metamodel.collision.reloaded') : rejectionMessage(outcome.rejection)
  }
  await changed()
}

function propertyName(entry: PropertyMapping): string {
  return label(labelKeys.property(entry.propertyDefinition.id), entry.propertyDefinition.name)
}

function stateColor(state: PropertyMapping['state']): 'success' | 'info' | 'neutral' {
  return state === 'active' ? 'success' : state === 'future' ? 'info' : 'neutral'
}
</script>

<template>
  <section class="flex flex-col gap-6" data-testid="metatype-detail">
    <header class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex flex-col gap-1">
        <h2 class="text-xl font-semibold text-highlighted" data-testid="metatype-title">
          {{ label(labelKeys.metatype(metatype.id), metatype.name) }}
          <span v-if="metatype.abbreviation" class="text-muted">[{{ metatype.abbreviation }}]</span>
        </h2>
        <p
          v-if="label(labelKeys.metatype(metatype.id), metatype.name) !== metatype.name"
          class="text-sm text-muted"
          data-testid="metatype-corporate-name"
        >
          {{ t('metamodel.metatype.corporateName', { name: metatype.name }) }}
        </p>
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <UBadge
            data-testid="metatype-scope"
            :color="own ? 'primary' : 'neutral'"
            variant="subtle"
            :label="scopeLabel(metatype.scope, metatype.sourcePackage)"
          />
          <span v-if="base" data-testid="metatype-base-type">
            {{ t('metamodel.metatype.inheritsFrom') }}
            <RouterLink :to="`/metamodel/metatypes/${base.id}`" class="text-primary underline">
              {{ label(labelKeys.metatype(base.id), base.name) }}
            </RouterLink>
          </span>
        </div>
      </div>
      <div v-if="canWrite && own" class="flex gap-2">
        <UButton data-testid="metatype-edit" icon="i-lucide-pencil" color="neutral" variant="outline" :label="t('metamodel.edit')" @click="editing = true" />
        <UButton data-testid="metatype-delete" icon="i-lucide-trash-2" color="error" variant="outline" :label="t('metamodel.delete')" @click="deleting = true" />
      </div>
    </header>

    <UAlert v-if="!own" data-testid="metatype-read-only" color="neutral" variant="subtle" icon="i-lucide-lock" :description="t('metamodel.metatype.readOnly')" />
    <UAlert v-if="notice" data-testid="metatype-notice" color="success" variant="subtle" :description="notice" />
    <UAlert v-if="failure" data-testid="metatype-detail-failure" color="error" variant="subtle" :description="failure" />

    <section class="flex flex-col gap-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h3 class="font-semibold text-highlighted">{{ t('metamodel.metatype.properties', { count: metatype.propertyMappings?.length ?? 0 }) }}</h3>
        <div class="flex items-center gap-3">
          <USwitch v-if="hasHistory" v-model="showHistory" data-testid="mapping-history" :label="t('metamodel.mapping.showHistory')" />
          <UButton v-if="canWrite" data-testid="mapping-new" icon="i-lucide-plus" size="sm" :label="t('metamodel.mapping.new')" @click="mappingFor = {}" />
        </div>
      </div>
      <div class="overflow-x-auto rounded-lg border border-default">
        <table class="w-full text-left text-sm" data-testid="mapping-table">
          <thead class="bg-elevated text-toned">
            <tr>
              <th class="px-3 py-2 font-medium">{{ t('metamodel.mapping.property') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('metamodel.property.dataType') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('metamodel.mapping.necessity') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('metamodel.mapping.validFrom') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('metamodel.mapping.origin') }}</th>
              <th v-if="canWrite" class="px-3 py-2"><span class="sr-only">{{ t('metamodel.actions') }}</span></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-default">
            <tr v-for="entry in shown" :key="entry.id" data-testid="mapping-row" :class="entry.state === 'past' ? 'text-muted' : ''">
              <td class="px-3 py-2 font-medium">{{ propertyName(entry) }}</td>
              <td class="px-3 py-2">{{ t(`metamodel.dataType.${entry.propertyDefinition.dataType}`) }}</td>
              <td class="px-3 py-2">{{ t(`metamodel.necessity.${entry.necessity}`) }}</td>
              <td class="px-3 py-2">
                <span class="flex flex-wrap items-center gap-2">
                  {{ entry.validFrom }}
                  <UBadge data-testid="mapping-state" size="sm" variant="subtle" :color="stateColor(entry.state)" :label="t(`metamodel.state.${entry.state}`)" />
                </span>
              </td>
              <td class="px-3 py-2" data-testid="mapping-origin">
                <span v-if="entry.inheritedFrom">{{ t('metamodel.mapping.inheritedFrom', { name: label(labelKeys.metatype(entry.inheritedFrom.id), entry.inheritedFrom.name) }) }}</span>
                <span v-else>{{ scopeLabel(entry.scope) }}</span>
              </td>
              <td v-if="canWrite" class="px-3 py-2 text-right whitespace-nowrap">
                <template v-if="!entry.inheritedFrom">
                  <UButton
                    v-if="entry.state !== 'past'"
                    data-testid="mapping-add-entry"
                    size="xs"
                    color="neutral"
                    variant="ghost"
                    icon="i-lucide-calendar-plus"
                    :aria-label="t('metamodel.mapping.addEntry')"
                    :title="t('metamodel.mapping.addEntry')"
                    @click="mappingFor = { property: entry.propertyDefinition }"
                  />
                  <UButton
                    v-if="entry.scope === 'organization'"
                    data-testid="mapping-remove"
                    size="xs"
                    color="error"
                    variant="ghost"
                    icon="i-lucide-trash-2"
                    :aria-label="t('metamodel.mapping.remove')"
                    :title="t('metamodel.mapping.remove')"
                    @click="removing = entry"
                  />
                </template>
              </td>
            </tr>
            <tr v-if="shown.length === 0">
              <td :colspan="canWrite ? 6 : 5" class="px-3 py-4 text-center text-muted" data-testid="mapping-empty">{{ t('metamodel.mapping.none') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="flex flex-col gap-2">
      <h3 class="font-semibold text-highlighted">{{ t('metamodel.reimport.title') }}</h3>
      <p class="text-sm text-muted">{{ t('metamodel.reimport.metatypeHint') }}</p>
      <div class="flex flex-wrap items-center gap-3">
        <USelect
          v-if="canWrite"
          v-model="reimportSetting"
          data-testid="metatype-reimport"
          :aria-label="t('metamodel.reimport.title')"
          :items="reimportItems"
          class="w-56"
          @update:model-value="(value: unknown) => changeReimportSetting(value as ReimportSetting)"
        />
        <span v-else>{{ t(`metamodel.reimport.${metatype.reimportPolicy ?? 'inherit'}`) }}</span>
        <span data-testid="metatype-reimport-effective" class="text-sm">
          {{ t('metamodel.reimport.applies', { setting: t(`metamodel.reimport.${metatype.effectiveReimportPolicy ?? 'keep'}`) }) }}
        </span>
      </div>
    </section>

    <UModal v-model:open="editing" :title="t('metamodel.metatype.edit')">
      <template #body>
        <MetatypeDialog :metatype="metatype" :fetch-fn="fetchFn" @saved="metatypeSaved" @cancel="editing = false" />
      </template>
    </UModal>
    <UModal :open="mappingFor !== undefined" :title="mappingFor?.property ? t('metamodel.mapping.addEntry') : t('metamodel.mapping.new')" @update:open="(open: boolean) => !open && (mappingFor = undefined)">
      <template #body>
        <MappingDialog
          v-if="mappingFor"
          :metatype="metatype"
          :property="mappingFor.property"
          :fetch-fn="fetchFn"
          @saved="mappingSaved"
          @cancel="mappingFor = undefined"
        />
      </template>
    </UModal>
    <DeleteDialog
      v-model:open="deleting"
      :title="t('metamodel.metatype.delete')"
      :description="t('metamodel.metatype.deleteHint', { name: metatype.name })"
      :version="metatype.version"
      :run="(version) => deleteMetatype(metatype.id, version, fetchFn)"
      @stale="changed"
      @done="emit('deleted')"
    >
      <template #current>{{ t('metamodel.collision.metatypeChanged', { name: metatype.name }) }}</template>
    </DeleteDialog>
    <DeleteDialog
      :open="removing !== undefined"
      :title="t('metamodel.mapping.remove')"
      :description="removing ? t('metamodel.mapping.removeHint', { property: propertyName(removing), date: removing.validFrom }) : ''"
      :warning="t('metamodel.mapping.removeWarning')"
      :version="metatype.version"
      :run="(version) => deleteMapping(metatype.id, removing!.id, version, fetchFn)"
      @update:open="(open: boolean) => !open && (removing = undefined)"
      @stale="changed"
      @done="changed"
    >
      <template #current>{{ t('metamodel.collision.metatypeChanged', { name: metatype.name }) }}</template>
    </DeleteDialog>
  </section>
</template>
