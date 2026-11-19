---
id: REQ-090
title: Create, edit and delete viewpoint
type: functional
priority: must
status: proposed
version: 0.3.0
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

# REQ-090: Create, edit and delete viewpoint

## Statement

The system MUST enable creating, editing and deleting user-defined `ViewpointDefinition` entries. A viewpoint consists of: `name` (mandatory, unique in the instance), `notation` (mandatory: `archimate3|uml|bpmn20`), `allowedEntityTypes[]`, `allowedConnectionTypes[]` and `notationMappings[]` (per EntityType: `notationElement`, `visualHint`) and `sizes[]` (per EntityType: `width`, `height` in pt; per connection type: `length` in pt). Editing takes place in a three-step form: base fields → type selection → notation mappings.

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

- Realigned by the OpenSpec change `us-090-viewpoints` (owner decisions 2026-11-17): viewpoints are generic for now – name (1–255 characters, unique case-insensitively), description (at most 1000 characters), at least one element metatype and any number of connection metatypes of the instance; notation and notation mapping follow with the diagrams; `/admin/viewpoints` with `If-Match` on update and delete (412, 428); AC2 (diagrams affected) follows with the diagrams
- Realigned by the OpenSpec change `us-091-viewpoint-notations` (owner decisions 2026-11-17, later the same day): `notation` is one of `generic`, `archimate3`, `uml` and `bpmn2` (not `bpmn20`), optional on write with the default `generic`; viewpoints created before have `generic` (V41). `mappings` (`metatypeId`, `notationElement`, `defaultWidth`, `defaultHeight`, `visualHint`) are optional, at most one per allowed type, for entity and connection types (REQ-091); `effectiveMappings` falls back to the generic element. The UI is one dialog on SCR-031 with the notation, the type selection and a mapping table, not a three-step form. AC1 and AC3 are met with the notation; AC2 follows with the diagrams
- Realigned by the OpenSpec change `s17-viewpoint-default-sizes` (owner decisions 2026-11-19): the size left the mapping. `sizes` (`metatypeId` with `width` and `height`, or `length`, integers 1–2000 pt) are optional, at most one per allowed type and 1000 per write; `effectiveMappings` carries `width`, `height`, `length` and `sizeStored`; without a stored size an element shape is 90 × 30 pt and a connection 90 pt. A `defaultWidth` or `defaultHeight` in a mapping is ignored like any unknown property.

- Three-step form with step indicators in the UI
- Ensure uniqueness of the `name` field via DB constraint at instance level
- `allowedEntityTypes` and `allowedConnectionTypes` as array fields in the DB

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Change us-090-viewpoints | Generic viewpoints implemented (AC1, AC3); AC2 with the diagrams |
| 0.3.0 | 2026-11-17 | Change us-091-viewpoint-notations | Notation and optional notation mapping implemented |
