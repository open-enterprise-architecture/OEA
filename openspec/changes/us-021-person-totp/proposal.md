# Proposal

## Why

Since changes 1 and 2 of S5, persons log in with a local password. That session is never second-factor-verified, so privileged roles from local assignments stay ineffective (SoD-8), and a stolen password alone opens the account. Only the system admin account has TOTP so far. Persons need their own authenticators, a TOTP login, and a way back when every device is lost ([UC-03](../../../requirements/use-cases/UC-03-set-up-authentication-method.md), [UC-01](../../../requirements/use-cases/UC-01-login.md) A4).

The owner decided on 2026-10-30:

- the first login offers the TOTP enrollment, and the person can skip it; the obligation follows with change 5 (2FA enforcement);
- persons see their own second factors and can remove them;
- administrators can reset the second factors of a person (REQ-173 AC5) in this change.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-03: Set up authentication method](../../../requirements/use-cases/UC-03-set-up-authentication-method.md); [UC-01: Login](../../../requirements/use-cases/UC-01-login.md) (A4) |
| User Stories | [US-021: TOTP enrollment](../../../requirements/user-stories/US-021-totp-enrollment.md); [US-025: Additional method for a logged-in person](../../../requirements/user-stories/US-025-additional-method-logged-in-person.md) (AC1, AC3, AC4); [US-010: TOTP login](../../../requirements/user-stories/US-010-totp-login.md); [US-029: Multiple TOTP credentials](../../../requirements/user-stories/US-029-multiple-totp-credentials.md); [US-024: Audit log of enrollments](../../../requirements/user-stories/US-024-audit-log-enrollment.md) (TOTP and password) |
| Requirements | [REQ-022](../../../requirements/req/REQ-022-totp-enrollment.md); [REQ-026](../../../requirements/req/REQ-026-additional-method-authenticated-person.md); [REQ-010](../../../requirements/req/REQ-010-username-password-totp.md); [REQ-030](../../../requirements/req/REQ-030-multiple-totp-credentials.md); [REQ-025](../../../requirements/req/REQ-025-audit-log-enrollment.md); [REQ-173](../../../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC5; [REQ-031](../../../requirements/req/REQ-031-password-reset-by-admin.md) AC8; [REQ-170](../../../requirements/req/REQ-170-totp-replay-protection.md); [REQ-172](../../../requirements/req/REQ-172-reauthentication-sensitive-actions.md); [REQ-188](../../../requirements/req/REQ-188-secrets-key-management.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (SoD-1, SoD-8, admin-class permissions), [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md) (TOTP of the system admin account), [ADR-013](../../../adrs/ADR-013-api-style.md) (API-first), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (screen stories) |
| Sprint / Milestone | S5 change 3 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-021, REQ-022 (enrollment "as part of the login"):**
  - There are no enrollment tokens (REQ-021 rejected). The person enrolls in the running session with the existing operations `startTotpEnrollment` and `confirmTotpEnrollment`, not with `/auth/enrollment/totp/*`.
  - The web portal offers the enrollment after a password login of a person without an active second factor; the person can skip it (owner decision 2026-10-30).
  - The key URI keeps the issuer `OEA` and uses the e-mail address of the person as the account name.
- **US-025, REQ-026:** the same operations serve the enrollment at the first login and in the profile; there is one code path. AC2 (second passkey) follows with change 4. AC5 (CSRF) is covered by the existing CSRF filter.
- **US-029, REQ-030:** the label is stored in `totp_credentials.label`, which exists already; without a label the credential gets "Authenticator app" and the date.
- **US-024, REQ-025:**
  - The success of a TOTP enrollment stays `auth.factor-added`; a failed confirmation is the new `auth.factor-enrollment-failed`.
  - Password "enrollments" are already audited as `person.password-set` and `auth.password-changed`; passkeys follow with change 4.
  - AC3 (invalid enrollment token) and REQ-025 AC4 (required-action state) do not apply without enrollment tokens; an expired or unknown pending enrollment is audited as a failure with reason `enrollment-expired`.
- **REQ-173 AC5:** after a factor reset "the person must enroll a new factor at the next login" becomes the skippable offer until change 5 makes it mandatory.

## What Changes

- **TOTP enrollment for persons (US-021, US-025, REQ-022, REQ-026):**
  - `startTotpEnrollment` and `confirmTotpEnrollment` accept persons with a local password, not only the system admin account. Re-authentication within 5 minutes (REQ-172), the secret shown once, and encryption with the key-encryption key (REQ-188) stay as they are.
  - A confirmed enrollment marks the session as second-factor-verified.
- **Several authenticators (US-029, REQ-030):** a person may hold several active TOTP credentials with labels; a code of any of them is accepted.
- **TOTP login of persons (US-010, REQ-010):**
  - A person with an active TOTP credential gets no session after the password step; the answer asks for the second factor with a login transaction, as for the system admin account.
  - `localLoginTotp` completes the login for persons. The session is second-factor-verified, so privileged roles from local assignments become effective (SoD-8).
  - A wrong code reveals nothing about the password; five wrong codes invalidate the login transaction; codes cannot be replayed (REQ-170).
  - A temporary password still leads to the required password change after the TOTP step (REQ-031 AC8).
- **Enrollment offer (US-021, owner decision):** after a password login of a person without an active second factor, the web portal opens the TOTP enrollment with a "Skip" button; after a required password change it comes next.
- **Own second factors (US-029 AC3, owner decision):**
  - New `GET /me/second-factors` lists the active second factors of the principal with type, label, `createdAt` and `lastUsedAt`.
  - New `DELETE /me/second-factors/{credentialId}` removes one. It requires a recent authentication (REQ-172), revokes the other sessions of the principal and is audited as `auth.factor-removed`.
  - The last TOTP credential of the system admin account after the setup phase cannot be removed (409), so that its login stays possible (ADR-042).
- **Factor reset by an administrator (REQ-173 AC5, owner decision):**
  - New `POST /admin/persons/{personId}/second-factors/reset` (`person:manage`) revokes every second factor of the person (reason `factor_reset`) and all sessions (reason `factor-reset`), in one transaction, audited as `person.second-factors-reset`.
  - Rejected with 403 for the caller's own person (SoD-1), with 403 audited as `authz.denied` for a target holding an admin-class permission through a local role assignment unless the caller is the system admin account (REQ-173 AC3), with 409 for a person without second factors, and with 422 for a service account.
- **Audit (US-024, REQ-025):** `auth.factor-added` for persons, new `auth.factor-enrollment-failed`, `auth.factor-removed` and `person.second-factors-reset`; `auth.login` of a person names `method: password-totp` and `secondFactor: true`.
- **Frontend:** the page *Second factor* (SCR-003, TOTP part) with label, offer mode and "Skip"; the TOTP step of the login page for persons; the list of own second factors with "Remove" on the profile page (new screen entry SCR-005). Screen stories and images for SCR-003 and SCR-005.
- **Manual:** usage (set up, use and remove an authenticator) and administration (factor reset); English and German.

## Out of Scope

- **Passkeys:** change 4 (US-022, US-009, US-025 AC2, US-024 for passkeys).
- **2FA enforcement:** change 5 (US-031, US-028); until then the enrollment offer can be skipped.
- **Recovery codes** (`type=recovery-codes`): not part of S5; the factor reset by an administrator is the recovery path.
- **An admin UI** for the factor reset; it remains an API operation, documented in the manual.

## Capabilities

### New Capabilities

- `identity/person-totp`: TOTP enrollment by persons, several authenticators, the TOTP login of persons, the enrollment offer and the audit of enrollments.
- `identity/second-factor-management`: the overview and removal of own second factors, and the factor reset of a person by an administrator.

### Modified Capabilities

- `identity/person-password`: "Password login of persons" – the session is second-factor-verified after the TOTP step of a person with an active TOTP credential.
- `identity/password-reset`: "Password reset by an administrator" – the second factor of the person stays required after the reset (REQ-031 AC8).

## Impact

- **`backend/`:**
  - `TotpEnrollment` for persons (owner, label default, failure audit); `LocalLogin.loginPerson` and `loginWithTotp` for persons; `LocalCredential` maps `person_id`.
  - New `io.oea.app.access.SecondFactors` (list, remove, reset).
  - `Sessions.createForPersonPassword` with the second-factor flag.
  - Controller operations in `io.oea.api.identity` and `io.oea.api.access`.
- **Flyway `V12__person_totp.sql`:** `login_transactions.person_id` with exactly one owner.
- **`api/openapi.yaml`:**
  - `startTotpEnrollment` and `confirmTotpEnrollment` for persons, `localLogin` and `localLoginTotp` for persons;
  - new `listOwnSecondFactors`, `removeOwnSecondFactor`, `resetPersonSecondFactors`; schema `SecondFactorSummary`.
- **Data model:** `docs/data-model.puml` (`login_transactions`), `business-objects/local-credential.md` (several TOTP credentials, label default, factor reset).
- **`frontend/`:** `SecondFactorPage.vue`, `LoginPage.vue`, `ProfilePage.vue`, `ChangePasswordPage.vue` (next step), `api/session.ts`; screen stories SCR-003 and SCR-005 and their images; `docs/screens/SCREENS.md`.
- **Docs:** audit event catalog, manual (English and German).
