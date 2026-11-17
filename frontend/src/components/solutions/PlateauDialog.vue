<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createPlateau, updatePlateau, type Plateau, type PlateauBasis } from '../../api/plateaus'
import { rateLimitMessage, type Rejection } from '../../api/problem'

// The dialog "New plateau" and "Edit plateau" of SCR-065 (US-084, US-155; design D8 of
// us-084-plateaus): name, description, planned date, and for a target or transition plateau the
// predecessor (the baseline preselected) and the basis. The status is chosen on creation only; the
// first plateau is the baseline. The predecessor cannot change while solutions target the plateau.
const props = defineProps<{
  plateaus: Plateau[]
  /** the plateau to edit; a new one without */
  plateau?: Plateau
  /** create the initial baseline (UC-11 A1) */
  initial?: boolean
  fetchFn?: typeof fetch
}>()
const emit = defineEmits<{ saved: [plateau: Plateau]; cancel: [] }>()
const { t } = useI18n()

const NAME_MAX = 255
const DESCRIPTION_MAX = 2000

const baseline = computed(() => props.plateaus.find((candidate) => candidate.status === 'baseline'))
const name = ref(props.plateau?.name ?? '')
const description = ref(props.plateau?.description ?? '')
const validFrom = ref(props.plateau?.validFrom ?? '')
const status = ref<Plateau['status']>(props.plateau?.status ?? (props.initial ? 'baseline' : 'target'))
const succeeds = ref<string | undefined>(props.plateau?.succeeds ?? baseline.value?.id)
const basis = ref<PlateauBasis>(props.plateau?.basis ?? 'catalog')
const failure = ref<string>()
const errors = ref<Record<string, string>>({})
const saving = ref(false)

const planned = computed(() => status.value === 'target' || status.value === 'transition')
const statusItems = computed(() => [
  { value: 'target', label: t('plateaus.status.target') },
  { value: 'transition', label: t('plateaus.status.transition') },
])
const predecessorItems = computed(() =>
  props.plateaus
    .filter((candidate) => candidate.status !== 'realized' && candidate.id !== props.plateau?.id)
    .map((candidate) => ({ value: candidate.id, label: candidate.name })),
)
const basisItems = computed(() => [
  { value: 'catalog', label: t('plateaus.basis.catalog'), description: t('plateaus.basis.catalogHint') },
  { value: 'predecessor', label: t('plateaus.basis.predecessor'), description: t('plateaus.basis.predecessorHint') },
])
const predecessorLocked = computed(() => (props.plateau?.solutionCount ?? 0) > 0)
const valid = computed(
  () =>
    name.value.trim().length > 0 &&
    name.value.trim().length <= NAME_MAX &&
    description.value.length <= DESCRIPTION_MAX &&
    (!planned.value || succeeds.value !== undefined),
)

function show(rejection: Rejection) {
  const field = rejection.invalidFields[0]
  if (rejection.status === 422 && field) {
    errors.value = { [field]: rejection.detail ?? t('plateaus.invalid') }
  } else if (rejection.status === 422 || rejection.status === 409) {
    failure.value = rejection.problemType === 'plateau-in-use' ? t('plateaus.inUse') : (rejection.detail ?? t('plateaus.invalid'))
  } else if (rejection.status === 403) {
    failure.value = t('plateaus.forbidden')
  } else {
    failure.value = rateLimitMessage(rejection) ?? t('plateaus.saveFailed')
  }
}

async function save() {
  failure.value = undefined
  errors.value = {}
  if (!valid.value) {
    return
  }
  const fields = {
    name: name.value.trim(),
    description: description.value.trim() ? description.value : null,
    validFrom: validFrom.value || null,
    succeeds: planned.value ? (succeeds.value ?? null) : null,
    basis: planned.value ? basis.value : null,
  }
  saving.value = true
  try {
    const outcome = props.plateau
      ? await updatePlateau(props.plateau.id, fields, props.fetchFn)
      : await createPlateau({ ...fields, status: status.value as 'baseline' | 'target' | 'transition' }, props.fetchFn)
    if (outcome.ok) {
      emit('saved', outcome.value)
      return
    }
    show(outcome.rejection)
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="plateau-dialog" @submit.prevent="save">
    <UFormField :label="t('plateaus.name')" :error="errors.name" required>
      <UInput v-model="name" data-testid="plateau-name" class="w-full" :maxlength="NAME_MAX" autofocus />
    </UFormField>
    <UFormField v-if="!props.plateau && !props.initial" :label="t('plateaus.statusLabel')" :error="errors.status">
      <URadioGroup v-model="status" data-testid="plateau-status" orientation="horizontal" :items="statusItems" />
    </UFormField>
    <UFormField
      :label="t('plateaus.description')"
      :error="description.length <= DESCRIPTION_MAX ? errors.description : t('plateaus.descriptionTooLong', { max: DESCRIPTION_MAX })"
    >
      <UTextarea v-model="description" data-testid="plateau-description" class="w-full" :rows="3" autoresize />
    </UFormField>
    <UFormField :label="t('plateaus.validFrom')" :help="t(planned ? 'plateaus.validFromPlanned' : 'plateaus.validFromBaseline')">
      <UInput v-model="validFrom" data-testid="plateau-valid-from" type="date" class="w-48" />
    </UFormField>
    <template v-if="planned">
      <UFormField
        :label="t('plateaus.succeeds')"
        :help="predecessorLocked ? t('plateaus.succeedsLocked') : undefined"
        :error="errors.succeeds"
        required
      >
        <USelect
          v-model="succeeds"
          data-testid="plateau-succeeds"
          :items="predecessorItems"
          :disabled="predecessorLocked"
          class="w-full"
        />
      </UFormField>
      <UFormField :label="t('plateaus.basisLabel')" :error="errors.basis" required>
        <URadioGroup v-model="basis" data-testid="plateau-basis" :items="basisItems" />
      </UFormField>
    </template>

    <UAlert v-if="failure" data-testid="plateau-failure" color="error" variant="subtle" :description="failure" />

    <div class="flex justify-end gap-2">
      <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton
        type="submit"
        data-testid="plateau-save"
        :disabled="!valid"
        :loading="saving"
        :label="props.plateau ? t('common.save') : t('plateaus.create')"
      />
    </div>
  </form>
</template>
