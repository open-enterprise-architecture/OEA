---
id: REQ-119
title: Standards Drift Analysis (Prohibited and Deviation Report)
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
    - trm-category
    - entity
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-119: Standards Drift Analysis (Prohibited and Deviation Report)

## Statement

The system MUST offer a standards drift analysis that reports all entities whose `instanceOfSBBId` points to an SBB with `governanceStatus=prohibited` or `deprecated`. In addition, the analysis MUST show entities whose `instanceOfSBBId` does not match the `preferredStandard` of the assigned TRM category (deviation report). Every row in the result list MUST contain a direct link to the entity detail view. The analysis is read-only.

## Rationale

Standards drift silently renders defined governance ineffective. Automatic detection is the prerequisite for targeted migration planning and early detection of compliance risks.

## Acceptance Criteria

**AC1** (Prohibited entities):

- When: 3 entities have `instanceOfSBBId=prohibited-SBB-UUID`
- Then: all 3 are in the drift list; `prohibited` is highlighted in red

**AC2** (Deviation report):

- When: an entity uses an SBB that does not match the `preferredStandard` of its TRM category
- Then: the entity appears in the deviation list

**AC3** (Direct link):

- When: an entry in the drift list is clicked
- Then: the entity detail view opens

## Dependencies

- **Prerequisites**: REQ-108 (SBB with governanceStatus), REQ-116 (preferredStandard per TRM category)
- **Consequences**: REQ-120 (prohibited entities feed into the executive summary)

## Implementation Notes

- SQL: LEFT JOIN entity on solution_building_block; filter on `governance_status IN ('prohibited', 'deprecated')`
- Deviation report: JOIN on trm_category via entity.trmCategoryId; comparison with `preferred_standard_sbb_id`
- Color coding: `prohibited` = red, `deprecated` = orange; controlled via CSS classes

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
