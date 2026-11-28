# Tasks

## 1. Package and catalog

- [x] 1.1 `packages/oea-starter-bpmn.json` 1.1.0 (D1): the types, abbreviations, the four property definitions with their limits, the mappings, the default sizes, the en and de labels of types, properties and enum values, and the updated package description.

  Verification: `BpmnStarterPackageTests` extended with:
  - 28 element types, 8 connection types and 6 property definitions; the IDs of 1.0.0 unchanged;
  - a re-import over 1.0.0 with a diagram keeps the types, the viewpoint ID, the diagram without conflicts and the entities, and the viewpoint allows the new types;
  - a "Data Input Association" from a TOGAF "Data Object" to a task is accepted;
  - a "Data Input Association" to a gateway gives 422;
  - a "Data Output Association" from a non-task gives 422;
  - "Realized By" between any two elements is accepted.
- [x] 1.2 The notation keys `bpmn:DataInputAssociation` and `bpmn:DataOutputAssociation` (relationship, `bpmn2`).

  Verification: `NotationCatalogTests` with the `bpmn2` count 34 (was 32), both keys as relationships that fit `bpmn2` and not `archimate3`.

## 2. Contract and backend

- [x] 2.1 `api/openapi.yaml`:
  - the issue codes `DATA_ASSOCIATION_DIRECTION` (naming `newConnections[i]`) and `DATA_OBJECT_PARENT` (naming `parentChanges[i]` or `newEntities[i]`) in the description of `saveDiagramView`;
  - `DiagramShape.collection` (boolean, required, D2) and `DiagramAssignments.realizedBy` (required array, D8), with the data store shape among the shapes that carry assignments;
  - `part=realizer` of `searchDiagramReferenceTargets`, "Realized By" as a connection without an edge and in `removedConnections`, and `DATA_ASSOCIATION_DIRECTION` for its source (D8);
  - `StarterPackage.importedVersion` (string or null), and the counts of `oea-starter-bpmn` (36 metatypes, 6 property definitions) in `listStarterPackages`.

  Verification: Redocly, both OpenAPI checks with their tests, and `check_openapi_conventions.py`.
- [x] 2.2 The direction check of D3 in `validateEdges`.

  Verification, parameterized:
  - input association:
    - from a data object to a task is accepted;
    - from a data store to a user task is accepted;
    - from a task to a data object gives 422 `DATA_ASSOCIATION_DIRECTION` naming `newConnections[i]`;
    - from a gateway to a task gives 422;
  - output association, the reverse cases;
  - "Realized By":
    - from a data store is accepted;
    - from a data object gives 422;
    - from a task in a viewpoint of another notation is not checked;
  - a new edge of an existing connection with the wrong direction is accepted;
  - a type of the organization mapped to `bpmn:DataInputAssociation` is checked like the package type;
  - in a viewpoint without the mapping there is no check;
  - a violation together with a conflict gives the 422, not the 409, and stores nothing;
  - a batch write is not checked.
- [x] 2.3 The parent rule of D4 in `validateParentChanges`, and no assignments of data shapes in `assignmentsOf`.

  Verification:
  - a `parentChanges` entry for a data object shape gives 422 `DATA_OBJECT_PARENT`, and nothing changes;
  - a new data object with a parent gives 422;
  - a new data object without a parent inside a lane shape is accepted, and its parent stays null;
  - a task in the same lane still gets its parent;
  - a data object shape in an assignment lane has `assignments: null`.
- [x] 2.4 The output association values and the view fields.

  Verification:
  - "Transformation type" with each of the five values is accepted (parameterized), and an unknown value gives 422;
  - "Affected attributes" with 50 lines of 100 characters (5049 characters) is stored and read back; 5050 characters give 422;
  - "Condition" with 2000 characters is accepted; 2001 give 422;
  - `collection` is true for a data object with "Collection" true, false with false or no value, false for a TOGAF data object, and false when the reader may not read the property.
- [x] 2.5 `StarterPackage.importedVersion` (D7) from the last import of the package.

  Verification: null before the import, `1.0.0` after importing 1.0.0, the shipped version after the update; `listStarterPackages` still answers 403 without `metamodel:write`.
- [x] 2.6 "Realized by" without an edge (D8): the search, the save and the removal.

  Verification:
  - `part=realizer` returns active element entities of any metatype, also one the viewpoint does not allow, no connections and no deleted entities; `q` of 255 characters is accepted and 256 give 400; `pageSize` 200 is accepted and 201 give 400; `pageSize` 1 is accepted and 0 gives 400; without `entity:read` 403;
  - a "Realized By" without an edge from a data store shape to an existing element is created, audited as `entity.created`, and listed in `realizedBy` of the shape; a subtype of "Realized By" works the same;
  - from a task shape or a data object shape 422 `DATA_ASSOCIATION_DIRECTION`; with `targetNewEntityKey` 422 `ASSIGNMENT_TARGET_INVALID`; to a deleted entity the endpoint error of the connection write; nothing changes in each case;
  - another connection type without an edge outside the organization model still gives 422 `METATYPE_NOT_ALLOWED`;
  - the removal with `entity:delete` deletes it softly and audits `entity.deleted`; without `entity:delete` 403 before 412 and every 422; with a stale version 409 `entity-version-conflict`; when an edge of the request shows it 422 `REMOVED_CONNECTION_SHOWN`;
  - 500 new connections and 500 removals per save are accepted, 501 give 400, as before;
  - `realizedBy` leaves out a deleted connection or target, and a task or data object shape has an empty `realizedBy`.
- [x] 2.7 Cucumber `bpmn-data-objects.feature`, on an instance of its own:
  - the scenario of US-083 through the API (place "Order", the input association, the output association with enrich and two attributes);
  - the wrong direction;
  - "Realized by" from a data store to a System Software through the API, and its removal;
  - the catalog join of AC7 with the empty cell.

## 3. Frontend

- [x] 3.1 The symbols of the data object (document, collection marker from `collection` or the pending edit) and the data store (cylinder), the dashed lines with an open arrow, and the palette group "Data" between "Gateways" and "Artifacts" (en and de).

  Verification: unit specs of `bpmnSymbols` (the data keys in the group `data`, no longer `other`; the order of `PALETTE_GROUPS`; the flow style of both keys) and a story.
- [x] 3.2 The drag direction filter of D5 in `connectionOptions`.

  Verification, in unit specs of `edgeModel`:
  - from a data object to a task: the input association only, not reversed;
  - from a task to a data object: the output association only, not the input association reversed;
  - from a gateway to a task: no data association and no "Realized By";
  - "Realized By" from a data store, not from a data object;
  - a type without a rule is offered as before.
- [x] 3.3 No nesting of data shapes (D4).

  Verification, in unit specs:
  - a drop into a lane gives no parent change and no "New parent?";
  - a new one in a lane has no parent;
  - a drop into a role lane gives no assignment;
  - "Show children" does not place a data child and names it in the notice.
- [x] 3.4 The tag input for "Affected attributes".

  Verification, in unit specs:
  - 50 tags are accepted, and a 51st is refused;
  - a tag of 100 characters is accepted, and one of 101 is refused;
  - an empty tag or one of blanks only is refused;
  - duplicates are dropped;
  - the stored value is the tags joined by line feeds, and a stored value is split into tags;
  - "create" together with an input association between the same task and object gives no warning.

  Plus the story `DataObjects`, with a real-pointer drag from a data object to a task and the details panel of the output association.
- [x] 3.5 "Realized by…" on a data store (D8): the context menu entry, the search dialog of change 19 with `part=realizer` and without "Create new", the badges and "×".

  Verification, in unit specs:
  - the entry is offered on a data store shape with `entity:write`, not on a data object, a task, or without `entity:write`, and not when the package type is missing;
  - choosing an entity adds a pending connection without an edge, counted as an unsaved change, and the request carries it with `sourceShapeId` and `targetEntityId`;
  - "×" is offered with `entity:delete` and not without it, and not for a connection an edge shows; it sends `removedConnections` with the version read;
  - a 422 `DATA_ASSOCIATION_DIRECTION` marks the shape.

  Plus the story `DataObjects` with the badge on a data store.
- [x] 3.6 "Update to <version>" on SCR-030 (D7).

  Verification, in unit specs of `StarterPackagesSection`: shown when `importedVersion` differs from `version`, not when they are equal or the package is not imported; the confirmation calls `importStarterPackage` and shows the result; a 409 shows the conflicts.

## 4. Documentation

- [x] 4.1 The manual pages "Diagrams" and "Metamodel packages" in en and de. They cover data objects, mapping the TOGAF "Data Object" in an organization viewpoint of notation `bpmn2`, "Realized by…" on a data store, no assignment of data in lanes, and the update to 1.1.0.
- [x] 4.2 The documents to update:
  - the business object process: BR-07 as realized, BR-08 retired by owner decision 2026-11-28 (a task may read and create or change the same data object), "Realized By" and the connection names;
  - SCREENS.md (SCR-021, SCR-030);
  - REQ-081 (AC6 and AC8 open), US-083 (AC8 for BR-07 only, BR-08 retired), US-074 (AC2 and AC3 done), UC-10, and the sprint plan.

  Then run `generate_traceability.py`.

## 5. Checks

- [x] 5.1 All local checks:
  - the backend verify;
  - in the frontend: unit tests, `vue-tsc --build` and story tests;
  - `openspec validate --specs --strict` and `openspec validate us-083-bpmn-data-objects --strict`;
  - the links, screens, manual and traceability scripts;
  - markdownlint on every changed Markdown file;
  - Redocly.
