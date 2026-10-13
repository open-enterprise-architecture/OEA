---
id: REQ-141
title: Components Can Be Nested Hierarchically in the Browser
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
    - ADR-020
    - ADR-021
supersedes: []
superseded_by: []
---

# REQ-141: Components Can Be Nested Hierarchically in the Browser

## Statement

The system MUST make it possible to arrange ArchitectureEntity elements (`isConnection=false`) hierarchically beneath each other in the browser by assigning one element to another as the parent element (`parentEntityId`). The hierarchy MUST be displayed in the browser panel as a nested tree. Structuring aids (`isStructuralGrouper=true`) MAY also act as parent nodes, but do not create an implicit connection (→ REQ-142).

## Rationale

Real enterprise architectures are hierarchical: an "ERP system" contains modules such as "Finance module" and "Logistics module". Mapping this hierarchy in the model and making it directly visible in the navigator is a central added value compared to flat lists.

## Type-Specific Fields

**Inputs**:
- ArchitectureEntity with `parentEntityId` set or null
- `isStructuralGrouper: boolean`

**Processing**:
1. Under "Components" of a solution, the system shows all elements with `parentEntityId=null` as the first level
2. Elements with `parentEntityId` are indented as children of their parent element
3. Structuring aids (`isStructuralGrouper=true`) appear as nodes without an ArchiMate icon (folder icon instead)
4. Instantiated elements (`isStructuralGrouper=false`) appear with the ArchiMate type icon (small)
5. Maximum nesting depth: 10 levels
6. Cyclic parent-child references are forbidden (BR validation)

**Output**:
```
▼ Components (6)
  ▼ [AC] SAP S/4HANA            ← instantiated
      [AC] Finance module        ← child, implicit composition (REQ-142)
      [AC] Logistics module      ← child, implicit composition
  ▼ [Folder] Backend systems     ← structuring aid, no icon, no connection
      [AC] Auth service
  [AC] Portal frontend
```

## Acceptance Criteria

**AC1** — Nesting displayed:
- Given: entity A has `parentEntityId = Entity B`
- When: the "Components" node is expanded
- Then: entity A appears indented under entity B

**AC2** — Structuring aids without ArchiMate icon:
- Given: entity C with `isStructuralGrouper=true`
- When: the browser is displayed
- Then: entity C appears with a folder icon, no ArchiMate icon

**AC3** — Flat elements at the top level:
- Given: entity D with `parentEntityId=null`
- When: "Components" is expanded
- Then: entity D appears at the first indentation level

## Verification Method

- [ ] Method: test (automated) + demonstration
- [ ] Test setup: entities with and without parentEntityId; nested structuring aids
- [ ] Pass criterion: AC1–AC3 all green

## Dependencies

- **Prerequisites**: ADR-021 (implicit connection); `entity.md` attributes `parentEntityId` and `isStructuralGrouper`
- **Consequences**: REQ-142 (connection logic for instances)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
