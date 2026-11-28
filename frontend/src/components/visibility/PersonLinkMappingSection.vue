<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchPropertyDefinitions } from '../../api/metamodel'
import { ACCOUNT_ATTRIBUTES, fetchPersonLinkMapping, updatePersonLinkMapping, type PersonLinkMapping } from '../../api/organization'
import { rateLimitMessage } from '../../api/problem'

// The account mapping of SCR-032 next to the person metatype (REQ-127; design D5 of us-075-lane-assignments): the string
// property of the person metatype and the account attribute (e-mail address or IdP subject) through which an account is
// linked to a person element. The property must be write-restricted (422 naming `propertyDefId` otherwise).
const props = defineProps<{ fetchFn?: typeof fetch }>()
// USelect does not accept an empty value, so "no mapping" has its own value
const NONE = 'none'
const { t } = useI18n()

const mapping = ref<PersonLinkMapping>()
const properties = ref<{ id: string; name: string }[]>([])
const property = ref<string>(NONE)
const attribute = ref<(typeof ACCOUNT_ATTRIBUTES)[number]>(ACCOUNT_ATTRIBUTES[0])
const failure = ref<string>()
const notice = ref<string>()
const saving = ref(false)

const propertyItems = computed(() => [
  { value: NONE, label: t('visibility.mapping.none') },
  ...properties.value.map((entry) => ({ value: entry.id, label: entry.name })),
])
const attributeItems = computed(() => ACCOUNT_ATTRIBUTES.map((value) => ({ value, label: t(`visibility.mapping.attribute.${value}`) })))
const unchanged = computed(() => (mapping.value?.propertyDefId ?? NONE) === property.value && (property.value === NONE || mapping.value?.accountAttribute === attribute.value))

onMounted(async () => {
  try {
    mapping.value = await fetchPersonLinkMapping(props.fetchFn)
    property.value = mapping.value.propertyDefId ?? NONE
    attribute.value = mapping.value.accountAttribute ?? ACCOUNT_ATTRIBUTES[0]
    properties.value = (await fetchPropertyDefinitions(props.fetchFn))
      .filter((entry) => entry.dataType === 'string')
      .map((entry) => ({ id: entry.id, name: entry.label ?? entry.name }))
  } catch {
    failure.value = mapping.value ? t('visibility.mapping.loadFailed') : undefined
  }
})

async function save() {
  failure.value = undefined
  notice.value = undefined
  saving.value = true
  try {
    const result = await updatePersonLinkMapping(property.value === NONE ? null : property.value, property.value === NONE ? null : attribute.value, props.fetchFn)
    if (result.ok) {
      mapping.value = result.value
      notice.value = t('visibility.mapping.saved')
    } else if (result.rejection.invalidFields.includes('propertyDefId')) {
      failure.value = t('visibility.mapping.invalidProperty')
    } else if (result.rejection.status === 403) {
      failure.value = t('visibility.mapping.forbidden')
    } else {
      failure.value = rateLimitMessage(result.rejection) ?? t('visibility.mapping.saveFailed')
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section v-if="mapping" class="flex max-w-xl flex-col gap-2" data-testid="person-link-mapping" aria-labelledby="person-link-mapping-title">
    <h2 id="person-link-mapping-title" class="font-semibold text-highlighted">{{ t('visibility.mapping.title') }}</h2>
    <p class="text-sm text-muted">{{ t('visibility.mapping.hint') }}</p>
    <div class="flex flex-wrap items-end gap-3">
      <UFormField :label="t('visibility.mapping.property')">
        <USelect v-model="property" :items="propertyItems" class="w-64" data-testid="person-link-mapping-property" />
      </UFormField>
      <UFormField :label="t('visibility.mapping.attributeLabel')">
        <USelect v-model="attribute" :items="attributeItems" :disabled="property === NONE" class="w-56" data-testid="person-link-mapping-attribute" />
      </UFormField>
      <UButton :loading="saving" :disabled="unchanged" :label="t('visibility.mapping.save')" data-testid="person-link-mapping-save" @click="save" />
    </div>
    <UAlert v-if="failure" data-testid="person-link-mapping-failure" color="error" variant="subtle" :description="failure" />
    <UAlert v-if="notice" data-testid="person-link-mapping-notice" color="success" variant="subtle" :description="notice" />
  </section>
</template>
