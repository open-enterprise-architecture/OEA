<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOwnSecondFactors, removeOwnSecondFactor, type SecondFactor } from '../api/session'
import { endSession, refreshSession, session } from '../session/store'

// SCR-005 Profile and security settings: GET /me (REQ-003, ADR-032 §7) and the own second factors
// with removal (US-029 AC3; design D7 of us-021-person-totp)
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()

const me = computed(() => session.me)

type RoleGrant = NonNullable<typeof session.me>['roles'][number]

// Source and effectiveness of a role (ADR-032 §7, SoD-8)
function roleLabel(grant: RoleGrant): string {
  const source = grant.source === 'idp' ? ' (identity provider)' : ''
  const state = grant.effective ? '' : grant.notEffectiveReason === 'mfa-required' ? ' – needs a second factor' : ' – not effective'
  return `${grant.role.name}${source}${state}`
}

const factors = ref<SecondFactor[]>([])
const factorFailure = ref<string>()
const reauthenticate = ref(false)

function date(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleDateString() : 'never'
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
      factorFailure.value = 'This is your last authenticator app. Set up another one before you remove it.'
    } else {
      factorFailure.value = 'The authenticator app could not be removed.'
    }
  } catch {
    factorFailure.value = 'The backend is not reachable.'
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
    <h1 class="text-2xl font-semibold text-highlighted">Profile</h1>

    <UAlert
      v-if="session.loaded && !me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      description="You are not logged in."
      :actions="[{ label: 'Log in', to: '/login' }]"
    />

    <template v-if="me">
      <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2">
        <dt class="text-muted">Name</dt>
        <dd data-testid="display-name">{{ me.principal.displayName }}</dd>
        <dt class="text-muted">Account type</dt>
        <dd>{{ me.principal.principalType }}</dd>
        <dt class="text-muted">Second factor</dt>
        <dd>
          <UBadge
            data-testid="mfa"
            :color="me.mfaSatisfied ? 'success' : 'warning'"
            variant="subtle"
            :label="me.mfaSatisfied ? 'Session confirmed' : 'Not confirmed'"
          />
        </dd>
        <dt class="text-muted">Roles</dt>
        <dd data-testid="roles">{{ me.roles.map(roleLabel).join(', ') || '–' }}</dd>
      </dl>

      <UAlert
        v-if="!me.roles.some((grant) => grant.effective)"
        data-testid="no-role"
        color="neutral"
        variant="subtle"
        title="No role assigned"
        description="You are logged in, but no role is assigned to you yet. Ask an administrator for access."
      />

      <section class="flex flex-col gap-3">
        <h2 class="text-lg font-semibold text-highlighted">Authenticator apps</h2>
        <UAlert
          v-if="reauthenticate"
          data-testid="reauthentication-required"
          color="warning"
          variant="subtle"
          title="Log in again"
          description="Removing an authenticator app needs a login within the last 5 minutes."
          :actions="[{ label: 'Log in', to: '/login' }]"
        />
        <UAlert v-if="factorFailure" data-testid="factor-failure" color="error" variant="subtle" :description="factorFailure" />
        <ul v-if="factors.length" data-testid="second-factors" class="flex flex-col divide-y divide-default">
          <li v-for="factor in factors" :key="factor.credentialId" class="flex items-center justify-between gap-4 py-2">
            <div>
              <p data-testid="factor-label" class="font-medium">{{ factor.label ?? 'Authenticator app' }}</p>
              <p class="text-sm text-muted">
                Added {{ date(factor.createdAt) }} · last used {{ date(factor.lastUsedAt) }}
              </p>
            </div>
            <UButton
              data-testid="remove-factor"
              color="error"
              variant="ghost"
              icon="i-lucide-trash-2"
              label="Remove"
              @click="remove(factor)"
            />
          </li>
        </ul>
        <p v-else data-testid="no-second-factor" class="text-muted">No authenticator app is set up.</p>
        <UButton
          data-testid="set-up-second-factor"
          to="/me/second-factor"
          variant="subtle"
          :label="factors.length ? 'Add an authenticator app' : 'Set up a second factor'"
          class="self-start"
        />
      </section>

      <details>
        <summary class="cursor-pointer text-muted">Permissions ({{ me.permissions.length }})</summary>
        <ul data-testid="permissions" class="mt-2 list-inside list-disc">
          <li v-for="entry in me.permissions" :key="entry.permission">{{ entry.permission }}</li>
        </ul>
      </details>

      <UButton data-testid="logout" color="neutral" variant="outline" label="Log out" @click="logOut" />
    </template>
  </main>
</template>
