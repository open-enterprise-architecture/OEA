<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createArchitectureLayer, LAYER_NAME_MAX, updateArchitectureLayer, type ArchitectureLayer } from '../../api/metamodel'
import { layerById, reloadMetamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Creating and renaming an organization architecture layer in SCR-030 (change
// s17-architecture-layers D4; US-057 AC4): the name has 1 to 100 characters and is unique
// regardless of case; the position is changed in the list.
const props = defineProps<{ layer?: ArchitectureLayer; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const { t } = useI18n()

const name = ref(props.layer?.name ?? '')
const nameError = ref<string>()
const failure = ref<string>()
const submitting = ref(false)
const stale = ref(false)
const current = computed(() => layerById(props.layer?.id))

async function submit(version = props.layer?.version) {
  failure.value = undefined
  nameError.value = undefined
  const stripped = name.value.trim()
  if (stripped.length === 0 || stripped.length > LAYER_NAME_MAX) {
    nameError.value = t('metamodel.layer.nameInvalid')
    return
  }
  submitting.value = true
  try {
    const outcome = props.layer
      ? await updateArchitectureLayer(props.layer.id, { name: stripped }, version, props.fetchFn)
      : await createArchitectureLayer({ name: stripped }, props.fetchFn)
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
    } else if (outcome.rejection.problemType === 'validation-failed' && outcome.rejection.invalidFields.includes('name')) {
      nameError.value = t('metamodel.layer.nameInvalid')
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
  <form class="flex flex-col gap-4" novalidate data-testid="layer-form" @submit.prevent="submit()">
    <UAlert v-if="failure" data-testid="layer-failure" color="error" variant="subtle" :description="failure" />
    <CollisionWarning v-if="stale" @overwrite="submit(current?.version)" @discard="emit('cancel')">
      <p v-if="current">{{ current.name }}</p>
      <p v-else>{{ t('metamodel.collision.deleted') }}</p>
    </CollisionWarning>
    <UFormField :label="t('metamodel.layer.name')" name="name" :error="nameError" required>
      <UInput v-model="name" data-testid="layer-name" class="w-full" autofocus />
    </UFormField>
    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="layer-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="layer-submit" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
