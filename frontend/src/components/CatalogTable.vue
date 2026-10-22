<script setup lang="ts">
import UButton from '@nuxt/ui/components/Button.vue'
import type { TableColumn } from '@nuxt/ui'
import { computed, h } from 'vue'
import type { CatalogQueryResult, CatalogRow, CatalogSort } from '../api/catalogs'

// The rows of a catalog (US-049, SCR-020; design D6 of us-049-show-catalog-data). Renders the
// columns the query returns; a header click cycles ascending → descending → default order.
const props = defineProps<{ result: CatalogQueryResult; loading?: boolean }>()
const emit = defineEmits<{ sort: [sort: CatalogSort | undefined]; page: [page: number] }>()

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

const columns = computed<TableColumn<CatalogRow>[]>(() =>
  props.result.columns.map((column) => ({
    id: column.key,
    accessorFn: (row) => row.values[column.key] ?? '',
    enableSorting: column.sortable,
    header: () =>
      column.sortable
        ? h(
            UButton,
            {
              'data-testid': `sort-${column.key}`,
              color: 'neutral',
              variant: 'ghost',
              class: '-mx-2.5',
              onClick: () => emit('sort', nextSort(column.key)),
            },
            () => [column.label, h('span', { 'aria-hidden': 'true', class: 'w-3' }, arrow(column.key))],
          )
        : column.label,
  })),
)

const page = computed(() => props.result.page)
</script>

<template>
  <UEmpty
    v-if="page.totalElements === 0"
    data-testid="catalog-empty"
    icon="i-lucide-table-2"
    title="No entries found"
    description="No entries found. Entities can be captured as a delta in a solution."
  />
  <div v-else class="flex flex-col gap-4">
    <UTable
      data-testid="catalog-table"
      :data="props.result.rows"
      :columns="columns"
      :sorting="sorting"
      :sorting-options="{ manualSorting: true }"
      :loading="props.loading"
      :get-row-id="(row: CatalogRow) => String(row.entityId)"
      class="rounded-lg border border-default"
    />
    <div class="flex items-center justify-between gap-4">
      <p data-testid="catalog-page-info" class="text-sm text-muted">
        Page {{ page.page + 1 }} of {{ page.totalPages }} · {{ page.totalElements }} entries
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
