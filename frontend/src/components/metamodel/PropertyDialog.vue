<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  createEnumValue,
  createPropertyDefinition,
  deleteEnumValue,
  updatePropertyDefinition,
  type PropertyDefinitionDetail,
  type PropertyDefinitionWrite,
  type WriteOutcome,
} from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel, propertyById, reloadMetamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Creating and changing an organization property definition with its enum values in SCR-030
// (US-032, REQ-032 inputs; design D3, D4 of scr-030-metamodel-configuration).
const props = defineProps<{ property?: PropertyDefinitionDetail; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [id: string]; cancel: [] }>()
const { t } = useI18n()

type DataType = PropertyDefinitionWrite['dataType']
type Field = 'name' | 'dataType' | 'categoryId' | 'maxLength' | 'minValue' | 'maxValue' | 'pattern'

const DATA_TYPES: DataType[] = ['string', 'text', 'integer', 'boolean', 'date', 'url', 'enum']
const NONE = '-'

const form = reactive({
  name: props.property?.name ?? '',
  dataType: (props.property?.dataType ?? 'string') as DataType,
  categoryId: props.property?.category?.id ?? NONE,
  maxLength: props.property?.maxLength?.toString() ?? '',
  minValue: props.property?.minValue?.toString() ?? '',
  maxValue: props.property?.maxValue?.toString() ?? '',
  pattern: props.property?.pattern ?? '',
})
// values of a new enum property, created after the definition
const newValues = ref<string[]>([])
const valueInput = ref('')
const pendingDelete = ref<string>()
const errors = ref<Partial<Record<Field, string>>>({})
const failure = ref<string>()
const notice = ref<string>()
const submitting = ref(false)
const stale = ref(false)

const current = computed(() => propertyById(props.property?.id))
const values = computed(() => (props.property ? (current.value?.enumValues ?? []) : []))
const typeItems = computed(() => DATA_TYPES.map((value) => ({ label: t(`metamodel.dataType.${value}`), value })))
const categoryItems = computed(() => [
  { label: t('metamodel.property.noCategory'), value: NONE },
  ...metamodel.categories.map((category) => ({ label: category.name, value: category.id })),
])

// number inputs may give numbers or strings
function numberOrNull(raw: string | number): number | null {
  return String(raw).trim() === '' ? null : Number(raw)
}

function body(): PropertyDefinitionWrite {
  const type = form.dataType
  return {
    name: form.name.trim(),
    dataType: type,
    categoryId: form.categoryId === NONE ? null : form.categoryId,
    maxLength: ['string', 'text', 'url'].includes(type) ? numberOrNull(form.maxLength) : null,
    minValue: type === 'integer' ? numberOrNull(form.minValue) : null,
    maxValue: type === 'integer' ? numberOrNull(form.maxValue) : null,
    pattern: type === 'string' && form.pattern.trim() !== '' ? form.pattern : null,
  }
}

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const name = form.name.trim()
  if (name.length === 0 || name.length > 100) {
    found.name = t('metamodel.metatype.nameInvalid')
  }
  for (const field of ['maxLength', 'minValue', 'maxValue'] as const) {
    if (String(form[field]).trim() !== '' && !Number.isInteger(Number(form[field]))) {
      found[field] = t('metamodel.property.wholeNumber')
    }
  }
  return found
}

function addNewValue() {
  const value = valueInput.value.trim()
  if (value !== '' && !newValues.value.includes(value)) {
    newValues.value.push(value)
  }
  valueInput.value = ''
}

async function submit(version = props.property?.version) {
  failure.value = undefined
  errors.value = validate()
  if (Object.keys(errors.value).length > 0) {
    return
  }
  submitting.value = true
  try {
    const outcome = props.property
      ? await updatePropertyDefinition(props.property.id, body(), version, props.fetchFn)
      : await createPropertyDefinition(body(), props.fetchFn)
    if (!outcome.ok) {
      const rejection = outcome.rejection
      if (rejection.problemType === 'stale-version') {
        stale.value = true
        await reloadMetamodel(props.fetchFn)
      } else if (rejection.problemType === 'name-taken') {
        errors.value = { name: t('metamodel.error.nameTaken') }
      } else if (rejection.problemType === 'in-use') {
        errors.value = { dataType: t('metamodel.property.typeInUse') }
      } else if (rejection.problemType === 'validation-failed' && rejection.invalidFields.length > 0) {
        for (const field of rejection.invalidFields) {
          errors.value[field as Field] = t('metamodel.metatype.fieldInvalid')
        }
      } else {
        failure.value = rejectionMessage(rejection)
      }
      return
    }
    stale.value = false
    const created = outcome.value
    if (!props.property && created.dataType === 'enum') {
      // each value is a change of the new definition: versions 1, 2, …
      let next = created.version ?? 1
      for (const value of newValues.value) {
        const added = await createEnumValue(created.id, value, next, props.fetchFn)
        if (!added.ok) {
          failure.value = rejectionMessage(added.rejection)
          await reloadMetamodel(props.fetchFn)
          return
        }
        next += 1
      }
    }
    emit('saved', created.id)
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}

/** Adds or deletes a value of an existing definition at once (design D4). */
async function changeValue(change: () => Promise<WriteOutcome<unknown>>) {
  failure.value = undefined
  notice.value = undefined
  try {
    const outcome = await change()
    if (!outcome.ok) {
      if (outcome.rejection.problemType === 'stale-version') {
        notice.value = t('metamodel.collision.reloaded')
      } else if (outcome.rejection.problemType === 'name-taken') {
        failure.value = t('metamodel.property.valueTaken')
      } else {
        failure.value = rejectionMessage(outcome.rejection)
      }
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  }
  await reloadMetamodel(props.fetchFn)
}

async function addValue() {
  const value = valueInput.value.trim()
  if (!props.property || value === '') {
    return
  }
  valueInput.value = ''
  await changeValue(() => createEnumValue(props.property!.id, value, current.value?.version, props.fetchFn))
}

async function removeValue(valueId: string) {
  pendingDelete.value = undefined
  await changeValue(() => deleteEnumValue(props.property!.id, valueId, current.value?.version, props.fetchFn))
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="property-form" @submit.prevent="submit()">
    <UAlert v-if="failure" data-testid="property-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="property-notice" color="warning" variant="subtle" :description="notice" />
    <CollisionWarning v-if="stale" @overwrite="submit(current?.version)" @discard="emit('cancel')">
      <p v-if="current">
        {{ current.name }} · {{ t(`metamodel.dataType.${current.dataType}`) }}
        <span v-if="current.category"> · {{ current.category.name }}</span>
      </p>
      <p v-else>{{ t('metamodel.collision.deleted') }}</p>
    </CollisionWarning>

    <UFormField :label="t('metamodel.metatype.name')" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="property-name" class="w-full" autofocus />
    </UFormField>
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <UFormField :label="t('metamodel.property.dataType')" name="dataType" :error="errors.dataType" required>
        <USelect v-model="form.dataType" data-testid="property-data-type" :items="typeItems" class="w-full" />
      </UFormField>
      <UFormField :label="t('metamodel.property.category')" name="categoryId" :error="errors.categoryId">
        <USelect v-model="form.categoryId" data-testid="property-category" :items="categoryItems" class="w-full" />
      </UFormField>
    </div>
    <UFormField
      v-if="['string', 'text', 'url'].includes(form.dataType)"
      :label="t('metamodel.property.maxLength')"
      name="maxLength"
      :error="errors.maxLength"
    >
      <UInput v-model="form.maxLength" data-testid="property-max-length" type="number" class="w-full" />
    </UFormField>
    <div v-if="form.dataType === 'integer'" class="grid grid-cols-2 gap-4">
      <UFormField :label="t('metamodel.property.minValue')" name="minValue" :error="errors.minValue">
        <UInput v-model="form.minValue" data-testid="property-min" type="number" class="w-full" />
      </UFormField>
      <UFormField :label="t('metamodel.property.maxValue')" name="maxValue" :error="errors.maxValue">
        <UInput v-model="form.maxValue" data-testid="property-max" type="number" class="w-full" />
      </UFormField>
    </div>
    <UFormField
      v-if="form.dataType === 'string'"
      :label="t('metamodel.property.pattern')"
      name="pattern"
      :error="errors.pattern"
      :help="t('metamodel.property.patternHelp')"
    >
      <UInput v-model="form.pattern" data-testid="property-pattern" class="w-full font-mono" />
    </UFormField>

    <fieldset v-if="form.dataType === 'enum'" class="flex flex-col gap-2" data-testid="property-values">
      <legend class="mb-1 text-sm font-medium">{{ t('metamodel.property.values') }}</legend>
      <ul class="flex flex-wrap gap-2">
        <li v-for="value in values" :key="value.id" data-testid="property-value" class="flex items-center gap-1">
          <UBadge color="neutral" variant="outline" :label="label(labelKeys.enumValue(value.id), value.value)" />
          <UButton
            :data-testid="`property-value-delete-${value.value}`"
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-x"
            :aria-label="t('metamodel.property.deleteValue', { value: value.value })"
            @click="pendingDelete = value.id"
          />
        </li>
        <li v-for="value in newValues" :key="value" data-testid="property-new-value" class="flex items-center gap-1">
          <UBadge color="primary" variant="subtle" :label="value" />
          <UButton
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-x"
            :aria-label="t('metamodel.property.deleteValue', { value })"
            @click="newValues = newValues.filter((candidate) => candidate !== value)"
          />
        </li>
      </ul>
      <UAlert
        v-if="pendingDelete"
        data-testid="value-delete-warning"
        color="warning"
        variant="subtle"
        :description="t('metamodel.property.deleteValueWarning')"
        :actions="[
          { label: t('metamodel.delete'), color: 'error', onClick: () => removeValue(pendingDelete!) },
          { label: t('common.cancel'), color: 'neutral', variant: 'ghost', onClick: () => (pendingDelete = undefined) },
        ]"
      />
      <div class="flex gap-2">
        <UInput
          v-model="valueInput"
          data-testid="property-value-input"
          class="flex-1"
          :placeholder="t('metamodel.property.newValue')"
          @keydown.enter.prevent="property ? addValue() : addNewValue()"
        />
        <UButton
          data-testid="property-value-add"
          color="neutral"
          variant="outline"
          icon="i-lucide-plus"
          :label="t('metamodel.property.addValue')"
          @click="property ? addValue() : addNewValue()"
        />
      </div>
    </fieldset>

    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="property-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="property-submit" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
