---
id: REQ-139
title: Every Solution Has an Auto-Generated Base Structure in the Explorer
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-13
  business_objects:
    - solution
    - entity
    - catalog
    - architecture-building-block
    - solution-building-block
  stakeholders:
    - SH-03
    - SH-04
  adrs:
    - ADR-020
supersedes: []
superseded_by: []
---

# REQ-139: Every Solution Has an Auto-Generated Base Structure in the Explorer

## Statement

Every solution MUST automatically contain, in the browser panel and without manual configuration, six base categories as child nodes: **Components**, **Connections**, **Catalogs**, **Functional Building Blocks**, **Solution Building Blocks**, and **Diagrams**. The categories MUST display the number of contained objects. Empty categories MUST remain visible (grayed out) and MUST NOT be hidden.

## Rationale

Every solution needs the same base structure. Manual creation via TreeNode CRUD would be repetitive and error-prone (a forgotten folder would then be missing entirely). The consistent structure enables users to find the same navigation path immediately in every solution.

**Categories and their contents**:

| Category | Contains | Entity type (BO) |
|---|---|---|
| Components | All ArchitectureEntities with `isConnection=false` in this solution | `entity` |
| Connections | All ArchitectureEntities with `isConnection=true` in this solution | `entity` |
| Catalogs | All catalog objects assigned to this solution | `catalog` |
| Functional Building Blocks | All ABBs (Architecture Building Blocks) assigned to the solution | `architecture-building-block` |
| Solution Building Blocks | All SBBs (Solution Building Blocks) assigned to the solution | `solution-building-block` |
| Diagrams | All diagram instances (viewpoint instances) assigned to the solution | (Diagram BO) |

*Note*: "Functional Building Block" (FBB) is the term used in OEA for `ArchitectureBuildingBlock` (ABB) in the solution context. It is the same business object — the term FBB is used in the UI to emphasize the functional role in the solution design.

## Type-Specific Fields

**Inputs**: solution ID → all linked objects from the six categories

**Processing**:

1. The system loads objects of all six categories for the given solution
2. Each category as a node with count (e.g. "Components (4)")
3. Empty categories: node remains visible, count "(0)", visually grayed out
4. Components are displayed hierarchically when `parentEntityId` is set (REQ-141)

## Acceptance Criteria

**AC1** — Complete base structure:

- Given: any solution (with or without content)
- When: the solution is expanded in the browser
- Then: exactly six categories visible as child nodes

**AC2** — Empty categories visible:

- Given: solution with 0 catalogs
- When: the solution is expanded in the browser
- Then: "Catalogs (0)" appears grayed out — not hidden

**AC3** — Count correct:

- Given: the solution has 3 components and 5 connections
- When: the browser is displayed
- Then: "Components (3)" and "Connections (5)" are displayed correctly

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: Playwright; solutions with empty and filled content
- [ ] Pass criterion: AC1–AC3 all green

## Dependencies

- **Prerequisites**: REQ-138 (solution as primary navigation level); ADR-020
- **Consequences**: REQ-141 (component hierarchy under "Components")

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft; equating FBB with ABB documented |
