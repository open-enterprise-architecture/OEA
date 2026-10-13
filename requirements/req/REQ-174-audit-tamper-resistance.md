---
id: REQ-174
title: Audit store tamper resistance
type: compliance
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-14
    - UC-22
  business_objects:
    - entity-version
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-024
    - ADR-026
    - ADR-015
    - ADR-033
supersedes: []
superseded_by: []
---

# REQ-174: Audit Store Tamper Resistance

## Statement

The audit store **MUST** be owned by a database role different from the application runtime role, **MUST** enforce the retention floor of REQ-083 so that retention cannot be reduced below 1 year through the application and a reduction takes effect no earlier than 30 days after it was recorded, **MUST** provide tamper evidence in all editions through a mandatory hash chain over all audit records **and** periodic checkpoints of the chain head emitted outside the database, and **MUST** record actor and agent identity only from authenticated principals.

## Rationale

If migrations run as the runtime role, that role owns the tables and `REVOKE` has no effect. A configurable retention without floor lets an admin erase their traces. Without tamper evidence, direct database changes go unnoticed. A hash chain alone does not detect a recomputed chain, a truncated tail or a restored old backup; checkpoints outside the database close these gaps. A client-controlled `X-Agent-Id` lets automated writes disguise themselves.

## Context

Source: security review SEC-13 (proposal PR-18); owner decisions Q3 (audit records cannot be deleted or changed by the application or by admins; deletion only through the retention policy; tamper evidence in the Community edition) and Q10 (retention configurable, floor 1 year, default 7 years). REQ-083 and REQ-151 were aligned on 2026-10-13. ADR-033 partially supersedes ADR-024 (optional Enterprise-only tamper chain, retention without floor) and ADR-026 §7 (`X-Agent-Id` as source of `agent_id`): owner role `oea_owner` separate from runtime role `oea_app`, migrations in a separate step under the owner role, hash chain computed by an owner-defined database trigger in all editions, checkpoints every 60 minutes (configurable 5–1440), after each retention purge and at orderly shutdown, emitted to the application log and the SIEM export (REQ-175), optionally signed; verification command `oea audit verify` and a daily scheduled verification. The 30-day delay for retention reductions is an owner confirmation of 2026-10-13.

## Type-Specific Fields

### For type=compliance

**Regulatory framework**: ISO/IEC 27001:2022, DORA

**Specific provision**: ISO/IEC 27001 Annex A 8.15 (logging, protection of log information); DORA Art. 9 (protection and prevention), Art. 12 (logging)

**Evidence requirement**: documented DB role and grant concept; table ownership demonstrable; verification command output; audit events for retention changes

**Audit relevance**: yes; annual review recommended

## Acceptance Criteria

**AC1**:

- Given: the runtime DB role used by the application
- When: it executes `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER` or `DROP` on tables of the audit schema
- Then: the database rejects the statement, and the table owner is a different role

**AC2**:

- Given: an admin sets the audit retention below 365 days via the settings API
- When: saving
- Then: it is rejected; any accepted change creates an audit event with old value, new value and effective date, written in the same transaction by the database, and a reduction becomes effective 30 days after it was recorded

**AC3**:

- Given: an installation of any edition with the default configuration
- When: one row in the audit events table is modified or deleted directly in the database by a superuser
- Then: the verification command reports the first broken record with its chain sequence number

**AC4**:

- Given: a service account request with header `X-Agent-Id: someone-else`
- When: the event is stored
- Then: `agent_id` equals the authenticated `client_id` and the header value is stored only in the informational field `agent_label`

**AC5**:

- Given: a checkpoint of the chain head that was emitted to the application log, and audit records appended after it
- When: a superuser deletes the newest records and recomputes the chain, or restores a database backup older than the checkpoint, and the verification command runs with the exported checkpoint as input
- Then: the verification command reports that the database chain head is behind or diverges from the checkpoint

## Verification Method

- [ ] Method: test (automated, Testcontainers with production DB roles) + inspection of grants and owners
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-083, REQ-151, ADR-033 (Flyway migrations run under the owner role in a separate step)
- **Follow-on effects**: REQ-154, REQ-155 (agent identity), REQ-175
- **Conflicts**: none known (the conflicts with ADR-024 and ADR-026 §7 are resolved by ADR-033)

## Risks of Non-Fulfillment

- Risk 1: silent manipulation or erasure of the audit trail – high; invalidates the DORA/ISO 27001 justification of REQ-151

## Implementation Notes

- The owner/runtime role separation must exist in the first migration of the walking skeleton (sign-off condition of the security review)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-13 (PR-18), owner decisions Q3 and Q10 |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-033: tamper evidence is the mandatory hash chain **and** external checkpoints (AC3 reworded, AC5 added); 30-day delay for retention reductions (statement, AC2); `agent_label` as informational field (AC4); ADR-024/ADR-026 conflicts marked resolved |
