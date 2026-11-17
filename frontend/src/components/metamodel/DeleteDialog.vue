<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SolutionDecision, WriteOutcome } from '../../api/metamodel'
import type { AffectedSolution } from '../../api/problem'
import CollisionWarning from './CollisionWarning.vue'
import SolutionDecisions from './SolutionDecisions.vue'
import { rejectionMessage } from './messages'

// Confirmation of a deletion in SCR-030 with the version read (design D4 of
// scr-030-metamodel-configuration): on 412 the dialog shows the current state and asks again. On
// 409 `solution-decision-required` it asks per affected active solution and sends the decisions
// with the next confirmation (US-154; design D12 of us-153-open-issues).
const props = defineProps<{
  title: string
  description: string
  warning?: string
  version?: number
  run: (version: number | undefined, decisions: SolutionDecision[]) => Promise<WriteOutcome<void>>
}>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ done: []; stale: [] }>()
const { t } = useI18n()

const failure = ref<string>()
const staleVersion = ref<number>()
const submitting = ref(false)
const affected = ref<AffectedSolution[]>([])
const decisions = ref<Record<string, SolutionDecision['decision'] | undefined>>({})
const decided = computed(() => affected.value.every((solution) => decisions.value[solution.id] !== undefined))

watch(open, (value) => {
  if (value) {
    failure.value = undefined
    staleVersion.value = undefined
    affected.value = []
    decisions.value = {}
  }
})

function chosen(): SolutionDecision[] {
  return affected.value.map((solution) => ({ solutionId: solution.id, decision: decisions.value[solution.id]! }))
}

async function confirm(version: number | undefined) {
  failure.value = undefined
  submitting.value = true
  try {
    const outcome = await props.run(version, chosen())
    if (outcome.ok) {
      open.value = false
      emit('done')
    } else if (outcome.rejection.problemType === 'solution-decision-required') {
      affected.value = outcome.rejection.solutions ?? []
    } else if (outcome.rejection.problemType === 'stale-version') {
      staleVersion.value = outcome.rejection.currentVersion
      // the caller reloads and shows the current state in the slot
      emit('stale')
    } else {
      failure.value = rejectionMessage(outcome.rejection)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="title" :description="description">
    <template #body>
      <div class="flex flex-col gap-4" data-testid="delete-dialog">
        <UAlert v-if="warning" data-testid="delete-warning" color="warning" variant="subtle" :description="warning" />
        <UAlert v-if="failure" data-testid="delete-failure" color="error" variant="subtle" :description="failure" />
        <SolutionDecisions v-if="affected.length > 0" v-model="decisions" :solutions="affected" />
        <CollisionWarning
          v-if="staleVersion !== undefined"
          :overwrite-label="t('metamodel.collision.deleteAnyway')"
          @overwrite="confirm(staleVersion)"
          @discard="open = false"
        >
          <slot name="current" />
        </CollisionWarning>
        <div v-else class="flex justify-end gap-2">
          <UButton data-testid="delete-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton
            data-testid="delete-confirm"
            color="error"
            :loading="submitting"
            :disabled="!decided"
            :label="t('metamodel.delete')"
            @click="confirm(version)"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
