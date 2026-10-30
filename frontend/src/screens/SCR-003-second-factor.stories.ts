import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import { onBeforeMount } from 'vue'
import { useRouter } from 'vue-router'
import SecondFactorPage from '../pages/SecondFactorPage.vue'
import { session } from '../session/store'
import { routedFetch } from '../test/fetch'

// SCR-003 Set up auth method (UC-03, US-021, US-022, US-025, US-029): the choice between a passkey
// and an authenticator app, offered after the password login of a person without a second factor
// (docs/screens/SCREENS.md). The first story is the screen image.
const ENROLLMENT = {
  credentialId: '7f0c1a2b-3c4d-4e5f-8a9b-0c1d2e3f4a5b',
  secret: 'JBSWY3DPEHPK3PXP',
  otpauthUri: 'otpauth://totp/OEA%3Aanna%40example.org?secret=JBSWY3DPEHPK3PXP&issuer=OEA&algorithm=SHA1&digits=6&period=30',
  expiresAt: '2026-10-30T09:10:00Z',
}

const FETCH = routedFetch({
  '/api/v1/me/second-factors/totp': { status: 201, body: ENROLLMENT },
  [`/api/v1/me/second-factors/totp/${ENROLLMENT.credentialId}/confirm`]: {
    status: 400,
    contentType: 'application/problem+json',
    body: { type: 'urn:oea:problem:invalid-totp-code', title: 'Invalid code', status: 400 },
  },
})

const PERSON = {
  principal: { principalType: 'person' as const, displayName: 'Anna Example' },
  mfaSatisfied: false,
  roles: [],
  permissions: [],
}

function page(
  path: string,
  principal: 'person' | 'system-admin-account' = 'person',
  requiredAction: 'enroll-second-factor' | null = null,
): Story {
  return {
    render: () => ({
      components: { SecondFactorPage },
      setup: () => {
        const router = useRouter()
        onBeforeMount(() => {
          session.me =
            principal === 'person'
              ? { ...PERSON, requiredAction }
              : { ...PERSON, principal: { principalType: 'system-admin-account' as const, displayName: 'admin' } }
          session.loaded = true
          return router.push(path)
        })
        return { fetchFn: FETCH }
      },
      template: '<SecondFactorPage :fetch-fn="fetchFn" />',
    }),
  }
}

const meta: Meta<typeof SecondFactorPage> = {
  title: 'Screens/SCR-003 Second Factor',
  component: SecondFactorPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const OfferAfterLogin: Story = page('/me/second-factor?offer=1')

export const FromTheProfile: Story = page('/me/second-factor')

export const QrCodeShown: Story = {
  ...page('/me/second-factor'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByText('Authenticator app'))
    await userEvent.type(await canvas.findByLabelText(/Name of the authenticator/), 'Phone')
    await userEvent.click(canvas.getByTestId('start'))
    await expect(await canvas.findByTestId('secret')).toHaveTextContent(ENROLLMENT.secret)
  },
}

export const WrongCode: Story = {
  ...page('/me/second-factor', 'system-admin-account'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByTestId('start'))
    await userEvent.type(await canvas.findByLabelText(/Code from the app/), '000000')
    await userEvent.click(canvas.getByTestId('confirm'))
    await expect(await canvas.findByTestId('enrollment-failure')).toHaveTextContent('The code is not valid')
  },
}

// US-031 AC2: the organization enforces a second factor; the offer cannot be skipped
export const RequiredByTheOrganization: Story = {
  ...page('/me/second-factor', 'person', 'enroll-second-factor'),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByTestId('enrollment-required')).toHaveTextContent('requires a second factor')
    await expect(canvas.queryByTestId('skip')).toBeNull()
  },
}
