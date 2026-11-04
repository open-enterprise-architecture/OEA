<script setup lang="ts">
import { useI18n } from 'vue-i18n'

// Warning after a write on a stale version (UC-04 A5, design D4 of scr-030-metamodel-configuration):
// the current state is shown in the slot; the administrator overwrites it or discards the input.
defineProps<{ overwriteLabel?: string }>()
const emit = defineEmits<{ overwrite: []; discard: [] }>()
const { t } = useI18n()
</script>

<template>
  <UAlert
    data-testid="collision-warning"
    color="warning"
    variant="subtle"
    icon="i-lucide-users"
    :title="t('metamodel.collision.title')"
  >
    <template #description>
      <div class="flex flex-col gap-2">
        <p>{{ t('metamodel.collision.description') }}</p>
        <div data-testid="collision-current" class="rounded border border-default bg-default p-2 text-sm">
          <slot />
        </div>
        <div class="flex flex-wrap gap-2">
          <UButton
            data-testid="collision-overwrite"
            size="sm"
            color="warning"
            :label="overwriteLabel ?? t('metamodel.collision.overwrite')"
            @click="emit('overwrite')"
          />
          <UButton
            data-testid="collision-discard"
            size="sm"
            color="neutral"
            variant="outline"
            :label="t('metamodel.collision.discard')"
            @click="emit('discard')"
          />
        </div>
      </div>
    </template>
  </UAlert>
</template>
