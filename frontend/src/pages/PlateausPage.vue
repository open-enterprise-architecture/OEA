<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchPlateaus, type Plateau } from '../api/plateaus'
import { rateLimitMessage, RequestRejected } from '../api/problem'
import PlateauDialog from '../components/solutions/PlateauDialog.vue'
import PlateauGoLiveDialog from '../components/solutions/PlateauGoLiveDialog.vue'
import { currentLanguage } from '../i18n'
import { refreshSession, session } from '../session/store'

// SCR-065 "Plateaus" (UC-11, US-084, US-085, US-089; design D8 of us-084-plateaus): the baseline
// highlighted, the target and transition plateaus with their planned date, and the realized ones
// collapsed. Holders of plateau:write create and edit plateaus, never a realized one; without a
// baseline the page offers the initial baseline (UC-11 A1).
const props = defineProps<{ fetchFn?: typeof fetch }>()
const { t } = useI18n()

const plateaus = ref<Plateau[]>([])
const loaded = ref(false)
const failure = ref<string>()
const editing = ref<{ plateau?: Plateau; initial?: boolean }>()
const realizedOpen = ref(false)
// the plateau whose go-live dialog is open (SCR-066, us-086-plateau-go-live)
const goingLive = ref<Plateau>()

const permissions = computed(() => new Set(session.me?.permissions.map((entry) => entry.permission) ?? []))
const canRead = computed(() => ['plateau:write', 'plateau:go-live', 'solution:write'].some((permission) => permissions.value.has(permission)))
const canWrite = computed(() => permissions.value.has('plateau:write'))
const canGoLive = computed(() => permissions.value.has('plateau:go-live'))
const baseline = computed(() => plateaus.value.find((plateau) => plateau.status === 'baseline'))
const planned = computed(() => plateaus.value.filter((plateau) => plateau.status === 'target' || plateau.status === 'transition'))
const realized = computed(() => plateaus.value.filter((plateau) => plateau.status === 'realized'))
const names = computed(() => new Map(plateaus.value.map((plateau) => [plateau.id, plateau.name])))

async function load() {
  try {
    plateaus.value = await fetchPlateaus(props.fetchFn)
    failure.value = undefined
  } catch (error) {
    failure.value = rateLimitMessage(error instanceof RequestRejected ? error.rejection : undefined) ?? t('plateaus.loadFailed')
  } finally {
    loaded.value = true
  }
}

onMounted(async () => {
  await refreshSession(props.fetchFn)
  if (!canRead.value) {
    loaded.value = true
    return
  }
  await load()
})

function date(value: string | null): string {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString(currentLanguage(), { dateStyle: 'medium' }) : ''
}

/** Only the plateau that succeeds the baseline goes live (owner decision 2026-11-17). */
function isNext(plateau: Plateau): boolean {
  return baseline.value !== undefined && plateau.succeeds === baseline.value.id
}

async function onWentLive() {
  goingLive.value = undefined
  realizedOpen.value = true
  await load()
}

async function onSaved() {
  editing.value = undefined
  await load()
}
</script>

<template>
  <main class="mx-auto flex max-w-4xl flex-col gap-6 p-8">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex flex-col gap-1">
        <h1 class="text-2xl font-semibold text-highlighted">{{ t('plateaus.title') }}</h1>
        <p class="text-sm text-muted">{{ t('plateaus.intro') }}</p>
      </div>
      <UButton
        v-if="canWrite && loaded && !failure && baseline"
        data-testid="plateau-new"
        icon="i-lucide-plus"
        :label="t('plateaus.new')"
        @click="editing = {}"
      />
    </div>

    <UAlert
      v-if="session.loaded && !session.me"
      data-testid="not-logged-in"
      color="neutral"
      variant="subtle"
      :description="t('profile.notLoggedIn')"
      :actions="[{ label: t('login.submit'), to: '/login' }]"
    />
    <UAlert
      v-else-if="session.me && !canRead"
      data-testid="plateaus-no-access"
      color="neutral"
      variant="subtle"
      :description="t('plateaus.noAccess')"
    />
    <UAlert v-if="failure" data-testid="plateaus-failure" color="error" variant="subtle" :description="failure" />

    <template v-if="canRead && loaded && !failure">
      <UAlert
        v-if="!baseline"
        data-testid="plateaus-no-baseline"
        color="info"
        variant="subtle"
        icon="i-lucide-info"
        :description="t('plateaus.noBaseline')"
        :actions="canWrite ? [{ label: t('plateaus.createBaseline'), onClick: () => (editing = { initial: true }) }] : []"
      />

      <section v-else class="flex flex-col gap-2" data-testid="plateaus-baseline">
        <h2 class="text-lg font-semibold text-highlighted">{{ t('plateaus.baselineTitle') }}</h2>
        <div class="flex items-start justify-between gap-4 rounded-lg border-2 border-primary p-4">
          <div class="flex min-w-0 flex-col gap-1">
            <span class="flex items-center gap-2">
              <span class="font-medium text-highlighted">{{ baseline.name }}</span>
              <UBadge color="primary" variant="solid" :label="t('plateaus.status.baseline')" />
            </span>
            <span v-if="baseline.description" class="text-sm text-muted">{{ baseline.description }}</span>
            <span v-if="baseline.validFrom" class="text-sm text-muted">{{ t('plateaus.since', { date: date(baseline.validFrom) }) }}</span>
          </div>
          <UButton
            v-if="canWrite"
            data-testid="plateau-edit"
            color="neutral"
            variant="ghost"
            size="sm"
            icon="i-lucide-pencil"
            :label="t('plateaus.edit')"
            @click="editing = { plateau: baseline }"
          />
        </div>
      </section>

      <section class="flex flex-col gap-2" data-testid="plateaus-planned">
        <h2 class="text-lg font-semibold text-highlighted">{{ t('plateaus.plannedTitle') }}</h2>
        <ul v-if="planned.length > 0" class="divide-y divide-default rounded-lg border border-default">
          <li v-for="plateau in planned" :key="plateau.id" data-testid="plateau-item" class="flex items-start justify-between gap-4 px-4 py-3">
            <div class="flex min-w-0 flex-col gap-1">
              <span class="flex flex-wrap items-center gap-2">
                <span class="font-medium text-highlighted">{{ plateau.name }}</span>
                <UBadge
                  :color="plateau.status === 'target' ? 'info' : 'neutral'"
                  variant="subtle"
                  :label="t(`plateaus.status.${plateau.status}`)"
                />
              </span>
              <span class="text-sm text-muted">
                {{ plateau.validFrom ? t('plateaus.plannedFor', { date: date(plateau.validFrom) }) : t('plateaus.noDate') }}
                · {{ t('plateaus.after', { name: names.get(plateau.succeeds ?? '') ?? '' }) }}
                · {{ t(`plateaus.basis.${plateau.basis}`) }}
                · {{ t('plateaus.solutions', { n: plateau.solutionCount }, plateau.solutionCount) }}
              </span>
            </div>
            <div class="flex shrink-0 gap-1">
              <UButton
                v-if="canGoLive && isNext(plateau)"
                data-testid="plateau-go-live"
                color="primary"
                variant="soft"
                size="sm"
                icon="i-lucide-rocket"
                :label="t('plateaus.goLive.action')"
                @click="goingLive = plateau"
              />
              <UButton
                v-if="canWrite"
                data-testid="plateau-edit"
                color="neutral"
                variant="ghost"
                size="sm"
                icon="i-lucide-pencil"
                :label="t('plateaus.edit')"
                @click="editing = { plateau }"
              />
            </div>
          </li>
        </ul>
        <p v-else data-testid="plateaus-no-target" class="text-sm text-muted">{{ t('plateaus.noTarget') }}</p>
      </section>

      <UCollapsible v-model:open="realizedOpen" data-testid="plateaus-realized" class="flex flex-col gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          class="self-start"
          :trailing-icon="realizedOpen ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
          :label="t('plateaus.realizedTitle', { n: realized.length })"
        />
        <template #content>
          <ul v-if="realized.length > 0" class="divide-y divide-default rounded-lg border border-default">
            <li v-for="plateau in realized" :key="plateau.id" data-testid="plateau-realized-item" class="flex flex-col gap-1 px-4 py-3">
              <span class="flex items-center gap-2">
                <span class="font-medium">{{ plateau.name }}</span>
                <UBadge color="neutral" variant="outline" icon="i-lucide-lock" :label="t('plateaus.status.realized')" />
              </span>
              <span class="text-sm text-muted">{{ t('plateaus.validRange', { from: date(plateau.validFrom), to: date(plateau.validTo) }) }}</span>
            </li>
          </ul>
          <p v-else class="text-sm text-muted">{{ t('plateaus.noRealized') }}</p>
        </template>
      </UCollapsible>
    </template>

    <UModal
      :open="goingLive !== undefined"
      :title="t('plateaus.goLive.title', { name: goingLive?.name ?? '' })"
      :description="t('plateaus.goLive.hint')"
      @update:open="(open: boolean) => !open && (goingLive = undefined)"
    >
      <template #body>
        <PlateauGoLiveDialog
          v-if="goingLive && baseline"
          :plateau="goingLive"
          :baseline="baseline"
          :fetch-fn="props.fetchFn"
          @done="onWentLive"
          @cancel="goingLive = undefined"
        />
      </template>
    </UModal>

    <UModal
      :open="editing !== undefined"
      :title="editing?.plateau ? t('plateaus.editTitle') : editing?.initial ? t('plateaus.createBaseline') : t('plateaus.new')"
      :description="t('plateaus.dialogHint')"
      @update:open="(open: boolean) => !open && (editing = undefined)"
    >
      <template #body>
        <PlateauDialog
          v-if="editing"
          :plateaus="plateaus"
          :plateau="editing.plateau"
          :initial="editing.initial"
          :fetch-fn="props.fetchFn"
          @saved="onSaved"
          @cancel="editing = undefined"
        />
      </template>
    </UModal>
  </main>
</template>
