---
id: US-079
title: Export and Import a Viewpoint
use_case: UC-04
requirement: REQ-059
priority: should
status: proposed
story_points: 3
created: 2026-06-26
---

# US-079: Export and Import a Viewpoint

**As** a Lead Enterprise Architect (Kurt, SH-03)  
**I want** to export individual viewpoints as JSON files and import them into other OEA instances,  
**so that** organizational standards for diagram views (e.g. standard landscape map (Bebauungsplan) view) can be shared.

## Acceptance Criteria

- [ ] A single viewpoint can be exported via the context menu in the viewpoint manager
- [ ] Export contains: viewpoint metadata, allowedEntityTypes, allowedConnectionTypes, NotationMappings
- [ ] Import via `POST /api/v1/metamodel/viewpoints/import` (JSON upload)
- [ ] On name conflict: the user chooses between overwrite, rename, or cancel
- [ ] All built-in viewpoints (bpmn-process-view etc.) are exportable but read-only on import (scope=built-in is preserved)

## Technical Notes

- `GET /api/v1/metamodel/viewpoints/{id}/export`
- `POST /api/v1/metamodel/viewpoints/import`
- Consistent with metamodel export (US-078): same package format (ADR-002)
