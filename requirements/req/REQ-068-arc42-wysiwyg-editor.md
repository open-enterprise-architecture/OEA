---
id: REQ-068
title: Arc42 WYSIWYG editor with Mermaid and PlantUML rendering
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - arc42
  stakeholders:
    - SH-04
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-068: Arc42 WYSIWYG editor with Mermaid and PlantUML rendering

## Statement

The system MUST provide a WYSIWYG editor for the `content` field of an Arc42MetaObject entity that supports Markdown formatting, `mermaid` code blocks and `plantuml` code blocks. Mermaid blocks MUST be rendered client-side as SVG. PlantUML blocks MUST be rendered as SVG either via a configured PlantUML server or via WASM-based client rendering. On a rendering error, the raw code MUST be displayed as a fallback.

## Rationale

Arc42 thrives on diagrams: context boundary, building block view, deployment view are all visual. Without diagram rendering in the editor, Arc42 in OEA would be mere prose. Mermaid and PlantUML are the two dominant diagram-as-code formats in the solution architecture field; both must be supported.

## Context

The editor is not a whiteboard canvas (that is ADR-007 / Vue Flow). It is a **rich-text editor** with code block rendering, comparable to Notion or Confluence. The diagrams are generated from text — no drag & drop.

## Type-Specific Fields

### Supported Formats

**Markdown** (CommonMark + GFM):

- Headings H1–H4, lists, tables, links, inline code, bold, italic

**Mermaid**:

- Trigger: code block with language `mermaid`
- Rendering: client-side via mermaid.js (separate dependency, independent of the canvas library in ADR-007)
- Supported types: flowchart, sequenceDiagram, classDiagram, C4Context, erDiagram, gantt, among others

**PlantUML**:

- Trigger: code block with language `plantuml`
- Rendering:
  - Primary: HTTP request to the configured PlantUML server (`/api/admin/config: plantumlServerUrl`)
  - Fallback: WASM-based rendering (plantuml-wasm, no server needed)
  - If neither is available: code as fallback display, warning icon

### Editor Behavior

- **Toggle**: Each code block can be switched between edit mode (code) and preview mode (rendering)
- **Inline preview**: Rendering appears directly below the code block in split view or via tab
- **Zoom**: Rendered SVGs are zoomable (SVG-native zoom)
- **Export**: SVG can be downloaded via right-click → "Save image"

### Storage Format

`content` is stored as a Markdown string (including code blocks). Maximum length: 100,000 characters. No pre-rendering on save.

## Acceptance Criteria

**AC1** (Mermaid rendered):

- Given: content contains:

  ````markdown
  ```mermaid
  graph LR; A-->B; B-->C;
  ```
  ````

- When: Michael opens the Arc42 answer
- Then: An SVG diagram (A→B→C) is visible in the editor; no raw code

**AC2** (PlantUML via server):

- Given: `plantumlServerUrl` configured; content with PlantUML block
- When: Editor opened
- Then: SVG loaded from the server and displayed

**AC3** (PlantUML fallback – no server):

- Given: no PlantUML server configured and WASM not loaded
- When: Editor opened
- Then: Raw code displayed; warning icon; message "PlantUML not available"

**AC4** (Edit toggle):

- When: Michael clicks on a Mermaid diagram → "Edit"
- Then: Code editor opens; changes re-rendered live

**AC5** (Save as Markdown):

- When: Michael saves
- Then: `content` field contains a Markdown string with unchanged code blocks; no SVG in DB

**AC6** (Character limit):

- When: content > 100,000 characters
- Then: Error message; saving prevented

**AC7** (Web portal rendering):

- When: CIO opens Arc42 documentation in the web portal
- Then: Mermaid and PlantUML are rendered (read-only); no editor

## Dependencies

- **Prerequisites**: REQ-067 (collections); REQ-069 (create Arc42 entity)
- **Configuration**: `plantumlServerUrl` as a new optional instance configuration (system setting)

## Implementation Notes

- Editor library: TipTap or ProseMirror (Vue 3 integration via `@tiptap/vue-3`, ADR-014); custom Mermaid/PlantUML node extension
- mermaid.js is license-compatible (MIT); plantuml-wasm is GPL → prefer the server variant or WASM only locally in Electron (client app)
- SVG rendering: always sanitized (XSS prevention on SVG content)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
