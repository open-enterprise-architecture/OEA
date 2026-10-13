---
id: REQ-134
title: DocumentItem Cross-Reference via {{ in the WYSIWYG Editor
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - document-collection
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-134: DocumentItem Cross-Reference via {{ in the WYSIWYG Editor

## Statement

In the WYSIWYG editor of DocumentItems, the system MUST recognize typing `{{` as the trigger for an autocomplete dropdown that searches DocumentItems of the **current DocumentCollection**. The search covers both `alias` and `name`. After selection, an ID-stable cross-reference is inserted.

**Storage format (free text):** `{{Chapter name|item:uuid}}`

**Rendering:** When rendering, the system resolves the current `name` of the referenced DocumentItem via UUID and displays it as a clickable inline link. If the target item is renamed, the display updates automatically. If the target item is deleted, the reference shows `[deleted chapter|item:uuid]` in red text.

**Scope:** Cross-references are limited to items of the same `DocumentCollection` (BR-15). Cross-collection references are not supported.

**In code blocks** (Mermaid, PlantUML, Draw.io): `{{` also triggers autocomplete; the plain `name` text is stored without ID bracketing; no automatic resolution on renaming.

## Rationale

Without cross-references, authors have to type chapter names manually — renamings produce orphaned text references. The `{{` mechanism (analogous to `[[` for entities) ensures that chapter references remain consistent even when the documentation structure is reorganized.

## Acceptance Criteria

**AC1** (Autocomplete trigger):
- When: the user types `{{` in the free-text area
- Then: a dropdown opens with all DocumentItems of the current collection; search via `alias` and `name`; first result preselected

**AC2** (Insert):
- When: the user selects a DocumentItem from the dropdown
- Then: `{{Chapter name|item:uuid}}` is inserted at the cursor position; the dropdown closes

**AC3** (Rendering — existing name):
- When: a DocumentItem with a cross-reference is rendered and the target exists
- Then: a clickable inline link with the current `name` of the target item is displayed; clicking navigates to the target chapter

**AC4** (Rendering — renamed target):
- When: the target item is renamed
- Then: all cross-references to this item automatically show the new name (ID resolution)

**AC5** (Rendering — deleted target):
- When: the target item is deleted
- Then: the reference shows `[deleted chapter|item:uuid]` in red text; no navigation possible

**AC6** (Code block):
- When: the user types `{{` in a Mermaid or PlantUML block and makes a selection
- Then: plain `name` text is stored (no `|item:uuid`); no link in the rendering

## Dependencies

- **Prerequisites**: REQ-132 (DocumentItem with `alias`), REQ-070 (entity mention mechanism as a template)
- **Consequences**: –

## Implementation Notes

- Implementation analogous to REQ-070 (`[[` trigger for entities): same editor extension mechanism (Tiptap/ProseMirror), different trigger character and different data source
- Autocomplete source: `GET /api/v1/collections/{collectionId}/items?search=<query>` — returns `id`, `name`, `alias`
- Rendering: custom node in Tiptap; `name` is resolved via the API on every render (cached, TTL 60 s)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
