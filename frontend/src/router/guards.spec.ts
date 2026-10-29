import { describe, expect, it } from 'vitest'
import type { Me } from '../api/session'
import { passwordChangeTarget } from './guards'

const ME: Me = {
  principal: { principalType: 'person', displayName: 'Anna' },
  mfaSatisfied: false,
  roles: [],
  permissions: [],
}

// Design D8 of us-011-local-password: a temporary password leads only to the password change
describe('passwordChangeTarget', () => {
  it('sends a session with a required password change to the change page', () => {
    const me = { ...ME, requiredAction: 'change-password' as const }
    expect(passwordChangeTarget('/catalogs', me)).toBe('/me/password')
    expect(passwordChangeTarget('/me', me)).toBe('/me/password')
    expect(passwordChangeTarget('/me/password', me)).toBe(true)
    expect(passwordChangeTarget('/login', me)).toBe(true)
  })

  it('lets every other session through', () => {
    expect(passwordChangeTarget('/catalogs', ME)).toBe(true)
    expect(passwordChangeTarget('/catalogs', { ...ME, requiredAction: null })).toBe(true)
    expect(passwordChangeTarget('/catalogs', undefined)).toBe(true)
  })
})
