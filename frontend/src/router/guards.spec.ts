import { describe, expect, it } from 'vitest'
import type { Me } from '../api/session'
import { passwordChangeTarget, portalTarget } from './guards'

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

  it('keeps a session that must enroll a second factor on the enrollment (US-031)', () => {
    const me = { ...ME, requiredAction: 'enroll-second-factor' as const }
    expect(passwordChangeTarget('/catalogs', me)).toBe('/me/second-factor')
    expect(passwordChangeTarget('/me', me)).toBe('/me/second-factor')
    expect(passwordChangeTarget('/me/second-factor', me)).toBe(true)
    expect(passwordChangeTarget('/login', me)).toBe(true)
  })
})

// spec portal/portal-catalogs "Landing page of a portal reader"
describe('portalTarget', () => {
  const reader = (permissions: string[]): Me => ({ ...ME, permissions: permissions.map((permission) => ({ permission, grantedBy: [] })) })

  it('sends a reader without entity:read from the start page and the catalogs to the portal', () => {
    const petra = reader(['portal:read'])
    expect(portalTarget('/', petra)).toBe('/portal')
    expect(portalTarget('/catalogs', petra)).toBe('/portal')
    expect(portalTarget('/catalogs/c0000000-0000-4000-8000-000000000001', petra)).toBe('/portal')
  })

  it('leaves the portal, the profile and the login to the portal reader', () => {
    const petra = reader(['portal:read'])
    expect(portalTarget('/portal', petra)).toBe(true)
    expect(portalTarget('/portal/catalogs/c0000000-0000-4000-8000-000000000001', petra)).toBe(true)
    expect(portalTarget('/me', petra)).toBe(true)
    expect(portalTarget('/login', petra)).toBe(true)
  })

  it('lets readers with entity:read and sessions without portal:read through', () => {
    expect(portalTarget('/catalogs', reader(['portal:read', 'entity:read']))).toBe(true)
    expect(portalTarget('/catalogs', reader([]))).toBe(true)
    expect(portalTarget('/', undefined)).toBe(true)
  })
})
