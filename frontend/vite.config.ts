/// <reference types="vitest/config" />
import { fileURLToPath } from 'node:url'
import ui from '@nuxt/ui/vite'
import { storybookTest } from '@storybook/addon-vitest/vitest-plugin'
import vue from '@vitejs/plugin-vue'
import { playwright } from '@vitest/browser-playwright'
import { defineConfig } from 'vite'
import { checkBundledIcons } from './bundled-icons.ts'
import { thirdPartyLicenses } from './third-party-licenses.ts'

// OEA frontend (ADR-011, ADR-040). Nuxt UI runs as a Vite plugin without Nuxt.
export default defineConfig({
  plugins: [
    vue(),
    ui({
      ui: {
        colors: { primary: 'blue', neutral: 'slate' },
      },
      // Offline at runtime (REQ-075): icons are bundled at build time, never fetched from the Iconify API
      icon: { clientBundle: { scan: true } },
    }),
    // ADR-043: list and license texts of the bundled third-party packages
    thirdPartyLicenses(),
    // Every icon used in src/ must be in the bundle; the CSP blocks the Iconify API fallback (#62)
    checkBundledIcons(),
  ],
  server: {
    proxy: {
      // Backend in compose.dev.yaml (service "backend") or a local ./mvnw spring-boot:run
      '/api': process.env.OEA_API_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
  test: {
    projects: [
      // Unit tests (npm test); the inline setting of the Nuxt UI plugin reaches only the root config
      {
        extends: true,
        test: {
          name: 'unit',
          environment: 'jsdom',
          include: ['src/**/*.spec.ts', 'bundled-icons.spec.ts'],
          server: { deps: { inline: ['@nuxt/ui'] } },
        },
      },
      // Every story as a test: rendering, play function and accessibility check (ADR-031, npm run test:stories)
      {
        extends: true,
        plugins: [storybookTest({ configDir: fileURLToPath(new URL('./.storybook', import.meta.url)) })],
        test: {
          name: 'storybook',
          browser: { enabled: true, headless: true, provider: playwright(), instances: [{ browser: 'chromium' }] },
        },
      },
    ],
  },
})
