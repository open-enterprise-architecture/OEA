<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SolutionDecision } from '../../api/metamodel'
import type { AffectedSolution } from '../../api/problem'

// The question of a metamodel change per affected active solution (US-154; design D12 of
// us-153-open-issues): "Apply to this solution" removes the values from its deltas, "Keep as
// snapshot" keeps the property in the solution until its go-live. The parent sends the change
// again with the decisions once every solution has one.
const props = defineProps<{ solutions: AffectedSolution[] }>()
const decisions = defineModel<Record<string, SolutionDecision['decision'] | undefined>>({ required: true })
const { t } = useI18n()

const items = computed(() => [
  { value: 'apply', label: t('metamodel.solutionDecisions.apply') },
  { value: 'keep', label: t('metamodel.solutionDecisions.keep') },
])

function affected(solution: AffectedSolution): string {
  const properties = [...new Set(solution.properties.map((entry) => (entry.enumValue ? `${entry.label}: ${entry.enumValue}` : entry.label)))]
  return t('metamodel.solutionDecisions.affected', {
    properties: properties.join(', '),
    values: solution.deltaValues,
    metatypes: solution.extensionMetatypes,
  })
}
</script>

<template>
  <section class="flex flex-col gap-3" data-testid="solution-decisions">
    <UAlert color="warning" variant="subtle" :title="t('metamodel.solutionDecisions.title')" :description="t('metamodel.solutionDecisions.intro')" />
    <fieldset
      v-for="solution in props.solutions"
      :key="solution.id"
      :data-testid="`solution-decision-${solution.id}`"
      class="flex flex-col gap-2 rounded-md border border-default p-3"
    >
      <legend class="px-1 text-sm font-medium">{{ solution.name }}</legend>
      <p class="text-xs text-muted">{{ affected(solution) }}</p>
      <URadioGroup v-model="decisions[solution.id]" :items="items" :aria-label="t('metamodel.solutionDecisions.label', { name: solution.name })" />
    </fieldset>
  </section>
</template>
