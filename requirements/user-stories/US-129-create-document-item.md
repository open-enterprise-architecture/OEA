# US-129: Create a DocumentItem with Name, Alias, and WYSIWYG Content

**ID**: US-129
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Michael – Solution Architect, I want to create chapters as DocumentItems in a DocumentCollection, each carrying a name, an optional alias, and WYSIWYG content, so that I can capture architecture documentation in a structured way directly in the EA tool.

## Reference

**Use Case**: [UC-09](../use-cases/UC-09-document-solution-architecture-arc42.md)
**Persona**: Michael – Solution Architect (SH-04)
**Requirements**: [REQ-132](../req/REQ-132-document-item-structure.md)

## Acceptance Criteria

**AC1** (Create):

- When: Michael clicks "New chapter" in a collection
- Then: new DocumentItem appears with focus on the `name` field; `content` empty; `sortOrder` automatically set to the end

**AC2** (Set alias):

- When: Michael sets an alias value that already exists in this collection
- Then: inline error; saving prevented

**AC3** (Chapter navigation):

- When: a collection with multiple items is opened
- Then: navigation shows all `name` values by `sortOrder`; active item highlighted

**AC4** (WYSIWYG content):

- When: Michael opens a chapter and inserts Markdown, Mermaid, or Draw.io
- Then: contents are rendered correctly (REQ-068)

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
