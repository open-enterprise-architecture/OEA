<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createDiagram, DIAGRAM_LIMITS, updateDiagram, type Diagram } from '../../api/diagrams'
import { fetchViewpoints, type Viewpoint } from '../../api/metamodel'

// Form of a diagram (US-074, UC-10; design D6 of us-074-diagrams): creating one with its name,
// description and viewpoint, or renaming it and changing its description; the viewpoint is fixed
// after the creation.
const props = defineProps<{ diagram?: Diagram; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: [diagram: Diagram]; cancel: [] }>()
const { t } = useI18n()

type Field = 'name' | 'description' | 'viewpointId'

const form = reactive({
  name: props.diagram?.name ?? '',
  description: props.diagram?.description ?? '',
  viewpointId: props.diagram?.viewpoint.id ?? '',
})
const viewpoints = ref<Viewpoint[]>([])
const viewpointsFailed = ref(false)
const errors = ref<Partial<Record<Field, string>>>({})
const failure = ref<string>()
const submitting = ref(false)

onMounted(async () => {
  if (props.diagram) {
    return
  }
  try {
    viewpoints.value = await fetchViewpoints(props.fetchFn)
  } catch {
    viewpointsFailed.value = true
  }
})

const items = (): { label: string; value: string }[] =>
  viewpoints.value.map((viewpoint) => ({ label: `${viewpoint.name} (${viewpoint.notation})`, value: viewpoint.id }))

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const name = form.name.trim()
  if (name.length === 0 || name.length > DIAGRAM_LIMITS.name) {
    found.name = t('diagrams.field.name')
  }
  if (form.description.length > DIAGRAM_LIMITS.description) {
    found.description = t('diagrams.field.description')
  }
  if (!props.diagram && !form.viewpointId) {
    found.viewpointId = t('diagrams.field.viewpointId')
  }
  return found
}

async function submit() {
  failure.value = undefined
  errors.value = validate()
  if (Object.keys(errors.value).length > 0) {
    return
  }
  submitting.value = true
  const description = form.description.trim().length > 0 ? form.description : null
  try {
    const outcome = props.diagram
      ? await updateDiagram(props.diagram.id, { name: form.name.trim(), description }, props.diagram.version, props.fetchFn)
      : await createDiagram({ name: form.name.trim(), description, viewpointId: form.viewpointId }, props.fetchFn)
    if (outcome.ok) {
      emit('saved', outcome.value)
      return
    }
    const rejection = outcome.rejection
    if (rejection.problemType === 'diagram-name-taken') {
      errors.value = { name: t('diagrams.nameTaken') }
    } else if (rejection.status === 422) {
      for (const field of rejection.invalidFields) {
        if (['name', 'description', 'viewpointId'].includes(field)) {
          errors.value[field as Field] = t(`diagrams.field.${field}`)
        }
      }
    } else if (rejection.status === 412) {
      failure.value = t('diagrams.stale')
    } else if (rejection.status === 404) {
      failure.value = t('diagrams.gone')
    } else if (rejection.status === 403) {
      failure.value = t('diagrams.forbidden')
    } else {
      failure.value = t('diagrams.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="diagram-form" @submit.prevent="submit">
    <UAlert v-if="failure" data-testid="diagram-failure" color="error" variant="subtle" :description="failure" />
    <UFormField :label="t('diagrams.name')" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="diagram-name" class="w-full" autofocus />
    </UFormField>
    <UFormField :label="t('diagrams.description')" name="description" :error="errors.description" :help="t('diagrams.descriptionHelp')">
      <UTextarea v-model="form.description" data-testid="diagram-description" :rows="2" class="w-full" />
    </UFormField>
    <UFormField v-if="!diagram" :label="t('diagrams.viewpoint')" name="viewpointId" :error="errors.viewpointId" :help="t('diagrams.viewpointHelp')" required>
      <UAlert v-if="viewpointsFailed" data-testid="diagram-viewpoints-failed" color="warning" variant="subtle" :description="t('diagrams.viewpointsFailed')" class="mb-2" />
      <USelect v-model="form.viewpointId" data-testid="diagram-viewpoint" :items="items()" :aria-label="t('diagrams.viewpoint')" class="w-full" />
    </UFormField>
    <div class="flex justify-end gap-2">
      <UButton data-testid="diagram-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton data-testid="diagram-submit" type="submit" :loading="submitting" :label="diagram ? t('diagrams.renameSubmit') : t('diagrams.createSubmit')" />
    </div>
  </form>
</template>
