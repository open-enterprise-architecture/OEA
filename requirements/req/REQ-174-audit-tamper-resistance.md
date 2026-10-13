---
id: REQ-174
title: Audit store tamper resistance
type: compliance
priority: must
status: proposed
version: 0.1.0
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
supersedes: []
superseded_by: []
---

# REQ-174: Audit Store Tamper Resistance

## Statement

The audit store **MUST** be owned by a database role different from the application runtime role, **MUST** enforce the retention floor of REQ-083 so that retention cannot be reduced below 1 year through the application, **MUST** provide tamper evidence in all editions (hash chain enabled by default, or external append-only export), and **MUST** record actor and agent identity only from authenticated principals.

## Rationale

If migrations run as the runtime role, that role owns the tables and `REVOKE` has no effect. A configurable retention without floor lets an admin erase their traces. Without tamper evidence, direct database changes go unnoticed. A client-controlled `X-Agent-Id` lets automated writes disguise themselves.

## Context

Source: security review SEC-13 (proposal PR-18); owner decisions Q3 (audit records cannot be deleted or changed by the application or by admins; deletion only through the retention policy; tamper evidence in the Community edition) and Q10 (retention configurable, floor 1 year, default 7 years). REQ-083 and REQ-151 were aligned on 2026-10-13. ADR-024 currently makes the tamper chain optional and Enterprise-only and allows retention changes without floor; a new ADR is needed (accepted ADRs are not rewritten).

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
- Then: it is rejected; any accepted change creates an audit event with old and new value

**AC3**:

- Given: the hash chain enabled by default
- When: one row in the audit events table is modified directly in the database
- Then: the verification command reports the first broken record

**AC4**:

- Given: a service account request with header `X-Agent-Id: someone-else`
- When: the event is stored
- Then: `agent_id` equals the authenticated `client_id` and the header value is stored only in a separate informational field

## Verification Method

- [ ] Method: test (automated, Testcontainers with production DB roles) + inspection of grants and owners
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-083, REQ-151, ADR-015 (Flyway migrations must run under the owner role)
- **Follow-on effects**: REQ-154, REQ-155 (agent identity), REQ-175
- **Conflicts**: ADR-024 (optional Enterprise-only tamper chain, retention without floor, `X-Agent-Id` as source)

## Risks of Non-Fulfillment

- Risk 1: silent manipulation or erasure of the audit trail – high; invalidates the DORA/ISO 27001 justification of REQ-151

## Implementation Notes

- The owner/runtime role separation must exist in the first migration of the walking skeleton (sign-off condition of the security review)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-13 (PR-18), owner decisions Q3 and Q10 |
