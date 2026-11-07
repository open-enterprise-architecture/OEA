<script setup lang="ts">
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
</script>

<template>
  <UApp>
    <AppHeader />
    <div class="flex min-h-[calc(100dvh-3.5rem)]">
      <AppSidebar />
      <div class="min-w-0 flex-1">
        <RouterView />
      </div>
    </div>
  </UApp>
</template>
