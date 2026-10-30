<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchLoginProviders,
  login,
  loginTotp,
  oidcLoginUrl,
  type LoginOutcome,
  type LoginProvider,
} from '../api/session'
import { afterPasswordLogin } from '../session/secondFactorOffer'
import { refreshSession } from '../session/store'

// SCR-001 Login: through an identity provider (US-001), and locally for the system admin account
// (US-013 AC2) and for persons with their e-mail address (US-011). A temporary password leads to
// the password change first (REQ-173 AC2). A person without a second factor is offered the TOTP
// enrollment afterwards (US-021; design D7 of us-021-person-totp).
const props = defineProps<{ fetchFn?: typeof fetch; navigate?: (url: string) => void }>()
const router = useRouter()
const route = useRoute()

const providers = ref<LoginProvider[]>([])

// Errors of the OIDC callback arrive as query parameter; one generic message (REQ-006)
const CALLBACK_ERRORS: Record<string, string> = {
  'login-failed': 'Login failed. Your account may not be set up for this instance yet; contact an administrator.',
  'provider-unavailable': 'The identity provider is not reachable at the moment. Try again later.',
}

onMounted(async () => {
  const error = route?.query.error
  if (typeof error === 'string') {
    failure.value = CALLBACK_ERRORS[error] ?? CALLBACK_ERRORS['login-failed']
  }
  providers.value = await fetchLoginProviders(props.fetchFn)
})

function loginWith(provider: LoginProvider) {
  const target = oidcLoginUrl(provider.id)
  if (props.navigate) {
    props.navigate(target)
  } else {
    window.location.assign(target)
  }
}

const form = reactive({ loginName: '', password: '', code: '' })
const loginTransaction = ref<string>()
const failure = ref<string>()
const submitting = ref(false)

const GENERIC_FAILURE = 'Login failed. Check the user name or e-mail address and the password.'

function failureMessage(outcome: Extract<LoginOutcome, { kind: 'rejected' }>, step: 'password' | 'code'): string {
  const { rejection } = outcome
  if (rejection.retryAfterSeconds !== undefined) {
    return `Too many failed attempts. Try again in ${rejection.retryAfterSeconds} seconds.`
  }
  if (step === 'code') {
    return 'The code is not valid, or the login has expired. Enter a new code or start again.'
  }
  return GENERIC_FAILURE
}

async function handle(outcome: LoginOutcome, step: 'password' | 'code') {
  if (outcome.kind === 'success') {
    await refreshSession(props.fetchFn)
    const { result } = outcome
    if (result.requiredAction === 'change-password') {
      await router.push('/me/password')
    } else if (result.principal.principalType === 'person' && !result.mfaSatisfied) {
      await router.push(await afterPasswordLogin('/me', props.fetchFn))
    } else {
      await router.push('/me')
    }
  } else if (outcome.kind === 'second-factor') {
    loginTransaction.value = outcome.loginTransaction
  } else {
    failure.value = failureMessage(outcome, step)
  }
}

async function submitPassword() {
  failure.value = undefined
  if (!form.loginName || !form.password) {
    failure.value = GENERIC_FAILURE
    return
  }
  submitting.value = true
  try {
    const outcome = await login(form.loginName, form.password, props.fetchFn)
    form.password = ''
    await handle(outcome, 'password')
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    submitting.value = false
  }
}

async function submitCode() {
  failure.value = undefined
  submitting.value = true
  try {
    const outcome = await loginTotp(loginTransaction.value!, form.code.trim(), props.fetchFn)
    form.code = ''
    await handle(outcome, 'code')
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    submitting.value = false
  }
}

function startAgain() {
  loginTransaction.value = undefined
  failure.value = undefined
  form.code = ''
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-6 p-8">
    <h1 class="text-2xl font-semibold text-highlighted">Log in to OEA</h1>

    <UAlert v-if="failure" data-testid="login-failure" color="error" variant="subtle" :description="failure" />

    <div v-if="providers.length > 0 && !loginTransaction" class="flex flex-col gap-3" data-testid="providers">
      <UButton
        v-for="provider in providers"
        :key="provider.id"
        :data-testid="`provider-${provider.id}`"
        :label="`Log in with ${provider.name}`"
        block
        @click="loginWith(provider)"
      />
      <USeparator label="or with your user name or e-mail address" />
    </div>

    <form v-if="!loginTransaction" class="flex flex-col gap-4" novalidate @submit.prevent="submitPassword">
      <UFormField label="User name or e-mail address" name="loginName" required>
        <UInput v-model="form.loginName" data-testid="loginName" autocomplete="username" class="w-full" />
      </UFormField>
      <UFormField label="Password" name="password" required>
        <UInput
          v-model="form.password"
          data-testid="password"
          type="password"
          autocomplete="current-password"
          class="w-full"
        />
      </UFormField>
      <UButton type="submit" data-testid="submit" :loading="submitting" label="Log in" block />
    </form>

    <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="submitCode">
      <p class="text-muted">Enter the code of your authenticator app.</p>
      <UFormField label="Code" name="code" required>
        <UInput
          v-model="form.code"
          data-testid="code"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          class="w-full"
        />
      </UFormField>
      <UButton type="submit" data-testid="submit-code" :loading="submitting" label="Confirm" block />
      <UButton data-testid="start-again" variant="link" label="Start again" @click="startAgain" />
    </form>
  </main>
</template>
