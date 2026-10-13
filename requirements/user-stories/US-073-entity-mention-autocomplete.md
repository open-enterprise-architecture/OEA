# US-073: Embed an Entity via /@ in the Arc42 Editor

**ID**: US-073
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to embed an entity from the repository in the Arc42 editor by typing `/@` and then searching, so that my documentation references the model directly and renames are picked up automatically.

## Reference

**Use Case**: [UC-09](../use-cases/UC-09-document-solution-architecture-arc42.md)
**Persona**: Michael – Solution Architect (SH-04)
**Requirements**: [REQ-070](../req/REQ-070-entity-mention-autocomplete.md)

## Acceptance Criteria

**AC1** (Dropdown in free text):

- When: Michael types `/@CRM` in the Markdown area
- Then: An autocomplete dropdown with matching entities appears

**AC2** (Selection → ID-stable link):

- When: Michael selects "CRM-System" (id=1)
- Then: `[@CRM-System](entity:1)` is inserted; rendered as a clickable badge

**AC3** (Rename automatic):

- Given: Entity id=1 is now named "Salesforce CRM"
- When: Michael reopens the answer
- Then: The badge shows "Salesforce CRM"; raw text unchanged

**AC4** (Dropdown in Mermaid block):

- When: Michael types `/@CRM` inside a Mermaid code block
- Then: The dropdown appears; after selection the text name `CRM-System` is inserted without a link

**AC5** (PlantUML block analogous to AC4):

- When: `/@CRM` in a PlantUML block
- Then: The text name is inserted

**AC6** (Deleted entity):

- Given: Entity id=1 deleted
- When: Answer with mention is opened
- Then: The badge shows `[deleted]` in red font

## Technical Notes

- `GET /api/v1/entities/search?q=CRM&limit=10` for live search (debounce 150 ms)
- Batch resolution on open: `POST /api/v1/entities/batch?ids=[...]`
- TipTap MentionExtension; code block detection for text-only insertion

## Definition of Done

- [ ] AC1–AC6 fulfilled
- [ ] Tests: dropdown trigger, selection, ID resolution, rename, delete, code block mode
- [ ] E2E test (Playwright) for free-text and Mermaid flow
- [ ] XSS prevention on entity names verified
- [ ] Code review; linter green; entered in trace matrix
