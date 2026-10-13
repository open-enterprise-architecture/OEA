---
id: REQ-090
title: Create, edit and delete viewpoint
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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

# REQ-090: Create, edit and delete viewpoint

## Statement

The system MUST enable creating, editing and deleting user-defined `ViewpointDefinition` entries. A viewpoint consists of: `name` (mandatory, unique in the instance), `notation` (mandatory: `archimate3|uml|bpmn20`), `allowedEntityTypes[]`, `allowedConnectionTypes[]` and `notationMappings[]` (per EntityType: `notationElement`, `defaultWidth`, `defaultHeight`). Editing takes place in a three-step form: base fields → type selection → notation mappings.

## Rationale

Organizations have specific perspectives (cloud security, domain overview) that cannot be represented in standard viewpoints. User-defined viewpoints enable adapting the tool to organization-specific notation conventions.

## Acceptance Criteria

**AC1** (Create viewpoint):
- When: a new viewpoint with 3 EntityTypes is created
- Then: the API responds with HTTP 201; the viewpoint is immediately selectable for new diagrams

**AC2** (Remove EntityType with warning):
- When: during editing, an EntityType that is used in existing diagrams is removed from `allowedEntityTypes`
- Then: the system shows a warning "N diagrams affected"; editing is not blocked

**AC3** (Delete viewpoint):
- When: a user-defined viewpoint is deleted
- Then: it is removed from the `MetamodelConfiguration`

## Dependencies

- **Prerequisites**: REQ-032 (EntityTypeDefinition present); REQ-058/059 (export/import already present)
- **Downstream effects**: REQ-091 (notation mapping), REQ-092 (system-defined write protection), REQ-093 (delete warning)

## Implementation Notes

- Three-step form with step indicators in the UI
- Ensure uniqueness of the `name` field via DB constraint at instance level
- `allowedEntityTypes` and `allowedConnectionTypes` as array fields in the DB

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
