<script setup lang="ts">
import type { SolutionDeltaConflict } from '../../api/solutions'

// The conflicts of a delta with other active solutions (US-044, REQ-042; design D4 of
// us-044-conflict-warning), on the delta list of SCR-061 and the cards of SCR-062: per conflict a
// warning icon and "Also changed in '{solution}' (owner: {owner})", the name linking to that
// solution. The icon is decorative; the text carries the warning.
const props = defineProps<{ conflicts: SolutionDeltaConflict[] }>()
</script>

<template>
  <ul v-if="props.conflicts.length > 0" data-testid="delta-conflicts" class="flex flex-col gap-1 text-sm">
    <li v-for="conflict in props.conflicts" :key="conflict.solutionId" data-testid="delta-conflict" class="flex items-start gap-1">
      <UIcon name="i-lucide-triangle-alert" class="mt-0.5 shrink-0 text-warning" aria-hidden="true" />
      <i18n-t keypath="solutions.conflicts.entry" tag="span" scope="global">
        <template #solution>
          <RouterLink
            :to="`/solutions/${encodeURIComponent(conflict.solutionId)}`"
            data-testid="delta-conflict-link"
            class="font-medium text-highlighted underline"
            >{{ conflict.solutionName }}</RouterLink
          >
        </template>
        <template #owner>{{ conflict.ownerName }}</template>
      </i18n-t>
    </li>
  </ul>
</template>
