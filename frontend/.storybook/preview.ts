import ui from '@nuxt/ui/vue-plugin'
import { setup, type Preview } from '@storybook/vue3-vite'
import { createMemoryHistory, createRouter } from 'vue-router'
import '../src/style.css'

setup((app) => {
  app.use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] }))
  app.use(ui)
})

const preview: Preview = {
  parameters: {
    // Accessibility violations fail the story test run (ADR-031)
    a11y: { test: 'error' },
  },
  decorators: [() => ({ template: '<UApp><story /></UApp>' })],
}

export default preview
