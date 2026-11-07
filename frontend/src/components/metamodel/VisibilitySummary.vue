<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PropertyDefinitionDetail } from '../../api/metamodel'
import { metamodel } from '../../metamodel/store'

// The visibility of a property, read-only in SCR-030 (US-125 AC1, REQ-125; design D5 of
// us-124-property-visibility): security administration changes it on SCR-032.
const props = defineProps<{ visibility: PropertyDefinitionDetail['visibility'] }>()
const { t } = useI18n()

function typeName(id: string | null | undefined): string {
  return metamodel.metatypes.find((metatype) => metatype.id === id)?.name ?? id ?? ''
}
</script>

<template>
  <div class="flex flex-col gap-1 rounded-md border border-default p-3 text-sm" data-testid="property-visibility">
    <span class="font-medium text-highlighted">{{ t('visibility.mode.title') }}</span>
    <span data-testid="property-visibility-mode">{{ t(`visibility.mode.${props.visibility?.visibilityMode ?? 'public'}`) }}</span>
    <span v-if="props.visibility?.visibilityMode === 'role-restricted'" class="text-muted" data-testid="property-visibility-roles">
      {{
        t('visibility.rolesSummary', {
          read: (props.visibility.readRoles ?? []).join(', '),
          write: (props.visibility.writeRoles ?? []).join(', ') || t('visibility.nobody'),
        })
      }}
    </span>
    <span v-if="props.visibility?.visibilityMode === 'connection-scoped'" class="text-muted">
      {{ typeName(props.visibility.scopingConnectionType) }}
    </span>
    <span class="text-xs text-muted">{{ t('visibility.metamodelHint') }}</span>
  </div>
</template>
