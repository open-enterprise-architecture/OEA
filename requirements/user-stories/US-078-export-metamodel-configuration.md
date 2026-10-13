---
id: US-078
title: Export Metamodel Configuration
use_case: UC-04
requirement: REQ-058
priority: should
status: proposed
story_points: 3
created: 2026-06-26
---

# US-078: Export Metamodel Configuration

**As** a Lead Enterprise Architect (Kurt, SH-03)  
**I want** to export the current metamodel configuration as a JSON or YAML file,  
**so that** I can import it into another OEA instance, back it up, or version it with the team.

## Acceptance Criteria

- [ ] "Export metamodel" button available in the MetamodelConfiguration UI
- [ ] Export contains all EntityTypeDefinitions (incl. scope, extends, properties, creationSteps)
- [ ] Export contains all connection types and arc42Collections
- [ ] Format: JSON and YAML selectable
- [ ] scope=built-in types are included in the export but marked as immutable
- [ ] The exported file can be re-imported via US-033 (import starter package)
- [ ] File name: `oea-metamodel-{instance-slug}-{date}.json`

## Technical Notes

- `GET /api/v1/metamodel/export?format=json|yaml`
- ADR-002: include the scope property in the export; import treats `scope=built-in` as `scope=imported`
