---
id: US-077
title: Embed a Draw.io Diagram in Arc42 Documentation
use_case: UC-09
requirement: REQ-078
priority: should
status: proposed
story_points: 8
created: 2026-06-26
---

# US-077: Embed a Draw.io Diagram in Arc42 Documentation

**As** a Solution Architect (Michael, SH-04)  
**I want** to embed a Draw.io diagram in an Arc42 documentation answer and edit it directly in the tool,  
**so that** I can maintain detailed component diagrams and deployment topologies directly in the architecture documentation without a screenshot workaround.

## Acceptance Criteria

- [ ] The Arc42 WYSIWYG editor offers the code block type `drawio` (via `/` slash command or toolbar dropdown)
- [ ] An empty `drawio` block shows a placeholder with an "Open in Draw.io" button
- [ ] Clicking "Open in Draw.io" opens the draw.io editor modal (embed.diagrams.net or self-hosted)
- [ ] After saving in the draw.io editor, the XML is stored in the block and rendered as SVG
- [ ] The SVG preview in read view is correct and visible without XML text
- [ ] Typing `[[` in a shape label in the draw.io editor opens entity autocomplete; selection inserts the entity name as plain text
- [ ] Draw.io XML is sanitized before rendering (no script tags, no javascript: URIs) — AC6 REQ-078
- [ ] Web portal: SVG visible read-only; no editor button
- [ ] Configurable `drawioEditorUrl` (env/MetamodelConfiguration) for air-gapped deployments

## Technical Notes

- draw.io embed API: `postMessage` protocol; TipTap CodeBlock with language `drawio`
- Viewer: `draw.io-viewer-static.min.js` (shipped locally)
- Sanitizing: DOMParser → whitelist traversal before rendering and before saving

## Out of Scope

- Automatic synchronization of shape labels with OEA entity names on rename (v2.0)
- Import of existing `.drawio` files as file upload (separate feature)
