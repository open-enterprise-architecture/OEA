# US-131: Reference Chapters via {{ in the WYSIWYG Editor

**ID**: US-131
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Michael – Solution Architect, I want to reference another chapter of the same DocumentCollection via `{{` in chapter content, so that readers can navigate directly to the referenced section and the reference stays correct automatically when I rename the target chapter.

## Reference

**Use Case**: [UC-09](../use-cases/UC-09-document-solution-architecture-arc42.md)
**Persona**: Michael – Solution Architect (SH-04)
**Requirements**: [REQ-134](../req/REQ-134-document-item-cross-reference.md)

## Acceptance Criteria

**AC1** (Autocomplete trigger):
- When: Michael types `{{` in the free-text area
- Then: dropdown with all items of the current collection opens; search by alias and name possible

**AC2** (Insertion and rendering):
- When: Michael selects an item from the dropdown
- Then: clickable inline link with the `name` of the target item appears in the text

**AC3** (Automatic update on rename):
- When: Michael renames the target chapter
- Then: all cross-references to this chapter automatically show the new name

**AC4** (Deleted target):
- When: the referenced chapter is deleted
- Then: reference shows `[deleted chapter]` in red font; no broken link

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present (incl. rename and deletion of the target)
- [ ] Code review; linter green; entered in trace matrix
