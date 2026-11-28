---
id: REQ-081
title: BPMN DataObject integration and process data lineage
type: functional
priority: should
status: accepted
version: 0.2.0
use_case: UC-10
created: 2026-06-26
last_updated: 2026-11-28
author: requirements-engineer
---

# REQ-081: BPMN DataObject integration and process data lineage

## Description

BPMN processes can contain DataObjects and DataStores. Via input and output associations, tasks describe which data they read, create or enrich (`transformationType`). A data object and a data store are **symbols**: a viewpoint decides which entity type is drawn as one, for example the TOGAF "Data Object", so the same entity appears both in the process diagram and, later, in the lineage query (UC-08). The package `oea-starter-bpmn` 1.1.0 brings the default types "BPMN Data Object" and "BPMN Data Store".

Realized by the change `us-083-bpmn-data-objects` (owner decisions 2026-11-28), except **AC6** (lineage, with the lineage API and view SCR-023), **AC8** (portal canvas, with SCR-054) and the `[[` mention of AC4 (REQ-070).

## Acceptance Criteria

**AC1** (DataObject in the process diagram):

- Business analyst can drag `bpmn-data-object` and `bpmn-data-store` from the BPMN palette onto the process diagram
- DataObjects can be placed within lanes or freely at process level; a data object in a pool, lane or sub-process lies there for the layout only: its entity parent stays, no "New parent?" is asked, a new one is created without a parent, and a lane of an organizational unit or role assigns nothing to it

**AC2** (Create input/output association):

- Business analyst draws a dashed line (BPMN notation) from a DataObject to a task (input) or from the task to a DataObject (output)
- The editor offers only the fitting type: a drag from a data object or data store to a task offers the input association, from a task to a data object or data store the output association (BR-07, checked in the diagram save with 422 `DATA_ASSOCIATION_DIRECTION`); the `transformationType` (create / enrich / overwrite / delete / read-only) is set in the details panel

**AC3** (Enrichment annotation):

- Detail panel of a `bpmn-data-output-association` shows fields: `transformationType` (dropdown), `affectedAttributes` (tag input: at most 50 tags of 1 to 100 characters, stored one per line, at most 5049 characters), `condition` (free text, at most 2000 characters)
- A task may read and create or change the same data object; there is no warning (BR-08 retired, owner decision 2026-11-28)
- These properties are stored in the repository and are queryable via API

**AC4** (Reference existing data-object):

- The business analyst can place a `data-object` already present in the EA repository with "Add existing"; the `[[` trigger of REQ-070 follows with that requirement
- The BPMN element then shows the name and ID of the referenced object; no duplicate is created

**AC5** (Link DataStore with technical infrastructure):

- A data store names the element that realizes it with a "Realized By" connection to any element of the EA repository, for example an application component or system software; the viewpoint need not allow its type
- Link: context menu "Realized by…" of the data store → search in the EA repository; the target is shown as a badge, removed with "×" (`entity:delete`)

**AC6** (Lineage query through process; **open**, with the lineage API and view SCR-023):

- `GET /api/v1/entities/{id}/lineage?direction=downstream` for a `data-object` also returns BPMN tasks that enrich this object via `bpmn-data-output-association`
- Analogously upstream: tasks that read it as input

**AC7** (Catalog join on process data; realized, the associations are ordinary connections):

- A catalog with `data-object` as primaryEntityType can show via a join on `bpmn-data-output-association` which process tasks create or enrich each DataObject

**AC8** (Web portal read-only; **open**, with SCR-054):

- BPMN diagram with DataObjects is readable in the web portal; DataObject names and transformationType annotations visible

## Technical Notes

- "BPMN Data Object" with the optional boolean property "Collection" (BPMN `isCollection`) and "BPMN Data Store" as types of the package `oea-starter-bpmn` 1.1.0; no inheritance from `data-object`: the data object is a symbol of the viewpoint
- "Data Input Association" (from any element to a task) and "Data Output Association" (from a task to any element) as connection types of the package; properties on the connection object (ADR-004: all connections have integer IDs): "Transformation type", "Affected attributes" (text, one per line) and "Condition"
- Canvas display: dashed line with open arrow (BPMN notation); DataObject symbol: document with a folded corner, three bars for a collection; DataStore symbol: cylinder
- Lineage API (REQ-062): must know `bpmn-data-input-association` and `bpmn-data-output-association` as traversable edges
- BR-07 from process.md applies (arrow direction); BR-08 is retired (owner decision 2026-11-28)
