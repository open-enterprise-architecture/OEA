<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetData } from '../../api/dashboards'
import type { Outcome } from '../../api/session'

// A table widget (US-058): the page of rows from the dashboard data, and further pages per widget
// (design D4 of us-055-dashboards). Values are text; a column the reader may not see is marked and
// its values are empty, as in the catalog. A catalog or saved view that is gone shows its message.
// `loadPage` reads another page (0-based): the work area and the portal pass their own operation
// (design D4 of us-061-portal-dashboards).
const props = defineProps<{ data?: WidgetData; loadPage: (page: number) => Promise<Outcome<WidgetData>> }>()
const { t } = useI18n()

const current = ref<WidgetData | undefined>(props.data)
const loading = ref(false)
const failure = ref(false)

watch(
  () => props.data,
  (data) => {
    current.value = data
  },
)

async function goTo(page: number) {
  loading.value = true
  failure.value = false
  try {
    const outcome = await props.loadPage(page - 1)
    if (outcome.ok) {
      current.value = outcome.value
    } else {
      failure.value = true
    }
  } catch {
    failure.value = true
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex h-full flex-col gap-2 text-sm" :aria-busy="loading">
    <UAlert
      v-if="current?.status === 'error'"
      data-testid="widget-error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="current.error ? t(`dashboard.widget.error.${current.error.code}`) : t('dashboard.widget.noData')"
    />
    <p v-else-if="!current?.table" data-testid="widget-no-data" class="text-muted">{{ t('dashboard.widget.noData') }}</p>
    <template v-else>
      <p v-if="current.table.page.totalElements === 0" data-testid="widget-no-rows" class="text-muted">{{ t('dashboard.widget.noRows') }}</p>
      <div v-else class="min-h-0 flex-1 overflow-auto" tabindex="0" role="region" :aria-label="t('dashboard.widget.tableRegion')">
        <table class="w-full border-collapse text-left" data-testid="widget-table">
          <thead>
            <tr>
              <th
                v-for="column in current.table.columns"
                :key="column.key"
                scope="col"
                class="border-b border-default px-2 py-1 font-medium text-highlighted"
              >
                <span v-if="column.readable === false" class="inline-flex items-center gap-1" :data-testid="`widget-restricted-${column.key}`" :title="t('dashboard.widget.restrictedColumn')">
                  {{ column.label }}
                  <UIcon name="i-lucide-lock" class="size-3.5 text-muted" :aria-label="t('dashboard.widget.restrictedColumn')" />
                </span>
                <template v-else>{{ column.label }}</template>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in current.table.rows" :key="row.entityId" data-testid="widget-row">
              <td v-for="column in current.table.columns" :key="column.key" class="border-b border-default px-2 py-1">
                {{ row.values[column.key] ?? '' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p data-testid="widget-page-info" class="text-muted">
          {{
            t('dashboard.widget.pageInfo', {
              page: current.table.page.page + 1,
              pages: current.table.page.totalPages,
              entries: current.table.page.totalElements,
            })
          }}
        </p>
        <UPagination
          v-if="current.table.page.totalPages > 1"
          data-testid="widget-pagination"
          :aria-label="t('dashboard.widget.pagination')"
          :page="current.table.page.page + 1"
          :items-per-page="current.table.page.pageSize"
          :total="current.table.page.totalElements"
          size="xs"
          @update:page="goTo"
        />
      </div>
      <p v-if="failure" data-testid="widget-page-failure" class="text-error">{{ t('dashboard.widget.pageFailed') }}</p>
    </template>
  </div>
</template>
