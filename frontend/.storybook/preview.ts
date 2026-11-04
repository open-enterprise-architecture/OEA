import UApp from '@nuxt/ui/components/App.vue'
import ui from '@nuxt/ui/vue-plugin'
import { setup, type Preview } from '@storybook/vue3-vite'
import { createMemoryHistory, createRouter } from 'vue-router'
import { i18n, setLanguage } from '../src/i18n'
import { clearLabels } from '../src/i18n/labels'
import '../src/style.css'

setup((app) => {
  // every path matches, so that links of the screens resolve without a route table
  app.use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/:path(.*)*', component: { render: () => null } }] }))
  app.use(ui)
  // screen images stay in English (design D7 of us-140-dynamic-labels)
  setLanguage('en')
  app.use(i18n)
})

const preview: Preview = {
  parameters: {
    // Accessibility violations fail the story test run (ADR-031)
    a11y: { test: 'error' },
  },
  // UApp provides toasts, tooltips and overlays; runtime templates do not see auto-imported components
  decorators: [
    () => {
      // every story starts in English without metamodel labels; a story may switch the language
      setLanguage('en')
      clearLabels()
      return { components: { UApp }, template: '<UApp><story /></UApp>' }
    },
  ],
}

export default preview
