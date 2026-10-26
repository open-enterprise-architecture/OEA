// Walkthrough of the documented flows from a fresh instance to the catalog table (ADR-045 rule 5,
// REQ-196 AC4; change s2c-manual-release, design D1). Every `shoot` produces a screenshot that a
// manual page references by name. The tests assert only what they need to reach each screen.

import { execFileSync } from 'node:child_process'
import { join } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { backendUrl, realmUser, repositoryDir, shoot, totp } from './support'

const setupToken = process.env.OEA_WALKTHROUGH_SETUP_TOKEN
const admin = { loginName: 'admin', password: 'walkthrough-admin-password' }
const starterPackage = 'oea-starter-togaf-classic'

// Fixed sample data of the catalog table (design D1, flow 5)
const applications = ['CRM System', 'Customer Portal', 'Data Warehouse', 'ERP Finance', 'HR Self-Service']

test.describe.configure({ mode: 'serial' })

async function logInAtKeycloak(page: Page, username: string): Promise<void> {
  const { password, otpSecret } = realmUser(username)
  await page.goto('/login')
  await page.getByRole('button', { name: /^Log in with / }).click()
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  if (otpSecret) {
    await page.locator('#otp').fill(totp(otpSecret))
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

  await page.getByRole('link', { name: 'Set up the instance' }).click()
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

test('catalogs', async ({ page }) => {
  await logInAtKeycloak(page, 'dev-architect')

  // Sample entities through the batch API with the session of the browser (manual: usage/entities-batch)
  const me = await (await page.request.get('/api/v1/me')).json()
  const metatypes = await (await page.request.get('/api/v1/metamodel/metatypes')).json()
  const application = metatypes.find((metatype: { name: string }) => metatype.name === 'Application Component')
  expect(application, 'entity type Application Component of the starter package').toBeTruthy()
  const batch = await page.request.post('/api/v1/entities/batch', {
    headers: { 'X-XSRF-TOKEN': me.csrfToken, 'X-Agent-Id': 'walkthrough' },
    data: {
      strategy: 'upsert',
      entities: applications.map((name) => ({
        name,
        metatypeId: application.id,
        externalId: `walkthrough:${name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`,
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
  await page.getByRole('option', { name: 'Application Component' }).click()
  await page.getByTestId('catalog-scope').getByText('Shared', { exact: true }).click()
  await shoot(page, 'catalog-create')

  await page.getByTestId('create-catalog-submit').click()
  await expect(page.getByTestId('catalog-table')).toBeVisible()
  await expect(page.getByTestId('catalog-table')).toContainText('CRM System')
  await shoot(page, 'catalog-table')

  await page.getByTestId('back-to-catalogs').click()
  await expect(page.getByTestId('catalog-list')).toBeVisible()
  await shoot(page, 'catalogs')
})
