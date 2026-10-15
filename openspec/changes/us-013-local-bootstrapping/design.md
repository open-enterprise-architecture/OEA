# Design

## Context

Change 1 (`ws-01-project-setup`, archived) delivered the backend scaffold, the role model with the migration step, the audit tables without chain logic (`db/migration-audit/V1`), startup guards and the test infrastructure. No `persons`, sessions or Spring Security exist yet; the API has no authenticated operation. See proposal.md for motivation and scope.

Owner decisions of 2026-10-15 that shape the design: login of the system admin account (US-013 AC2) follows after change 3; the setup token is handed over as a file inside the container; the setup page is built without screen story (SCR-002 stays `legacy-mockup`).

## Goals / Non-Goals

**Goals:**

- A bootstrapping that is safe without authentication infrastructure: no public operation can do anything without the setup token, and the token never leaves the container except through a privileged channel
- An audit chain that later changes only append to: trigger, canonical serialization and verification are final for `hash_version = 1`
- The schema of the bootstrapping tables follows `docs/data-model.puml`, so that the login change only adds columns and tables

**Non-Goals:**

- Authentication of the system admin account, sessions, throttling (see proposal, Out of Scope)
- Signed checkpoints, scheduled in-application verification, SIEM export

## Architecture Constraints

- ADR-028: `api` (setup controller) → `app` (bootstrapping service, transaction boundary) → `core` (account, credentials, settings); `audit` and `config` as technical packages
- ADR-013: `api/openapi.yaml` first; the controller implements the generated interface
- ADR-032 §8 / L1: public operations only through the allowlist of `scripts/check_openapi_authorization.py`
- ADR-033 §3: pseudonymous actor fields, no names or e-mail addresses in the audit schema; §5: chain computed in the database by an owner-defined `SECURITY DEFINER` trigger; §6: checkpoints in the application log; §8: event catalog with stable names
- ADR-037: runtime role `oea_app` holds only INSERT/SELECT on the audit tables and nothing on `audit.chain_head`; the verification uses `oea_auditor`; audit migrations are additive
- ADR-001: instance slug set during bootstrapping, immutable afterwards
- REQ-169: argon2id with OWASP parameters (m = 19 MiB, t = 2, p = 1), length 12–128
- REQ-175 AC3: no setup token, password or hash in any log

## Decisions

### D1 – Setup token lifecycle

At startup (after the database checks, before the web server accepts connections) the backend checks whether a system admin account exists. If not, it generates a token of 32 random bytes (Base64url), stores its SHA-256 hash with `expires_at = now + 24 h` (`oea.bootstrap.setup-token-validity`) in the single row of `bootstrap_setup_tokens` (replacing a previous token), and writes the token to `oea.bootstrap.setup-token-file` (default `/run/oea/setup-token`, file mode `0600`, owner = OEA user). The log states: setup required, read the token with `docker compose exec backend cat /run/oea/setup-token`. A successful bootstrapping deletes the row and the file.

- A restart without system admin account issues a new token and invalidates the old one – this is the recovery path for a lost or expired token (UC-02 E1).
- With several backend replicas, the last start wins; the operator reads the token in the container that wrote it. Acceptable for the setup phase.
- *Alternative*: token in the log (rejected: REQ-175 AC3); token set by the operator as an environment secret (rejected by the owner: an additional mandatory secret).
- SHA-256 instead of argon2id for the token: the token has 256 bits of entropy, so a slow hash adds nothing.

### D2 – Exactly one successful bootstrapping

`POST /setup` runs in one transaction: lock the token row (`SELECT … FOR UPDATE`), verify hash and expiry (constant-time comparison), insert the single row of `system_settings` (`id = 1`, `instance_slug`), insert the system admin account and its credentials, delete the token row, append `system.bootstrapped`. A competing transaction waits for the row lock and then finds no token, or fails on the primary key of `system_settings`; both map to `409 already-bootstrapped`. The `system_settings` row is the durable "instance is bootstrapped" marker.

- *Alternative*: PostgreSQL advisory lock (rejected: the primary key already gives the guarantee, and it is visible in the data).

### D3 – Rejected attempts are audited in their own transaction

A rejected attempt (`invalid-setup-token`, `already-bootstrapped`, `validation`) rolls back the business transaction; the audit event `system.bootstrap-rejected` is written in a separate transaction (`REQUIRES_NEW`) so that the rejection is recorded. Validation errors are recorded with the failing field names only. Actor type `unauthenticated`, mode `local`, outcome `denied`, reason in `details`.

### D4 – Password hashing

argon2id through `spring-security-crypto` (`Argon2PasswordEncoder` with the OWASP parameters of REQ-169) and Bouncy Castle as its implementation. The PHC string is stored in `password_credentials.password_hash`; `origin = 'self-set'` and `expires_at = NULL`, because the operator chooses the password (REQ-173 applies to passwords set by someone else). Spring Security's web module is not added; it comes with change 3.

- *Alternative*: `argon2-jvm` (native libargon2; rejected: native library in the image).

### D5 – Audit writer

`io.oea.audit.AuditWriter` inserts into `audit.events` (and `audit.event_changes` when needed) with `JdbcTemplate` in the caller's transaction (READ COMMITTED, required by the chain trigger). It never sets chain columns. Fields of this change: `event_type`, `event_category`, `severity`, `outcome`, `source` (`API` for HTTP requests, `Job` for checkpoints), `source_ip` (remote address of the request; no proxy trust configuration yet), `request_id` (per-request UUID, also in the log MDC), actor fields, `target_type`/`target_id`, `details`.

### D6 – Chain trigger and canonical serialization

`db/migration-audit/V2__hash_chain.sql` creates `audit.append_to_chain()` (PL/pgSQL, `SECURITY DEFINER`, owner `oea_owner`, `SET search_path = audit, pg_catalog`), `BEFORE INSERT` triggers on the three chain tables, the genesis row of `audit.chain_head` (`last_seq = 0`, `last_hash` = 32 zero bytes) and the retention seed (2555 days, `effective_from = now()`) as the first link. The function rejects isolation levels other than READ COMMITTED, locks the head row, assigns `chain_seq`, `prev_hash`, `hash_version = 1` and computes `record_hash = sha256(…)` exactly as specified in ADR-033 §5. `REVOKE ALL ON FUNCTION … FROM PUBLIC`.

The ordered field list per table, the `table_tag` values (`events`, `event_changes`, `retention_settings`) and the text forms (timestamps `YYYY-MM-DDTHH:MM:SS.ffffffZ` in UTC, integers decimal, `INET` in PostgreSQL text form, `JSONB` in PostgreSQL's normalized text form, booleans `true`/`false`, UUIDs lower case) are specified in `docs/audit-hash-chain.md`. The specification is the contract for the trigger, the verification command and external verifiers.

### D7 – Verification command

`oea audit verify [--checkpoints=<file>]` is a mode of the same application, like `oea migrate` (non-web context, no auto-configuration). It connects with `oea.audit-verify.url`, `oea_auditor` and `oea.audit-verify.password` (checked by guard G4 in mode `audit-verify`), reads the three tables ordered by `chain_seq` with the canonical text forms selected in SQL, recomputes every hash in Java and checks the continuity of `chain_seq` and `prev_hash`. Output: `OK <n> links, head <seq> <hash>` (exit 0), or the first broken link with `chain_seq`, table, expected and actual hash (exit 1); configuration errors exit 2. With `--checkpoints`, it reads log lines containing `event=audit.checkpoint` and reports a head that is behind a checkpoint or a checkpoint whose hash differs from the stored link (exit 1). The Java implementation and the trigger are tested against each other.

### D8 – Checkpoints

A scheduled task (`oea.audit.checkpoint-interval`, default `PT60M`, validated 5–1440 minutes) and the orderly shutdown append `audit.checkpoint` (category `system`, actor `system`, source `Job`) and log one INFO line `event=audit.checkpoint chainSeq=<n> recordHash=<hex> at=<UTC timestamp>` with the values of the checkpoint record itself (read back with `RETURNING`). The shutdown checkpoint runs before the data source closes.

### D9 – Setup page in the frontend

`/setup` is a plain Nuxt UI form (token, login name, password with confirmation, instance slug) using the generated API types; the start page shows a hint with a link while `GET /setup/status` reports `setupRequired`. Field validation mirrors the API constraints; the API is authoritative. No screen story for SCR-002 in this change (owner decision); unit tests with Vitest cover the form logic.

### D10 – Container location of the token file

`backend/Dockerfile` creates `/run/oea` owned by the OEA user (mode `0700`). The Compose files need no change apart from documentation; `oea.bootstrap.setup-token-file` can be overridden for runs outside the container.

## Data Model

Changes to `docs/data-model.puml` and `business-objects/` (data model rule of AGENTS.md):

- `system_settings`: new attribute `instance_slug TEXT NOT NULL`, `CHECK (instance_slug ~ '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$')`, immutable after bootstrapping (ADR-001); the row is created by the bootstrapping. Business object `system-settings.md` gains `instanceSlug`.
- New table `bootstrap_setup_tokens` (`id SMALLINT PK CHECK (id = 1)`, `token_hash BYTEA NOT NULL` (32 bytes), `created_at`, `expires_at TIMESTAMPTZ NOT NULL`); documented as a note on `system_admin_accounts` and in `system-admin-account.md` (no own business object: a technical, short-lived artifact).
- `login_name` of `system_admin_accounts`: `CHECK (login_name ~ '^[a-z][a-z0-9._-]{2,63}$')` added to the model.

Planned Flyway migrations:

- `db/migration/V2__bootstrapping.sql` (owner `oea_owner`): `system_settings`, `system_admin_accounts`, `local_credentials`, `password_credentials`, `bootstrap_setup_tokens` as modeled. Columns that reference `persons` (`linked_person_id`, `deactivated_by_person_id`, `local_credentials.person_id`, `revoked_by_person_id`, `updated_by_person_id`) follow with the `persons` table (change 3); the owner check of `local_credentials` is then extended. Grants for `oea_app`: SELECT/INSERT/UPDATE on the account, credential and settings tables, SELECT/INSERT/UPDATE/DELETE on `bootstrap_setup_tokens`; no DELETE on accounts and credentials.
- `db/migration-audit/V2__hash_chain.sql` (owner `oea_owner`): see D6; additive.

## API

Delta to `api/openapi.yaml` (tag `Setup`, both operations `x-oea-authorization: { access: public }` and in the public allowlist):

- `GET /setup/status` → `200 { "setupRequired": boolean }`, `429`
- `POST /setup` with `SetupRequest { setupToken, loginName, password, instanceSlug }` (`password` with `minLength: 12`, `maxLength: 128`, `format: password`; patterns for `loginName` and `instanceSlug`) → `201 SetupResult { loginName, instanceSlug }`; `400` problem `urn:oea:problem:validation-failed`; `401` problem `urn:oea:problem:invalid-setup-token`; `409` problem `urn:oea:problem:already-bootstrapped`; `429`
- Audit enum values: `system.bootstrapped`, `system.bootstrap-rejected`, `audit.checkpoint` documented in `docs/audit-event-catalog.md`; no API exposes audit events yet.

## Risks / Trade-offs

- [Chain trigger and Java verifier disagree on a text form] → shared specification, tests that tamper with every field type and compare trigger hashes with the verifier; `hash_version` allows a corrected serialization later
- [The head row lock serializes all audit inserts] → expected volume is low (ADR-033 §5); audit inserts stay at the end of transactions
- [Public setup endpoint before authentication exists] → it does nothing without the 256-bit token; the token is invalidated after use; throttling follows with REQ-168
- [Token file readable through `docker compose exec` by everybody with container access] → that is the intended privileged channel (REQ-017); documented in `docs/operations.md`
- [Login of the bootstrapped account is not possible until a later change] → the walking skeleton logs in through the IdP in change 3 anyway; documented in the walking-skeleton document

## Open Questions

- Log format of the checkpoint line once structured logging (JSON) is enabled for production – the verifier accepts the key/value form and can be extended without changing the specs
