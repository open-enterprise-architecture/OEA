<script setup lang="ts">
import UButton from '@nuxt/ui/components/Button.vue'
import UIcon from '@nuxt/ui/components/Icon.vue'
import type { TableColumn } from '@nuxt/ui'
import { computed, h } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import type { CatalogColumn, CatalogQueryResult, CatalogRow, CatalogSort, JoinMode } from '../api/catalogs'
import { session } from '../session/store'

// The rows of a catalog (US-049, SCR-020; design D6 of us-049-show-catalog-data). Renders the
// columns the query returns; a header click cycles ascending → descending → default order. Every
// join adds a column group with a "Compact / Rows" switch (US-048, US-050; design D5 of
// us-048-catalog-joins): compact cells list one value per result, rows cells the single value.
// readOnly: the join headers show the name without the mode switch (portal, s9-portal-catalogs D7)
// Every entity row has the action "History" to its change history (SCR-026; design D5 of
// us-098-entity-history), with this catalog as the page to return to; it needs entity:read.
const props = defineProps<{ result: CatalogQueryResult; loading?: boolean; primaryLabel?: string; readOnly?: boolean }>()
const emit = defineEmits<{ sort: [sort: CatalogSort | undefined]; page: [page: number]; mode: [joinId: string, mode: JoinMode] }>()
const { t } = useI18n()
const route = useRoute()

const canReadHistory = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:read'))

function historyAction(row: CatalogRow) {
  return h(UButton, {
    'data-testid': `history-${row.entityId}`,
    to: { path: `/entities/${row.entityId}/history`, query: { from: route?.fullPath ?? '/catalogs' } },
    color: 'neutral',
    variant: 'ghost',
    size: 'sm',
    icon: 'i-lucide-history',
    label: t('catalog.history'),
  })
}

const sort = computed(() => props.result.sort ?? undefined)

// the table only shows the server's order; aria-sort follows from this state
const sorting = computed(() => (sort.value ? [{ id: sort.value.column, desc: sort.value.direction === 'desc' }] : []))

function nextSort(key: string): CatalogSort | undefined {
  if (sort.value?.column !== key) {
    return { column: key, direction: 'asc' }
  }
  return sort.value.direction === 'asc' ? { column: key, direction: 'desc' } : undefined
}

function arrow(key: string): string {
  if (sort.value?.column !== key) {
    return ''
  }
  return sort.value.direction === 'asc' ? '↑' : '↓'
}

function header(column: CatalogColumn, key: string) {
  if (column.readable === false) {
    // the reader may not see the values of this column (us-126-role-restricted-enforcement D6)
    return () =>
      h('span', { class: 'inline-flex items-center gap-1', 'data-testid': `restricted-${key}`, title: t('catalog.restrictedColumn') }, [
        column.label,
        h(UIcon, { name: 'i-lucide-lock', class: 'size-3.5 text-muted', 'aria-label': t('catalog.restrictedColumn') }),
      ])
  }
  return () =>
    column.sortable
      ? h(
          UButton,
          {
            'data-testid': `sort-${key}`,
            color: 'neutral',
            variant: 'ghost',
            class: '-mx-2.5',
            onClick: () => emit('sort', nextSort(key)),
          },
          () => [column.label, h('span', { 'aria-hidden': 'true', class: 'w-3' }, arrow(key))],
        )
      : column.label
}

function modeSwitch(joinId: string, name: string, mode: JoinMode) {
  if (props.readOnly) {
    return () => h('span', { class: 'font-semibold text-highlighted', 'data-testid': `join-header-${joinId}` }, name)
  }
  const option = (value: JoinMode, text: string) =>
    h(UButton, {
      'data-testid': `join-mode-${value}`,
      size: 'xs',
      color: mode === value ? 'primary' : 'neutral',
      variant: mode === value ? 'solid' : 'outline',
      'aria-pressed': mode === value,
      label: text,
      onClick: () => mode !== value && emit('mode', joinId, value),
    })
  return () =>
    h('div', { class: 'flex flex-wrap items-center gap-2', 'data-testid': `join-header-${joinId}` }, [
      h('span', { class: 'font-semibold text-highlighted' }, name),
      h('div', { class: 'flex gap-1', role: 'group', 'aria-label': t('catalog.joins.modeOf', { name }) }, [
        option('aggregate', t('catalog.joins.compact')),
        option('rows', t('catalog.joins.rows')),
      ]),
    ])
}

const columns = computed<TableColumn<CatalogRow>[]>(() => {
  const primary: TableColumn<CatalogRow>[] = props.result.columns.map((column) => ({
    id: column.key,
    accessorFn: (row: CatalogRow) => row.values[column.key] ?? '',
    enableSorting: column.sortable,
    header: header(column, column.key),
  }))
  if (canReadHistory.value) {
    primary.push({
      id: 'actions',
      enableSorting: false,
      header: () => h('span', { class: 'sr-only' }, t('catalog.actions')),
      cell: ({ row }: { row: { original: CatalogRow } }) => historyAction(row.original),
    })
  }
  const joins = props.result.joins ?? []
  if (joins.length === 0) {
    return primary
  }
  // with joins the header has two rows; the primary columns get a group too, so no header is empty
  return [
    { id: 'primary', header: props.primaryLabel ?? '', columns: primary },
    ...joins.map((join) => ({
    id: `join-${join.id}`,
    header: modeSwitch(join.id, join.name, join.mode),
    columns: join.columns.map((column) => {
      const key = `${join.id}.${column.key}`
      return {
        id: key,
        enableSorting: column.sortable,
        header: header(column, key),
        cell: ({ row }: { row: { original: CatalogRow } }) => {
          const values = (row.original.joinResults?.[join.id] ?? []).map((result) => result.values[column.key] ?? '')
          return join.mode === 'rows' ? (values[0] ?? '') : h('ul', { 'data-testid': 'join-cell' }, values.map((value) => h('li', value)))
        },
      }
    }),
  })),
  ]
})

const page = computed(() => props.result.page)
</script>

<template>
  <UEmpty
    v-if="page.totalElements === 0"
    data-testid="catalog-empty"
    icon="i-lucide-table-2"
    :title="t('catalog.noEntries')"
    :description="t('catalog.noEntriesHint')"
  />
  <div v-else class="flex flex-col gap-4">
    <UTable
      data-testid="catalog-table"
      :data="props.result.rows"
      :columns="columns"
      :sorting="sorting"
      :sorting-options="{ manualSorting: true }"
      :loading="props.loading"
      :get-row-id="(row: CatalogRow, index: number) => `${row.entityId}-${index}`"
      class="rounded-lg border border-default"
    />
    <div class="flex items-center justify-between gap-4">
      <p data-testid="catalog-page-info" class="text-sm text-muted">
        {{ t('catalog.pageInfo', { page: page.page + 1, pages: page.totalPages, entries: page.totalElements }) }}
      </p>
      <UPagination
        v-if="page.totalPages > 1"
        data-testid="catalog-pagination"
        :page="page.page + 1"
        :items-per-page="page.pageSize"
        :total="page.totalElements"
        @update:page="(next: number) => emit('page', next - 1)"
      />
    </div>
  </div>
</template>
