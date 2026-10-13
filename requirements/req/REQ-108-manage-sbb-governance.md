---
id: REQ-108
title: Manage SBB Governance Status
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-17
  business_objects:
    - solution-building-block
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-108: Manage SBB Governance Status

## Statement

The system MUST allow creating, editing, and deleting `SolutionBuildingBlock` entries (SBBs) with `scope=organization`. Mandatory fields: `name`, `governanceStatus` (`[approved, acceptable, deprecated, prohibited]`). Optional: `vendor`, `version`, `description`, `evaluationNotes`, `implements` (multi-select of existing ABBs). When `governanceStatus=prohibited` is set, the system MUST mark all entities with `instanceOfSBBId` = this SBB ID in the UI with the note "Product in use prohibited – migration required".

## Rationale

SBBs with governance status are the mechanism for technology standards enforcement. Without a prohibited warning on entities, the standard remains ineffective, because architects have no direct visibility into the affected landscape elements.

## Acceptance Criteria

**AC1** (Create SBB):
- When: an SBB is created with `governanceStatus=approved` and `implements=[ABB-UUID]`
- Then: the system responds with HTTP 201

**AC2** (Prohibited warning on entities):
- When: an SBB is set to `governanceStatus=prohibited`
- Then: all entities with `instanceOfSBBId=this-SBB` show the note "Product in use prohibited – migration required" in the UI

**AC3** (ABB reference warning):
- When: an SBB is created without `implements` ABBs
- Then: the system shows a governance warning "SBB without ABB reference is only a product catalog entry" without blocking saving

## Dependencies

- **Prerequisites**: REQ-107 (ABBs must exist for the `implements` reference), REQ-111 (scope protection)
- **Consequences**: REQ-116 (TRM-SBB governance assignment), REQ-119 (standards drift analysis)

## Implementation Notes

- `governanceStatus=prohibited` triggers an asynchronous job that marks all `instanceOfSBBId` references
- Warning on entities: UI badge, no API block at the entity level itself
- `implements` as a many-to-many relation between SBB and ABB in a separate join table

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
