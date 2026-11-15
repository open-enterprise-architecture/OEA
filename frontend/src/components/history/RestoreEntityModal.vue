<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchRestorePreview, restoreEntity, type RestoreField, type RestorePreview, type RestoreResult } from '../../api/history'
import { rateLimitMessage, RequestRejected } from '../../api/problem'
import { fieldLabel, fieldValue } from './fields'

// SCR-027 "Restore entity" (UC-15, US-101, US-102, REQ-101; design D5 of us-101-entity-restore):
// the preview of a restore to the target version: the fields that change from the current value
// to the target value, the fields that are not restored with their reason, the warnings of
// empty mandatory or recommended properties, the notices of UC-15 step 3 and an optional reason
// of at most 1000 characters. "Restore" is disabled while nothing can be restored. The restore
// sends `If-Match` with the current version of the preview; a 412 reloads the preview and says
// that the entity was changed meanwhile. Cancel sends nothing.
const props = defineProps<{
  open: boolean
  entityId: number
  targetVersion: number
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{ 'update:open': [open: boolean]; restored: [result: RestoreResult] }>()
const { t } = useI18n()

const REASON_MAX_LENGTH = 1000

const preview = ref<RestorePreview>()
const loading = ref(false)
const restoring = ref(false)
const failure = ref<string>()
const changedMeanwhile = ref(false)
const reason = ref('')

const restored = computed(() => preview.value?.fields.filter((field) => field.restore) ?? [])
const notRestored = computed(() => preview.value?.fields.filter((field) => !field.restore) ?? [])
const canRestore = computed(() => restored.value.length > 0 && !loading.value && !restoring.value)

function message(error: unknown): string {
  return rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('restore.loadFailed')
}

async function load() {
  loading.value = true
  failure.value = undefined
  try {
    preview.value = await fetchRestorePreview(props.entityId, props.targetVersion, props.fetchFn)
  } catch (error) {
    preview.value = undefined
    failure.value = message(error)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.targetVersion] as const,
  ([open]) => {
    if (!open) return
    reason.value = ''
    changedMeanwhile.value = false
    void load()
  },
  { immediate: true },
)

async function confirm() {
  if (!preview.value || !canRestore.value) return
  restoring.value = true
  failure.value = undefined
  try {
    const outcome = await restoreEntity(
      props.entityId,
      { targetVersion: props.targetVersion, currentVersion: preview.value.currentVersion, reason: reason.value },
      props.fetchFn,
    )
    if (outcome.ok) {
      emit('restored', outcome.value)
      emit('update:open', false)
      return
    }
    if (outcome.rejection.status === 412) {
      changedMeanwhile.value = true
      await load()
    } else if (outcome.rejection.problemType === 'nothing-to-restore') {
      failure.value = t('restore.nothingToRestore')
      await load()
    } else {
      failure.value = rateLimitMessage(outcome.rejection) ?? t('restore.failed')
    }
  } finally {
    restoring.value = false
  }
}

function reasonText(field: RestoreField): string {
  return t(`restore.reasons.${field.reason ?? 'write-protected'}`)
}

function warningLabel(field: string): string {
  // `properties[<name>]` of the batch issues
  const match = /^properties\[(.+)\]$/.exec(field)
  return match ? match[1]! : field
}
</script>

<template>
  <UModal
    :open="open"
    :title="t('restore.title', { version: targetVersion })"
    :description="t('restore.intro')"
    @update:open="(value: boolean) => emit('update:open', value)"
  >
    <template #body>
      <div class="flex flex-col gap-4" data-testid="restore-modal">
        <UAlert
          v-if="changedMeanwhile"
          data-testid="restore-changed-meanwhile"
          color="warning"
          variant="subtle"
          icon="i-lucide-refresh-cw"
          :description="t('restore.changedMeanwhile')"
        />
        <UAlert v-if="failure" data-testid="restore-failure" color="error" variant="subtle" :description="failure" />
        <p v-if="loading && !preview" data-testid="restore-loading" class="text-sm text-muted">{{ t('restore.loading') }}</p>

        <template v-if="preview">
          <section class="flex flex-col gap-2">
            <h3 class="text-sm font-semibold text-highlighted">{{ t('restore.changes', { current: preview.currentVersion, target: preview.targetVersion }) }}</h3>
            <p v-if="restored.length === 0" data-testid="restore-nothing" class="text-sm text-muted italic">{{ t('restore.nothing') }}</p>
            <ul v-else class="flex flex-col gap-1.5 text-sm" data-testid="restore-fields">
              <li
                v-for="field in restored"
                :key="field.field"
                :data-testid="`restore-field-${field.field}`"
                class="grid grid-cols-1 gap-x-3 sm:grid-cols-[12rem_1fr]"
              >
                <span class="font-medium text-highlighted">{{ fieldLabel(field, t) }}</span>
                <span v-if="field.redacted">
                  <UBadge data-testid="restore-restricted" color="neutral" variant="outline" icon="i-lucide-lock" :label="t('history.restricted')" />
                </span>
                <span v-else class="flex flex-wrap items-baseline gap-2">
                  <del data-testid="restore-current" class="text-error line-through">
                    <span class="sr-only">{{ t('restore.current') }}: </span>{{ fieldValue(field.currentValue, field.dataType, field.field, t) }}
                  </del>
                  <span aria-hidden="true" class="text-muted">→</span>
                  <ins data-testid="restore-target" class="text-success no-underline">
                    <span class="sr-only">{{ t('restore.after') }}: </span>{{ fieldValue(field.targetValue, field.dataType, field.field, t) }}
                  </ins>
                </span>
              </li>
            </ul>
          </section>

          <section v-if="notRestored.length > 0" class="flex flex-col gap-2" data-testid="restore-not-restored">
            <h3 class="text-sm font-semibold text-highlighted">{{ t('restore.notRestored') }}</h3>
            <ul class="flex flex-col gap-1.5 text-sm">
              <li
                v-for="field in notRestored"
                :key="field.field"
                :data-testid="`restore-kept-${field.field}`"
                class="grid grid-cols-1 gap-x-3 sm:grid-cols-[12rem_1fr]"
              >
                <span class="font-medium text-highlighted">{{ fieldLabel(field, t) }}</span>
                <span class="text-muted">{{ reasonText(field) }}</span>
              </li>
            </ul>
          </section>

          <UAlert
            v-if="preview.warnings.length > 0"
            data-testid="restore-warnings"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            :title="t('restore.warnings')"
          >
            <template #description>
              <ul class="list-disc ps-4">
                <li v-for="warning in preview.warnings" :key="warning.field" data-testid="restore-warning">
                  {{ t(warning.code === 'MANDATORY_MISSING' ? 'restore.mandatoryMissing' : 'restore.valueMissing', { property: warningLabel(warning.field) }) }}
                </li>
              </ul>
            </template>
          </UAlert>

          <ul class="flex flex-col gap-1 text-sm text-muted" data-testid="restore-notices">
            <li class="flex gap-2"><UIcon name="i-lucide-history" class="mt-0.5 shrink-0" />{{ t('restore.noticeHistory') }}</li>
            <li class="flex gap-2"><UIcon name="i-lucide-lock" class="mt-0.5 shrink-0" />{{ t('restore.noticeImmutable') }}</li>
          </ul>

          <UFormField :label="t('restore.reason')" :hint="t('restore.optional')" :help="t('restore.reasonCount', { count: reason.length, max: REASON_MAX_LENGTH })">
            <UTextarea v-model="reason" data-testid="restore-reason" class="w-full" :maxlength="REASON_MAX_LENGTH" :rows="2" :placeholder="t('restore.reasonPlaceholder')" />
          </UFormField>
        </template>

        <div class="flex justify-end gap-2">
          <UButton data-testid="restore-cancel" color="neutral" variant="outline" :label="t('restore.cancel')" @click="emit('update:open', false)" />
          <UButton
            data-testid="restore-confirm"
            icon="i-lucide-rotate-ccw"
            :label="t('restore.confirm')"
            :disabled="!canRestore"
            :loading="restoring"
            @click="confirm"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
