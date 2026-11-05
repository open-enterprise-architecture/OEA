<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  SYSTEM_FIELDS,
  updateCatalogColumns,
  type Catalog,
  type CatalogColumnConfig,
  type CatalogSystemField,
} from '../api/catalogs'
import { fetchMetatype, type PropertyDefinitionDetail } from '../api/metamodel'
import { rateLimitMessage } from '../api/problem'
import { label, labelKeys } from '../i18n/labels'

// The dialog "Configure columns" of SCR-020 (US-047, REQ-044; design D6 of
// us-047-configure-columns): every attribute of the primary metatype with visibility, header and
// sortability; the order by drag and drop or the up/down buttons; at most one default sort.
const props = defineProps<{ catalog: Catalog; definitions: PropertyDefinitionDetail[]; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [catalog: Catalog]; cancel: [] }>()
const { t } = useI18n()

interface Row {
  key: string
  field?: CatalogSystemField
  propertyDefId?: string
  name: string
  visible: boolean
  sortable: boolean
  displayLabel: string
  available: boolean
}

const NONE = '-'
const rows = ref<Row[]>([])
const defaultSort = ref(NONE)
const loading = ref(true)
const loadFailed = ref(false)
const saving = ref(false)
const failure = ref<string>()
const dragged = ref<number>()

const noneVisible = computed(() => !rows.value.some((row) => row.visible))
const sortItems = computed(() => [
  { label: t('catalog.columns.noDefaultSort'), value: NONE },
  ...rows.value
    .filter((row) => row.visible && row.sortable && row.available)
    .flatMap((row) => [
      { label: t('catalog.columns.ascending', { name: shownName(row) }), value: `${row.key}:asc` },
      { label: t('catalog.columns.descending', { name: shownName(row) }), value: `${row.key}:desc` },
    ]),
])

function fieldRow(field: CatalogSystemField): Row {
  return { key: field, field, name: t(`catalog.columns.field.${field}`), visible: false, sortable: true, displayLabel: '', available: true }
}

function propertyRow(id: string, available: boolean): Row {
  const definition = props.definitions.find((candidate) => candidate.id === id)
  const name = definition ? label(labelKeys.property(id), definition.name) : id
  return { key: id, propertyDefId: id, name, visible: false, sortable: true, displayLabel: '', available }
}

function shownName(row: Row): string {
  return row.displayLabel.trim() || row.name
}

/** Configured columns first in their order, then every other attribute hidden. */
onMounted(async () => {
  let mapped: string[] = []
  try {
    const metatype = await fetchMetatype(props.catalog.primaryMetatypeId, props.fetchFn)
    mapped = [...new Set((metatype.propertyMappings ?? []).map((mapping) => mapping.propertyDefinition.id))]
  } catch {
    loadFailed.value = true
  }
  const configured: Row[] = props.catalog.columns.map((column) => {
    const row = column.field ? fieldRow(column.field) : propertyRow(column.propertyDefId!, column.available !== false)
    return {
      ...row,
      visible: column.visible !== false,
      sortable: column.sortable !== false,
      displayLabel: column.displayLabel ?? '',
    }
  })
  const keys = new Set(configured.map((row) => row.key))
  const others = [
    ...SYSTEM_FIELDS.filter((field) => !keys.has(field)).map(fieldRow),
    ...mapped.filter((id) => !keys.has(id)).map((id) => propertyRow(id, true)),
  ]
  if (configured.length === 0) {
    // the default columns of a catalog without configuration (US-049)
    for (const row of others) {
      row.visible = row.field === 'name' || row.name.toLowerCase() === 'status'
    }
  }
  rows.value = [...configured, ...others]
  const sorted = props.catalog.columns.find((column) => column.defaultSortDirection)
  if (sorted) {
    defaultSort.value = `${sorted.field ?? sorted.propertyDefId}:${sorted.defaultSortDirection}`
  }
  loading.value = false
})

function move(from: number, to: number) {
  if (to < 0 || to >= rows.value.length || from === to) {
    return
  }
  const next = [...rows.value]
  const [row] = next.splice(from, 1)
  next.splice(to, 0, row!)
  rows.value = next
}

function drop(to: number) {
  if (dragged.value !== undefined) {
    move(dragged.value, to)
  }
  dragged.value = undefined
}

async function save() {
  if (noneVisible.value) {
    return
  }
  saving.value = true
  failure.value = undefined
  const [sortKey, direction] = defaultSort.value === NONE ? [undefined, undefined] : defaultSort.value.split(':')
  const columns: CatalogColumnConfig[] = rows.value
    .filter((row) => row.available)
    .map((row) => ({
      ...(row.field ? { field: row.field } : { propertyDefId: row.propertyDefId }),
      displayLabel: row.displayLabel.trim() || null,
      visible: row.visible,
      sortable: row.sortable,
      defaultSortDirection: row.key === sortKey ? (direction as 'asc' | 'desc') : null,
    }))
  const outcome = await updateCatalogColumns(props.catalog.id, columns, props.fetchFn)
  saving.value = false
  if (outcome.ok) {
    emit('saved', outcome.value)
  } else {
    failure.value = rateLimitMessage(outcome.rejection) ?? t('catalog.columns.failed')
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <p class="text-sm text-muted">{{ t('catalog.columns.hint') }}</p>
    <UAlert v-if="loadFailed" data-testid="columns-load-failure" color="error" variant="subtle" :description="t('catalog.columns.loadFailed')" />
    <UAlert v-if="failure" data-testid="columns-failure" color="error" variant="subtle" :description="failure" />

    <ol v-if="!loading" data-testid="column-rows" class="flex flex-col gap-2">
      <li
        v-for="(row, index) in rows"
        :key="row.key"
        data-testid="column-row"
        class="flex flex-wrap items-center gap-3 rounded-md border border-default p-2"
        :class="{ 'opacity-60': !row.available }"
        draggable="true"
        @dragstart="dragged = index"
        @dragover.prevent
        @drop.prevent="drop(index)"
      >
        <UIcon name="i-lucide-grip-vertical" class="cursor-grab text-muted" :aria-label="t('catalog.columns.drag', { name: row.name })" />
        <div class="flex flex-col">
          <UButton
            data-testid="column-up"
            icon="i-lucide-chevron-up"
            size="xs"
            color="neutral"
            variant="ghost"
            :disabled="index === 0"
            :aria-label="t('catalog.columns.moveUp', { name: row.name })"
            @click="move(index, index - 1)"
          />
          <UButton
            data-testid="column-down"
            icon="i-lucide-chevron-down"
            size="xs"
            color="neutral"
            variant="ghost"
            :disabled="index === rows.length - 1"
            :aria-label="t('catalog.columns.moveDown', { name: row.name })"
            @click="move(index, index + 1)"
          />
        </div>
        <span data-testid="column-name" class="min-w-40 font-medium text-highlighted">{{ row.name }}</span>
        <UBadge v-if="!row.available" data-testid="column-unavailable" color="neutral" variant="subtle" :label="t('catalog.columns.unavailable')" />
        <USwitch v-model="row.visible" data-testid="column-visible" :disabled="!row.available" :label="t('catalog.columns.visible')" />
        <UInput
          v-model="row.displayLabel"
          data-testid="column-label"
          :placeholder="row.name"
          :aria-label="`${t('catalog.columns.label')} ${row.name}`"
          :disabled="!row.available"
          class="w-48"
        />
        <USwitch v-model="row.sortable" data-testid="column-sortable" :disabled="!row.available" :label="t('catalog.columns.sortable')" />
      </li>
    </ol>

    <UFormField v-if="!loading" :label="t('catalog.columns.defaultSort')">
      <USelect v-model="defaultSort" data-testid="column-default-sort" :items="sortItems" class="w-72" />
    </UFormField>

    <p v-if="!loading && noneVisible" data-testid="columns-none-visible" class="text-sm text-error">{{ t('catalog.columns.noneVisible') }}</p>

    <div class="flex justify-end gap-2">
      <UButton data-testid="columns-cancel" color="neutral" variant="ghost" :label="t('catalog.columns.cancel')" @click="emit('cancel')" />
      <UButton data-testid="columns-save" :loading="saving" :disabled="loading || noneVisible" :label="t('catalog.columns.save')" @click="save" />
    </div>
  </div>
</template>
