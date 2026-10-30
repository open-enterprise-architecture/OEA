import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import LoginPage from '../pages/LoginPage.vue'
import { routedFetch } from '../test/fetch'

// SCR-001 Login (UC-01, US-001, US-011, US-013, US-009): identity providers, the local login of the
// system admin account and of persons, and passkeys (docs/screens/SCREENS.md). The first story is the
// screen image.
const PROVIDERS = [
  { id: '0b6c1c58-5d8f-4a3a-9f39-2a4c8c1d0a11', name: 'Entra ID' },
  { id: '7f0e8f2a-1f1c-4c55-8d0f-4f9b6d3e2b22', name: 'Authentik' },
]

const INVALID = {
  status: 401,
  contentType: 'application/problem+json',
  body: { type: 'urn:oea:problem:invalid-credentials', title: 'Login failed', status: 401 },
}

function api(login: { status?: number; body: unknown; contentType?: string }): typeof fetch {
  return routedFetch({ '/api/v1/auth/oidc/providers': { body: PROVIDERS }, '/api/v1/auth/local/login': login })
}

const meta: Meta<typeof LoginPage> = {
  title: 'Screens/SCR-001 Login',
  component: LoginPage,
  args: { navigate: () => undefined },
}

export default meta
type Story = StoryObj<typeof meta>

export const WithProviders: Story = { args: { fetchFn: api(INVALID) } }

export const LocalOnly: Story = {
  args: {
    fetchFn: routedFetch({ '/api/v1/auth/oidc/providers': { body: [] }, '/api/v1/auth/local/login': INVALID }),
  },
}

export const FailedLogin: Story = {
  args: { fetchFn: api(INVALID) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.type(await canvas.findByLabelText(/User name or e-mail address/), 'anna@example.org')
    await userEvent.type(canvas.getByLabelText(/Password/), 'wrong-password')
    await userEvent.click(canvas.getByTestId('submit'))
    await expect(await canvas.findByTestId('login-failure')).toHaveTextContent('Login failed')
  },
}

const PASSKEY_SECOND_STEP = {
  status: 401,
  contentType: 'application/problem+json',
  body: {
    type: 'urn:oea:problem:second-factor-required',
    title: 'Second factor required',
    status: 401,
    loginTransaction: 'tx-1',
    secondFactors: ['totp', 'passkey'],
  },
}

export const SecondStepWithPasskey: Story = {
  args: { fetchFn: api(PASSKEY_SECOND_STEP) },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.type(await canvas.findByLabelText(/User name or e-mail address/), 'anna@example.org')
    await userEvent.type(canvas.getByLabelText(/Password/), 'chosen-password-456')
    await userEvent.click(canvas.getByTestId('submit'))
    await expect(await canvas.findByTestId('use-passkey')).toBeInTheDocument()
    await expect(canvas.getByTestId('second-step-hint')).toHaveTextContent('passkey or the code')
  },
}
