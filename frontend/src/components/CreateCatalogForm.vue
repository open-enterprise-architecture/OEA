<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createCatalog, type Catalog, type CatalogScope, type JoinMode } from '../api/catalogs'
import { fetchMetatypes, type MetatypeDetail } from '../api/metamodel'
import { label, labelKeys } from '../i18n/labels'

// Creation form of a catalog (US-046, SCR-028; design D7 of us-046-create-catalog). Shared
// catalogs need catalog:manage (ADR-032); without it only a personal catalog can be created.
const props = defineProps<{ fetchFn?: typeof fetch; canManage: boolean }>()
const emit = defineEmits<{ created: [catalog: Catalog]; cancel: [] }>()
const { t } = useI18n()

type Field = 'name' | 'description' | 'primaryMetatypeId' | 'scope' | 'defaultJoinMode'

const FIELDS: Field[] = ['name', 'description', 'primaryMetatypeId', 'scope', 'defaultJoinMode']

function fieldMessage(field: Field): string {
  return t(`createCatalog.field.${field}`)
}

const form = reactive<{
  name: string
  description: string
  primaryMetatypeId?: string
  scope: CatalogScope
  defaultJoinMode: JoinMode
}>({
  name: '',
  description: '',
  primaryMetatypeId: undefined,
  scope: props.canManage ? 'instance' : 'personal',
  defaultJoinMode: 'aggregate',
})
const metatypes = ref<MetatypeDetail[]>([])
const errors = ref<Partial<Record<Field, string>>>({})
const failure = ref<string>()
const submitting = ref(false)

// elements and connections in two groups (US-080): a connection catalog lists connections
const typeItems = computed(() => {
  const item = (metatype: MetatypeDetail) => ({ label: label(labelKeys.metatype(metatype.id), metatype.name), value: metatype.id })
  const elements = metatypes.value.filter((metatype) => !metatype.isConnection).map(item)
  const connections = metatypes.value.filter((metatype) => metatype.isConnection).map(item)
  if (connections.length === 0) {
    return elements
  }
  return [
    [{ type: 'label' as const, label: t('createCatalog.elementTypes') }, ...elements],
    [{ type: 'label' as const, label: t('createCatalog.connectionTypes') }, ...connections],
  ]
})
const scopeItems = computed(() => [
  {
    label: t('catalogs.shared'),
    description: props.canManage ? t('createCatalog.sharedHint') : t('createCatalog.sharedNeedsPermission'),
    value: 'instance',
    disabled: !props.canManage,
    // the description explains why the option is disabled and stays readable (REQ-130 AC3);
    // only the radio itself is dimmed instead of the whole item
    class: 'opacity-100',
    ui: { base: 'disabled:opacity-50 data-disabled:opacity-50' },
  },
  { label: t('catalogs.personal'), description: t('createCatalog.personalHint'), value: 'personal' },
])
const joinModeItems = computed(() => [
  { label: t('createCatalog.aggregate'), description: t('createCatalog.aggregateHint'), value: 'aggregate' },
  { label: t('createCatalog.rows'), description: t('createCatalog.rowsHint'), value: 'rows' },
])

onMounted(async () => {
  try {
    metatypes.value = [...(await fetchMetatypes(props.fetchFn))].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    form.primaryMetatypeId = (metatypes.value.find((metatype) => !metatype.isConnection) ?? metatypes.value[0])?.id
  } catch {
    failure.value = t('createCatalog.typesFailed')
  }
})

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const name = form.name.trim()
  if (name.length === 0 || name.length > 255) {
    found.name = fieldMessage('name')
  }
  if (form.description.length > 1000) {
    found.description = fieldMessage('description')
  }
  if (!form.primaryMetatypeId) {
    found.primaryMetatypeId = fieldMessage('primaryMetatypeId')
  }
  return found
}

async function submit() {
  failure.value = undefined
  errors.value = validate()
  if (Object.keys(errors.value).length > 0 || !form.primaryMetatypeId) {
    return
  }
  submitting.value = true
  try {
    const outcome = await createCatalog(
      {
        name: form.name.trim(),
        description: form.description,
        primaryMetatypeId: form.primaryMetatypeId,
        scope: form.scope,
        defaultJoinMode: form.defaultJoinMode,
      },
      props.fetchFn,
    )
    if (outcome.ok) {
      emit('created', outcome.value)
      return
    }
    const rejection = outcome.rejection
    if (rejection.problemType === 'catalog-name-taken') {
      errors.value = { name: t('createCatalog.nameTaken') }
    } else if (rejection.status === 422) {
      for (const field of rejection.invalidFields) {
        if ((FIELDS as string[]).includes(field)) {
          errors.value[field as Field] = fieldMessage(field as Field)
        }
      }
    } else if (rejection.status === 403) {
      failure.value = t('createCatalog.forbidden')
    } else {
      failure.value = t('createCatalog.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="create-catalog-form" @submit.prevent="submit">
    <UAlert v-if="failure" data-testid="create-catalog-failure" color="error" variant="subtle" :description="failure" />

    <UFormField :label="t('catalog.nameColumn')" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="catalog-name" class="w-full" autofocus />
    </UFormField>
    <UFormField
      :label="t('createCatalog.description')"
      name="description"
      :error="errors.description"
      :help="t('createCatalog.descriptionHelp')"
    >
      <UTextarea v-model="form.description" data-testid="catalog-description" :rows="2" class="w-full" />
    </UFormField>
    <UFormField
      :label="t('createCatalog.primaryType')"
      name="primaryMetatypeId"
      :error="errors.primaryMetatypeId"
      required
      :help="t('createCatalog.primaryTypeHelp')"
    >
      <USelect
        v-model="form.primaryMetatypeId"
        data-testid="catalog-type"
        :items="typeItems"
        :placeholder="t('createCatalog.selectType')"
        class="w-full"
      />
    </UFormField>
    <UFormField :label="t('createCatalog.visibility')" name="scope" :error="errors.scope" required>
      <URadioGroup v-model="form.scope" data-testid="catalog-scope" :items="scopeItems" />
    </UFormField>
    <UFormField
      :label="t('createCatalog.joinMode')"
      name="defaultJoinMode"
      :error="errors.defaultJoinMode"
      :help="t('createCatalog.joinModeHelp')"
    >
      <URadioGroup v-model="form.defaultJoinMode" data-testid="catalog-join-mode" orientation="horizontal" :items="joinModeItems" />
    </UFormField>

    <div class="flex justify-end gap-2">
      <UButton
        data-testid="create-catalog-cancel"
        color="neutral"
        variant="ghost"
        :label="t('common.cancel')"
        @click="emit('cancel')"
      />
      <UButton type="submit" data-testid="create-catalog-submit" :loading="submitting" :label="t('createCatalog.submit')" />
    </div>
  </form>
</template>
