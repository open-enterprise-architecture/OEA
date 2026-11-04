---
id: REQ-148
title: Property Mapping Effective Date Changeable
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-29
last_updated: 2026-11-04
author: requirements-engineer
references:
  use_cases:
    - UC-04
  adrs:
    - ADR-022
supersedes: []
superseded_by: []
---

# REQ-148: Property Mapping Effective Date Changeable

## Statement

The system **MUST** make it possible to change the necessity (mandatory/warning/optional) of a property assignment to a MetaType by effective date, without deleting existing values or overwriting historical assignments.

## Rationale

Metamodels evolve. A property that is optional today can become mandatory from a certain date. Without an effective date mechanism, either all historical assignments would have to be changed (loss of traceability) or ad hoc solutions arise.

## Context

`metatype_property_mappings.valid_from` is the effective date. The system determines the active assignment by "latest valid_from that is ≤ today". Older entries are retained (history). (ADR-022)

## Type-Specific Fields

**Inputs**:

- Existing mapping (metatype_id, property_def_id)
- New `valid_from`, new `necessity`

**Processing**:

- Create a new record in `metatype_property_mappings` with the new valid_from
- Existing entries remain unchanged
- UNIQUE(metatype_id, property_def_id, valid_from) prevents duplicates

**Outputs**:

- 201 Created; new mapping entry

**Error Cases**:

- valid_from already present for this pair → 409 Conflict

## Acceptance Criteria

**AC1**:

- Given: property P has been mapped as `optional` for MetaType M since 2026-01-01
- When: an admin creates a new mapping P→M with valid_from=2026-07-01, necessity=mandatory
- Then: from 2026-07-01, mandatory applies; entities missing P receive a warning/error on validation

**AC2**:

- Given: a mapping entry for valid_from=2026-01-01 exists
- When: an admin tries to enter the same valid_from again
- Then: 409 Conflict

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: integration test against PostgreSQL (Testcontainers, ADR-030)
- [ ] Pass criterion: POST /metamodel/metatypes/{id}/property-mappings creates a second entry; GET returns the latest assignment
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-149 (validation by necessity)
- **Consequences**: US-137

## Notes on the Implementation

- Realized by OpenSpec change `us-137-mapping-effective-date`: entries by effective date for organization and package mappings (ADR-049), a date in the past takes effect at once; existing entries and values stay. AC1: the marks of entities without a value are set when the entry takes effect, by a scheduled check at the latest about ten minutes after midnight UTC; the error follows when such an entity is written again (REQ-149). AC2: 409 with problem type `conflict`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-022 |
| 0.2.0 | 2026-11-04 | requirements-engineer | Implementation note (change `us-137-mapping-effective-date`) |
