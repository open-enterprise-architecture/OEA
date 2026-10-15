<script setup lang="ts">
import { reactive, ref } from 'vue'
import { completeSetup, type SetupResult } from '../api/setup'
import { FIELD_MESSAGES, type SetupField, type SetupForm, validateSetupForm } from '../setup/validation'

// Local bootstrapping (UC-02, US-013). SCR-002 stays `legacy-mockup` until change 6 (screen story).
const props = defineProps<{ fetchFn?: typeof fetch }>()

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
  'invalid-setup-token':
    'The setup token is wrong or expired. Read the current token inside the backend container, e.g. docker compose exec backend cat /run/oea/setup-token.',
  'already-bootstrapped': 'This instance is already set up. A second system admin account cannot be created here.',
  'validation-failed': 'Some fields do not meet the requirements.',
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
    failure.value = PROBLEM_MESSAGES[outcome.problemType] ?? 'The setup failed. Check the backend log.'
    for (const field of outcome.invalidFields) {
      if (field in FIELD_MESSAGES) {
        errors.value[field as SetupField] = FIELD_MESSAGES[field as SetupField]
      }
    }
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-6 p-8">
    <h1 class="text-2xl font-semibold text-highlighted">Set up this OEA instance</h1>

    <UAlert
      v-if="result"
      data-testid="setup-success"
      color="success"
      variant="subtle"
      title="Setup complete"
      :description="`System admin account “${result.loginName}” created for instance “${result.instanceSlug}”.`"
    />

    <template v-else>
      <p class="text-muted">
        Create the local system admin account. The one-time setup token is stored in a file inside the backend
        container: <code>docker compose exec backend cat /run/oea/setup-token</code>
      </p>

      <UAlert v-if="failure" data-testid="setup-failure" color="error" variant="subtle" :description="failure" />

      <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
        <UFormField label="Setup token" name="setupToken" :error="errors.setupToken" required>
          <UInput v-model="form.setupToken" data-testid="setupToken" autocomplete="off" class="w-full" />
        </UFormField>
        <UFormField label="Login name" name="loginName" :error="errors.loginName" required>
          <UInput v-model="form.loginName" data-testid="loginName" autocomplete="username" class="w-full" />
        </UFormField>
        <UFormField label="Password" name="password" :error="errors.password" help="12–128 characters" required>
          <UInput
            v-model="form.password"
            data-testid="password"
            type="password"
            autocomplete="new-password"
            class="w-full"
          />
        </UFormField>
        <UFormField label="Repeat password" name="passwordConfirmation" :error="errors.passwordConfirmation" required>
          <UInput
            v-model="form.passwordConfirmation"
            data-testid="passwordConfirmation"
            type="password"
            autocomplete="new-password"
            class="w-full"
          />
        </UFormField>
        <UFormField
          label="Instance slug"
          name="instanceSlug"
          :error="errors.instanceSlug"
          help="Used in external references (urn:oea:<slug>:<id>); cannot be changed later"
          required
        >
          <UInput v-model="form.instanceSlug" data-testid="instanceSlug" class="w-full" />
        </UFormField>
        <UButton type="submit" data-testid="submit" :loading="submitting" label="Create system admin account" block />
      </form>
    </template>
  </main>
</template>
