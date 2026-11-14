<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { FieldDiff } from '../../api/history'
import { fieldLabel, fieldValue } from './fields'

// The changed fields of a version or of a comparison (SCR-026; US-099; design D5 of
// us-099-version-diff): the old value in red and struck through, the new value in green, a
// restricted field with the badge "Restricted" and no values. Colors are never the only signal:
// the old value is struck through, and both carry "before"/"after" for screen readers.
defineProps<{ changes: FieldDiff[] }>()
const { t } = useI18n()
</script>

<template>
  <p v-if="changes.length === 0" data-testid="history-diff-empty" class="text-sm text-muted italic">{{ t('history.noDifferences') }}</p>
  <ul v-else class="flex flex-col gap-1.5 text-sm" data-testid="history-diff">
    <li
      v-for="change in changes"
      :key="change.field"
      :data-testid="`history-diff-${change.field}`"
      class="grid grid-cols-1 gap-x-3 sm:grid-cols-[12rem_1fr]"
    >
      <span class="font-medium text-highlighted">{{ fieldLabel(change, t) }}</span>
      <span v-if="change.redacted">
        <UBadge data-testid="history-diff-restricted" color="neutral" variant="outline" icon="i-lucide-lock" :label="t('history.restricted')" />
      </span>
      <span v-else class="flex flex-wrap items-baseline gap-2">
        <del data-testid="history-diff-old" class="text-error line-through">
          <span class="sr-only">{{ t('history.before') }}: </span>{{ fieldValue(change.oldValue, change.dataType, change.field, t) }}
        </del>
        <span aria-hidden="true" class="text-muted">→</span>
        <ins data-testid="history-diff-new" class="text-success no-underline">
          <span class="sr-only">{{ t('history.after') }}: </span>{{ fieldValue(change.newValue, change.dataType, change.field, t) }}
        </ins>
      </span>
    </li>
  </ul>
</template>
