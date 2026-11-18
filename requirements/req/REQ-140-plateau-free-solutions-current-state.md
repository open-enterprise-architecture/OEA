---
id: REQ-140
title: Plateau-Free Solutions Visible Only in the Current State Node
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-18
author: requirements-engineer
references:
  use_cases:
    - UC-11
    - UC-13
  business_objects:
    - solution
    - plateau
  stakeholders:
    - SH-03
    - SH-04
    - SH-06
  adrs:
    - ADR-020
supersedes: []
superseded_by: []
---

# REQ-140: Plateau-Free Solutions Visible Only in the Current State Node

## Statement

Solutions in project mode (both plateau IDs `null`) MUST NOT appear as standalone top-level nodes in the browser. Their implemented contents MUST flow into the virtual "Current State" node. Solutions in plateau mode additionally appear under their respective plateau node.

## Rationale

The "Current State" is the aggregated overall state of the architecture — regardless of whether contents arose via plateaus or project solutions. Listing plateau-free solutions separately would suggest a false separation to users: the contents are part of the landscape, no matter through which governance process they arose.

## Type-Specific Fields

**Processing**:

1. Solution with `fromPlateauId=null` AND `toPlateauId=null` (project mode):
   - `status=implemented`: contents flow into "Current State" → no top-level node of its own
   - `status ∈ {draft, proposed, approved, in-progress}`: the solution does NOT appear in the browser (only in the solution management view)
2. Solution with plateau references (plateau mode):
   - Appears under the corresponding plateau node
   - Additionally, implemented contents flow into "Current State"

## Acceptance Criteria

**AC1** — Project mode: no node of its own:

- Given: solution A (project mode, `status=implemented`), solution B (plateau mode, CURRENT→TARGET)
- When: the browser panel is opened
- Then: solution A does NOT appear as a node of its own; solution B appears under the plateau node

**AC2** — Contents in Current State:

- Given: solution A (project mode, `status=implemented`) with 3 components
- When: "Current State" is expanded in the browser
- Then: the 3 components are visible under "Current State → Components"

**AC3** — Non-implemented project solutions invisible:

- Given: solution C (project mode, `status=in-progress`)
- When: the browser panel is displayed
- Then: solution C is visible neither in "Current State" nor elsewhere in the browser

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: solutions in both modes and various statuses
- [ ] Pass criterion: AC1–AC3 all green

## Dependencies

- **Prerequisites**: REQ-138; ADR-020; Solution BO (BR-01: both IDs either set or null)

## Implementation

- Realigned by OpenSpec change `us-132-solution-browser` (owner decision 2026-11-18): active project solutions (status neither `implemented` nor `archived`) appear in their own group "Projects" after the plateaus, instead of being invisible until implemented; no solution is a top-level node of its own. A solution that went live (`implemented`) leaves the browser and its entities count under "Current state" (AC1, AC2). The status `in-progress` is listed, so AC3 (not visible) no longer applies.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-18 | fullstack-engineer | Implementation note and realignment (change `us-132-solution-browser`) |
