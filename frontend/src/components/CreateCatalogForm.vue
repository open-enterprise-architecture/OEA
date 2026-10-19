<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createCatalog, type Catalog, type CatalogScope, type JoinMode } from '../api/catalogs'
import { fetchMetatypes, type MetatypeDetail } from '../api/metamodel'

// Creation form of a catalog (US-046, SCR-028; design D7 of us-046-create-catalog). Shared
// catalogs need catalog:manage (ADR-032); without it only a personal catalog can be created.
const props = defineProps<{ fetchFn?: typeof fetch; canManage: boolean }>()
const emit = defineEmits<{ created: [catalog: Catalog]; cancel: [] }>()

type Field = 'name' | 'description' | 'primaryMetatypeId' | 'scope' | 'defaultJoinMode'

const NAME_TAKEN = 'A catalog with this name already exists'

const FIELD_MESSAGES: Record<Field, string> = {
  name: 'Enter a name with 1 to 255 characters.',
  description: 'The description can have at most 1000 characters.',
  primaryMetatypeId: 'Select an entity type of the metamodel.',
  scope: 'Select a visibility.',
  defaultJoinMode: 'Select a join mode.',
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

const typeItems = computed(() => metatypes.value.map((metatype) => ({ label: metatype.name, value: metatype.id })))
const scopeItems = computed(() => [
  {
    label: 'Shared',
    description: props.canManage
      ? 'Visible to all users of the instance'
      : 'Visible to all users of the instance; needs the permission to manage catalogs',
    value: 'instance',
    disabled: !props.canManage,
  },
  { label: 'Personal', description: 'Visible only to you', value: 'personal' },
])
const joinModeItems = [
  { label: 'Aggregate', description: 'One row per entity; linked entities in one cell', value: 'aggregate' },
  { label: 'Rows', description: 'One row per linked entity', value: 'rows' },
]

onMounted(async () => {
  try {
    metatypes.value = [...(await fetchMetatypes(props.fetchFn))].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    form.primaryMetatypeId = metatypes.value[0]?.id
  } catch {
    failure.value = 'The entity types could not be loaded.'
  }
})

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const name = form.name.trim()
  if (name.length === 0 || name.length > 255) {
    found.name = FIELD_MESSAGES.name
  }
  if (form.description.length > 1000) {
    found.description = FIELD_MESSAGES.description
  }
  if (!form.primaryMetatypeId) {
    found.primaryMetatypeId = FIELD_MESSAGES.primaryMetatypeId
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
      errors.value = { name: NAME_TAKEN }
    } else if (rejection.status === 422) {
      for (const field of rejection.invalidFields) {
        if (field in FIELD_MESSAGES) {
          errors.value[field as Field] = FIELD_MESSAGES[field as Field]
        }
      }
    } else if (rejection.status === 403) {
      failure.value = 'You are not allowed to create this catalog.'
    } else {
      failure.value = 'The catalog could not be created.'
    }
  } catch {
    failure.value = 'The backend is not reachable.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="create-catalog-form" @submit.prevent="submit">
    <UAlert v-if="failure" data-testid="create-catalog-failure" color="error" variant="subtle" :description="failure" />

    <UFormField label="Name" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="catalog-name" class="w-full" autofocus />
    </UFormField>
    <UFormField label="Description" name="description" :error="errors.description" help="Optional, up to 1000 characters">
      <UTextarea v-model="form.description" data-testid="catalog-description" :rows="2" class="w-full" />
    </UFormField>
    <UFormField label="Primary entity type" name="primaryMetatypeId" :error="errors.primaryMetatypeId" required help="Supplies the rows of the table">
      <USelect
        v-model="form.primaryMetatypeId"
        data-testid="catalog-type"
        :items="typeItems"
        placeholder="Select an entity type"
        class="w-full"
      />
    </UFormField>
    <UFormField label="Visibility" name="scope" :error="errors.scope" required>
      <URadioGroup v-model="form.scope" data-testid="catalog-scope" :items="scopeItems" />
    </UFormField>
    <UFormField label="Default join mode" name="defaultJoinMode" :error="errors.defaultJoinMode" help="Applies to joins added later">
      <URadioGroup v-model="form.defaultJoinMode" data-testid="catalog-join-mode" orientation="horizontal" :items="joinModeItems" />
    </UFormField>

    <div class="flex justify-end gap-2">
      <UButton data-testid="create-catalog-cancel" color="neutral" variant="ghost" label="Cancel" @click="emit('cancel')" />
      <UButton type="submit" data-testid="create-catalog-submit" :loading="submitting" label="Create catalog" />
    </div>
  </form>
</template>
