<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { listOwnSecondFactors, removeOwnSecondFactor, updateOwnLanguage, type SecondFactor } from '../api/session'
import { LANGUAGES, type Language } from '../i18n'
import { applyLanguage, endSession, refreshSession, session } from '../session/store'

// SCR-005 Profile and security settings: GET /me (REQ-003, ADR-032 §7) and the own second factors –
// authenticator apps and passkeys – with removal (US-029 AC3; design D7 of us-021-person-totp) – and the
// language of the user interface (US-140; design D6 of us-140-dynamic-labels)
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

const me = computed(() => session.me)

// "browser" stands for no chosen language: the language of the browser applies
const BROWSER = 'browser'
const languageItems = computed(() => [
  { label: t('profile.browserLanguage'), value: BROWSER },
  ...LANGUAGES.map((language) => ({ label: t(`languages.${language}`), value: language })),
])
const chosenLanguage = computed(() => session.me?.uiLanguage ?? BROWSER)
const languageFailure = ref<string>()

async function chooseLanguage(value: string) {
  languageFailure.value = undefined
  const language = value === BROWSER ? null : (value as Language)
  try {
    const outcome = await updateOwnLanguage(language, props.fetchFn)
    if (!outcome.ok) {
      languageFailure.value = t('profile.languageFailed')
      return
    }
    if (session.me) {
      session.me.uiLanguage = language
    }
    await applyLanguage(props.fetchFn)
  } catch {
    languageFailure.value = t('common.backendUnreachable')
  }
}

type RoleGrant = NonNullable<typeof session.me>['roles'][number]

// Source and effectiveness of a role (ADR-032 §7, SoD-8)
function roleLabel(grant: RoleGrant): string {
  const source = grant.source === 'idp' ? t('profile.roleFromIdp') : ''
  const state = grant.effective
    ? ''
    : grant.notEffectiveReason === 'mfa-required'
      ? t('profile.roleNeedsSecondFactor')
      : t('profile.roleNotEffective')
  return `${grant.role.name}${source}${state}`
}

const factors = ref<SecondFactor[]>([])
const factorFailure = ref<string>()
const reauthenticate = ref(false)

function typeName(factor: SecondFactor): string {
  return factor.type === 'passkey' ? t('profile.passkey') : t('profile.authenticatorApp')
}

function date(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleDateString() : t('profile.never')
}

async function loadFactors() {
  try {
    factors.value = await listOwnSecondFactors(props.fetchFn)
  } catch {
    factors.value = []
  }
}

async function remove(factor: SecondFactor) {
  factorFailure.value = undefined
  reauthenticate.value = false
  try {
    const outcome = await removeOwnSecondFactor(factor.credentialId, props.fetchFn)
    if (outcome.ok) {
      await loadFactors()
    } else if (outcome.rejection.problemType === 'reauthentication-required') {
      reauthenticate.value = true
    } else if (outcome.rejection.problemType === 'last-second-factor') {
      factorFailure.value = t('profile.lastSecondFactor')
    } else {
      factorFailure.value = t('profile.removeFailed')
    }
  } catch {
    factorFailure.value = t('common.backendUnreachable')
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (session.me) {
    await loadFactors()
  }
})

async function logOut() {
  await endSession(props.fetchFn)
  await router.push('/login')
}
</script>

<template>
  <main class="mx-auto flex max-w-xl flex-col gap-6 p-8">
    <h1 class="text-2xl font-semibold text-highlighted">{{ t('profile.title') }}</h1>

    <UAlert
      v-if="session.loaded && !me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />

    <template v-if="me">
      <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2">
        <dt class="text-muted">{{ t('profile.name') }}</dt>
        <dd data-testid="display-name">{{ me.principal.displayName }}</dd>
        <dt class="text-muted">{{ t('profile.accountType') }}</dt>
        <dd>{{ me.principal.principalType }}</dd>
        <dt class="text-muted">{{ t('profile.secondFactor') }}</dt>
        <dd>
          <UBadge
            data-testid="mfa"
            :color="me.mfaSatisfied ? 'success' : 'warning'"
            variant="subtle"
            :label="me.mfaSatisfied ? t('profile.sessionConfirmed') : t('profile.notConfirmed')"
          />
        </dd>
        <dt class="text-muted">{{ t('profile.roles') }}</dt>
        <dd data-testid="roles">{{ me.roles.map(roleLabel).join(', ') || '–' }}</dd>
        <dt class="text-muted">{{ t('profile.language') }}</dt>
        <dd>
          <USelect
            data-testid="language"
            :model-value="chosenLanguage"
            :items="languageItems"
            :aria-label="t('profile.language')"
            class="w-56"
            @update:model-value="(value: string) => chooseLanguage(value)"
          />
        </dd>
      </dl>

      <UAlert
        v-if="languageFailure"
        data-testid="language-failure"
        color="error"
        variant="subtle"
        :description="languageFailure"
      />

      <UAlert
        v-if="!me.roles.some((grant) => grant.effective)"
        data-testid="no-role"
        color="neutral"
        variant="subtle"
        :title="t('profile.noRole')"
        :description="t('profile.noRoleHint')"
      />

      <section class="flex flex-col gap-3">
        <h2 class="text-lg font-semibold text-highlighted">{{ t('profile.secondFactors') }}</h2>
        <UAlert
          v-if="reauthenticate"
          data-testid="reauthentication-required"
          color="warning"
          variant="subtle"
          :title="t('profile.logInAgain')"
          :description="t('profile.reauthenticate')"
          :actions="[{ label: t('login.submit'), to: '/login' }]"
        />
        <UAlert v-if="factorFailure" data-testid="factor-failure" color="error" variant="subtle" :description="factorFailure" />
        <ul v-if="factors.length" data-testid="second-factors" class="flex flex-col divide-y divide-default">
          <li v-for="factor in factors" :key="factor.credentialId" class="flex items-center justify-between gap-4 py-2">
            <div>
              <p data-testid="factor-label" class="font-medium">{{ factor.label ?? typeName(factor) }}</p>
              <p class="text-sm text-muted">
                {{
                  t('profile.factorDetails', {
                    type: typeName(factor),
                    added: date(factor.createdAt),
                    used: date(factor.lastUsedAt),
                  })
                }}
              </p>
            </div>
            <UButton
              data-testid="remove-factor"
              color="error"
              variant="ghost"
              icon="i-lucide-trash-2"
              :label="t('profile.remove')"
              @click="remove(factor)"
            />
          </li>
        </ul>
        <p v-else data-testid="no-second-factor" class="text-muted">{{ t('profile.noSecondFactor') }}</p>
        <UButton
          data-testid="set-up-second-factor"
          to="/me/second-factor"
          variant="subtle"
          :label="factors.length ? t('profile.addSecondFactor') : t('profile.setUpSecondFactor')"
          class="self-start"
        />
      </section>

      <details>
        <summary class="cursor-pointer text-muted">{{ t('profile.permissions', { count: me.permissions.length }) }}</summary>
        <ul data-testid="permissions" class="mt-2 list-inside list-disc">
          <li v-for="entry in me.permissions" :key="entry.permission">{{ entry.permission }}</li>
        </ul>
      </details>

      <UButton data-testid="logout" color="neutral" variant="outline" :label="t('common.logOut')" @click="logOut" />
    </template>
  </main>
</template>
