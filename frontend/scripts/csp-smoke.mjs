// Browser smoke test of the built web portal under its content security policy (spec
// platform/portal-delivery "Portal runs under the policy"; design D6 of s9-portal-catalogs).
// Serves dist/ through `vite preview` with the policy the backend sends (read from the backend's
// resource, so the two cannot drift), answers the API with canned responses for a portal reader,
// opens the login, the portal and a shared catalog in Chromium and fails on any policy violation.
// Usage: npm run build && npm run test:csp
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { preview } from 'vite'

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..')
const policy = readFileSync(join(frontend, '../backend/src/main/resources/portal/content-security-policy.txt'), 'utf8').trim()
const catalogId = 'c0000000-0000-4000-8000-000000000001'

const me = {
  principal: { principalType: 'person', id: 9, displayName: 'Petra' },
  mfaSatisfied: true,
  roles: [],
  permissions: [{ permission: 'portal:read', grantedBy: [] }],
  csrfToken: 'csrf-smoke',
}
const catalogs = [
  {
    id: catalogId,
    name: 'Application Inventory',
    primaryMetatypeId: '5a000000-0000-4000-8000-000000000001',
    primaryMetatypeName: 'Application Component',
    scope: 'instance',
    createdBy: { principalType: 'person', id: 7, displayName: 'Kurt' },
    createdAt: '2026-10-19T10:00:00Z',
  },
]
const query = {
  columns: [
    { key: 'name', label: 'Name', sortable: true, filterType: 'text' },
    { key: 'status', label: 'Status', sortable: true, filterType: 'enum', enumValues: ['active', 'planned'] },
  ],
  rows: [
    { entityId: 1, values: { name: 'CRM System', status: 'active' }, joinResults: {} },
    { entityId: 2, values: { name: 'ERP', status: 'planned' }, joinResults: {} },
  ],
  joins: [],
  page: { page: 0, pageSize: 50, totalElements: 2, totalPages: 1 },
  sort: null,
}

/** The canned answer of an API path; everything else is a 404 problem. */
function answer(path) {
  if (path === '/api/v1/me') return { body: me }
  if (path === '/api/v1/portal/catalogs') return { body: catalogs }
  if (path.startsWith(`/api/v1/portal/catalogs/${catalogId}/query`)) return { body: query }
  return { status: 404, body: { type: 'about:blank', title: 'Not found', status: 404 }, contentType: 'application/problem+json' }
}

const server = await preview({
  root: frontend,
  logLevel: 'warn',
  preview: { port: 0, strictPort: false, host: '127.0.0.1', headers: { 'Content-Security-Policy': policy } },
})
const base = server.resolvedUrls.local[0].replace(/\/$/, '')
const browser = await chromium.launch()
const violations = []
try {
  const page = await browser.newPage()
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      console.error(`CSP violation: ${event.violatedDirective} ${event.blockedURI} ${event.sourceFile}:${event.lineNumber}`)
    })
  })
  page.on('console', (message) => {
    if (/Content Security Policy|CSP violation/i.test(message.text())) violations.push(`${page.url()}: ${message.text()}`)
  })
  page.on('pageerror', (error) => violations.push(`${page.url()}: page error ${error.message}`))
  await page.route('**/api/**', (route) => {
    const { status = 200, body, contentType = 'application/json' } = answer(new URL(route.request().url()).pathname)
    return route.fulfill({ status, contentType, body: JSON.stringify(body) })
  })
  for (const [path, text] of [['/login', null], ['/portal', 'Application Inventory'], [`/portal/catalogs/${catalogId}`, 'CRM System']]) {
    const response = await page.goto(base + path)
    if (response?.headers()['content-security-policy'] !== policy) {
      violations.push(`${path}: the preview did not send the portal policy`)
    }
    await page.waitForLoadState('networkidle')
    if (text) await page.getByText(text).first().waitFor({ timeout: 10_000 })
    console.log(`✓ ${path}`)
  }
} finally {
  await browser.close()
  await server.close()
}
if (violations.length > 0) {
  console.error('✗ content security policy violations:')
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('✓ the built portal runs under its content security policy')
