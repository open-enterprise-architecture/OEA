# Design

## Context

See proposal.md – Why. Relevant current state (after `us-011-local-password`):

- **Password services:**
  - `PersonPasswords.setInitial` and `PasswordChange.change` check only the length (12–128, `PasswordHasher`).
  - `LocalLogin.loginPerson` sets `change-password` for any origin other than `self-set`.
- **Switch:** `PersonPasswordProperties` holds `enabled` and `temporaryPasswordValidity` (operator, `oea.auth.person-password.*`).
- **Storage:**
  - `password_credentials` has `origin` (`self-set | initial | admin-reset | break-glass`) and `expires_at`.
  - `local_credentials` keeps revoked passwords (status `revoked`, reason `superseded`), so their hashes are available for a history.
  - `local_credentials.created_at` is the time a password was set.
- **Settings:** `system_settings` is a single row. `GET/PUT /admin/settings` are planned, and `settings:manage` belongs to `system-admin`.
- **Admin-class permissions** are marked in `permissions.admin_class`. `RoleAssignments` knows the local assignments of a person.

## Goals / Non-Goals

**Goals:**

- One policy service used by every place that stores a new password.
- Floors that administrators cannot go below, and a policy they maintain through the API.
- A reset that is one transaction and refuses resets of administrators by other administrators.

**Non-Goals:**

- The general settings API, an admin UI, notifications about expiry, second factors of persons.

## Architecture Constraints

- **ADR-013:** the four new operations and the schema changes go into `api/openapi.yaml` first.
- **ADR-028:**
  - `PasswordPolicies` and `PasswordResets` live in `io.oea.app.access` next to `PersonPasswords`; `PasswordChange`, `LocalLogin` and `PersonPasswordProperties` stay in `io.oea.app.login`.
  - The generator is a pure service in `io.oea.core.service` next to `PasswordHasher`.
  - The controllers live in `io.oea.api`.
- **REQ-169:** argon2id and 12–128 characters remain the absolute bounds.
- **ADR-032:**
  - SoD-1: no reset of the caller's own person.
  - A reset of an administrator by anyone but the system admin account is a denial (REQ-173 AC3), audited through `PermissionDenials`.
- **ADR-031:** the screen story SCR-004 and its images change with the page.

## Decisions

### D1: Floors in the operator configuration, policy in `system_settings`

**Operator floors** (`PasswordPolicyFloors`, `@ConfigurationProperties("oea.auth.password-policy")`, validated at startup):

| Property | Meaning | Default | Allowed |
|---|---|---|---|
| `min-length` | floor of the minimum length | 12 | 12 to 128 |
| `require-uppercase`, `-lowercase`, `-digits`, `-special-chars` | character types required in any case | `false` | – |
| `max-expiry-days` | ceiling of the expiry in days; `0` means no ceiling | 0 | – |
| `min-history-count` | floor of the history | 0 | 0 to 24 |

**`V11__password_policy.sql`** adds these columns to `system_settings`:

- `password_min_length SMALLINT NOT NULL DEFAULT 12`;
- `password_require_uppercase`, `_lowercase`, `_digits`, `_special_chars` (`BOOLEAN`, default `false`);
- `password_expiry_days SMALLINT NOT NULL DEFAULT 0`;
- `password_history_count SMALLINT NOT NULL DEFAULT 0`;
- `person_password_login BOOLEAN NOT NULL DEFAULT true`;
- CHECKs on the ranges: length 12–128, expiry 0–3650, history 0–24.

**The effective policy** is computed at every check, so that an operator who raises a floor later takes effect at once:

- minimum length, history: the maximum of floor and stored value;
- character types: the floor OR the stored value;
- expiry: the stored value, capped by a non-zero ceiling. If a ceiling is set and the stored value is 0, the ceiling applies.

`PUT` rejects stored values that would be weaker than a floor (422 naming the rule), so the stored policy never claims less than what applies.

### D2: One resource for policy and switch

`GET/PUT /admin/settings/password-policy` (`getPasswordPolicy`, `updatePasswordPolicy`, `settings:manage`) carries the editable fields:

- `minLength`, `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars`;
- `expiryDays`, `historyCount`;
- `personPasswordLogin`.

It also carries read-only fields: `floors` (the operator values) and `personPasswordLoginPermitted` (the operator setting `oea.auth.person-password.enabled`).

`PUT` checks the following and answers every violation with 422, together with the field:

- every value against the floors;
- the minimum length against the number of required types (REQ-028 AC4);
- `personPasswordLogin: true` only while it is permitted.

The change is audited as `settings.password-policy-changed` with the old and new values.

Password login of persons is possible while `enabled` (operator) and `person_password_login` (administrators) are both true. `PersonPasswordProperties.enabled` keeps its name; the manual describes it as the permission.

### D3: Checking a new password

`PasswordPolicies.check(password, owner)` collects every unmet rule name: `minLength`, `maxLength`, `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars`, `historyCount`. It throws `PasswordPolicyViolationException` (422 `password-policy-violation`, `invalidFields` with the rule names).

- **Special characters** are the printable ASCII characters 0x21–0x2F, 0x3A–0x40, 0x5B–0x60 and 0x7B–0x7E.
- **The history** compares with `PasswordHasher.matches` against the current active password and the last `historyCount − 1` revoked passwords of the owner (by `revoked_at`). This is at most 24 verifications, only when a password is set.

It is called by `PersonPasswords.setInitial` (entered password), `PasswordChange.change` and the reset. Generated passwords pass the same check, as a safety net.

### D4: Expiry at login

`LocalLogin.loginPerson` sets `change-password` if:

- the origin is not `self-set`, or
- the effective expiry is > 0 and the `created_at` of the active password credential is older than that many days.

The system admin account is not subject to expiry; its password is protected by the second factor of ADR-042.

### D5: Generator

`PasswordGenerator.generate(policy)` (in `io.oea.core.service`, `SecureRandom`):

1. **Length:** the effective minimum length, at least 16.
2. **Required classes:** one character from each required class.
3. **Rest:** filled from the full printable ASCII set without space (0x21–0x7E).
4. **Shuffle:** a CSPRNG Fisher–Yates shuffle.

It throws `PasswordPolicyUnsatisfiableException` (409 `password-policy-unsatisfiable`) if the minimum length is below the number of required classes. Thanks to D2 this cannot happen with a stored policy; it covers REQ-027 AC5.

`POST /me/password/suggestion` (`suggestPassword`, `authenticated`) returns `{password}` for principals with a local password, and is allowed in a session that must change its password. The suggestion is not stored. The person types or pastes it into the form.

### D6: Reset by an administrator

`POST /admin/persons/{personId}/password/reset` (`resetPersonPassword`, `person:manage`, body optional `{reason}`) answers `200 {password, expiresAt}`. In one transaction:

1. **Checks** of the person: exists (404), human (422), switch possible (409 `conflict`), and has an active password (409 `conflict`).
2. **SoD-1:** the caller is not the person (403 `self-administration`).
3. **Administrators:** if one of the person's granted local assignments contains an admin-class permission and the caller is not the system admin account, the request is answered with 403. It is recorded through `PermissionDenials` with reason `admin-reset-restricted`.
4. **Credentials:** the active password is revoked (`admin_action`). A generated password (D5) is stored with origin `admin-reset` and expiry `now + temporary-password-validity`.
5. **Sessions:** all sessions of the person are revoked with reason `password-reset`.
6. **Audit:** `person.password-reset` with `reason` and `expiresAt`, never the password.

The answer is the only place the password appears.

### D7: Frontend

`ChangePasswordPage.vue` gets:

- the button "Suggest a password", which calls `suggestPassword` and shows the suggestion once in a read-only field with a copy button;
- for a 422 `password-policy-violation`, the unmet rules in plain words, from `invalidFields`.

The screen story SCR-004 gets the stories "Suggestion" and "Policy violation", and the images are rendered again.

## Data Model

- **`docs/data-model.puml`:** the new `system_settings` columns.
- **`business-objects/local-credential.md`:** origin `admin-reset` realized; history from revoked credentials.
- **`business-objects/system-settings.md`:** the password policy fields and `person_password_login`.

## API

Delta to `api/openapi.yaml`:

- **New `GET/PUT /admin/settings/password-policy`** (`getPasswordPolicy`, `updatePasswordPolicy`, tag Admin, `settings:manage`):
  - schemas `PasswordPolicy` and `PasswordPolicyFloors`;
  - `200`, `400`, `401`, `403`, `413`, `422`, `429`.
- **New `POST /admin/persons/{personId}/password/reset`** (`resetPersonPassword`, tag Access Control, `person:manage`):
  - schemas `PasswordReset` and `GeneratedPassword` (`{password, expiresAt}`);
  - `200`, `400`, `401`, `403`, `404`, `409`, `413`, `422`, `429`.
- **New `POST /me/password/suggestion`** (`suggestPassword`, tag Identity, `authenticated`): `200 {password}`, `401`, `403`, `409`, `429`.
- **`InitialPassword`:** `password` optional, plus `generate` (boolean). Exactly one of the two must be given; otherwise `422`.
- **`InitialPasswordResult`:** `password` (only if generated).
- **New problem types** `password-policy-violation` (422, `invalidFields` = rule names) and `password-policy-unsatisfiable` (409).
- **Interceptor allowlist:** `suggestPassword` joins the operations allowed while `change-password` is required.

## Risks / Trade-offs

- **History checks cost one argon2id verification per remembered password** (at most 24 at about 50 ms). → They run only when a password is set, never at login.
- **A generated password is shown once in an API response.** → The responses are `no-store` like every API response, and the password is never logged or audited.
- **Admin-class roles derived from IdP groups are not seen by the reset check.** → Persons with such roles log in through the IdP, where OEA does not manage passwords. A local password of such a person can still be reset by a non-system administrator, which the manual names.
