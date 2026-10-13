---
id: REQ-142
title: Implicit Composition Connection When Nesting Instantiated Components
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-13
  business_objects:
    - entity
  stakeholders:
    - SH-03
    - SH-04
  adrs:
    - ADR-021
supersedes: []
superseded_by: []
---

# REQ-142: Implicit Composition Connection When Nesting Instantiated Components

## Statement

When an instantiated ArchitectureEntity (`isStructuralGrouper=false`) is arranged as a child of another instantiated entity, the system MUST automatically create an ArchiMate composition connection (`autoCreated=true`, `source=parentEntityId`, `target=childEntityId`). This connection MUST be visible in the "Connections" category of the solution. On detaching, the `autoCreated=true` connection MUST be deleted automatically. Structuring aids (`isStructuralGrouper=true`) MUST NOT create an implicit connection.

## Rationale

The hierarchy of components has semantic meaning in ArchiMate: a parent-child relationship is a composition relationship. This information should not be lost when users arrange components in the explorer. The distinction between structuring aids (folders) and instances (real EA elements) prevents unwanted connections arising from pure navigation decisions.

## Type-Specific Fields

**Inputs**:
- Parent entity A: `isStructuralGrouper=false`, `isConnection=false`
- Child entity B: `isStructuralGrouper=false`, `isConnection=false`
- B.`parentEntityId` is set to A

**Processing**:
1. Check: both entities `isStructuralGrouper=false`? → create implicit connection
2. Check: at least one entity `isStructuralGrouper=true`? → no connection
3. Creation: new ArchitectureEntity of type `composition` with `autoCreated=true`, `source=A.id`, `target=B.id`
4. On detaching (setting B.`parentEntityId=null`): delete the autoCreated connection
5. Manual change of the connection (e.g. switching to aggregation) → `autoCreated=false`; survives detaching

**Output**:
- New connection visible under "Connections" with the label "[auto] Composition: A → B"
- Clearly marked as automatically created in the connection UI

## Acceptance Criteria

**AC1** — Implicit connection created:
- Given: entities A and B (`isStructuralGrouper=false`); B.parentEntityId is set to A
- When: the change is saved
- Then: a composition connection (autoCreated=true) between A and B exists in "Connections"

**AC2** — No connection for structuring aid:
- Given: structuring aid C (`isStructuralGrouper=true`) and entity D
- When: D.parentEntityId = C is set
- Then: no connection is created

**AC3** — Connection deleted on detaching:
- Given: AC1 state (autoCreated connection exists)
- When: B.parentEntityId is set to null
- Then: the autoCreated connection is removed automatically

**AC4** — Manually changed connection survives:
- Given: autoCreated connection from AC1; user changes the type to aggregation (`autoCreated=false`)
- When: B.parentEntityId is set to null
- Then: the aggregation connection is retained (autoCreated=false)

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: unit tests for connection logic; Playwright for UI visibility
- [ ] Pass criterion: AC1–AC4 all green

## Dependencies

- **Prerequisites**: ADR-021; REQ-141; `entity` BO with `parentEntityId`, `isStructuralGrouper`, `autoCreated`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft; derived from ADR-021 |
