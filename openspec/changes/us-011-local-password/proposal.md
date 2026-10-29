# Proposal

## Why

Persons can log in only through an identity provider. An instance without an IdP, such as a small team or a test instance, has no way to give a person access. The second factors of persons (TOTP, passkeys) need a local login first. That is why S5 was merged and starts with this change (sprint plan, owner decision 2026-10-29).

The change adds the minimal local variant of UC-01 (A5) and UC-03:

- an operator explicitly switches on password login for persons (US-011);
- an administrator sets an initial password, which is shown once, expires and must be changed at the first login (US-023, REQ-173 AC1/AC2);
- failed logins reveal nothing through their response time (US-006 AC2).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-01: Login](../../../requirements/use-cases/UC-01-login.md) (A5 local login), [UC-03: Set up authentication method](../../../requirements/use-cases/UC-03-set-up-authentication-method.md) |
| User Stories | [US-011: Minimal password variant](../../../requirements/user-stories/US-011-password-minimal.md); [US-023: Initial password by an administrator](../../../requirements/user-stories/US-023-initial-password-admin.md); [US-006: Generic error message](../../../requirements/user-stories/US-006-generic-error-message.md) AC2 |
| Requirements | [REQ-011](../../../requirements/req/REQ-011-username-password-minimal.md); [REQ-024](../../../requirements/req/REQ-024-initial-password-admin.md); [REQ-173](../../../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC1, AC2; [REQ-006](../../../requirements/req/REQ-006-no-account-existence-disclosure.md); REQ-168 (throttling), REQ-169 (argon2id) |
| ADRs | [ADR-006](../../../adrs/ADR-006-auth-stack-choice.md) (local authentication next to the IdPs), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (sessions, SoD-8), [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (screen stories) |
| Sprint / Milestone | S5 change 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-011, REQ-011 ("enabled at instance level"):** the variant is switched on by the operator setting `oea.auth.person-password.enabled` (default `false`), not through `PUT /admin/settings`. That operation is still planned, and the switch belongs to the operator who runs the instance (assumption, see design D1).
- **US-011 ("username"):** a person logs in with the e-mail address of the person record, which is unique; the system admin account keeps its login name. The same operation `localLogin` serves both (design D2).
- **US-023 AC1 ("Generate password"):** the administrator enters the initial password. The generator follows with US-026 in change 2 of S5. US-023 AC3 (2FA enforcement as a required action) follows with change 5.
- **REQ-024 ("when creating a person"):** the initial password is set by a separate operation on an existing person, in status `invited` or `active` and without a password, rather than as part of `createPerson`. The plain text is in the response of that operation only.
- **REQ-173 AC1/AC2:** the temporary password expires after `oea.auth.temporary-password-validity` (default 72 hours). A session created with it allows only the password change, `GET /me` and the logout.

## What Changes

- **Switch:** `oea.auth.person-password.enabled` (default `false`). While it is off, a person cannot log in with a password; the answer is the same as for a wrong password.
- **Initial password (US-023):**
  - New operation `POST /admin/persons/{personId}/password` (`setInitialPassword`, `person:manage`).
  - It stores the password as argon2id with `origin` `initial` and an expiry, and answers with the time of expiry; the plain text is never returned or logged.
  - It is rejected with 409 if the switch is off or the person already has a password, with 403 for the caller's own person (SoD-1), and with 422 for service accounts or a password outside 12–128 characters.
  - Audited as `person.password-set`.
- **Login of persons:**
  - `localLogin` accepts the e-mail address of a person in status `invited` or `active` with a valid password. The first login sets `invited → active`.
  - An expired temporary password fails like a wrong one (REQ-173 AC1).
  - Throttling (REQ-168) and audit (`auth.login`, method `password`, second factor `false`) work as for the system admin.
  - A person's session is never second-factor-verified in this change, so privileged roles stay ineffective (SoD-8).
- **Required password change (REQ-173 AC2):**
  - A login with a temporary password creates a session marked `change-password`. Every other operation answers 403 with problem type `password-change-required` until `POST /me/password` (`changeOwnPassword`) sets a new, self-chosen password.
  - The same operation lets every person with a local password change it.
  - Audited as `auth.password-changed`.
- **Equal response times (US-006 AC2):** every failed variant runs one argon2id verification: unknown name, wrong password, expired temporary password, person not active, switch off.
- **Frontend:**
  - The local form of the login page is labelled for persons and the system admin.
  - After a login with a temporary password, the client opens the new page *Change password* (SCR-004).
  - Screen stories SCR-001 (login) and SCR-004 (change password) are added, and `docs/screens/SCREENS.md` is updated.
- **Manual:** setting, initial password, first login, password change; English and German.

## Out of Scope

- **Change 2 of S5:** password policies (US-027), the generator (US-026), and reset by an administrator (US-030, including REQ-173 AC3).
- **Changes 3 to 5 of S5:** TOTP and passkeys for persons, and the 2FA enforcement.
- **No UI for administrators:** setting the initial password is an API operation and documented in the manual.

## Capabilities

### New Capabilities

- `identity/person-password`: the operator switch, the initial password, the password login of persons, the required password change and the equal response times of failed logins.

### Modified Capabilities

- None. The local login of the system admin account (`identity/local-login`) keeps its behavior. The person login is specified in the new capability and shares the operation.

## Impact

- **`backend/`:**
  - `io.oea.app.login.LocalLogin` gets the person branch.
  - New `io.oea.app.access.PersonPasswords` for the initial password and `io.oea.app.login.PasswordChange`.
  - A check of the `change-password` session in the authorization interceptor.
  - Properties `oea.auth.*`.
- **Flyway `V10__person_passwords.sql`:**
  - `local_credentials.person_id` with the owner check "exactly one owner";
  - a partial unique index "one active password per person";
  - an index for the login by e-mail.
- **`api/openapi.yaml`:**
  - new `setInitialPassword` and `changeOwnPassword`;
  - `localLogin` description, and `LoginResult.requiredAction`;
  - problem types `password-change-required`, `password-already-set`.
- **Data model:** `docs/data-model.puml` (`local_credentials.person_id`), `business-objects/local-credential.md`.
- **`frontend/`:**
  - `LoginPage.vue` (label, redirect);
  - new `ChangePasswordPage.vue` (`/me/password`);
  - screen stories SCR-001 and SCR-004, `docs/screens/SCREENS.md`.
- **Docs:**
  - `docs/audit-event-catalog.md`;
  - manual: login page, identity providers and persons page, operations (setting); English and German.
