import { listOwnSecondFactors } from '../api/session'

/** Path of the TOTP enrollment offered after a password login (design D7 of us-021-person-totp). */
export const SECOND_FACTOR_OFFER = '/me/second-factor?offer=1'

/**
 * Where a person goes after a password login without a second factor, or after the required
 * password change: the enrollment offer if the person has no active second factor yet
 * (US-021, owner decision 2026-10-30), otherwise the given target.
 */
export async function afterPasswordLogin(target: string, fetchFn: typeof fetch = fetch): Promise<string> {
  try {
    return (await listOwnSecondFactors(fetchFn)).length === 0 ? SECOND_FACTOR_OFFER : target
  } catch {
    return target
  }
}
