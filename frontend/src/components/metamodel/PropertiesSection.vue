<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { deletePropertyDefinition, type PropertyDefinitionDetail } from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel, reloadMetamodel } from '../../metamodel/store'
import DeleteDialog from './DeleteDialog.vue'
import PropertyDialog from './PropertyDialog.vue'
import { scopeLabel } from './messages'

// Property definitions of SCR-030 (US-032, REQ-032 inputs; design D3 of
// scr-030-metamodel-configuration). Built-in and imported definitions are read-only.
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const editing = ref<{ property?: PropertyDefinitionDetail }>()
const deleting = ref<PropertyDefinitionDetail>()

function constraints(property: PropertyDefinitionDetail): string {
  const parts: string[] = []
  if (property.maxLength) {
    parts.push(t('metamodel.property.maxLengthShort', { length: property.maxLength }))
  }
  if (property.minValue !== null && property.minValue !== undefined) {
    parts.push(`≥ ${property.minValue}`)
  }
  if (property.maxValue !== null && property.maxValue !== undefined) {
    parts.push(`≤ ${property.maxValue}`)
  }
  if (property.pattern) {
    parts.push(property.pattern)
  }
  return parts.join(' · ')
}

async function saved() {
  editing.value = undefined
  await reloadMetamodel(props.fetchFn)
}
</script>

<template>
  <section class="flex flex-col gap-3">
    <div class="flex items-center justify-between gap-2">
      <h2 class="font-semibold text-highlighted">{{ t('metamodel.property.list', { count: metamodel.properties.length }) }}</h2>
      <UButton v-if="canWrite" data-testid="property-new" size="sm" icon="i-lucide-plus" :label="t('metamodel.new')" @click="editing = {}" />
    </div>
    <div class="overflow-x-auto rounded-lg border border-default">
      <table class="w-full text-left text-sm" data-testid="property-table">
        <thead class="bg-elevated text-toned">
          <tr>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.metatype.name') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.property.dataType') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.property.category') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.property.details') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('metamodel.scope.title') }}</th>
            <th v-if="canWrite" class="px-3 py-2"><span class="sr-only">{{ t('metamodel.actions') }}</span></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-default">
          <tr v-for="property in metamodel.properties" :key="property.id" data-testid="property-row">
            <td class="px-3 py-2 font-medium">{{ label(labelKeys.property(property.id), property.name) }}</td>
            <td class="px-3 py-2">{{ t(`metamodel.dataType.${property.dataType}`) }}</td>
            <td class="px-3 py-2">{{ property.category?.name ?? '–' }}</td>
            <td class="px-3 py-2">
              <span v-if="property.dataType === 'enum'" class="flex flex-wrap gap-1" data-testid="property-row-values">
                <UBadge
                  v-for="value in property.enumValues ?? []"
                  :key="value.id"
                  size="sm"
                  color="neutral"
                  variant="outline"
                  :label="label(labelKeys.enumValue(value.id), value.value)"
                />
              </span>
              <span v-else class="text-muted">{{ constraints(property) }}</span>
            </td>
            <td class="px-3 py-2 text-muted">{{ scopeLabel(property.scope, property.sourcePackage) }}</td>
            <td v-if="canWrite" class="px-3 py-2 text-right whitespace-nowrap">
              <template v-if="property.scope === 'organization'">
                <UButton
                  data-testid="property-edit"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-pencil"
                  :aria-label="t('metamodel.edit')"
                  @click="editing = { property }"
                />
                <UButton
                  data-testid="property-delete"
                  size="xs"
                  color="error"
                  variant="ghost"
                  icon="i-lucide-trash-2"
                  :aria-label="t('metamodel.delete')"
                  @click="deleting = property"
                />
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <UModal
      :open="editing !== undefined"
      :title="editing?.property ? t('metamodel.property.edit') : t('metamodel.property.new')"
      @update:open="(open: boolean) => !open && (editing = undefined)"
    >
      <template #body>
        <PropertyDialog v-if="editing" :property="editing.property" :fetch-fn="fetchFn" @saved="saved" @cancel="editing = undefined" />
      </template>
    </UModal>
    <DeleteDialog
      :open="deleting !== undefined"
      :title="t('metamodel.property.delete')"
      :description="deleting ? t('metamodel.property.deleteHint', { name: deleting.name }) : ''"
      :version="deleting?.version"
      :run="(version) => deletePropertyDefinition(deleting!.id, version, fetchFn)"
      @update:open="(open: boolean) => !open && (deleting = undefined)"
      @stale="reloadMetamodel(fetchFn)"
      @done="reloadMetamodel(fetchFn)"
    >
      <template #current>{{ t('metamodel.collision.changed', { name: deleting?.name }) }}</template>
    </DeleteDialog>
  </section>
</template>
