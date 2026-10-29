# Design

## Context

See proposal.md – Why. Relevant current state:

- **Local login:**
  - `LocalLogin` (`io.oea.app.login`) logs in the system admin account: login name and password, with a TOTP step after the setup phase (ADR-042).
  - Throttling per login name and source address (`LoginThrottle`, REQ-168), a dummy argon2id hash for unknown names, and audit `auth.login` and `auth.login-failed`.
  - The operation is `localLogin` (`POST /auth/local/login`), with `LoginResult` and `LoginProblem`.
- **Credentials:**
  - `local_credentials` has the owner check `system_admin_account_id IS NOT NULL` ("extended with person_id together with the persons table", V2).
  - `password_credentials` already has `origin` (`self-set | initial | admin-reset | break-glass`) and `expires_at` with the check "temporary ⇔ expiry".
  - `PasswordHasher` hashes with argon2id (REQ-169) and enforces 12 to 128 characters.
- **Sessions:** `sessions.required_action` (`change-password | enroll-second-factor`) exists but is unused. `Sessions.createForSystemAdmin` and `createForPerson` create sessions. `OeaPrincipal` carries type, IDs, session and second-factor flag.
- **Persons:** they have a unique `email` and `status`. `Sessions.authenticate` accepts persons in `invited` or `active`. The OIDC login sets `invited → active` and writes `person.activated`.
- **Authorization:** `AuthorizationInterceptor` enforces the `x-oea-authorization` of each operation. `getCurrentPrincipal` and `logout` are `authenticated`.
- **Frontend:** `LoginPage.vue` shows the IdP buttons and a local form ("or with the local system admin account"). There are no screen stories for the login (SCR-001 is a legacy mockup).

## Goals / Non-Goals

**Goals:**

- One login operation and one login page for persons and the system admin account.
- Temporary passwords that only lead to a password change.
- No timing or answer differences between failed variants.

**Non-Goals:**

- Policies, generator, admin reset (change 2), second factors of persons (changes 3 and 4), 2FA enforcement (change 5), admin UI.

## Architecture Constraints

- **ADR-028:** the login logic lives in `io.oea.app.login`, the administration in `io.oea.app.access`, the controllers in `io.oea.api`.
- **ADR-013:** the contract changes first: new operations, `requiredAction`, and the problem types.
- **ADR-032:**
  - SoD-1: no own initial password, 403 `self-administration`.
  - SoD-8: privileged roles need a second factor, so they stay ineffective for password sessions.
- **REQ-169** (argon2id, 12–128 characters), **REQ-168** (throttling), **REQ-175** (no secrets in logs or audit details).
- **ADR-031:** screen stories for SCR-001 and the new SCR-004, `docs/screens/SCREENS.md` updated in the same task group.

## Decisions

### D1: Operator switch instead of an instance setting

`oea.auth.person-password.enabled` (default `false`), validated at startup like the other `oea.*` properties. The validity of temporary passwords is `oea.auth.temporary-password-validity` (default `PT72H`, between 1 hour and 30 days).

- **Alternative – a field of `SystemSettings` through `PUT /admin/settings`:** that operation is planned, not implemented. Building it for one flag would pull in the whole settings administration. The operator also decides how the instance is run, for example whether an IdP is mandatory. The setting can later be mirrored into the settings API if administrators should switch it.

### D2: One login operation, the e-mail address as the person's name

`localLogin` keeps its request (`loginName`, `password`):

- A `loginName` containing `@` is looked up as the e-mail address of a human person (case-insensitive).
- Any other `loginName` is looked up as before as the login name of the system admin account. The pattern of system admin login names contains no `@`.

The person branch accepts the login only if all of these hold:

- the switch is on;
- the person is human and in status `invited` or `active`;
- an active password credential exists and is not expired.

Every other case uses the dummy hash and fails with `invalid-credentials` (D6). The first login of an invited person sets it to `active` and writes `person.activated` with trigger `first-login`, as the OIDC login does.

The person session is created with `auth_method` `password` and `second_factor_verified = false`.

- **Alternative – a separate person login operation:** it would duplicate throttling, audit and answers. The login page would also need to ask which kind of account the user has.

### D3: Credentials of persons

`V10__person_passwords.sql`:

- adds `local_credentials.person_id BIGINT REFERENCES persons (id)`;
- replaces the owner check by "exactly one of `system_admin_account_id`, `person_id`";
- adds `UNIQUE (person_id) WHERE type = 'password' AND status = 'active'`;
- adds an index on `lower(persons.email)`.

`LocalCredential` and `PasswordCredential` get factory methods for persons. The initial password has `origin` `initial` and `expires_at` = now + validity.

### D4: Required action on the session

A login with a password whose `origin` is not `self-set` creates the session with `required_action = 'change-password'`.

- `OeaPrincipal` gets `requiredAction`.
- `AuthorizationInterceptor` answers every operation except `getCurrentPrincipal`, `logout` and `changeOwnPassword` with 403 and problem type `password-change-required` while it is set.
- `LoginResult` and `Me` get `requiredAction` (`change-password` or null). The client therefore knows it right after the login and after a reload.

The interceptor runs after authentication, so public operations are unaffected.

### D5: Changing the own password

`POST /me/password` (`changeOwnPassword`, access `authenticated`) takes `currentPassword` and `newPassword`. It works for every principal with an active local password, persons and the system admin account alike, and for no other principal.

- **Wrong current password:** 403 with problem type `invalid-credentials`. It counts as a failure in the login throttle of the account (REQ-168).
- **Unchanged or invalid new password:** 422.

On success, in one transaction:

- the old password credential is revoked (`superseded`) and a new one stored with `origin` `self-set` and no expiry;
- `required_action` of the current session is cleared;
- every other session of the principal is revoked (reason `credential-revoked`);
- the change is audited as `auth.password-changed`.

### D6: One hash verification per failed login

All failing branches – unknown name, switch off, person not active, no or expired credential, wrong password – call `PasswordHasher.matches` exactly once, against the stored hash or the dummy hash, before they answer. A unit test counts the calls per branch. Measuring times in CI would be flaky, so the guarantee is checked structurally.

### D7: Initial password operation

`POST /admin/persons/{personId}/password` (`setInitialPassword`, `person:manage`) takes `{password}` and answers `200` with `{expiresAt}`. Its checks:

- the person exists (404);
- the switch is on and the person has no active password (409 `password-already-set`, or `conflict` while the switch is off);
- the caller is not the person (403 `self-administration`, audited through `PermissionDenials`);
- the person is human and the length is valid (422).

The event `person.password-set` has the details `origin` and `expiresAt`, never the password.

### D8: Frontend

- **Login page:** the separator reads "or with your user name or e-mail address and password". A login answer with `requiredAction: change-password` routes to `/me/password`.
- **Router guard:** it redirects every route except `/me/password` and `/login` while `Me.requiredAction` is set.
- **`ChangePasswordPage.vue`:** current password, new password, repetition, client-side length check, and the server's problem shown.
- **Screen stories:**
  - `SCR-001-login.stories.ts`: IdP buttons, local form, failed login, temporary password.
  - `SCR-004-change-password.stories.ts`: required change, mismatch, success.
  - `docs/screens/SCREENS.md`: SCR-001 becomes `implemented` (it was a legacy mockup), and SCR-004 is new and `implemented`. The CI generates the images.

## Data Model

- **`docs/data-model.puml`:** `local_credentials.person_id` and the owner check "exactly one owner", replacing the note "extended with person_id".
- **`business-objects/local-credential.md`:** owner may be a person (already described); the realized origins `initial` and `self-set`.
- **`sessions.required_action`:** exists; no change.

## API

Delta to `api/openapi.yaml`:

- **New `POST /admin/persons/{personId}/password`** (`setInitialPassword`, tag Access Control, `person:manage`):
  - request `InitialPassword` `{password}`;
  - `200` `{expiresAt}`;
  - `400`, `401`, `403`, `404`;
  - `409` with `password-already-set` or `conflict`;
  - `413`, `422`, `429`.
- **New `POST /me/password`** (`changeOwnPassword`, tag Identity, `authenticated`):
  - request `PasswordChange` `{currentPassword, newPassword}`;
  - `204`;
  - `400`, `401`;
  - `403` with `invalid-credentials`;
  - `413`, `422`, `429`.
- **`localLogin`:** the description covers persons, and `LoginResult` gets `requiredAction` (`change-password` or null).
- **`Me`:** gets `requiredAction`.
- **New problem type** `password-change-required`, documented on `Forbidden`.

## Risks / Trade-offs

- **The `@` rule decides between the two kinds of account.** → System admin login names cannot contain `@` (pattern of the data model). A person cannot log in with a name that is not an e-mail address; the manual says so.
- **Password-only sessions of persons never reach privileged roles (SoD-8)** until TOTP or passkeys exist for persons (changes 3 and 4). → Administrators of instances without an IdP keep the local system admin account for administration until then; the manual says so.
- **A temporary password lets its holder read `GET /me`.** → That reveals only the person's own principal and roles, which the person may see anyway.
