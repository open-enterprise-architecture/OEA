# Proposal

## Why

US-074 asks for process diagrams with pools, lanes, tasks, events and gateways (UC-10). The diagram editor now supports shapes, connections, nesting and viewpoint changes, but it draws every element as a generic box, and no metamodel content for BPMN exists. On 2026-11-27 the owner decided how BPMN arrives:

- the BPMN types come in a starter package of their own, which can be imported in addition to the TOGAF starter package;
- it contains every type of REQ-076, except the data objects (change 20) and the organizational units and lane assignments (change 19);
- lanes in a pool stack automatically;
- breaking the pool rules of sequence and message flows is marked with a warning, and the diagram is saved anyway.

This is S17 change 18 ([sprint plan](../../../docs/sprint-plan.md)). It brings the package, the BPMN symbols and the flow warnings. The automatic stacking of lanes and the collapsing of sub-processes (REQ-076 AC8) follow with change 18b, `us-074-bpmn-pools-and-lanes`.

## Traceability

| Artifact | Reference |
|---|---|
| User story | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) |
| Requirement | [REQ-076](../../../requirements/req/REQ-076-bpmn-process-types-metamodel.md) |
| Use cases | UC-10, UC-12 |
| ADRs | [ADR-005](../../../adrs/ADR-005-app-vs-tech-default.md), [ADR-007](../../../adrs/ADR-007-canvas-library.md), [ADR-013](../../../adrs/ADR-013-api-style.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Screens | SCR-021 "Diagram editor", SCR-030 "Metamodel configuration" |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions (2026-11-27):**

- **Separate starter package.** The package is `oea-starter-bpmn`, named "BPMN 2.0 process modeling". It brings the BPMN metatypes, connection types and the viewpoint "Process Model (BPMN 2.0)". It is importable on its own or next to `oea-starter-togaf-classic`. Its types are imported types like any other, read-only and extensible by subtypes. REQ-076's "built-in and immutable" is realigned to this.
- **Scope.** All types of REQ-076 except data (change 20) and the organizational unit with its assignment connections (change 19).
- **Lanes stack automatically.** This follows with change 18b.
- **Pool rules of flows warn and do not reject.**
  - A sequence flow that crosses a pool boundary is marked with a warning.
  - So is a message flow within one pool.
  - The save is accepted.
  - BR-01 stays strict: the endpoint rules of the connection types reject a flow to a type that is not BPMN.

**Decisions and assumptions of this change (open for review):**

- **Containment is the entity hierarchy.** It replaces the connections `bpmn-contained-in`, `bpmn-lane-in-pool` and `bpmn-lane-in-lane` of REQ-076. A lane is a child of its pool or its parent lane, and a flow element is a child of its lane, pool or sub-process (change 16). The package marks no composition, because only one composition is allowed per instance. When the TOGAF "Composition" exists, the hierarchy creates it as before.
- **Base types.** The task variants are subtypes of "Task", "Message Start Event" and "Timer Start Event" are subtypes of "Start Event", and "Message End Event" and "Terminate End Event" are subtypes of "End Event". Endpoint rules then cover company subtypes such as "Approval Task". A subtype appears in the palette once the viewpoint allows it, because the exact-metatype rule of the viewpoints stays.
- **Element catalog.** The catalog gets one key per BPMN type, for example `bpmn:UserTask`, `bpmn:ExclusiveGateway`, `bpmn:MessageStartEvent`, `bpmn:TextAnnotation` and `bpmn:Group`. The existing keys stay.
- **BPMN symbols.** In a viewpoint with the notation `bpmn2`, the canvas draws the symbol of each shape's notation element and the line style of each edge's element. Other notations keep the box.
- **Grouped palette.** In a `bpmn2` viewpoint the palette is grouped into "Pools and lanes", "Activities", "Events", "Gateways", "Artifacts" and "Other".
- **Containers on a BPMN canvas.** Only shapes drawn as pool, lane or sub-process take nested shapes on the canvas, and only they offer "Show children".
- **Flow warnings** are computed in the editor from the layout and the mappings. They are not stored and do not block the save.
- **Starter packages on SCR-030.** A section lists the shipped starter packages with "Import", so that BPMN can be added to an instance that already has metatypes.
- **Labels.** The package translates its metatypes and properties into German. The viewpoint keeps its English name, because labels of viewpoints do not exist yet.
- **No architecture layer.** The package brings none, because the name "Business" would clash with the layer of the TOGAF package.

**Not in this change:**

- the stacking of lanes and the collapsing of sub-processes (change 18b);
- data objects (change 20);
- organizational units and assignments (change 19);
- BPMN XML export;
- automatic layout.

## Capabilities

### New Capabilities

- `diagram/bpmn-process-diagrams`: BPMN symbols, the grouped palette, containers and flow warnings on the canvas.

### Modified Capabilities

- `metamodel/starter-packages`: the shipped package `oea-starter-bpmn` and the section on SCR-030.
- `metamodel/viewpoints`: the element catalog with the BPMN keys.

## Impact

- **Contract:** descriptions only. The starter package operations and the element catalog keep their schema.
- **Database:** none.
- **Backend:**
  - `packages/oea-starter-bpmn.json`;
  - the element catalog `notations/catalog.json`;
  - tests of the import.
- **Frontend:**
  - BPMN shape and edge rendering, the grouped palette and the container rule on SCR-021;
  - the flow warnings;
  - the section "Starter packages" on SCR-030.
- **Documentation:**
  - the manual pages "Diagrams" and "Metamodel" (en/de);
  - SCREENS.md;
  - REQ-076, US-074, UC-10 and the sprint plan.
