<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage, type Rejection } from '../../api/problem'
import { createAuthorizationRole, type AuthorizationRole, type Permission } from '../../api/roles'

// The dialog "New role" of SCR-039 (US-151 AC1–AC4; design D5 of us-128-override-role-list): ID,
// name, description, an explicit permission list and "assignable to service accounts". A
// rejection keeps the dialog open and says why.
const props = defineProps<{ permissions: Permission[]; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ created: [role: AuthorizationRole]; cancel: [] }>()
const { t } = useI18n()

const ID_PATTERN = /^[a-z][a-z0-9-]{1,62}$/

const id = ref('')
const name = ref('')
const description = ref('')
const chosen = ref<string[]>([])
const assignable = ref(false)
const failure = ref<string>()
const saving = ref(false)

const idValid = computed(() => ID_PATTERN.test(id.value))
const nameValid = computed(() => name.value.trim().length > 0 && name.value.length <= 100)

function toggle(permission: string, on: boolean) {
  const index = chosen.value.indexOf(permission)
  if (on && index < 0) {
    chosen.value.push(permission)
  } else if (!on && index >= 0) {
    chosen.value.splice(index, 1)
  }
}

function message(rejection: Rejection): string {
  if (rejection.status === 409) {
    return t('roles.idTaken', { id: id.value })
  }
  if (rejection.invalidFields.includes('assignableToServiceAccounts')) {
    return t('roles.serviceAccountsPrivileged')
  }
  if (rejection.invalidFields.includes('permissions')) {
    return t('roles.permissionsInvalid')
  }
  return rateLimitMessage(rejection) ?? t('roles.createFailed')
}

async function save() {
  failure.value = undefined
  if (!idValid.value || !nameValid.value) {
    return
  }
  saving.value = true
  try {
    const outcome = await createAuthorizationRole(
      {
        id: id.value,
        name: name.value.trim(),
        description: description.value.trim() || null,
        permissions: [...chosen.value],
        assignableToServiceAccounts: assignable.value,
      },
      props.fetchFn,
    )
    if (outcome.ok) {
      emit('created', outcome.value)
      return
    }
    failure.value = message(outcome.rejection)
  } catch {
    failure.value = t('roles.createFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" data-testid="role-dialog" @submit.prevent="save">
    <UFormField
      :label="t('roles.id')"
      :description="t('roles.idHint')"
      :error="id && !idValid ? t('roles.idInvalid') : undefined"
      required
    >
      <UInput v-model="id" data-testid="role-id" class="w-72" />
    </UFormField>
    <UFormField :label="t('roles.name')" required>
      <UInput v-model="name" data-testid="role-name" class="w-72" :maxlength="100" />
    </UFormField>
    <UFormField :label="t('roles.description')">
      <UTextarea v-model="description" data-testid="role-description" class="w-full" :maxlength="1000" />
    </UFormField>
    <UFormField :label="t('roles.permissions')" :description="t('roles.permissionsHint')" data-testid="role-permissions">
      <div class="grid grid-cols-2 gap-1">
        <UCheckbox
          v-for="permission in permissions"
          :key="permission.id"
          :data-testid="`role-permission-${permission.id}`"
          :label="permission.adminClass ? t('roles.adminPermission', { id: permission.id }) : permission.id"
          :model-value="chosen.includes(permission.id)"
          @update:model-value="(on: boolean | 'indeterminate') => toggle(permission.id, on === true)"
        />
      </div>
    </UFormField>
    <UCheckbox v-model="assignable" data-testid="role-assignable" :label="t('roles.assignable')" />

    <UAlert v-if="failure" data-testid="role-failure" color="error" variant="subtle" :description="failure" />

    <div class="flex justify-end gap-2">
      <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton
        type="submit"
        data-testid="role-save"
        :disabled="!idValid || !nameValid"
        :loading="saving"
        :label="t('roles.create')"
      />
    </div>
  </form>
</template>
