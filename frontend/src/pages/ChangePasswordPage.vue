<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changeOwnPassword, suggestPassword } from '../api/session'
import { afterPasswordLogin } from '../session/secondFactorOffer'
import { refreshSession, session } from '../session/store'

// SCR-004 Change password (REQ-173 AC2, US-023): required after a login with a temporary or
// expired password, available to every principal with a local password. The password policy
// (US-027) is checked by the backend; a suggestion comes from the generator (US-026). After a
// required change, a person without a second factor is offered the TOTP enrollment (US-021).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()

const form = reactive({ currentPassword: '', newPassword: '', repeated: '' })
const failure = ref<string>()
const unmetRules = ref<string[]>([])
const suggestion = ref<string>()
const copied = ref(false)
const busy = ref(false)
const required = computed(() => session.me?.requiredAction === 'change-password')

// Rule names of the problem type password-policy-violation (api/openapi.yaml)
const RULES: Record<string, string> = {
  minLength: 'It is shorter than the password policy allows.',
  maxLength: 'It is longer than 128 characters.',
  requireUppercase: 'It needs an uppercase letter.',
  requireLowercase: 'It needs a lowercase letter.',
  requireDigits: 'It needs a digit.',
  requireSpecialChars: 'It needs a special character, such as ! or #.',
  historyCount: 'You used it recently; choose a password you have not used before.',
}

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

async function suggest() {
  failure.value = undefined
  copied.value = false
  try {
    const outcome = await suggestPassword(props.fetchFn)
    if (outcome.ok) {
      suggestion.value = outcome.value
    } else {
      failure.value = 'No password could be suggested.'
    }
  } catch {
    failure.value = 'The backend is not reachable.'
  }
}

async function copySuggestion() {
  if (suggestion.value) {
    await navigator.clipboard?.writeText(suggestion.value)
    copied.value = true
  }
}

function useSuggestion() {
  if (suggestion.value) {
    form.newPassword = suggestion.value
    form.repeated = suggestion.value
  }
}

async function submit() {
  unmetRules.value = []
  failure.value = clientCheck()
  if (failure.value) {
    return
  }
  busy.value = true
  try {
    const outcome = await changeOwnPassword(form.currentPassword, form.newPassword, props.fetchFn)
    if (outcome.ok) {
      const offer = required.value && session.me?.principal.principalType === 'person' && !session.me.mfaSatisfied
      await refreshSession(props.fetchFn)
      // US-031 AC2: an enforced second factor follows the password change
      const enroll = session.me?.requiredAction === 'enroll-second-factor'
      const target = enroll
        ? '/me/second-factor'
        : offer
          ? await afterPasswordLogin('/me?passwordChanged=1', props.fetchFn)
          : '/me?passwordChanged=1'
      await router.push(target)
    } else if (outcome.rejection.status === 403) {
      failure.value = 'The current password is not correct.'
    } else if (outcome.rejection.problemType === 'password-policy-violation') {
      failure.value = 'The new password does not meet the password policy:'
      unmetRules.value = outcome.rejection.invalidFields.map((rule) => RULES[rule] ?? rule)
    } else {
      failure.value = 'The new password was not accepted: it must differ from the current password.'
    }
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    form.currentPassword = ''
    form.newPassword = ''
    form.repeated = ''
    suggestion.value = undefined
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
      description="You logged in with a temporary or expired password. Choose a new password to continue."
    />
    <UAlert v-if="failure" data-testid="password-failure" color="error" variant="subtle" :description="failure">
      <template v-if="unmetRules.length" #description>
        <p>{{ failure }}</p>
        <ul data-testid="unmet-rules" class="mt-1 list-disc pl-5">
          <li v-for="rule in unmetRules" :key="rule">{{ rule }}</li>
        </ul>
      </template>
    </UAlert>

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
      <UFormField label="New password" name="newPassword" required help="At least 12 characters; the password policy may require more">
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

      <div class="flex flex-col gap-2">
        <UButton
          data-testid="suggest-password"
          color="neutral"
          variant="outline"
          icon="i-lucide-wand-sparkles"
          label="Suggest a password"
          block
          @click="suggest"
        />
        <template v-if="suggestion">
          <UFormField label="Suggested password" name="suggestion" help="Store it in your password manager before you use it">
            <UInput
              :model-value="suggestion"
              data-testid="suggestion"
              readonly
              class="w-full font-mono"
              autocomplete="off"
            />
          </UFormField>
          <div class="flex gap-2">
            <UButton
              data-testid="copy-suggestion"
              color="neutral"
              variant="subtle"
              icon="i-lucide-copy"
              :label="copied ? 'Copied' : 'Copy'"
              @click="copySuggestion"
            />
            <UButton
              data-testid="use-suggestion"
              color="neutral"
              variant="subtle"
              icon="i-lucide-arrow-down-to-line"
              label="Use as new password"
              @click="useSuggestion"
            />
          </div>
        </template>
      </div>

      <UButton type="submit" data-testid="change-password" :loading="busy" label="Change password" block />
    </form>
  </main>
</template>
