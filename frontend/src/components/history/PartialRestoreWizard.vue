<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  fetchEntityVersion,
  fetchRestorePreview,
  restoreEntity,
  type EntityVersion,
  type EntityVersionField,
  type RestoreField,
  type RestorePreview,
  type RestoreResult,
} from '../../api/history'
import { rateLimitMessage, RequestRejected } from '../../api/problem'
import { currentLanguage } from '../../i18n'
import { fieldLabel, fieldValue, warningLabel } from './fields'

// SCR-029 "Partially restore" (UC-16, US-104 to US-106, REQ-104 to REQ-106; design D6 of
// us-104-partial-restore): a wizard with the steps General, Properties, Connections and Summary.
// The source version comes from the version the wizard was opened on and can be changed; a change
// reloads the preview and clears the selection. Only fields that differ and can be restored are
// selectable: an identical field is grayed out, a not restorable one shows its reason, a redacted
// one shows "Restricted". A step without differences is skipped with a note; every step offers
// "Skip". Connections are informational and link to their own history. "Partially restore"
// sends the selected keys as `fields` with `If-Match`; a 412 reloads the preview.
const props = defineProps<{
  open: boolean
  entityId: number
  sourceVersion: number
  /** The versions of the history, newest first; every one but the current one can be the source. */
  versions: EntityVersion[]
  currentVersion: number
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{ 'update:open': [open: boolean]; restored: [result: RestoreResult] }>()
const { t } = useI18n()

type StepKey = 'general' | 'properties' | 'connections' | 'summary'
const STEPS: StepKey[] = ['general', 'properties', 'connections', 'summary']
const GENERAL = ['name', 'description', 'isLogical']
const REASON_MAX_LENGTH = 1000

/** A row of the steps General and Properties: a differing field of the preview, or an identical one. */
interface Row {
  field: string
  label: string
  isSystemField: boolean
  dataType: RestoreField['dataType']
  sourceValue: string | null
  currentValue: string | null
  redacted: boolean
  identical: boolean
  restore: boolean
  reason: RestoreField['reason']
}

const source = ref(props.sourceVersion)
const preview = ref<RestorePreview>()
const state = ref<EntityVersionField[]>([])
const selected = ref<string[]>([])
const step = ref<StepKey>('general')
const skipped = ref<StepKey[]>([])
const loading = ref(false)
const restoring = ref(false)
const failure = ref<string>()
const changedMeanwhile = ref(false)
const reason = ref('')

const sourceItems = computed(() =>
  props.versions
    .filter((version) => version.version !== props.currentVersion)
    .map((version) => ({ label: t('partialRestore.sourceItem', { version: version.version, time: time(version.changedAt), person: version.changedBy.name }), value: version.version })),
)
const sourceInfo = computed(() => props.versions.find((version) => version.version === source.value))

const rows = computed<Row[]>(() => {
  if (!preview.value) return []
  const differing = new Map(preview.value.fields.map((field) => [field.field, field]))
  const result: Row[] = []
  const seen = new Set<string>()
  for (const field of state.value) {
    if (['metatypeId', 'sourceId', 'targetId'].includes(field.field)) continue
    seen.add(field.field)
    const diff = differing.get(field.field)
    result.push(
      diff
        ? row(diff)
        : {
            field: field.field,
            label: field.label,
            isSystemField: field.isSystemField,
            dataType: field.dataType,
            sourceValue: field.value,
            currentValue: field.value,
            redacted: field.restricted,
            identical: true,
            restore: false,
            reason: null,
          },
    )
  }
  // differing fields the source state does not list, e.g. properties mapped after it
  for (const diff of preview.value.fields) {
    if (!seen.has(diff.field)) result.push(row(diff))
  }
  return result
})

function row(field: RestoreField): Row {
  return {
    field: field.field,
    label: field.label,
    isSystemField: field.isSystemField,
    dataType: field.dataType,
    sourceValue: field.targetValue,
    currentValue: field.currentValue,
    redacted: field.redacted,
    identical: false,
    restore: field.restore,
    reason: field.reason,
  }
}

const generalRows = computed(() => rows.value.filter((candidate) => GENERAL.includes(candidate.field)))
const propertyRows = computed(() => rows.value.filter((candidate) => !GENERAL.includes(candidate.field)))
const differs = (list: Row[]) => list.some((candidate) => !candidate.identical)
const stepEmpty = computed<Record<StepKey, boolean>>(() => ({
  general: !differs(generalRows.value),
  properties: !differs(propertyRows.value),
  connections: (preview.value?.connections.length ?? 0) === 0,
  summary: false,
}))
const selectedRows = computed(() => rows.value.filter((candidate) => selected.value.includes(candidate.field)))
const selectedWarnings = computed(() => (preview.value?.warnings ?? []).filter((warning) => selected.value.includes(warning.field)))
const canRestore = computed(() => selected.value.length > 0 && !loading.value && !restoring.value)

function time(value: string): string {
  return new Intl.DateTimeFormat(currentLanguage(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function message(error: unknown): string {
  return rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('restore.loadFailed')
}

/** Moves to the first step from `from` on that has differences; the others are noted as skipped. */
function advance(from: number) {
  for (let index = from; index < STEPS.length; index++) {
    const key = STEPS[index]!
    if (!stepEmpty.value[key]) {
      step.value = key
      return
    }
    if (!skipped.value.includes(key)) skipped.value = [...skipped.value, key]
  }
}

async function load(keepSelection = false) {
  loading.value = true
  failure.value = undefined
  try {
    const [loaded, stateOfSource] = await Promise.all([
      fetchRestorePreview(props.entityId, source.value, props.fetchFn),
      fetchEntityVersion(props.entityId, source.value, props.fetchFn),
    ])
    preview.value = loaded
    state.value = stateOfSource.fields
    const restorable = new Set(loaded.fields.filter((field) => field.restore).map((field) => field.field))
    selected.value = keepSelection ? selected.value.filter((key) => restorable.has(key)) : []
    if (!keepSelection) {
      skipped.value = []
      advance(0)
    }
  } catch (error) {
    preview.value = undefined
    failure.value = message(error)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    source.value = props.sourceVersion
    reason.value = ''
    changedMeanwhile.value = false
    void load()
  },
  { immediate: true },
)

watch(source, (next, previous) => {
  if (props.open && previous !== undefined && next !== previous) void load()
})

function toggle(key: string, checked: boolean) {
  selected.value = checked ? [...selected.value.filter((candidate) => candidate !== key), key] : selected.value.filter((candidate) => candidate !== key)
}

function next() {
  advance(STEPS.indexOf(step.value) + 1)
}

/** Skips the step: its fields contribute nothing to the restore (UC-16 A1). */
function skip() {
  const keys = new Set((step.value === 'general' ? generalRows.value : step.value === 'properties' ? propertyRows.value : []).map((item) => item.field))
  selected.value = selected.value.filter((key) => !keys.has(key))
  next()
}

function back() {
  for (let index = STEPS.indexOf(step.value) - 1; index >= 0; index--) {
    const key = STEPS[index]!
    if (!stepEmpty.value[key]) {
      step.value = key
      return
    }
  }
}

const canGoBack = computed(() => STEPS.slice(0, STEPS.indexOf(step.value)).some((key) => !stepEmpty.value[key]))

async function confirm() {
  if (!preview.value || !canRestore.value) return
  restoring.value = true
  failure.value = undefined
  try {
    // the keys in the order of the rows
    const fields = rows.value.filter((candidate) => selected.value.includes(candidate.field)).map((candidate) => candidate.field)
    const outcome = await restoreEntity(
      props.entityId,
      { targetVersion: source.value, currentVersion: preview.value.currentVersion, reason: reason.value, fields },
      props.fetchFn,
    )
    if (outcome.ok) {
      emit('restored', outcome.value)
      emit('update:open', false)
      return
    }
    if (outcome.rejection.status === 412) {
      changedMeanwhile.value = true
      await load(true)
    } else if (outcome.rejection.problemType === 'nothing-to-restore' || outcome.rejection.invalidFields.includes('fields')) {
      failure.value = t('partialRestore.selectionOutdated')
      await load(true)
    } else {
      failure.value = rateLimitMessage(outcome.rejection) ?? t('restore.failed')
    }
  } finally {
    restoring.value = false
  }
}

function reasonText(row: Row): string {
  return t(`restore.reasons.${row.reason ?? 'write-protected'}`)
}
</script>

<template>
  <UModal
    :open="open"
    :title="t('partialRestore.title')"
    :description="t('partialRestore.intro')"
    :ui="{ content: 'sm:max-w-3xl' }"
    @update:open="(value: boolean) => emit('update:open', value)"
  >
    <template #body>
      <div class="flex flex-col gap-4" data-testid="partial-restore">
        <div class="flex flex-wrap items-end gap-3">
          <UFormField :label="t('partialRestore.source')">
            <USelect v-model="source" data-testid="partial-restore-source" :aria-label="t('partialRestore.source')" :items="sourceItems" class="w-80" />
          </UFormField>
          <p v-if="sourceInfo" data-testid="partial-restore-source-info" class="pb-2 text-sm text-muted">
            {{ t('partialRestore.sourceInfo', { version: sourceInfo.version, time: time(sourceInfo.changedAt), person: sourceInfo.changedBy.name }) }}
          </p>
        </div>

        <ol class="flex flex-wrap gap-2 text-sm" :aria-label="t('partialRestore.steps')" data-testid="partial-restore-steps">
          <li
            v-for="(key, index) in STEPS"
            :key="key"
            :data-testid="`partial-restore-step-${key}`"
            :aria-current="step === key ? 'step' : undefined"
            class="rounded-full border px-3 py-1"
            :class="step === key ? 'border-primary bg-primary/10 font-semibold text-highlighted' : 'border-default text-muted'"
          >
            {{ index + 1 }}. {{ t(`partialRestore.step.${key}`) }}
          </li>
        </ol>

        <UAlert
          v-if="changedMeanwhile"
          data-testid="partial-restore-changed-meanwhile"
          color="warning"
          variant="subtle"
          icon="i-lucide-refresh-cw"
          :description="t('restore.changedMeanwhile')"
        />
        <UAlert v-if="failure" data-testid="partial-restore-failure" color="error" variant="subtle" :description="failure" />
        <p v-if="loading && !preview" class="text-sm text-muted">{{ t('restore.loading') }}</p>

        <template v-if="preview">
          <ul v-if="skipped.length > 0 && step !== 'summary'" class="flex flex-col gap-1" data-testid="partial-restore-skipped">
            <li v-for="key in skipped" :key="key" class="text-sm text-muted italic">
              {{ t('partialRestore.noDifferences', { step: t(`partialRestore.step.${key}`) }) }}
            </li>
          </ul>

          <section v-if="step === 'general' || step === 'properties'" :data-testid="`partial-restore-${step}`">
            <table class="w-full text-left text-sm">
              <thead class="text-muted">
                <tr>
                  <th class="py-1 pe-3 font-medium">{{ t('partialRestore.field') }}</th>
                  <th class="py-1 pe-3 font-medium">{{ t('partialRestore.sourceValue', { version: source }) }}</th>
                  <th class="py-1 pe-3 font-medium">{{ t('partialRestore.currentValue') }}</th>
                  <th class="py-1 font-medium">{{ t('partialRestore.restore') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in step === 'general' ? generalRows : propertyRows"
                  :key="item.field"
                  :data-testid="`partial-restore-row-${item.field}`"
                  class="border-t border-default align-top"
                  :class="item.identical ? 'text-muted' : ''"
                >
                  <td class="py-1.5 pe-3 font-medium">{{ fieldLabel(item, t) }}</td>
                  <template v-if="item.redacted">
                    <td colspan="2" class="py-1.5 pe-3">
                      <UBadge color="neutral" variant="outline" icon="i-lucide-lock" :label="t('history.restricted')" />
                    </td>
                  </template>
                  <template v-else-if="item.reason === 'not-in-source'">
                    <td class="py-1.5 pe-3 italic">{{ t('partialRestore.notInSource') }}</td>
                    <td class="py-1.5 pe-3">{{ fieldValue(item.currentValue, item.dataType, item.field, t) }}</td>
                  </template>
                  <template v-else>
                    <td class="py-1.5 pe-3">{{ fieldValue(item.sourceValue, item.dataType, item.field, t) }}</td>
                    <td class="py-1.5 pe-3">{{ fieldValue(item.currentValue, item.dataType, item.field, t) }}</td>
                  </template>
                  <td class="py-1.5">
                    <div class="flex flex-col gap-1">
                      <UCheckbox
                        :data-testid="`partial-restore-select-${item.field}`"
                        :model-value="selected.includes(item.field)"
                        :disabled="item.identical || !item.restore"
                        :aria-label="t('partialRestore.select', { field: fieldLabel(item, t) })"
                        @update:model-value="(checked: boolean | 'indeterminate') => toggle(item.field, checked === true)"
                      />
                      <span v-if="item.identical" class="text-xs">{{ t('partialRestore.identical') }}</span>
                      <span v-else-if="!item.restore && item.reason !== 'not-in-source'" class="text-xs text-muted">{{ reasonText(item) }}</span>
                      <span v-if="selected.includes(item.field) && !item.redacted" :data-testid="`partial-restore-after-${item.field}`" class="text-xs text-success">
                        {{ t('partialRestore.after', { value: fieldValue(item.sourceValue, item.dataType, item.field, t) }) }}
                      </span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </section>

          <section v-else-if="step === 'connections'" data-testid="partial-restore-connections" class="flex flex-col gap-2">
            <p class="text-sm text-muted">{{ t('partialRestore.connectionsIntro') }}</p>
            <ul class="flex flex-col gap-1.5 text-sm">
              <li
                v-for="connection in preview.connections"
                :key="connection.entityId"
                :data-testid="`partial-restore-connection-${connection.entityId}`"
                class="flex flex-wrap items-center gap-2"
              >
                <span class="font-medium text-highlighted">{{ connection.name }}</span>
                <UBadge color="neutral" variant="subtle" :label="t(`partialRestore.change.${connection.change}`)" />
                <span class="text-muted">{{ t(`partialRestore.direction.${connection.direction}`, { other: connection.otherEndpoint }) }}</span>
                <ULink
                  v-if="connection.change === 'changed'"
                  :to="`/entities/${connection.entityId}/history`"
                  :data-testid="`partial-restore-connection-history-${connection.entityId}`"
                  class="ms-auto text-primary"
                >
                  {{ t('partialRestore.openHistory') }}
                </ULink>
              </li>
            </ul>
          </section>

          <section v-else data-testid="partial-restore-summary" class="flex flex-col gap-3">
            <p v-if="selectedRows.length === 0" data-testid="partial-restore-nothing-selected" class="text-sm text-muted italic">
              {{ t('partialRestore.nothingSelected') }}
            </p>
            <ul v-else class="flex flex-col gap-1.5 text-sm" data-testid="partial-restore-selection">
              <li v-for="item in selectedRows" :key="item.field" class="grid grid-cols-1 gap-x-3 sm:grid-cols-[12rem_1fr]">
                <span class="font-medium text-highlighted">{{ fieldLabel(item, t) }}</span>
                <span v-if="item.redacted"><UBadge color="neutral" variant="outline" icon="i-lucide-lock" :label="t('history.restricted')" /></span>
                <span v-else class="flex flex-wrap items-baseline gap-2">
                  <del class="text-error line-through"><span class="sr-only">{{ t('restore.current') }}: </span>{{ fieldValue(item.currentValue, item.dataType, item.field, t) }}</del>
                  <span aria-hidden="true" class="text-muted">→</span>
                  <ins class="text-success no-underline"><span class="sr-only">{{ t('restore.after') }}: </span>{{ fieldValue(item.sourceValue, item.dataType, item.field, t) }}</ins>
                </span>
              </li>
            </ul>
            <UAlert v-if="selectedWarnings.length > 0" data-testid="partial-restore-warnings" color="warning" variant="subtle" icon="i-lucide-triangle-alert" :title="t('restore.warnings')">
              <template #description>
                <ul class="list-disc ps-4">
                  <li v-for="warning in selectedWarnings" :key="warning.field">
                    {{ t(warning.code === 'MANDATORY_MISSING' ? 'restore.mandatoryMissing' : 'restore.valueMissing', { property: warningLabel(warning, preview.fields, t) }) }}
                  </li>
                </ul>
              </template>
            </UAlert>
            <p class="flex gap-2 text-sm text-muted"><UIcon name="i-lucide-info" class="mt-0.5 shrink-0" />{{ t('partialRestore.othersUnchanged') }}</p>
            <UFormField :label="t('restore.reason')" :hint="t('restore.optional')" :help="t('restore.reasonCount', { count: reason.length, max: REASON_MAX_LENGTH })">
              <UTextarea v-model="reason" data-testid="partial-restore-reason" class="w-full" :maxlength="REASON_MAX_LENGTH" :rows="2" :placeholder="t('restore.reasonPlaceholder')" />
            </UFormField>
          </section>
        </template>

        <div class="flex flex-wrap justify-end gap-2">
          <UButton data-testid="partial-restore-cancel" class="me-auto" color="neutral" variant="ghost" :label="t('restore.cancel')" @click="emit('update:open', false)" />
          <UButton v-if="canGoBack" data-testid="partial-restore-back" color="neutral" variant="outline" :label="t('partialRestore.back')" @click="back" />
          <template v-if="step !== 'summary'">
            <UButton data-testid="partial-restore-skip" color="neutral" variant="outline" :label="t('partialRestore.skip')" :disabled="!preview" @click="skip" />
            <UButton data-testid="partial-restore-next" :label="t('partialRestore.next')" :disabled="!preview" @click="next" />
          </template>
          <UButton
            v-else
            data-testid="partial-restore-confirm"
            icon="i-lucide-rotate-ccw"
            :label="t('partialRestore.confirm')"
            :disabled="!canRestore"
            :loading="restoring"
            @click="confirm"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
