import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, within } from 'storybook/test'
import ProfilePage from '../pages/ProfilePage.vue'
import { routedFetch } from '../test/fetch'

// SCR-005 Profile and security settings (UC-03, US-029 AC3): the principal, its roles and its
// authenticator apps with removal (docs/screens/SCREENS.md). The first story is the screen image.
const ME = {
  principal: { principalType: 'person' as const, displayName: 'Anna Example' },
  email: 'anna@example.org',
  mfaSatisfied: true,
  roles: [
    { role: { id: 'viewer', name: 'Viewer', privileged: false }, source: 'local' as const, effective: true },
    { role: { id: 'metamodel-admin', name: 'Metamodel Admin', privileged: true }, source: 'local' as const, effective: true },
  ],
  permissions: [{ permission: 'entity:read', grantedBy: [] }],
}

const FACTORS = [
  { credentialId: 'c1', type: 'totp', label: 'Phone', createdAt: '2026-10-30T08:00:00Z', lastUsedAt: '2026-10-30T08:30:00Z' },
  { credentialId: 'c2', type: 'totp', label: 'Backup phone', createdAt: '2026-10-30T09:00:00Z', lastUsedAt: null },
]

function page(me: object, factors: object[]): Story {
  const fetchFn = routedFetch({ '/api/v1/me': { body: me }, '/api/v1/me/second-factors': { body: factors } })
  return {
    render: () => ({
      components: { ProfilePage },
      setup: () => ({ fetchFn }),
      template: '<ProfilePage :fetch-fn="fetchFn" />',
    }),
  }
}

const meta: Meta<typeof ProfilePage> = {
  title: 'Screens/SCR-005 Profile',
  component: ProfilePage,
}

export default meta
type Story = StoryObj<typeof meta>

export const TwoAuthenticators: Story = {
  ...page(ME, FACTORS),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findAllByTestId('factor-label')).toHaveLength(2)
  },
}

export const WithoutSecondFactor: Story = {
  ...page({ ...ME, mfaSatisfied: false }, []),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByTestId('no-second-factor')).toBeInTheDocument()
  },
}
