<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  addCatalogJoin,
  removeCatalogJoin,
  SYSTEM_FIELDS,
  updateCatalogJoin,
  type Catalog,
  type CatalogColumnConfig,
  type CatalogJoinDefinition,
  type CatalogSystemField,
  type JoinDirection,
  type JoinMode,
} from '../api/catalogs'
import { fetchMetatype, fetchMetatypes, type MetatypeDetail, type PropertyDefinitionDetail } from '../api/metamodel'
import { rateLimitMessage } from '../api/problem'
import { label, labelKeys } from '../i18n/labels'

// The dialog "Joins" of SCR-020 (US-048, REQ-045; design D5 of us-048-catalog-joins): the joins
// of the catalog with edit and remove, and a form in four steps – connection metatype, direction,
// target metatype, columns and mode. The options follow the allowed endpoints of the connection
// metatypes (US-036); the server checks them again.
const props = defineProps<{ catalog: Catalog; definitions: PropertyDefinitionDetail[]; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ changed: [catalog: Catalog]; close: [] }>()
const { t } = useI18n()

interface ColumnRow {
  key: string
  field?: CatalogSystemField
  propertyDefId?: string
  name: string
  visible: boolean
  sortable: boolean
  displayLabel: string
}

const CATALOG_DEFAULT = 'catalog'
const metatypes = ref<MetatypeDetail[]>([])
const loadFailed = ref(false)
const failure = ref<string>()
const busy = ref(false)
// the join being edited: undefined for the list, null for a new join
const editing = ref<CatalogJoinDefinition | null>()
const connectionId = ref<string>()
const direction = ref<JoinDirection>()
const targetId = ref<string>()
const name = ref('')
const mode = ref<string>(CATALOG_DEFAULT)
const columns = ref<ColumnRow[]>([])

onMounted(async () => {
  try {
    metatypes.value = await fetchMetatypes(props.fetchFn)
  } catch {
    loadFailed.value = true
  }
})

const byId = computed(() => new Map(metatypes.value.map((metatype) => [metatype.id, metatype])))

function metatypeName(id: string | undefined): string {
  const metatype = id ? byId.value.get(id) : undefined
  return metatype ? label(labelKeys.metatype(metatype.id), metatype.name) : (id ?? '')
}

/** The metatype and its base types. */
function chain(id: string): string[] {
  const ids: string[] = []
  let current: string | null | undefined = id
  while (current && !ids.includes(current)) {
    ids.push(current)
    current = byId.value.get(current)?.baseMetatypeId
  }
  return ids
}

/** Whether a side of the connection admits the metatype; no constraint admits every metatype. */
function admits(connection: MetatypeDetail, side: 'source' | 'target', metatypeId: string): boolean {
  const allowed = (connection.connectionConstraints ?? [])
    .filter((constraint) => constraint.direction === side)
    .map((constraint) => constraint.allowedMetatype?.id)
  return allowed.length === 0 || chain(metatypeId).some((id) => allowed.includes(id))
}

const primary = computed(() => props.catalog.primaryMetatypeId)
const connectionItems = computed(() =>
  metatypes.value
    .filter((metatype) => metatype.isConnection)
    .filter((connection) => admits(connection, 'source', primary.value) || admits(connection, 'target', primary.value))
    .map((connection) => ({ label: metatypeName(connection.id), value: connection.id })),
)
const connectionCatalogWithoutJoins = computed(
  () => !loadFailed.value && byId.value.get(primary.value)?.isConnection === true && connectionItems.value.length === 0,
)
const connection = computed(() => (connectionId.value ? byId.value.get(connectionId.value) : undefined))
const directionItems = computed(() => {
  if (!connection.value) {
    return []
  }
  const items: { label: string; value: JoinDirection }[] = []
  if (admits(connection.value, 'source', primary.value)) {
    items.push({ label: t('catalog.joins.outbound'), value: 'outbound' })
  }
  if (admits(connection.value, 'target', primary.value)) {
    items.push({ label: t('catalog.joins.inbound'), value: 'inbound' })
  }
  items.push({ label: t('catalog.joins.both'), value: 'both' })
  return items
})
const targetItems = computed(() => {
  const via = connection.value
  if (!via || !direction.value) {
    return []
  }
  return metatypes.value
    .filter((metatype) => !metatype.isConnection)
    .filter((metatype) => {
      const outbound = admits(via, 'source', primary.value) && admits(via, 'target', metatype.id)
      const inbound = admits(via, 'target', primary.value) && admits(via, 'source', metatype.id)
      return direction.value === 'outbound' ? outbound : direction.value === 'inbound' ? inbound : outbound || inbound
    })
    .map((metatype) => ({ label: metatypeName(metatype.id), value: metatype.id }))
})
const modeItems = computed(() => [
  { label: t('catalog.joins.catalogDefault'), value: CATALOG_DEFAULT },
  { label: t('catalog.joins.compact'), value: 'aggregate' },
  { label: t('catalog.joins.rows'), value: 'rows' },
])
const noneVisible = computed(() => !columns.value.some((row) => row.visible))
const canSave = computed(
  () => !busy.value && !!connectionId.value && !!direction.value && !!targetId.value && name.value.trim() !== '' && !noneVisible.value,
)

function fieldRow(field: CatalogSystemField): ColumnRow {
  return { key: field, field, name: t(`catalog.columns.field.${field}`), visible: false, sortable: true, displayLabel: '' }
}

function propertyRow(id: string): ColumnRow {
  const definition = props.definitions.find((candidate) => candidate.id === id)
  return {
    key: id,
    propertyDefId: id,
    name: definition ? label(labelKeys.property(id), definition.name) : id,
    visible: false,
    sortable: true,
    displayLabel: '',
  }
}

/** The attributes of the target metatype: the configured columns first, then the others hidden. */
async function loadColumns(target: string, configured: CatalogColumnConfig[]) {
  let mapped: string[] = []
  try {
    const metatype = await fetchMetatype(target, props.fetchFn)
    mapped = [...new Set((metatype.propertyMappings ?? []).map((mapping) => mapping.propertyDefinition.id))]
  } catch {
    loadFailed.value = true
  }
  const rows: ColumnRow[] = configured.map((column) => ({
    ...(column.field ? fieldRow(column.field) : propertyRow(column.propertyDefId!)),
    visible: column.visible !== false,
    sortable: column.sortable !== false,
    displayLabel: column.displayLabel ?? '',
  }))
  const keys = new Set(rows.map((row) => row.key))
  const others = [...SYSTEM_FIELDS.filter((field) => !keys.has(field)).map(fieldRow), ...mapped.filter((id) => !keys.has(id)).map(propertyRow)]
  if (rows.length === 0) {
    others.forEach((row) => (row.visible = row.field === 'name'))
  }
  columns.value = [...rows, ...others]
}

function startAdd() {
  failure.value = undefined
  editing.value = null
  connectionId.value = undefined
  direction.value = undefined
  targetId.value = undefined
  name.value = ''
  mode.value = CATALOG_DEFAULT
  columns.value = []
}

async function startEdit(join: CatalogJoinDefinition) {
  failure.value = undefined
  editing.value = join
  connectionId.value = join.connectionMetatypeId
  direction.value = join.joinDirection
  name.value = join.name
  mode.value = join.defaultJoinMode ?? CATALOG_DEFAULT
  targetId.value = join.targetMetatypeId
  await loadColumns(join.targetMetatypeId, join.targetColumns)
}

function chooseConnection(value: string) {
  connectionId.value = value
  direction.value = undefined
  targetId.value = undefined
  columns.value = []
}

function chooseDirection(value: JoinDirection) {
  direction.value = value
  targetId.value = undefined
  columns.value = []
}

async function chooseTarget(value: string) {
  targetId.value = value
  if (!name.value.trim()) {
    name.value = metatypeName(value)
  }
  await loadColumns(value, [])
}

function move(from: number, to: number) {
  if (to < 0 || to >= columns.value.length) {
    return
  }
  const next = [...columns.value]
  const [row] = next.splice(from, 1)
  next.splice(to, 0, row!)
  columns.value = next
}

async function save() {
  if (!canSave.value) {
    return
  }
  busy.value = true
  failure.value = undefined
  const input = {
    name: name.value.trim(),
    connectionMetatypeId: connectionId.value!,
    joinDirection: direction.value!,
    targetMetatypeId: targetId.value!,
    targetColumns: columns.value.map((row) => ({
      ...(row.field ? { field: row.field } : { propertyDefId: row.propertyDefId }),
      displayLabel: row.displayLabel.trim() || null,
      visible: row.visible,
      sortable: row.sortable,
    })),
    defaultJoinMode: mode.value === CATALOG_DEFAULT ? null : (mode.value as JoinMode),
  }
  const outcome = editing.value
    ? await updateCatalogJoin(props.catalog.id, editing.value.id, input, props.fetchFn)
    : await addCatalogJoin(props.catalog.id, input, props.fetchFn)
  busy.value = false
  if (outcome.ok) {
    editing.value = undefined
    emit('changed', outcome.value)
  } else {
    failure.value = rateLimitMessage(outcome.rejection) ?? t('catalog.joins.failed')
  }
}

async function remove(join: CatalogJoinDefinition) {
  busy.value = true
  failure.value = undefined
  const outcome = await removeCatalogJoin(props.catalog.id, join.id, props.fetchFn)
  busy.value = false
  if (outcome.ok) {
    emit('changed', outcome.value)
  } else {
    failure.value = rateLimitMessage(outcome.rejection) ?? t('catalog.joins.failed')
  }
}

function directionLabel(value: JoinDirection): string {
  return t(`catalog.joins.${value}`)
}

function modeLabel(value: JoinMode | null | undefined): string {
  return value === 'rows' ? t('catalog.joins.rows') : value === 'aggregate' ? t('catalog.joins.compact') : t('catalog.joins.catalogDefault')
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <UAlert v-if="loadFailed" data-testid="joins-load-failure" color="error" variant="subtle" :description="t('catalog.joins.loadFailed')" />
    <UAlert v-if="failure" data-testid="joins-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="editing === undefined">
      <p class="text-sm text-muted">{{ t('catalog.joins.hint') }}</p>
      <p v-if="catalog.joinDefinitions.length === 0" data-testid="joins-empty" class="text-sm text-toned">{{ t('catalog.joins.none') }}</p>
      <!-- a connection catalog that no connection type can join (US-080, REQ-065 BR-04) -->
      <UAlert
        v-if="connectionCatalogWithoutJoins"
        data-testid="joins-connection-catalog"
        color="neutral"
        variant="subtle"
        icon="i-lucide-info"
        :description="t('catalog.joins.connectionCatalogHint')"
      />
      <ul v-else data-testid="join-list" class="flex flex-col gap-2">
        <li
          v-for="join in catalog.joinDefinitions"
          :key="join.id"
          data-testid="join-item"
          class="flex flex-wrap items-center gap-3 rounded-md border border-default p-2"
        >
          <div class="flex min-w-0 flex-1 flex-col">
            <span data-testid="join-name" class="font-medium text-highlighted">{{ join.name }}</span>
            <span class="text-sm text-toned">
              {{ metatypeName(join.connectionMetatypeId) }} · {{ directionLabel(join.joinDirection) }} ·
              {{ metatypeName(join.targetMetatypeId) }} · {{ modeLabel(join.defaultJoinMode) }}
            </span>
          </div>
          <UBadge v-if="!join.available" data-testid="join-unavailable" color="neutral" variant="subtle" :label="t('catalog.columns.unavailable')" />
          <UButton
            data-testid="join-edit"
            size="sm"
            color="neutral"
            variant="outline"
            :label="t('catalog.joins.edit')"
            :aria-label="t('catalog.joins.editNamed', { name: join.name })"
            :disabled="busy"
            @click="startEdit(join)"
          />
          <UButton
            data-testid="join-remove"
            size="sm"
            color="error"
            variant="outline"
            :label="t('catalog.joins.remove')"
            :aria-label="t('catalog.joins.removeNamed', { name: join.name })"
            :disabled="busy"
            @click="remove(join)"
          />
        </li>
      </ul>
      <div class="flex justify-between gap-2">
        <UButton data-testid="join-add" icon="i-lucide-plus" :label="t('catalog.joins.add')" :disabled="busy || loadFailed" @click="startAdd" />
        <UButton data-testid="joins-close" color="neutral" variant="ghost" :label="t('catalog.joins.close')" @click="emit('close')" />
      </div>
    </template>

    <template v-else>
      <UFormField :label="t('catalog.joins.connection')" :hint="t('catalog.joins.step', { step: 1 })">
        <USelect
          data-testid="join-connection"
          :model-value="connectionId"
          :items="connectionItems"
          class="w-72"
          @update:model-value="(value: string) => chooseConnection(value)"
        />
      </UFormField>
      <p v-if="connectionItems.length === 0" data-testid="join-no-connections" class="text-sm text-toned">{{ t('catalog.joins.noConnections') }}</p>
      <UFormField :label="t('catalog.joins.direction')" :hint="t('catalog.joins.step', { step: 2 })">
        <USelect
          data-testid="join-direction"
          :model-value="direction"
          :items="directionItems"
          :disabled="!connectionId"
          class="w-72"
          @update:model-value="(value: JoinDirection) => chooseDirection(value)"
        />
      </UFormField>
      <UFormField :label="t('catalog.joins.target')" :hint="t('catalog.joins.step', { step: 3 })">
        <USelect
          data-testid="join-target"
          :model-value="targetId"
          :items="targetItems"
          :disabled="!direction"
          class="w-72"
          @update:model-value="(value: string) => chooseTarget(value)"
        />
      </UFormField>

      <template v-if="targetId">
        <div class="flex flex-wrap gap-4">
          <UFormField :label="t('catalog.joins.name')" :hint="t('catalog.joins.step', { step: 4 })">
            <UInput v-model="name" data-testid="join-name-input" class="w-72" />
          </UFormField>
          <UFormField :label="t('catalog.joins.defaultMode')">
            <USelect v-model="mode" data-testid="join-mode" :items="modeItems" class="w-56" />
          </UFormField>
        </div>
        <ol data-testid="join-columns" class="flex flex-col gap-2" :aria-label="t('catalog.joins.columns')">
          <li
            v-for="(row, index) in columns"
            :key="row.key"
            data-testid="join-column"
            class="flex flex-wrap items-center gap-3 rounded-md border border-default p-2"
          >
            <div class="flex flex-col">
              <UButton
                icon="i-lucide-chevron-up"
                size="xs"
                color="neutral"
                variant="ghost"
                :disabled="index === 0"
                :aria-label="t('catalog.columns.moveUp', { name: row.name })"
                @click="move(index, index - 1)"
              />
              <UButton
                icon="i-lucide-chevron-down"
                size="xs"
                color="neutral"
                variant="ghost"
                :disabled="index === columns.length - 1"
                :aria-label="t('catalog.columns.moveDown', { name: row.name })"
                @click="move(index, index + 1)"
              />
            </div>
            <span data-testid="join-column-name" class="min-w-40 font-medium text-highlighted">{{ row.name }}</span>
            <USwitch v-model="row.visible" data-testid="join-column-visible" :label="t('catalog.columns.visible')" />
            <UInput v-model="row.displayLabel" :placeholder="row.name" :aria-label="`${t('catalog.columns.label')} ${row.name}`" class="w-48" />
            <USwitch v-model="row.sortable" :label="t('catalog.columns.sortable')" />
          </li>
        </ol>
        <p v-if="noneVisible" data-testid="join-none-visible" class="text-sm text-error">{{ t('catalog.columns.noneVisible') }}</p>
      </template>

      <div class="flex justify-end gap-2">
        <UButton data-testid="join-cancel" color="neutral" variant="ghost" :label="t('catalog.columns.cancel')" @click="editing = undefined" />
        <UButton data-testid="join-save" :loading="busy" :disabled="!canSave" :label="t('catalog.columns.save')" @click="save" />
      </div>
    </template>
  </div>
</template>
