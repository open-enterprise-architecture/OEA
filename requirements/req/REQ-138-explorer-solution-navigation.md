---
id: REQ-138
title: Browser Panel Shows Solutions as the Primary Navigation Level
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
    - plateau
  stakeholders:
    - SH-03
    - SH-04
  adrs:
    - ADR-020
supersedes: []
superseded_by: []
---

# REQ-138: Browser Panel Shows Solutions as the Primary Navigation Level

## Statement

The browser panel in the native client MUST display solutions as primary navigation nodes at the top level. A virtual "Current State" node MUST appear as the first node, representing the aggregated contents of all realized solutions. Below it follow plateau-bound solutions, grouped by plateau.

## Rationale

Solution architects work primarily in the context of a solution. The navigation should reflect this working context directly. The "Current State" node is the most important entry point for analyses and reviews.

## Type-Specific Fields

**Inputs**:
- All solutions with `status ∈ {draft, proposed, approved, in-progress, implemented}` (not `archived`)
- Plateau information (fromPlateauId, toPlateauId) from the solution object

**Processing**:
1. Always display the "Current State" node as the first element (aggregates all `status=implemented` solutions and plateau-free contents)
2. Solutions with plateau mapping are grouped under the corresponding plateau node
3. Solutions without plateau mapping (`fromPlateauId=null`, `toPlateauId=null`) appear ONLY in the "Current State" node — not as their own top-level node (→ REQ-140)
4. Within a plateau: solutions sorted alphabetically
5. Solutions with `status=archived` are not displayed

**Outputs**:
- Hierarchical tree view: [Current State] → [Plateau A] → [Solution 1] → [Base Structure]

## Acceptance Criteria

**AC1** — Current State node:
- Given: at least one implemented solution exists
- When: the browser panel is opened
- Then: "Current State" appears as the first node

**AC2** — Plateau grouping:
- Given: solution with `fromPlateauId=P1, toPlateauId=P2`
- When: the browser panel is loaded
- Then: the solution appears under the plateau node (e.g. "CURRENT → TARGET 2027")

**AC3** — No archived solutions:
- Given: solution with `status=archived`
- When: the browser panel is displayed
- Then: the archived solution is not visible

## Verification Method

- [ ] Method: test (automated) + demonstration
- [ ] Test setup: Playwright; solutions with various statuses and plateau mappings
- [ ] Pass criterion: AC1–AC3 all green

## Dependencies

- **Prerequisites**: ADR-020 (explorer navigation model)
- **Consequences**: REQ-139 (base structure), REQ-140 (plateau-free solutions)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft; derived from ADR-020 and mockup feedback |
