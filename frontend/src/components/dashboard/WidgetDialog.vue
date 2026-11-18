<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchCatalog, fetchCatalogs, type CatalogSavedView, type CatalogSummary } from '../../api/catalogs'
import { LIMITS, type WidgetConfig, type WidgetType, type WidgetWrite } from '../../api/dashboards'
import { RequestRejected } from '../../api/problem'

// Add and edit a widget (US-058, US-059; design D4 of us-055-dashboards). A text widget has Markdown
// content, a table widget a catalog, optionally one of its saved views, and a page size. The type
// of an existing widget does not change. A shared dashboard offers shared catalogs only; the server
// rejects the others with 422 naming catalogId.
const props = defineProps<{
  fetchFn?: typeof fetch
  /** the widget to edit, or undefined to add one */
  widget?: WidgetConfig
  /** whether the dashboard is shared: only shared catalogs fit */
  shared: boolean
  /** where a new widget goes */
  position: { col: number; row: number; width: number; height: number }
  /** the rejection of the last submit, shown at its fields */
  rejection?: { invalidFields: string[]; forbidden: boolean }
  submitting?: boolean
}>()
const emit = defineEmits<{ submit: [widget: WidgetWrite]; cancel: [] }>()
const { t } = useI18n()

type Field = 'title' | 'content' | 'catalogId' | 'savedViewId' | 'pageSize'

const form = reactive<{ type: WidgetType; title: string; content: string; catalogId?: string; savedViewId: string; pageSize: number }>({
  type: props.widget?.type ?? 'text',
  title: props.widget?.title ?? '',
  content: props.widget?.config.content ?? '',
  catalogId: props.widget?.config.catalogId,
  savedViewId: props.widget?.config.savedViewId ?? '',
  pageSize: props.widget?.config.pageSize ?? LIMITS.defaultPageSize,
})
const errors = ref<Partial<Record<Field, string>>>({})
const catalogs = ref<CatalogSummary[]>([])
const views = ref<CatalogSavedView[]>([])
const failure = ref<string>()

const NO_VIEW = '__none__'

const typeItems = computed(() => [
  { label: t('widgetDialog.text'), description: t('widgetDialog.textHint'), value: 'text' },
  { label: t('widgetDialog.table'), description: t('widgetDialog.tableHint'), value: 'table' },
])
const catalogItems = computed(() =>
  catalogs.value.filter((catalog) => !props.shared || catalog.scope === 'instance').map((catalog) => ({ label: catalog.name, value: catalog.id })),
)
const viewItems = computed(() => [
  { label: t('widgetDialog.noView'), value: NO_VIEW },
  ...views.value.map((view) => ({ label: view.name, value: view.id })),
])

async function loadViews(catalogId: string | undefined) {
  views.value = []
  if (!catalogId) {
    return
  }
  try {
    views.value = (await fetchCatalog(catalogId, props.fetchFn))?.savedViews ?? []
  } catch {
    views.value = []
  }
}

onMounted(async () => {
  try {
    catalogs.value = await fetchCatalogs(props.fetchFn)
  } catch (error) {
    failure.value = error instanceof RequestRejected || error instanceof Error ? t('widgetDialog.catalogsFailed') : undefined
  }
  await loadViews(form.catalogId)
})

watch(
  () => form.catalogId,
  async (catalogId, previous) => {
    if (previous !== undefined || catalogId !== props.widget?.config.catalogId) {
      form.savedViewId = ''
    }
    await loadViews(catalogId)
  },
)

// the rejection of the server lands at its fields; a nested field is named by its last part
watch(
  () => props.rejection,
  (rejection) => {
    if (!rejection) {
      return
    }
    failure.value = rejection.forbidden ? t('widgetDialog.catalogForbidden') : undefined
    const found: Partial<Record<Field, string>> = {}
    for (const name of rejection.invalidFields) {
      const field = name.split('.').pop() as string
      if (['title', 'content', 'catalogId', 'savedViewId', 'pageSize'].includes(field)) {
        found[field as Field] = t(`widgetDialog.field.${field}`)
      } else if (['type', 'widgets', 'position', 'width'].includes(field)) {
        failure.value = t(`widgetDialog.field.${field}`)
      }
    }
    errors.value = found
  },
)

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const title = form.title.trim()
  if (title.length === 0 || title.length > LIMITS.title) {
    found.title = t('widgetDialog.field.title')
  }
  if (form.type === 'text' && form.content.length > LIMITS.content) {
    found.content = t('widgetDialog.field.content')
  }
  if (form.type === 'table') {
    if (!form.catalogId) {
      found.catalogId = t('widgetDialog.field.catalogId')
    }
    if (!Number.isInteger(form.pageSize) || form.pageSize < LIMITS.minPageSize || form.pageSize > LIMITS.maxPageSize) {
      found.pageSize = t('widgetDialog.field.pageSize')
    }
  }
  return found
}

function submit() {
  failure.value = undefined
  errors.value = validate()
  if (Object.keys(errors.value).length > 0) {
    return
  }
  const position = props.widget?.position ?? props.position
  emit('submit', {
    type: form.type,
    title: form.title.trim(),
    position: { ...position },
    config:
      form.type === 'text'
        ? { content: form.content }
        : {
            catalogId: form.catalogId,
            ...(form.savedViewId && form.savedViewId !== NO_VIEW ? { savedViewId: form.savedViewId } : {}),
            pageSize: form.pageSize,
          },
  })
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="widget-form" @submit.prevent="submit">
    <UAlert v-if="failure" data-testid="widget-failure" color="error" variant="subtle" :description="failure" />

    <UFormField v-if="!props.widget" :label="t('widgetDialog.type')" name="type">
      <URadioGroup v-model="form.type" data-testid="widget-type" orientation="horizontal" :items="typeItems" />
    </UFormField>
    <UFormField :label="t('widgetDialog.title')" name="title" :error="errors.title" required>
      <UInput v-model="form.title" data-testid="widget-title" class="w-full" autofocus />
    </UFormField>

    <template v-if="form.type === 'text'">
      <UFormField
        :label="t('widgetDialog.content')"
        name="content"
        :error="errors.content"
        :help="t('widgetDialog.contentHelp', { count: form.content.length })"
      >
        <UTextarea v-model="form.content" data-testid="widget-content" :rows="6" class="w-full" />
      </UFormField>
    </template>
    <template v-else>
      <UFormField :label="t('widgetDialog.catalog')" name="catalogId" :error="errors.catalogId" required>
        <USelect
          v-model="form.catalogId"
          data-testid="widget-catalog"
          :items="catalogItems"
          :placeholder="t('widgetDialog.catalogPlaceholder')"
          class="w-full"
        />
      </UFormField>
      <UFormField :label="t('widgetDialog.view')" name="savedViewId" :error="errors.savedViewId" :help="t('widgetDialog.viewHelp')">
        <USelect
          :model-value="form.savedViewId || NO_VIEW"
          data-testid="widget-view"
          :items="viewItems"
          :disabled="!form.catalogId"
          class="w-full"
          @update:model-value="(value: string) => (form.savedViewId = value)"
        />
      </UFormField>
      <UFormField :label="t('widgetDialog.pageSize')" name="pageSize" :error="errors.pageSize" :help="t('widgetDialog.pageSizeHelp')">
        <UInputNumber v-model="form.pageSize" data-testid="widget-page-size" :min="LIMITS.minPageSize" :max="LIMITS.maxPageSize" class="w-40" />
      </UFormField>
    </template>

    <div class="flex justify-end gap-2">
      <UButton data-testid="widget-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton
        type="submit"
        data-testid="widget-submit"
        :loading="props.submitting"
        :label="props.widget ? t('widgetDialog.submitSave') : t('widgetDialog.submitAdd')"
      />
    </div>
  </form>
</template>
