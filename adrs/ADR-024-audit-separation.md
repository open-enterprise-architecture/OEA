# ADR-024: Audit Data Storage — Separate Schema, Configurable as External Database

**Status**: accepted
**Date**: 2026-06-29
**Deciders**: [Lukas – Repository Owner](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Context

Audit logs are subject to special requirements in regulated industries:
- **Immutability**: no UPDATE/DELETE by the application
- **Retention obligation**: 7–10 years (depending on regulation)
- **Separate access rights**: auditors need read access, the app only INSERT
- **Regulatory queryability**: individual fields must be indexed directly (no JSONB payload)
- **Separation from system data**: during compliance audits the audit trail must not be mixed
  with operational data

The previous `audit_events` model with `payload JSONB` fulfills none of these requirements.

---

## Decision

### Schema Separation

All audit tables are kept in the schema **`audit`**, separate from the application schema `public`.

**Default (Community / simple deployments)**: `audit` schema in the same database as the
application data. Configurable via `oea.datasource.audit.url` — if set, OEA writes
the audit data to its own database.

```yaml
# application.yml (excerpt)
oea:
  datasource:
    audit:
      url:      ${OEA_AUDIT_DB_URL:}          # empty = use app DB
      username: ${OEA_AUDIT_DB_USER:}
      password: ${OEA_AUDIT_DB_PASS:}
```

If the URL is set, Spring configures a second `DataSource` and a second
`EntityManagerFactory` for the `audit` package. If the URL is empty, OEA uses the app `DataSource`
and writes to `audit.*` of the same DB.

### Database Permissions

The app user receives only `INSERT` permissions on `audit.*`:
```sql
GRANT INSERT ON ALL TABLES IN SCHEMA audit TO oea_app;
REVOKE UPDATE, DELETE ON ALL TABLES IN SCHEMA audit FROM oea_app;
```

A separate read user for auditors:
```sql
GRANT SELECT ON ALL TABLES IN SCHEMA audit TO oea_auditor;
```

### Normalized Table Structure

No JSONB payload. Every field is directly queryable and indexable.

**`audit.events`** — event header:
```
id             BIGSERIAL PK
event_type     TEXT NN   -- e.g. 'entity.created', 'entity.property_changed', 'entity.deleted'
entity_id      INTEGER   -- soft reference (no FK — audit DB can be external)
entity_name    TEXT      -- denormalized for tamper evidence
actor_id       BIGINT    -- soft reference
actor_name     TEXT NN   -- denormalized (person may be anonymized later)
actor_email    TEXT NN   -- denormalized
source         TEXT NN   -- enum: UI | API | Job | Import
occurred_at    TIMESTAMPTZ NN DEFAULT NOW()
request_id     TEXT      -- correlation ID (HTTP request or job run)
prev_hash      TEXT      -- SHA-256 of the predecessor record (Enterprise: tamper chain)
record_hash    TEXT      -- SHA-256(prev_hash || event_type || entity_id || occurred_at || …)
```

**`audit.event_changes`** — property deltas per event:
```
id               BIGSERIAL PK
event_id         BIGINT NN   -- FK→audit.events.id (or soft ref for external DB)
property_name    TEXT NN     -- denormalized ('name', 'lifecycle', 'version', …)
system_field     BOOLEAN NN DEFAULT false  -- true = system field, false = user-defined property
old_value        TEXT        -- serialized as text (DB-agnostic)
new_value        TEXT        -- serialized as text
```

### Denormalization for Tamper Evidence

`actor_name`, `actor_email`, and `entity_name` are stored at the time of the event,
even if the person is later anonymized or the entity is deleted. The audit trail
remains complete and immutable.

### Tamper Chain (optional, Enterprise)

`prev_hash` and `record_hash` enable a cryptographic chaining of all audit records.
A gap or manipulation is computationally provable. In Community operation these
fields are not populated (NULL). In Enterprise operation they can be activated via configuration:

```yaml
oea:
  audit:
    hash-chain-enabled: ${OEA_AUDIT_HASH_CHAIN:false}
```

### Retention

Configurable retention policy via cron job (DELETE WHERE occurred_at < NOW() - INTERVAL):
```yaml
oea:
  audit:
    retention-days: ${OEA_AUDIT_RETENTION_DAYS:2555}  # default: 7 years
```
The delete job runs as the `oea_audit_cleanup` user with DELETE permission — not the app user.

---

## Distinction from entity_property_value_history

| Aspect | `entity_property_value_history` | `audit.event_changes` |
|---|---|---|
| Purpose | Restore (UC-15/UC-16) | Compliance, traceability (UC-14) |
| Schema | `public` | `audit` |
| App access | READ + WRITE | INSERT only |
| Denormalization | no (FKs) | yes (actor_name, entity_name) |
| Retention | as long as the entity exists | configurable (default 7 years) |
| Content | typed value columns | TEXT (serialized) |
| Tamper protection | no | optional (hash_chain) |

Both tables are populated in parallel when an entity change occurs.

---

## Rejected Alternatives

### JSONB payload (previous approach)
Not indexable, not portable (ADR-023), not regulatorily queryable. **Rejected**.

### External audit system (CloudTrail, Azure Monitor)
Maximum isolation, but vendor lock-in, self-hosting not possible. **Rejected** for open source.

### Event sourcing as primary model
Would have audit as a by-product. Too complex for v1. **Deferred** to v2.

---

## Consequences

**Positive:**
- Regulatorily compliant (immutability, retention, separation)
- DB-agnostic (no JSONB)
- Auditors get their own read-only user
- Optional external audit DB without application changes (config only)
- Tamper chain can be retrofitted for enterprise requirements

**Negative / Compromises:**
- Two DataSources increase the complexity of the Spring configuration
- Denormalization (actor_name etc.) must be actively populated when writing
- Cleanup job needs a separate DB user

---

## Affected Concept Chapters

- §14 (Audit and Versioning), §15 (Security), §20 (Compliance/Conformance)

## Related ADRs

- ADR-019: Soft-delete strategy
- ADR-022: Structured property model (entity_property_value_history)
- ADR-023: Multi-DB strategy (no JSONB)
