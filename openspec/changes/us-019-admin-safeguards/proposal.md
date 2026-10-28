# Proposal

## Why

UC-02 requires that system administration can move to the identity provider without leaving the instance either open or locked. OEA can already map an IdP group to the role `system-admin` (US-147), but an operator gets no hint that the mapped group has never been seen. The local bootstrap account also cannot be deactivated once remote administration works (US-019). If both access paths are lost (UC-02 E4), the only way back is an unaudited edit in the database. This second change of S3 closes these gaps before the instance is used beyond its first administrator.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-02: System admin bootstrapping](../../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) (A1 remote bootstrapping, E4 complete lockout) |
| User Stories | [US-014: Remote bootstrapping via IdP claim mapping](../../../requirements/user-stories/US-014-remote-bootstrapping.md); [US-018: Warning for an empty or non-existent remote admin claim](../../../requirements/user-stories/US-018-warning-empty-admin-claim.md); [US-019: Ability to disable the local system admin account](../../../requirements/user-stories/US-019-disable-local-admin.md) |
| Requirements | [REQ-014: Remote bootstrapping via IdP claim mapping](../../../requirements/req/REQ-014-remote-bootstrapping.md); [REQ-018: Warning for empty remote admin claim](../../../requirements/req/REQ-018-warning-empty-admin-claim.md); [REQ-019: Ability to disable the local system admin account](../../../requirements/req/REQ-019-disable-local-admin.md); [REQ-173: Account recovery and temporary password lifecycle](../../../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC4 (break-glass) |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (SoD-7, DM-6), [ADR-042](../../../adrs/ADR-042-setup-phase-without-second-factor.md) (setup phase, second factor), [ADR-033: Audit trail](../../../adrs/ADR-033-audit-trail-hardening.md) (event catalog), [ADR-006](../../../adrs/ADR-006-auth-stack-choice.md) |
| Sprint / Milestone | S3 / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md), S3 change 2) |

The change is named after US-019. It also covers US-014, US-018 and the break-glass procedure of UC-02 E4.

**Owner decisions (2026-10-28):**

- **US-014:** counts as realized through IdP group mappings to `system-admin` (US-147). This change adds tests and documentation for its acceptance criteria and no new configuration path.
- **US-018:** the warning works without a provider API. It is based on what logins at this instance have shown.
- **US-019:** deactivating the local account when it is the last path to `system-admin` is rejected with 409 (SoD-7), as ADR-032 requires. It is not just a warning.
- **Break-glass (UC-02 E4):** a CLI command inside the backend container restores local admin access and is audited.

**Realigned sections** (non-binding details made concrete by this change):

- **US-018 AC1 and REQ-018:** "the group is empty or does not exist" becomes "no login at this instance has presented the group yet". OEA cannot see group membership without provider API access. AC2 (save despite the warning) is kept: the mapping is saved and the response carries the warning.
- **US-019 AC2 and REQ-019:** "warns of a possible lockout, but does not necessarily block" becomes "rejected with 409 if no other usable path to `system-admin` remains" (ADR-032 SoD-7, business object `system-admin-account`). A mapping counts as a usable path only after it has granted the role in at least one login (design D3).
- **REQ-173 AC4:** the break-glass command does not issue a time-limited recovery credential that must be changed after login. The operator sets the new password and, after the setup phase, the new TOTP factor directly in the command (design D5). Credentials typed in the operator's terminal have no transport risk to limit. No password-change flow exists yet that a temporary credential could lead to.
- **REQ-014, US-014:** "configuration endpoint for claim mapping" and "`SystemAdminAccount` with `mode: remote`" are the existing IdP group mapping operations with role `system-admin` (business object 0.2.0, DM-6).

## What Changes

- **Remote system administration (US-014):** tests prove that a person whose token carries a group mapped to `system-admin` receives the system admin permissions, and that a person without the group does not. The manual describes how to hand over system administration to an IdP group.
- **Mapping usage (US-018):** every IdP group mapping records when it last granted its role in a login (`lastMatchedAt`). Creating or changing a mapping to `system-admin` returns a warning if the mapping has never matched and no current session of the provider carries the group. The mapping is saved anyway.
- **Deactivating the local account (US-019):**
  - A principal with `auth-config:manage` can read the state of the local system admin account and deactivate it.
  - Deactivation revokes the account's sessions and ends password login.
  - It is rejected with 409 if no other usable path to `system-admin` remains. A usable path is an active, approved mapping to `system-admin` of an enabled provider that has matched at least once.
  - Both outcomes are audited.
- **Break-glass command (UC-02 E4):**
  - `oea admin recover`, run inside the backend container, reactivates the local system admin account and sets a new password typed in the terminal.
  - After the setup phase it also replaces the second factor: the old TOTP credentials are revoked, and a new secret is shown and confirmed with a code in the terminal.
  - It revokes the account's sessions and clears its login throttling, and writes the audit event `system.admin-recovered` with source `CLI`.
  - Nothing changes if a step fails.
- **Audit:**
  - New events `system-admin-account.deactivated` and `system.admin-recovered`.
  - New audit source `CLI` in the audit schema and the contract.
  - The event catalog is updated.

## Out of Scope

- **SoD-7 for revocations:** deleting or deactivating the last mapping to `system-admin`, and the other revocations of SoD-7, stay unchecked; they are covered by [US-146](../../../requirements/user-stories/US-146-protect-last-system-admin-path.md). Until then, the break-glass command is the way back after such a change.
- **Reactivation through the API:** reactivating a deactivated local account through the API is not offered; the break-glass command reactivates it.
- **Provider API checks:** group checks through provider APIs such as Microsoft Graph or the Authentik API are not part of this change (owner decision).
- **REQ-173 beyond AC4:** temporary passwords, password change, recovery codes and the factor reset of persons follow with UC-03.
- **Admin UI:** there is no admin UI for mappings or the local account; the operations are used through the API and documented in the manual.
- **Operations change of S3:** JSON logging and the icon check (#63, #62) are the third S3 change.

## Capabilities

### New Capabilities

- `identity/admin-recovery`: the break-glass command for a complete admin lockout, covering reactivation, the new password, the new second factor and the audit.

### Modified Capabilities

- `identity/access-administration`: warning for a mapping to `system-admin` that has never matched, `lastMatchedAt` on mappings, and deactivation of the local system admin account with the SoD-7 check (a deactivated account can no longer log in).
- `identity/idp-roles`: system admin permissions derived from a group mapping (US-014), and the recorded last match of a mapping.

## Impact

- **`backend/`:**
  - `io.oea.app.access.IdpGroupMappings`: warning and `lastMatchedAt` in the view.
  - `io.oea.app.security.IdpRoles`: records the match.
  - New service for the local account (state, deactivation, SoD-7 path check) with an endpoint in `io.oea.api.admin`.
  - New `oea admin recover` command next to `audit verify` and `secrets reencrypt` (dispatch in `OeaBackendApplication`).
  - `AuditEvent.Source.CLI`.
- **Flyway:**
  - `V8__admin_safeguards.sql`: `idp_group_mappings.last_matched_at`, `system_admin_accounts.deactivated_by_person_id`.
  - Audit migration `V3__cli_source.sql`: source `CLI`.
- **`api/openapi.yaml`:**
  - `GET /admin/system-admin-account`.
  - `POST /admin/system-admin-account/deactivate`.
  - `lastMatchedAt` and `warnings` on `IdpGroupMapping`.
  - `CLI` in the audit source enums.
- **`docs/data-model.puml`, `business-objects/idp-group-mapping.md`, `business-objects/system-admin-account.md`:** new attributes.
- **Docs:**
  - `docs/audit-event-catalog.md`.
  - `manual/administration/roles-and-permissions.md` (system administration through a group, warning).
  - `manual/administration/operations.md` (deactivating the local account, break-glass), with their German pages.
  - `manual/setup/first-administrator.md` (pointer to the recovery).
  - UC-02 open question E4 answered; US-018, US-019, REQ-018, REQ-019, REQ-173 realignment notes.
- **`frontend/`:** no change, no screen added.
