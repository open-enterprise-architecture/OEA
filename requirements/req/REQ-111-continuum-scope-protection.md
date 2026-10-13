---
id: REQ-111
title: Write Protection for Imported and Built-in Continuum Building Blocks
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
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-111: Write Protection for Imported and Built-in Continuum Building Blocks

## Statement

All continuum building blocks (ABB, SBB, pattern, reference architecture) with `scope=imported` or `scope=built-in` MUST be locked for all write operations. In the UI, edit and delete actions MUST NOT be visible for these building blocks; API attempts MUST be rejected with HTTP 422. Building blocks with `scope=imported` MUST be recognizable by a badge "Imported – [sourcePackage]".

## Rationale

Imported building blocks are the immutable foundation of the continuum library. Changes would corrupt package updates and break traceability to the originating frameworks.

## Acceptance Criteria

**AC1** (API write protection):

- When: a PUT or DELETE request is issued on an ABB with `scope=imported`
- Then: the system responds with HTTP 422

**AC2** (UI write protection and badge):

- When: a building block with `scope=imported` is displayed in the UI
- Then: edit and delete buttons are not visible; a badge "Imported – TOGAF 10 TRM" is visible

**AC3** (SBB assignments editable):

- When: a TRM category with `scope=imported` is opened
- Then: SBB assignments (`preferredStandard`, `acceptedAlternatives`, `deprecatedOptions`) are editable; name and parent category are locked

## Dependencies

- **Prerequisites**: REQ-112 (import mechanism produces scope=imported)
- **Consequences**: REQ-107, REQ-108, REQ-109, REQ-110 (all building block CRUDs must check scope)

## Implementation Notes

- Middleware guard on all PUT/PATCH/DELETE endpoints: check `scope` before the database operation
- UI: the `scope` field from the API response determines visible actions (no client-side security alone)
- Badge content: `sourcePackage` field from the building block record

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
