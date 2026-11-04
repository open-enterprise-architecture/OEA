<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createMapping, type MetatypeDetail, type PropertyDefinitionDetail, type PropertyMappingWrite } from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel, metatypeById, propertyById, reloadMetamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Mapping a property to a metatype, or a further dated entry of a mapping, also of a package
// mapping (US-032, US-137, REQ-032 AC4, ADR-049; design D3, D4 of scr-030-metamodel-configuration).
const props = defineProps<{ metatype: MetatypeDetail; property?: PropertyDefinitionDetail; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [marked: number]; cancel: [] }>()
const { t } = useI18n()

type Necessity = PropertyMappingWrite['necessity']

// select items cannot have an empty value
const NONE = '-'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

const form = reactive<{
  propertyDefId?: string
  necessity: Necessity
  validFrom: string
  defaultValue: string
}>({ propertyDefId: props.property?.id, necessity: 'optional', validFrom: today(), defaultValue: '' })
const errors = ref<{ propertyDefId?: string; validFrom?: string; defaultValue?: string }>({})
const failure = ref<string>()
const submitting = ref(false)
const stale = ref(false)

const mapped = computed(() => new Set((props.metatype.propertyMappings ?? []).map((mapping) => mapping.propertyDefinition.id)))
const propertyItems = computed(() =>
  metamodel.properties
    .filter((property) => property.scope === 'organization' && !mapped.value.has(property.id))
    .map((property) => ({ label: label(labelKeys.property(property.id), property.name), value: property.id })),
)
const selected = computed(() => propertyById(form.propertyDefId) ?? props.property)
const necessityItems = computed(() =>
  (['mandatory', 'warning', 'optional'] as const).map((value) => ({
    label: t(`metamodel.necessity.${value}`),
    description: t(`metamodel.necessity.${value}Hint`),
    value,
  })),
)
const enumItems = computed(() => [
  { label: t('metamodel.mapping.noDefault'), value: NONE },
  ...(selected.value?.enumValues ?? []).map((value) => ({ label: label(labelKeys.enumValue(value.id), value.value), value: value.value })),
])
const current = computed(() => metatypeById(props.metatype.id))

function defaultValue(): PropertyMappingWrite['defaultValue'] {
  const raw = form.defaultValue.trim()
  if (raw === '' || raw === NONE || !selected.value) {
    return null
  }
  switch (selected.value.dataType) {
    case 'integer':
      return { valueInt: Number(raw) }
    case 'boolean':
      return { valueBool: raw === 'true' }
    case 'date':
      return { valueDate: raw }
    case 'enum':
      return { valueEnum: raw }
    default:
      return { valueText: raw }
  }
}

async function submit(version = props.metatype.version) {
  failure.value = undefined
  errors.value = {}
  if (!form.propertyDefId) {
    errors.value.propertyDefId = t('metamodel.mapping.propertyRequired')
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.validFrom)) {
    errors.value.validFrom = t('metamodel.mapping.validFromInvalid')
  }
  if (selected.value?.dataType === 'integer' && form.defaultValue.trim() !== '' && !Number.isInteger(Number(form.defaultValue))) {
    errors.value.defaultValue = t('metamodel.mapping.defaultInvalid')
  }
  if (Object.keys(errors.value).length > 0 || !form.propertyDefId) {
    return
  }
  submitting.value = true
  try {
    const outcome = await createMapping(
      props.metatype.id,
      { propertyDefId: form.propertyDefId, necessity: form.necessity, validFrom: form.validFrom, sortOrder: 0, defaultValue: defaultValue() },
      version,
      props.fetchFn,
    )
    if (outcome.ok) {
      stale.value = false
      emit('saved', outcome.value.markedEntityCount ?? 0)
      return
    }
    const rejection = outcome.rejection
    if (rejection.problemType === 'stale-version') {
      stale.value = true
      await reloadMetamodel(props.fetchFn)
    } else if (rejection.problemType === 'conflict') {
      failure.value = props.property ? t('metamodel.mapping.dateTaken') : t('metamodel.error.conflict')
    } else if (rejection.invalidFields.includes('defaultValue')) {
      errors.value.defaultValue = t('metamodel.mapping.defaultInvalid')
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
  <form class="flex flex-col gap-4" novalidate data-testid="mapping-form" @submit.prevent="submit()">
    <UAlert v-if="failure" data-testid="mapping-failure" color="error" variant="subtle" :description="failure" />
    <CollisionWarning v-if="stale" @overwrite="submit(current?.version)" @discard="emit('cancel')">
      <p>{{ t('metamodel.collision.metatypeChanged', { name: current?.name ?? metatype.name }) }}</p>
      <ul class="list-inside list-disc">
        <li v-for="mapping in current?.propertyMappings ?? []" :key="mapping.id">
          {{ mapping.propertyDefinition.name }} · {{ t(`metamodel.necessity.${mapping.necessity}`) }}
        </li>
      </ul>
    </CollisionWarning>

    <UFormField :label="t('metamodel.mapping.property')" name="propertyDefId" :error="errors.propertyDefId" required>
      <p v-if="property" data-testid="mapping-fixed-property" class="font-medium">
        {{ label(labelKeys.property(property.id), property.name) }}
      </p>
      <USelect
        v-else
        v-model="form.propertyDefId"
        data-testid="mapping-property"
        :items="propertyItems"
        :placeholder="t('metamodel.mapping.selectProperty')"
        class="w-full"
      />
    </UFormField>
    <UFormField :label="t('metamodel.mapping.necessity')" name="necessity" required>
      <URadioGroup v-model="form.necessity" data-testid="mapping-necessity" :items="necessityItems" />
    </UFormField>
    <UFormField
      :label="t('metamodel.mapping.validFrom')"
      name="validFrom"
      :error="errors.validFrom"
      :help="t('metamodel.mapping.validFromHelp')"
      required
    >
      <UInput v-model="form.validFrom" data-testid="mapping-valid-from" type="date" class="w-full" />
    </UFormField>
    <UFormField
      v-if="selected"
      :label="t('metamodel.mapping.default')"
      name="defaultValue"
      :error="errors.defaultValue"
      :help="t('metamodel.mapping.defaultHelp')"
    >
      <USelect v-if="selected.dataType === 'enum'" v-model="form.defaultValue" data-testid="mapping-default" :items="enumItems" class="w-full" />
      <USelect
        v-else-if="selected.dataType === 'boolean'"
        v-model="form.defaultValue"
        data-testid="mapping-default"
        :items="[
          { label: t('metamodel.mapping.noDefault'), value: NONE },
          { label: t('metamodel.yes'), value: 'true' },
          { label: t('metamodel.no'), value: 'false' },
        ]"
        class="w-full"
      />
      <UInput
        v-else
        v-model="form.defaultValue"
        data-testid="mapping-default"
        :type="selected.dataType === 'date' ? 'date' : selected.dataType === 'integer' ? 'number' : 'text'"
        class="w-full"
      />
    </UFormField>

    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="mapping-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="mapping-submit" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
