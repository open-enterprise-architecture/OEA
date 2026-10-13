---
id: REQ-105
title: Partial Restore Selective Field Selection
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-10-13
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

In the wizard, ONLY fields that differ between the source and the current state MUST be selectable. Identical fields MUST be grayed out and not selectable. Properties that did not yet exist in the metamodel in the source version (added after a later extension) MUST be labeled "(not present in source version)" and MUST NOT be selectable. Properties the acting principal is not permitted to write MUST be labeled "(insufficient permission)" and MUST NOT be selectable, and the server MUST reject them if submitted anyway; values of properties the principal is not permitted to read MUST NOT be shown in the preview (REQ-160, REQ-162).

## Rationale

Accidentally selecting identical fields would be confusing. Properties that did not exist in the source version cannot be meaningfully restored, because no source value is available.

Security review SEC-04 (2026-10-13): partial restore is a write path; without a permission check it bypasses property write protection.

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

**AC4** (Write permission respected):

- Given: a principal without write permission on property P that differs between source and current state
- When: the wizard is shown and, separately, a restore request including P is sent directly to the API
- Then: P is labeled "(insufficient permission)" and not selectable; the API request leaves P unchanged and reports it as "not restored (insufficient permission)"

**AC5** (Read permission respected):

- Given: a principal without read permission on property P
- When: the wizard is shown
- Then: neither the source nor the current value of P is displayed or contained in the response

## Dependencies

- **Prerequisites**: REQ-104 (wizard), REQ-100 (snapshots), REQ-160 (visibility on all read channels), REQ-162 (write protection on all write paths)
- **Consequences**: REQ-106 (restoredFields log)

## Implementation Notes

- Diff calculation via dot-notation flattening of both snapshots
- Mark fields with `source=undefined` (not in source snapshot) as not-restorable
- Read the preview value directly from the snapshot JSON of the source version

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-04: partial restore respects property write and read permissions (AC4, AC5) |
