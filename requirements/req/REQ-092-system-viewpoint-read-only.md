---
id: REQ-092
title: System-defined viewpoints read-only
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-17
author: requirements-engineer
references:
  use_cases:
    - UC-12
  business_objects:
    - viewpoint
    - metamodel-configuration
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-092: System-defined viewpoints read-only

## Statement

Viewpoints with `viewpointType=system-defined` MUST be locked for all write operations. In the UI, edit and delete actions MUST NOT be available for system-defined viewpoints. API attempts MUST be rejected with HTTP 422.

## Rationale

System-defined viewpoints (ArchiMate 3, UML, BPMN 2.0) are part of the OEA core functionality. Changing them would damage the rendering system and lead to inconsistent diagram representations.

## Acceptance Criteria

**AC1** (API write protection):

- When: a DELETE request is sent to `/viewpoints/{system-defined-id}`
- Then: the API responds with HTTP 422

**AC2** (UI write protection):

- When: system-defined viewpoints are displayed in the list
- Then: edit and delete buttons are not visible

**AC3** (Import write protection):

- When: system-defined viewpoints are contained in an import (REQ-059)
- Then: they are not overwritten

## Dependencies

- **Prerequisites**: REQ-090 (viewpoint management)
- **Downstream effects**: none

## Implementation Notes

- `viewpointType` field in the DB; guard logic in the service layer checks the type before every write operation
- UI renders action buttons conditionally based on `viewpointType`
- System-defined viewpoints are initially populated during DB migration and cannot be overwritten by user actions
- Implemented by the OpenSpec change `us-091-viewpoint-notations` (owner decisions 2026-11-17): system-defined viewpoints are not populated by a migration but installed by a metamodel package import (`metamodel:write` alone), with `viewpointType = system-defined` and `sourcePackage`; the starter package (1.2.0) carries "Application Landscape" and "Technology Landscape". AC1: `updateViewpoint` and `deleteViewpoint` (`/admin/viewpoints/{id}`, not `/viewpoints/{id}`) answer 422 with the problem type `urn:oea:problem:read-only-viewpoint` and the detail "System-defined viewpoints are read-only", even with the current version; the API never sets the origin, so a forged body creates a user-defined viewpoint. AC2: SCR-031 shows them in the group "System-defined" with a lock and without "Edit" and "Delete". AC3: only a re-import of the same package updates or deletes them; another package or an organization viewpoint with the same ID or name is a conflict (409)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Change us-091-viewpoint-notations | Implemented; system-defined viewpoints from metamodel packages |
