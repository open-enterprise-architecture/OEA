import type { Me } from '../api/session'

/** Paths a session with a required password change may open (design D8 of us-011-local-password). */
const ALLOWED_WHILE_CHANGING = new Set(['/me/password', '/login'])

/** Paths a session with a required enrollment may open (design D6 of us-031-second-factor-enforcement). */
const ALLOWED_WHILE_ENROLLING = new Set(['/me/second-factor', '/login'])

/**
 * The redirect for a session that must change its password or enroll a second factor first, or
 * `true` to continue.
 */
export function passwordChangeTarget(path: string, me: Me | undefined): string | true {
  if (me?.requiredAction === 'change-password' && !ALLOWED_WHILE_CHANGING.has(path)) {
    return '/me/password'
  }
  if (me?.requiredAction === 'enroll-second-factor' && !ALLOWED_WHILE_ENROLLING.has(path)) {
    return '/me/second-factor'
  }
  return true
}
