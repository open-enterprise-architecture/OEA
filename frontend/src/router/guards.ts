import type { Me } from '../api/session'

/** Paths a session with a required password change may open (design D8 of us-011-local-password). */
const ALLOWED_WHILE_CHANGING = new Set(['/me/password', '/login'])

/** The redirect for a session that must change its password first, or `true` to continue. */
export function passwordChangeTarget(path: string, me: Me | undefined): string | true {
  if (me?.requiredAction === 'change-password' && !ALLOWED_WHILE_CHANGING.has(path)) {
    return '/me/password'
  }
  return true
}
