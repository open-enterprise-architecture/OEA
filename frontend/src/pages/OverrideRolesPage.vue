<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { fetchOverrideRoles, fetchRoles, updateOverrideRoles, type AuthorizationRole } from '../api/visibility'
import { refreshSession, session } from '../session/store'

// SCR-040 "Override roles" (UC-21, US-128, REQ-128; design D5 of us-128-override-role-list):
// security administration (visibility:configure) chooses the roles whose holders read every
// property of the objects they can read. Every such read is audited; the list is empty by default.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const roles = ref<AuthorizationRole[]>([])
const stored = ref<string[]>([])
const chosen = ref<string[]>([])
const loaded = ref(false)
const failure = ref<string>()
const saveFailure = ref<string>()
const saving = ref(false)
const saved = ref(false)

const canConfigure = computed(() =>
  (session.me?.permissions ?? []).some((entry) => entry.permission === 'visibility:configure'),
)
const changed = computed(() => [...chosen.value].sort().join(',') !== [...stored.value].sort().join(','))

async function load() {
  failure.value = undefined
  try {
    const [allRoles, list] = await Promise.all([fetchRoles(props.fetchFn), fetchOverrideRoles(props.fetchFn)])
    roles.value = allRoles
    stored.value = list
    chosen.value = [...list]
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('override.loadFailed')
  } finally {
    loaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canConfigure.value) {
    loaded.value = true
    return
  }
  await load()
})

function toggle(role: string, on: boolean) {
  saved.value = false
  const index = chosen.value.indexOf(role)
  if (on && index < 0) {
    chosen.value.push(role)
  } else if (!on && index >= 0) {
    chosen.value.splice(index, 1)
  }
}

async function save() {
  saveFailure.value = undefined
  saving.value = true
  try {
    const outcome = await updateOverrideRoles([...chosen.value], props.fetchFn)
    if (outcome.ok) {
      stored.value = outcome.value
      chosen.value = [...outcome.value]
      saved.value = true
      return
    }
    // a rejected change keeps the stored list
    chosen.value = [...stored.value]
    saveFailure.value = rateLimitMessage(outcome.rejection) ?? t('override.saveFailed')
  } catch {
    chosen.value = [...stored.value]
    saveFailure.value = t('override.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex max-w-3xl flex-col gap-6 p-8">
    <div class="flex flex-col gap-1">
      <h1 class="text-2xl font-semibold text-highlighted">{{ t('override.title') }}</h1>
      <p class="text-sm text-muted">{{ t('override.intro') }}</p>
    </div>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert
      v-else-if="session.me && !canConfigure"
      data-testid="no-override-access"
      color="neutral"
      variant="subtle"
      :description="t('visibility.noAccess')"
    />
    <UAlert v-if="failure" data-testid="override-load-failure" color="error" variant="subtle" :description="failure" />

    <form v-if="canConfigure && loaded && !failure" class="flex flex-col gap-4" data-testid="override-form" @submit.prevent="save">
      <UAlert
        data-testid="override-audit-warning"
        color="warning"
        variant="subtle"
        icon="i-lucide-eye"
        :description="t('override.auditWarning')"
      />
      <UAlert
        v-if="stored.length === 0"
        data-testid="override-empty"
        color="info"
        variant="subtle"
        :description="t('override.empty')"
      />
      <UFormField :label="t('override.roles')" data-testid="override-roles">
        <div class="grid grid-cols-2 gap-1">
          <UCheckbox
            v-for="role in roles"
            :key="role.id"
            :data-testid="`override-role-${role.id}`"
            :label="role.name"
            :model-value="chosen.includes(role.id)"
            @update:model-value="(on: boolean | 'indeterminate') => toggle(role.id, on === true)"
          />
        </div>
      </UFormField>
      <UAlert v-if="saveFailure" data-testid="override-failure" color="error" variant="subtle" :description="saveFailure" />
      <p v-if="saved" data-testid="override-saved" class="text-sm text-success">{{ t('override.saved') }}</p>
      <div class="flex justify-end">
        <UButton type="submit" data-testid="override-save" :disabled="!changed" :loading="saving" :label="t('override.save')" />
      </div>
    </form>
  </main>
</template>
