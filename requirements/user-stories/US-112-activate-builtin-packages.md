# US-112: Activate Embedded Continuum Packages with One Click

**ID**: US-112
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to import embedded continuum packages such as "TOGAF 10 TRM" with one click, so that I immediately get a complete base library without a file upload.

## Reference

**Use Case**: [UC-18](../use-cases/UC-18-import-continuum-package.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-112](../req/REQ-112-activate-builtin-packages.md)

## Acceptance Criteria

**AC1** (Package preview):

- When: I open the package list
- Then: I see "TOGAF 10 TRM" with a preview (47 ABBs, 38 TRM categories, 12 patterns)

**AC2** (Import with one click):

- When: I click "Import"
- Then: the import is executed atomically; an import log shows the result

**AC3** (Already imported package):

- When: the package has already been imported
- Then: the UI shows a badge "Already imported" and a note on conflict handling

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
