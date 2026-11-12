<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { sidebar } from '../navigation/store'
import { endSession, refreshSession, session } from '../session/store'

const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

// SCR-030 is readable with entity:read and changeable with metamodel:write
// the sidebar with the navigation tree needs entity:read; on narrow screens this button opens it (SCR-055)
const canSeeTree = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:read'))

// the portal needs portal:read, the catalogs entity:read (design D7 of s9-portal-catalogs), and
// the solutions entity:read as well (design D4 of us-038-create-solution)
const canSeePortal = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'portal:read'))

// SCR-032 needs visibility:configure (design D5 of us-124-property-visibility)
const canConfigureVisibility = computed(() =>
  (session.me?.permissions ?? []).some((entry) => entry.permission === 'visibility:configure'),
)

// SCR-039 needs role:assign (design D5 of us-128-override-role-list)
const canAssignRoles = computed(() => (session.me?.permissions ?? []).some((entry) => entry.permission === 'role:assign'))

const canSeeMetamodel = computed(() =>
  (session.me?.permissions ?? []).some((entry) => entry.permission === 'entity:read' || entry.permission === 'metamodel:write'),
)

onMounted(() => {
  if (!session.loaded) {
    void refreshSession(props.fetchFn)
  }
})

async function logOut() {
  await endSession(props.fetchFn)
  await router.push('/login')
}
</script>

<template>
  <header class="flex items-center justify-between border-b border-default px-6 py-3">
    <div class="flex items-center gap-2">
      <UButton
        v-if="canSeeTree"
        data-testid="header-navigation"
        class="lg:hidden"
        size="sm"
        color="neutral"
        variant="ghost"
        icon="i-lucide-menu"
        :aria-label="t('navigation.open')"
        @click="sidebar.open = true"
      />
      <RouterLink to="/" class="font-semibold">OEA</RouterLink>
    </div>
    <nav class="flex items-center gap-3">
      <template v-if="session.me">
        <RouterLink v-if="canSeePortal" to="/portal" data-testid="header-portal">{{ t('portal.title') }}</RouterLink>
        <RouterLink v-if="canSeeTree" to="/catalogs" data-testid="header-catalogs">{{ t('catalogs.title') }}</RouterLink>
        <RouterLink v-if="canSeeTree" to="/solutions" data-testid="header-solutions">{{ t('solutions.title') }}</RouterLink>
        <RouterLink v-if="canSeeMetamodel" to="/metamodel" data-testid="header-metamodel">{{ t('metamodel.title') }}</RouterLink>
        <RouterLink v-if="canConfigureVisibility" to="/admin/property-visibility" data-testid="header-visibility">{{
          t('visibility.title')
        }}</RouterLink>
        <RouterLink v-if="canConfigureVisibility" to="/admin/override-roles" data-testid="header-override">{{
          t('override.title')
        }}</RouterLink>
        <RouterLink v-if="canAssignRoles" to="/admin/roles" data-testid="header-roles">{{ t('roles.title') }}</RouterLink>
        <RouterLink to="/me" data-testid="header-profile">{{ session.me.principal.displayName }}</RouterLink>
        <UButton
          data-testid="header-logout"
          size="sm"
          color="neutral"
          variant="ghost"
          :label="t('common.logOut')"
          @click="logOut"
        />
      </template>
      <UButton
        v-else-if="session.loaded"
        data-testid="header-login"
        size="sm"
        variant="ghost"
        to="/login"
        :label="t('login.submit')"
      />
    </nav>
  </header>
</template>
