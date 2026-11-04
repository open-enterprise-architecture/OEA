<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { endSession, refreshSession, session } from '../session/store'

const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()
const { t } = useI18n()

// SCR-030 is readable with entity:read and changeable with metamodel:write
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
    <RouterLink to="/" class="font-semibold">OEA</RouterLink>
    <nav class="flex items-center gap-3">
      <template v-if="session.me">
        <RouterLink to="/catalogs" data-testid="header-catalogs">{{ t('catalogs.title') }}</RouterLink>
        <RouterLink v-if="canSeeMetamodel" to="/metamodel" data-testid="header-metamodel">{{ t('metamodel.title') }}</RouterLink>
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
