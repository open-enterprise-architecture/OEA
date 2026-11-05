import type { Rejection } from '../../api/problem'
import { rateLimitMessage } from '../../api/problem'
import { currentLanguage, i18n } from '../../i18n'

/** A chapter of the published user manual in the current language (ADR-045). */
export function manualUrl(chapter: string): string {
  return `https://open-enterprise-architecture.github.io/OEA/${currentLanguage() === 'de' ? 'de/' : ''}${chapter}`
}

/** Scope of metamodel content with the source package, for the scope badge (REQ-032 AC5). */
export function scopeLabel(scope: string | undefined, sourcePackage?: { packageId: string; version: string } | null): string {
  const t = i18n.global.t
  if (scope === 'organization') {
    return t('metamodel.scope.organization')
  }
  if (scope === 'imported' && sourcePackage) {
    return t('metamodel.scope.importedFrom', { package: sourcePackage.packageId, version: sourcePackage.version })
  }
  return t(scope === 'imported' ? 'metamodel.scope.imported' : 'metamodel.scope.builtIn')
}

/**
 * The message for a rejected metamodel write (design D4 of scr-030-metamodel-configuration);
 * `stale-version` is handled by the collision warning and `invalidFields` at the fields.
 */
export function rejectionMessage(rejection: Rejection): string {
  const t = i18n.global.t
  const limited = rateLimitMessage(rejection)
  if (limited) {
    return limited
  }
  switch (rejection.problemType) {
    case 'name-taken':
      return t('metamodel.error.nameTaken')
    case 'read-only-metatype':
      return t('metamodel.error.readOnly')
    case 'in-use':
      return rejection.count ? t('metamodel.error.inUseEntities', rejection.count) : t('metamodel.error.inUse')
    case 'conflict':
      return t('metamodel.error.conflict')
    case 'validation-failed':
      return t('metamodel.error.invalid')
    case 'stale-version':
      return t('metamodel.collision.title')
    case 'metamodel-locked':
      return t('metamodel.lock.rejected')
    default:
      if (rejection.status === 403) {
        return t('metamodel.error.forbidden')
      }
      if (rejection.status === 404) {
        return t('metamodel.error.notFound')
      }
      return t('metamodel.error.failed')
  }
}
