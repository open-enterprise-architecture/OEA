<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Viewpoint } from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'

// The name, notation, description and allowed types of one viewpoint in the list of SCR-031
// (US-090, US-091). Names and descriptions are shown as text only.
defineProps<{ viewpoint: Viewpoint }>()
const { t } = useI18n()
</script>

<template>
  <span class="flex min-w-0 flex-1 flex-col gap-1">
    <span class="flex flex-wrap items-center gap-2">
      <span class="font-medium text-highlighted">{{ viewpoint.name }}</span>
      <UBadge data-testid="viewpoint-notation-badge" color="primary" variant="subtle" size="sm" :label="t(`metamodel.viewpoint.notations.${viewpoint.notation}`)" />
    </span>
    <span v-if="viewpoint.description" class="text-sm text-muted">{{ viewpoint.description }}</span>
    <span class="flex flex-wrap gap-1">
      <UBadge
        v-for="metatype in viewpoint.allowedMetatypes"
        :key="metatype.id"
        color="neutral"
        variant="subtle"
        size="sm"
        :label="label(labelKeys.metatype(metatype.id), metatype.name)"
      />
      <UBadge
        v-for="relation in viewpoint.allowedRelations"
        :key="relation.id"
        color="info"
        variant="subtle"
        size="sm"
        icon="i-lucide-arrow-right-left"
        :label="label(labelKeys.metatype(relation.id), relation.name)"
      />
    </span>
  </span>
</template>
