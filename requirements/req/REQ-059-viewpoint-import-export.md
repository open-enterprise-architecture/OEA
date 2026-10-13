---
id: REQ-059
title: Import and export viewpoints
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - viewpoint
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
    - SH-07
  concept:
    - concept/40-extensibility/14-extensibility.md
    - concept/30-dynamics/12-domain-views.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-059: Import and export viewpoints

## Statement

The system MUST enable persons with metamodel editor permission to export individual viewpoints as a YAML file and to import viewpoint YAML files; during import the system MUST validate all referenced EntityType IDs (`allowedEntityTypes`, `allowedConnectionTypes`, `notationMappings.entityTypeId`) against the target MetamodelConfiguration and report missing IDs as errors before the import is aborted.

## Rationale

Viewpoints encapsulate visual knowledge (which types, which representation, which default sizes). This knowledge should be reusable between teams, between OEA instances and in a viewpoint catalog (community library). ID-based referencing (cf. viewpoint.md v0.2.0) ensures that the viewpoint file is portable and that its import into another instance remains deterministically validatable.

## Context

- Exported viewpoints contain all fields of the ViewpointDefinition including `notationMappings` with `defaultWidth`, `defaultHeight` and `notationElement`.
- System-defined viewpoints CAN be exported but NOT imported (built-ins already exist in the target instance).
- An imported viewpoint receives a new UUID on import if its ID already exists in the target instance (conflict handling: the user chooses cancel or rename).
- The import path does not check the semantics of the `notationElement` values; it only ensures structural correctness (BR-07 from viewpoint.md) and ID existence.

## Type-Specific Fields

### Export

**Inputs**:

- Viewpoint ID (single export)
- Optional: bulk export of all user-defined viewpoints of the instance

**Processing**:

1. Read the viewpoint from the database
2. Serialize as YAML (JSON as an option)
3. Insert a header comment with export timestamp, OEA version, source instance ID

**Outputs**:

- File download; filename: `oea-viewpoint-{viewpointId}-{ISO8601date}.yaml`
- Bulk: `oea-viewpoints-{instanceId}-{ISO8601date}.zip` (one file per viewpoint)

**Error Cases**:

- Viewpoint not found → HTTP 404
- No permission → HTTP 403

### Import

**Inputs**:

- Viewpoint YAML file (multipart upload)

**Processing**:

1. Accept the file; max. size: 256 KB
2. Parsing: YAML → internal data structure; syntax errors → immediate error message
3. Schema validation: mandatory fields present (`id`, `name`, `notation`, `viewpointType`, `allowedEntityTypes`)
4. ID validation: all IDs in `allowedEntityTypes`, `allowedConnectionTypes` and `notationMappings.entityTypeId` MUST exist in the target MetamodelConfiguration; missing IDs → list of all errors; no partial import (BR-08 from viewpoint.md)
5. Conflict check: viewpoint ID already exists → user chooses cancel or new ID suffix
6. The imported viewpoint gets `viewpointType=user-defined`; system-defined from the import is ignored
7. Persist; audit log entry

**Outputs**:

- HTTP 201 with the created ViewpointDefinition (JSON)
- Audit log entry: `viewpoint.imported` with file name and source instance ID from the header comment

**Error Cases**:

- Missing EntityType IDs → HTTP 422 with list of the unknown IDs
- ID conflict (without user resolution) → HTTP 409 with proposed alternative ID
- File too large → HTTP 413
- Invalid YAML → HTTP 422 with line/column information

## Acceptance Criteria

**AC1** (Export single viewpoint):

- Given: User-defined viewpoint `cloud-security-view` with 4 allowedEntityTypes, 3 notationMappings
- When: Kurt exports this viewpoint as YAML
- Then: YAML contains all fields including notationMappings with defaultWidth/defaultHeight; ID `cloud-security-view` is included

**AC2** (Roundtrip):

- When: Kurt imports the exported YAML file into another instance (all referenced EntityType IDs present)
- Then: HTTP 201; viewpoint exists in the target instance with identical configuration

**AC3** (Missing EntityType IDs):

- Given: YAML references `allowedEntityTypes: [security-zone]`; target instance does not know an EntityType ID `security-zone`
- When: Kurt imports the file
- Then: HTTP 422; error message contains `unknown entityTypeId: security-zone`; no viewpoint created

**AC4** (ID conflict):

- Given: Target instance already has a viewpoint with ID `cloud-security-view`
- When: Kurt uploads a viewpoint file with the same ID
- Then: HTTP 409; response contains a proposed alternative ID (e.g., `cloud-security-view-2`); no import without explicit confirmation

**AC5** (System-defined not importable):

- Given: YAML with `viewpointType: system-defined`
- When: Kurt imports the file
- Then: System ignores `viewpointType=system-defined`; imports the viewpoint as `user-defined`; HTTP 201

**AC6** (Bulk export):

- When: Kurt exports all user-defined viewpoints
- Then: ZIP download with one YAML file per user-defined viewpoint; system-defined viewpoints included (optional, as helpful for roundtrip)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: Instance with known viewpoints; clean target instance for roundtrip; test file with missing IDs; test file with ID conflict
- [x] Measuring tool: Test suite of the import/export module
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, as soon as implementation exists

## Dependencies

- **Prerequisites**: viewpoint.md v0.2.0 (ID-based referencing as a stable foundation); REQ-033 (import pattern for diff + confirmation; viewpoint import is stricter: no partial import)
- **Downstream effects**: Enables a community library for viewpoints (e.g., ArchiMate standard viewpoints importable from an external source)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Viewpoint knowledge is instance-bound and not reusable; every team redefines representation standards
- Risk 2: During metamodel migration (EntityType renaming), viewpoints cannot be ported if there is no ID-based referencing

## Implementation Notes

- Export: `GET /admin/metamodel/viewpoints/{id}/export?format=yaml`
- Bulk export: `GET /admin/metamodel/viewpoints/export?format=zip`
- Import: `POST /admin/metamodel/viewpoints/import` (multipart form data)
- ID validation server-side; no client-side pre-parsing

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
