<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { passkeysSupported, registerPasskey } from '../api/passkey'
import { confirmTotpEnrollment, startTotpEnrollment, type TotpEnrollment } from '../api/session'
import QrCode from '../components/QrCode.vue'
import { refreshSession, session } from '../session/store'

// SCR-003 TOTP enrollment in the running session (REQ-171, REQ-172, ADR-042) for the system admin
// account and for persons (US-021, US-025, US-029). With `?offer=1` it is the skippable offer after
// a password login (design D7 of us-021-person-totp). Persons choose between a passkey and an
// authenticator app (US-022; design D6 of us-022-passkeys).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const offer = computed(() => route?.query.offer === '1')
// US-031 AC2: an enforced second factor cannot be skipped
const enforced = computed(() => session.me?.requiredAction === 'enroll-second-factor')

const label = ref('')
// passkeys for persons only; the system admin account keeps TOTP (owner decision 2026-10-30)
const passkeyChoice = computed(() => passkeysSupported() && session.me?.principal.principalType === 'person')
const method = ref<'passkey' | 'totp'>('passkey')
const chosen = computed(() => (passkeyChoice.value ? method.value : 'totp'))
const METHODS = computed(() => [
  { label: t('secondFactor.passkeyRecommended'), value: 'passkey', description: t('secondFactor.passkeyDescription') },
  { label: t('profile.authenticatorApp'), value: 'totp', description: t('secondFactor.totpDescription') },
])
const enrollment = ref<TotpEnrollment>()
const code = ref('')
const failure = ref<string>()
const reauthenticate = ref(false)
const confirmed = ref(false)
const busy = ref(false)

async function startPasskey() {
  failure.value = undefined
  reauthenticate.value = false
  busy.value = true
  try {
    const outcome = await registerPasskey(label.value.trim() || undefined, props.fetchFn)
    if (outcome.kind === 'registered') {
      confirmed.value = true
      await refreshSession(props.fetchFn)
    } else if (outcome.kind === 'cancelled') {
      failure.value = t('secondFactor.passkeyCancelled')
    } else if (outcome.rejection.problemType === 'reauthentication-required') {
      reauthenticate.value = true
    } else {
      failure.value = t('secondFactor.passkeyFailed')
    }
  } catch {
    failure.value = t('secondFactor.passkeyUnsupported')
  } finally {
    busy.value = false
  }
}

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
      failure.value = t('secondFactor.startFailed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
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
      failure.value = t('secondFactor.expired')
    } else {
      failure.value = t('secondFactor.invalidCode')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
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
    <h1 class="text-2xl font-semibold text-highlighted">{{ t('profile.secondFactor') }}</h1>

    <UAlert
      v-if="confirmed"
      data-testid="enrollment-confirmed"
      color="success"
      variant="subtle"
      :title="t('secondFactor.active')"
      :description="chosen === 'passkey' ? t('secondFactor.passkeyActive') : t('secondFactor.totpActive')"
      :actions="[
        {
          label: offer || enforced ? t('secondFactor.continue') : t('secondFactor.backToProfile'),
          to: offer || enforced ? '/' : '/me',
        },
      ]"
    />
    <UAlert
      v-if="reauthenticate"
      data-testid="reauthentication-required"
      color="warning"
      variant="subtle"
      :title="t('profile.logInAgain')"
      :description="t('secondFactor.reauthenticate')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert v-if="failure" data-testid="enrollment-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="enrollment">
      <p class="text-muted">{{ t('secondFactor.scan') }}</p>
      <QrCode data-testid="qr-code" :value="enrollment.otpauthUri" :label="t('secondFactor.qrCode')" />
      <p>
        {{ t('secondFactor.key') }} <code data-testid="secret" class="break-all">{{ enrollment.secret }}</code>
      </p>
      <form class="flex flex-col gap-4" novalidate @submit.prevent="confirm">
        <UFormField :label="t('secondFactor.code')" name="code" required>
          <UInput
            v-model="code"
            data-testid="code"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            class="w-full"
          />
        </UFormField>
        <UButton type="submit" data-testid="confirm" :loading="busy" :label="t('login.confirm')" block />
      </form>
    </template>

    <template v-else-if="!confirmed">
      <UAlert
        v-if="enforced"
        data-testid="enrollment-required"
        color="warning"
        variant="subtle"
        :title="t('secondFactor.required')"
        :description="t('secondFactor.requiredHint')"
      />
      <p v-if="offer || enforced" data-testid="offer" class="text-muted">{{ t('secondFactor.offer') }}</p>
      <p v-else class="text-muted">{{ t('secondFactor.setUpHint') }}</p>
      <URadioGroup
        v-if="passkeyChoice"
        v-model="method"
        data-testid="method"
        :legend="t('profile.secondFactor')"
        :items="METHODS"
      />
      <UFormField :label="t('secondFactor.name')" name="label" :help="t('secondFactor.nameHelp')">
        <UInput
          v-model="label"
          data-testid="label"
          maxlength="64"
          class="w-full"
          :placeholder="chosen === 'passkey' ? t('profile.passkey') : t('profile.authenticatorApp')"
        />
      </UFormField>
      <div class="flex gap-2">
        <UButton
          v-if="chosen === 'passkey'"
          data-testid="start-passkey"
          icon="i-lucide-key-round"
          :loading="busy"
          :label="t('secondFactor.setUpPasskey')"
          @click="startPasskey"
        />
        <UButton v-else data-testid="start" :loading="busy" :label="t('secondFactor.setUpTotp')" @click="start" />
        <UButton
          v-if="offer && !enforced"
          data-testid="skip"
          color="neutral"
          variant="ghost"
          :label="t('secondFactor.skip')"
          @click="skip"
        />
      </div>
    </template>
  </main>
</template>
