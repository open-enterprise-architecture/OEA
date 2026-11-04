<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { completeSetup, type SetupResult } from '../api/setup'
import {
  FIELD_MESSAGES,
  PASSWORD_MAX,
  PASSWORD_MIN,
  type SetupField,
  type SetupForm,
  validateSetupForm,
} from '../setup/validation'

// Local bootstrapping (UC-02, US-013). SCR-002 stays `legacy-mockup` until change 6 (screen story).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const form = reactive<SetupForm>({
  setupToken: '',
  loginName: '',
  password: '',
  passwordConfirmation: '',
  instanceSlug: '',
})
const errors = ref<Partial<Record<SetupField, string>>>({})
const failure = ref<string>()
const submitting = ref(false)
const result = ref<SetupResult>()

const PROBLEM_MESSAGES: Record<string, string> = {
  'invalid-setup-token': 'setup.invalidToken',
  'already-bootstrapped': 'setup.alreadyBootstrapped',
  'validation-failed': 'setup.validationFailed',
}

/** The translated error of a field, if any. */
function fieldError(field: SetupField): string | undefined {
  const key = errors.value[field]
  return key ? t(key, { min: PASSWORD_MIN, max: PASSWORD_MAX }) : undefined
}

async function submit() {
  failure.value = undefined
  errors.value = validateSetupForm(form)
  if (Object.keys(errors.value).length > 0) {
    return
  }
  submitting.value = true
  try {
    const outcome = await completeSetup(
      {
        setupToken: form.setupToken.trim(),
        loginName: form.loginName,
        password: form.password,
        instanceSlug: form.instanceSlug,
      },
      props.fetchFn,
    )
    if (outcome.ok) {
      result.value = outcome.result
      form.password = ''
      form.passwordConfirmation = ''
      form.setupToken = ''
      return
    }
    failure.value =
      outcome.status === 429
        ? t('login.tooManyAttempts', { seconds: outcome.retryAfterSeconds ?? 600 })
        : t(PROBLEM_MESSAGES[outcome.problemType] ?? 'setup.failed')
    for (const field of outcome.invalidFields) {
      if (field in FIELD_MESSAGES) {
        errors.value[field as SetupField] = FIELD_MESSAGES[field as SetupField]
      }
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-6 p-8">
    <h1 class="text-2xl font-semibold text-highlighted">{{ t('setup.title') }}</h1>

    <UAlert
      v-if="result"
      data-testid="setup-success"
      color="success"
      variant="subtle"
      :title="t('setup.complete')"
      :description="t('setup.created', { loginName: result.loginName, instanceSlug: result.instanceSlug })"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />

    <template v-else>
      <p class="text-muted">
        {{ t('setup.intro') }} <code>docker compose exec backend cat /run/oea/setup-token</code>
      </p>

      <UAlert v-if="failure" data-testid="setup-failure" color="error" variant="subtle" :description="failure" />

      <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
        <UFormField :label="t('setup.setupToken')" name="setupToken" :error="fieldError('setupToken')" required>
          <UInput v-model="form.setupToken" data-testid="setupToken" autocomplete="off" class="w-full" />
        </UFormField>
        <UFormField :label="t('setup.loginName')" name="loginName" :error="fieldError('loginName')" required>
          <UInput v-model="form.loginName" data-testid="loginName" autocomplete="username" class="w-full" />
        </UFormField>
        <UFormField
          :label="t('login.password')"
          name="password"
          :error="fieldError('password')"
          :help="t('setup.passwordHelp', { min: PASSWORD_MIN, max: PASSWORD_MAX })"
          required
        >
          <UInput
            v-model="form.password"
            data-testid="password"
            type="password"
            autocomplete="new-password"
            class="w-full"
          />
        </UFormField>
        <UFormField
          :label="t('setup.repeatPassword')"
          name="passwordConfirmation"
          :error="fieldError('passwordConfirmation')"
          required
        >
          <UInput
            v-model="form.passwordConfirmation"
            data-testid="passwordConfirmation"
            type="password"
            autocomplete="new-password"
            class="w-full"
          />
        </UFormField>
        <UFormField
          :label="t('setup.instanceSlug')"
          name="instanceSlug"
          :error="fieldError('instanceSlug')"
          :help="t('setup.instanceSlugHelp')"
          required
        >
          <UInput v-model="form.instanceSlug" data-testid="instanceSlug" class="w-full" />
        </UFormField>
        <UButton type="submit" data-testid="submit" :loading="submitting" :label="t('setup.submit')" block />
      </form>
    </template>
  </main>
</template>
