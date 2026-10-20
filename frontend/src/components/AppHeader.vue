<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { endSession, refreshSession, session } from '../session/store'

const props = defineProps<{ fetchFn?: typeof fetch }>()
const router = useRouter()

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
        <RouterLink to="/catalogs" data-testid="header-catalogs">Catalogs</RouterLink>
        <RouterLink to="/me" data-testid="header-profile">{{ session.me.principal.displayName }}</RouterLink>
        <UButton data-testid="header-logout" size="sm" color="neutral" variant="ghost" label="Log out" @click="logOut" />
      </template>
      <UButton v-else-if="session.loaded" data-testid="header-login" size="sm" variant="ghost" to="/login" label="Log in" />
    </nav>
  </header>
</template>
