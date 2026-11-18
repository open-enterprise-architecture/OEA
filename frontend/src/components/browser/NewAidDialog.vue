<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Rejection } from '../../api/problem'
import { rateLimitMessage } from '../../api/problem'

// "New structuring aid" (US-136 AC1; design D5 of us-135-component-hierarchy): a name of 1 to 255 characters and an
// optional description, created at the root level or under the element the action was chosen on.

const props = defineProps<{ parentName: string | null; create: (name: string, description: string) => Promise<Rejection | undefined> }>()
const open = defineModel<boolean>('open', { required: true })
const { t } = useI18n()

const name = ref('')
const description = ref('')
const error = ref<string>()
const busy = ref(false)

watch(open, (value) => {
  if (value) {
    name.value = ''
    description.value = ''
    error.value = undefined
  }
})

async function submit() {
  const trimmed = name.value.trim()
  if (!trimmed) {
    error.value = t('browser.aidNameRequired')
    return
  }
  busy.value = true
  error.value = undefined
  const rejection = await props.create(trimmed, description.value)
  busy.value = false
  if (!rejection) {
    open.value = false
  } else if (rejection.status === 422 && rejection.invalidFields.includes('name')) {
    error.value = t('browser.aidNameInvalid')
  } else if (rejection.status === 422 && rejection.invalidFields.includes('description')) {
    error.value = t('browser.aidDescriptionInvalid')
  } else if (rejection.status === 422) {
    error.value = t('browser.aidParentInvalid')
  } else {
    error.value = rateLimitMessage(rejection) ?? t('browser.changeFailed')
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="parentName ? t('browser.newAidUnder', { name: parentName }) : t('browser.newAid')">
    <template #body>
      <form data-testid="new-aid-form" class="flex flex-col gap-4" @submit.prevent="submit">
        <UFormField :label="t('browser.aidName')" required>
          <UInput v-model="name" data-testid="new-aid-name" maxlength="255" class="w-full" />
        </UFormField>
        <UFormField :label="t('browser.aidDescription')">
          <UTextarea v-model="description" data-testid="new-aid-description" maxlength="2000" class="w-full" />
        </UFormField>
        <p v-if="error" data-testid="new-aid-error" role="alert" class="text-sm text-error">{{ error }}</p>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton data-testid="new-aid-submit" type="submit" :loading="busy" :label="t('browser.newAidSubmit')" />
        </div>
      </form>
    </template>
  </UModal>
</template>
