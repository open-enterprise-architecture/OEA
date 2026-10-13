---
id: REQ-107
title: Create ABBs and Manage Refinement Hierarchy
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
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-107: Create ABBs and Manage Refinement Hierarchy

## Statement

The system MUST allow creating, editing, and deleting `ArchitectureBuildingBlock` entries (ABBs) with `scope=organization`. Mandatory fields: `name` (unique per `continuumLevel`), `continuumLevel`, `maturityLevel`, `governanceStatus`. Optional: `specifications` (Markdown), `industry` (mandatory if `continuumLevel=industry`), `refines` (reference to an existing ABB; the dropdown only shows entries without cycle risk). When saving, the system MUST detect cycles in the refinement graph and reject them with HTTP 422.

## Rationale

ABBs are the central specification building blocks of the Enterprise Continuum. Without CRUD operations and a refinement hierarchy, no organization-specific continuum library can be built. Cycle detection is necessary to prevent inconsistent refinement chains that would corrupt later graph traversals.

## Acceptance Criteria

**AC1** (Create ABB):

- When: an ABB is created with `continuumLevel=organization` and `maturityLevel=established`
- Then: the system responds with HTTP 201; the ABB is visible in the library

**AC2** (Cycle detection):

- When: ABB "B" is set to `refines=A` and ABB "A" is subsequently to be set to `refines=B`
- Then: the system responds with HTTP 422 "Cycle detected: A → B → A" and rejects saving

**AC3** (Mandatory field industry):

- When: an ABB with `continuumLevel=industry` is to be saved without the `industry` field
- Then: the system responds with HTTP 422

## Dependencies

- **Prerequisites**: REQ-111 (scope protection for imported building blocks)
- **Consequences**: REQ-110 (reference architecture composes ABBs), REQ-118 (ABB coverage analysis)

## Implementation Notes

- Cycle detection: DFS or Kahn's algorithm on the ABB refinement graph before every save
- Dropdown for `refines`: only show ABBs without a transitive reference to the ABB currently being edited
- `name` must be secured by a unique constraint per `continuumLevel` in the DB

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
