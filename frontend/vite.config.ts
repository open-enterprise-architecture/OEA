/// <reference types="vitest/config" />
import ui from '@nuxt/ui/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
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
  ],
  server: {
    proxy: {
      // Backend in compose.dev.yaml (service "backend") or a local ./mvnw spring-boot:run
      '/api': process.env.OEA_API_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
  },
})
