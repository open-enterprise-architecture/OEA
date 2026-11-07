<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  invalidFields,
  updatePropertyVisibility,
  type AuthorizationRole,
  type PropertyVisibilityEntry,
  type VisibilityChange,
  type VisibilityMode,
} from '../../api/visibility'

// The edit dialog of SCR-032 (US-124 AC1–AC3, US-125 AC2, AC3; design D5 of
// us-124-property-visibility): only the fields of the chosen mode, inline validation without a
// request, and the fields named by a 422.
const props = defineProps<{
  entry: PropertyVisibilityEntry
  roles: AuthorizationRole[]
  connectionTypes: { id: string; name: string }[]
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{ saved: [entries: PropertyVisibilityEntry[]]; cancel: [] }>()
const { t } = useI18n()

const mode = ref<VisibilityMode>(props.entry.visibilityMode ?? 'public')
const readRoles = ref<string[]>([...(props.entry.readRoles ?? [])])
const writeRoles = ref<string[]>([...(props.entry.writeRoles ?? [])])
const scopingType = ref<string | undefined>(props.entry.scopingConnectionType ?? undefined)
const invalid = ref<string[]>([])
const failure = ref<string>()
const saving = ref(false)

const modeItems = computed(() => [
  { value: 'public', label: t('visibility.mode.public'), description: t('visibility.mode.publicHint') },
  { value: 'role-restricted', label: t('visibility.mode.role-restricted'), description: t('visibility.mode.roleHint') },
  { value: 'connection-scoped', label: t('visibility.mode.connection-scoped'), description: t('visibility.mode.connectionHint') },
])
const typeItems = computed(() => props.connectionTypes.map((type) => ({ value: type.id, label: type.name })))

function change(): VisibilityChange {
  const restricted = mode.value === 'role-restricted'
  return {
    propertyDefId: props.entry.propertyDefId,
    visibilityMode: mode.value,
    readRoles: restricted ? [...readRoles.value] : [],
    writeRoles: restricted ? [...writeRoles.value] : [],
    scopingConnectionType: mode.value === 'connection-scoped' ? (scopingType.value ?? null) : null,
  }
}

function toggle(list: string[], role: string, on: boolean) {
  const index = list.indexOf(role)
  if (on && index < 0) {
    list.push(role)
  } else if (!on && index >= 0) {
    list.splice(index, 1)
  }
}

async function save() {
  const request = change()
  invalid.value = invalidFields(request)
  failure.value = undefined
  if (invalid.value.length > 0) {
    return
  }
  saving.value = true
  try {
    const outcome = await updatePropertyVisibility([request], props.fetchFn)
    if (outcome.ok) {
      emit('saved', outcome.value)
      return
    }
    // a 422 names the fields as [0].readRoles
    invalid.value = outcome.rejection.invalidFields.map((field) => field.replace(/^\[\d+\]\./, ''))
    failure.value = invalid.value.length > 0 ? undefined : t('visibility.saveFailed')
  } catch {
    failure.value = t('visibility.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="flex min-h-96 flex-col gap-4" data-testid="visibility-dialog" @submit.prevent="save">
    <UFormField :label="t('visibility.mode.title')">
      <URadioGroup v-model="mode" data-testid="visibility-mode" :items="modeItems" />
    </UFormField>

    <template v-if="mode === 'role-restricted'">
      <UFormField
        :label="t('visibility.readRoles')"
        :error="invalid.includes('readRoles') ? t('visibility.readRolesRequired') : undefined"
        data-testid="visibility-read-roles"
      >
        <div class="grid grid-cols-2 gap-1">
          <UCheckbox
            v-for="role in roles"
            :key="role.id"
            :data-testid="`read-role-${role.id}`"
            :label="role.name"
            :model-value="readRoles.includes(role.id)"
            @update:model-value="(on: boolean | 'indeterminate') => toggle(readRoles, role.id, on === true)"
          />
        </div>
      </UFormField>
      <UFormField
        :label="t('visibility.writeRoles')"
        :description="t('visibility.writeRolesHint')"
        :error="invalid.includes('writeRoles') ? t('visibility.writeRolesSubset') : undefined"
        data-testid="visibility-write-roles"
      >
        <div class="grid grid-cols-2 gap-1">
          <UCheckbox
            v-for="role in roles"
            :key="role.id"
            :data-testid="`write-role-${role.id}`"
            :label="role.name"
            :model-value="writeRoles.includes(role.id)"
            @update:model-value="(on: boolean | 'indeterminate') => toggle(writeRoles, role.id, on === true)"
          />
        </div>
      </UFormField>
    </template>

    <UFormField
      v-if="mode === 'connection-scoped'"
      :label="t('visibility.scopingType')"
      :description="t('visibility.scopingTypeHint')"
      :error="invalid.includes('scopingConnectionType') ? t('visibility.scopingTypeRequired') : undefined"
      data-testid="visibility-scoping-type"
    >
      <USelect v-model="scopingType" data-testid="visibility-scoping-select" :items="typeItems" class="w-72" />
    </UFormField>

    <UAlert v-if="failure" data-testid="visibility-failure" color="error" variant="subtle" :description="failure" />

    <div class="mt-auto flex justify-end gap-2">
      <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="visibility-save" :loading="saving" :label="t('visibility.save')" />
    </div>
  </form>
</template>
