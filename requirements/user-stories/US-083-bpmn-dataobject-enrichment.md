---
id: US-083
title: Place and Enrich a DataObject in a BPMN Process
use_case: UC-10
requirement: REQ-081
priority: should
status: proposed
story_points: 8
created: 2026-06-26
---

# US-083: Place and Enrich a DataObject in a BPMN Process

**As** a Business Analyst (Anna, SH-08)  
**I want** to place DataObjects in a BPMN process diagram and connect them to tasks, and annotate what a task does with the data object,  
**so that** the data flows through the business process are traceable and clean data lineage emerges.

## Acceptance Criteria

- [ ] The BPMN palette contains `bpmn-data-object` (document icon) and `bpmn-data-store` (cylinder icon)
- [ ] When creating a DataObject, an existing `data-object` from the EA repository can be referenced (`[[` trigger, REQ-070)
- [ ] Dashed line from DataObject to task = input association; from task to DataObject = output association
- [ ] Detail panel of the output association: dropdown `transformationType` (create / enrich / overwrite / delete / read-only) and tag field `affectedAttributes`
- [ ] `bpmn-data-store` can be linked via context menu to a `data-component` from the repository
- [ ] `GET /api/v1/entities/{id}/lineage` for a data-object returns tasks that read or enrich it
- [ ] A catalog on `data-object` can show via join on `bpmn-data-output-association` which tasks enrich each object
- [ ] BR-07/08 from process.md are validated by the API (arrow direction, create constraint)

## Scenario

Anna models the procurement process:

1. Drags "Order" (existing `data-object` #15) onto the canvas — no duplicate
2. Draws a dashed line from "Order" to task "Review order" → input association is created
3. Draws a dashed line from task "Review order" to "Order" → output association; detail panel opens
4. Selects `transformationType: enrich`, enters `affectedAttributes: [status, reviewedBy]`
5. Query: `GET /api/v1/entities/15/lineage?direction=downstream` → shows task "Review order" as writer

## Technical Notes

- `bpmn-data-object` extends `data-object` (integer ID space shared, lineage API traverses both)
- Connection properties on `bpmn-data-output-association`: `transformationType`, `affectedAttributes` (array), `condition`
- Canvas: dashed line with arrowhead for associations (unlike SequenceFlow = solid line)
