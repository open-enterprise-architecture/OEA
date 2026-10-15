<script setup lang="ts">
import { encode } from 'uqr'
import { computed } from 'vue'

// QR code rendered locally from the text (REQ-188): the otpauth URI never leaves the browser
const props = defineProps<{ value: string; label: string }>()

const qr = computed(() => encode(props.value, { border: 2 }))
const cells = computed(() =>
  qr.value.data.flatMap((row, y) => row.flatMap((dark, x) => (dark ? [{ x, y }] : []))),
)
</script>

<template>
  <svg
    role="img"
    :aria-label="label"
    :viewBox="`0 0 ${qr.size} ${qr.size}`"
    shape-rendering="crispEdges"
    class="size-48 bg-white"
  >
    <rect v-for="cell in cells" :key="`${cell.x}-${cell.y}`" :x="cell.x" :y="cell.y" width="1" height="1" fill="black" />
  </svg>
</template>
