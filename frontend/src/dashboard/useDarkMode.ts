import { onBeforeUnmount, onMounted, ref } from 'vue'

/** Whether the page shows the dark theme (`dark` class on the root element, as Nuxt UI sets it). */
export function useDarkMode() {
  const dark = ref(false)
  let observer: MutationObserver | undefined
  const read = () => {
    dark.value = document.documentElement.classList.contains('dark')
  }
  onMounted(() => {
    read()
    observer = new MutationObserver(read)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  })
  onBeforeUnmount(() => observer?.disconnect())
  return dark
}
