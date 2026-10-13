---
id: REQ-113
title: Import Continuum Package via File Upload
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-18
  business_objects:
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
    - trm-category
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-113: Import Continuum Package via File Upload

## Statement

The system MUST allow uploading continuum packages as a `.json` or `.yaml` file. After the upload, the system MUST immediately validate the package against the OEA continuum package schema and, on schema violations, return HTTP 422 with an error list (line, field, error description) — without importing a single building block. On successful validation, a preview MUST be displayed before the import is confirmed.

## Rationale

Schema errors must be visible before the import so that the operator can correct the file. A preview before the commit prevents accidental imports and provides transparency about the scope of the import.

## Acceptance Criteria

**AC1** (Valid file – preview):
- When: a valid package file is uploaded
- Then: the UI shows a preview with the number and kind of the contained building blocks

**AC2** (Schema violation):
- When: a file with a missing mandatory field is uploaded
- Then: the system responds with HTTP 422 and an error list (line, field, error description); no building block is imported

**AC3** (Import after confirmation):
- When: the preview is confirmed
- Then: the import is executed; all imported building blocks have `scope=imported`

## Dependencies

- **Prerequisites**: REQ-111 (scope protection), REQ-114 (atomic import transaction)
- **Consequences**: none

## Implementation Notes

- Schema validation: JSON Schema or Zod on the server; convert YAML to JSON via a parser before validation
- Error list: array of `{line, field, message}` in the 422 response body
- File upload: multipart form upload; max file size configurable (default: 10 MB)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
