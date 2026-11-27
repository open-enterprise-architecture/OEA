# Design

## Context

- **Starter packages (ADR-005, REQ-112):**
  - `StarterPackages` reads every `classpath*:packages/*.json` (the repository folder `packages/`).
  - `listStarterPackages` and `importStarterPackage` need `metamodel:write`. The import runs the checks of `importMetamodelPackage`: a 409 for conflicting names or IDs, metatypes and viewpoints of the same package or the instance, `connectionConstraints`, `baseMetatypeId`, viewpoint `mappings` and `sizes`, `askNewParent`, and `i18nBundles`.
  - The UI offers the packages only on the start page of an instance without metatypes (`StarterConfigurationCard.vue`).
  - The TOGAF package 1.4.0 has eight metatypes, among them "Composition" (`isComposition`).
- **Element catalog:** `notations/catalog.json` is read by `NotationCatalog`. It has 13 BPMN keys: Task, SubProcess, StartEvent, EndEvent, IntermediateEvent, Gateway, DataObject, DataStore, Pool, Lane, SequenceFlow, MessageFlow and Association.
- **Canvas:**
  - `EntityShape.vue` draws a box for every notation, and `ConnectionEdges.vue` a straight line with an arrow.
  - `effectiveMappings` of the view give the notation element per allowed metatype.
  - The nesting comes from change 16: `containerAt`, `nestShape`, `parentOfShape`, "New parent?", and a new element dropped into a container becomes a child of its entity.
- **Hierarchy:** setting a parent creates the automatic composition connection only when a connection type is marked `isComposition` and allows both metatypes. At most one is marked per instance.

## Goals / Non-Goals

**Goals:**

- the BPMN starter package;
- BPMN symbols and lines;
- the grouped palette;
- the container rule;
- the flow warnings;
- the starter packages on SCR-030.

**Non-Goals:**

- lane stacking and the collapsing of sub-processes (change 18b);
- data (change 20);
- organizational units and assignments (change 19);
- BPMN XML;
- automatic layout;
- server-side checks of the pool rules.

## Architecture Constraints

- **ADR-005:** metamodel content comes from packages, and no built-in BPMN metatypes are added.
- **ADR-013:** contract first. Only descriptions change; no schema changes.
- **ADR-051:** no new SQL is expected. If any is needed, it goes in `core` stores.
- **ADR-007:** Vue Flow custom nodes and edges render the symbols as inline SVG, with no new dependency.

## Decisions

### D1 Package `oea-starter-bpmn` 1.0.0

- The file `packages/oea-starter-bpmn.json` in the format of the TOGAF package:
  - name "BPMN 2.0 process modeling", publisher OEA, a description, `formatVersion` 1.0;
  - stable UUIDs, generated once as UUIDv5 in a namespace of their own and written into the file;
  - no architecture layers.
- **Property definitions** in category "BPMN":
  - "Association direction": enum `none`, `one`, `both`, optional, mapped on "Association";
  - "Group category": string, at most 255 characters, optional, mapped on "Group" (BPMN `categoryRef`).
  - Property names are unique in the instance, and a taken name rejects the whole import with 409. A plain "Category" is likely to exist in an organization's metamodel, so the package uses "Group category" (SQL column `group_category`). "Association direction" gives `association_direction`. Neither column is reserved.
- **Element metatypes (26),** with their base types and catalog keys:
  - **Process (1).** "Process" (none, unmapped).
  - **Pools and lanes (2).** "Pool" (`bpmn:Pool`) and "Lane" (`bpmn:Lane`).
  - **Tasks (7).**
    - "Task" (`bpmn:Task`);
    - with base "Task": "User Task" (`bpmn:UserTask`), "Service Task" (`bpmn:ServiceTask`), "Send Task" (`bpmn:SendTask`), "Receive Task" (`bpmn:ReceiveTask`), "Business Rule Task" (`bpmn:BusinessRuleTask`) and "Script Task" (`bpmn:ScriptTask`).
  - **Other activities (2).** "Sub-Process" (`bpmn:SubProcess`) and "Call Activity" (`bpmn:CallActivity`).
  - **Start and end events (6).**
    - "Start Event" (`bpmn:StartEvent`);
    - with base "Start Event": "Message Start Event" (`bpmn:MessageStartEvent`) and "Timer Start Event" (`bpmn:TimerStartEvent`);
    - "End Event" (`bpmn:EndEvent`);
    - with base "End Event": "Message End Event" (`bpmn:MessageEndEvent`) and "Terminate End Event" (`bpmn:TerminateEndEvent`).
  - **Intermediate events (2).** "Intermediate Catch Event" (`bpmn:IntermediateCatchEvent`) and "Intermediate Throw Event" (`bpmn:IntermediateThrowEvent`).
  - **Gateways (4).** "Exclusive Gateway" (`bpmn:ExclusiveGateway`), "Parallel Gateway" (`bpmn:ParallelGateway`), "Inclusive Gateway" (`bpmn:InclusiveGateway`) and "Event-Based Gateway" (`bpmn:EventBasedGateway`).
  - **Artifacts (2).** "Text Annotation" (`bpmn:TextAnnotation`) and "Group" (`bpmn:Group`).
- **Connection types (5),** none marked `isComposition`. *Flow nodes* are "Task" (with its subtypes), "Sub-Process", "Call Activity", every event and every gateway.
  - **"Sequence Flow"** (`bpmn:SequenceFlow`): source and target are flow nodes.
  - **"Message Flow"** (`bpmn:MessageFlow`): source and target are "Pool", "Task" (with its subtypes), "Sub-Process", "Call Activity" and every event.
  - **"Association"** (`bpmn:Association`): the source is "Text Annotation"; the target is every element metatype of the package except "Process".
  - **"Calls"** (unmapped): from "Call Activity" to "Process".
  - **"References Process"** (unmapped): from "Pool" to "Process".
- **Viewpoint "Process Model (BPMN 2.0)":**
  - the notation `bpmn2`, all 26 element metatypes and all 5 connection types, and every mapping above ("Process", "Calls" and "References Process" are unmapped and drawn as `generic:Box` and `generic:Arrow`);
  - `askNewParent` null, so the global setting applies.
- **Default sizes in pt** (none for "Calls" and "References Process", which get the default length of 90):

  | Type | Size |
  |---|---|
  | Pool | 600 × 200 |
  | Lane | 570 × 100 |
  | Tasks and "Call Activity" | 90 × 60 |
  | "Sub-Process" | 270 × 150 |
  | Events | 27 × 27 |
  | Gateways | 38 × 38 |
  | "Text Annotation" | 90 × 45 |
  | "Group" | 180 × 120 |
  | "Process" | 90 × 30 |
  | "Sequence Flow" (length) | 45 |
  | "Message Flow" (length) | 90 |
  | "Association" (length) | 45 |

- **`i18nBundles`:** `en` and `de` for every metatype (`metatype.{id}.name`), property definition (`property-def.{id}.label`), enum value (`enum-value.{id}.label`) and the category (`property-category.{id}.name`). Examples: "Aufgabe", "Benutzeraufgabe", "Sequenzfluss", "Nachrichtenfluss", "Exklusives Gateway". The label keys of ADR-025 have no viewpoint element (`LabelKey`, `PackageValidator.labels`), so a viewpoint label would fail the import. The viewpoint keeps its English name in every locale, as the TOGAF viewpoints do.
- **Summary.** `listStarterPackages` gives 31 metatypes (26 element and 5 connection metatypes) and 2 property definitions.
- **Import validation.** The design fits the checks of `PackageValidator` and `PackageImporter`:
  - every base type and allowed endpoint is a metatype of the package, and the base types are elements without a cycle;
  - no metatype, property, category or viewpoint name is used by the TOGAF package or by the built-in "Structuring aid";
  - every mapping key is in the catalog (D2), belongs to `bpmn2`, and is an element key for an element metatype and a relationship key for a connection type;
  - every size is 1 to 2000 pt, with width and height for an element and a length for a connection;
  - `StarterPackageCheck` validates the file at startup, so a broken package fails the build tests.
- **Containment** is the parent hierarchy of change 16. The package needs no containment connections; REQ-076 is realigned.
- **The TOGAF package** stays as it is. Its "Composition" has no endpoint restriction. When both packages are imported, it is created automatically for BPMN parents, and the BPMN viewpoint does not show it.

### D2 Element catalog

`catalog.json` gets 19 element keys. The existing 13 keys stay, so stored mappings keep working.

- `bpmn:UserTask`, `bpmn:ServiceTask`, `bpmn:SendTask`, `bpmn:ReceiveTask`, `bpmn:BusinessRuleTask`, `bpmn:ScriptTask` and `bpmn:CallActivity`;
- `bpmn:IntermediateCatchEvent`, `bpmn:IntermediateThrowEvent`, `bpmn:MessageStartEvent`, `bpmn:TimerStartEvent`, `bpmn:MessageEndEvent` and `bpmn:TerminateEndEvent`;
- `bpmn:ExclusiveGateway`, `bpmn:ParallelGateway`, `bpmn:InclusiveGateway` and `bpmn:EventBasedGateway`;
- `bpmn:TextAnnotation` and `bpmn:Group`.

The labels are in English, as the existing ones.

### D3 Symbols and lines (frontend)

- **Shapes.** A module `bpmnSymbols.ts` maps a notation key to a symbol descriptor: kind, marker and label position. `EntityShape.vue` renders the descriptor as inline SVG scaled to the shape size, but only when the view's viewpoint notation is `bpmn2`. Every other notation, and every unmapped type, keeps today's box. The symbols:
  - **Tasks:** a rounded rectangle with the name inside, and a marker at the top left (user, gear, filled envelope, open envelope, table, script).
  - **Call Activity:** a thick border.
  - **Sub-Process:** a rounded rectangle, with the name at the top.
  - **Events:** circles with the name below the symbol. A start event has a thin line, an end event a thick one, and an intermediate event a double one. The markers are a filled envelope for throw events (send), an open envelope for catch events (receive), a clock for the timer and a filled circle for terminate.
  - **Gateways:** a diamond with the name below. The markers are X, +, O, and a pentagon in a double circle for the event-based gateway.
  - **Pool and Lane:** a rectangle with a vertical label band of 30 pt on the left and the name rotated.
  - **Text Annotation:** an open bracket on the left, showing the entity description, else the name.
  - **Group:** a dashed rounded rectangle with the name at the top and no fill.
  - **Shared parts:** the existing marks (unsaved, incomplete, conflict, error) and the name editing stay on all symbols.
- **Edges.** `ConnectionEdges.vue` takes the line style from the notation key of the connection type:
  - `bpmn:SequenceFlow`: solid, with a filled arrow;
  - `bpmn:MessageFlow`: dashed, with an open circle at the source and an open arrow at the target;
  - `bpmn:Association`: dotted, without an arrow;
  - any other type keeps today's line.
- **Accessibility.** Each symbol has an `aria-label` with the type name.

### D4 Palette

- **Groups.** In a `bpmn2` viewpoint the palette entries are grouped by the notation key of their effective mapping:
  - "Pools and lanes": Pool, Lane;
  - "Activities": the tasks, SubProcess, CallActivity;
  - "Events": every event key;
  - "Gateways": the gateway keys and `bpmn:Gateway`;
  - "Artifacts": TextAnnotation, Group;
  - "Other": unmapped types and the data keys, until change 20.
- **Display.** Groups are labelled and keep the existing sort order of the types within them. Empty groups are hidden.
- **Other notations** keep the flat palette.

### D5 Containers

- In a `bpmn2` viewpoint, `containerAt` considers only shapes whose notation key is `bpmn:Pool`, `bpmn:Lane` or `bpmn:SubProcess`. A drop on another shape falls through to the container below it, if there is one.
- **Fit to the nesting code of change 16.** `containerAt(shapes, point, exclude)` gets an optional predicate `canContain(shape)`, built from the view's `effectiveMappings` and notation. Its callers `dropInto` (a new element from the palette) and `settle` (a moved shape) pass it, so "New parent?" and the parent of a new element follow the rule without other changes. `nestShape`, `nestBlock`, `growContainers` and `layoutChildren` stay as they are.
- **Children of a placed entity.** In a `bpmn2` viewpoint, the question "Show children" and the menu entry are offered only for a shape that can contain. Otherwise the children of, for example, a task would be nested in it. The children of a non-container stay as they are and show as hierarchy lines when placed.
- **Loaded nesting.** A shape saved nested in a shape that is not a container, for example after a viewpoint change, stays nested as saved, until it is moved.
- Other notations keep the rule of change 16.
- The server stays generic and accepts any valid `parentShapeId`.

### D6 Flow warnings

- A pure function `bpmnFlowWarnings(shapes, edges, mappings)` in the canvas model gives the warnings for the current layout.
- **The pool of a shape** is the nearest ancestor shape in the diagram nesting whose notation key is `bpmn:Pool`, or the shape itself when it is a pool. Otherwise the shape has no pool.
- **Sequence flows** (`bpmn:SequenceFlow`) get the warning `sequence-crosses-pool` when the pools of their ends differ. That includes one end in a pool and the other in none. Two ends that both have no pool give no warning.
- **Message flows** (`bpmn:MessageFlow`) get `message-within-pool` when both ends have the same pool.
- **Display.** Each warning is marked on the edge with a warning style and a text: "Sequence flow crosses a pool boundary" or "Message flow within one pool". The toolbar shows "N BPMN warnings", which selects the first warned edge.
- **Saving.** Warnings never block the save and are not sent to the server.
- **Edges of conflicts or hidden shapes** are not checked.

### D7 Starter packages on SCR-030

- A section "Starter packages" in the metamodel configuration for `metamodel:write`, built on `listStarterPackages`.
- For each package it shows the name, version, description, metatype count and "Imported" or "Import".
- "Import" asks for confirmation and calls `importStarterPackage`. It then shows the created counts, or the conflicts of a 409 by name, and reloads the configuration.
- Lock mode `import-only` does not block it, as for every import.
- The start page card stays as it is.

### D8 Contract descriptions

- `listStarterPackages`: the two shipped packages.
- `NotationElement`: keys per BPMN type.
- No schema changes.

### D9 Tests and shared state

- **Backend tests:**
  - the BPMN package imports alone, after TOGAF, and fails with 409 on a taken name;
  - its counts, base types, constraints, viewpoint, mappings and sizes;
  - a sequence flow to an "Application Component" is rejected with 422 by `saveDiagramView`;
  - a round trip of the descriptive process via `saveDiagramView` and `getDiagramView`.
- **Isolation.** The import changes the metamodel of the database it runs on. The BPMN tests use a test class of their own with `OeaDatabases.freshMigrated()`, as `StarterPackagesTests` does.
- **Existing tests that change.**
  - `StarterPackagesTests.packageListOfANewInstance` reads the TOGAF package as `body(result).get(0)`. The list is sorted by `packageId`, so `oea-starter-bpmn` comes first; the test looks the package up by its identifier.
  - `NotationCatalogTests.theCountsPerNotation` expects 13 `bpmn2` keys; it becomes 32.
- **Cucumber.**
  - `StarterPackageSteps` shares one instance across the scenarios of `starter-packages.feature`. The step "{int} metatypes exist with scope {string}" counts every imported metatype; it counts only those of the imported package (`source_package_id`), so a BPMN import in the same instance does not change it.
  - The BPMN scenarios run in a feature of their own whose steps start their own instance, as `MetamodelInstance.start()` does. No other feature imports or expects the BPMN package.

### D10 Documentation

- The manual pages "Diagrams" and "Metamodel" in English and German: the BPMN package, the palette, the containers and the warnings.
- SCREENS.md: SCR-021 and SCR-030.
- REQ-076 (version 0.3.0) realigned:
  - the package instead of built-in types;
  - the hierarchy instead of the containment connections;
  - base types.
- US-074 and UC-10 realigned.
- The sprint plan: the status of change 18 and the cut of change 18b, `us-074-bpmn-pools-and-lanes`.
