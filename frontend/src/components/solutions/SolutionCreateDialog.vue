<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { rateLimitMessage, type Rejection } from '../../api/problem'
import { fetchPlateaus, plannedPlateaus, type Plateau } from '../../api/plateaus'
import { createSolution, type Solution } from '../../api/solutions'

// The dialog "New solution" of SCR-060 (US-038 AC1, AC2; design D4 of us-038-create-solution):
// name and architecture vision. A rejection keeps the dialog open and says why. When plateaus
// exist, the solution may target a target or transition plateau and then starts from its
// predecessor (US-038 AC3; design D8 of us-084-plateaus).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const emit = defineEmits<{ created: [solution: Solution]; cancel: [] }>()
const { t } = useI18n()

const NAME_MAX = 255
const VISION_MAX = 2000

const name = ref('')
const vision = ref('')
const failure = ref<string>()
const nameError = ref<string>()
const saving = ref(false)
// USelect does not accept an empty value, so project mode has its own value
const NONE = 'none'
const plateaus = ref<Plateau[]>([])
const target = ref(NONE)
const planned = computed(() => plannedPlateaus(plateaus.value))
const targetItems = computed(() => [
  { value: NONE, label: t('solutions.plateaus.none') },
  ...planned.value.map((plateau) => ({ value: plateau.id, label: plateau.name })),
])
const start = computed(() => {
  const chosen = planned.value.find((plateau) => plateau.id === target.value)
  return chosen ? plateaus.value.find((plateau) => plateau.id === chosen.succeeds) : undefined
})

onMounted(async () => {
  try {
    plateaus.value = await fetchPlateaus(props.fetchFn)
  } catch {
    // without plateaus the solution is created in project mode
  }
})

const nameValid = computed(() => name.value.trim().length > 0 && name.value.trim().length <= NAME_MAX)
const visionValid = computed(() => vision.value.length <= VISION_MAX)

function message(rejection: Rejection): string | undefined {
  if (rejection.status === 422 && rejection.invalidFields.includes('name')) {
    nameError.value = t('solutions.nameTaken', { name: name.value.trim() })
    return undefined
  }
  if (rejection.status === 403) {
    return t('solutions.forbidden')
  }
  return rateLimitMessage(rejection) ?? t('solutions.createFailed')
}

async function save() {
  failure.value = undefined
  nameError.value = undefined
  if (!nameValid.value || !visionValid.value) {
    return
  }
  saving.value = true
  try {
    const outcome = await createSolution(
      {
        name: name.value.trim(),
        ...(vision.value.trim() ? { description: vision.value } : {}),
        ...(start.value ? { fromPlateauId: start.value.id, toPlateauId: target.value } : {}),
      },
      props.fetchFn,
    )
    if (outcome.ok) {
      emit('created', outcome.value)
      return
    }
    failure.value = message(outcome.rejection)
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="solution-dialog" @submit.prevent="save">
    <UFormField :label="t('solutions.name')" :error="nameError" required>
      <UInput v-model="name" data-testid="solution-name" class="w-full" :maxlength="NAME_MAX" autofocus />
    </UFormField>
    <UFormField
      :label="t('solutions.vision')"
      :help="t('solutions.visionHint')"
      :error="visionValid ? undefined : t('solutions.visionTooLong', { max: VISION_MAX })"
    >
      <UTextarea v-model="vision" data-testid="solution-vision" class="w-full" :rows="6" autoresize />
      <template #hint>
        <span data-testid="solution-vision-count" :class="visionValid ? 'text-muted' : 'text-error'"
          >{{ vision.length }} / {{ VISION_MAX }}</span
        >
      </template>
    </UFormField>

    <template v-if="planned.length > 0">
      <UFormField :label="t('solutions.plateaus.target')" :help="t('solutions.plateaus.targetHint')">
        <USelect v-model="target" data-testid="solution-target-plateau" :items="targetItems" class="w-full" />
      </UFormField>
      <p v-if="start" data-testid="solution-start-plateau" class="text-sm text-muted">
        {{ t('solutions.plateaus.start', { name: start.name }) }}
      </p>
    </template>

    <UAlert v-if="failure" data-testid="solution-failure" color="error" variant="subtle" :description="failure" />

    <div class="flex justify-end gap-2">
      <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton
        type="submit"
        data-testid="solution-save"
        :disabled="!nameValid || !visionValid"
        :loading="saving"
        :label="t('solutions.create')"
      />
    </div>
  </form>
</template>
