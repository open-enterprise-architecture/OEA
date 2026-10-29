# Design

## Context

See proposal.md – Why. Relevant current state:

- **Roles of a principal:**
  - `PermissionResolver` derives roles per request: the local system admin account holds `system-admin`; a person holds the roles of their session's IdP group mappings (`session_idp_roles`).
  - Results are cached per session for at most 60 seconds (`MAXIMUM_AGE`), and `evictAll()` clears the cache.
  - A privileged role of a person is effective only in a second-factor-verified session (SoD-8).
- **Sessions:** `Sessions.authenticate` accepts sessions of persons in status `invited` or `active` only. `sessions.revoked_reason` already allows `role-change`.
- **SoD-7:** `AdministrationPaths.remotePaths()` counts usable IdP group mappings; only `LocalAdminAccount.deactivate` checks it, under a row lock on the account. IdP group mappings can be deleted, deactivated or retargeted without any check.
- **Contract:** `listRoleAssignments`, `createRoleAssignment`, `revokeRoleAssignment` and `getAuthorizationRole` exist with `x-oea-status: planned`. `RoleAssignment` has `status` `scheduled | active | expired | revoked`, `sodConflict` and approval fields.
- **Data model:** `role_assignments` is designed in `docs/data-model.puml` (DM-4), but no migration creates it yet. The column `status` is `pending | granted | rejected | revoked`; expiry is derived from `valid_to`.
- **Scheduling:** `@EnableScheduling` exists (change `s3-throttling-and-denials`).

## Goals / Non-Goals

**Goals:**

- Local assignments with the validity and history of DM-4, effective within 60 seconds.
- One SoD-7 check for every revocation that exists after this change, safe against concurrent requests.

**Non-Goals:**

- Four-eyes approval, person status changes, changing existing assignments, an admin UI.

## Architecture Constraints

- **ADR-028:** the services live in `io.oea.app.access`, the controllers in `io.oea.api.access` (assignments, as for mappings) and `io.oea.api.admin` (`getAuthorizationRole`, tag Admin).
- **ADR-032 DM-4:** no hard delete. The runtime role has no `DELETE` grant on `role_assignments`, and a revocation sets `revoked_*`.
- **ADR-032 API-9:** SoD-7 violations are answered with `409`. **SoD-1:** with `403` and reason `self-administration`, audited through `PermissionDenials`.
- **ADR-033:** the new events are added to `docs/audit-event-catalog.md`.
- **ADR-015:** one Flyway migration `V9__role_assignments.sql`.
- **ADR-013:** the contract is updated first: status `implemented`, `429`, problem types.

## Decisions

### D1: Table as designed, approval columns unused

`V9__role_assignments.sql` creates `role_assignments` exactly as in `docs/data-model.puml` (DM-4), including the approval columns with `approval_required = false`, plus `expiry_recorded_at` (D4). Its constraints are the CHECKs of the data model: one granter, `valid_to > valid_from`, `status = 'revoked'` ⇔ `revoked_at`.

The runtime role `oea_app` gets `SELECT, INSERT, UPDATE` and no `DELETE`. Creating the full table now avoids a second migration for US-150.

The API `status` is derived:

- `revoked` if revoked;
- `expired` if `valid_to` ≤ now;
- `scheduled` if `valid_from` > now;
- otherwise `active`.

`pending` and `rejected` do not occur yet.

### D2: Local grants in PermissionResolver

For a person, `resolve` adds every assignment that is effective. That means status `granted`, `valid_from` ≤ now < `valid_to`, and the person has status `active`, which is read with the assignment.

The grant gets source `local`. Privileged roles follow the same SoD-8 rule as IdP-derived ones.

`GET /me` already lists grants with their source, so the existing schema covers `local`. Every create and revoke calls `evictAll()`. Expiry needs no eviction, because the 60-second cache age bounds it.

### D3: Revocation ends sessions

A revocation sets `status = 'revoked'`, `revoked_at` and `revoked_by_*`. In the same transaction it revokes all open sessions of the person with reason `role-change` (REQ-164), as `LocalAdminAccount` does for its sessions. The next login derives the remaining roles.

### D4: Expiry job

A scheduled method runs every 60 seconds. It selects assignments with `valid_to` ≤ now and `expiry_recorded_at IS NULL` (`FOR UPDATE SKIP LOCKED`), writes one `role.assignment-expired` each, sets `expiry_recorded_at`, and calls `evictAll()` if any assignment expired.

The job only records the expiry. Whether a role is effective is always decided by `valid_to` (D2), so a delayed job never extends access.

### D5: SoD rules at creation

- **SoD-1:** the assignee is not the caller's person. Both the create and the revoke operation reject this with `SelfAdministrationException`, which is audited through `PermissionDenials`. The link between the system admin account and a person (`linkedPerson`, data model) does not exist in the schema yet; its SoD-1 case follows with the link.
- **SoD-4:** for a person with `person_type = 'service-account'`, the role must have `assignable_to_service_accounts`; otherwise `422`.
- **SoD-3:** the permissions of the person's granted local assignments plus the new role are checked. If they contain `metamodel:write` and `visibility:configure` or `visibility-scope:assign`, the request is rejected with `409` `sod-conflict` unless `acknowledgeSodConflict` is set.
  - A new column `sod_conflict` stores the flag at creation, so that a later read does not depend on other assignments.
  - IdP-derived roles are not part of the check: they belong to sessions and change with the IdP.
- **Existing assignment:** an assignment of the same role to the same person that is granted and not expired gives `409` (`conflict`), so that it cannot be duplicated.

### D6: One SoD-7 check with an advisory lock

`AdministrationPaths` gets `requireRemainingPath(Excluded change)`. It takes `pg_advisory_xact_lock(<fixed key>)` and counts the usable paths after the change:

- the local account unless it is being deactivated;
- usable mappings except the one being deleted, deactivated or retargeted;
- the open-ended `system-admin` assignments of active persons, except the one being revoked.

If no path remains, it throws `LastAdminPathException`. Every guarded transaction calls it before writing:

- `LocalAdminAccount.deactivate`: replaces its own count but keeps the row lock.
- `RoleAssignments.revoke`, for assignments of `system-admin`.
- `IdpGroupMappings.update` and `delete`, for mappings that are currently a usable path to `system-admin`.

The lock serializes these transactions, so two parallel revocations of the last two paths see each other's result.

- **Alternative – `SERIALIZABLE` transactions:** this needs retry handling in every caller.
- **Alternative – row locks on each counted row:** the set of rows changes with each kind of path, so they cannot be locked consistently.

The person status change of US-148 will call the same check.

### D7: Role preview

`getAuthorizationRole` returns `AuthorizationRole` from `authorization_roles` and `role_permissions`, with the permissions and a `privileged` flag (any admin-class permission). It needs `role:assign` as in the contract. `listAuthorizationRoles` is already implemented, and the detail uses the same mapping.

## Data Model

- **`V9__role_assignments.sql`:** `role_assignments` as in `docs/data-model.puml`, plus:
  - `expiry_recorded_at TIMESTAMPTZ` (D4);
  - `sod_conflict BOOLEAN NOT NULL DEFAULT false` (D5);
  - index on `person_id` where status `granted`;
  - grants without `DELETE`.
- **`docs/data-model.puml`:** the two columns, and the note that `persons.status = 'active'` is part of the effectiveness rule (already noted).
- **`business-objects/role-assignment.md`:** attributes `sodConflict` and `expiryRecordedAt`, and the API status mapping of D1.

## API

Delta to `api/openapi.yaml`:

- **`listRoleAssignments`, `createRoleAssignment`, `revokeRoleAssignment`, `getAuthorizationRole`:**
  - remove `x-oea-status: planned`;
  - add `429`;
  - replace `501` by the real responses.
- **`createRoleAssignment` `409`:** problem types `sod-conflict` and `conflict` (duplicate).
- **`revokeRoleAssignment` `409`:** problem types `last-admin-path` and `conflict` (already revoked or expired).
- **`updateIdpGroupMapping`, `deleteIdpGroupMapping`:** `409` with problem type `last-admin-path`. The `409` exists already; its description is aligned.
- **`createRoleAssignment` `202`:** stays documented for US-150 and is not returned.

## Risks / Trade-offs

- **The advisory lock serializes all SoD-7-guarded changes of the instance.** → They are rare administrative actions; the lock is held only for the transaction.
- **A person holding `system-admin` counts as a path although the role needs a second factor at login (SoD-8).** → The person can still reach administration after setting up a factor at the IdP. Requiring proof of an earlier effective use, as for mappings, would make a freshly granted administrator not count. Open-ended assignments of active persons are a deliberate grant by another administrator.
- **The expiry job runs on every backend instance.** → `SKIP LOCKED` and `expiry_recorded_at` record each expiry once; OEA runs one instance (D1 of `s3-throttling-and-denials`).
