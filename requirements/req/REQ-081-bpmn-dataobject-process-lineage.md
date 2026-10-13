---
id: REQ-081
title: BPMN DataObject integration and process data lineage
type: functional
priority: should
status: proposed
version: 0.1.0
use_case: UC-10
created: 2026-06-26
author: requirements-engineer
---

# REQ-081: BPMN DataObject integration and process data lineage

## Description

BPMN processes can contain DataObjects and DataStores. Via input and output associations, tasks describe which data they read, create or enrich (`transformationType`). Since `bpmn-data-object` is modeled as a subtype of `data-object` (entity.md), a seamless connection to the technical data lineage (UC-08) arises: the same entity appears both in the process diagram and in the lineage query.

## Acceptance Criteria

**AC1** (DataObject in the process diagram):
- Business analyst can drag `bpmn-data-object` and `bpmn-data-store` from the BPMN palette onto the process diagram
- DataObjects can be placed within lanes or freely at process level

**AC2** (Create input/output association):
- Business analyst draws a dashed line (BPMN notation) from a DataObject to a task (input) or from the task to a DataObject (output)
- On creation, the UI asks: direction (input / output) and optionally `transformationType` (create / enrich / overwrite / delete / read-only)

**AC3** (Enrichment annotation):
- Detail panel of a `bpmn-data-output-association` shows fields: `transformationType` (dropdown), `affectedAttributes` (tag input), `condition` (free text)
- These properties are stored in the repository and are queryable via API

**AC4** (Reference existing data-object):
- When creating a `bpmn-data-object`, the business analyst can select a `data-object` already present in the EA repository (autocomplete via `[[` trigger, REQ-070)
- The BPMN element then shows the name and ID of the referenced object; no duplicate is created

**AC5** (Link DataStore with technical infrastructure):
- `bpmn-data-store` can be linked via a `realizes` connection with a `data-component` from the EA repository
- Link: context menu "Realized by" → search in the EA repository

**AC6** (Lineage query through process):
- `GET /api/v1/entities/{id}/lineage?direction=downstream` for a `data-object` also returns BPMN tasks that enrich this object via `bpmn-data-output-association`
- Analogously upstream: tasks that read it as input

**AC7** (Catalog join on process data):
- A catalog with `data-object` as primaryEntityType can show via a join on `bpmn-data-output-association` which process tasks create or enrich each DataObject

**AC8** (Web portal read-only):
- BPMN diagram with DataObjects is readable in the web portal; DataObject names and transformationType annotations visible

## Technical Notes

- `bpmn-data-object` as EntityTypeDefinition with `extends: data-object`, `isBuiltIn: true`; property `isCollection: boolean` (BPMN standard)
- `bpmn-data-store` as its own EntityTypeDefinition; no inheritance from `data-object` (semantically different)
- `bpmn-data-input-association` and `bpmn-data-output-association` as built-in connections; properties on the connection object (ADR-004: all connections have integer IDs)
- Canvas display: dashed line with filled/open arrow (BPMN notation); DataObject symbol: unfolded document icon; DataStore symbol: cylinder
- Lineage API (REQ-062): must know `bpmn-data-input-association` and `bpmn-data-output-association` as traversable edges
- BR-07/BR-08 from process.md apply (arrow direction, create constraint)
