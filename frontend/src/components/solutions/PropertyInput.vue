<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { PropertyMapping } from '../../api/metamodel'
import { fetchSolutionLandscape } from '../../api/solutions'

// The input of one property value by its data type, shared by the dialogs "New entity" and
// "Modify entity" of SCR-061 (design D9 of us-040-entity-deltas, D6 of us-041-modified-delta). A
// reference is chosen from the baseline, filtered by the referenced metatype. The value is text, as
// the contract sends it; the number field is turned to text here.
const props = defineProps<{ mapping: PropertyMapping; solutionId: string; fetchFn?: typeof fetch }>()
const model = defineModel<string>()

const references = ref<{ label: string; value: string }[]>([])

function enumItems() {
  return [...(props.mapping.propertyDefinition.enumValues ?? [])]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((entry) => ({ label: entry.value, value: entry.value }))
}

async function searchReferences(term = '') {
  const refMetatype = props.mapping.propertyDefinition.refMetatype?.id
  if (!refMetatype) return
  const outcome = await fetchSolutionLandscape(props.solutionId, { q: term, metatypeId: refMetatype, pageSize: 50 }, props.fetchFn)
  if (outcome.ok) {
    references.value = outcome.value.items.map((entry) => ({ label: entry.name, value: String(entry.id) }))
  }
}

onMounted(() => {
  if (props.mapping.propertyDefinition.dataType === 'reference') void searchReferences()
})
</script>

<template>
  <UTextarea v-if="props.mapping.propertyDefinition.dataType === 'text'" v-model="model" class="w-full" :rows="2" autoresize />
  <UInput
    v-else-if="props.mapping.propertyDefinition.dataType === 'integer'"
    :model-value="model"
    type="number"
    class="w-full"
    @update:model-value="(value: unknown) => (model = value === null || value === undefined ? '' : String(value))"
  />
  <USwitch
    v-else-if="props.mapping.propertyDefinition.dataType === 'boolean'"
    :model-value="model === 'true'"
    @update:model-value="(on: boolean) => (model = on ? 'true' : 'false')"
  />
  <UInput v-else-if="props.mapping.propertyDefinition.dataType === 'date'" v-model="model" type="date" class="w-full" />
  <USelect v-else-if="props.mapping.propertyDefinition.dataType === 'enum'" v-model="model" :items="enumItems()" class="w-full" />
  <USelectMenu
    v-else-if="props.mapping.propertyDefinition.dataType === 'reference'"
    v-model="model"
    :items="references"
    value-key="value"
    :ignore-filter="true"
    class="w-full"
    @update:search-term="(term: string) => searchReferences(term)"
  />
  <UInput v-else v-model="model" class="w-full" />
</template>
