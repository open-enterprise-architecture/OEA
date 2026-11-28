# Proposal

## Why

BPMN process diagrams (changes 18 to 19) show the flow and who performs it, but not which data a task reads or changes. US-083 places data objects and data stores in a process, connects them to tasks and records what a task does with the data (REQ-081). This completes the open parts AC2 and AC3 of US-074. It is S17 change 20 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| User story | [US-083](../../../requirements/user-stories/US-083-bpmn-dataobject-enrichment.md), [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) AC2, AC3 |
| Requirement | [REQ-081](../../../requirements/req/REQ-081-bpmn-dataobject-process-lineage.md) AC1–AC5, AC7; [REQ-076](../../../requirements/req/REQ-076-bpmn-process-types-metamodel.md) |
| Use case | UC-10 |
| ADRs | [ADR-007](../../../adrs/ADR-007-canvas-library.md), [ADR-013](../../../adrs/ADR-013-api-style.md) |
| Screen | SCR-021 "Diagram editor" |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions (2026-11-28):**

- **Data object and data store are symbols, and their type is free.** As with lanes and pools, a viewpoint decides which entity type is drawn as a BPMN data object or data store, for example the TOGAF "Data Object". The BPMN package brings default types for instances without TOGAF.
- **Lineage comes later.** Lineage through processes (REQ-081 AC6) follows with the lineage API and view (SCR-023). The associations are ordinary connections that a catalog can already query (AC7).
- **"Realized By" leads to any element.** A data store can name the element that realizes it, for example an Application Component or a piece of System Software. It is set from the context menu of the data store with a repository search, like "Set participant" on pools in change 19, shown as a badge and removed with "×"; the viewpoint need not allow the target type.
- **No assignment for data in lanes.** A data object or data store in a lane of an organizational unit or role gets no assignment: it is no process step but the input or output of one.
- **BR-08 is retired.** A task may read and create or change the same data object.
- **Data associations** stay the connection between task and data object; message flows remain for the communication between pools.
- **The portal comes later.** Data objects in the read-only portal canvas (AC8) follow with SCR-054, as decided for change 19.

**Decisions and assumptions of this change (open for review):**

- **The BPMN package becomes 1.1.0.**
  - New element types "BPMN Data Object" (with the boolean property "Collection") and "BPMN Data Store".
  - New connection types:
    - "Data Input Association", from any element to "Task";
    - "Data Output Association", from "Task" to any element;
    - "Realized By", from any element to any element.
  - The open ends allow a TOGAF "Data Object" to take part without a dependency between packages. The package viewpoint is read-only, so drawing the TOGAF type as a data object needs an organization viewpoint of notation `bpmn2` that maps it.
  - The viewpoint "Process Model (BPMN 2.0)" maps the new types.
- **Update from SCR-030.** SCR-030 offers "Import" only for a package not yet imported. The starter package list gets the imported version, and SCR-030 offers "Update to 1.1.0" for an older import.
- **Direction (BR-07).**
  - In a diagram save, the source of a new input association must be drawn as a data object or a data store, and so must the target of a new output association.
  - The source of a new "Realized By" must be drawn as a data store in a BPMN diagram.
  - Otherwise the save answers 422 `DATA_ASSOCIATION_DIRECTION`. Existing connections and batch writes stay unchecked, as with the org unit limit of change 19.
- **Transformation.** "Data Output Association" carries these properties:
  - "Transformation type", an enum of create, enrich, overwrite, delete and read-only;
  - "Affected attributes", a text of at most 5049 characters with one attribute per line, which the details panel edits as at most 50 tags of 1 to 100 characters;
  - "Condition", a text of at most 2000 characters.
- **Collection marker.** The view carries the "Collection" value of each data object shape, so that the canvas can draw the marker.
- **"Realized by" without an edge.** The save path of change 19 for connections without an edge accepts "Realized By" from a shape drawn as data store, recognized by the package ID and its subtypes; `removedConnections` removes it with `entity:delete`. The view lists the realizing elements of a data store as `realizedBy`, and the assignment search gets `part=realizer`.
- **Data objects do not nest.**
  - A shape drawn as a data object or a data store keeps its entity parent when it is placed in a pool, lane or sub-process. No "New parent?" is asked, a new one is created without a parent, and the server rejects a parent change for it with 422 `DATA_OBJECT_PARENT`.
  - It gets no assignment from a lane of an organizational unit or role.
  - This keeps a TOGAF data object out of the process hierarchy.
- **Canvas.**
  - The data object is drawn as a document, with three bars when "Collection" is set.
  - The data store is drawn as a cylinder.
  - Data associations are dashed lines with an open arrow; the new notation keys are `bpmn:DataInputAssociation` and `bpmn:DataOutputAssociation`.
  - Dragging from a data object to a task offers the input association, and from a task to a data object the output association.
  - "Realized By" can also be drawn as an edge from a data store to a shape on the canvas.
- **Existing data objects** are placed with "Add existing", so no duplicate is created (REQ-081 AC4). The `[[` mention of REQ-070 is not part of this change.

**Not in this change:**

- lineage (AC6);
- the portal canvas (AC8);
- the `[[` mention;
- data inputs and outputs of the process itself (BPMN `dataInput` and `dataOutput`).

## Capabilities

### New Capabilities

- `diagram/bpmn-data-objects`: data objects, data stores and data associations on the canvas, their direction and transformation.

### Modified Capabilities

- `metamodel/starter-packages`: the BPMN starter package 1.1.0 and its update from SCR-030.
- `diagram/bpmn-process-diagrams`: the palette group "Data" and data shapes as non-nesting shapes in containers.
- `diagram/bpmn-assignments`: data shapes in assignment lanes get no assignment, and "Realized By" of a data store as a connection without an edge.

## Impact

- **Contract:**
  - the 422 issue codes `DATA_ASSOCIATION_DIRECTION` and `DATA_OBJECT_PARENT` of `saveDiagramView`;
  - `DiagramShape.collection` and `DiagramAssignments.realizedBy` in the view;
  - `part=realizer` of `searchDiagramReferenceTargets`, `DATA_ASSOCIATION_DIRECTION` also for "Realized By" without an edge, and "Realized By" in `removedConnections`;
  - `StarterPackage.importedVersion`;
  - no new operation.
- **Database:** none. The package and the notation catalog are resources; the imported version is read from `metamodel_package_imports`.
- **Backend:**
  - the package 1.1.0;
  - the two notation keys;
  - the direction check of the save;
  - the parent rule and the assignment rule for data object shapes;
  - "Realized By" without an edge, its search and its removal;
  - the two view fields and the imported version.
- **Frontend:**
  - the symbols and the dashed lines;
  - the offered connection type by direction;
  - the tag input for "Affected attributes";
  - "Realized by…" with search, badge and "×";
  - no nesting of data objects;
  - "Update" on SCR-030.
- **Documentation:**
  - the manual pages "Diagrams" and "Metamodel packages" (en/de);
  - SCREENS.md (SCR-021, SCR-030);
  - the business object process (BR-07 realized, BR-08 retired);
  - REQ-081, US-083, US-074 and UC-10.
