---
id: REQ-114
title: Atomic Package Import with Conflict Handling
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-18
  business_objects:
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
    - trm-category
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-114: Atomic Package Import with Conflict Handling

## Statement

The package import MUST be executed as an atomic transaction: either all valid building blocks are imported or none (rollback on error). If the package contains building blocks with an ID that already exists in the system, the system MUST display these conflicts as a list and let the user choose between "Skip" (default) and "Update". "Update" MUST only be permitted for building blocks with `scope=imported` from the same `sourcePackage` — not for `scope=organization` building blocks. A cycle in the ABB refinement graph within the package MUST abort the import completely.

## Rationale

A partial import creates inconsistent references between building blocks. Conflict handling protects organization-specific customizations from unintended overwriting by package updates.

## Acceptance Criteria

**AC1** (Abort on cycle):
- When: the imported package contains a cycle in the ABB refinement graph
- Then: the import aborts; no building block is imported; the error message describes the cycle

**AC2** (Conflict list):
- When: the package contains 3 building blocks with already existing IDs
- Then: the system shows the conflict list; the default is "Skip" for all conflicts

**AC3** (Rollback on DB error):
- When: a database error occurs after 20 of 50 building blocks have been imported
- Then: a complete rollback is executed; 0 building blocks are imported

## Dependencies

- **Prerequisites**: REQ-112 (built-in packages), REQ-113 (file upload), REQ-111 (scope protection)
- **Consequences**: none

## Implementation Notes

- Transaction: database level (BEGIN/COMMIT/ROLLBACK); no two-phase commit necessary with a single DB
- Cycle detection: topological sort of the ABB graph in the package before the transaction begins
- "Update" for foreign `scope=organization` building blocks: blocked in the backend by a guard, not only in the UI

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
