<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  addWidget,
  deleteDashboard,
  fetchDashboard,
  fetchDashboardData,
  LIMITS,
  nextFreeRow,
  removeWidget,
  updateDashboard,
  updateLayout,
  updateWidget,
  type DashboardConfig,
  type DashboardData,
  type DashboardScope,
  type LayoutPosition,
  type PositionProblem,
  type WidgetConfig,
  type WidgetWrite,
} from '../api/dashboards'
import { rateLimitMessage, RequestRejected, type Rejection } from '../api/problem'
import type { Outcome } from '../api/session'
import DashboardGrid from '../components/dashboard/DashboardGrid.vue'
import TableWidget from '../components/dashboard/TableWidget.vue'
import TextWidget from '../components/dashboard/TextWidget.vue'
import WidgetDialog from '../components/dashboard/WidgetDialog.vue'
import { refreshSession, session } from '../session/store'

// A dashboard (UC-07, US-055, US-058 to US-060, US-062, SCR-024): the widgets in the 12-column grid.
// Whoever may change it (canEdit) has the edit mode: add, edit and remove widgets, arrange them by
// drag and drop or buttons, rename, share and delete the dashboard. Every write sends the version
// that was read as If-Match; a 412 reloads the dashboard (design D4 of us-055-dashboards).
const props = defineProps<{ id: string; fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

const config = ref<DashboardConfig>()
const data = ref<DashboardData>()
const loaded = ref(false)
const notFound = ref(false)
const failure = ref<string>()
const notice = ref<string>()
const editing = ref(false)
const saving = ref(false)

const widgetDialog = ref<{ widget?: WidgetConfig }>()
const widgetRejection = ref<{ invalidFields: string[]; forbidden: boolean }>()
const renaming = ref(false)
const rename = reactive({ name: '', description: '', error: '' })
const confirming = ref<{ kind: 'dashboard' } | { kind: 'widget'; widget: WidgetConfig }>()

const permissions = computed(() => new Set(session.me?.permissions.map((entry) => entry.permission) ?? []))
const canEdit = computed(() => config.value?.canEdit === true)
const canShare = computed(() => canEdit.value && config.value?.scope === 'personal' && permissions.value.has('dashboard:manage'))
const foreign = computed(() => config.value?.scope === 'personal' && config.value.owner.id !== session.me?.principal.id)
const dataOf = computed(() => new Map((data.value?.widgets ?? []).map((widget) => [widget.widgetId, widget])))
const newPosition = computed(() => ({ col: 1, row: nextFreeRow(config.value?.widgets ?? []), width: 12, height: 2 }))

function message(error: unknown, fallback: string): string {
  return rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? fallback
}

async function loadData() {
  try {
    data.value = await fetchDashboardData(props.id, props.fetchFn)
  } catch (error) {
    failure.value = message(error, t('dashboard.loadFailed'))
  }
}

async function load() {
  failure.value = undefined
  try {
    config.value = await fetchDashboard(props.id, props.fetchFn)
    notFound.value = config.value === undefined
    if (config.value) {
      await loadData()
    }
  } catch (error) {
    failure.value = message(error, t('dashboard.loadFailed'))
  } finally {
    loaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!permissions.value.has('entity:read')) {
    loaded.value = true
    return
  }
  await load()
})

/**
 * Runs a write with the version that was read. 412 reloads the dashboard and tells why; other
 * rejections are returned to the caller, which shows them where they belong.
 */
async function write(operation: (version: number) => Promise<Outcome<DashboardConfig>>, reloadData: boolean): Promise<Rejection | undefined> {
  if (!config.value) {
    return undefined
  }
  failure.value = undefined
  notice.value = undefined
  saving.value = true
  try {
    const outcome = await operation(config.value.version)
    if (outcome.ok) {
      config.value = outcome.value
      if (reloadData) {
        await loadData()
      }
      return undefined
    }
    if (outcome.rejection.status === 412) {
      notice.value = t('dashboard.stale')
      await load()
    } else if (outcome.rejection.status === 403) {
      failure.value = t('dashboard.forbidden')
    }
    return outcome.rejection
  } catch (error) {
    failure.value = message(error, t('common.backendUnreachable'))
    return undefined
  } finally {
    saving.value = false
  }
}

// ---- layout

async function onLayout(positions: LayoutPosition[]) {
  const rejection = await write((version) => updateLayout(props.id, positions, version, props.fetchFn), false)
  if (rejection && rejection.status !== 412 && rejection.status !== 403) {
    failure.value = t('dashboard.layoutFailed')
  }
}

function onInvalid(problem: PositionProblem) {
  notice.value = undefined
  failure.value = problem === 'exceeds-grid' ? t('dashboard.exceedsGrid') : t('dashboard.outOfRange')
}

// ---- widgets

function openAdd() {
  editing.value = true
  widgetRejection.value = undefined
  widgetDialog.value = {}
}

function openEdit(widget: WidgetConfig) {
  widgetRejection.value = undefined
  widgetDialog.value = { widget }
}

async function onWidgetSubmit(widget: WidgetWrite) {
  const existing = widgetDialog.value?.widget
  const positioned = existing ? widget : { ...widget, position: { ...widget.position, height: widget.type === 'table' ? 3 : 2 } }
  const rejection = await write(
    (version) =>
      existing
        ? updateWidget(props.id, existing.id, positioned, version, props.fetchFn)
        : addWidget(props.id, positioned, version, props.fetchFn),
    true,
  )
  if (!rejection) {
    widgetDialog.value = undefined
  } else if (rejection.status === 412) {
    widgetDialog.value = undefined
  } else {
    widgetRejection.value = { invalidFields: [...rejection.invalidFields], forbidden: rejection.status === 403 }
  }
}

async function confirmed() {
  const target = confirming.value
  confirming.value = undefined
  if (!target) {
    return
  }
  if (target.kind === 'widget') {
    await write((version) => removeWidget(props.id, target.widget.id, version, props.fetchFn), true)
    return
  }
  if (!config.value) {
    return
  }
  const outcome = await deleteDashboard(props.id, config.value.version, props.fetchFn).catch(() => undefined)
  if (outcome?.ok) {
    await router.push('/dashboards')
  } else if (outcome?.rejection.status === 412) {
    notice.value = t('dashboard.stale')
    await load()
  } else {
    failure.value = outcome?.rejection.status === 403 ? t('dashboard.forbidden') : t('dashboard.failed')
  }
}

// ---- name, description and scope

function openRename() {
  rename.name = config.value?.name ?? ''
  rename.description = config.value?.description ?? ''
  rename.error = ''
  renaming.value = true
}

async function saveDashboard(scope: DashboardScope, name: string, description: string): Promise<Rejection | undefined> {
  return write((version) => updateDashboard(props.id, { name, description, scope }, version, props.fetchFn), false)
}

async function submitRename() {
  const name = rename.name.trim()
  if (name.length === 0 || name.length > LIMITS.name) {
    rename.error = t('createDashboard.field.name')
    return
  }
  if (rename.description.length > LIMITS.description) {
    rename.error = t('createDashboard.field.description')
    return
  }
  const rejection = await saveDashboard(config.value?.scope ?? 'personal', name, rename.description)
  if (!rejection) {
    renaming.value = false
  } else if (rejection.problemType === 'dashboard-name-taken') {
    rename.error = t('createDashboard.nameTaken')
  } else if (rejection.status === 422) {
    rename.error = t('createDashboard.field.name')
  } else {
    renaming.value = false
  }
}

async function share() {
  if (!config.value) {
    return
  }
  const rejection = await saveDashboard('instance', config.value.name, config.value.description)
  if (rejection?.status === 422) {
    failure.value = rejection.problemType === 'dashboard-name-taken' ? t('createDashboard.nameTaken') : t('dashboard.shareRejected')
  }
}
</script>

<template>
  <main class="mx-auto flex max-w-7xl flex-col gap-4 p-8">
    <RouterLink to="/dashboards" data-testid="back-to-dashboards" class="inline-flex items-center gap-1 text-sm text-muted hover:text-default">
      <UIcon name="i-lucide-arrow-left" class="size-4" aria-hidden="true" />
      {{ t('dashboard.back') }}
    </RouterLink>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert v-else-if="loaded && notFound" data-testid="dashboard-not-found" color="neutral" variant="subtle" :description="t('dashboard.notFound')" />
    <UAlert v-if="failure" data-testid="dashboard-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="dashboard-notice" color="warning" variant="subtle" :description="notice" />

    <template v-if="config">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div class="flex flex-col gap-1">
          <div class="flex items-center gap-3">
            <h1 data-testid="dashboard-title" class="text-2xl font-semibold text-highlighted">{{ config.name }}</h1>
            <UBadge
              :color="config.scope === 'personal' ? 'neutral' : 'primary'"
              variant="subtle"
              :label="config.scope === 'personal' ? t('dashboard.personal') : t('dashboard.shared')"
            />
          </div>
          <p v-if="config.description" class="text-sm text-muted">{{ config.description }}</p>
          <p class="text-sm text-muted">{{ t('dashboard.ownerLabel') }}: {{ config.owner.displayName }}</p>
          <p v-if="foreign && !canEdit" data-testid="dashboard-foreign" class="text-sm text-muted">{{ t('dashboard.readOnlyAdmin') }}</p>
        </div>
        <div v-if="canEdit" class="flex flex-wrap items-center gap-2">
          <template v-if="editing">
            <UButton data-testid="add-widget" icon="i-lucide-plus" :label="t('dashboard.addWidget')" @click="openAdd" />
            <UButton data-testid="rename-dashboard" color="neutral" variant="outline" icon="i-lucide-pencil" :label="t('dashboard.rename')" @click="openRename" />
            <UButton
              v-if="canShare"
              data-testid="share-dashboard"
              color="neutral"
              variant="outline"
              icon="i-lucide-users"
              :label="t('dashboard.makeShared')"
              :loading="saving"
              @click="share"
            />
            <UButton
              data-testid="delete-dashboard"
              color="error"
              variant="outline"
              icon="i-lucide-trash-2"
              :label="t('dashboard.delete')"
              @click="confirming = { kind: 'dashboard' }"
            />
            <UButton data-testid="done-editing" color="neutral" icon="i-lucide-check" :label="t('dashboard.done')" @click="editing = false" />
          </template>
          <UButton v-else data-testid="edit-dashboard" icon="i-lucide-pencil" :label="t('dashboard.edit')" @click="editing = true" />
        </div>
      </div>
      <p v-if="editing" class="text-sm text-muted">{{ t('dashboard.editing') }}</p>

      <UEmpty
        v-if="config.widgets.length === 0"
        data-testid="dashboard-empty"
        icon="i-lucide-layout-dashboard"
        :title="canEdit ? t('dashboard.emptyEditable') : t('dashboard.empty')"
      >
        <template v-if="canEdit" #actions>
          <UButton data-testid="empty-add-widget" icon="i-lucide-plus" :label="t('dashboard.addWidget')" @click="openAdd" />
        </template>
      </UEmpty>
      <DashboardGrid v-else :widgets="config.widgets" :editing="editing" @layout="onLayout" @invalid="onInvalid" @edit="openEdit" @remove="(widget) => (confirming = { kind: 'widget', widget })">
        <template #widget="{ widget }">
          <TextWidget v-if="widget.type === 'text'" :content="widget.config.content ?? ''" />
          <TableWidget
            v-else-if="widget.type === 'table'"
            :dashboard-id="config.id"
            :widget-id="widget.id"
            :data="dataOf.get(widget.id)"
            :fetch-fn="props.fetchFn"
          />
        </template>
      </DashboardGrid>
    </template>

    <UModal
      :open="widgetDialog !== undefined"
      :title="widgetDialog?.widget ? t('widgetDialog.editTitle') : t('widgetDialog.addTitle')"
      @update:open="(open: boolean) => { if (!open) widgetDialog = undefined }"
    >
      <template #body>
        <WidgetDialog
          v-if="config && widgetDialog"
          :key="widgetDialog.widget?.id ?? 'new'"
          :fetch-fn="props.fetchFn"
          :widget="widgetDialog.widget"
          :shared="config.scope === 'instance'"
          :position="newPosition"
          :rejection="widgetRejection"
          :submitting="saving"
          @submit="onWidgetSubmit"
          @cancel="widgetDialog = undefined"
        />
      </template>
    </UModal>

    <UModal v-model:open="renaming" :title="t('dashboard.renameTitle')">
      <template #body>
        <form class="flex flex-col gap-4" novalidate data-testid="rename-form" @submit.prevent="submitRename">
          <UFormField :label="t('createDashboard.name')" name="name" :error="rename.error || undefined" required>
            <UInput v-model="rename.name" data-testid="rename-name" class="w-full" autofocus />
          </UFormField>
          <UFormField :label="t('createDashboard.description')" name="description" :help="t('createDashboard.descriptionHelp')">
            <UTextarea v-model="rename.description" data-testid="rename-description" :rows="2" class="w-full" />
          </UFormField>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="renaming = false" />
            <UButton type="submit" data-testid="rename-submit" :loading="saving" :label="t('dashboard.save')" />
          </div>
        </form>
      </template>
    </UModal>

    <UModal
      :open="confirming !== undefined"
      :title="confirming?.kind === 'widget' ? t('dashboard.widget.remove', { title: confirming.widget.title }) : t('dashboard.deleteTitle')"
      :description="
        confirming?.kind === 'widget'
          ? t('dashboard.widget.removeConfirm', { title: confirming.widget.title })
          : t('dashboard.deleteConfirm', { name: config?.name ?? '' })
      "
      @update:open="(open: boolean) => { if (!open) confirming = undefined }"
    >
      <template #footer>
        <div class="flex w-full justify-end gap-2">
          <UButton data-testid="confirm-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="confirming = undefined" />
          <UButton data-testid="confirm-submit" color="error" :label="t('dashboard.delete')" @click="confirmed" />
        </div>
      </template>
    </UModal>
  </main>
</template>
