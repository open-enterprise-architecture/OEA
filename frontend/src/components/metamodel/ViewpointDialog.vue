<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createViewpoint, INSTANCE_CONFIGURATION, updateViewpoint, type Viewpoint } from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel } from '../../metamodel/store'
import CollisionWarning from './CollisionWarning.vue'
import { rejectionMessage } from './messages'

// Creating and changing a generic viewpoint in SCR-031 (US-090; design D4 of us-090-viewpoints):
// name, description, and the element and connection metatypes of the instance as checkboxes. A
// stale version shows the collision warning with the current state.
const props = defineProps<{ viewpoint?: Viewpoint; current?: Viewpoint; fetchFn?: typeof fetch }>()
const emit = defineEmits<{ saved: []; cancel: []; stale: [] }>()
const { t } = useI18n()

const NAME_MAX = 255
const DESCRIPTION_MAX = 1000

const form = reactive({
  name: props.viewpoint?.name ?? '',
  description: props.viewpoint?.description ?? '',
  metatypeIds: (props.viewpoint?.allowedMetatypes ?? []).map((metatype) => metatype.id),
  connectionTypeIds: (props.viewpoint?.allowedRelations ?? []).map((metatype) => metatype.id),
})
const errors = ref<Record<string, string>>({})
const failure = ref<string>()
const submitting = ref(false)
const stale = ref(false)

// the metamodel store holds the instance metatypes; extensions name their own configuration
const instance = computed(() => metamodel.metatypes.filter((metatype) => !metatype.configId || metatype.configId === INSTANCE_CONFIGURATION))
const elements = computed(() =>
  instance.value.filter((metatype) => !metatype.isConnection).map((metatype) => ({ value: metatype.id, label: label(labelKeys.metatype(metatype.id), metatype.name) })),
)
const connections = computed(() =>
  instance.value.filter((metatype) => metatype.isConnection).map((metatype) => ({ value: metatype.id, label: label(labelKeys.metatype(metatype.id), metatype.name) })),
)
const valid = computed(
  () =>
    form.name.trim().length > 0 &&
    form.name.trim().length <= NAME_MAX &&
    form.description.length <= DESCRIPTION_MAX &&
    form.metatypeIds.length > 0,
)

async function submit(version = props.viewpoint?.version) {
  failure.value = undefined
  errors.value = {}
  if (!valid.value) {
    return
  }
  submitting.value = true
  const body = {
    name: form.name.trim(),
    description: form.description.trim() ? form.description : null,
    metatypeIds: form.metatypeIds,
    connectionTypeIds: form.connectionTypeIds,
  }
  try {
    const outcome = props.viewpoint
      ? await updateViewpoint(props.viewpoint.id, body, version, props.fetchFn)
      : await createViewpoint(body, props.fetchFn)
    if (outcome.ok) {
      stale.value = false
      emit('saved')
    } else if (outcome.rejection.problemType === 'stale-version') {
      stale.value = true
      emit('stale')
    } else if (outcome.rejection.status === 422 && outcome.rejection.invalidFields[0]) {
      errors.value = { [outcome.rejection.invalidFields[0]]: outcome.rejection.detail ?? t('metamodel.viewpoint.invalid') }
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
  <form class="flex flex-col gap-4" novalidate data-testid="viewpoint-form" @submit.prevent="submit()">
    <UAlert v-if="failure" data-testid="viewpoint-failure" color="error" variant="subtle" :description="failure" />
    <CollisionWarning v-if="stale" @overwrite="submit(props.current?.version)" @discard="emit('cancel')">
      <p v-if="props.current">{{ props.current.name }}<span v-if="props.current.description"> – {{ props.current.description }}</span></p>
      <p v-else>{{ t('metamodel.collision.deleted') }}</p>
    </CollisionWarning>
    <UFormField :label="t('metamodel.viewpoint.name')" name="name" :error="errors.name" required>
      <UInput v-model="form.name" data-testid="viewpoint-name" class="w-full" :maxlength="NAME_MAX" autofocus />
    </UFormField>
    <UFormField
      :label="t('metamodel.viewpoint.description')"
      name="description"
      :error="form.description.length <= DESCRIPTION_MAX ? undefined : t('metamodel.viewpoint.descriptionTooLong', { max: DESCRIPTION_MAX })"
    >
      <UTextarea v-model="form.description" data-testid="viewpoint-description" :rows="2" class="w-full" />
    </UFormField>
    <UFormField :label="t('metamodel.viewpoint.metatypes')" :error="errors.metatypeIds" :help="t('metamodel.viewpoint.metatypesHint')" required>
      <UCheckboxGroup v-model="form.metatypeIds" data-testid="viewpoint-metatypes" :items="elements" class="max-h-56 overflow-y-auto" />
    </UFormField>
    <UFormField :label="t('metamodel.viewpoint.connections')" :error="errors.connectionTypeIds">
      <UCheckboxGroup v-if="connections.length > 0" v-model="form.connectionTypeIds" data-testid="viewpoint-connections" :items="connections" class="max-h-40 overflow-y-auto" />
      <p v-else class="text-sm text-muted">{{ t('metamodel.viewpoint.noConnections') }}</p>
    </UFormField>
    <div v-if="!stale" class="flex justify-end gap-2">
      <UButton data-testid="viewpoint-cancel" color="neutral" variant="ghost" :label="t('common.cancel')" @click="emit('cancel')" />
      <UButton type="submit" data-testid="viewpoint-submit" :disabled="!valid" :loading="submitting" :label="t('metamodel.save')" />
    </div>
  </form>
</template>
