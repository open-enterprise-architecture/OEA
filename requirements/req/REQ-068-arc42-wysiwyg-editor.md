---
id: REQ-068
title: Arc42 WYSIWYG editor with Mermaid and PlantUML rendering
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-10-13
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

The system MUST provide a WYSIWYG editor for the `content` field of an Arc42MetaObject entity that supports Markdown formatting, `mermaid` code blocks and `plantuml` code blocks. Mermaid blocks MUST be rendered client-side as SVG with Mermaid's strict security level and without honoring init directives. PlantUML blocks MUST be rendered as SVG either via WASM-based client rendering or via an operator-hosted PlantUML server; by default no diagram source leaves the instance, and an external PlantUML server MUST only be used if the operator configures it explicitly. All rendered output MUST pass the allowlist sanitizer of REQ-180. On a rendering error, the raw code MUST be displayed as a fallback.

## Rationale

Arc42 thrives on diagrams: context boundary, building block view, deployment view are all visual. Without diagram rendering in the editor, Arc42 in OEA would be mere prose. Mermaid and PlantUML are the two dominant diagram-as-code formats in the solution architecture field; both must be supported.

## Context

The editor is not a whiteboard canvas (that is ADR-007 / Vue Flow). It is a **rich-text editor** with code block rendering, comparable to Notion or Confluence. The diagrams are generated from text — no drag & drop.

Security review SEC-18 (2026-10-13) and owner decision Q9: Markdown links, Mermaid `click` callbacks and init directives, and PlantUML SVG output are stored-XSS vectors in the web portal and the Electron client; a public PlantUML server receives confidential diagram sources; `!include` of URLs on a server is an SSRF vector. Content does not leave the instance for rendering unless the operator configures an external service explicitly.

## Type-Specific Fields

### Supported Formats

**Markdown** (CommonMark + GFM):

- Headings H1–H4, lists, tables, links, inline code, bold, italic
- Raw HTML is not rendered; links are only active for the schemes `https`, `http` and `mailto` (no `javascript:`, `data:` or `vbscript:`)

**Mermaid**:

- Trigger: code block with language `mermaid`
- Rendering: client-side via mermaid.js (separate dependency, independent of the canvas library in ADR-007), security level `strict`, init directives (`%%{init: ...}%%`) ignored, `click` callbacks inert
- Supported types: flowchart, sequenceDiagram, classDiagram, C4Context, erDiagram, gantt, among others

**PlantUML**:

- Trigger: code block with language `plantuml`
- Rendering:
  - Default: local rendering without a network call (WASM-based client rendering, or a PlantUML server shipped with and operated inside the instance's deployment)
  - Optional: HTTP request to a PlantUML server set by the operator (`plantumlServerUrl`); no public server is preconfigured; the server must run with a security profile that disables `!include` of URLs and file access; outbound calls follow REQ-178
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

**AC2** (PlantUML via operator-configured server):

- Given: `plantumlServerUrl` explicitly configured by the operator; content with PlantUML block
- When: Editor opened
- Then: SVG loaded from that server, sanitized and displayed

**AC3** (PlantUML fallback – no renderer):

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

**AC8** (No external rendering by default):

- Given: a fresh installation without `plantumlServerUrl`
- When: content with Mermaid and PlantUML blocks is rendered in the Electron client and the web portal
- Then: no network request with diagram source is sent to any host outside the instance

**AC9** (Mermaid hardening):

- Given: a Mermaid block with `%%{init: {"securityLevel":"loose"}}%%` and `click A href "javascript:alert(1)"`
- When: it is rendered
- Then: the init directive is ignored, the link is inert and no script executes

**AC10** (Markdown links):

- Given: content with `[x](javascript:alert(1))` and raw `<img src=x onerror=alert(1)>`
- When: it is rendered in the editor and the web portal
- Then: no script executes; the link is not clickable and the HTML is shown as text or removed

## Dependencies

- **Prerequisites**: REQ-067 (collections); REQ-069 (create Arc42 entity); REQ-180 (safe rendering, CSP)
- **Related**: REQ-178 (outbound request controls for an operator-configured PlantUML server)
- **Configuration**: `plantumlServerUrl` as an optional instance configuration (system setting); empty by default

## Implementation Notes

- Editor library: TipTap or ProseMirror (Vue 3 integration via `@tiptap/vue-3`, ADR-014); custom Mermaid/PlantUML node extension
- mermaid.js is license-compatible (MIT); plantuml-wasm is GPL → license fit of a shipped renderer must be checked (local WASM in Electron, or a PlantUML server container in the deployment)
- SVG rendering: always sanitized with an allowlist sanitizer (REQ-180), not a deny list

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-18 / owner decision Q9: Mermaid strict mode without init directives, safe Markdown links, local PlantUML rendering by default, external server only if configured by the operator (AC8–AC10) |
