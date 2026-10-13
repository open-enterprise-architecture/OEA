# ADR-033: Audit Trail Hardening – Owner/Runtime Role Separation, Hash Chain in All Editions, Retention Floor and Pseudonymous Actors

**Status**: accepted
**Date**: 2026-10-13
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Security Engineer (security review 2026-10, SEC-13/SEC-14/SEC-28), Requirements Engineer (REQ-083, REQ-151, REQ-174, REQ-175, REQ-189)
**Informed**: Business Engineer (data model follow-up), Backend Engineer, Frontend Engineer
**Partially supersedes**: [ADR-024](./ADR-024-audit-separation.md) (database permissions, actor fields, optional Enterprise-only tamper chain, retention mechanics, app write access to `entity_property_value_history`), [ADR-026](./ADR-026-external-system-integration.md) (§7: source of `agent_id`), [ADR-015](./ADR-015-db-migration.md) (migrations executed by the application at start) – see [Relation to Existing ADRs](#relation-to-existing-adrs)

## Context and Problem

[ADR-024](./ADR-024-audit-separation.md) separated the audit trail into the schema `audit` with normalized tables and INSERT-only grants for the application. The security review of the requirements baseline (`security-reviews/requirements-baseline-2026-10.md`) rated **SEC-13 "Audit trail is not tamper-resistant against the application and its admins"** as High and found three further gaps:

- **Ownership defeats the grants.** Flyway runs inside the application with the single datasource (ADR-015, ADR-027). If it connects as `oea_app`, that role owns the audit tables, and `REVOKE UPDATE, DELETE` has no effect for an owner. An SQL injection or remote code execution in the application could rewrite or truncate `audit.events`.
- **Retention without a floor.** `SystemSettings.auditRetentionDays` is writable through `PUT /admin/settings`; the cleanup job deletes everything older than that value. An administrator (or an attacker with an admin session) sets it to 1 day and the nightly job erases the trail of their own actions.
- **No tamper evidence in the Community edition.** The hash chain is optional and Enterprise-only; "denormalization for tamper evidence" provides completeness, not evidence.
- **Client-controlled agent identity.** ADR-026 §7 takes `agent_id` from the `X-Agent-Id` header or a JWT claim, so a service account can disguise its writes as another system.
- **SEC-14**: security events (403, role changes, settings changes, session revocation, throttling) are not recorded systematically, and there is no SIEM export.
- **SEC-28**: `actor_name` and `actor_email` are mandatory, immutable plaintext columns retained for 7 years; a data subject request (GDPR Art. 15/17) cannot be fulfilled, and `/portal/audit/events` returns e-mail addresses to every portal reader. Old and new values of restricted properties are stored in plaintext in `audit.event_changes` (SEC-02).

The requirements engineer has aligned the requirements: REQ-083 (retention, append-only), REQ-151 (owner role ≠ runtime role, INSERT and SELECT only), REQ-174 (tamper resistance), REQ-175 (security event logging), REQ-189 (pseudonymous actors) and REQ-160 AC4 (restricted values protected in the audit change table). They reference "a new ADR"; accepted ADRs are not rewritten. The security review makes owner/runtime role separation in the **first Flyway migration** a sign-off condition for the walking skeleton.

Binding owner decisions of 2026-10-13:

| # | Decision | Effect on this ADR |
|---|---|---|
| Q3 | Audit records cannot be deleted or changed by the application or by administrators; deletion only through the retention policy; tamper evidence in the Community edition | No delete or modify function in the application; runtime role without UPDATE/DELETE/TRUNCATE/ALTER; hash chain mandatory in all editions |
| Q10 | Retention configurable by the operator with a floor of 1 year; default 7 years | Floor enforced in the database, not only in the API |
| Q5 | Machine access only via service accounts with OAuth2 client credentials | `agent_id` = authenticated `client_id` |
| Q2 | The web portal must be safe to expose to the internet | Audit flooding by unauthenticated or low-privileged callers must be bounded |
| Q20 | Ended sessions: tokens deleted immediately, record kept 30 days | Login and logout events stay in the audit log; session rows do not |

[ADR-032](./ADR-032-authorization-model.md) defines the permission `audit:read` (built-in role `auditor`, assignable to service accounts for SIEM pulls) and `settings:manage` (system admin, "retention within its floor"). The local system admin account is not a person (ADR-032 §1).

## Decision Drivers

- **Tamper resistance against the application and its administrators (Q3)**: neither a compromised application process nor any application permission may change or delete existing audit records.
- **Tamper evidence beyond prevention**: a database superuser or host administrator cannot be prevented from changing rows; such changes, deletions in the middle, truncation of the tail and the restore of an old backup must at least be **detectable** by an auditor.
- **Regulatory evidence**: ISO/IEC 27001 Annex A 8.15/8.16 and DORA Art. 9/12 require protected, complete logs with documented controls; the grant concept, ownership and verification output must be demonstrable (REQ-151, REQ-174).
- **GDPR data minimization (SEC-28, REQ-189)**: the append-only store must not contain data that later has to be erased (names, e-mail addresses), and restricted property values must not be readable by everyone with audit access (REQ-160).
- **Operability for a single-maintainer project and SME operators (SH-06)**: no mandatory additional runtime component (no SIEM, WORM store, timestamping authority or PostgreSQL extension); `docker compose up` keeps working; misconfiguration must fail closed rather than silently degrade.
- **Walking skeleton first**: the role model must be part of the first migration; anything expensive to retrofit (ownership, chain fields, actor columns) is decided now.
- **Verifiability by third parties**: an auditor must be able to verify an export without access to OEA secrets.
- **Performance**: audit writes run in the same transaction as entity changes (REQ-071 catalog latency, REQ-154 batch writes of 500 items).

## Considered Options

### Option (a): PostgreSQL privileges only

A dedicated owner role (`oea_owner`) owns all schemas and objects and is used only for migrations. The runtime role `oea_app` holds INSERT and SELECT on the audit tables and nothing else; ALTER, DROP and TRUNCATE are impossible because it is not the owner. Retention runs through a function owned by `oea_owner`.

- **Pro**:
  - Prevents modification and deletion by the application and by every application-level administrator, including after an SQL injection or code execution in the application process
  - No additional component, no performance cost
  - Standard PostgreSQL mechanism; easy to inspect (`pg_tables.tableowner`, `has_table_privilege`) and to test with Testcontainers (REQ-151 AC4)
- **Con**:
  - No evidence at all against a database superuser, a host administrator or a restored old backup – a silent change is indistinguishable from the original
  - Correctness depends on deployment configuration (role setup, credentials); a misconfigured installation silently loses all protection
  - Does not satisfy owner decision Q3 ("tamper evidence in the Community edition")

### Option (b): (a) + hash chain in the Community edition

In addition to (a), every audit record carries `prev_hash` and `record_hash` over a canonical serialization of its content and the hash of its predecessor. A verification command recomputes the chain.

- **Pro**:
  - Any modification or deletion of a record in the middle of the chain is detected, regardless of who made it
  - Verifiable by third parties from an export without secrets (unkeyed SHA-256)
  - Cheap: PostgreSQL ≥ 11 has a built-in `sha256()` function, no extension needed; tiny storage overhead
  - Was already designed in ADR-024 (columns exist), only not populated in Community
- **Con**:
  - A database superuser can recompute the entire chain after a change; truncating the newest records or restoring an old backup leaves a valid, shorter chain – the chain alone does not detect it
  - Appends must be serialized to obtain a total order; this limits audit write concurrency
  - A canonical serialization must be specified and kept stable across releases

### Option (c): (a) + external WORM / append-only store or SIEM as the primary store

Audit records are written primarily to an external store with write-once semantics (S3 Object Lock, immutable cloud log service, SIEM such as Splunk or Elastic); the database holds at most a copy.

- **Pro**:
  - Strongest protection: even a full compromise of the OEA host cannot change records already stored externally
  - Retention and legal hold handled by a specialized system
  - Many regulated organizations already operate a SIEM
- **Con**:
  - Mandatory additional infrastructure for every installation; many SMEs (SH-06) operate neither a SIEM nor object storage with object lock
  - The audit trail becomes unavailable for UC-14 queries and REQ-083 query latency when the external system is slow or down; writes need an outbox and retry logic
  - Vendor-specific integrations (ADR-024 rejected external audit systems for lock-in reasons); a single maintainer cannot test several sinks
  - Restricted values and actor identities leave the instance and must be protected in a foreign system

### Option (d): Signed periodic checkpoints exported outside the database

At regular intervals the system computes a digest over all records since the last checkpoint (e.g. a Merkle root) and exports it, optionally signed, to a location outside the database.

- **Pro**:
  - Detects recomputation, tail truncation and backup rollback as long as the exported checkpoint is outside the attacker's control
  - Small amount of data; can be sent to any existing channel (log pipeline, SIEM, e-mail, file)
  - Signing with a key outside the database proves the checkpoint's origin
- **Con**:
  - Alone it only covers records up to the last checkpoint and needs its own tree computation; without a chain, locating the first broken record requires the original data
  - Signing key management (generation, storage outside the database, rotation, publication of the public key)
  - The value depends on the export target really being outside the attacker's reach – an operator who stores checkpoints on the same host gains little

## Decision

We choose **Option (b) – privileges and owner separation plus a mandatory hash chain in all editions – complemented by the checkpoint anchoring of Option (d) in a lightweight form**: checkpoints are links of the same hash chain, emitted to the application log and the SIEM export, and signed only if the operator configures a key. External WORM storage and SIEM (Option (c)) are supported only as **secondary** consumers of a pull export, never as the primary store.

Rationale: (a) alone does not satisfy Q3; (c) as the primary store imposes infrastructure that SME operators and a single maintainer cannot carry. The chain of (b) makes every change in the middle of the trail detectable at negligible cost, and the checkpoints of (d) close the chain's blind spots (recomputation, tail truncation, backup rollback) by placing the chain head outside the database – without a mandatory component, because the application log already leaves the database. Every mechanism below is part of the Community edition.

### Threat coverage

| Threat | Mechanism | Result |
|---|---|---|
| Administrator deletes or edits records through UI/API | No such operation exists (§8); only `audit:read` operations | Prevented |
| Administrator lowers retention to purge recent traces | Floor 365 days enforced in the database; reductions take effect after 30 days; every change audited by a database trigger (§4) | Prevented below 1 year; visible 30 days before any effect |
| SQL injection / code execution in the application (`oea_app`) | INSERT and SELECT only; not owner; chain fields computed by an owner-defined trigger (§1, §5) | Modification and deletion prevented; forged **additional** records remain possible and are bounded to the compromise window |
| Theft of retention job credentials | The role can only execute the purge function, which deletes nothing inside the effective retention (§4) | No additional power |
| Database superuser or host administrator edits or deletes rows | Hash chain, daily verification (§5) | Detected, first broken record reported |
| Superuser recomputes the chain, truncates the tail or restores an old backup | Checkpoints outside the database (§6) | Detected up to the last exported checkpoint |
| Service account disguises its writes as another system | `agent_id` from the authenticated `client_id` only (§7) | Prevented |
| Audit flooding (403 storms, login sprays) filling the non-deletable store | Suppression of repeated events (§9), rate limits (REQ-186) | Bounded |

**Non-goal**: preventing a database superuser or host administrator from destroying the database (availability). Backups (REQ-082) remain the operator's responsibility; this ADR guarantees detection, not prevention, at that level.

### 1. Database roles

All roles are created once by a provisioning step with database superuser rights – automatically by the PostgreSQL init script in the shipped Docker Compose setup, and by a documented SQL script for operator-managed PostgreSQL. No OEA role is `SUPERUSER`, `CREATEROLE`, `CREATEDB` or `BYPASSRLS`, and no OEA role is a member of another OEA role. Role names are configurable (Flyway placeholders); the names below are defaults.

| Role | Used by | Rights |
|---|---|---|
| `oea_owner` | Migration step only (§2) | Owns the schemas `public`, `audit` and the reporting schema of REQ-179 and every object in them; `CREATE` on the database. Its credentials are never given to the running application |
| `oea_app` | Application runtime | `audit.events`, `audit.event_changes`, `audit.retention_settings`: **INSERT, SELECT** only; `USAGE` on their sequences; `SELECT` on `audit.flyway_schema_history`. No UPDATE, DELETE, TRUNCATE, REFERENCES or TRIGGER; ALTER and DROP impossible (not owner). No rights on `audit.chain_head`. Table-specific DML on `public` (see §10) |
| `oea_audit_cleanup` | Retention job (§4) | `USAGE` on schema `audit`; `EXECUTE` on `audit.purge_expired()` only; no table privileges |
| `oea_auditor` | Verification command (§5); optional direct read access for external auditors | `SELECT` on all tables in `audit` |
| `oea_reporting` | Raw analytics (REQ-179) | No privileges on `audit` at all |

Grants are explicit per object in the audit migrations (no `ALTER DEFAULT PRIVILEGES` on `audit`). `REVOKE ALL ON SCHEMA audit FROM PUBLIC` and `REVOKE ALL ON FUNCTION … FROM PUBLIC` are part of the first audit migration (functions are executable by `PUBLIC` by default).

Sketch of the first audit migration (executed as `oea_owner`):

```sql
CREATE SCHEMA audit AUTHORIZATION oea_owner;
REVOKE ALL ON SCHEMA audit FROM PUBLIC;
GRANT USAGE ON SCHEMA audit TO ${runtimeRole}, ${cleanupRole}, ${auditorRole};

-- tables, chain trigger, purge function (owned by oea_owner) ...

GRANT INSERT, SELECT ON audit.events, audit.event_changes, audit.retention_settings TO ${runtimeRole};
GRANT USAGE ON ALL SEQUENCES IN SCHEMA audit TO ${runtimeRole};
GRANT SELECT ON ALL TABLES IN SCHEMA audit TO ${auditorRole};
REVOKE ALL ON FUNCTION audit.purge_expired() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.purge_expired() TO ${cleanupRole};
```

**Fail closed at startup**: the application checks its own role at startup (`has_table_privilege`, `pg_tables.tableowner`, `pg_roles`) and refuses to start outside the explicit local development profile if the runtime role owns any object in `audit`, holds UPDATE, DELETE, TRUNCATE, REFERENCES or TRIGGER on an audit table, is a superuser or has `BYPASSRLS`, or if migrations are pending. This turns a misconfigured installation into a visible startup error instead of a silent loss of protection (cf. REQ-183).

### 2. Migrations run as the owner role in a separate step

Flyway no longer runs inside the application process at startup. Migrations run in a **one-shot migration step** of the same container image (`oea migrate`, a Spring Boot mode without web server that exits after migrating) with the credentials of `oea_owner`:

- **Two Flyway executions** in that step: `db/migration` for the application schemas (history table `public.flyway_schema_history`) and `db/migration-audit` for the audit schema (history table `audit.flyway_schema_history`), so that the audit schema can be migrated against the external audit database of ADR-024 with its own owner credentials (`OEA_AUDIT_DB_OWNER_USER`/`…_PASS`). If no external audit database is configured, both run against the same database.
- **Docker Compose**: a `migrate` service runs before `api` (`depends_on: condition: service_completed_successfully`); `docker compose up` remains a single command (REQ-075). Only the `migrate` service receives owner credentials; `api` receives the credentials of `oea_app` and `oea_audit_cleanup`. Kubernetes deployments use an init container or a Job.
- **The application** runs with `spring.flyway.enabled=false` for migrating and only validates that the schema is current (startup check of §1).
- **Migration rule**: no migration may contain `UPDATE`, `DELETE` or `TRUNCATE` on tables in `audit`, or drop audit tables or columns; structural changes to the audit schema are additive. A CI check scans `db/migration-audit` for these statements; exceptions need an ADR.

### 3. Audit records and personal data

**Actors are stored pseudonymously.** The audit schema contains no names and no e-mail addresses of OEA users:

- `actor_type` (`person | service-account | system-admin-account | system | unauthenticated`) plus the surrogate key of the principal as a soft reference: `actor_person_id` (persons, including service accounts) or `actor_admin_id` (local system admin account), following the actor-pair convention of `docs/data-model.puml`. Both are NULL for `system` and `unauthenticated`.
- The **protected identity mapping** is the set of identity tables (`persons`, `system_admin_accounts`) in the application schema. Names and e-mail addresses are resolved at read time and only for principals holding `audit:read` (REQ-189 AC3). When a person is anonymized (REQ-189), the identity tables lose name and e-mail; the audit records stay unchanged and keep linking to the now pseudonymous key. No append-only record has to be modified for erasure.
- Events without an authenticated principal (failed login with an unknown identifier, throttling) store the attempted login identifier only as a keyed HMAC-SHA-256 (key outside the database, REQ-188). This correlates password sprays without storing typos that may contain passwords and without an enumeration aid.
- `entity_name` stays denormalized for completeness, except for entities of metatype `person` (modeled persons), for which it is NULL; `entity_id` suffices.
- Free-text fields (`reason`, comments) may contain names typed by users; they are kept like any other record content and are covered by the record of processing in the operator documentation.

**Restricted property values are encrypted.** When a change of a property whose `visibilityMode` is not `public` at the time of the event is recorded, `old_value` and `new_value` are stored as AES-256-GCM ciphertext (`value_protection = encrypted`, `key_id` of the audit value key) instead of plaintext (REQ-160 AC4). The audit value key is a data key wrapped by the key-encryption key of REQ-188 (outside the database, never in backups). The application decrypts a value only for a caller holding `audit:read` **and** read permission on the property under the current visibility configuration; otherwise the change is returned with `redacted: true`. Keys are rotated by introducing a new `key_id` for new records; old keys stay in the keyring as long as records encrypted with them exist (append-only records are never re-encrypted). Deliberately destroying an old key renders the affected values unreadable (crypto-shredding) without touching the chain. A keyed MAC was rejected because auditors with the right permissions must be able to see the values.

`property_def_id` is stored with every change (soft reference) so that visibility is evaluated on the definition, not on the denormalized `property_name`.

### 4. Retention

- **Setting**: the retention period is an append-only series of rows in `audit.retention_settings` (`retention_days`, `recorded_at`, `effective_from`, actor). Only principals with `settings:manage` can add a row, through a dedicated operation; `SystemSettings.auditRetentionDays` is removed from the generic settings. The initial row is written by the first audit migration with **2555 days (7 years)**; `OEA_AUDIT_RETENTION_DAYS` is only the initial value on first installation.
- **Floor in the database**: `CHECK (retention_days >= 365)` on the table, and `audit.purge_expired()` additionally computes `GREATEST(retention_days, 365)` with the floor hard-coded in the function body. The floor is not configurable (Q10).
- **Reductions take effect after 30 days**: `effective_from = recorded_at + 30 days` when the new value is lower than the one in force, `= recorded_at` when it is higher or equal. A reduction is therefore visible to auditors, in the SIEM export and in the access review a month before any record is deleted because of it.
- **Changes are audited by the database**: an owner-defined trigger on `audit.retention_settings` appends a `settings.audit-retention-changed` event (old value, new value, effective date, actor) in the same transaction. Even a compromised application cannot change the retention without the event (REQ-083 AC5, REQ-174 AC2).
- **Purge job**: a scheduled job (nightly by default) in the application connects with the credentials of `oea_audit_cleanup` and calls `audit.purge_expired()`. The function is `SECURITY DEFINER`, owned by `oea_owner`, with a fixed `search_path`. It deletes the oldest contiguous range of chain links whose `occurred_at` is older than the effective retention, in bounded batches, and appends an `audit.retention-purged` event with the first and last deleted `chain_seq`, the count, the effective retention and the `record_hash` of the last deleted link (new verification anchor, §5). It cannot delete anything inside the retention period – a stolen cleanup credential adds no power.
- **No other deletion path**: there is no API or UI operation that deletes or changes audit records, for any permission (REQ-083 AC6). Legal hold (suspending the purge) is a follow-up decision.

### 5. Hash chain

- **Mandatory in all editions**; the configuration flag `oea.audit.hash-chain-enabled` of ADR-024 is removed.
- **Chain members**: every row of `audit.events`, `audit.event_changes` and `audit.retention_settings` is a link of **one** chain, ordered by `chain_seq`.
- **Fields per link**: `chain_seq BIGINT NOT NULL UNIQUE`, `hash_version SMALLINT NOT NULL`, `prev_hash BYTEA NOT NULL` (32 bytes), `record_hash BYTEA NOT NULL` (32 bytes). `id` (BIGSERIAL) stays the technical key; `chain_seq` is the order of the chain.
- **Computed in the database**: a `BEFORE INSERT` trigger owned by `oea_owner` (`SECURITY DEFINER`) locks the single row of `audit.chain_head` (`last_seq`, `last_hash`), assigns `chain_seq = last_seq + 1`, sets `prev_hash = last_hash`, computes `record_hash` and updates the head. Values supplied by the application for these columns are overwritten. The row lock serializes appends; audit inserts run in READ COMMITTED transactions (the trigger rejects other isolation levels).
- **Hash input** (`hash_version = 1`):

  ```text
  record_hash = SHA-256( hash_version || chain_seq || prev_hash || table_tag || field_1 || … || field_n )
  field_i     = 4-byte big-endian length || value bytes   (NULL: length 0xFFFFFFFF, no bytes)
  values      = UTF-8 text; timestamps as UTC ISO-8601 with microseconds; integers as decimal text;
                BYTEA (ciphertext) as raw bytes; JSONB as its canonical text form
  genesis     = prev_hash of chain_seq 1 is 32 zero bytes
  ```

  The ordered field list per table and `table_tag` are part of the specification document `docs/audit-hash-chain.md` (follow-up) and change only with a new `hash_version`. The chain uses plain SHA-256 so that anyone can verify an export without OEA secrets; encrypted values are hashed as ciphertext.
- **Verification**:
  - Command `oea audit verify` (same image, read-only `oea_auditor` credentials): walks the chain from the latest purge anchor (or genesis), recomputes every hash, checks continuity of `chain_seq` and `prev_hash`, and reports the **first broken link** with its `chain_seq`, table and expected/actual hash (REQ-174 AC3). Optionally it takes exported checkpoints (§6) as input and reports a database head that is behind or diverges from them.
  - A scheduled verification in the application (daily, incremental from the last verified checkpoint) appends `audit.integrity-verified` or `audit.integrity-violation` (severity critical, ERROR log entry, banner for principals with `audit:read` and for system administrators).
  - `GET /audit/integrity` (`audit:read`) returns the last verification result and the latest checkpoint.
- **Performance**: the head lock is held until commit. Large transactions (batch writes, REQ-154) collect their audit records and insert them immediately before commit, so that the lock is held only briefly. The expected audit volume of an EA repository (hundreds of events per minute at peak) is far below the serialization limit.

### 6. Checkpoints outside the database

- Every 60 minutes (configurable 5–1440), after each retention purge and at orderly shutdown, the application appends an `audit.checkpoint` event and emits its `chain_seq`, `record_hash` and timestamp **outside the database**:
  1. as a structured application log entry (`event=audit.checkpoint`) – the log stream (container log, journald, log shipper) is outside the database and usually outside the database administrator's reach;
  2. as part of the SIEM export (§9), so that every consumer of the export holds the chain heads.
- **Optional signature**: if the operator configures an Ed25519 signing key (file or environment, handled like the keys of REQ-188), each checkpoint carries a signature; the verification command checks it against the configured public key. Default: unsigned – the log pipeline and the export are the anchors.
- Detection extends to the latest checkpoint that reached a place outside the attacker's control; the operator documentation explains that checkpoints stored only on the database host add little.
- External anchoring services (RFC 3161 timestamping, object storage with object lock) are not part of this decision (follow-up, Enterprise candidate).

### 7. Source of `agent_id`

- `agent_id` is set **only from the authenticated principal**: for service accounts it is the `client_id` of the client credentials token (REQ-176, stable across rotations); for human principals and the local system admin account it is NULL.
- The `X-Agent-Id` request header is stored, if present, only in the informational column `agent_label` (untrusted, truncated to 100 characters, control characters removed); it is never used for filtering decisions, authorization or attribution. JWT claims other than the client identity are ignored (ADR-032 §7: tokens of service accounts carry only `client_id`).
- `agent_run` (correlation ID of a run, `X-Agent-Run`) stays informational as in ADR-026.

### 8. Security event logging

All security events of REQ-175 are records of the same audit trail and the same chain; there is no separate security log.

- **Common fields** (columns, queryable and indexed): `occurred_at`, `event_type`, `event_category` (`change | security | access | system`), `severity` (`info | warning | high | critical`), `outcome` (`success | failure | denied`), actor fields (§3), `agent_id`, `source` (`UI | API | Job | Import`), `source_ip` (INET), `request_id` (correlation ID), `target_type` and `target_id` (entity, person, role, assignment, setting, service account, …). Event-type-specific supplementary fields (e.g. role, validity, `sodConflict`, `selfBenefiting`) go into `details JSONB`; fields needed for regulatory queries are columns (ADR-024 principle "no payload-only records" kept, ADR-030 allows JSONB for the supplement).
- **Event catalog**: event types have stable names (`auth.login`, `auth.logout`, `session.revoked`, `authz.denied`, `auth.throttled`, `role.assigned`, `role.revoked`, `idp-mapping.changed`, `auth-provider.changed`, `settings.changed`, `settings.audit-retention-changed`, `visibility.changed`, `visibility.override-changed`, `service-account.created|secret-rotated|deactivated`, `analytics.query`, `audit.exported`, `audit.checkpoint`, `audit.retention-purged`, `audit.integrity-verified|violation`, `entity.*`, `metamodel.*`, …). The catalog is maintained in `docs/audit-event-catalog.md` (follow-up) and referenced by the requirements.
- **There is no API or UI operation that modifies or deletes audit records.** The only audit operations are reading, exporting and verifying, all requiring `audit:read`. `/portal/audit/events` is removed or requires `audit:read` (ADR-032 API-8).
- **Bounded volume**: because records cannot be deleted before the retention period, repeated `authz.denied`, `auth.throttled` and failed-login events of the same principal (or source address) and operation are recorded individually up to 20 per minute; beyond that, one summary event per minute carries the count. Rate limits (REQ-186) apply in addition.
- **Application logs** (as opposed to the audit trail) never contain secrets, tokens, passwords or TOTP codes (REQ-175 AC3); they carry the correlation ID that links them to audit records.

### 9. SIEM export

- `GET /audit/events/export` (`audit:read`) returns JSON Lines, one record per line, ordered by `chain_seq`, with a cursor (`afterSeq`) for incremental polling by a SIEM collector or a service account with the role `auditor`. Each line contains all chain fields, so consumers can verify continuity and recompute hashes independently.
- Restricted values are exported as ciphertext (base64) with `key_id`, actors as pseudonymous keys; the export contains no names or e-mail addresses and no decrypted restricted values.
- Every export is itself audited (`audit.exported` with range and principal, REQ-175 AC4).
- Push forwarding (syslog over TLS, OTLP) is a follow-up decision; operators with a WORM store or SIEM attach it to the pull export (Option (c) as a secondary channel).

### 10. Entity history tables in the application schema

REQ-083 refers to `entity_versions` and `audit_events` (ADR-016 terminology). The mapping is:

- **Audit trail** (retention, floor, chain, this ADR): `audit.events`, `audit.event_changes`.
- **Entity history** (restore, UC-14 to UC-16, ADR-022): `entity_version_headers`, `entity_property_value_history`, `entity_system_field_history` in `public`. They are **append-only for the runtime role as well** (INSERT and SELECT, no UPDATE or DELETE; owner `oea_owner`). Their lifecycle follows the entity, not the audit retention: rows are removed only by the purge of soft-deleted entities (REQ-190) through an owner-defined function executed by a dedicated purge role, analogous to §4. They are not part of the hash chain; the audit trail records every change independently.

## Consequences

### Positive Consequences

- SEC-13 is resolved: neither the application nor any application administrator can change or delete audit records; database-level manipulation is detectable, including chain recomputation, tail truncation and backup rollback up to the last exported checkpoint.
- Tamper evidence is part of the Community edition at negligible cost (built-in `sha256()`, no extension, no additional service).
- The retention floor is enforced where the application cannot bypass it, and every retention change is audited by the database itself, with a 30-day warning period for reductions.
- The audit store contains no names or e-mail addresses; data subject requests (REQ-189) are fulfilled without modifying append-only records. Restricted values are protected by encryption and the visibility rules (REQ-160).
- Security events, change events and checkpoints share one verifiable trail and one export format suitable for SIEM ingestion (REQ-175).
- Misconfigured role setups fail at startup instead of silently losing protection.
- The application process no longer holds owner credentials; a compromise of the running application cannot alter the schema.

### Negative Consequences / Trade-offs

- **Migration step**: migrations no longer run inside the application at startup; deployments need a one-shot migration step (Compose service, Kubernetes Job/init container) and four database roles. The shipped Compose setup automates this; operators of managed PostgreSQL must run the provisioning script.
- **Serialized audit appends**: the chain head lock limits audit write concurrency; large transactions must flush their audit records just before commit.
- **External audit database** (ADR-024): audit records and entity changes are then written in two databases without a common transaction; the chain is per audit database. Unchanged from ADR-024, but now more visible.
- **Forged additional records** by a compromised application cannot be prevented, only bounded in time and attributed to the compromise window.
- **No protection against destruction** by a database superuser; detection only, and only up to the last checkpoint that reached an external place.
- **Restricted values are protected only if the property was restricted at the time of the event**; making a property restricted later does not encrypt earlier plaintext records (append-only). The operator documentation must point this out.
- **Keyring growth**: audit value keys must be kept as long as records encrypted with them exist (up to the retention period).
- **Display of actors** in audit views needs a join with the identity tables (across databases if the audit database is external); anonymized actors appear only as pseudonymous keys.
- **Canonical serialization** is a long-lived contract; changing a hashed column requires a new `hash_version`.
- Some contributor convenience is lost: local development also uses separate roles (the Compose init script creates them).

### Follow-up Decisions

- Legal hold that suspends the retention purge for selected records or periods (pending)
- Push forwarding to SIEM (syslog over TLS, OTLP) (pending)
- External anchoring of checkpoints via RFC 3161 timestamping or object storage with object lock (pending; Enterprise candidate)
- Handling of personal data of **modeled** persons (EA content) in `old_value`/`new_value` of audit changes beyond the `entity_name` rule (pending, with the business engineer and REQ-189)
- Monthly partitioning of `audit.events` (REQ-083 trade-off, v2.0) and its interaction with the chain and the purge function (pending)

## Required Changes to Other Artifacts

### Data model changes (for the business engineer)

The business engineer updates `docs/data-model.puml` and the business objects; this ADR does not change them.

| # | Change | Affected |
|---|---|---|
| DM-A1 | `audit.events`: remove `actor_id`, `actor_name`, `actor_email`; add `actor_type TEXT NN` (enum `person`, `service-account`, `system-admin-account`, `system`, `unauthenticated`), `actor_person_id BIGINT` (soft ref → persons.id), `actor_admin_id UUID` (soft ref → system_admin_accounts.id) with CHECK on consistency with `actor_type` | `docs/data-model.puml` |
| DM-A2 | `audit.events`: add `event_category`, `severity`, `outcome` (TEXT NN, enums of §8), `source_ip INET`, `target_type TEXT`, `target_id TEXT`, `details JSONB`; `entity_name` NULL for metatype `person`; note "denormalized for tamper evidence" corrected to "for completeness" | `docs/data-model.puml` |
| DM-A3 | `audit.events`: `agent_id` = `client_id` (UUID text) of the authenticated service account only; add `agent_label TEXT` (untrusted `X-Agent-Id`, max. 100 characters); `agent_run` unchanged | `docs/data-model.puml` |
| DM-A4 | Chain fields on `audit.events`, `audit.event_changes` and `audit.retention_settings`: `chain_seq BIGINT NN UQ`, `hash_version SMALLINT NN`, `prev_hash BYTEA NN`, `record_hash BYTEA NN` (replace the nullable TEXT columns and the "Enterprise: tamper chain" note) | `docs/data-model.puml` |
| DM-A5 | `audit.event_changes`: add `property_def_id UUID` (soft ref), `value_protection TEXT NN DEFAULT 'plain'` (enum `plain`, `encrypted`), `old_value_enc BYTEA`, `new_value_enc BYTEA`, `key_id TEXT`; CHECK: encrypted ⇒ plaintext columns NULL and `key_id` NOT NULL | `docs/data-model.puml` |
| DM-A6 | New `audit.chain_head` (single row: `id` CHECK = 1, `last_seq`, `last_hash`, `updated_at`); owner only, no grants to the runtime role | `docs/data-model.puml` |
| DM-A7 | New `audit.retention_settings` (`id`, `retention_days INTEGER NN CHECK >= 365`, `recorded_at`, `effective_from`, actor pair, chain fields); INSERT and SELECT for the runtime role; seeded with 2555 days | `docs/data-model.puml` |
| DM-A8 | Note on the audit package: owner `oea_owner`; runtime role INSERT + SELECT only; cleanup role EXECUTE on `audit.purge_expired()` only; auditor role SELECT; retention floor 1 year, default 7 years, reductions effective after 30 days; chain mandatory in all editions (replaces the current note) | `docs/data-model.puml` |
| DM-A9 | Note on `entity_version_headers`, `entity_property_value_history`, `entity_system_field_history`: runtime role INSERT + SELECT only; deletion only by the REQ-190 purge | `docs/data-model.puml` |
| DM-A10 | Business objects: the audit event is described with pseudonymous actor references; `person.md` documents that anonymization (REQ-189) removes identity from `persons` while audit records keep the key | `business-objects/person.md`, business object for audit events if created |

### API contract changes (for a later step)

`api/openapi.yaml` is changed in a separate spec-first step; this ADR does not change it.

| # | Change |
|---|---|
| API-A1 | Remove `auditRetentionDays` from `SystemSettings`; new `GET`/`PUT /admin/audit/retention` (`settings:manage`): `422` below 365 days; response shows value in force and a pending reduction with its effective date |
| API-A2 | `AuditEvent`: remove `actorEmail`/`actorName`; add `actorType`, `actorRef`, `actorDisplayName` (resolved only for `audit:read`, absent for anonymized persons), `agentId`, `agentLabel`, `category`, `severity`, `outcome`, `sourceIp`, `targetType`, `targetId`, `chainSeq`, `recordHash`; `AuditEventChange` gets `redacted` |
| API-A3 | `GET /audit/events/export` (JSON Lines, `afterSeq` cursor, `audit:read`) and `GET /audit/integrity` (`audit:read`) |
| API-A4 | Document `X-Agent-Id` as informational only (stored as `agentLabel`); remove the JWT claim `agent_id` |
| API-A5 | No operation that modifies or deletes audit records; `/portal/audit/events` removed or `audit:read` (ADR-032 API-8) |

### Other follow-ups

- Concept §21.8 "Audit trail": replace "principal: User | ApiKey | Role" by pseudonymous actor, actor type and agent; "for sensitive data: hash instead of plaintext" by encryption under visibility rules; "typically 3+ years" by the floor of 1 year and default of 7 years; add tamper evidence (hash chain, checkpoints) and JSON Lines export (concept change with CHANGELOG entry).
- REQ-083: replace `entity_versions`/`audit_events` by the mapping of §10; REQ-174: tamper evidence is the hash chain **and** external checkpoints (not "or"); add the 30-day effectiveness delay for retention reductions to REQ-083/REQ-174 (requirements engineer).
- REQ-175: reference the event catalog and the suppression rule for repeated events.
- Operator documentation: role provisioning script, migration step, checkpoint handling (log retention outside the database host), verification command, record of processing and legal basis for pseudonymous audit retention (GDPR Art. 6(1)(c)/(f), Art. 17(3)(b)).
- Specification documents `docs/audit-hash-chain.md` (canonical serialization, field lists) and `docs/audit-event-catalog.md`.
- CI: grant/ownership test with Testcontainers and production roles (REQ-151 AC4, REQ-174 AC1); scan of `db/migration-audit` for UPDATE/DELETE/TRUNCATE.

## Relation to Existing ADRs

| ADR | Relation |
|---|---|
| [ADR-024](./ADR-024-audit-separation.md) | **Partially superseded.** Superseded: section "Database Permissions" (replaced by §1; runtime role now INSERT **and** SELECT, owner role separate); in "Normalized Table Structure" the columns `actor_id`, `actor_name`, `actor_email` and the nullable, Enterprise-only semantics of `prev_hash`/`record_hash`; section "Denormalization for Tamper Evidence" (actor part removed; `entity_name` kept for completeness, not evidence); section "Tamper Chain (optional, Enterprise)" (mandatory in all editions, flag removed); section "Retention" (environment value without floor and cleanup role with DELETE privilege replaced by §4); in the distinction table the app access "READ + WRITE" for `entity_property_value_history` (now INSERT + SELECT, §10) and "Tamper protection: optional". **Still valid**: separate schema `audit`, optional external audit database (`oea.datasource.audit.url`, PostgreSQL per ADR-030), normalized and directly queryable core fields, auditor read role, distinction between audit trail and entity history, rejection of an external audit system as the primary store, event sourcing deferred. |
| [ADR-026](./ADR-026-external-system-integration.md) | Partially superseded: §7 sentence "The `agent_id` value comes from the `X-Agent-Id` request header or the JWT claim `agent_id`" is replaced by §7 of this ADR. Columns `agent_id`/`agent_run` stay. |
| [ADR-015](./ADR-015-db-migration.md) | Partially superseded: "migrations run automatically at app start" and "no separate migration container" are replaced by the one-shot migration step with the owner role (§2). Flyway, plain SQL migrations and forward-only strategy stay; a second migration location for the audit schema is added. |
| [ADR-027](./ADR-027-project-setup-monorepo.md) | Extended: Docker Compose gains a `migrate` service and a PostgreSQL init script for the roles. |
| [ADR-030](./ADR-030-postgresql-only.md) | Applied: built-in `sha256()`, `INET`, `JSONB`, `SECURITY DEFINER` functions; the "one Flyway location" rule refers to dialects and is refined by the separate audit location. |
| [ADR-032](./ADR-032-authorization-model.md) | Used: `audit:read` for reading, exporting, verifying and resolving actor identities; `settings:manage` for the retention setting; audit events for authorization changes (ADR-032 §10) are records of this trail. |
| [ADR-016](./ADR-016-persistence-strategy.md), [ADR-022](./ADR-022-structured-property-model.md) | Clarified: entity history tables are append-only for the runtime role and are not the audit trail (§10). |
| [ADR-019](./ADR-019-soft-delete-strategy.md) | Consistent: hard deletion of soft-deleted entities and their history only via the REQ-190 purge; audit records remain. |

## References

**Concept Chapters**:

- [§21.8 Security and Authorization – Audit trail](../concept/70-platform/21-api-architecture.md)
- [§20 GRC, GDPR and ISMS Integration](../concept/60-integrations/20-grc-gdpr-isms-integration.md)

**Related ADRs**:

- ADR-015, ADR-016, ADR-019, ADR-022, ADR-024, ADR-026, ADR-027, ADR-030, ADR-032

**Use Cases / Requirements**:

- [UC-14: View the Change History of an Entity](../requirements/use-cases/UC-14-view-change-history.md), [UC-22: Import Current State from Source System](../requirements/use-cases/UC-22-import-current-state-from-source-system.md)
- [REQ-083](../requirements/req/REQ-083-audit-log-retention.md), [REQ-151](../requirements/req/REQ-151-audit-schema-separation.md), [REQ-154](../requirements/req/REQ-154-batch-write-upsert.md), [REQ-160](../requirements/req/REQ-160-visibility-on-all-read-channels.md), [REQ-174](../requirements/req/REQ-174-audit-tamper-resistance.md), [REQ-175](../requirements/req/REQ-175-security-event-logging.md), [REQ-189](../requirements/req/REQ-189-data-subject-rights-persons.md)
- [REQ-176](../requirements/req/REQ-176-machine-credential-lifecycle.md), [REQ-179](../requirements/req/REQ-179-secured-raw-query-analytics.md), [REQ-183](../requirements/req/REQ-183-production-safe-defaults.md), [REQ-186](../requirements/req/REQ-186-rate-limits-resource-quotas.md), [REQ-188](../requirements/req/REQ-188-secrets-key-management.md), [REQ-190](../requirements/req/REQ-190-soft-deleted-data-retention.md)

**Security Review**:

- [Security review requirements baseline 2026-10](../security-reviews/requirements-baseline-2026-10.md) – SEC-13, SEC-14, SEC-28 (and SEC-02 for restricted values); owner decisions Q3, Q10 (and Q2, Q5, Q20)

## Notes

- Owner confirmation (2026-10-13): shortening the retention takes effect only after 30 days; names of modeled persons are not copied into audit records.

- The 30-day effectiveness delay for retention reductions and the omission of `entity_name` for modeled persons go beyond the literal owner decisions; both are consistent with Q3/Q10 and REQ-189 and are recorded here so that the requirements can be aligned.
- ADR-024 named the cleanup role `oea_audit_cleanup`; the name is kept, but the role no longer holds a DELETE privilege.
