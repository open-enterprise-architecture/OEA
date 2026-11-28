<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { PropertyMapping } from '../api/metamodel'
import { joinTags, MAX_TAG_LENGTH, MAX_TAGS, normalizeTags, splitTags, type TagRefusal } from './diagram/dataObjects'

/** A candidate of a reference: the name shown and the ID of the entity as text. */
export interface ReferenceChoice {
  label: string
  value: string
}

// The input of one property value by its data type, shared by the dialogs "New entity" and
// "Modify entity" of SCR-061 (design D9 of us-040-entity-deltas, D6 of us-041-modified-delta) and
// the details panel of SCR-021 (design D3 of us-074-diagram-entity-details). A reference is chosen
// from the candidates `searchReferences` finds for a search term; each screen passes its own search
// (the baseline of a solution, the entities of a diagram). The value is text, as the contract
// sends it; the number field is turned to text here.
const props = defineProps<{
  mapping: PropertyMapping
  searchReferences?: (term: string) => Promise<ReferenceChoice[]>
  /** the name of the entity the current reference value names, shown before a search ran */
  selectedLabel?: string
  disabled?: boolean
  invalid?: boolean
  /** a text of one entry per line is edited as tags: at most 50, each of 1 to 100 characters (us-083-bpmn-data-objects D5) */
  tags?: boolean
}>()
const model = defineModel<string>()
const emit = defineEmits<{ tagRefused: [reason: TagRefusal | undefined] }>()

const found = ref<ReferenceChoice[]>([])

// tag mode: the stored text holds one tag per line; what is refused is reported to the screen, which tells it
const tagList = computed(() => splitTags(model.value))
function setTags(list: string[] | undefined) {
  const outcome = normalizeTags(list ?? [])
  emit('tagRefused', outcome.refused)
  model.value = joinTags(outcome.tags)
}

// the current value stays selectable when a search does not list it
const references = computed(() => {
  const value = model.value
  if (value && value !== '' && !found.value.some((entry) => entry.value === value)) {
    return [{ label: props.selectedLabel ?? value, value }, ...found.value]
  }
  return found.value
})

function enumItems() {
  return [...(props.mapping.propertyDefinition.enumValues ?? [])]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((entry) => ({ label: entry.value, value: entry.value }))
}

async function search(term = '') {
  if (props.searchReferences) {
    found.value = await props.searchReferences(term)
  }
}

onMounted(() => {
  if (props.mapping.propertyDefinition.dataType === 'reference') void search()
})
</script>

<template>
  <div v-if="props.tags && props.mapping.propertyDefinition.dataType === 'text'" data-testid="tag-input">
    <UInputTags
      :model-value="tagList"
      class="w-full"
      :max="MAX_TAGS"
      :max-length="MAX_TAG_LENGTH"
      :disabled="disabled"
      :highlight="invalid"
      @update:model-value="setTags"
      @invalid="emit('tagRefused', 'count')"
    />
  </div>
  <UTextarea v-else-if="props.mapping.propertyDefinition.dataType === 'text'" v-model="model" class="w-full" :rows="2" autoresize :disabled="disabled" :highlight="invalid" />
  <UInput
    v-else-if="props.mapping.propertyDefinition.dataType === 'integer'"
    :model-value="model"
    type="number"
    class="w-full"
    :disabled="disabled"
    :highlight="invalid"
    @update:model-value="(value: unknown) => (model = value === null || value === undefined ? '' : String(value))"
  />
  <USwitch
    v-else-if="props.mapping.propertyDefinition.dataType === 'boolean'"
    :model-value="model === 'true'"
    :disabled="disabled"
    @update:model-value="(on: boolean) => (model = on ? 'true' : 'false')"
  />
  <UInput v-else-if="props.mapping.propertyDefinition.dataType === 'date'" v-model="model" type="date" class="w-full" :disabled="disabled" :highlight="invalid" />
  <USelect v-else-if="props.mapping.propertyDefinition.dataType === 'enum'" v-model="model" :items="enumItems()" class="w-full" :disabled="disabled" :highlight="invalid" />
  <USelectMenu
    v-else-if="props.mapping.propertyDefinition.dataType === 'reference'"
    v-model="model"
    :items="references"
    value-key="value"
    :ignore-filter="true"
    class="w-full"
    :disabled="disabled"
    :highlight="invalid"
    @update:search-term="(term: string) => search(term)"
  />
  <UInput v-else v-model="model" class="w-full" :disabled="disabled" :highlight="invalid" />
</template>
