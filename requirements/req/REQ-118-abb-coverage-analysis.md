---
id: REQ-118
title: ABB Coverage Analysis (Gap Ratio)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-20
  business_objects:
    - architecture-building-block
    - solution-building-block
    - entity
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-118: ABB Coverage Analysis (Gap Ratio)

## Statement

The system MUST offer an ABB coverage analysis that reports for every ABB with `governanceStatus=approved`: the number of conforming entities (`conformsToABBId`), the number of implementing SBBs, and whether a gap exists (no conforming entities). The overall ratio (ABBs with at least one instance / all approved ABBs) MUST be visible as a percentage "gap ratio". The analysis is read-only.

## Rationale

ABBs without conforming instances show where defined standards have not arrived in the landscape — the essential governance statement of the Architecture Continuum. The gap ratio enables trending over time.

## Acceptance Criteria

**AC1** (Gap ratio calculation):

- When: 5 approved ABBs exist, 2 of them without an instance
- Then: the analysis shows gap ratio = 40%; the table shows all 5 ABBs with their instance counts

**AC2** (ABB detail view):

- When: an ABB is opened in the analysis table
- Then: implementing SBBs and conforming entities are visible as clickable links

**AC3** (Empty state):

- When: no ABBs are configured yet
- Then: the system shows the note "No ABBs configured yet; start with UC-17"

## Dependencies

- **Prerequisites**: REQ-107 (ABBs), REQ-108 (SBBs)
- **Consequences**: REQ-120 (technology diversity analysis uses the same data for the executive summary)

## Implementation Notes

- Gap ratio: SQL aggregation; no caching required in the first version
- Conforming entities: JOIN on `entity.conformsToABBId`; links navigate to the entity detail view
- Analysis: read-only GET endpoint; no write operations

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
