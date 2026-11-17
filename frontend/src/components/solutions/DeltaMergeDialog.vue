<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage } from '../../api/problem'
import { resolveDeltaConflicts, type DeltaConflict, type DeltaConflictChoice } from '../../api/solutions'
import { fieldValue } from '../history/fields'

// SCR-063 "Resolve conflicts" (US-152 AC2; design D6 of us-152-solution-go-live): the field
// conflicts of a go-live, grouped by entity. A conflict is a field the delta changes that was also
// changed in the catalog after the capture. Per field, the architect takes the current value, the
// delta value or an own value; "Apply" sends one resolution per delta and is enabled once every
// conflict has a choice. Redacted values show "Restricted" and offer only current and delta.
// A value that only a snapshot of the solution keeps (`not-in-metamodel`, US-154 AC4) offers
// "Discard value" or "Resolve later"; it needs no choice, and "Resolve later" sends nothing.
const props = defineProps<{ solutionId: string; conflicts: DeltaConflict[]; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ resolved: []; cancel: [] }>()
const { t } = useI18n()

type Choice = DeltaConflictChoice['choice'] | 'later'
const key = (conflict: DeltaConflict) => `${conflict.deltaId}:${conflict.field}`
const choices = reactive<Record<string, Choice | undefined>>({})
const values = reactive<Record<string, string>>({})
const busy = ref(false)
const failure = ref<string>()

const groups = computed(() => {
  const byEntity = new Map<string, { entityName: string; conflicts: DeltaConflict[] }>()
  for (const conflict of props.conflicts) {
    // a delta new has no entity yet; its delta stands for it
    const entity = conflict.entityId !== null ? String(conflict.entityId) : conflict.deltaId
    const group = byEntity.get(entity) ?? { entityName: conflict.entityName, conflicts: [] }
    group.conflicts.push(conflict)
    byEntity.set(entity, group)
  }
  return [...byEntity.values()]
})
const isSnapshot = (conflict: DeltaConflict) => conflict.kind === 'not-in-metamodel'
const complete = computed(() =>
  props.conflicts.every((conflict) => isSnapshot(conflict) || choices[key(conflict)] !== undefined),
)

function itemsOf(conflict: DeltaConflict) {
  if (isSnapshot(conflict)) {
    return [
      { value: 'discard', label: t('solutions.goLive.merge.discard') },
      { value: 'later', label: t('solutions.goLive.merge.later') },
    ]
  }
  return [
    {
      value: 'current',
      label: conflict.redacted
        ? t('solutions.goLive.merge.currentHidden')
        : t('solutions.goLive.merge.current', { value: fieldValue(conflict.current, conflict.dataType, conflict.field, t) }),
    },
    {
      value: 'delta',
      label: conflict.redacted
        ? t('solutions.goLive.merge.deltaHidden')
        : t('solutions.goLive.merge.delta', { value: fieldValue(conflict.after, conflict.dataType, conflict.field, t) }),
    },
    ...(conflict.redacted ? [] : [{ value: 'value', label: t('solutions.goLive.merge.own') }]),
  ]
}

async function apply() {
  if (!complete.value) return
  busy.value = true
  failure.value = undefined
  try {
    const byDelta = new Map<string, DeltaConflictChoice[]>()
    for (const conflict of props.conflicts) {
      const choice = choices[key(conflict)]
      if (choice === undefined || choice === 'later') {
        continue
      }
      const entries = byDelta.get(conflict.deltaId) ?? []
      entries.push({ field: conflict.field, choice, ...(choice === 'value' ? { value: values[key(conflict)] ?? '' } : {}) })
      byDelta.set(conflict.deltaId, entries)
    }
    for (const [deltaId, fields] of byDelta) {
      const outcome = await resolveDeltaConflicts(props.solutionId, deltaId, fields, props.fetchFn)
      if (!outcome.ok) {
        failure.value =
          rateLimitMessage(outcome.rejection) ??
          (outcome.rejection.status === 422 ? t('solutions.goLive.merge.invalid') : t('solutions.goLive.merge.failed'))
        return
      }
    }
    emit('resolved')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-4" data-testid="merge-dialog">
    <p class="text-sm text-muted">{{ t('solutions.goLive.merge.intro') }}</p>
    <UAlert v-if="failure" data-testid="merge-failure" color="error" variant="subtle" :description="failure" />
    <section v-for="group in groups" :key="group.entityName" class="flex flex-col gap-3">
      <h3 class="font-semibold text-highlighted">{{ group.entityName }}</h3>
      <fieldset
        v-for="conflict in group.conflicts"
        :key="key(conflict)"
        :data-testid="`merge-conflict-${conflict.field}`"
        class="flex flex-col gap-2 rounded-md border border-default p-3"
      >
        <legend class="px-1 text-sm font-medium">{{ conflict.label }}</legend>
        <p class="text-xs text-muted">
          <template v-if="conflict.redacted">{{ t('history.restricted') }}</template>
          <template v-else-if="isSnapshot(conflict)">{{
            t('solutions.goLive.merge.notInMetamodel', { value: fieldValue(conflict.after, conflict.dataType, conflict.field, t) })
          }}</template>
          <template v-else>{{ t('solutions.goLive.merge.before', { value: fieldValue(conflict.before, conflict.dataType, conflict.field, t) }) }}</template>
        </p>
        <URadioGroup v-model="choices[key(conflict)]" :data-testid="`merge-choice-${conflict.field}`" :items="itemsOf(conflict)" />
        <UInput
          v-if="choices[key(conflict)] === 'value'"
          v-model="values[key(conflict)]"
          :data-testid="`merge-value-${conflict.field}`"
          :aria-label="t('solutions.goLive.merge.ownLabel', { field: conflict.label })"
          class="w-full"
        />
      </fieldset>
    </section>
    <div class="flex justify-end gap-2">
      <UButton data-testid="merge-cancel" color="neutral" variant="outline" :label="t('solutions.goLive.cancel')" @click="emit('cancel')" />
      <UButton data-testid="merge-apply" :label="t('solutions.goLive.merge.apply')" :disabled="!complete" :loading="busy" @click="apply" />
    </div>
  </div>
</template>
