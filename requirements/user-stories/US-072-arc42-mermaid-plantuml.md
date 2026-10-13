# US-072: Embed Mermaid and PlantUML Diagrams in an Arc42 Answer

**ID**: US-072
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to write Mermaid and PlantUML code blocks in an Arc42 answer that are rendered as diagrams immediately, so that I can maintain the context view, building block view, and deployment view as diagram-as-code without opening a separate tool.

## Reference

**Use Case**: [UC-09](../use-cases/UC-09-document-solution-architecture-arc42.md)
**Persona**: Michael – Solution Architect (SH-04)
**Requirements**: [REQ-068](../req/REQ-068-arc42-wysiwyg-editor.md)

## Acceptance Criteria

**AC1** (Mermaid rendered):
- When: Michael types a ```mermaid block with a C4Context diagram
- Then: The diagram is displayed inline as SVG; no raw code

**AC2** (PlantUML rendered):
- When: Michael types a ```plantuml block with a sequence diagram
- Then: The diagram is rendered as SVG via the configured server or WASM

**AC3** (Fallback without PlantUML server):
- When: No server is configured and WASM is not available
- Then: Raw code is displayed + warning icon; no app crash

**AC4** (Code edit toggle):
- When: Michael clicks a rendered Mermaid diagram → "Edit code"
- Then: The code editor opens; saving re-renders

**AC5** (Web portal reads diagrams):
- When: The CIO opens the Arc42 tab in the web portal
- Then: Mermaid and PlantUML are rendered read-only; no editor

## Definition of Done

- [ ] AC1–AC5 fulfilled
- [ ] Tests: Mermaid rendering, PlantUML rendering, fallback, toggle, web portal
- [ ] SVG sanitized (XSS prevention verified)
- [ ] Code review; linter green; entered in trace matrix
