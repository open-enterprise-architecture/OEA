<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchHistoryVisibility, updateHistoryVisibility, type HistoryVisibilityEntry, type HistoryVisibilityMode } from '../../api/history'
import { rateLimitMessage, RequestRejected } from '../../api/problem'
import type { AuthorizationRole } from '../../api/visibility'

// The section "Change history" of SCR-032 (US-098; design D5 of us-098-entity-history): per
// metatype, whether a change to a field a role may not read appears as "Restricted" or is hidden
// in the change history. A pair without an entry is "Restricted"; the changed roles are saved
// together, and a rejected entry is shown at its row.
const props = defineProps<{ roles: AuthorizationRole[]; metatypes: { id: string; name: string }[]; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const entries = ref<HistoryVisibilityEntry[]>([])
const metatype = ref<string>()
const draft = reactive<Record<string, HistoryVisibilityMode>>({})
const errors = ref<Record<string, boolean>>({})
const failure = ref<string>()
const saved = ref(false)
const saving = ref(false)

const metatypeItems = computed(() => props.metatypes.map((type) => ({ value: type.id, label: type.name })))
const modeItems = computed(() => [
  { value: 'show-restricted', label: t('visibility.history.restricted') },
  { value: 'hide', label: t('visibility.history.hidden') },
])

/** The stored mode of a role for the chosen metatype. */
function stored(roleId: string): HistoryVisibilityMode {
  return entries.value.some((entry) => entry.roleId === roleId && entry.metatypeId === metatype.value && entry.mode === 'hide')
    ? 'hide'
    : 'show-restricted'
}

const changed = computed(() => props.roles.filter((role) => draft[role.id] !== undefined && draft[role.id] !== stored(role.id)))

function reset() {
  for (const key of Object.keys(draft)) delete draft[key]
  for (const role of props.roles) draft[role.id] = stored(role.id)
  errors.value = {}
}

onMounted(async () => {
  metatype.value = props.metatypes[0]?.id
  try {
    entries.value = await fetchHistoryVisibility(props.fetchFn)
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('visibility.history.loadFailed')
  }
  reset()
})

watch(metatype, () => {
  saved.value = false
  reset()
})

async function save() {
  if (!metatype.value || changed.value.length === 0) return
  const sent = changed.value.map((role) => ({ roleId: role.id, metatypeId: metatype.value!, mode: draft[role.id]! }))
  saving.value = true
  failure.value = undefined
  saved.value = false
  try {
    const result = await updateHistoryVisibility(sent, props.fetchFn)
    if (result.ok) {
      entries.value = result.value
      reset()
      saved.value = true
    } else {
      const invalid: Record<string, boolean> = {}
      for (const field of result.rejection.invalidFields) {
        const match = /^entries\[(\d+)]/.exec(field)
        const entry = match ? sent[Number(match[1])] : undefined
        if (entry) invalid[entry.roleId] = true
      }
      errors.value = invalid
      failure.value = rateLimitMessage(result.rejection) ?? t('visibility.history.saveFailed')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="flex flex-col gap-4" data-testid="history-visibility">
    <div class="flex flex-col gap-1">
      <h2 class="text-lg font-semibold text-highlighted">{{ t('visibility.history.title') }}</h2>
      <p class="text-sm text-muted">{{ t('visibility.history.hint') }}</p>
    </div>
    <UFormField :label="t('visibility.history.metatype')" class="max-w-md">
      <USelect
        v-model="metatype"
        data-testid="history-visibility-metatype"
        :aria-label="t('visibility.history.metatype')"
        :items="metatypeItems"
        class="w-72"
      />
    </UFormField>
    <UAlert v-if="failure" data-testid="history-visibility-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="saved" data-testid="history-visibility-saved" color="success" variant="subtle" :description="t('visibility.history.saved')" />
    <div v-if="metatype" class="overflow-x-auto rounded-lg border border-default">
      <table class="w-full text-left text-sm" data-testid="history-visibility-table">
        <thead class="bg-elevated text-toned">
          <tr>
            <th class="px-3 py-2 font-medium">{{ t('visibility.history.role') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('visibility.history.mode') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-default">
          <tr v-for="role in props.roles" :key="role.id" :data-testid="`history-visibility-${role.id}`">
            <td class="px-3 py-2 font-medium">{{ role.name }}</td>
            <td class="px-3 py-2">
              <UFormField :error="errors[role.id] ? t('visibility.history.invalid') : undefined">
                <URadioGroup
                  v-model="draft[role.id]"
                  :data-testid="`history-visibility-mode-${role.id}`"
                  :aria-label="`${role.name}: ${t('visibility.history.mode')}`"
                  orientation="horizontal"
                  :items="modeItems"
                />
              </UFormField>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div>
      <UButton
        data-testid="history-visibility-save"
        :label="t('visibility.history.save')"
        :loading="saving"
        :disabled="changed.length === 0"
        @click="save"
      />
    </div>
  </section>
</template>
