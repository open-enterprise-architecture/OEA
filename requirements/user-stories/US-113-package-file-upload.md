# US-113: Import a Continuum Package via File Upload

**ID**: US-113
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to upload a `.json` or `.yaml` package file and see a preview with the schema validation result before the import, so that I can correct errors in the file before building blocks are imported.

## Reference

**Use Case**: [UC-18](../use-cases/UC-18-import-continuum-package.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-113](../req/REQ-113-package-file-upload.md)

## Acceptance Criteria

**AC1** (Valid file – preview):
- When: I upload a valid package file
- Then: the UI shows a preview with the number and kind of the contained building blocks

**AC2** (Schema violation):
- When: I upload a file with a missing mandatory field
- Then: I receive HTTP 422 with an error list (line, field, error description); no building block is imported

**AC3** (Import after confirmation):
- When: I confirm the preview
- Then: the import is executed; all imported building blocks have `scope=imported`

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
