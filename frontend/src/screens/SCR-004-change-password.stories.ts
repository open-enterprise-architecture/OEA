import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import { onBeforeMount } from 'vue'
import ChangePasswordPage from '../pages/ChangePasswordPage.vue'
import { session } from '../session/store'
import { routedFetch } from '../test/fetch'

// SCR-004 Change password (UC-03, US-023, US-026, US-027, REQ-173 AC2): required after a login with
// a temporary or expired password (docs/screens/SCREENS.md). The first story is the screen image.
const ME = {
  principal: { principalType: 'person' as const, displayName: 'Anna Example' },
  mfaSatisfied: false,
  roles: [],
  permissions: [],
}

function page(requiredAction: 'change-password' | null, fetchFn: typeof fetch): Story {
  return {
    render: () => ({
      components: { ChangePasswordPage },
      setup: () => {
        onBeforeMount(() => {
          session.me = { ...ME, requiredAction }
          session.loaded = true
        })
        return { fetchFn }
      },
      template: '<ChangePasswordPage :fetch-fn="fetchFn" />',
    }),
  }
}

const meta: Meta<typeof ChangePasswordPage> = {
  title: 'Screens/SCR-004 Change Password',
  component: ChangePasswordPage,
}

export default meta
type Story = StoryObj<typeof meta>

const WRONG_CURRENT = routedFetch({
  '/api/v1/me/password': {
    status: 403,
    contentType: 'application/problem+json',
    body: { type: 'urn:oea:problem:invalid-credentials', title: 'Password not changed', status: 403 },
  },
})

export const RequiredAfterTemporaryPassword: Story = page('change-password', WRONG_CURRENT)

export const OwnPassword: Story = page(null, WRONG_CURRENT)

export const PasswordsDiffer: Story = {
  ...page('change-password', WRONG_CURRENT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.type(await canvas.findByLabelText(/Current password/), 'initial-password-123')
    await userEvent.type(canvas.getByLabelText(/^New password/), 'chosen-password-456')
    await userEvent.type(canvas.getByLabelText(/Repeat the new password/), 'other-password-789')
    await userEvent.click(canvas.getByTestId('change-password'))
    await expect(await canvas.findByTestId('password-failure')).toHaveTextContent('differ')
  },
}

export const WrongCurrentPassword: Story = {
  ...page('change-password', WRONG_CURRENT),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.type(await canvas.findByLabelText(/Current password/), 'wrong-password-000')
    await userEvent.type(canvas.getByLabelText(/^New password/), 'chosen-password-456')
    await userEvent.type(canvas.getByLabelText(/Repeat the new password/), 'chosen-password-456')
    await userEvent.click(canvas.getByTestId('change-password'))
    await expect(await canvas.findByTestId('password-failure')).toHaveTextContent('current password is not correct')
  },
}

const POLICY_VIOLATION = routedFetch({
  '/api/v1/me/password/suggestion': { body: { password: 'k#8Qz!r2Lw$9pXa4' } },
  '/api/v1/me/password': {
    status: 422,
    contentType: 'application/problem+json',
    body: {
      type: 'urn:oea:problem:password-policy-violation',
      title: 'Password policy not met',
      status: 422,
      invalidFields: ['minLength', 'requireUppercase', 'requireDigits', 'requireSpecialChars'],
    },
  },
})

export const Suggestion: Story = {
  ...page('change-password', POLICY_VIOLATION),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByTestId('suggest-password'))
    await expect(await canvas.findByDisplayValue('k#8Qz!r2Lw$9pXa4')).toBeInTheDocument()
  },
}

export const PolicyViolation: Story = {
  ...page('change-password', POLICY_VIOLATION),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.type(await canvas.findByLabelText(/Current password/), 'initial-password-123')
    await userEvent.type(canvas.getByLabelText(/^New password/), 'chosen-password-abc')
    await userEvent.type(canvas.getByLabelText(/Repeat the new password/), 'chosen-password-abc')
    await userEvent.click(canvas.getByTestId('change-password'))
    await expect(await canvas.findByTestId('unmet-rules')).toHaveTextContent('It needs a digit.')
  },
}
