<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirmTotpEnrollment, startTotpEnrollment, type TotpEnrollment } from '../api/session'
import QrCode from '../components/QrCode.vue'
import { refreshSession } from '../session/store'

// SCR-003 TOTP enrollment in the running session (REQ-171, REQ-172, ADR-042) for the system admin
// account and for persons (US-021, US-025, US-029). With `?offer=1` it is the skippable offer after
// a password login (design D7 of us-021-person-totp).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const route = useRoute()
const router = useRouter()
const offer = computed(() => route?.query.offer === '1')

const label = ref('')
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
    const outcome = await startTotpEnrollment(label.value.trim() || undefined, props.fetchFn)
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

async function skip() {
  await router.push('/')
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
      :actions="[{ label: offer ? 'Continue' : 'Back to the profile', to: offer ? '/' : '/me' }]"
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
      <p v-if="offer" data-testid="offer" class="text-muted">
        Protect your account with a second factor: an authenticator app (TOTP) on your phone. Logins then need a code
        from the app in addition to your password, and roles with administrative permissions become effective.
      </p>
      <p v-else class="text-muted">
        Set up an authenticator app (TOTP). You can set up several, for example one on a backup phone.
      </p>
      <UFormField label="Name of the authenticator" name="label" help="For example the device; optional">
        <UInput v-model="label" data-testid="label" maxlength="64" class="w-full" placeholder="Authenticator app" />
      </UFormField>
      <div class="flex gap-2">
        <UButton data-testid="start" :loading="busy" label="Set up authenticator app" @click="start" />
        <UButton
          v-if="offer"
          data-testid="skip"
          color="neutral"
          variant="ghost"
          label="Skip for now"
          @click="skip"
        />
      </div>
    </template>
  </main>
</template>
