# Design

## Context

- **Persons (V3):**
  - `persons.person_type` already allows `service-account`, and a service account can never be `invited`.
  - `persons` has no description column.
  - The table `machine_credentials` is modelled in `docs/data-model.puml` and `business-objects/machine-credential.md`, but no migration creates it.
- **Roles (V3, V25):**
  - `authorization_roles.assignable_to_service_accounts` is set for `viewer`, `integration-writer` and `auditor`.
  - `service-account:manage` is an admin-class permission of `system-admin`.
- **Role assignments (`app/access/RoleAssignments`):**
  - `create` already rejects a non-assignable role for a service account with 422 (SoD-4);
  - it rejects self-administration (SoD-1) and checks SoD-3;
  - it audits `role.assigned` and `role.revoked`;
  - `revoke` revokes the sessions of the person.
  - `PermissionResolver.permissions` resolves the permissions of a principal from its grants.
- **Custom roles (`identity/authorization-roles`):** they already reject an admin-class permission in a role flagged `assignableToServiceAccounts` (US-149 AC4).
- **Hashing:** `core/service/PasswordHasher` hashes with argon2id (m = 19 MiB, t = 2, p = 1). Its length limits (12–128) apply to passwords; a client secret of 50 characters fits. It has no concurrency limit.
- **Re-authentication:** `RecentAuthentication.require(principal)` throws `ReauthenticationRequiredException` after 5 minutes, which `ApiExceptionHandler` answers with 401 `reauthentication-required`.
- **Denials:** `PermissionDenials` writes `authz.denied`.
- **Contract:**
  - The six operations under `/admin/service-accounts` are `x-oea-status: planned` and answer 501.
  - `ServiceAccount.id` is the `client_id`, and the path parameter is `UuidIdParam`.
  - `ServiceAccountWrite` has `name`, `description` and `roles` (`maxItems: 20`, default `["integration-writer"]`).
- **Frontend:**
  - `AppHeader.vue` shows admin links by permission (for example `canAssignRoles`).
  - `AuthorizationRolesPage.vue` (SCR-039) shows a page only with its permission.
  - There is no person administration page.

## Goals / Non-Goals

**Goals:**

- Service accounts with secrets, expiry, rotation, roles and final deactivation.
- The screen SCR-041.
- A store that change 4 can use to check a secret and to record `last_used_at`.

**Non-Goals:**

- Token endpoint, bearer authentication and `last_used_at` updates (change 4).
- A person administration screen.
- A UI for the maximum validity.

## Architecture Constraints

- [ADR-026](../../../adrs/ADR-026-external-system-integration.md): the service account is the only machine identity, and its `client_id` is the audit `agent_id`.
- [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md):
  - SQL in `core` (`ServiceAccountStore`);
  - rules and audit in `app/access` (`ServiceAccounts`);
  - the six operations in `AdminController` (`api/admin`), which implements the generated `AdminApi`; a second controller cannot implement the same interface without duplicate mappings. `ServiceAccountViews` maps between contract and app layer.
- [ADR-032](../../../adrs/ADR-032-authorization-model.md): `x-oea-authorization` with `service-account:manage`. The condition "request body sets or changes roles" needs `role:assign` and is checked in `ServiceAccounts`, as the interceptor checks only the base permission. Both operations carry `@ConditionsEnforcedByHandler`. Its probe needs a role with `service-account:manage` and without `role:assign`, which no built-in role is, so `HandlerEnforcedConditionsTests` creates the role `probe-account-manager` and a probe service account. SoD-4 is enforced through `RoleAssignments`.
- [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md): audit events in the same transaction as the change. No secret or hash is ever part of an event.
- [ADR-050](../../../adrs/ADR-050-service-account-access-tokens.md): the token rules apply in change 4. This change provides the secret hash and its status and expiry, which rule 6 checks.

## Decisions

### D1 – One row per secret version

`machine_credentials` follows the business object: each rotation inserts a row with the same `client_id` and sets the previous row to `rotated` with `revoked_at`. A partial unique index `(client_id) WHERE status = 'active'` allows at most one active secret.

- **Why:** the rotation history stays auditable, and change 4 checks exactly one row.
- **Alternative:** overwriting the hash in a single row per account, as US-142's technical notes suggest. It loses the rotation history and makes `rotatedAt` the only trace.
- The `client_id` is generated once on creation, stored on every row, and returned as `ServiceAccount.id`.

### D2 – Secret format and hashing

The secret is `oea_cs_` plus 32 bytes from `SecureRandom`, encoded base64url without padding (50 characters). It is hashed with the argon2id `PasswordHasher`, with the same parameters as passwords.

- **Why the prefix:** it matches `oea_at_` of ADR-050, so secret scanners and log redaction recognize it.
- **Why argon2id:** REQ-176 requires it, and the hasher already exists with its parameters.
- **Alternative:** SHA-256, since the secret has 256 bits of entropy. It would conflict with REQ-176 and the business object.

### D3 – Expiry

- `expires_at = created_at + validity`, where `validity` is `secretValidityDays` and defaults to 90 days.
- The bounds are 1 to `oea.service-accounts.secret-max-validity-days`. That value defaults to 365 and is checked at startup to lie between 1 and 3650.
- `secretExpiresSoon` is computed on read with the injected `Clock`: `expires_at − now ≤ 14 days`.
- **Why a configuration value:** the business object says "maximum configurable", and no settings screen exists yet (SCR-038 is a legacy mockup).
- **Alternative:** a system setting in `system_settings`. That needs `updateSystemSettings`, which is still planned.

### D4 – Roles through role assignments

Roles are local role assignments (`source=local`), created and revoked through `RoleAssignments`. There is no separate role column.

- **Why:** the permission resolution, SoD-4, the audit events, expiry and revocation stay in one place. `listRoleAssignments` already works for a service account.
- **Order of checks:**
  - First, before anything is stored, all roles of the request are checked: they must exist and be assignable. A violation gives 422 naming the first offending role and an `authz.denied` event with reason `sod-4`.
  - Only then are the person, the secret and the assignments written, in one transaction.
- **`updateServiceAccount`:** it computes the difference between the requested and the granted roles. It revokes the removed roles with reason "service account roles changed" and assigns the added ones. An unchanged role list needs no `role:assign`.
- **Privileged-assignment approval (Q15):** no assignable built-in role is privileged. Custom assignable roles cannot carry admin-class permissions. So the 202 path of `createRoleAssignment` is not reachable here. A privileged role is rejected like a non-assignable one, as a defensive check.

### D5 – Description and status on `persons`

- The migration adds `persons.description TEXT` (nullable, at most 1000 characters by a check). The column is used only by service accounts.
- The status uses `persons.status`: `active` on creation, `inactive` on deactivation. `PersonLifecycle` already rejects status changes of service accounts with 422, so `deleteServiceAccount` is the only path to `inactive`, and nothing leads back.
- **Alternative:** a separate `service_accounts` table. It would duplicate the identity, which ADR-026 puts in `persons`.

### D6 – Deactivation

In one transaction:

- set the person `inactive`;
- set the active secret to `revoked` with `revoked_at` and `revoked_by`;
- revoke every granted assignment through `RoleAssignments`, with reason "service account deactivated".

The last-admin-path check does not apply, since a service account never holds `system-admin`. Every later write to the account answers 409 (problem type `conflict`), and `getServiceAccount` still returns it.

### D7 – Re-authentication

`ServiceAccounts` calls `RecentAuthentication.require` for create, rotate and deactivate, before any check that stores something. `updateServiceAccount` does not require it: it hands out no secret, and REQ-172 names creation and rotation of secrets. The boundary test uses a fixed `Clock` at 4:59 and 5:01 minutes after the session's authentication.

### D8 – Screen SCR-041

- **Route and link:** `ServiceAccountsPage.vue` on `/admin/service-accounts`. The header link `header-service-accounts` is shown with `service-account:manage`.
- **Table:** name, client ID (shortened, copyable), status, roles, last use, secret expiry, with an amber "Expires soon" badge.
- **Row actions:** "Edit roles", "Rotate secret" and "Deactivate", hidden for inactive accounts.
- **Dialogs:**
  - The create dialog offers name, description, validity (days, default 90) and a multi-select of the roles with `assignableToServiceAccounts=true`, from `listAuthorizationRoles?assignableToServiceAccounts=true`.
  - The secret dialog shows the secret in a read-only field with a copy button and the warning, and forgets the value on close.
  - "Deactivate" and "Rotate secret" need a confirmation.
  - A 401 `reauthentication-required` shows the notice to log in again.
- **Story:** the SCR-041 story has the states "list", "create", "secret", "edit roles" and "deactivate".

## Data Model

The migration `V30__service_accounts.sql` makes these changes:

- It creates `machine_credentials` as modelled in `docs/data-model.puml`:
  - `id`, `client_id`, `person_id`, `client_secret_hash`, `status`;
  - `created_at`, `created_by_person_id`, `created_by_admin_id`;
  - `expires_at`, `last_used_at`, `revoked_at`, `revoked_by_person_id`, `revoked_by_admin_id`;
  - the partial unique index on `client_id` for active rows;
  - a check that `expires_at > created_at`.
- The check `persons.person_type = 'service-account'` becomes a trigger, since a foreign key cannot check it.
- It adds `persons.description`.
- It adds a unique index on `lower(name)` for rows with `person_type = 'service-account'`.
- It adds the grants for `oea_app`, as earlier migrations do.

Updates to the model sources:

- `docs/data-model.puml` gets `persons.description` and the name index, with a note.
- `business-objects/person.md` gets the description of a service account.
- `business-objects/machine-credential.md` gets the secret format and the configured maximum.

## API

The delta to `api/openapi.yaml`:

- The six operations under `/admin/service-accounts` lose `x-oea-status: planned` and the 501 response.
- **`ServiceAccount`:**
  - `roles` becomes required;
  - it gets `secretExpiresAt` (date-time, null for an inactive account) and `secretExpiresSoon` (boolean);
  - `name` gets `maxLength: 100` and `description` gets `maxLength: 1000`.
- **`ServiceAccountWrite`:**
  - `name` gets `minLength: 1` and `maxLength: 100`, and `description` gets `maxLength: 1000`;
  - it gets `secretValidityDays` (integer, minimum 1, default 90). The maximum comes from the instance configuration, so the description names it, and the server answers 400.
  - `secretValidityDays` is ignored on update.
- **Request body of `rotateServiceAccountCredentials`:** a new optional schema `ServiceAccountRotation` with `secretValidityDays`.
- **Responses:**
  - create, rotate and delete get 401 with `reauthentication-required` in the description;
  - update, rotate and delete get 409 for an inactive account;
  - create and update describe 422 for an unknown, non-assignable or privileged role and for a taken name.
- The operations reuse the existing problem types. No new tag.

## Risks / Trade-offs

- **[argon2id costs memory and time]** Each creation or rotation hashes once. That is acceptable for an admin action, which also needs `service-account:manage` and a recent authentication. The concurrency limit of ADR-050 rule 6 belongs to the token endpoint of change 4, where unauthenticated requests hash.
- **[Unused `persons.description`]** The column is unused for humans. A check limits its length, and nothing reads it for humans.
- **[Effective permissions are tested without a token]** Until change 4, the test that `viewer` gives no `entity:write` uses `PermissionResolver` for the service account's person. Change 4 repeats it with a bearer token and 403 (US-142 AC4, US-149 AC5).
- **[Secret in browser memory]** The secret dialog keeps the secret only in a local ref that is cleared on close. It is never written to stores, logs or the URL.

## Migration Plan

- One forward migration (V30), with no data to migrate, since no service accounts exist.
- Rollback means redeploying the previous version. The new table and column are unused by it.
