# Design

## Context

See proposal.md – Why. Relevant current state (after `us-022-passkeys`):

- **Session start:** `PersonSessions.start(personId, authMethod, client)` (`io.oea.app.login`) creates every session of a person after a local login. It sets the required action `change-password` for a temporary or expired password.
- **Required actions:**
  - `sessions.required_action` allows `change-password` and `enroll-second-factor` (V3). `OeaPrincipal.requiredAction` carries it.
  - `AuthorizationInterceptor` allows only `getCurrentPrincipal`, `logout`, `changeOwnPassword` and `suggestPassword` while `change-password` is required.
  - `PasswordChange` clears the action with `Sessions.clearRequiredAction`.
- **Enrollments:**
  - `TotpEnrollment.confirm` and `PasskeyRegistration.confirm` mark the session second-factor-verified with `Sessions.confirmSecondFactor`.
  - `SecondFactors.remove` keeps the last TOTP of the system admin account (`LastSecondFactorException`).
- **Policies:** `PasswordPolicies` reads typed columns of `system_settings`, applies operator floors from `@ConfigurationProperties` and audits changes. `/admin/settings/password-policy` is the model for a settings resource.
- **Roles:** granted local role assignments are in `role_assignments` (`status = 'granted'`, `valid_from`, `valid_to`); `permissions.admin_class` marks admin-class permissions.
- **Frontend:**
  - `router/guards.ts` keeps a session with `change-password` on `/me/password`.
  - `SecondFactorPage.vue` shows the offer with "Skip" for `?offer=1`.

## Goals / Non-Goals

**Goals:**

- One place that decides whether a person must enroll a second factor (`SecondFactorPolicies`), used at the login, after the password change and at the removal of a factor.
- The same restriction mechanism as the required password change.

**Non-Goals:**

- Enforcement at IdP logins, grace periods, an admin UI.

## Architecture Constraints

- **ADR-013:** the two operations, the required action and the problem type go into `api/openapi.yaml` first.
- **ADR-028:** `SecondFactorPolicies` lives in `io.oea.app.access` next to `PasswordPolicies`; the controller lives in `io.oea.api.admin`.
- **ADR-032:**
  - An admin-class role cannot be exempt (REQ-029 AC6).
  - The exemption looks at local role assignments only, because the enforcement covers local logins only.
- **ADR-042:** the system admin account keeps its own rules (setup phase, TOTP after it).
- **ADR-031:** the screen story SCR-003 gets the required state.

## Decisions

### D1: Policy and operator floor

**Operator setting:** `SecondFactorProperties` (`@ConfigurationProperties("oea.auth.second-factor")`, `enforced`, default `false`).

**`V14__second_factor_policy.sql`** adds to `system_settings`:

- `second_factor_enforced BOOLEAN NOT NULL DEFAULT false`;
- `second_factor_exempt_roles TEXT[] NOT NULL DEFAULT '{}'`.

**Effective enforcement:** the operator setting OR the stored value.

**`GET/PUT /admin/settings/second-factor-policy`** (`getSecondFactorPolicy`, `updateSecondFactorPolicy`, `settings:manage`):

- **Fields:**
  - `enforced` (boolean);
  - `exemptRoles` (array of role IDs);
  - `enforcedByOperator` (read-only).
- **`PUT` rejects with 422,** naming each field:
  - `enforced: false` while the operator forces it;
  - an unknown role ID;
  - a role with an admin-class permission.
- **Audit:** `settings.second-factor-policy-changed` with the old and new values.

### D2: Exemption and requirement

`SecondFactorPolicies.enrollmentRequired(personId, now)` is true if all of the following hold:

1. the enforcement is effective;
2. the person has no active second factor (`totp` or `passkey`);
3. the person is not exempt: they hold no granted local role assignment that is valid now, or at least one such role is not in the exempt list.

Only valid assignments count (`valid_from <= now`, `valid_to` empty or later), so a scheduled role does not change the result before it starts.

### D3: Required action at the login

- **Password login:** `PersonSessions.start` gets the action as follows:
  - `change-password` if the password is temporary or expired, as before;
  - otherwise `enroll-second-factor` if the method is `password` and `enrollmentRequired` is true.
- **Logins with a second factor** (`password-totp`, `password-passkey`, `passkey`) never require the enrollment, because the person has a factor.
- **After the password change:** `PasswordChange` asks `SecondFactorPolicies` after a successful change of a person's password. If the enrollment is required, it sets `enroll-second-factor` (`Sessions.setRequiredAction`) instead of clearing the action. This is how the order "password first, then enrollment" comes about.
- **Lifting the action:** `TotpEnrollment.confirm` and `PasskeyRegistration.confirm` clear `enroll-second-factor` when they confirm the session (`Sessions.confirmSecondFactor` also clears a required action `enroll-second-factor`).

### D4: Allowed operations

`AuthorizationInterceptor` gets a second set for `enroll-second-factor`:

- `getCurrentPrincipal`, `logout`, `listOwnSecondFactors`;
- `startTotpEnrollment`, `confirmTotpEnrollment`;
- `startPasskeyRegistration`, `confirmPasskeyRegistration`.

Every other operation gets 403 with problem type `second-factor-enrollment-required`.

The re-authentication of the enrollment (5 minutes) still applies. A session that waited longer logs in again and gets the same required action.

### D5: Last factor

`SecondFactors.remove` rejects the removal of the last active second factor of a person with 409 (`last-second-factor`) if `enrollmentRequired` would be true without that factor. The factor reset by an administrator is not restricted: the next login then requires the enrollment (REQ-173 AC5).

### D6: Frontend

- **`router/guards.ts`:** keeps a session with `enroll-second-factor` on `/me/second-factor` (and `/login`), as for the password change.
- **`SecondFactorPage.vue`:** hides "Skip" and shows "Your organization requires a second factor" while `session.me.requiredAction === 'enroll-second-factor'`.
- **`LoginPage.vue` and `ChangePasswordPage.vue`:** open `/me/second-factor` for this action.
- **Screen story:** SCR-003 gets the story "Required by the organization".

## Data Model

- **`docs/data-model.puml`:** `system_settings.second_factor_enforced`, `second_factor_exempt_roles`.
- **`business-objects/system-settings.md`:** the second-factor policy.

## API

Delta to `api/openapi.yaml`:

- **New `GET/PUT /admin/settings/second-factor-policy`** (`getSecondFactorPolicy`, `updateSecondFactorPolicy`, tag Admin, `settings:manage`):
  - schema `SecondFactorPolicy`;
  - `200`, `400`, `401`, `403`, `413`, `422`, `429`.
- **`RequiredAction`:** value `enroll-second-factor`.
- **`Forbidden` response:** names the problem type `second-factor-enrollment-required`.
- **`localLogin`, `changeOwnPassword`, `confirmTotpEnrollment`, `confirmPasskeyRegistration`, `removeOwnSecondFactor`:** the descriptions name the effects of the enforcement.

## Risks / Trade-offs

- **Switching the enforcement on affects persons at their next password login, without a grace period.** → The manual recommends announcing it. Persons can enroll beforehand in the profile.
- **A person without roles is not exempt.** → Such a person has no access anyway; the rule keeps the exemption strict (all roles exempt, at least one role).
- **IdP sessions without second factor stay possible.** → They are governed by the identity provider (owner decision), and privileged roles stay ineffective for them (SoD-8).
