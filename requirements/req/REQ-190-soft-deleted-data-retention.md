---
id: REQ-190
title: Retention of soft-deleted data
type: compliance
priority: should
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-14
    - UC-15
  business_objects:
    - entity
    - entity-version
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/60-integrations/20-grc-gdpr-isms-integration.md
  adrs:
    - ADR-019
supersedes: []
superseded_by: []
---

# REQ-190: Retention of Soft-Deleted Data

## Statement

The system **SHOULD** permanently purge soft-deleted entities, their property value history and their attachments after an operator-configurable retention period (default 365 days), while keeping the pseudonymous audit trail.

## Rationale

Soft-deleted data, including personal data in EA content, would otherwise be kept forever (ADR-019 plans no purge before v2.0), which conflicts with storage limitation.

## Context

Source: security review SEC-28 (proposal PR-34). The audit trail follows REQ-083 and is not purged by this REQ.

## Type-Specific Fields

### For type=compliance

**Regulatory framework**: GDPR

**Specific provision**: Art. 5(1)(e) (storage limitation)

**Evidence requirement**: purge job configuration and audit events `entity.purged`

**Audit relevance**: yes

## Acceptance Criteria

**AC1**:

- Given: an entity soft-deleted 366 days ago with default settings
- When: the purge job runs
- Then: the entity row and its property value history are removed and an audit event `entity.purged` exists

**AC2**:

- Given: a purge
- When: the audit trail is queried for that entity ID
- Then: the earlier audit events remain retrievable

## Verification Method

- [ ] Method: test (automated, controlled clock)
- [ ] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: ADR-019, REQ-083
- **Follow-on effects**: REQ-101 (restore no longer possible after purge)
- **Conflicts**: ADR-019 (no purge before v2.0)

## Risks of Non-Fulfillment

- Risk 1: indefinite retention of personal data – low to medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-28 (PR-34) |
