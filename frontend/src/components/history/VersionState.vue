<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { EntityVersionState } from '../../api/history'
import { fieldLabel, fieldValue } from './fields'

// The complete state of a version (SCR-026; US-100; design D5 of us-099-version-diff): every
// field with its value, "—" for an empty property, and "Restricted" without value for a field
// the reader may not read.
defineProps<{ state: EntityVersionState }>()
const { t } = useI18n()
</script>

<template>
  <section class="flex flex-col gap-2 rounded-md bg-elevated p-3" :data-testid="`history-state-${state.version}`">
    <h3 class="text-sm font-semibold text-highlighted">{{ t('history.stateTitle', { version: state.version }) }}</h3>
    <dl class="grid grid-cols-1 gap-x-3 gap-y-1 text-sm sm:grid-cols-[12rem_1fr]">
      <template v-for="field in state.fields" :key="field.field">
        <dt class="font-medium text-toned">{{ fieldLabel(field, t) }}</dt>
        <dd :data-testid="`history-state-${field.field}`">
          <UBadge v-if="field.restricted" color="neutral" variant="outline" icon="i-lucide-lock" :label="t('history.restricted')" />
          <template v-else>{{ fieldValue(field.value, field.dataType, field.field, t) }}</template>
        </dd>
      </template>
    </dl>
  </section>
</template>
