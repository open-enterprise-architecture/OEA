---
id: REQ-132
title: DocumentItem as the Atomic Unit of the DocumentCollection
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

# REQ-132: DocumentItem as the Atomic Unit of the DocumentCollection

## Statement

The system MUST provide `DocumentItem` as the atomic instance unit of a `DocumentCollection`. Every DocumentItem MUST carry the following fields:

| Field | Mandatory | Description |
|---|---|---|
| `name` | required | Chapter title; displayed in the chapter navigation and as a heading in the content; max. 200 characters |
| `alias` | optional | Short name for cross-references; unique within the collection (case-insensitive, kebab-case); max. 60 characters |
| `content` | required | WYSIWYG content (Markdown, Mermaid, PlantUML, Draw.io, entity mentions, DocumentItem cross-references) |
| `sortOrder` | required | Order among sibling items; ascending |

A DocumentCollection consists of an ordered list of DocumentItems. The total number of items per collection is not limited; the `content` value is limited to 100,000 characters (BR-07).

## Rationale

The previous separation of `DocumentChapterDefinition` (template) and `DocumentationEntry` (instance) is hard for users to understand. `DocumentItem` combines structure and content in one unit and allows free creation and editing of chapters without template binding.

## Acceptance Criteria

**AC1** (Create):
- When: a user creates a new chapter in a DocumentCollection
- Then: the system creates a DocumentItem with `name` (mandatory), optional `alias`, empty `content`, and automatically assigned `sortOrder`

**AC2** (Name in navigation):
- When: the DocumentCollection is opened
- Then: the chapter navigation shows the `name` of each item; items are sorted by `sortOrder`

**AC3** (Alias unique):
- When: a user sets an `alias` that is already assigned within the collection
- Then: inline error; saving prevented

**AC4** (Content WYSIWYG):
- When: a user opens a DocumentItem
- Then: the WYSIWYG editor is available; all supported formats (Markdown, Mermaid, PlantUML, Draw.io, `[[`, `{{`) are usable

## Dependencies

- **Prerequisites**: REQ-068 (WYSIWYG editor available), REQ-069 (edit documentation)
- **Consequences**: REQ-133 (nesting), REQ-134 (cross-references)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
