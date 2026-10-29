<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changeOwnPassword } from '../api/session'
import { refreshSession, session } from '../session/store'

// SCR-004 Change password (REQ-173 AC2, US-023): required after a login with a temporary
// password, available to every principal with a local password.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()

const form = reactive({ currentPassword: '', newPassword: '', repeated: '' })
const failure = ref<string>()
const busy = ref(false)
const required = computed(() => session.me?.requiredAction === 'change-password')

function clientCheck(): string | undefined {
  if (form.newPassword.length < 12 || form.newPassword.length > 128) {
    return 'The new password must have 12 to 128 characters.'
  }
  if (form.newPassword !== form.repeated) {
    return 'The new passwords differ.'
  }
  if (form.newPassword === form.currentPassword) {
    return 'The new password must differ from the current one.'
  }
  return undefined
}

async function submit() {
  failure.value = clientCheck()
  if (failure.value) {
    return
  }
  busy.value = true
  try {
    const outcome = await changeOwnPassword(form.currentPassword, form.newPassword, props.fetchFn)
    if (outcome.ok) {
      await refreshSession(props.fetchFn)
      await router.push({ path: '/me', query: { passwordChanged: '1' } })
    } else if (outcome.rejection.status === 403) {
      failure.value = 'The current password is not correct.'
    } else {
      failure.value = 'The new password was not accepted: use 12 to 128 characters that differ from the current password.'
    }
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    form.currentPassword = ''
    form.newPassword = ''
    form.repeated = ''
    busy.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-6 p-8">
    <h1 class="text-2xl font-semibold text-highlighted">Change password</h1>
    <UAlert
      v-if="required"
      data-testid="password-change-required"
      color="warning"
      variant="subtle"
      description="You logged in with a temporary password. Choose your own password to continue."
    />
    <UAlert v-if="failure" data-testid="password-failure" color="error" variant="subtle" :description="failure" />

    <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
      <UFormField label="Current password" name="currentPassword" required>
        <UInput
          v-model="form.currentPassword"
          data-testid="currentPassword"
          type="password"
          autocomplete="current-password"
          class="w-full"
        />
      </UFormField>
      <UFormField label="New password" name="newPassword" required help="12 to 128 characters">
        <UInput
          v-model="form.newPassword"
          data-testid="newPassword"
          type="password"
          autocomplete="new-password"
          class="w-full"
        />
      </UFormField>
      <UFormField label="Repeat the new password" name="repeated" required>
        <UInput v-model="form.repeated" data-testid="repeated" type="password" autocomplete="new-password" class="w-full" />
      </UFormField>
      <UButton type="submit" data-testid="change-password" :loading="busy" label="Change password" block />
    </form>
  </main>
</template>
