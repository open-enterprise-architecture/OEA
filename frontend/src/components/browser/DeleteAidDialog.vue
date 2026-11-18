<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BrowserItem } from '../../api/navigation'
import { rateLimitMessage, type Rejection } from '../../api/problem'

// "Delete" of a structuring aid (US-136 AC3; design D5 of us-135-component-hierarchy): the warning names the number of
// children, which move to the root level and are not deleted.

const props = defineProps<{ item: BrowserItem | null; remove: (item: BrowserItem) => Promise<Rejection | undefined> }>()
const open = defineModel<boolean>('open', { required: true })
const { t } = useI18n()

const error = ref<string>()
const busy = ref(false)

watch(open, (value) => {
  if (value) {
    error.value = undefined
  }
})

async function confirm() {
  if (!props.item) {
    return
  }
  busy.value = true
  error.value = undefined
  const rejection = await props.remove(props.item)
  busy.value = false
  if (!rejection) {
    open.value = false
  } else if (rejection.status === 409) {
    error.value = t('browser.deleteAidBlocked')
  } else if (rejection.status === 412) {
    error.value = t('browser.stale')
  } else {
    error.value = rateLimitMessage(rejection) ?? t('browser.changeFailed')
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="t('browser.deleteAidTitle', { name: item?.name ?? '' })">
    <template #body>
      <div data-testid="delete-aid-dialog" class="flex flex-col gap-4">
        <p data-testid="delete-aid-warning" class="text-sm">{{ t('browser.deleteAidWarning', { count: item?.childCount ?? 0 }, item?.childCount ?? 0) }}</p>
        <p v-if="error" data-testid="delete-aid-error" role="alert" class="text-sm text-error">{{ error }}</p>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" :label="t('common.cancel')" @click="open = false" />
          <UButton data-testid="delete-aid-confirm" color="error" :loading="busy" :label="t('browser.deleteAid')" @click="confirm" />
        </div>
      </div>
    </template>
  </UModal>
</template>
