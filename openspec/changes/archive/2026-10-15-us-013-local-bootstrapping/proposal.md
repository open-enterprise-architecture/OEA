# Proposal

## Why

A freshly installed OEA instance has no person, no role and possibly no identity provider, so nobody can administer it (UC-02, chicken-and-egg problem). This second walking-skeleton change lets the operator create the local system admin account and the instance slug, and it brings the audit hash chain that every later audited action relies on: the bootstrapping is the first audited action of an instance.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-02: System Admin Bootstrapping](../../../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) |
| User Stories | [US-013: Local bootstrapping](../../../../requirements/user-stories/US-013-local-bootstrapping.md) (AC1; AC2 follows, see Out of Scope), [US-015: No parallel bootstrapping](../../../../requirements/user-stories/US-015-no-parallel-bootstrapping.md), [US-016: Audit log of bootstrapping](../../../../requirements/user-stories/US-016-audit-log-bootstrapping.md) (local mode), [US-017: Secure setup token handover](../../../../requirements/user-stories/US-017-secure-setup-token-handover.md) |
| Requirements | [REQ-013](../../../../requirements/req/REQ-013-local-bootstrapping.md), [REQ-015](../../../../requirements/req/REQ-015-no-parallel-bootstrapping.md), [REQ-016](../../../../requirements/req/REQ-016-audit-log-bootstrapping.md), [REQ-017](../../../../requirements/req/REQ-017-secure-setup-token-handover.md), [REQ-169](../../../../requirements/req/REQ-169-password-storage-quality.md) (password storage and length), [REQ-174](../../../../requirements/req/REQ-174-audit-tamper-resistance.md) (AC3, AC5), [REQ-175](../../../../requirements/req/REQ-175-security-event-logging.md) (AC3: no secrets in logs), [REQ-083](../../../../requirements/req/REQ-083-audit-log-retention.md) (retention default) |
| ADRs | [ADR-001](../../../../adrs/ADR-001-urn-schema.md) (instance slug), [ADR-006](../../../../adrs/ADR-006-auth-stack-choice.md), [ADR-024](../../../../adrs/ADR-024-audit-separation.md), [ADR-032](../../../../adrs/ADR-032-authorization-model.md), [ADR-033](../../../../adrs/ADR-033-audit-trail-hardening.md) (§3, §5, §6, §8), [ADR-037](../../../../adrs/ADR-037-database-roles-provisioning.md) |
| Sprint / Milestone | S1 / Walking skeleton 1/2 ([Sprint plan](../../../../docs/sprint-plan.md)) |

Walking-skeleton change 2 of 6 ([docs/walking-skeleton.md](../../../../docs/walking-skeleton.md)). US-015 to US-017 are not listed in the walking skeleton but are needed for a safe local bootstrapping; their local-mode acceptance criteria are covered here.

## What Changes

- **Setup token** (REQ-017, owner decision 2026-10-15): when the backend starts and no system admin account exists, it generates a one-time setup token (256 bit), stores only its hash with an expiry and writes the token to a file inside the container that only the OEA user can read. The application log contains only the hint how to read the file, never the token (REQ-175 AC3).
- **Setup API and page**: public operations `GET /setup/status` (setup required yes/no) and `POST /setup` (setup token, login name, password, instance slug) in `api/openapi.yaml`; a minimal setup page in the frontend that the start page links to while setup is required.
- **System admin account**: `POST /setup` creates the local system admin account (`status = bootstrapped`) with an argon2id password credential (REQ-169: length 12–128, OWASP parameters) and stores the instance slug (ADR-001, immutable afterwards). Exactly one setup can succeed, also under concurrency; every further attempt is rejected (REQ-015).
- **Audit hash chain** (ADR-033 §5): `BEFORE INSERT` trigger (owner `oea_owner`, `SECURITY DEFINER`) over `audit.events`, `audit.event_changes` and `audit.retention_settings` with the genesis row of `audit.chain_head`; canonical serialization and field lists in the new specification `docs/audit-hash-chain.md`.
- **Retention seed** (ADR-033 §4, decision for ws-01 task 2.3): the first chain member is the seed row of `audit.retention_settings` with 2555 days.
- **Audit writer** (ADR-033 §3, §8): application component that appends audit events with pseudonymous actor fields; first event types `system.bootstrapped`, `system.bootstrap-rejected` and `audit.checkpoint`, documented in the new event catalog `docs/audit-event-catalog.md`.
- **Verification and checkpoints** (REQ-174 AC3, AC5; ADR-033 §5, §6): command `oea audit verify` (same image, `oea_auditor` credentials, optional exported checkpoints as input) reporting the first broken link or a head behind/diverging from a checkpoint; periodic checkpoints (default 60 minutes, and at orderly shutdown) appended as `audit.checkpoint` and emitted as structured application log entries.
- **Data model**: `system_admin_accounts`, `local_credentials`, `password_credentials` (subset of the modeled tables), `system_settings` with the new attribute `instance_slug`, new table `bootstrap_setup_tokens`; `docs/data-model.puml` and `business-objects/` updated accordingly.

## Out of Scope

- **US-013 AC2 – login of the system admin account**: local login with password, forced TOTP enrollment at the first login (REQ-171), expiry rules of REQ-173, server-side sessions (REQ-164, REQ-165) and throttling (REQ-168) follow in a separate change after change 3, which builds the session infrastructure for the OIDC login (owner decision 2026-10-15). US-013 is therefore only partially done after this change.
- Remote bootstrapping via IdP group mapping (REQ-014, US-014) and the OIDC configuration that `docs/walking-skeleton.md` mentions in step 3 – change 3 (US-001) configures the IdP through properties
- **Screen story and generated images for SCR-002** (ADR-031): the setup page is built as a plain page; SCR-002 stays `legacy-mockup` until change 6 introduces the screenshot workflow (owner decision 2026-10-15)
- Scheduled in-application verification, `GET /audit/integrity`, the SIEM export and signed checkpoints (ADR-033 §5, §6, §9) – they need authenticated `audit:read` principals
- Retention changes through the settings API and the retention trigger event (REQ-174 AC2), `audit.purge_expired()` and the retention job
- Recovery for a lost setup token beyond restarting the backend (a restart without system admin account generates a new token); break-glass procedure (REQ-173, UC-02 E4)
- Breached-password list (REQ-169, SHOULD)

## Capabilities

### New Capabilities

- `identity/bootstrapping`: detection of a fresh instance, setup token handover, creation of the local system admin account and the instance slug, rejection of repeated or parallel bootstrapping, and the audit records of bootstrapping
- `audit/hash-chain`: tamper evidence of the audit trail – chain over all audit tables computed in the database, verification command and checkpoints outside the database
- `audit/retention`: audit retention settings – seeded default of 2555 days and the floor of 365 days

### Modified Capabilities

- none

## Impact

- `backend/`: `io.oea.api` (setup controller), `io.oea.app` (bootstrapping service), `io.oea.core` (system admin account, credentials, settings), `io.oea.audit` (audit writer, verification, checkpoints), `io.oea.config` (setup token at startup, `oea audit verify` mode); new dependency for argon2id hashing in `backend/pom.xml`
- Flyway: `db/migration/V2__…` (bootstrapping tables), `db/migration-audit/V2__…` (chain trigger, chain head, retention seed)
- `api/openapi.yaml`: `GET /setup/status`, `POST /setup`, schemas and problem types; public allowlist of `scripts/check_openapi_authorization.py`
- `frontend/`: setup page and start-page hint
- `compose.yaml`, `compose.dev.yaml`, `backend/Dockerfile`: writable location for the setup token file; command `oea audit verify` documented in `docs/operations.md`
- Docs: new `docs/audit-hash-chain.md`, `docs/audit-event-catalog.md`; `docs/data-model.puml`, `business-objects/system-admin-account.md`, `business-objects/system-settings.md`, `business-objects/local-credential.md` and `docs/walking-skeleton.md` (step 3) updated
- `docs/screens/SCREENS.md`: unchanged (SCR-002 stays `legacy-mockup`)
