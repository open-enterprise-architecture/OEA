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

/** Paths of the work area that a reader without entity:read leaves for the portal (design D7 of s9-portal-catalogs). */
const WORK_AREA = [/^\/$/, /^\/catalogs(\/[^/]+)?\/?$/]

/**
 * The portal for a session with portal:read and without entity:read that opens the start page or
 * the catalogs, or `true` to continue.
 */
export function portalTarget(path: string, me: Me | undefined): string | true {
  const permissions = new Set(me?.permissions.map((entry) => entry.permission) ?? [])
  if (permissions.has('portal:read') && !permissions.has('entity:read') && WORK_AREA.some((pattern) => pattern.test(path))) {
    return '/portal'
  }
  return true
}
