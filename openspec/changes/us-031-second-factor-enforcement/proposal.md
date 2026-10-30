# Proposal

## Why

Since changes 3 and 4 of S5, persons can set up authenticator apps and passkeys, but nobody has to: a password alone still opens an account. An organization that requires two factors cannot enforce it ([US-031](../../../requirements/user-stories/US-031-configure-2fa-enforcement.md), [REQ-020](../../../requirements/req/REQ-020-enforce-second-factor.md)), nor exempt low-risk roles ([US-028](../../../requirements/user-stories/US-028-role-based-2fa-exemption.md), [REQ-029](../../../requirements/req/REQ-029-role-based-2fa-exemption.md)).

The owner decided on 2026-10-30:

- administrators with `settings:manage` switch the enforcement and maintain the exempt roles through the API; the operator can force the enforcement on, and then it cannot be switched off;
- the enforcement covers local logins only; at an IdP login the identity provider decides about the second factor.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-01: Login](../../../requirements/use-cases/UC-01-login.md); [UC-03: Set up authentication method](../../../requirements/use-cases/UC-03-set-up-authentication-method.md) |
| User Stories | [US-031: Configure 2FA enforcement](../../../requirements/user-stories/US-031-configure-2fa-enforcement.md); [US-028: Role-based 2FA exemption](../../../requirements/user-stories/US-028-role-based-2fa-exemption.md) |
| Requirements | [REQ-020](../../../requirements/req/REQ-020-enforce-second-factor.md); [REQ-029](../../../requirements/req/REQ-029-role-based-2fa-exemption.md); [REQ-171](../../../requirements/req/REQ-171-mfa-privileged-accounts.md); [REQ-173](../../../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC5 |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (admin-class permissions, SoD-8), [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md), [ADR-013](../../../adrs/ADR-013-api-style.md), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S5 change 5 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-031, US-028 (configuration):** the settings `enforce2FA` and `twoFactorExemptRoles` are the fields `enforced` and `exemptRoles` of the new resource `GET/PUT /admin/settings/second-factor-policy` (`settings:manage`), not of `/admin/config/auth`; the operator setting `oea.auth.second-factor.enforced` forces the enforcement on (owner decision 2026-10-30).
- **REQ-020 AC1, US-031 AC1 ("password without second factor rejected"):** the password login of persons stays possible, but with active enforcement it never gives access by itself: a person with a second factor must use it (changes 3 and 4), and a person without one gets a session that only allows the enrollment (US-031 AC2).
- **REQ-020, REQ-029 (scope):** the enforcement covers the password login of persons; IdP logins follow the identity provider (owner decision 2026-10-30), and the system admin account follows ADR-042 and REQ-171 unchanged (US-031 AC3, REQ-029 AC5).
- **REQ-029 (exemption):** a person is exempt if they hold at least one role through a local role assignment and every such role is in the exempt list; a person without roles is not exempt.
- **REQ-173 AC5:** after a factor reset, the next login of a person who is not exempt requires the enrollment while the enforcement is active.

## What Changes

- **Second-factor policy (US-031, US-028):**
  - New `GET/PUT /admin/settings/second-factor-policy` (`settings:manage`) with `enforced`, `exemptRoles`, and the read-only `enforcedByOperator`; audited as `settings.second-factor-policy-changed`.
  - Operator setting `oea.auth.second-factor.enforced` (default `false`); while it is `true`, `enforced: false` is rejected with 422.
  - An exempt role that does not exist or contains an admin-class permission is rejected with 422 naming the field (REQ-029 AC4, AC6).
  - Stored in `system_settings` (`V14__second_factor_policy.sql`).
- **Enrollment required (US-031 AC2):**
  - While enforced, a password login of a person without an active second factor who is not exempt creates a session with the new required action `enroll-second-factor`. It allows reading the principal, logging out, listing the own second factors and the TOTP and passkey enrollment; everything else is answered with 403 and problem type `second-factor-enrollment-required`.
  - A confirmed enrollment lifts the restriction. A required password change comes first, the enrollment second.
- **Exemption (US-028):** evaluated at every password login from the person's granted local role assignments.
- **Last factor (REQ-020):** while enforced, a person who is not exempt cannot remove the last active second factor (409 `last-second-factor`); the factor reset by an administrator stays possible.
- **Frontend:**
  - The second-factor page (SCR-003) shows no "Skip" while the enrollment is required, and the router keeps the session on it.
  - The login page and the password change lead to it.
- **Manual:** administration (second-factor policy) and usage (required enrollment); English and German.

## Out of Scope

- **IdP logins:** an IdP session without second factor stays as it is (owner decision 2026-10-30).
- **An admin UI** for the policy; it remains an API operation, documented in the manual.
- **Grace periods** before the enforcement takes effect for existing persons.

## Capabilities

### New Capabilities

- `identity/second-factor-enforcement`: the second-factor policy with operator floor and exempt roles, the required enrollment at the password login of persons, and the scope of the enforcement.

### Modified Capabilities

- `identity/person-totp`: "Enrollment offer after the password login" – the offer cannot be skipped while the enrollment is required.
- `identity/second-factor-management`: "Removing an own second factor" – the last second factor of a person who is not exempt stays while the enforcement is active.

## Impact

- **`backend/`:**
  - New `io.oea.app.access.SecondFactorPolicies` (policy, operator floor, exemption).
  - `PersonSessions` (required action), `PasswordChange` (next required action), `TotpEnrollment` and `PasskeyRegistration` (lift the action), `SecondFactors` (last factor), `AuthorizationInterceptor` (allowed operations).
  - Controller operations in `io.oea.api.admin`.
- **Flyway `V14__second_factor_policy.sql`:** `system_settings.second_factor_enforced`, `second_factor_exempt_roles` (`sessions.required_action` already allows `enroll-second-factor`).
- **`api/openapi.yaml`:** `getSecondFactorPolicy`, `updateSecondFactorPolicy`, schema `SecondFactorPolicy`, `RequiredAction` value `enroll-second-factor`, problem type `second-factor-enrollment-required`.
- **Data model:** `docs/data-model.puml`, `business-objects/system-settings.md`.
- **`frontend/`:** `router/guards.ts`, `SecondFactorPage.vue`, `LoginPage.vue`, `ChangePasswordPage.vue`; screen story SCR-003 and its images.
- **Docs:** audit event catalog, manual (English and German).
