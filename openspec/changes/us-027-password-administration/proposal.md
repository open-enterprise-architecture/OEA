# Proposal

## Why

The first S5 change gives persons a local password, but every password only has to be 12 to 128 characters long. An organization cannot enforce its password policy: character types, expiry, no reuse ([REQ-028](../../../requirements/req/REQ-028-password-policies.md)). Administrators have to invent compliant initial passwords themselves ([REQ-027](../../../requirements/req/REQ-027-password-generator.md)). A person who forgot the password has no way back except a new person record ([REQ-031](../../../requirements/req/REQ-031-password-reset-by-admin.md)).

The owner decided on 2026-10-29 (API-first):

- the operator sets floors and starting values;
- administrators maintain the policy above the floors through a dedicated API;
- the switch for the password login of persons can also be changed by administrators, within the permission of the operator.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-03: Set up authentication method](../../../requirements/use-cases/UC-03-set-up-authentication-method.md) |
| User Stories | [US-027: Configure password policies](../../../requirements/user-stories/US-027-configure-password-policies.md); [US-026: Password generator](../../../requirements/user-stories/US-026-password-generator.md); [US-030: Password reset by an administrator](../../../requirements/user-stories/US-030-password-reset-by-admin.md); [US-011](../../../requirements/user-stories/US-011-password-minimal.md) (switch, realigned) |
| Requirements | [REQ-028](../../../requirements/req/REQ-028-password-policies.md); [REQ-027](../../../requirements/req/REQ-027-password-generator.md); [REQ-031](../../../requirements/req/REQ-031-password-reset-by-admin.md); [REQ-173](../../../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC3; REQ-169 (argon2id, 12 to 128 characters) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (SoD-1, admin-class permissions), [ADR-013](../../../adrs/ADR-013-api-style.md) (API-first), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (screen story SCR-004) |
| Sprint / Milestone | S5 change 2 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **REQ-028 ("the operator configures"):**
  - The operator sets floors through `oea.auth.password-policy.*`.
  - Administrators with `settings:manage` set the effective policy through `GET/PUT /admin/settings/password-policy`, which must not go below the floors.
  - The minimum length is never below 12 (REQ-169). The defaults of REQ-028 AC5 (all rules off) apply on top of that.
- **US-011, REQ-011 (switch):**
  - The operator setting `oea.auth.person-password.enabled` permits password login of persons.
  - Administrators switch it off and on again with the field `personPasswordLogin` of the same resource; the default is on.
  - Without the permission of the operator, the field cannot be switched on (owner decision 2026-10-29).
- **REQ-027 (generator in the admin and the user context):**
  - `setInitialPassword` accepts `generate: true` and returns the generated password once.
  - The reset always generates one.
  - Persons get a suggestion from `POST /me/password/suggestion` on the *Change password* page.
- **REQ-031 AC6 (reset of the system admin account):** the operation addresses persons only; the system admin account has its own recovery (`admin recover`).
- **REQ-031 AC8 (second factor before the change):** persons have no second factors yet, so this follows with change 3 of S5.
- **REQ-031 AC9 and REQ-173 AC3 (reset of administrators):** a target counts as holding an admin-class permission if one of its granted local role assignments contains one. Roles derived from IdP groups are not known outside a session.

## What Changes

- **Password policy (US-027, REQ-028):**
  - Rules: minimum length, required uppercase, lowercase, digits and special characters (all printable ASCII special characters), expiry in days (0 = none), and the number of previous passwords that must not be reused.
  - Operator floors are set with `oea.auth.password-policy.*`, validated at startup. The stored policy lives in `system_settings`.
  - `GET /admin/settings/password-policy` returns the policy, the floors and whether the operator permits password login. `PUT` changes it (`settings:manage`). Inconsistent values or values below a floor are rejected with 422, each naming the rule. Audited as `settings.password-policy-changed`.
  - Every new password is checked against the policy: initial password, own change, reset. A rejection names every unmet rule (422, `password-policy-violation`).
  - An expired self-set password leads at login to the required action `change-password`, as a temporary one does.
  - The history covers the last `historyCount` passwords of the owner.
- **Switch (realigned US-011):** the field `personPasswordLogin` of the policy resource. Password login of persons works when the operator permits it and the field is on.
- **Generator (US-026, REQ-027):**
  - A CSPRNG produces passwords from the full printable ASCII set that satisfy the active policy.
  - It answers 409 (`password-policy-unsatisfiable`) if the policy cannot be met.
  - Used by `setInitialPassword` with `generate: true`, by the reset, and by `POST /me/password/suggestion` for the own change.
- **Reset by an administrator (US-030, REQ-031):**
  - New operation `POST /admin/persons/{personId}/password/reset` (`person:manage`).
  - It revokes the active password at once, sets a generated temporary password (origin `admin-reset`, expiry) and returns it once.
  - It revokes all sessions of the person (reason `password-reset`) and forces the change at the next login, all in one transaction.
  - Rejections: 403 for the caller's own person (SoD-1), 403 when an administrator other than the system admin account resets a target holding an admin-class permission (REQ-173 AC3, audited as `authz.denied`), 409 when the person has no password or password login is off.
  - Audited as `person.password-reset`.
- **Frontend:** the page *Change password* (SCR-004) gets a "Suggest a password" button and shows every unmet rule. The screen story is updated.
- **Manual:** operations (floors), the new *Password policy* section, the initial password with generation, the reset, the login page; English and German.

## Out of Scope

- **Second factors of persons** and the 2FA enforcement: changes 3 to 5 of S5. REQ-031 AC8 follows with them.
- **The general settings API** (`GET/PUT /admin/settings`): it stays planned.
- **An admin UI** for policy, reset and generation; these remain API operations, documented in the manual.
- **Notifying persons about upcoming password expiry.**

## Capabilities

### New Capabilities

- `identity/password-policy`: the operator floors, the policy resource for administrators, enforcement of the rules on every new password, expiry and history, and the generator.
- `identity/password-reset`: the reset of a person's password by an administrator, with its rules and effects.

### Modified Capabilities

- `identity/person-password`:
  - "Password login of persons is switched on explicitly": the operator permits, administrators switch.
  - "Initial password set by an administrator": generation, and the policy instead of the fixed length rule.
  - "Rules for setting an initial password": the policy.
  - "Changing the own password": policy, history, suggestion.

## Impact

- **`backend/`:**
  - New `io.oea.app.access.PasswordPolicies` (policy, floors, check, history), `io.oea.core.service.PasswordGenerator` and `io.oea.app.access.PasswordResets`.
  - `PersonPasswords`, `PasswordChange` and `LocalLogin` (switch, expiry) use them.
  - Controller operations in `io.oea.api.admin` (policy) and `io.oea.api.access` (reset, suggestion in identity).
- **Flyway `V11__password_policy.sql`:** `system_settings` columns of the policy and `person_password_login`.
- **`api/openapi.yaml`:**
  - new `getPasswordPolicy`, `updatePasswordPolicy`, `resetPersonPassword`, `suggestPassword`;
  - `InitialPassword.generate` and the generated password in `InitialPasswordResult`;
  - problem types `password-policy-violation` and `password-policy-unsatisfiable`.
- **Data model:** `docs/data-model.puml` (`system_settings`), `business-objects/local-credential.md` (origin `admin-reset` realized).
- **`frontend/`:** `ChangePasswordPage.vue`, `api/session.ts`, screen story SCR-004 and its images.
- **Docs:** audit event catalog, manual (English and German).
