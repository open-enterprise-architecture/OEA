---
id: US-083
title: Place and Enrich a DataObject in a BPMN Process
use_case: UC-10
requirement: REQ-081
priority: should
status: in-progress
story_points: 8
created: 2026-06-26
---

# US-083: Place and Enrich a DataObject in a BPMN Process

**As** a Business Analyst (Anna, SH-08)  
**I want** to place DataObjects in a BPMN process diagram and connect them to tasks, and annotate what a task does with the data object,  
**so that** the data flows through the business process are traceable and clean data lineage emerges.

## Acceptance Criteria

- [x] The BPMN palette contains `bpmn-data-object` (document icon) and `bpmn-data-store` (cylinder icon)
- [x] An existing `data-object` from the EA repository can be placed with "Add existing" and drawn as a data object by the viewpoint, without a duplicate (the `[[` trigger of REQ-070 follows with that requirement)
- [x] Dashed line from DataObject to task = input association; from task to DataObject = output association
- [x] Detail panel of the output association: dropdown `transformationType` (create / enrich / overwrite / delete / read-only) and tag field `affectedAttributes`
- [x] `bpmn-data-store` can be linked via context menu "Realized by…" to any element from the repository (owner decision 2026-11-28)
- [ ] `GET /api/v1/entities/{id}/lineage` for a data-object returns tasks that read or enrich it (open, with the lineage API and view SCR-023)
- [x] A catalog on `data-object` can show via join on `bpmn-data-output-association` which tasks enrich each object
- [x] BR-07 from process.md is validated by the API (arrow direction); BR-08 is retired by owner decision 2026-11-28: a task may read and create or change the same data object

## Scenario

Anna models the procurement process:

1. Drags "Order" (existing `data-object` #15) onto the canvas — no duplicate
2. Draws a dashed line from "Order" to task "Review order" → input association is created
3. Draws a dashed line from task "Review order" to "Order" → output association; detail panel opens
4. Selects `transformationType: enrich`, enters `affectedAttributes: [status, reviewedBy]`
5. Query: `GET /api/v1/entities/15/lineage?direction=downstream` → shows task "Review order" as writer

## Implementation

OpenSpec change `us-083-bpmn-data-objects` (S17, owner decisions 2026-11-28, SCR-021 and SCR-030) – the BPMN starter package `oea-starter-bpmn` 1.1.0 with the types "BPMN Data Object" (property "Collection") and "BPMN Data Store", the connection types "Data Input Association" (from any element to a task), "Data Output Association" (from a task to any element; "Transformation type", "Affected attributes", "Condition") and "Realized By" (any element to any element), and the notation keys `bpmn:DataInputAssociation` and `bpmn:DataOutputAssociation`; on the canvas the document and cylinder symbols (collection marker from `DiagramShape.collection`), the dashed lines with an open arrow offered only in their direction, the tag input of the attributes, "Realized by…" on a data store (`part=realizer`, `DiagramAssignments.realizedBy`, a connection without an edge) and data shapes that do not nest and get no lane assignment; the diagram save checks the direction (422 `DATA_ASSOCIATION_DIRECTION`, 422 `DATA_OBJECT_PARENT`); SCR-030 offers "Update to <version>" (`StarterPackage.importedVersion`). The lineage criterion follows with the lineage API and view (SCR-023); the portal canvas (REQ-081 AC8) with SCR-054.

## Technical Notes

- A data object is a symbol: a viewpoint draws any element type, the TOGAF `data-object` for example, as `bpmn:DataObject` (integer ID space shared, the lineage API will traverse them); the package brings the default types "BPMN Data Object" and "BPMN Data Store"
- Connection properties on `bpmn-data-output-association`: `transformationType`, `affectedAttributes` (text, one attribute per line, at most 5049 characters), `condition` (at most 2000 characters)
- Canvas: dashed line with arrowhead for associations (unlike SequenceFlow = solid line)
