<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchLoginProviders,
  login,
  loginTotp,
  oidcLoginUrl,
  type LoginOutcome,
  type LoginProvider,
  type SecondFactorMethod,
} from '../api/session'
import { loginWithPasskey, passkeysSupported, type PasskeyLoginOutcome } from '../api/passkey'
import { afterPasswordLogin } from '../session/secondFactorOffer'
import { refreshSession } from '../session/store'

// SCR-001 Login: through an identity provider (US-001), and locally for the system admin account
// (US-013 AC2) and for persons with their e-mail address (US-011). A temporary password leads to
// the password change first (REQ-173 AC2). A person without a second factor is offered the TOTP
// enrollment afterwards (US-021; design D7 of us-021-person-totp). Persons log in with a passkey
// without password, or confirm the password with it (US-009; design D6 of us-022-passkeys).
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
const methods = ref<SecondFactorMethod[]>([])
const passkeys = passkeysSupported()

// Which second factors the person can use (design D5 of us-022-passkeys)
const secondStepHint = computed(() => {
  const totp = methods.value.includes('totp')
  const passkey = methods.value.includes('passkey') && passkeys
  if (totp && passkey) {
    return 'Confirm the login with your passkey or the code of your authenticator app.'
  }
  return passkey ? 'Confirm the login with your passkey.' : 'Enter the code of your authenticator app.'
})
const failure = ref<string>()
const submitting = ref(false)

const GENERIC_FAILURE = 'Login failed. Check the user name or e-mail address and the password.'

function failureMessage(
  outcome: Extract<LoginOutcome, { kind: 'rejected' }>,
  step: 'password' | 'code' | 'passkey',
): string {
  const { rejection } = outcome
  if (rejection.retryAfterSeconds !== undefined) {
    return `Too many failed attempts. Try again in ${rejection.retryAfterSeconds} seconds.`
  }
  if (step === 'code') {
    return 'The code is not valid, or the login has expired. Enter a new code or start again.'
  }
  if (step === 'passkey') {
    return 'The passkey was not accepted. Try again, or log in with your password.'
  }
  return GENERIC_FAILURE
}

async function handle(outcome: PasskeyLoginOutcome, step: 'password' | 'code' | 'passkey') {
  if (outcome.kind === 'cancelled') {
    return
  }
  if (outcome.kind === 'success') {
    await refreshSession(props.fetchFn)
    const { result } = outcome
    if (result.requiredAction === 'change-password') {
      await router.push('/me/password')
    } else if (result.requiredAction === 'enroll-second-factor') {
      await router.push('/me/second-factor')
    } else if (result.principal.principalType === 'person' && !result.mfaSatisfied) {
      await router.push(await afterPasswordLogin('/me', props.fetchFn))
    } else {
      await router.push('/me')
    }
  } else if (outcome.kind === 'second-factor') {
    loginTransaction.value = outcome.loginTransaction
    methods.value = outcome.methods
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

async function submitPasskey() {
  failure.value = undefined
  submitting.value = true
  try {
    await handle(await loginWithPasskey(loginTransaction.value, props.fetchFn), 'passkey')
  } catch {
    failure.value = 'The passkey could not be used.'
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
      <UButton
        v-if="passkeys"
        data-testid="passkey-login"
        color="neutral"
        variant="outline"
        icon="i-lucide-key-round"
        label="Log in with a passkey"
        block
        @click="submitPasskey"
      />
    </form>

    <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="submitCode">
      <p data-testid="second-step-hint" class="text-muted">{{ secondStepHint }}</p>
      <UButton
        v-if="methods.includes('passkey') && passkeys"
        data-testid="use-passkey"
        icon="i-lucide-key-round"
        :loading="submitting"
        label="Use a passkey"
        block
        @click="submitPasskey"
      />
      <UFormField v-if="methods.includes('totp')" label="Code" name="code" required>
        <UInput
          v-model="form.code"
          data-testid="code"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          class="w-full"
        />
      </UFormField>
      <UButton
        v-if="methods.includes('totp')"
        type="submit"
        data-testid="submit-code"
        :loading="submitting"
        label="Confirm"
        block
      />
      <UButton data-testid="start-again" variant="link" label="Start again" @click="startAgain" />
    </form>
  </main>
</template>
