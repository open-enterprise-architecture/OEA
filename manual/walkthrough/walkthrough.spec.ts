// Walkthrough of the documented flows from a fresh instance to the catalog table (ADR-045 rule 5,
// REQ-196 AC4; change s2c-manual-release, design D1). Every `shoot` produces a screenshot that a
// manual page references by name. The tests assert only what they need to reach each screen.

import { execFileSync } from 'node:child_process'
import { join } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { backendUrl, keycloakTotp, language, realmUser, repositoryDir, shoot, totp } from './support'

const setupToken = process.env.OEA_WALKTHROUGH_SETUP_TOKEN
const admin = { loginName: 'admin', password: 'walkthrough-admin-password' }
const starterPackage = 'oea-starter-togaf-classic'

// Texts the walkthrough selects by, in the language of the run (us-140-dynamic-labels, design D8):
// static texts in English or German, labels of the starter package
const shown =
  language === 'de'
    ? { applicationComponent: 'Anwendungskomponente', shared: 'Geteilt', phasingOut: 'Auslaufend', selectionList: 'Auswahlliste' }
    : { applicationComponent: 'Application Component', shared: 'Shared', phasingOut: 'Phasing out', selectionList: 'Selection list' }

// Fixed sample data of the catalog table (design D1, flow 5)
const applications = [
  { name: 'CRM System', status: 'active' },
  { name: 'Customer Portal', status: 'active' },
  { name: 'Data Warehouse', status: 'phasing-out' },
  { name: 'ERP Finance', status: 'active' },
  { name: 'HR Self-Service', status: 'planned' },
]

test.describe.configure({ mode: 'serial' })

/** The last TOTP code used per realm user. */
const usedCodes = new Map<string, string>()

async function logInAtKeycloak(page: Page, username: string): Promise<void> {
  const { password, otpSecret } = realmUser(username)
  await page.goto('/login')
  await page.locator('[data-testid^="provider-"]').first().click()
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  if (otpSecret) {
    // Keycloak accepts a code only once: a second login of the same user within the same 30-second
    // step waits for the next code
    let code = keycloakTotp(otpSecret)
    if (usedCodes.get(username) === code) {
      await page.waitForTimeout(30_000 - (Date.now() % 30_000) + 1_000)
      code = keycloakTotp(otpSecret)
    }
    usedCodes.set(username, code)
    await page.locator('#otp').fill(code)
    await page.locator('#kc-login').click()
  }
  await expect(page.getByTestId('header-logout')).toBeVisible()
}

test('setup and first administrator', async ({ page }) => {
  test.skip(!setupToken, 'OEA_WALKTHROUGH_SETUP_TOKEN is required (scripts/walkthrough.sh reads it from the container)')

  await page.goto('/')
  await expect(page.getByTestId('setup-hint')).toBeVisible()
  await expect(page.getByTestId('health')).toHaveText('UP')
  await shoot(page, 'setup-required')

  await page.getByTestId('setup-hint').getByRole('link').click()
  await page.getByTestId('setupToken').fill(setupToken!)
  await page.getByTestId('loginName').fill(admin.loginName)
  await page.getByTestId('password').fill(admin.password)
  await page.getByTestId('passwordConfirmation').fill(admin.password)
  await page.getByTestId('instanceSlug').fill('acme')
  await shoot(page, 'setup-form', [page.getByTestId('setupToken')])

  await page.getByTestId('submit').click()
  await expect(page.getByTestId('setup-success')).toBeVisible()
  await shoot(page, 'setup-done')
})

test('system admin login and second factor', async ({ page }) => {
  await page.goto('/login')
  await page.getByTestId('loginName').fill(admin.loginName)
  await page.getByTestId('password').fill(admin.password)
  await shoot(page, 'login')

  await page.getByTestId('submit').click()
  await expect(page).toHaveURL(/\/me$/)
  await page.getByTestId('set-up-second-factor').click()
  await page.getByTestId('start').click()
  const secret = (await page.getByTestId('secret').textContent())!.trim()
  await shoot(page, 'second-factor-enroll', [page.getByTestId('qr-code'), page.getByTestId('secret')])

  await page.getByTestId('code').fill(totp(secret))
  await page.getByTestId('confirm').click()
  await expect(page.getByTestId('enrollment-confirmed')).toBeVisible()
})

test('identity provider, persons and group mappings', () => {
  // scripts/dev_seed.py logs in with the password (the setup phase is still running), enrolls its own
  // TOTP credential for the first binding (ADR-042) and creates provider, persons and mappings
  execFileSync('python3', [join(repositoryDir, 'scripts', 'dev_seed.py'), '--url', backendUrl, '--login-name', admin.loginName], {
    env: { ...process.env, OEA_ADMIN_PASSWORD: admin.password },
    stdio: 'inherit',
  })
})

test('starter configuration', async ({ page }) => {
  await logInAtKeycloak(page, 'dev-metamodel-admin')
  await page.goto('/')
  await expect(page.getByTestId(`starter-${starterPackage}`)).toBeVisible()
  await shoot(page, 'starter-import')

  await page.getByTestId(`import-${starterPackage}`).click()
  await expect(page.getByTestId('starter-imported')).toBeVisible()
})

test('metamodel configuration', async ({ page }) => {
  await logInAtKeycloak(page, 'dev-metamodel-admin')
  await page.getByTestId('header-metamodel').click()
  await expect(page.getByTestId('metatype-list')).toBeVisible()

  // An entity type of the organization (manual: administration/metamodel-editing)
  await page.getByTestId('metatype-new').click()
  await page.getByTestId('metatype-name').fill('Security Zone')
  await page.getByTestId('metatype-abbreviation').fill('SZ')
  await page.getByTestId('metatype-sort-order').fill('20')
  await shoot(page, 'metamodel-entity-type-new')
  await page.getByTestId('metatype-submit').click()
  await expect(page.getByTestId('metatype-title')).toContainText('Security Zone')

  // A selection list with three values
  await page.getByTestId('metamodel-sections').getByRole('tab').nth(1).click()
  await page.getByTestId('property-new').click()
  await page.getByTestId('property-name').fill('Trust Level')
  await page.getByTestId('property-data-type').click()
  await page.getByRole('option', { name: shown.selectionList }).click()
  for (const value of ['low', 'medium', 'high']) {
    await page.getByTestId('property-value-input').fill(value)
    await page.getByTestId('property-value-add').click()
  }
  await shoot(page, 'metamodel-property-new')
  await page.getByTestId('property-submit').click()
  await expect(page.getByTestId('property-table')).toContainText('Trust Level')

  // The property becomes mandatory for Security Zone
  await page.getByTestId('metamodel-sections').getByRole('tab').nth(0).click()
  await page.getByTestId('metatype-item').filter({ hasText: 'Security Zone' }).click()
  await page.getByTestId('mapping-new').click()
  await page.getByTestId('mapping-property').click()
  await page.getByRole('option', { name: 'Trust Level' }).click()
  await page.getByTestId('mapping-necessity').locator('[value="mandatory"]').click()
  await shoot(page, 'metamodel-mapping-new', [page.getByTestId('mapping-valid-from')])
  await page.getByTestId('mapping-submit').click()
  await expect(page.getByTestId('mapping-table')).toContainText('Trust Level')
  await shoot(page, 'metamodel-entity-type', [page.getByTestId('mapping-state').locator('..')])

  // The German name of the entity type
  await page.getByTestId('metamodel-sections').getByRole('tab').nth(3).click()
  await page.getByTestId('translation-input-Security Zone').fill('Sicherheitszone')
  await shoot(page, 'metamodel-translations')
  await page.getByTestId('translation-save').click()
  await expect(page.getByTestId('translation-notice')).toBeVisible()
})

test('catalogs', async ({ page }) => {
  await logInAtKeycloak(page, 'dev-architect')

  // Sample entities through the batch API with the session of the browser (manual: usage/entities-batch)
  const me = await (await page.request.get('/api/v1/me')).json()
  const metatypes = await (await page.request.get('/api/v1/metamodel/metatypes')).json()
  const application = metatypes.find((metatype: { name: string }) => metatype.name === 'Application Component')
  expect(application, 'entity type Application Component of the starter package').toBeTruthy()
  const status = application.propertyMappings.find(
    (mapping: { propertyDefinition: { name: string } }) => mapping.propertyDefinition.name === 'Status',
  )
  expect(status, 'property Status of Application Component').toBeTruthy()
  // Same origin as the browser: state-changing requests are checked against the portal origin (REQ-187)
  const origin = new URL(page.url()).origin
  const batch = await page.request.post('/api/v1/entities/batch?atomic=true', {
    headers: { Origin: origin, 'X-XSRF-TOKEN': me.csrfToken, 'X-Agent-Id': 'walkthrough' },
    data: {
      strategy: 'upsert',
      entities: applications.map(({ name, status: value }) => ({
        name,
        metatypeId: application.id,
        externalId: `walkthrough:${name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`,
        properties: [{ propertyDefId: status.propertyDefinition.id, value }],
      })),
    },
  })
  expect(batch.ok(), await batch.text()).toBeTruthy()

  await page.getByTestId('header-catalogs').click()
  await expect(page.getByTestId('new-catalog')).toBeVisible()
  await page.getByTestId('new-catalog').click()
  await page.getByTestId('catalog-name').fill('Application landscape')
  await page.getByTestId('catalog-description').fill('All application components of the company')
  await page.getByTestId('catalog-type').click()
  await page.getByRole('option', { name: shown.applicationComponent }).click()
  await page.getByTestId('catalog-scope').getByText(shown.shared, { exact: true }).click()
  await shoot(page, 'catalog-create')

  await page.getByTestId('create-catalog-submit').click()
  await expect(page.getByTestId('catalog-table')).toBeVisible()
  await expect(page.getByTestId('catalog-table')).toContainText('CRM System')
  await expect(page.getByTestId('catalog-table')).toContainText(shown.phasingOut)
  await shoot(page, 'catalog-table')

  await page.getByTestId('back-to-catalogs').click()
  await expect(page.getByTestId('catalog-list')).toBeVisible()
  await shoot(page, 'catalogs')
})
