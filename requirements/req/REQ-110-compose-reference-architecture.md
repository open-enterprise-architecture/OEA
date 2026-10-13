---
id: REQ-110
title: Compose Reference Architecture from Building Blocks
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
    - reference-architecture
    - architecture-building-block
    - architecture-pattern
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-110: Compose Reference Architecture from Building Blocks

## Statement

The system MUST allow creating, editing, and deleting `ReferenceArchitecture` entries. Mandatory fields: `name`, `continuumLevel`, `governanceStatus`. Optional: `description`, `targetIndustry` (mandatory if `continuumLevel=industry`), `composedOf` (ABB multi-select), `basedOnPatterns` (pattern multi-select). A reference architecture MUST reference at least one ABB or one pattern; an empty composition MUST produce a governance warning (no block). Reference architectures with `governanceStatus=approved` MUST be selectable as a blueprint option when creating a plateau (UC-11).

## Rationale

Reference architectures without building blocks are empty phrases. Linking with plateau creation activates the reuse benefit of the Enterprise Continuum in the operational planning process.

## Acceptance Criteria

**AC1** (Create reference architecture and make available as blueprint):
- When: a reference architecture is created with 3 ABBs and 1 pattern and `governanceStatus=approved` is set
- Then: the system responds with HTTP 201; the reference architecture is selectable as a blueprint in UC-11 (plateau creation)

**AC2** (Empty composition warning):
- When: a reference architecture without ABBs and without patterns is to be saved
- Then: the system shows a governance warning; saving is not blocked

**AC3** (Mandatory field targetIndustry):
- When: a reference architecture with `continuumLevel=industry` is to be saved without `targetIndustry`
- Then: the system responds with HTTP 422

## Dependencies

- **Prerequisites**: REQ-107 (ABBs), REQ-109 (patterns), REQ-111 (scope protection)
- **Consequences**: UC-11 (plateau creation with blueprint option)

## Implementation Notes

- `composedOf` and `basedOnPatterns` as separate many-to-many relations
- Blueprint link to UC-11: reference architectures with `governanceStatus=approved` retrievable via an API endpoint for the plateau dropdown
- Governance warning for an empty composition: UI banner, no API error

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
