<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createMetatype, INSTANCE_CONFIGURATION, updateMetatype, type MetatypeDetail } from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel, metatypeById, reloadMetamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Creating and changing an organization metatype in SCR-030 (US-032 AC1, AC2, REQ-032, UC-04 A4;
// design D3, D4 of scr-030-metamodel-configuration).
const props = defineProps<{ metatype?: MetatypeDetail; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [id: string]; cancel: [] }>()
const { t } = useI18n()

type Field = 'name' | 'abbreviation' | 'sortOrder' | 'baseMetatypeId'

const NONE = '-'

const form = reactive({
  name: props.metatype?.name ?? '',
  abbreviation: props.metatype?.abbreviation ?? '',
  sortOrder: props.metatype?.sortOrder ?? 0,
  baseMetatypeId: props.metatype?.baseMetatypeId ?? NONE,
})
const errors = ref<Partial<Record<Field, string>>>({})
const failure = ref<string>()
const submitting = ref(false)
const stale = ref(false)

const current = computed(() => metatypeById(props.metatype?.id))

const baseItems = computed(() => [
  { label: t('metamodel.metatype.noBase'), value: NONE },
  ...metamodel.metatypes
    .filter((candidate) => candidate.id !== props.metatype?.id && !candidate.isConnection)
    .map((candidate) => ({ label: label(labelKeys.metatype(candidate.id), candidate.name), value: candidate.id })),
])

function baseName(id: string | null | undefined): string {
  const base = metatypeById(id ?? undefined)
  return base ? label(labelKeys.metatype(base.id), base.name) : t('metamodel.metatype.noBase')
}

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const name = form.name.trim()
  if (name.length === 0 || name.length > 100) {
    found.name = t('metamodel.metatype.nameInvalid')
  }
  if (form.abbreviation.trim().length > 10) {
    found.abbreviation = t('metamodel.metatype.abbreviationInvalid')
  }
  return found
}

async function submit(version = props.metatype?.version) {
  failure.value = undefined
  errors.value = validate()
  if (Object.keys(errors.value).length > 0) {
    return
  }
  submitting.value = true
  const body = {
    name: form.name.trim(),
    abbreviation: form.abbreviation.trim(),
    sortOrder: Number(form.sortOrder) || 0,
    isConnection: false,
    configId: INSTANCE_CONFIGURATION,
    baseMetatypeId: form.baseMetatypeId === NONE ? null : form.baseMetatypeId,
  }
  try {
    const outcome = props.metatype
      ? await updateMetatype(props.metatype.id, body, version, props.fetchFn)
      : await createMetatype(body, props.fetchFn)
    if (outcome.ok) {
      stale.value = false
      emit('saved', outcome.value.id)
      return
    }
    const rejection = outcome.rejection
    if (rejection.problemType === 'stale-version') {
      stale.value = true
      await reloadMetamodel(props.fetchFn)
    } else if (rejection.problemType === 'name-taken') {
      errors.value = { name: t('metamodel.error.nameTaken') }
    } else if (rejection.problemType === 'validation-failed' && rejection.invalidFields.length > 0) {
      for (const field of rejection.invalidFields) {
        errors.value[field as Field] = t('metamodel.metatype.fieldInvalid')
      }
    } else {
      failure.value = rejectionMessage(rejection)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="metatype-form" @submit.prevent="submit()">
    <UAlert v-if="failure" data-testid="metatype-failure" color="error" variant="subtle" :description="failure" />
    <CollisionWarning v-if="stale" @overwrite="submit(current?.version)" @discard="emit('cancel')">
      <dl v-if="current" class="grid grid-cols-[auto_1fr] gap-x-3">
        <dt class="text-toned">{{ t('metamodel.metatype.name') }}</dt>
        <dd data-testid="collision-name">{{ current.name }}</dd>
        <dt class="text-toned">{{ t('metamodel.metatype.abbreviation') }}</dt>
        <dd data-testid="collision-abbreviation">{{ current.abbreviation || '–' }}</dd>
        <dt class="text-toned">{{ t('metamodel.metatype.base') }}</dt>
        <dd>{{ baseName(current.baseMetatypeId) }}</dd>
      </dl>
      <p v-else>{{ t('metamodel.collision.deleted') }}</p>
    </CollisionWarning>

    <UFormField :label="t('metamodel.metatype.name')" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="metatype-name" class="w-full" autofocus />
    </UFormField>
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <UFormField
        :label="t('metamodel.metatype.abbreviation')"
        name="abbreviation"
        :error="errors.abbreviation"
        :help="t('metamodel.metatype.abbreviationHelp')"
      >
        <UInput v-model="form.abbreviation" data-testid="metatype-abbreviation" class="w-full" />
      </UFormField>
      <UFormField :label="t('metamodel.sortOrder')" name="sortOrder" :error="errors.sortOrder">
        <UInput v-model.number="form.sortOrder" data-testid="metatype-sort-order" type="number" class="w-full" />
      </UFormField>
    </div>
    <UFormField
      :label="t('metamodel.metatype.base')"
      name="baseMetatypeId"
      :error="errors.baseMetatypeId"
      :help="t('metamodel.metatype.baseHelp')"
    >
      <USelect v-model="form.baseMetatypeId" data-testid="metatype-base" :items="baseItems" class="w-full" />
    </UFormField>

    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="metatype-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="metatype-submit" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
