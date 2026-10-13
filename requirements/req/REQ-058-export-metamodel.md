---
id: REQ-058
title: Export metamodel configuration
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
    - metamodel-configuration
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/40-extensibility/14-extensibility.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-058: Export metamodel configuration

## Statement

The system MUST enable persons with metamodel editor permission to download the complete MetamodelConfiguration of their own instance as a YAML file or JSON file; the exported file MUST be usable as input for the import according to REQ-033 of another OEA instance.

## Rationale

Organizations with multiple OEA instances (e.g., a corporate standards instance + a project instance) must be able to transfer metamodels between instances. The export also serves for versioning and backing up the metamodel configuration outside the database (Git repo, document management).

## Context

The export contains all configurable elements of MetamodelConfiguration v0.6.0:
- EntityTypeDefinitions (built-in + custom)
- Stereotypes
- ConstraintRules
- Viewpoints (including NotationMappings with defaultWidth/defaultHeight; cf. REQ-059)
- ArchitectureLayerDefinitions
- ArchitectureDomainDefinitions
- MandatoryConnectionConstraints

The exported format is the same as the import format from REQ-033, so that export → editing → import is possible without format conversion.

## Type-Specific Fields

### For type=functional

**Inputs**:
- Format selection: YAML (default) or JSON
- Optional: scope filter (`includeBuiltins: true|false`; default `true`)

**Processing**:
1. Read the instance's current MetamodelConfiguration from the database
2. Serialize into the chosen format (YAML/JSON); key order deterministic (alphabetical)
3. Insert a header comment with export timestamp, OEA version and instance ID
4. Deliver as a file download

**Outputs**:
- File download; filename: `oea-metamodel-{instanceId}-{ISO8601date}.yaml` (or `.json`)
- HTTP header: `Content-Disposition: attachment`

**Error Cases**:
- No permission → HTTP 403
- Internal serialization errors → HTTP 500 with log entry (no stack trace in the response)

## Acceptance Criteria

**AC1** (Export as YAML):
- Given: Kurt has metamodel editor permission; instance has 5 custom EntityTypes, 2 ArchitectureLayers, 3 viewpoints
- When: Kurt exports as YAML
- Then: Download starts; YAML contains all 5 custom EntityTypes, 2 ArchitectureLayers, 3 viewpoints with notationMappings including defaultWidth/defaultHeight

**AC2** (Export as JSON):
- Given: Same configuration as AC1
- When: Kurt exports as JSON
- Then: Download starts; JSON is structurally equivalent to the YAML version

**AC3** (Roundtrip compatibility):
- When: Kurt imports the exported YAML file into another OEA instance via REQ-033
- Then: All non-built-in types are imported correctly (diff shows: X new types, 0 syntax errors)

**AC4** (Without permission):
- Given: Franz has no metamodel editor permission
- When: Franz calls the export endpoint
- Then: HTTP 403; no download

**AC5** (includeBuiltins=false):
- When: Kurt exports with `includeBuiltins=false`
- Then: Exported file contains only custom EntityTypes and user-defined configuration elements; no built-in EntityTypes

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: Instance with known configuration; export roundtrip test (export → import into clean instance → comparison)
- [x] Measuring tool: Test suite of the export module
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, as soon as implementation exists

## Dependencies

- **Prerequisites**: REQ-033 (import; export format must be import-compatible)
- **Downstream effects**: Combined with REQ-033, this enables metamodel versioning in Git
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Metamodel transfer between instances is only possible manually (high effort with many custom types)
- Risk 2: No external backup of the metamodel outside the database

## Implementation Notes

- Backend: `GET /admin/metamodel/export?format=yaml&includeBuiltins=true`
- Streaming recommended for large configurations (> 100 EntityTypes)
- Header comment: `# OEA Metamodel Export | instance: {id} | exported: {ISO8601} | oea-version: {version}`

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
