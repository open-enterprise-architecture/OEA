# Design

## Context

See proposal.md – Why. Relevant current state:

- **Remote administration**:
  - IdP group mappings (`io.oea.app.access.IdpGroupMappings`, US-147) can map any role, `system-admin` included.
  - `IdpRoles` derives the roles at login and re-evaluation into `session_idp_roles`.
  - `PermissionResolver` withholds a privileged derived role without a second factor at the IdP (`mfa-required`).
  - Nothing records whether a mapping has ever matched.
- **Local account**:
  - `system_admin_accounts` has the status `deactivated` and the columns `deactivated_at`, `deactivated_by_admin_id`.
  - `SystemAdminAccount.canLogIn()` already refuses a deactivated account in `LocalLogin`.
  - No operation reads or deactivates the account.
- **Credentials and sessions**:
  - `local_credentials` has the status `revoked` with the reason `factor_reset`.
  - `sessions.revoked_at` ends a session.
  - `TotpEnrollment` and `TotpAlgorithm` create and check TOTP secrets encrypted with `SecretCipher` (KEK, REQ-188).
- **CLI commands**:
  - `OeaBackendApplication` dispatches `migrate`, `audit verify` and `secrets reencrypt`.
  - Each builds its own small Spring context without a web server and sets `oea.mode` for `ConfigurationGuards`.
  - `secrets reencrypt` uses the runtime datasource and the KEK.
- **Audit**: the source of an audit record is limited to `UI`, `API`, `Job` and `Import` (audit schema check, contract enums).

## Goals / Non-Goals

**Goals:**

- One definition of a "usable path to `system-admin`" (SoD-7), used by the deactivation now and reusable by US-146 for revocations.
- A warning for US-018 that needs no provider credentials and works for every OIDC provider.
- A break-glass procedure that needs container access, changes everything or nothing, and is audited like web actions.

**Non-Goals:**

- SoD-7 checks on mapping deletion or deactivation (US-146).
- An admin UI.
- Temporary passwords and password change (REQ-173 AC1–AC3, UC-03).

## Architecture Constraints

- **ADR-028 (layers):**
  - The new service for the local account and the SoD-7 path check live in `io.oea.app.access`, its endpoint in `io.oea.api.admin`.
  - The recovery logic lives in a new `io.oea.app.recovery`, and its command class next to it as `AdminRecoverCommand`, like `AuditVerifyCommand` in `io.oea.audit.verify`.
- **ADR-032 SoD-7, DM-6:** the remote path is an IdP group mapping to `system-admin`. Deactivating the last usable path is rejected with 409.
- **ADR-042:** after the setup phase, every login of the system admin needs TOTP. The recovery therefore cannot leave the account without an enrolled factor.
- **ADR-033 §8:** the new events are records of the hash chain and are added to `docs/audit-event-catalog.md`.
- **ADR-013:** the contract is changed first. **ADR-015:** Flyway migrations, one per schema.
- **REQ-188:** the CLI encrypts the new TOTP secret with the current KEK, which it reads from the same configuration as the backend.

## Decisions

### D1: US-014 needs no new code

A mapping to `system-admin` already grants the role, and only with a second factor at the IdP (spec `identity/idp-roles`, "Privileged role with second factor"). This change adds a Cucumber scenario for the two acceptance criteria and documents the hand-over in the manual. A system admin from the IdP calls an administrative operation and gets 200; a person without the group gets 403.

- **Alternative – a separate "remote admin claim" setting:** this would create a second mechanism next to the mappings, with its own SoD and audit rules. DM-6 already folded the former `mode: remote` into the mappings.

### D2: Last match recorded by IdpRoles

`idp_group_mappings.last_matched_at` is set when `IdpRoles` writes a derived role at login or re-evaluation and the role is effective. A non-privileged role is always effective; a privileged role counts only if the session is second-factor-verified, which is the same rule as `PermissionResolver`.

To avoid a write on every 15-minute re-evaluation, the update only runs when the stored value is null or older than one hour:

```sql
UPDATE idp_group_mappings SET last_matched_at = ?
WHERE id = ? AND (last_matched_at IS NULL OR last_matched_at < ? - interval '1 hour')
```

The mapping view exposes it as `lastMatchedAt`.

- **Alternative – derive it from `session_idp_roles`:** these rows only exist while sessions live. An instance whose admins log in rarely would lose the evidence.

### D3: Usable path to system-admin

`AdministrationPaths` counts:

- the local system admin account if its status is not `deactivated`;
- every IdP group mapping with role `system-admin`, `active`, `approval_status = 'approved'`, not deleted, of a provider with `enabled = true`, and `last_matched_at IS NOT NULL`.

The deactivation is allowed only if, apart from the account itself, at least one mapping counts. Direct role assignments to persons do not exist yet. When US-146 adds them, it extends `AdministrationPaths`.

Requiring a past match is stricter than the wording of ADR-032 ("active IdP group mapping"). It applies its intent ("administration cannot be lost"): a mapping that has never granted the role is exactly the lockout case of US-018.

The check and the status change run in one transaction that locks the account row (`SELECT … FOR UPDATE`).

### D4: Warning in the write response

`createIdpGroupMapping` and `updateIdpGroupMapping` return the stored mapping with a `warnings` array of `{code, message}`; it is empty in every other response, because the generated model always serializes the array. Code `group-not-seen` is set when all of these hold:

- the target role is `system-admin`;
- the mapping has no `last_matched_at`;
- no session that is not revoked and not expired, of the mapping's provider, has the group identifier in `idp_group_identifiers`.

The check only reads; it never blocks.

- **Alternative – a confirmation flag** (`acknowledgeWarnings: true`, 409 without it): this is closer to the wording "acknowledges the warning", but turns a best-effort hint into a second round trip for API clients. US-018 AC2 explicitly asks for no hard block.

### D5: Break-glass as an interactive CLI command

Run `docker compose exec -it backend java -jar /app/oea-backend.jar admin recover`. It uses the backend container's environment: datasource of `oea_app` and KEK.

The command:

1. Builds a context without a web server, with `oea.mode=admin-recover`. It contains the datasource, `JdbcTemplate`, the transaction manager, `SecretsConfiguration`, `PasswordHasher` and `AuditWriter`; there is no JPA and no Flyway.
2. Reads the bootstrapped account from `system_settings`. Without it, it exits 1 with a pointer to `/setup`.
3. Reads the password twice from the console, without echo. Without a console it reads lines from standard input, for tests and `exec -i`. It repeats until both entries match and the length is 12–128.
4. If the setup phase has ended, it generates a TOTP secret and shows it once (Base32 and `otpauth` URI), then asks for a code, three attempts at most. It checks the code with `TotpAlgorithm` before anything is written.
5. Makes all changes in one transaction:
   - status `active`, `deactivated_*` cleared;
   - the old password credential revoked (`admin_action`) and a new one stored (argon2id, `origin` `self-set`). The operator chose it, and the database allows `break-glass` only for an expiring temporary password, see the REQ-173 realignment in the proposal;
   - old TOTP credentials revoked (`factor_reset`) and the new one stored active with the accepted time step;
   - the account's sessions revoked;
   - the `auth_throttle` row of the login name deleted;
   - the audit event `system.admin-recovered` written.
6. Prints `OK` and exits 0. Exit codes as in the other commands: 0 done, 1 refused (nothing changed), 2 usage or connection error.

Neither the password nor the secret goes to the log. The secret appears only on the terminal.

- **Alternative – the command issues a recovery token for a web flow, as for setup:** this reuses the setup page but needs a new web flow, both for password and TOTP after the setup phase, and an unauthenticated endpoint that is open while a token exists.
- **Alternative – time-limited temporary password (REQ-173 wording):** it needs the password-change flow of UC-03, which does not exist. Its purpose is to limit an exposed credential, but the operator types the password into a terminal inside the container, so nothing is exposed.
- **Alternative – SQL procedure in the manual:** no audit and no consistency, which is the problem REQ-173 names.

### D6: Audit source CLI

A new source value `CLI` (`AuditEvent.Source.CLI`) is added to the audit schema (migration of the `audit` schema), the contract enums and the catalog. `system.admin-recovered` has:

- actor `system`, since the operator is not an OEA principal;
- target `system-admin-account`;
- severity `high`;
- details `reactivated`, `secondFactorReplaced`, `sessionsRevoked` (count);
- source address null.

`system-admin-account.deactivated` has the acting principal as actor, the account as target, outcome `success` or `denied`, and the detail `reason: last-admin-path` on denial.

- **Alternative – source `Job`:** this would misstate who acted; the catalog defines `Job` as scheduled work.

### D7: Operations on the local account

- `GET /admin/system-admin-account` returns `loginName`, `status`, `createdAt`, `firstLoginAt`, `lastUsedAt` and `deactivatedAt`.
- `POST /admin/system-admin-account/deactivate` returns the same object.
- Both need `auth-config:manage`, whose catalog description already names the remote system admin mapping.
- An already deactivated account is answered with 200 without a new event.
- The local admin may deactivate itself if a usable path remains. Its own session is revoked with the others, so its next request gets 401.

## Data Model

- `V8__admin_safeguards.sql` (schema `public`):
  - `idp_group_mappings.last_matched_at TIMESTAMPTZ NULL`;
  - `system_admin_accounts.deactivated_by_person_id BIGINT NULL REFERENCES persons (id)`, because a remote admin is a person; the existing `deactivated_by_admin_id` covers self-deactivation;
  - a check that at most one of the two is set.
- No change to `password_credentials`: the recovered password has `origin` `self-set`. The value `break-glass` stays reserved for an expiring temporary password (check `password_credentials_temporary_expiry`), and the audit event records the recovery.
- Audit migration `V3__cli_source.sql`: the check on `audit.events.source` is extended by `CLI`.
- `docs/data-model.puml`: `idp_group_mappings.last_matched_at`, `system_admin_accounts.deactivated_by_person_id`, audit source enum.
- `business-objects/idp-group-mapping.md`:
  - attribute `lastMatchedAt`;
  - BR-06 note that a path counts only after a match;
  - BR-08 for the warning.
- `business-objects/system-admin-account.md`: `deactivatedBy` may be a person; the lifecycle transition `deactivated → active` happens only through the break-glass command.

## API

Delta to `api/openapi.yaml`:

- **New `GET /admin/system-admin-account`** (`getSystemAdminAccount`):
  - permission `auth-config:manage`;
  - `200` `SystemAdminAccountState`;
  - `401`, `403`, `429`.
- **New `POST /admin/system-admin-account/deactivate`** (`deactivateSystemAdminAccount`):
  - permission `auth-config:manage`;
  - `200` `SystemAdminAccountState`;
  - `409` with problem type `urn:oea:problem:last-admin-path`;
  - `401`, `403`, `429`.
- **New schema `SystemAdminAccountState`.**
- **`IdpGroupMapping`:**
  - `lastMatchedAt` (date-time, nullable, read-only);
  - `warnings` (array of `{code: enum [group-not-seen], message}`), empty except in the responses of create and update.
- **Audit source enums** (`AuditEvent.source`, query parameter `source`): `CLI`.

## Risks / Trade-offs

- **A mapping that matched once may no longer have members**, for example after the group was emptied in the IdP. The deactivation is then allowed and the instance can be locked. → The match is the best evidence available without a provider API. The break-glass command is the documented way back, and the manual advises testing a login through the group just before deactivating.
- **Mapping deletion does not check SoD-7 yet (US-146):** an administrator can deactivate the local account and then delete the last mapping. → The break-glass command reactivates the local account. US-146 closes this in the next authorization change.
- **The TOTP secret appears in the terminal**, including scrollback and session recording. → The same holds for every TOTP enrollment screen. The manual says to clear the terminal, and the secret is never logged.
- **The CLI writes while the backend runs.** → All changes run in one transaction. Sessions are checked against the database on every request, so the revocation applies at once. Login throttling is read from the table, so clearing it applies at once.
- **Anyone with `exec` access to the backend container can take over the instance.** → This is equally true today through the database password and the KEK in the same container. The recovery makes this visible in the audit trail instead of adding a new capability. The manual names container access as the trust boundary.
