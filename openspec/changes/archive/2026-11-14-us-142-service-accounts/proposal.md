# Proposal

## Why

External systems such as scanners, CI pipelines and the CMDB importer must write to the API without a human login. Their writes must be identifiable as machine-originated, and a leaked secret must never grant administration ([UC-02](../../../requirements/use-cases/UC-02-system-admin-bootstrapping.md), [UC-23](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md)). The contract declares the service account operations as `planned`, and no table holds their secrets yet. This is S12 change 3. It creates the accounts that the token endpoint of change 4 (`s12-client-credentials-token`) authenticates.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-02: System Administration Bootstrapping](../../../requirements/use-cases/UC-02-system-admin-bootstrapping.md), [UC-23: Manage Roles and Role Assignments](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (A4, E2) |
| User Stories | [US-142: Create a Service Account for External Systems](../../../requirements/user-stories/US-142-create-service-account.md) (AC1, AC3 for the secret; AC4 realigned), [US-149: Assign Roles to a Service Account](../../../requirements/user-stories/US-149-assign-roles-to-service-account.md) (AC1, AC3, AC4) |
| Requirements | [REQ-155: Service Account Management for External Systems](../../../requirements/req/REQ-155-service-account-management.md) (AC1, AC4; inputs realigned), [REQ-176: Machine credential lifecycle and scoping](../../../requirements/req/REQ-176-machine-credential-lifecycle.md) (AC1; secret expiry and the 14-day notice of AC2), [REQ-172: Re-authentication](../../../requirements/req/REQ-172-reauthentication-sensitive-actions.md), [REQ-157: Permission model](../../../requirements/req/REQ-157-permission-model.md) |
| ADRs | [ADR-026: External system integration](../../../adrs/ADR-026-external-system-integration.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (SoD-4), [ADR-050: Access tokens of service accounts](../../../adrs/ADR-050-service-account-access-tokens.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S12 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-14:**

- **Screen:** a new screen SCR-041 "Service accounts" under administration. It lists the accounts with status, roles, last use and secret expiry, and has four dialogs:
  - "Create", which shows the secret once;
  - "Edit roles", which offers only assignable roles;
  - "Rotate secret", which shows the new secret once;
  - "Deactivate".
- **Secret expiry:** create and rotate set the expiry of the secret, by default 90 days. A shorter validity can be chosen, up to a maximum set in the instance configuration. The list marks secrets that expire within 14 days. Change 4 enforces the expiry at the token endpoint.
- **Deactivation is final:** a deactivated service account cannot be reactivated. A new integration gets a new account, so a `client_id` always names one integration in the audit log.

**Service accounts (`identity`):** the six planned operations under `/admin/service-accounts` are implemented and lose `x-oea-status: planned`. All of them need `service-account:manage`.

- **`createServiceAccount`:** creates a person with `person_type=service-account`, the active secret and the role assignments. It returns the account, whose `id` is the `clientId`, and, once only, the plaintext `clientSecret` (201).
  - The secret has 256 random bits with the prefix `oea_cs_` and is stored only as an argon2id hash (REQ-176).
  - The roles default to `integration-writer`. Setting roles also needs `role:assign`.
  - A role not flagged `assignableToServiceAccounts`, which includes every role with an admin-class permission, is rejected with 422, and nothing is created (SoD-4, REQ-176 AC1, REQ-155 AC4). The denial is audited as `authz.denied`.
  - A name already used by a service account is rejected with 422.
- **`listServiceAccounts` and `getServiceAccount`:** they return the accounts without secrets, with roles, status, `lastUsedAt`, `rotatedAt`, the expiry of the secret and whether it expires within 14 days.
- **`updateServiceAccount`:** changes name, description and roles. Changed roles become local role assignments or revocations, audited as `role.assigned` and `role.revoked`, by the rules of `identity/role-assignments`.
- **`rotateServiceAccountCredentials`:** creates a new secret with the same `clientId`, returned once. The old secret becomes `rotated` at once.
- **`deleteServiceAccount`:** sets the account to `inactive` for good. It revokes its secret and its role assignments. Every further write to the account is rejected with 409.
- **Re-authentication:** create, rotate and deactivate need an authentication within the last 5 minutes (REQ-172). Without it, the request is answered with 401 `reauthentication-required`.
- **Audit:** the events `service-account.created`, `service-account.updated`, `service-account.secret-rotated` and `service-account.deactivated` are recorded. A secret is never logged.

**Frontend (SCR-041):** the page `/admin/service-accounts` with its four dialogs and a navigation entry for principals with `service-account:manage`. The secret dialog shows the secret once, with a copy button and a notice that it cannot be shown again.

**Realigned sections:**

- **US-142 AC1 and REQ-155 inputs:** the request takes `roles` (a list, default `["integration-writer"]`) instead of a single `role`, as the contract already does. It also takes an optional `secretValidityDays`.
- **US-142 AC4:** a service account without `integration-writer` is answered with 403 on a write. This is proven with a token in change 4. This change proves that such an account has no `entity:write` permission.
- **US-142 technical notes:** `machine_credentials` follows the business object: one row per secret version, with its own `id`, `status` and `expires_at`, and not `client_id` as the primary key.
- **REQ-155 error case** "Role `integration-writer` not present → 422" becomes "a role that is unknown or not assignable → 422".

## Out of Scope

These parts belong to change 4 (`s12-client-credentials-token`):

- the token endpoint and bearer tokens;
- `lastUsedAt` and the check of the expiry at the token endpoint;
- every acceptance criterion that needs a token: US-142 AC2, the use of the old secret in US-142 AC3 and REQ-155 AC3, US-149 AC2 and AC5, REQ-155 AC2, and REQ-176 AC2 to AC4.

Also out of scope:

- reactivating a service account (owner decision);
- revoking a single secret without rotating it;
- a setting for the maximum secret validity on SCR-038 (it stays an instance configuration value);
- notifications about expiring secrets beyond the marker in the list.

## Capabilities

### New Capabilities

- `identity/service-accounts`: creating, reading, changing and deactivating service accounts, their secrets with rotation and expiry, the roles they may hold, and the screen SCR-041.

### Modified Capabilities

- None. `identity/role-assignments` already rejects a non-assignable role for a service account (SoD-4), and `identity/authorization-roles` already rejects an admin-class permission in an assignable custom role (US-149 AC4). This change reuses both rules unchanged.

## Impact

- **api/openapi.yaml:**
  - the six `/admin/service-accounts` operations lose `planned`;
  - `ServiceAccount` gets `secretExpiresAt` and `secretExpiresSoon` (its `id` already is the `clientId`);
  - `ServiceAccountWrite` gets `secretValidityDays`;
  - `rotateServiceAccountCredentials` gets an optional body with `secretValidityDays`;
  - the responses 401 `reauthentication-required`, 409 for an inactive account and 422 are described.
- **backend:**
  - a Flyway migration for `machine_credentials`;
  - `core` for the store;
  - `app/access` for `ServiceAccounts`, which reuses the role assignment rules and `PasswordHasher` (argon2id);
  - `api` for the controller;
  - the configuration value `oea.service-accounts.secret-max-validity-days`.
- **frontend:** `ServiceAccountsPage.vue`, `api/service-accounts.ts`, the router and navigation, and the SCR-041 story.
- **Documentation:**
  - `docs/screens/SCREENS.md` (SCR-041);
  - `docs/audit-event-catalog.md`;
  - the user manual, a new page `administration/service-accounts.md` in English and German;
  - US-142, US-149, REQ-155 and REQ-176;
  - `docs/data-model.puml` and `business-objects/machine-credential.md`, which are checked and get no change except notes, since the table is already modelled.
