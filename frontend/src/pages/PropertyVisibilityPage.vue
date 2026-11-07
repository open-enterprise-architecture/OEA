<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchMetatypes } from '../api/metamodel'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import {
  fetchPropertyVisibility,
  fetchRoles,
  type AuthorizationRole,
  type PropertyVisibilityEntry,
} from '../api/visibility'
import PropertyVisibilityDialog from '../components/visibility/PropertyVisibilityDialog.vue'
import { label, labelKeys } from '../i18n/labels'
import { refreshSession, session } from '../session/store'

// SCR-032 "Property visibility" (UC-21, US-124, US-125; design D5 of us-124-property-visibility):
// security administration (visibility:configure) sees and changes who may read and write the
// values of each property definition.
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const entries = ref<PropertyVisibilityEntry[]>([])
const roles = ref<AuthorizationRole[]>([])
const connectionTypes = ref<{ id: string; name: string }[]>([])
const loaded = ref(false)
const failure = ref<string>()
const filter = ref('')
const editing = ref<PropertyVisibilityEntry>()

const canConfigure = computed(() =>
  (session.me?.permissions ?? []).some((entry) => entry.permission === 'visibility:configure'),
)

const shown = computed(() => {
  const text = filter.value.trim().toLowerCase()
  return entries.value.filter((entry) => !text || (entry.propertyName ?? '').toLowerCase().includes(text))
})

const roleNames = computed(() => new Map(roles.value.map((role) => [role.id, role.name])))
const typeNames = computed(() => new Map(connectionTypes.value.map((type) => [type.id, type.name])))

function propertyLabel(entry: PropertyVisibilityEntry): string {
  return label(labelKeys.property(entry.propertyDefId), entry.propertyName ?? entry.propertyDefId)
}

function restriction(entry: PropertyVisibilityEntry): string {
  if (entry.visibilityMode === 'role-restricted') {
    const read = (entry.readRoles ?? []).map((role) => roleNames.value.get(role) ?? role).join(', ')
    const write = (entry.writeRoles ?? []).map((role) => roleNames.value.get(role) ?? role).join(', ')
    return t('visibility.rolesSummary', { read, write: write || t('visibility.nobody') })
  }
  if (entry.visibilityMode === 'connection-scoped' && entry.scopingConnectionType) {
    return typeNames.value.get(entry.scopingConnectionType) ?? entry.scopingConnectionType
  }
  return ''
}

async function load() {
  failure.value = undefined
  try {
    const [visibility, allRoles, metatypes] = await Promise.all([
      fetchPropertyVisibility(props.fetchFn),
      fetchRoles(props.fetchFn),
      fetchMetatypes(props.fetchFn),
    ])
    entries.value = visibility
    roles.value = allRoles
    connectionTypes.value = metatypes
      .filter((metatype) => metatype.isConnection)
      .map((metatype) => ({ id: metatype.id, name: label(labelKeys.metatype(metatype.id), metatype.name) }))
  } catch (error) {
    failure.value =
      rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('visibility.loadFailed')
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

function saved(changed: PropertyVisibilityEntry[]) {
  for (const entry of changed) {
    const index = entries.value.findIndex((candidate) => candidate.propertyDefId === entry.propertyDefId)
    if (index >= 0) {
      entries.value[index] = entry
    }
  }
  editing.value = undefined
}
</script>

<template>
  <main class="mx-auto flex max-w-5xl flex-col gap-6 p-8">
    <div class="flex flex-col gap-1">
      <h1 class="text-2xl font-semibold text-highlighted">{{ t('visibility.title') }}</h1>
      <p class="text-sm text-muted">{{ t('visibility.intro') }}</p>
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
      data-testid="no-visibility-access"
      color="neutral"
      variant="subtle"
      :description="t('visibility.noAccess')"
    />
    <UAlert v-if="failure" data-testid="visibility-load-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="canConfigure && loaded && !failure">
      <UAlert data-testid="visibility-enforcement-note" color="info" variant="subtle" :description="t('visibility.enforcementNote')" />
      <UInput v-model="filter" data-testid="visibility-filter" icon="i-lucide-search" :placeholder="t('visibility.filter')" class="w-72" />
      <div class="overflow-x-auto rounded-lg border border-default">
        <table class="w-full text-left text-sm" data-testid="visibility-table">
          <thead class="bg-elevated text-toned">
            <tr>
              <th class="px-3 py-2 font-medium">{{ t('visibility.property') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('visibility.metatypes') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('visibility.mode.title') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('visibility.restriction') }}</th>
              <th class="px-3 py-2"><span class="sr-only">{{ t('visibility.actions') }}</span></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-default">
            <tr v-for="entry in shown" :key="entry.propertyDefId" data-testid="visibility-row">
              <td class="px-3 py-2 font-medium">{{ propertyLabel(entry) }}</td>
              <td class="px-3 py-2 text-muted">{{ (entry.metatypes ?? []).join(', ') || '–' }}</td>
              <td class="px-3 py-2">
                <span class="flex items-center gap-2">
                  {{ t(`visibility.mode.${entry.visibilityMode}`) }}
                  <UBadge
                    v-if="entry.visibilityMode !== 'public'"
                    data-testid="visibility-restricted"
                    size="sm"
                    color="warning"
                    variant="subtle"
                    icon="i-lucide-lock"
                    :label="t('visibility.restricted')"
                  />
                </span>
              </td>
              <td class="px-3 py-2 text-muted">{{ restriction(entry) }}</td>
              <td class="px-3 py-2 text-right">
                <UButton
                  data-testid="visibility-edit"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-pencil"
                  :aria-label="t('visibility.edit', { name: propertyLabel(entry) })"
                  @click="editing = entry"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <UModal
      :open="editing !== undefined"
      :title="editing ? t('visibility.editTitle', { name: propertyLabel(editing) }) : ''"
      @update:open="(open: boolean) => !open && (editing = undefined)"
    >
      <template #body>
        <PropertyVisibilityDialog
          v-if="editing"
          :entry="editing"
          :roles="roles"
          :connection-types="connectionTypes"
          :fetch-fn="fetchFn"
          @saved="saved"
          @cancel="editing = undefined"
        />
      </template>
    </UModal>
  </main>
</template>
