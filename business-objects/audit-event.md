---
identifier: audit-event
name_de: Audit-Ereignis
name_en: Audit Event
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Audit Record
  - Audit Log Entry
  - Security Event
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - concept: concept/60-integrations/20-grc-gdpr-isms-integration.md
  - adr: adrs/ADR-024-audit-separation.md
  - adr: adrs/ADR-033-audit-trail-hardening.md
  - adr: adrs/ADR-034-secured-raw-analytics.md
---

# Business Object: Audit Event

## Definition

An audit event is the immutable, tamper-evident record of one relevant occurrence in OEA – a change of content or configuration, a security event, an access or a system event – attributed to a pseudonymous actor. Together, all audit events form the audit trail, one hash chain in the schema `audit`.

## Description

The audit trail answers "who did what, when, from where, with which result" for regulators and auditors (ISO/IEC 27001 A 8.15/8.16, DORA Art. 9/12). It is separate from the [entity history](./entity-version.md), which serves restore and change views and follows the entity's lifecycle (ADR-033 §10).

Key properties ([ADR-033](../adrs/ADR-033-audit-trail-hardening.md)):

- **Append-only**: no API or UI operation changes or deletes audit events, for any permission. The runtime database role can only insert and read. Events are removed only by the retention purge after the retention period.
- **Tamper-evident**: every event (and every change detail and retention setting row) is a link of one SHA-256 hash chain, computed in the database. Checkpoints of the chain head are exported to the application log and the SIEM export, so that recomputation, tail truncation and backup rollback are detectable.
- **Pseudonymous actors**: the actor is stored as actor type plus the key of the principal ([person](./person.md) or [system admin account](./system-admin-account.md)); names and e-mail addresses are resolved at read time and only for principals holding `audit:read`. Anonymizing a person therefore never touches an audit event (REQ-189).
- **Protected restricted values**: changes of properties whose visibility mode is not `public` at the time of the event are stored encrypted; they are shown only to principals with `audit:read` and read permission on the property (REQ-160 AC4).
- **One trail for all event kinds**: change events, security events (REQ-175), analytics queries (ADR-034) and integrity events share one store, one chain and one export.

Typical lifetime: the retention period in force, at least 1 year, default 7 years (2555 days).

## Attributes

### AuditEvent (table `audit.events`)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | integer | required | | BIGSERIAL; technical key | Technical identifier |
| eventType | string | required | | stable name from the event catalog (`docs/audit-event-catalog.md`, follow-up), e.g. `entity.created`, `auth.login`, `authz.denied`, `analytics.query.executed` | What happened |
| eventCategory | enum | required | | `[change, security, access, system]` | Category for filtering and SIEM routing |
| severity | enum | required | | `[info, warning, high, critical]` | Severity |
| outcome | enum | required | | `[success, failure, denied]` | Result of the operation |
| occurredAt | datetime | required | now | UTC, microseconds | Time of the event |
| source | enum | required | | `[UI, API, Job, Import]` | Channel |
| sourceIp | string | optional | | INET | Source address |
| requestId | string | optional | | | Correlation ID (HTTP request or job run); links to application logs |
| actorType | enum | required | | `[person, service-account, system-admin-account, system, unauthenticated]` | Kind of actor |
| actorPerson | reference | conditional | | target: [person](./person.md) (soft reference, no foreign key); set iff `actorType` is `person` or `service-account` | Pseudonymous actor key |
| actorAdmin | reference | conditional | | target: [system-admin-account](./system-admin-account.md) (soft reference); set iff `actorType = system-admin-account` | Pseudonymous actor key |
| targetType | string | optional | | e.g. `entity`, `person`, `role`, `role-assignment`, `setting`, `service-account` | Kind of affected object |
| targetId | string | optional | | | Identifier of the affected object |
| entityId | reference | optional | | target: [entity](./entity.md) (soft reference) | Affected entity, for change events |
| entityName | string | optional | | empty for entities of metatype `person` | Entity name at the time of the event, denormalized for completeness |
| details | composite | optional | | JSONB; event-type-specific | Supplementary fields (e.g. role, validity, `sodConflict`, analytics fields, HMAC of an attempted login identifier) |
| agentId | string | optional | | `client_id` of the authenticated service account only; empty for humans and the system admin account | Writing external system (ADR-026, ADR-033 §7) |
| agentLabel | string | optional | | max. 100 characters; control characters removed; untrusted | Value of the `X-Agent-Id` header, informational only |
| agentRun | string | optional | | | Correlation ID of an integration run (`X-Agent-Run`) |
| chainSeq | integer | required | | unique; assigned by the database | Position in the hash chain |
| hashVersion | integer | required | | SMALLINT | Version of the canonical serialization |
| prevHash | binary | required | | 32 bytes; genesis = 32 zero bytes | Hash of the predecessor link |
| recordHash | binary | required | | 32 bytes; SHA-256 | Hash of this link |

### AuditEventChange (table `audit.event_changes`)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| event | reference | required | | target: AuditEvent | Owning event |
| propertyDefinition | reference | optional | | target: PropertyDefinition of the [metamodel configuration](./metamodel-configuration.md) (soft reference); empty for system fields | Definition on which visibility is evaluated |
| propertyName | string | required | | denormalized | Name of the changed field or property |
| systemField | boolean | required | false | | System field (`name`, `description`, …) vs. user-defined property |
| valueProtection | enum | required | plain | `[plain, encrypted]`; `encrypted` iff the property's visibility mode was not `public` at the time of the event | Protection of the values |
| oldValue / newValue | string | optional | | only if `valueProtection = plain` | Plaintext values, serialized as text |
| oldValueEncrypted / newValueEncrypted | binary | optional | | only if `valueProtection = encrypted`; AES-256-GCM | Encrypted values |
| keyId | string | conditional | | required iff `valueProtection = encrypted` | Audit value key used |
| chainSeq, hashVersion, prevHash, recordHash | | required | | as for AuditEvent | Chain link fields |

### AuditRetentionSetting (table `audit.retention_settings`)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| retentionDays | integer | required | 2555 (initial row) | ≥ 365 (database CHECK; floor not configurable) | Retention period |
| recordedAt | datetime | required | now | | When the setting was recorded |
| effectiveFrom | datetime | required | | `recordedAt + 30 days` for reductions, otherwise `recordedAt` | When the value takes effect |
| recordedBy | principal | optional | | person or system admin account; empty for the migration seed | Who changed the retention |
| chainSeq, hashVersion, prevHash, recordHash | | required | | as for AuditEvent | Chain link fields |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| hasChanges | AuditEventChange | 1:n | yes | Field-level changes of a change event |
| actor | [person](./person.md) | n:1 | yes | Pseudonymous reference (key only) |
| actorAdmin | [system-admin-account](./system-admin-account.md) | n:1 | yes | Pseudonymous reference (key only) |
| concerns | [entity](./entity.md) | n:1 | yes | Affected entity (soft reference; the audit database can be external) |
| precededBy | AuditEvent / AuditEventChange / AuditRetentionSetting | 1:1 | yes | Predecessor link in the hash chain (none for the genesis link) |

**Notes**: references to the application schema are soft references without foreign keys, because the audit schema may live in a separate database (ADR-024).

## Lifecycle

```text
recorded ──► purged (only after the effective retention period, by audit.purge_expired())
```

**States**:

- `recorded`: inserted in the same transaction as the triggering change; immutable from then on
- `purged`: removed as part of the oldest contiguous chain range older than the effective retention; the purge itself appends an `audit.retention-purged` event that becomes the new verification anchor

**Transitions**:

- `recorded → purged`: nightly retention job (role `oea_audit_cleanup`, function `audit.purge_expired()`); never earlier than `GREATEST(retention in force, 365 days)`

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Audit events cannot be changed or deleted through any API or UI operation and by no permission; the only operations are read, export and verify, all requiring `audit:read` | always | ADR-033 §8, REQ-083, Q3 |
| BR-02 | Audit events contain no names or e-mail addresses of OEA users; actors are stored as actor type plus key | onCreate | ADR-033 §3, REQ-189, GDPR Art. 5(1)(c) |
| BR-03 | `entityName` is empty for entities of metatype `person` (modeled persons) | onCreate | ADR-033 §3, owner confirmation 2026-10-13 |
| BR-04 | `agentId` is taken only from the authenticated service account's `client_id`; the `X-Agent-Id` header is stored only as `agentLabel` and is never used for decisions | onCreate | ADR-033 §7, Q5 |
| BR-05 | Values of a property whose visibility mode is not `public` at the time of the event are stored encrypted and decrypted only for principals with `audit:read` and read permission on the property; otherwise the change is returned as redacted | onCreate, onRead | REQ-160 AC4 |
| BR-06 | Every audit event, change and retention setting row is a link of one hash chain computed by the database; a scheduled verification reports the first broken link (`audit.integrity-violation`) | onCreate, daily | ADR-033 §5, REQ-174 |
| BR-07 | The retention period is at least 365 days (default 2555); a reduction takes effect 30 days after it was recorded; every retention change creates a `settings.audit-retention-changed` event written by the database | onUpdate (retention) | ADR-033 §4, REQ-083, Q10 |
| BR-08 | Repeated `authz.denied`, `auth.throttled` and failed-login events of the same principal or source address and operation are recorded individually up to 20 per minute; beyond that one summary event per minute carries the count | onCreate | ADR-033 §8, REQ-186 |
| BR-09 | Every raw analytics request that passed authentication creates exactly one `analytics.query.executed` or `analytics.query.rejected` event with query hash, literal-free fingerprint, referenced views, outcome and metrics; never result content or bound parameter values; the query result is not returned if the audit write fails | onAnalyticsQuery | ADR-034 §6, REQ-179, REQ-175 |
| BR-10 | Events without an authenticated principal store an attempted login identifier only as a keyed HMAC-SHA-256 | onCreate | ADR-033 §3, REQ-188 |

## Examples

**Example 1**: Change of a restricted property by a service account

```yaml
eventType: entity.property_changed
eventCategory: change
severity: info
outcome: success
source: API
actorType: service-account
actorPerson: 2051
agentId: "8f0c2d1e-…"        # client_id
agentLabel: "oea-scanner/1.0"
entityId: 42
entityName: "SAP ERP"
changes:
  - propertyName: annualLicenseCost
    valueProtection: encrypted
    keyId: audit-2026-01
chainSeq: 184467
```

**Example 2**: Raw analytics query

```yaml
eventType: analytics.query.executed
eventCategory: access
severity: info
outcome: success
source: API
actorType: person
actorPerson: 1042
details:
  queryHash: "9b1f…"
  queryFingerprint: "SELECT … FROM reporting.entities WHERE metatype_id = $1"
  referencedViews: [reporting.entities]
  analyticsOutcome: truncated-rows
  durationMs: 812
  rowCount: 10000
```

## Distinction from Similar Objects

- **NOT** [entity-version](./entity-version.md) / entity history: the entity history serves restore and change views (UC-14 to UC-16), is not chained and is purged with soft-deleted entities (REQ-190).
- **NOT** application logs: logs carry the correlation ID but are not the audit trail and never contain secrets.
- **NOT** [session](./session.md): login and logout are audit events; the session record itself is deleted after `sessionRetentionDays`.

## Usage in Use Cases

- [UC-14: View the Change History of an Entity](../requirements/use-cases/UC-14-view-change-history.md)
- [UC-22: Import Current State from Source System](../requirements/use-cases/UC-22-import-current-state-from-source-system.md)

## Concept Reference

- [§21.8 Security and Authorization – Audit trail](../concept/70-platform/21-api-architecture.md)
- [§20 GRC, GDPR and ISMS Integration](../concept/60-integrations/20-grc-gdpr-isms-integration.md)

## Notes

Created for ADR-033 DM-A10 (and DM-A1 to DM-A7) and ADR-034 DM-4; previously the audit trail existed only in `docs/data-model.puml`. Reason: SEC-13 (tamper resistance), SEC-14 (security events), SEC-28 (personal data in append-only records).

Open questions:

- The detailed analytics outcome of ADR-034 §6 (`ok`, `truncated-rows`, `timeout`, `rejected-<rule>`, …) does not match the common `outcome` enum; modeled as `details.analyticsOutcome` with the mapping `ok`/`truncated-*` → `success`, `rejected-*`/`rate-limited` → `denied`, `timeout`/`execution-error` → `failure`, to be confirmed in the event catalog.
- ADR-034 §6 lists the session ID and client among the principal fields; there is no session column, so it is kept in `details`.
- Personal data of modeled persons in `oldValue`/`newValue` beyond the `entityName` rule (ADR-033 follow-up).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Business Engineer | Initial draft from ADR-033 (DM-A1 to DM-A10) and ADR-034 (DM-4) |
