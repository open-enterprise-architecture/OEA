import type { StorybookConfig } from '@storybook/vue3-vite'

// Screen prototypes and component stories with real Vue/Nuxt UI components (ADR-031, ADR-040).
// Screen stories live in src/screens/SCR-NNN-<short-name>.stories.ts (docs/screens/SCREENS.md).
const config: StorybookConfig = {
  stories: ['../src/**/*.stories.ts'],
  addons: ['@storybook/addon-a11y'],
  framework: { name: '@storybook/vue3-vite', options: {} },
  core: { disableTelemetry: true },
}

export default config
