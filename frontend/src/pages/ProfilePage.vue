<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { endSession, refreshSession, session } from '../session/store'

// Minimal profile view from GET /me (REQ-003, ADR-032 §7)
const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()

const me = computed(() => session.me)

onMounted(() => refreshSession(props.fetchFn))

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
        <dd data-testid="roles">{{ me.roles.map((grant) => grant.role.name).join(', ') || '–' }}</dd>
      </dl>

      <UButton
        v-if="!me.mfaSatisfied"
        data-testid="set-up-second-factor"
        to="/me/second-factor"
        variant="subtle"
        label="Set up a second factor"
      />

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
