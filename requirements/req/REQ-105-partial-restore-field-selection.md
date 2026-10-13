---
id: REQ-105
title: Partial Restore Selective Field Selection
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-16
  business_objects:
    - entity
    - entity-version
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-105: Partial Restore Selective Field Selection

## Statement

In the wizard, ONLY fields that differ between the source and the current state MUST be selectable. Identical fields MUST be grayed out and not selectable. Properties that did not yet exist in the metamodel in the source version (added after a later extension) MUST be labeled "(not present in source version)" and MUST NOT be selectable.

## Rationale

Accidentally selecting identical fields would be confusing. Properties that did not exist in the source version cannot be meaningfully restored, because no source value is available.

## Acceptance Criteria

**AC1** (Identical fields grayed out):
- When: a field is identical in the source and the current state
- Then: the checkbox is grayed out and not clickable

**AC2** (New properties labeled):
- When: a property "cost-center" was not yet present in the source version
- Then: the wizard shows "(not present in source version)"; checkbox not selectable

**AC3** (Preview on field selection):
- When: the checkbox of a changed field is activated
- Then: the value from the source version is displayed as a preview

## Dependencies

- **Prerequisites**: REQ-104 (wizard), REQ-100 (snapshots)
- **Consequences**: REQ-106 (restoredFields log)

## Implementation Notes

- Diff calculation via dot-notation flattening of both snapshots
- Mark fields with `source=undefined` (not in source snapshot) as not-restorable
- Read the preview value directly from the snapshot JSON of the source version

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
