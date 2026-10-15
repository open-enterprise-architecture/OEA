<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, loginTotp, type LoginOutcome } from '../api/session'
import { refreshSession } from '../session/store'

// Login of the local system admin account (US-013 AC2). SCR-001 stays `legacy-mockup` until change 6 (screen story).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()

const form = reactive({ loginName: '', password: '', code: '' })
const loginTransaction = ref<string>()
const failure = ref<string>()
const submitting = ref(false)

const GENERIC_FAILURE = 'Login failed. Check the login name and the password.'

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
    await router.push('/me')
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

    <form v-if="!loginTransaction" class="flex flex-col gap-4" novalidate @submit.prevent="submitPassword">
      <UFormField label="Login name" name="loginName" required>
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
