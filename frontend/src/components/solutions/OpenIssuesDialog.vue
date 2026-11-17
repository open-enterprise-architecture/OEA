<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage } from '../../api/problem'
import { resolveOpenIssues, type OpenIssueResolution, type SolutionOpenIssue } from '../../api/solutions'

// SCR-064 "Resolve open issues" (US-153 AC2, AC3, US-154 AC4; design D7 of us-153-open-issues):
// the open issues of a solution, grouped by entity. Per issue a new value, or "Remove value" where
// removing resolves it (an invalid value of an optional property, or the kept value of a property
// no longer in the metamodel). "Apply" sends all at once and is enabled once every issue has an
// entry; a rejection names the issues it refused.
const props = defineProps<{ solutionId: string; issues: SolutionOpenIssue[]; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ resolved: [issues: SolutionOpenIssue[]]; cancel: [] }>()
const { t } = useI18n()

// a number input may hand over a number; the contract takes the text form
const values = reactive<Record<string, string | number | undefined>>({})
const text = (issue: SolutionOpenIssue) => String(values[issue.id] ?? '').trim()
const removals = reactive<Record<string, boolean>>({})
const rejected = ref<string[]>([])
const busy = ref(false)
const failure = ref<string>()

const groups = computed(() => {
  const byEntity = new Map<number, { entityName: string; issues: SolutionOpenIssue[] }>()
  for (const issue of props.issues) {
    const group = byEntity.get(issue.entityId) ?? { entityName: issue.entityName, issues: [] }
    group.issues.push(issue)
    byEntity.set(issue.entityId, group)
  }
  return [...byEntity.values()]
})

const entered = (issue: SolutionOpenIssue) => removals[issue.id] === true || text(issue) !== ''
const complete = computed(() => props.issues.length > 0 && props.issues.every(entered))

/** The input type for the data type of the property; enumerations and references take their text. */
function inputType(issue: SolutionOpenIssue): string {
  switch (issue.dataType) {
    case 'integer':
      return 'number'
    case 'date':
      return 'date'
    default:
      return 'text'
  }
}

async function apply() {
  if (!complete.value) return
  busy.value = true
  failure.value = undefined
  rejected.value = []
  try {
    const resolutions: OpenIssueResolution[] = props.issues.map((issue) =>
      removals[issue.id] ? { issueId: issue.id, remove: true } : { issueId: issue.id, value: text(issue), remove: false },
    )
    const outcome = await resolveOpenIssues(props.solutionId, resolutions, props.fetchFn)
    if (outcome.ok) {
      emit('resolved', outcome.value)
      return
    }
    const rejection = outcome.rejection
    if (rejection.status === 422) {
      rejected.value = rejection.invalidFields
      failure.value = t('solutions.openIssues.invalid')
    } else if (rejection.status === 403) {
      failure.value = t('solutions.openIssues.forbidden')
    } else {
      failure.value = rateLimitMessage(rejection) ?? t('solutions.openIssues.failed')
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-4" data-testid="open-issues-dialog">
    <p class="text-sm text-muted">{{ t('solutions.openIssues.intro') }}</p>
    <UAlert v-if="failure" data-testid="open-issues-failure" color="error" variant="subtle" :description="failure" />
    <section v-for="group in groups" :key="group.entityName" class="flex flex-col gap-3">
      <h3 class="font-semibold text-highlighted">{{ group.entityName }}</h3>
      <fieldset
        v-for="issue in group.issues"
        :key="issue.id"
        :data-testid="`open-issue-entry-${issue.id}`"
        class="flex flex-col gap-2 rounded-md border p-3"
        :class="rejected.includes(issue.id) ? 'border-error' : 'border-default'"
      >
        <legend class="px-1 text-sm font-medium">{{ issue.label }}</legend>
        <p class="text-xs text-muted">
          {{ t(`solutions.goLive.openIssues.kind.${issue.kind}`) }} ·
          <template v-if="issue.redacted">{{ t('history.restricted') }}</template>
          <template v-else-if="issue.kind === 'not-in-metamodel'">{{ t('solutions.openIssues.kept', { value: issue.keptValue }) }}</template>
          <template v-else>{{ t('solutions.openIssues.current', { value: issue.currentValue ?? t('solutions.openIssues.empty') }) }}</template>
        </p>
        <UInput
          v-model="values[issue.id]"
          :data-testid="`open-issue-value-${issue.id}`"
          :type="inputType(issue)"
          :disabled="removals[issue.id] === true"
          :aria-label="t('solutions.openIssues.valueLabel', { field: issue.label, entity: issue.entityName })"
          :aria-invalid="rejected.includes(issue.id)"
          class="w-full"
        />
        <UCheckbox
          v-if="issue.removable"
          v-model="removals[issue.id]"
          :data-testid="`open-issue-remove-${issue.id}`"
          :label="issue.kind === 'not-in-metamodel' ? t('solutions.openIssues.discard') : t('solutions.openIssues.remove')"
        />
        <p v-if="rejected.includes(issue.id)" class="text-xs text-error" :data-testid="`open-issue-error-${issue.id}`">
          {{ t('solutions.openIssues.rejected') }}
        </p>
      </fieldset>
    </section>
    <div class="flex justify-end gap-2">
      <UButton data-testid="open-issues-cancel" color="neutral" variant="outline" :label="t('solutions.goLive.cancel')" @click="emit('cancel')" />
      <UButton data-testid="open-issues-apply" :label="t('solutions.openIssues.apply')" :disabled="!complete" :loading="busy" @click="apply" />
    </div>
  </div>
</template>
