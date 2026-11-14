import { i18n } from '../../i18n'
import { rateLimitMessage, type Rejection } from '../../api/problem'

/**
 * The message of a rejected service account request (design D8 of us-142-service-accounts): an
 * old authentication, a non-assignable role (SoD-4), a taken name, an inactive account.
 */
export function rejectionMessage(rejection: Rejection): string {
  const t = i18n.global.t
  if (rejection.problemType === 'reauthentication-required') {
    return t('serviceAccounts.reauthenticate')
  }
  if (rejection.status === 409) {
    return t('serviceAccounts.inactive')
  }
  if (rejection.status === 422 && rejection.invalidFields.includes('roles')) {
    return t('serviceAccounts.roleRejected')
  }
  if (rejection.status === 422 && rejection.invalidFields.includes('name')) {
    return t('serviceAccounts.nameTaken')
  }
  if (rejection.status === 403) {
    return t('serviceAccounts.forbidden')
  }
  return rateLimitMessage(rejection) ?? t('serviceAccounts.failed')
}
