---
id: REQ-033
title: Import metamodel from file
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
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

# REQ-033: Import metamodel from file

## Statement

The system MUST allow persons with metamodel editor permission to upload a metamodel configuration file (YAML or JSON); the system MUST validate the file before import, show a preview of the changes (diff: new types, conflicts, unchanged types), and import only after explicit confirmation.

## Rationale

Many organizations have existing architecture standards as YAML/JSON documents, or an architect lead prepares the metamodel locally and submits it as a file. The import path is more efficient than manually entering many EntityTypes via the GUI, especially during initial setup. The mandatory diff before import protects against unintentionally overwriting existing configuration.

## Context

YAML is the primary import format (corresponds to the format from §14 of the concept). JSON is an equivalent alternative. ArchiMate XMI is out of scope of this requirement. The import is additive: new types are added; existing, unchanged types are retained; built-in types cannot be overwritten. Conflicts (name collisions with custom types) must be resolved before the import.

## Type-Specific Fields

### For type=functional

**Inputs**:
- File (YAML or JSON); format is detected automatically
- Confirmation by the user after the preview

**Processing**:
1. Upload: accept the file; maximum size TBD (proposal: 1 MB)
2. Parsing: convert YAML/JSON into an internal data structure; syntax errors → immediate error message
3. Schema validation: mandatory fields present, types correct, cardinality format valid, enum values not empty
4. Diff calculation:
   - New types (green): name not in built-in and not in existing custom types
   - Conflicts (red): name collides with built-in → not importable; name collides with custom type → overwrite possible (with warning)
   - Unchanged types (gray): exactly the same name and same definition already present
5. User confirms the import
6. Import: all conflict-free new types are persisted; for overwritable conflicts only those explicitly confirmed

**Outputs**:
- Preview diff (before the import)
- Summary after import: X types imported, Y conflicts skipped
- Audit log entries for each imported/updated type

**Error Cases**:
- File not parseable (invalid YAML/JSON) → syntax error with line and column
- Schema violation (e.g., missing mandatory field `name`) → list of all validation errors
- All entries of the file are conflicts → import not possible without confirmation; the user must correct the file
- File too large → error message with size limit

## Acceptance Criteria

**AC1**:
- Given: a valid YAML file with 3 new custom EntityTypes
- When: Kurt uploads the file and confirms the preview
- Then: all 3 types are present in the MetamodelConfiguration; the audit log contains 3 entries

**AC2**:
- Given: a YAML file that defines a type with the name `ApplicationComponent` (built-in)
- When: Kurt uploads the file
- Then: the preview shows this entry as red/conflict with "not importable (built-in)"; it cannot be confirmed

**AC3**:
- Given: a syntactically invalid YAML file
- When: Kurt uploads it
- Then: an error message with line and column appears immediately; no preview

**AC4**:
- Given: a valid file with 2 new types and 1 conflicting (overwritable custom type)
- When: Kurt confirms only the 2 new types and skips the conflict
- Then: only the 2 new ones are imported; the existing custom type remains unchanged

**AC5**:
- Given: a JSON file with a structure identical to the YAML variant
- When: Kurt uploads it
- Then: it is handled in the same way (format auto-detected)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: valid YAML/JSON files; file with built-in conflict; invalid syntax; partial import test
- [x] Measurement tool: test suite of the import module
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-032 (the import result corresponds to the same schema as the GUI result; both end up in MetamodelConfiguration)
- **Downstream effects**: none of its own; imported types behave like types created via GUI
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without import, many types must be entered manually via the GUI – time-consuming during initial setup, low to medium severity
- Risk 2: Import without a diff preview could unintentionally overwrite existing configuration, medium severity (mitigated by the mandatory diff)

## Trade-offs

- Partial import (AC4) increases UI complexity (checkboxes per entry) but offers flexible control. Alternative: all-or-nothing → simpler but less flexible. Recommendation: partial import.

## Implementation Notes

- Backend: `POST /admin/metamodel/import` with multipart form data; response: diff object; `POST /admin/metamodel/import/confirm` with the selection of entries to import
- YAML parsing: established library (e.g., `gopkg.in/yaml.v3` for Go, `js-yaml` for Node.js)
- Diff: calculated server-side, not client-side (file is never parsed directly in the browser)

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
