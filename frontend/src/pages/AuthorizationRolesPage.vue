<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import { fetchAuthorizationRoles, fetchPermissions, type AuthorizationRole, type Permission } from '../api/roles'
import RoleCreateDialog from '../components/roles/RoleCreateDialog.vue'
import { refreshSession, session } from '../session/store'

// SCR-039 "Authorization roles" (UC-23 A5, US-151; design D5 of us-128-override-role-list): the
// built-in and custom roles with their permissions and markers; with role:assign a custom role
// is created in a dialog.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const roles = ref<AuthorizationRole[]>([])
const permissions = ref<Permission[]>([])
const loaded = ref(false)
const failure = ref<string>()
const creating = ref(false)

const canAssign = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'role:assign'))

async function load() {
  failure.value = undefined
  try {
    const [allRoles, catalog] = await Promise.all([fetchAuthorizationRoles(props.fetchFn), fetchPermissions(props.fetchFn)])
    roles.value = allRoles
    permissions.value = catalog
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('roles.loadFailed')
  } finally {
    loaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canAssign.value) {
    loaded.value = true
    return
  }
  await load()
})

async function created() {
  creating.value = false
  await load()
}
</script>

<template>
  <main class="mx-auto flex max-w-5xl flex-col gap-6 p-8">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex flex-col gap-1">
        <h1 class="text-2xl font-semibold text-highlighted">{{ t('roles.title') }}</h1>
        <p class="text-sm text-muted">{{ t('roles.intro') }}</p>
      </div>
      <UButton
        v-if="canAssign && loaded && !failure"
        data-testid="role-new"
        icon="i-lucide-plus"
        :label="t('roles.new')"
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
      v-else-if="session.me && !canAssign"
      data-testid="no-roles-access"
      color="neutral"
      variant="subtle"
      :description="t('roles.noAccess')"
    />
    <UAlert v-if="failure" data-testid="roles-load-failure" color="error" variant="subtle" :description="failure" />

    <div v-if="canAssign && loaded && !failure" class="overflow-x-auto rounded-lg border border-default">
      <table class="w-full text-left text-sm" data-testid="roles-table">
        <thead class="bg-elevated text-toned">
          <tr>
            <th class="px-3 py-2 font-medium">{{ t('roles.role') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('roles.permissions') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('roles.markers') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-default">
          <tr v-for="role in roles" :key="role.id" data-testid="role-row">
            <td class="px-3 py-2 align-top">
              <div class="font-medium">{{ role.name }}</div>
              <div class="font-mono text-xs text-muted">{{ role.id }}</div>
              <div v-if="role.description" class="text-xs text-muted">{{ role.description }}</div>
            </td>
            <td class="px-3 py-2 align-top">
              <span v-if="role.permissions.length === 0" class="text-muted">{{ t('roles.noPermissions') }}</span>
              <span v-else class="flex flex-wrap gap-1">
                <UBadge
                  v-for="permission in role.permissions"
                  :key="permission"
                  size="sm"
                  color="neutral"
                  variant="subtle"
                  :label="permission"
                />
              </span>
            </td>
            <td class="px-3 py-2 align-top">
              <span class="flex flex-wrap gap-1">
                <UBadge v-if="role.isBuiltin" data-testid="role-builtin" size="sm" color="neutral" variant="outline" :label="t('roles.builtin')" />
                <UBadge
                  v-if="role.privileged"
                  data-testid="role-privileged"
                  size="sm"
                  color="warning"
                  variant="subtle"
                  icon="i-lucide-shield-alert"
                  :label="t('roles.privileged')"
                />
                <UBadge
                  v-if="role.overridesPropertyVisibility"
                  data-testid="role-override"
                  size="sm"
                  color="error"
                  variant="subtle"
                  icon="i-lucide-eye"
                  :label="t('roles.override')"
                />
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <UModal :open="creating" :title="t('roles.new')" @update:open="(open: boolean) => (creating = open)">
      <template #body>
        <RoleCreateDialog v-if="creating" :permissions="permissions" :fetch-fn="fetchFn" @created="created" @cancel="creating = false" />
      </template>
    </UModal>
  </main>
</template>
