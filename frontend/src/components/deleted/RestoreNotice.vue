<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RestoreNoticeData } from '../../api/deletedEntities'

// The notice after a restore (SCR-068 and SCR-020; design D3 of s17-deleted-entities): what was
// restored, what stayed deleted and why, what was placed at the top level; or that the entity was
// changed meanwhile or is a connection with a deleted endpoint.
const props = defineProps<{ notice: RestoreNoticeData }>()
const { t } = useI18n()

const stayed = computed(() =>
  props.notice.kind === 'done'
    ? props.notice.summary.stayed.map((entry) => t(`deletedEntities.notice.stayedReason.${entry.reason}`, { name: entry.name }))
    : [],
)
</script>

<template>
  <UAlert
    v-if="notice.kind === 'done'"
    data-testid="restore-notice"
    :color="notice.summary.stayed.length > 0 ? 'warning' : 'success'"
    variant="subtle"
    icon="i-lucide-undo-2"
    :title="t('deletedEntities.notice.restored', { names: notice.summary.restored.join(', ') })"
  >
    <template v-if="stayed.length > 0 || notice.summary.movedToTopLevel.length > 0" #description>
      <p v-if="stayed.length > 0" data-testid="restore-notice-stayed">{{ t('deletedEntities.notice.stayed', { names: stayed.join(', ') }) }}</p>
      <p v-if="notice.summary.movedToTopLevel.length > 0" data-testid="restore-notice-moved">
        {{ t('deletedEntities.notice.moved', { names: notice.summary.movedToTopLevel.join(', ') }) }}
      </p>
    </template>
  </UAlert>
  <UAlert
    v-else-if="notice.kind === 'stale'"
    data-testid="restore-notice-stale"
    color="warning"
    variant="subtle"
    :description="t('deletedEntities.notice.stale', { name: notice.name })"
  />
  <UAlert
    v-else
    data-testid="restore-notice-endpoint"
    color="warning"
    variant="subtle"
    :description="t('deletedEntities.notice.endpointDeleted', { name: notice.name })"
  />
</template>
