# Design

## Context

See proposal.md – Why. Relevant current state (after `us-011-local-password` and `us-027-password-administration`):

- **TOTP of the system admin account** (`us-013-system-admin-login`, ADR-042):
  - `TotpEnrollment.start/confirm` in `io.oea.app.login`, guarded by `requireSystemAdmin`. Start requires an authentication within 5 minutes (`RecentAuthentication`, REQ-172); the secret is encrypted with the key-encryption key (`SecretCipher`, REQ-188); a pending credential expires after 10 minutes; confirmation activates it, marks the session second-factor-verified and appends `auth.factor-added`.
  - `LocalLogin.login` throws `SecondFactorRequiredException` with a login transaction after the setup phase; `loginWithTotp` checks the code against every active TOTP credential of the account (`acceptCode`), with replay protection per credential (`totp_credentials.last_used_time_step`, REQ-170) and five wrong codes per transaction.
- **Persons:** `LocalLogin.loginPerson` creates a session that is never second-factor-verified (`Sessions.createForPersonPassword`). `local_credentials.person_id` exists (V10), but the JPA entity `LocalCredential` maps only `system_admin_account_id`.
- **Storage:** `totp_credentials.label` exists; several active TOTP credentials per owner are allowed (no unique index). `login_transactions.system_admin_account_id` is `NOT NULL`.
- **Revocation reasons:** `local_credentials.revoked_reason` knows `factor_reset` and `person_request`; `sessions.revoked_reason` knows `factor-reset` and `credential-revoked`.
- **Frontend:** `SecondFactorPage.vue` (`/me/second-factor`) enrolls with the fixed label "Authenticator app"; `LoginPage.vue` handles the second-factor step; `ProfilePage.vue` links to the enrollment.

## Goals / Non-Goals

**Goals:**

- One enrollment and one login code path for the system admin account and persons.
- Persons can recover from a lost device on their own (second authenticator, removal) or through an administrator (factor reset).

**Non-Goals:**

- Passkeys, 2FA enforcement, recovery codes, an admin UI for the factor reset.

## Architecture Constraints

- **ADR-013:** the three new operations and the changed descriptions go into `api/openapi.yaml` first.
- **ADR-028:**
  - Enrollment and login stay in `io.oea.app.login`; list, removal and factor reset live in the new `io.oea.app.access.SecondFactors`.
  - Controllers in `io.oea.api.identity` (own factors) and `io.oea.api.access` (factor reset).
  - Core types do not reach `io.oea.api`.
- **ADR-032:**
  - SoD-1: no factor reset of the caller's own person.
  - SoD-8: a second-factor-verified session of a person makes privileged roles from local assignments effective; nothing else changes in the permission resolution.
  - A factor reset of an administrator by anyone but the system admin account is a denial (REQ-173 AC3), audited through `PermissionDenials` with reason `admin-reset-restricted`, as for the password reset.
- **ADR-042:** the system admin account keeps at least one TOTP credential after the setup phase.
- **ADR-031:** SCR-003 gets its screen story (TOTP part; the passkey mockup stays linked until change 4); the profile page becomes SCR-005 with a story.

## Decisions

### D1: Owner of a TOTP credential

`TotpEnrollment` works for the owner of the principal:

- **System admin account:** as before.
- **Person:** only with an active local password (`local_credentials` of type `password`); otherwise 403 (problem type `forbidden`). Persons logged in through an IdP use the IdP's factors.

`LocalCredential` maps `person_id` (read-only owner field, set by the factory `pendingTotp(owner, now)`), and `LocalCredentialRepository` gets `findByPersonIdAndTypeAndStatus`. The key URI uses the e-mail address of the person as account name (`OEA:anna@example.org`) and the issuer `OEA`.

### D2: Label default

A blank label becomes `Authenticator app (YYYY-MM-DD)` with the date of the enrollment in UTC (REQ-030 AC4). The label stays at most 64 characters (schema).

### D3: Failed confirmation

`confirm` appends `auth.factor-enrollment-failed` (independent transaction, like `auth.login-failed`) with `type: totp` and `reason`:

- `invalid-code`: wrong code; the pending credential stays and can be confirmed again (REQ-022 AC3);
- `enrollment-expired`: unknown, foreign or expired pending credential; answered with 404 as before.

### D4: TOTP login of persons

`LocalLogin.loginPerson`, after a valid password:

- **No active TOTP credential:** session as before (not second-factor-verified).
- **Active TOTP credential:** a login transaction for the person; `SecondFactorRequiredException` (401 `second-factor-required` with `loginTransaction`), as for the system admin account. The invited person stays `invited` until the TOTP step succeeds.

`V12__person_totp.sql`: `login_transactions.person_id BIGINT REFERENCES persons (id)`, `system_admin_account_id` nullable, CHECK exactly one owner.

`loginWithTotp` resolves the owner of the transaction; `acceptCode` checks the active TOTP credentials of that owner. For a person, success:

1. activates an invited person (`person.activated`, as at the password login);
2. computes the required action as `loginPerson` does (temporary or expired password → `change-password`; REQ-031 AC8);
3. creates the session with `Sessions.createForPersonPassword(person, requiredAction, true, …)`;
4. appends `auth.login` with `method: password-totp`, `secondFactor: true`.

The password expiry and the switch of password login of persons are evaluated at the password step only.

### D5: Own second factors

`SecondFactors` in `io.oea.app.access`:

- **`list(principal)`** – `GET /me/second-factors` (`listOwnSecondFactors`, `authenticated`): active credentials of type `totp` (later `passkey`) of the owner, ordered by `created_at`: `credentialId`, `type`, `label`, `createdAt`, `lastUsedAt`. A principal without local credentials gets an empty list.
- **`remove(principal, credentialId)`** – `DELETE /me/second-factors/{credentialId}` (`removeOwnSecondFactor`, `authenticated`, 204):
  - requires an authentication within 5 minutes (`RecentAuthentication`, 401 `reauthentication-required`);
  - 404 for an unknown, foreign or inactive credential;
  - 409 (`last-second-factor`) for the last active TOTP credential of the system admin account while the setup phase has ended;
  - revokes the credential (`person_request`, `revoked_by_person_id` / `revoked_by_admin_id`), ends the other sessions of the principal (`credential-revoked`), appends `auth.factor-removed` (`type`, `label`, `otherSessionsRevoked`).

### D6: Factor reset by an administrator

`POST /admin/persons/{personId}/second-factors/reset` (`resetPersonSecondFactors`, `person:manage`, body optional `{reason}`), 200 with `{credentialsRevoked}`. In one transaction, with the order of the password reset:

1. **Checks:** person exists (404), human (422).
2. **SoD-1:** not the caller's own person (403 `self-administration`).
3. **State:** at least one active second factor (409 `conflict`).
4. **Administrators:** a target holding an admin-class permission through a granted local role assignment is reset only by the system admin account; otherwise 403, recorded through `PermissionDenials` with reason `admin-reset-restricted`. The check is shared with `PasswordResets` (one package-private helper).
5. **Credentials:** every active and pending `totp` (later `passkey`) credential is revoked with `factor_reset`.
6. **Sessions:** all sessions of the person are revoked with reason `factor-reset`, each audited as `session.revoked`.
7. **Audit:** `person.second-factors-reset` with `credentialsRevoked`, `sessionsRevoked` and `reason`.

### D7: Frontend

- **`SecondFactorPage.vue` (SCR-003):** a label field; for persons and the system admin account alike. With `?offer=1` it explains the benefit and shows "Skip", which opens the start page.
- **Offer:** after a successful password login of a person (`LoginPage.vue`, including the TOTP step) and after a required password change (`ChangePasswordPage.vue`), the portal reads `GET /me/second-factors`; if the list is empty, it opens `/me/second-factor?offer=1`.
- **`ProfilePage.vue` (new inventory entry SCR-005 "Profile and security settings"):** the list of own second factors with label, dates and "Remove". A removal answered with `reauthentication-required` asks to log in again.
- **Login:** the existing TOTP step works for persons without change; its texts no longer name the system admin account only.

## Data Model

- **`docs/data-model.puml`:** `login_transactions.person_id` and the owner check.
- **`business-objects/local-credential.md`:** several active TOTP credentials per person, label default, removal by the owner (`person_request`) and factor reset (`factor_reset`).

## API

Delta to `api/openapi.yaml`:

- **`startTotpEnrollment`, `confirmTotpEnrollment`:** for persons with a local password as well; 403 for principals without one; the label default.
- **`localLogin`:** the `second-factor-required` answer applies to persons with an active TOTP credential.
- **`localLoginTotp`:** completes the login of persons; the result may carry `requiredAction: change-password`.
- **New `GET /me/second-factors`** (`listOwnSecondFactors`, tag Identity, `authenticated`): `200` with `SecondFactor[]`, `401`, `403`, `429`.
- **New `DELETE /me/second-factors/{credentialId}`** (`removeOwnSecondFactor`, tag Identity, `authenticated`): `204`, `401` (incl. `reauthentication-required`), `403`, `404`, `409` (`last-second-factor`), `429`.
- **New `POST /admin/persons/{personId}/second-factors/reset`** (`resetPersonSecondFactors`, tag Access Control, `person:manage`): `200` `{credentialsRevoked}`, `400`, `401`, `403`, `404`, `409`, `413`, `422`, `429`.
- **Schema `SecondFactor`** (existing, returned by the confirmation): gets `lastUsedAt`; it serves the list as well. New `SecondFactorReset` (`reason`) and `SecondFactorResetResult` (`credentialsRevoked`).

## Risks / Trade-offs

- **A person can remove every factor and skip the offer.** → Acceptable until change 5 enforces 2FA; privileged roles then stay ineffective (SoD-8), so nothing is gained by removing them.
- **The offer appears at every password login of a person without a factor.** → It nudges towards the second factor, which change 5 makes mandatory; one click skips it.
- **Admin-class roles derived from IdP groups are not seen by the reset check.** → Same as for the password reset; such persons log in through their IdP.
- **A stolen session within 5 minutes after login could remove a factor.** → The other sessions end, the removal is audited, and the remaining factor or the factor reset restores the account.
