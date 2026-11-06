<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createCategory, updateCategory, type PropertyCategory } from '../../api/metamodel'
import { categoryById, reloadMetamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Creating and changing an organization property category in SCR-030 (US-032, REQ-032; design D3,
// D4 of scr-030-metamodel-configuration).
const props = defineProps<{ category?: PropertyCategory; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const { t } = useI18n()

const form = reactive({
  name: props.category?.name ?? '',
  description: props.category?.description ?? '',
  sortOrder: props.category?.sortOrder ?? 0,
})
const nameError = ref<string>()
const failure = ref<string>()
const submitting = ref(false)
const stale = ref(false)
const current = computed(() => categoryById(props.category?.id))

async function submit(version = props.category?.version) {
  failure.value = undefined
  nameError.value = undefined
  const name = form.name.trim()
  if (name.length === 0 || name.length > 100) {
    nameError.value = t('metamodel.metatype.nameInvalid')
    return
  }
  submitting.value = true
  const body = { name, description: form.description, sortOrder: Number(form.sortOrder) || 0 }
  try {
    const outcome = props.category
      ? await updateCategory(props.category.id, body, version, props.fetchFn)
      : await createCategory(body, props.fetchFn)
    if (outcome.ok) {
      stale.value = false
      emit('saved')
    } else if (outcome.rejection.problemType === 'stale-version') {
      // show the warning with the current state of the other change, also if reloading fails
      try {
        await reloadMetamodel(props.fetchFn)
      } finally {
        stale.value = true
      }
    } else if (outcome.rejection.problemType === 'name-taken') {
      nameError.value = t('metamodel.error.nameTaken')
    } else {
      failure.value = rejectionMessage(outcome.rejection)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4" novalidate data-testid="category-form" @submit.prevent="submit()">
    <UAlert v-if="failure" data-testid="category-failure" color="error" variant="subtle" :description="failure" />
    <CollisionWarning v-if="stale" @overwrite="submit(current?.version)" @discard="emit('cancel')">
      <p v-if="current">{{ current.name }}<span v-if="current.description"> – {{ current.description }}</span></p>
      <p v-else>{{ t('metamodel.collision.deleted') }}</p>
    </CollisionWarning>
    <UFormField :label="t('metamodel.metatype.name')" name="name" :error="nameError" required>
      <UInput v-model="form.name" data-testid="category-name" class="w-full" autofocus />
    </UFormField>
    <UFormField :label="t('metamodel.category.description')" name="description">
      <UTextarea v-model="form.description" data-testid="category-description" :rows="2" class="w-full" />
    </UFormField>
    <UFormField :label="t('metamodel.sortOrder')" name="sortOrder">
      <UInput v-model.number="form.sortOrder" data-testid="category-sort-order" type="number" class="w-full" />
    </UFormField>
    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="category-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="category-submit" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
