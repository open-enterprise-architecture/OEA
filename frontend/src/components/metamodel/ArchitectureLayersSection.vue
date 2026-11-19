<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteArchitectureLayer, LAYER_LIMIT, updateArchitectureLayer, type ArchitectureLayer } from '../../api/metamodel'
import { label, labelKeys } from '../../i18n/labels'
import { metamodel, reloadMetamodel } from '../../metamodel/store'
import ArchitectureLayerDialog from './ArchitectureLayerDialog.vue'
import DeleteDialog from './DeleteDialog.vue'
import { rejectionMessage, scopeLabel } from './messages'

// The architecture layers of SCR-030 (change s17-architecture-layers D4; US-057 AC4, REQ-053
// BR-04): the list in its order with origin and number of entity types, creating, renaming,
// moving and deleting organization layers. Imported layers come from packages and have no actions.
const props = defineProps<{ canWrite: boolean; fetchFn?: typeof fetch }>()
const { t } = useI18n()

const editing = ref<{ layer?: ArchitectureLayer }>()
const deleting = ref<ArchitectureLayer>()
const failure = ref<string>()

const full = computed(() => metamodel.layers.length >= LAYER_LIMIT)

function shown(layer: ArchitectureLayer): string {
  return label(labelKeys.architectureLayer(layer.id), layer.name)
}

async function saved() {
  editing.value = undefined
  await reloadMetamodel(props.fetchFn)
}

/** Moves the layer one position up or down; the name is sent unchanged, the version guards against a parallel change. */
async function move(layer: ArchitectureLayer, offset: -1 | 1) {
  failure.value = undefined
  try {
    const outcome = await updateArchitectureLayer(
      layer.id,
      { name: layer.name, sortOrder: layer.sortOrder + offset },
      layer.version,
      props.fetchFn,
    )
    if (!outcome.ok) {
      failure.value =
        outcome.rejection.problemType === 'stale-version' ? t('metamodel.collision.reloaded') : rejectionMessage(outcome.rejection)
    }
  } catch {
    failure.value = t('common.backendUnreachable')
  }
  await reloadMetamodel(props.fetchFn)
}

function inUse(count: number | undefined): string {
  return t('metamodel.layer.inUse', count ?? 0)
}
</script>

<template>
  <section class="flex flex-col gap-3">
    <div class="flex items-center justify-between gap-2">
      <div class="flex flex-col gap-1">
        <h2 class="font-semibold text-highlighted">{{ t('metamodel.layer.list', { count: metamodel.layers.length }) }}</h2>
        <p class="text-sm text-muted" data-testid="layer-hint">{{ full ? t('metamodel.layer.limitReached', { limit: LAYER_LIMIT }) : t('metamodel.layer.newHint') }}</p>
      </div>
      <UButton
        v-if="canWrite"
        data-testid="layer-new"
        size="sm"
        icon="i-lucide-plus"
        :disabled="full"
        :label="t('metamodel.new')"
        @click="editing = {}"
      />
    </div>
    <UAlert v-if="failure" data-testid="layer-list-failure" color="error" variant="subtle" :description="failure" />
    <ul class="divide-y divide-default rounded-lg border border-default" data-testid="layer-list">
      <li v-for="(layer, index) in metamodel.layers" :key="layer.id" data-testid="layer-row" class="flex items-center gap-3 px-3 py-2">
        <span class="flex min-w-0 flex-1 flex-col">
          <span class="truncate font-medium text-highlighted" data-testid="layer-name-shown">{{ shown(layer) }}</span>
          <span class="text-xs text-muted" data-testid="layer-count">{{ t('metamodel.layer.metatypeCount', { count: layer.metatypeCount }) }}</span>
        </span>
        <span class="text-xs text-muted" data-testid="layer-scope">{{ scopeLabel(layer.scope, layer.sourcePackage) }}</span>
        <template v-if="canWrite && layer.scope === 'organization'">
          <UButton
            data-testid="layer-up"
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-arrow-up"
            :disabled="index === 0"
            :aria-label="t('metamodel.layer.moveUp')"
            @click="move(layer, -1)"
          />
          <UButton
            data-testid="layer-down"
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-arrow-down"
            :disabled="index === metamodel.layers.length - 1"
            :aria-label="t('metamodel.layer.moveDown')"
            @click="move(layer, 1)"
          />
          <UButton data-testid="layer-edit" size="xs" color="neutral" variant="ghost" icon="i-lucide-pencil" :aria-label="t('metamodel.layer.edit')" @click="editing = { layer }" />
          <UButton data-testid="layer-delete" size="xs" color="error" variant="ghost" icon="i-lucide-trash-2" :aria-label="t('metamodel.layer.delete')" @click="deleting = layer" />
        </template>
      </li>
      <li v-if="metamodel.layers.length === 0" class="px-3 py-4 text-center text-sm text-muted">{{ t('metamodel.layer.none') }}</li>
    </ul>
    <p v-if="canWrite && metamodel.layers.some((layer) => layer.scope === 'imported')" class="text-sm text-muted" data-testid="layer-imported-hint">
      {{ t('metamodel.layer.imported') }}
    </p>

    <UModal
      :open="editing !== undefined"
      :title="editing?.layer ? t('metamodel.layer.edit') : t('metamodel.layer.new')"
      @update:open="(open: boolean) => !open && (editing = undefined)"
    >
      <template #body>
        <ArchitectureLayerDialog v-if="editing" :layer="editing.layer" :fetch-fn="fetchFn" @saved="saved" @cancel="editing = undefined" />
      </template>
    </UModal>
    <DeleteDialog
      :open="deleting !== undefined"
      :title="t('metamodel.layer.delete')"
      :description="deleting ? t('metamodel.layer.deleteHint', { name: deleting.name }) : ''"
      :version="deleting?.version"
      :in-use="inUse"
      :run="(version) => deleteArchitectureLayer(deleting!.id, version, fetchFn)"
      @update:open="(open: boolean) => !open && (deleting = undefined)"
      @stale="reloadMetamodel(fetchFn)"
      @done="reloadMetamodel(fetchFn)"
    >
      <template #current>{{ t('metamodel.collision.changed', { name: deleting?.name }) }}</template>
    </DeleteDialog>
  </section>
</template>
