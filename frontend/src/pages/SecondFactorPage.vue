<script setup lang="ts">
import { ref } from 'vue'
import { confirmTotpEnrollment, startTotpEnrollment, type TotpEnrollment } from '../api/session'
import QrCode from '../components/QrCode.vue'
import { refreshSession } from '../session/store'

// TOTP enrollment in the running session (REQ-171, REQ-172, ADR-042). SCR-003 stays `legacy-mockup` until change 6.
const props = defineProps<{ fetchFn?: typeof fetch }>()

const enrollment = ref<TotpEnrollment>()
const code = ref('')
const failure = ref<string>()
const reauthenticate = ref(false)
const confirmed = ref(false)
const busy = ref(false)

async function start() {
  failure.value = undefined
  reauthenticate.value = false
  busy.value = true
  try {
    const outcome = await startTotpEnrollment('Authenticator app', props.fetchFn)
    if (outcome.ok) {
      enrollment.value = outcome.value
    } else if (outcome.rejection.problemType === 'reauthentication-required') {
      reauthenticate.value = true
    } else {
      failure.value = 'The enrollment could not be started.'
    }
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    busy.value = false
  }
}

async function confirm() {
  failure.value = undefined
  busy.value = true
  try {
    const outcome = await confirmTotpEnrollment(enrollment.value!.credentialId, code.value.trim(), props.fetchFn)
    code.value = ''
    if (outcome.ok) {
      confirmed.value = true
      enrollment.value = undefined
      await refreshSession(props.fetchFn)
    } else if (outcome.rejection.status === 404) {
      enrollment.value = undefined
      failure.value = 'The enrollment has expired. Start it again.'
    } else {
      failure.value = 'The code is not valid. Enter the current code of your authenticator app.'
    }
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex max-w-xl flex-col gap-6 p-8">
    <h1 class="text-2xl font-semibold text-highlighted">Second factor</h1>

    <UAlert
      v-if="confirmed"
      data-testid="enrollment-confirmed"
      color="success"
      variant="subtle"
      title="Second factor active"
      description="Your authenticator app is set up, and this session is confirmed with it."
      :actions="[{ label: 'Back to the profile', to: '/me' }]"
    />
    <UAlert
      v-if="reauthenticate"
      data-testid="reauthentication-required"
      color="warning"
      variant="subtle"
      title="Log in again"
      description="Adding a second factor needs a login within the last 5 minutes."
      :actions="[{ label: 'Log in', to: '/login' }]"
    />
    <UAlert v-if="failure" data-testid="enrollment-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="enrollment">
      <p class="text-muted">
        Scan the QR code with your authenticator app, or enter the key manually. The key is shown only now.
      </p>
      <QrCode data-testid="qr-code" :value="enrollment.otpauthUri" label="QR code for the authenticator app" />
      <p>
        Key: <code data-testid="secret" class="break-all">{{ enrollment.secret }}</code>
      </p>
      <form class="flex flex-col gap-4" novalidate @submit.prevent="confirm">
        <UFormField label="Code from the app" name="code" required>
          <UInput
            v-model="code"
            data-testid="code"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            class="w-full"
          />
        </UFormField>
        <UButton type="submit" data-testid="confirm" :loading="busy" label="Confirm" block />
      </form>
    </template>

    <template v-else-if="!confirmed">
      <p class="text-muted">
        Set up an authenticator app (TOTP). It is required before other identities get access to this instance.
      </p>
      <UButton data-testid="start" :loading="busy" label="Set up authenticator app" @click="start" />
    </template>
  </main>
</template>
