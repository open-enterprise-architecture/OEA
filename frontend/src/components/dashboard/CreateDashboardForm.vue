<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createDashboard, LIMITS, type DashboardConfig, type DashboardScope } from '../../api/dashboards'

// Creation form of a dashboard (US-055, US-062; design D4 of us-055-dashboards). A shared dashboard
// needs dashboard:manage (ADR-032); without it only a personal dashboard can be created.
const props = defineProps<{ fetchFn?: typeof fetch; canManage: boolean }>()
const emit = defineEmits<{ created: [dashboard: DashboardConfig]; cancel: [] }>()
const { t } = useI18n()

type Field = 'name' | 'description' | 'scope'

const FIELDS: Field[] = ['name', 'description', 'scope']

const form = reactive<{ name: string; description: string; scope: DashboardScope }>({
  name: '',
  description: '',
  scope: props.canManage ? 'instance' : 'personal',
})
const errors = ref<Partial<Record<Field, string>>>({})
const failure = ref<string>()
const submitting = ref(false)

const scopeItems = [
  {
    label: t('dashboards.shared'),
    description: props.canManage ? t('createDashboard.sharedHint') : t('createDashboard.sharedNeedsPermission'),
    value: 'instance',
    disabled: !props.canManage,
    // the description explains why the option is disabled and stays readable (REQ-130 AC3)
    class: 'opacity-100',
    ui: { base: 'disabled:opacity-50 data-disabled:opacity-50' },
  },
  { label: t('dashboards.personal'), description: t('createDashboard.personalHint'), value: 'personal' },
]

function validate(): Partial<Record<Field, string>> {
  const found: Partial<Record<Field, string>> = {}
  const name = form.name.trim()
  if (name.length === 0 || name.length > LIMITS.name) {
    found.name = t('createDashboard.field.name')
  }
  if (form.description.length > LIMITS.description) {
    found.description = t('createDashboard.field.description')
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
  try {
    const outcome = await createDashboard({ name: form.name.trim(), description: form.description, scope: form.scope }, props.fetchFn)
    if (outcome.ok) {
      emit('created', outcome.value)
      return
    }
    const rejection = outcome.rejection
    if (rejection.problemType === 'dashboard-name-taken') {
      errors.value = { name: t('createDashboard.nameTaken') }
    } else if (rejection.status === 422) {
      for (const field of rejection.invalidFields) {
        if ((FIELDS as string[]).includes(field)) {
          errors.value[field as Field] = t(`createDashboard.field.${field}`)
        }
      }
    } else if (rejection.status === 403) {
      failure.value = t('createDashboard.forbidden')
    } else {
      failure.value = t('createDashboard.failed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="create-dashboard-form" @submit.prevent="submit">
    <UAlert v-if="failure" data-testid="create-dashboard-failure" color="error" variant="subtle" :description="failure" />

    <UFormField :label="t('createDashboard.name')" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="dashboard-name" class="w-full" autofocus />
    </UFormField>
    <UFormField
      :label="t('createDashboard.description')"
      name="description"
      :error="errors.description"
      :help="t('createDashboard.descriptionHelp')"
    >
      <UTextarea v-model="form.description" data-testid="dashboard-description" :rows="2" class="w-full" />
    </UFormField>
    <UFormField :label="t('createDashboard.scope')" name="scope" :error="errors.scope" required>
      <URadioGroup v-model="form.scope" data-testid="dashboard-scope" :items="scopeItems" />
    </UFormField>

    <div class="flex justify-end gap-2">
      <UButton
        data-testid="create-dashboard-cancel"
        color="neutral"
        variant="ghost"
        :label="t('common.cancel')"
        @click="emit('cancel')"
      />
      <UButton type="submit" data-testid="create-dashboard-submit" :loading="submitting" :label="t('createDashboard.submit')" />
    </div>
  </form>
</template>
