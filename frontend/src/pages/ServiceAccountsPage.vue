<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import type { AuthorizationRole } from '../api/roles'
import {
  deactivateServiceAccount,
  fetchAssignableRoles,
  fetchServiceAccounts,
  rotateServiceAccountSecret,
  type ServiceAccount,
  type ServiceAccountCreated,
} from '../api/service-accounts'
import ServiceAccountDialog from '../components/service-accounts/ServiceAccountDialog.vue'
import { rejectionMessage } from '../components/service-accounts/messages'
import { currentLanguage } from '../i18n'
import { refreshSession, session } from '../session/store'

// SCR-041 "Service accounts" (US-142, US-149, REQ-155, REQ-176; design D8 of
// us-142-service-accounts): the accounts with status, roles, last use and secret expiry, marked
// when the secret expires within 14 days; creating with the secret shown once, editing the
// roles, rotating the secret and the final deactivation. Needs service-account:manage.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

type Confirmation = { kind: 'rotate' | 'deactivate'; account: ServiceAccount }

const accounts = ref<ServiceAccount[]>([])
const roles = ref<AuthorizationRole[]>([])
const loaded = ref(false)
const failure = ref<string>()
const creating = ref(false)
const editing = ref<ServiceAccount>()
const confirming = ref<Confirmation>()
const confirmFailure = ref<string>()
const working = ref(false)
// the plaintext secret lives only here and is forgotten when its dialog closes
const secret = ref<{ name: string; value: string }>()
const copied = ref(false)

const canManage = computed(() =>
  (session.me?.permissions ?? []).some((entry) => entry.permission === 'service-account:manage'),
)
const roleNames = computed(() => new Map(roles.value.map((role) => [role.id, role.name])))

async function load() {
  failure.value = undefined
  try {
    const [list, assignable] = await Promise.all([fetchServiceAccounts(props.fetchFn), fetchAssignableRoles(props.fetchFn)])
    accounts.value = list
    roles.value = assignable
  } catch (error) {
    failure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('serviceAccounts.loadFailed')
  } finally {
    loaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canManage.value) {
    loaded.value = true
    return
  }
  await load()
})

function show(created: ServiceAccountCreated) {
  copied.value = false
  secret.value = { name: created.name, value: created.clientSecret }
}

async function created(account: ServiceAccountCreated) {
  creating.value = false
  show(account)
  await load()
}

async function updated() {
  editing.value = undefined
  await load()
}

async function confirm() {
  const current = confirming.value
  if (!current) {
    return
  }
  confirmFailure.value = undefined
  working.value = true
  try {
    if (current.kind === 'rotate') {
      const outcome = await rotateServiceAccountSecret(current.account.id, props.fetchFn)
      if (!outcome.ok) {
        confirmFailure.value = rejectionMessage(outcome.rejection)
        return
      }
      confirming.value = undefined
      show(outcome.value)
    } else {
      const outcome = await deactivateServiceAccount(current.account.id, props.fetchFn)
      if (!outcome.ok) {
        confirmFailure.value = rejectionMessage(outcome.rejection)
        return
      }
      confirming.value = undefined
    }
    await load()
  } catch {
    confirmFailure.value = t('serviceAccounts.failed')
  } finally {
    working.value = false
  }
}

function closeConfirmation() {
  confirming.value = undefined
  confirmFailure.value = undefined
}

function closeSecret() {
  secret.value = undefined
  copied.value = false
}

async function copy() {
  if (secret.value) {
    await navigator.clipboard?.writeText(secret.value.value)
    copied.value = true
  }
}

function time(value: string | null | undefined): string {
  return value ? new Intl.DateTimeFormat(currentLanguage(), { dateStyle: 'medium' }).format(new Date(value)) : '—'
}
</script>

<template>
  <main class="mx-auto flex max-w-6xl flex-col gap-6 p-8">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex flex-col gap-1">
        <h1 class="text-2xl font-semibold text-highlighted">{{ t('serviceAccounts.title') }}</h1>
        <p class="text-sm text-muted">{{ t('serviceAccounts.intro') }}</p>
      </div>
      <UButton
        v-if="canManage && loaded && !failure"
        data-testid="service-account-new"
        icon="i-lucide-plus"
        :label="t('serviceAccounts.new')"
        @click="creating = true"
      />
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
      v-else-if="session.me && !canManage"
      data-testid="no-service-accounts-access"
      color="neutral"
      variant="subtle"
      :description="t('serviceAccounts.noAccess')"
    />
    <UAlert v-if="failure" data-testid="service-accounts-load-failure" color="error" variant="subtle" :description="failure" />

    <p v-if="canManage && loaded && !failure && accounts.length === 0" data-testid="service-accounts-empty" class="text-sm text-muted">
      {{ t('serviceAccounts.empty') }}
    </p>

    <div v-if="canManage && loaded && !failure && accounts.length > 0" class="overflow-x-auto rounded-lg border border-default">
      <table class="w-full text-left text-sm" data-testid="service-accounts-table">
        <thead class="bg-elevated text-toned">
          <tr>
            <th class="whitespace-nowrap px-3 py-2 font-medium">{{ t('serviceAccounts.name') }}</th>
            <th class="whitespace-nowrap px-3 py-2 font-medium">{{ t('serviceAccounts.status') }}</th>
            <th class="whitespace-nowrap px-3 py-2 font-medium">{{ t('serviceAccounts.roles') }}</th>
            <th class="whitespace-nowrap px-3 py-2 font-medium">{{ t('serviceAccounts.lastUsed') }}</th>
            <th class="whitespace-nowrap px-3 py-2 font-medium">{{ t('serviceAccounts.expires') }}</th>
            <th class="whitespace-nowrap px-3 py-2 font-medium"><span class="sr-only">{{ t('serviceAccounts.actions') }}</span></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-default">
          <tr v-for="account in accounts" :key="account.id" data-testid="service-account-row" :data-name="account.name">
            <td class="px-3 py-2 align-top">
              <div class="font-medium">{{ account.name }}</div>
              <div class="font-mono text-xs text-muted" :title="account.id">{{ t('serviceAccounts.clientId') }}: {{ account.id }}</div>
              <div v-if="account.description" class="text-xs text-muted">{{ account.description }}</div>
            </td>
            <td class="px-3 py-2 align-top">
              <UBadge
                data-testid="service-account-status"
                size="sm"
                :color="account.status === 'active' ? 'success' : 'neutral'"
                variant="subtle"
                :label="t(`serviceAccounts.statusValue.${account.status}`)"
              />
            </td>
            <td class="px-3 py-2 align-top">
              <span v-if="account.roles.length === 0" class="text-muted">{{ t('serviceAccounts.noRoles') }}</span>
              <span v-else class="flex flex-wrap gap-1">
                <UBadge
                  v-for="role in account.roles"
                  :key="role"
                  size="sm"
                  color="neutral"
                  variant="subtle"
                  :label="roleNames.get(role) ?? role"
                />
              </span>
            </td>
            <td class="px-3 py-2 align-top" data-testid="service-account-last-used">
              {{ account.lastUsedAt ? time(account.lastUsedAt) : t('serviceAccounts.neverUsed') }}
            </td>
            <td class="px-3 py-2 align-top">
              <span class="flex flex-wrap items-center gap-1" data-testid="service-account-expiry">
                {{ time(account.secretExpiresAt) }}
                <UBadge
                  v-if="account.secretExpiresSoon"
                  data-testid="service-account-expires-soon"
                  size="sm"
                  color="warning"
                  variant="subtle"
                  icon="i-lucide-clock-alert"
                  :label="t('serviceAccounts.expiresSoon')"
                />
              </span>
            </td>
            <td class="px-3 py-2 text-right align-top">
              <span v-if="account.status === 'active'" class="flex justify-end gap-1">
                <UButton
                  data-testid="service-account-edit-roles"
                  size="sm"
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-shield"
                  :label="t('serviceAccounts.editRoles')"
                  @click="editing = account"
                />
                <UButton
                  data-testid="service-account-rotate"
                  size="sm"
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-rotate-cw"
                  :label="t('serviceAccounts.rotate')"
                  @click="confirming = { kind: 'rotate', account }"
                />
                <UButton
                  data-testid="service-account-deactivate"
                  size="sm"
                  color="error"
                  variant="ghost"
                  icon="i-lucide-power"
                  :label="t('serviceAccounts.deactivate')"
                  @click="confirming = { kind: 'deactivate', account }"
                />
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <UModal :open="creating" :title="t('serviceAccounts.new')" @update:open="(open: boolean) => (creating = open)">
      <template #body>
        <ServiceAccountDialog v-if="creating" :roles="roles" :fetch-fn="fetchFn" @created="created" @cancel="creating = false" />
      </template>
    </UModal>

    <UModal
      :open="editing !== undefined"
      :title="t('serviceAccounts.editRoles')"
      @update:open="(open: boolean) => { if (!open) editing = undefined }"
    >
      <template #body>
        <ServiceAccountDialog
          v-if="editing"
          :roles="roles"
          :account="editing"
          :fetch-fn="fetchFn"
          @updated="updated"
          @cancel="editing = undefined"
        />
      </template>
    </UModal>

    <UModal
      :open="confirming !== undefined"
      :title="confirming?.kind === 'rotate' ? t('serviceAccounts.rotateTitle') : t('serviceAccounts.deactivateTitle')"
      @update:open="(open: boolean) => { if (!open) closeConfirmation() }"
    >
      <template #body>
        <div v-if="confirming" class="flex flex-col gap-4" data-testid="service-account-confirm">
          <p class="text-sm">
            {{
              confirming.kind === 'rotate'
                ? t('serviceAccounts.rotateConfirm', { name: confirming.account.name })
                : t('serviceAccounts.deactivateConfirm', { name: confirming.account.name })
            }}
          </p>
          <UAlert v-if="confirmFailure" data-testid="service-account-confirm-failure" color="error" variant="subtle" :description="confirmFailure" />
          <div class="flex justify-end gap-2">
            <UButton data-testid="service-account-confirm-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="closeConfirmation" />
            <UButton
              data-testid="service-account-confirm-ok"
              :color="confirming.kind === 'deactivate' ? 'error' : 'primary'"
              :loading="working"
              :label="confirming.kind === 'rotate' ? t('serviceAccounts.rotate') : t('serviceAccounts.deactivate')"
              @click="confirm"
            />
          </div>
        </div>
      </template>
    </UModal>

    <UModal
      :open="secret !== undefined"
      :title="t('serviceAccounts.secretTitle')"
      :dismissible="false"
      @update:open="(open: boolean) => { if (!open) closeSecret() }"
    >
      <template #body>
        <div v-if="secret" class="flex flex-col gap-4" data-testid="service-account-secret">
          <UAlert color="warning" variant="subtle" icon="i-lucide-triangle-alert" :description="t('serviceAccounts.secretOnce', { name: secret.name })" />
          <UFormField :label="t('serviceAccounts.secret')">
            <div class="flex gap-2">
              <UInput :model-value="secret.value" data-testid="service-account-secret-value" class="w-full font-mono" readonly />
              <UButton
                data-testid="service-account-secret-copy"
                color="neutral"
                variant="outline"
                :icon="copied ? 'i-lucide-check' : 'i-lucide-copy'"
                :label="copied ? t('serviceAccounts.copied') : t('serviceAccounts.copy')"
                @click="copy"
              />
            </div>
          </UFormField>
          <div class="flex justify-end">
            <UButton data-testid="service-account-secret-close" :label="t('serviceAccounts.secretStored')" @click="closeSecret" />
          </div>
        </div>
      </template>
    </UModal>
  </main>
</template>
