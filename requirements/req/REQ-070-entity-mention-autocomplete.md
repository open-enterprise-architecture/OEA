---
id: REQ-070
title: Entity mention via [[ in the WYSIWYG editor (autocomplete + linking)
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - arc42
    - entity
  business_rules:
    - arc42:BR-07
    - arc42:BR-08
  stakeholders:
    - SH-04
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-070: Entity mention via [[ in the WYSIWYG editor (autocomplete + linking)

## Statement

The system MUST, everywhere in the WYSIWYG editor — in Markdown free-text sections, in Mermaid/PlantUML code blocks and in Draw.io shape labels — open an autocomplete dropdown when `[[` is typed, which live-searches entities from the architecture repository. After an entity is selected, the system MUST insert the mention context-dependently: as a structured wiki link `[[Name|entity:ID]]` in free text, as a plain text name in all code block contexts. When rendering free-text mentions, the entity's current name MUST be resolved via ID.

## Rationale

Arc42 documentation describes systems that are modeled in the EA repository. Without `[[` linking, names are maintained twice: the architect writes "CRM-System" as text, while the model knows the entity with id=1. On renaming, documentation and model diverge. ID-stable linking in free text ensures that renamings in the repository are automatically carried over into all Arc42 documents. In code blocks this is technically not possible (diagram languages have no link concept), so a plain text name is used there as a pragmatic solution.

## Context

`[[` (double square bracket) is the trigger for entity mentions. This syntax is known from wiki tools (Obsidian, Confluence, Logseq, Roam Research) as "link to a page/object". It is unambiguous in every context:

- Not in URLs (URLs contain no `[[`)
- Not in email addresses
- Not in Mermaid/PlantUML syntax
- Not in Draw.io XML
- Not in standard Markdown syntax

The previously evaluated `/@` pattern was discarded: `/@` appears in GitHub routing URLs and has cognitive overlap with OEA slash commands.

## Type-Specific Fields

### Autocomplete Behavior

| Phase | Behavior |
|---|---|
| User types `[[` | Dropdown opens immediately; shows top 10 entities by relevance (recently used / alphabetical) |
| User keeps typing (e.g., `[[CRM`) | Live search against `GET /api/v1/entities/search?q=CRM&limit=10`; result is filtered |
| User selects entity | Mention is inserted; `[[CRM` is replaced by the mention; dropdown closes |
| User presses ESC | Dropdown closes; `[[` text remains as raw text |

### Search Request and Response

```http
GET /api/v1/entities/search?q={term}&limit=10
```

Response:

```json
[
  { "id": 1, "name": "CRM-System", "entityTypeId": "application-component", "entityTypeName": "ApplicationComponent" },
  { "id": 5, "name": "ERP→CRM Customer master data sync", "entityTypeId": "data-flow", "entityTypeName": "DataFlow" }
]
```

- Search: case-insensitive, prefix match on `name`; optionally fuzzy match
- Scope: all ArchitectureEntities of the current instance (elements and connections)
- Sorting: exact match before prefix match before fuzzy match; alphabetical on ties

### Dropdown Display

Each result shows:

- Entity name (bold)
- EntityType name (gray, small) — e.g., "ApplicationComponent"
- Entity ID (right, monospace) — e.g., "#1"

### Insertion Format per Context

**Markdown free text** (outside code blocks):

```markdown
[[CRM-System|entity:1]]
```

Rendering: inline badge with the current name (ID-resolved), clickable → opens entity detail. If the entity no longer exists: `[[deleted|entity:1]]` in red font.

**Mermaid code block** (inside ` ```mermaid `):

```text
CRM-System
```

Plain text name — no link markup, since Mermaid syntax does not support hyperlinks in node labels.

**PlantUML code block** (inside ` ```plantuml `):

```text
CRM-System
```

Plain text name — analogous to Mermaid.

**Draw.io code block** (inside ` ```drawio `):

```text
CRM-System
```

Plain text name in the shape label of the active shape in the Draw.io editor — no link markup, since the Draw.io XML format does not know OEA-specific link concepts (v1.0).

### Rendering of `[[Name|entity:ID]]` in Free Text

When reading the `content` field, the backend (or the frontend via API call) MUST reload the current `name` of the entity with the given ID:

- Entity exists + name unchanged: normal badge with current name
- Entity exists + renamed: badge shows new name (transparent to the reader)
- Entity deleted: badge shows `[deleted]` + red font + no link

## Acceptance Criteria

**AC1** (Dropdown opens in free text):

- When: Michael types `[[` in the Markdown free text of the editor
- Then: Autocomplete dropdown appears with top 10 entities; no page load needed

**AC2** (Live search filters results):

- When: Michael types `[[CRM`
- Then: Dropdown shows only entities whose name contains "CRM" (case-insensitive); results update with every additional character

**AC3** (Mention in free text — ID-stable):

- When: Michael selects "CRM-System" (id=1) in the dropdown
- Then: `[[CRM-System|entity:1]]` is inserted; rendering shows a clickable badge "CRM-System"

**AC4** (Renaming is reflected in rendering):

- Given: Entity id=1 is renamed from "CRM-System" to "Salesforce CRM"
- When: Michael reopens the Arc42 answer
- Then: Badge shows "Salesforce CRM"; raw text in `content` remains `[[CRM-System|entity:1]]` (unchanged raw value)

**AC5** (Deleted entity):

- Given: Entity id=1 was deleted
- When: Arc42 answer with `[[CRM-System|entity:1]]` opened
- Then: Badge shows `[deleted]` in red font; no link; no exception

**AC6** (Dropdown in Mermaid block):

- When: Michael types `[[CRM` inside a ` ```mermaid ` code block (in edit mode)
- Then: Autocomplete dropdown appears; after selection only the text name `CRM-System` is inserted (no link markup)

**AC7** (Dropdown in PlantUML block):

- Same as AC6, for a PlantUML code block

**AC8** (ESC closes dropdown without change):

- When: Michael types `[[CR` and presses ESC
- Then: Dropdown closes; `[[CR` remains as raw text

**AC9** (Web portal: mentions rendered, no edit):

- When: CIO opens Arc42 documentation in the web portal
- Then: Mentions rendered as badges (with current name); no dropdown trigger when clicking a badge in read mode (badge opens entity detail)

## Dependencies

- **Prerequisites**: REQ-068 (WYSIWYG editor exists); REQ-069 (Arc42 tab)
- **New backend endpoint**: `GET /api/v1/entities/search?q=&limit=` (also usable for other features, e.g., UC-05 entity selection in deltas)
- **Rendering resolution**: Either server-side (API already delivers resolved names) or client-side (frontend resolves via ID batch request)

## Implementation Notes

- Editor extension: TipTap MentionExtension (compatible); trigger: `[[`; custom SuggestionList component
- Entity search debounce: 150 ms after the last keystroke; max. 10 results
- In code blocks: the editor extension must detect whether the cursor is inside a code block → then text-only insertion instead of link node; the TipTap code block node knows this information
- Batch ID resolution on opening: `POST /api/v1/entities/batch?ids=[1,5,42]` — individual calls per mention would be too many requests
- XSS: entity names are HTML-escaped before insertion into the DOM

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-06-26 | requirements-engineer | Trigger changed from `/@` to `[[`; storage format `[[Name\|entity:ID]]`; Draw.io code block added as a further context |
