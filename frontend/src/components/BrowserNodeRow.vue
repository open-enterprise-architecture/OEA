<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { browserItemRoute, type BrowserItem } from '../api/navigation'
import type { BrowserNode } from '../navigation/browserNodes'
import { browser } from '../navigation/store'

// One node of the solution browser (SCR-055; design D4 of us-132-solution-browser): the expand toggle, the
// name with its count, the children, and for a category its objects page by page. Names are shown as text only, and
// links are built from kind and ID.

const props = defineProps<{ node: BrowserNode; depth: number }>()
const emit = defineEmits<{ toggle: [node: BrowserNode]; more: [node: BrowserNode]; navigate: [] }>()
const { t } = useI18n()

const open = (): boolean => browser.expanded.has(props.node.key)
const empty = (): boolean => props.node.count === 0
const state = () => browser.categories[props.node.key]
const canExpand = (): boolean => !empty() && (props.node.children.length > 0 || props.node.query !== undefined)

const NODE_ICONS: Record<BrowserNode['kind'], string> = {
  'current-state': 'i-lucide-layers',
  plateau: 'i-lucide-flag',
  projects: 'i-lucide-folder-kanban',
  solution: 'i-lucide-milestone',
  components: 'i-lucide-box',
  connections: 'i-lucide-cable',
  catalogs: 'i-lucide-table-2',
}

const DELTA_COLORS = { new: 'success', modified: 'warning', retiring: 'error' } as const

function deltaLabel(item: BrowserItem): string {
  return item.deltaType ? t(`browser.delta.${item.deltaType}`) : ''
}

function itemIcon(item: BrowserItem): string {
  return item.kind === 'catalog' ? 'i-lucide-table-2' : item.kind === 'delta' ? 'i-lucide-git-compare' : 'i-lucide-box'
}
</script>

<template>
  <li data-testid="browser-node" :data-node-key="node.key" role="treeitem" :aria-level="depth + 1" :aria-expanded="canExpand() ? open() : undefined">
    <div class="flex items-center gap-1 rounded-md pe-1 hover:bg-elevated" :style="{ paddingInlineStart: `${depth * 0.75}rem` }">
      <UButton
        data-testid="browser-toggle"
        size="xs"
        color="neutral"
        variant="ghost"
        :icon="open() ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
        :aria-label="t(open() ? 'navigation.collapse' : 'navigation.expand', { name: node.label })"
        :class="canExpand() ? '' : 'invisible'"
        :tabindex="canExpand() ? undefined : -1"
        @click="emit('toggle', node)"
      />
      <UIcon :name="NODE_ICONS[node.kind]" class="size-4 shrink-0" :class="empty() ? 'text-dimmed' : 'text-muted'" aria-hidden="true" />
      <button
        type="button"
        data-testid="browser-label"
        class="min-w-0 flex-1 truncate py-1 text-start text-sm"
        :class="empty() ? 'text-muted' : 'text-default'"
        :data-empty="empty() ? 'true' : undefined"
        @click="canExpand() && emit('toggle', node)"
      >
        {{ node.label }}<template v-if="node.count !== undefined"> ({{ node.count }})</template>
      </button>
      <RouterLink
        v-if="node.solutionId"
        data-testid="browser-solution-link"
        :to="`/solutions/${encodeURIComponent(node.solutionId)}`"
        class="shrink-0 rounded-md p-1 text-muted hover:text-default"
        :aria-label="t('browser.openSolution', { name: node.label })"
        @click="emit('navigate')"
      >
        <UIcon name="i-lucide-external-link" class="size-4" aria-hidden="true" />
      </RouterLink>
    </div>
    <ul v-if="open() && canExpand()" role="group">
      <BrowserNodeRow v-for="child in node.children" :key="child.key" :node="child" :depth="depth + 1" @toggle="emit('toggle', $event)" @more="emit('more', $event)" @navigate="emit('navigate')" />
      <template v-if="node.query">
        <li
          v-for="item in state()?.items ?? []"
          :key="`${item.kind}:${item.id}`"
          data-testid="browser-item"
          :data-item-kind="item.kind"
          role="treeitem"
          :aria-level="depth + 2"
        >
          <div class="flex items-center gap-1 rounded-md pe-1 hover:bg-elevated" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
            <UIcon :name="itemIcon(item)" class="size-4 shrink-0 text-muted" aria-hidden="true" />
            <RouterLink
              v-if="browserItemRoute(item)"
              data-testid="browser-item-link"
              :to="browserItemRoute(item)!"
              class="min-w-0 flex-1 truncate py-1 text-sm text-default hover:underline"
              active-class="font-semibold text-highlighted"
              @click="emit('navigate')"
            >
              {{ item.name }}
            </RouterLink>
            <span v-else class="min-w-0 flex-1 truncate py-1 text-sm text-muted">{{ item.name }}</span>
            <UBadge v-if="item.metatypeAbbreviation" data-testid="browser-item-abbreviation" color="neutral" variant="outline" size="sm" :label="item.metatypeAbbreviation" />
            <UBadge
              v-if="item.deltaType"
              data-testid="browser-item-delta"
              :data-delta-type="item.deltaType"
              :color="DELTA_COLORS[item.deltaType]"
              variant="subtle"
              size="sm"
              :label="deltaLabel(item)"
            />
          </div>
        </li>
        <li v-if="state()?.failed" data-testid="browser-category-failed" class="flex flex-col items-start gap-1 py-1 text-sm text-error" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
          {{ t('browser.loadFailed') }}
          <UButton size="xs" color="neutral" variant="outline" :label="t('navigation.retry')" @click="emit('more', node)" />
        </li>
        <li v-else-if="state()?.loading" data-testid="browser-category-loading" class="py-1 text-sm text-muted" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
          {{ t('browser.loading') }}
        </li>
        <li v-else-if="state() && state()!.items.length < state()!.total" :style="{ paddingInlineStart: `${(depth + 1) * 0.75 + 1.5}rem` }">
          <UButton data-testid="browser-load-more" size="xs" color="neutral" variant="link" :label="t('browser.loadMore')" @click="emit('more', node)" />
        </li>
      </template>
    </ul>
  </li>
</template>
