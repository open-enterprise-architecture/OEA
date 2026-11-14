<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AuthorizationRole } from '../../api/roles'
import {
  createServiceAccount,
  updateServiceAccount,
  type ServiceAccount,
  type ServiceAccountCreated,
} from '../../api/service-accounts'
import { rejectionMessage } from './messages'

// The dialogs "New service account" and "Edit roles" of SCR-041 (US-142 AC1, US-149 AC1, AC3;
// design D8 of us-142-service-accounts). Only roles assignable to service accounts are offered.
// A rejection keeps the dialog open and says why.
const props = defineProps<{ roles: AuthorizationRole[]; account?: ServiceAccount; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ created: [account: ServiceAccountCreated]; updated: [account: ServiceAccount]; cancel: [] }>()
const { t } = useI18n()

const NAME_LENGTH = 100
const DEFAULT_VALIDITY = 90

const editing = computed(() => props.account !== undefined)
const name = ref(props.account?.name ?? '')
const description = ref(props.account?.description ?? '')
const validity = ref<number>(DEFAULT_VALIDITY)
const chosen = ref<string[]>(props.account ? [...props.account.roles] : ['integration-writer'])
const failure = ref<string>()
const saving = ref(false)

const nameValid = computed(() => name.value.trim().length > 0 && name.value.trim().length <= NAME_LENGTH)
const validityValid = computed(() => Number.isInteger(validity.value) && validity.value >= 1)
const valid = computed(() => nameValid.value && (editing.value || validityValid.value))

function toggle(role: string, on: boolean) {
  const index = chosen.value.indexOf(role)
  if (on && index < 0) {
    chosen.value.push(role)
  } else if (!on && index >= 0) {
    chosen.value.splice(index, 1)
  }
}

async function save() {
  failure.value = undefined
  if (!valid.value) {
    return
  }
  saving.value = true
  try {
    const body = {
      name: name.value.trim(),
      description: description.value.trim() || null,
      roles: [...chosen.value],
    }
    if (props.account) {
      const outcome = await updateServiceAccount(props.account.id, body, props.fetchFn)
      if (outcome.ok) {
        emit('updated', outcome.value)
        return
      }
      failure.value = rejectionMessage(outcome.rejection)
    } else {
      const outcome = await createServiceAccount({ ...body, secretValidityDays: validity.value }, props.fetchFn)
      if (outcome.ok) {
        emit('created', outcome.value)
        return
      }
      failure.value = rejectionMessage(outcome.rejection)
    }
  } catch {
    failure.value = t('serviceAccounts.failed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" data-testid="service-account-dialog" @submit.prevent="save">
    <template v-if="!editing">
      <UFormField
        :label="t('serviceAccounts.name')"
        :error="name && !nameValid ? t('serviceAccounts.nameInvalid') : undefined"
        required
      >
        <UInput v-model="name" data-testid="service-account-name" class="w-72" :maxlength="NAME_LENGTH" />
      </UFormField>
      <UFormField :label="t('serviceAccounts.description')">
        <UTextarea v-model="description" data-testid="service-account-description" class="w-full" :maxlength="1000" />
      </UFormField>
      <UFormField
        :label="t('serviceAccounts.validity')"
        :description="t('serviceAccounts.validityHint')"
        :error="!validityValid ? t('serviceAccounts.validityInvalid') : undefined"
      >
        <UInput v-model.number="validity" data-testid="service-account-validity" type="number" class="w-40" :min="1" />
      </UFormField>
    </template>
    <p v-else class="text-sm text-muted">{{ t('serviceAccounts.rolesOf', { name: account?.name }) }}</p>
    <UFormField :label="t('serviceAccounts.roles')" :description="t('serviceAccounts.rolesHint')" data-testid="service-account-roles">
      <div class="flex flex-col gap-1">
        <UCheckbox
          v-for="role in roles"
          :key="role.id"
          :data-testid="`service-account-role-${role.id}`"
          :label="role.name"
          :description="role.permissions.join(', ')"
          :model-value="chosen.includes(role.id)"
          @update:model-value="(on: boolean | 'indeterminate') => toggle(role.id, on === true)"
        />
      </div>
    </UFormField>

    <UAlert v-if="failure" data-testid="service-account-failure" color="error" variant="subtle" :description="failure" />

    <div class="flex justify-end gap-2">
      <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton
        type="submit"
        data-testid="service-account-save"
        :disabled="!valid"
        :loading="saving"
        :label="editing ? t('serviceAccounts.saveRoles') : t('serviceAccounts.create')"
      />
    </div>
  </form>
</template>
