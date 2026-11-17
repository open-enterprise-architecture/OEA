<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AffectedSolution } from '../../api/problem'
import { decisionKey, type Decisions } from './decisions'

// The question of a metamodel change per affected active solution (US-154; design D12 of
// us-153-open-issues): "Apply" removes the values from the deltas, "Keep as snapshot" keeps the
// property until the go-live. The solutions that target a plateau are asked once per plateau,
// naming them (US-156; design D6 of us-084-plateaus). The parent sends the change again with the
// decisions once every group has one.
const props = defineProps<{ solutions: AffectedSolution[] }>()
const decisions = defineModel<Decisions>({ required: true })
const { t } = useI18n()

const items = computed(() => [
  { value: 'apply', label: t('metamodel.solutionDecisions.apply') },
  { value: 'keep', label: t('metamodel.solutionDecisions.keep') },
])

/** One question per plateau, then one per solution without a plateau, in the order of the answer. */
const groups = computed(() => {
  const byKey = new Map<string, { key: string; name: string; plateau: boolean; solutions: AffectedSolution[] }>()
  for (const solution of props.solutions) {
    const key = decisionKey(solution)
    const group = byKey.get(key) ?? {
      key,
      name: solution.plateau ? solution.plateau.name : solution.name,
      plateau: Boolean(solution.plateau),
      solutions: [],
    }
    group.solutions.push(solution)
    byKey.set(key, group)
  }
  return [...byKey.values()].sort((a, b) => Number(b.plateau) - Number(a.plateau))
})

function affected(solutions: AffectedSolution[]): string {
  const properties = [
    ...new Set(
      solutions.flatMap((solution) => solution.properties.map((entry) => (entry.enumValue ? `${entry.label}: ${entry.enumValue}` : entry.label))),
    ),
  ]
  return t('metamodel.solutionDecisions.affected', {
    properties: properties.join(', '),
    values: solutions.reduce((sum, solution) => sum + solution.deltaValues, 0),
    metatypes: solutions.reduce((sum, solution) => sum + solution.extensionMetatypes, 0),
  })
}
</script>

<template>
  <section class="flex flex-col gap-3" data-testid="solution-decisions">
    <UAlert color="warning" variant="subtle" :title="t('metamodel.solutionDecisions.title')" :description="t('metamodel.solutionDecisions.intro')" />
    <fieldset
      v-for="group in groups"
      :key="group.key"
      :data-testid="`solution-decision-${group.key}`"
      class="flex flex-col gap-2 rounded-md border border-default p-3"
    >
      <legend class="px-1 text-sm font-medium">
        {{ group.plateau ? t('metamodel.solutionDecisions.plateau', { name: group.name }) : group.name }}
      </legend>
      <p v-if="group.plateau" class="text-sm" data-testid="solution-decision-members">
        {{ t('metamodel.solutionDecisions.members', { names: group.solutions.map((solution) => solution.name).join(', ') }) }}
      </p>
      <p class="text-xs text-muted">{{ affected(group.solutions) }}</p>
      <URadioGroup
        v-model="decisions[group.key]"
        :items="items"
        :aria-label="t('metamodel.solutionDecisions.label', { name: group.name })"
      />
    </fieldset>
  </section>
</template>
