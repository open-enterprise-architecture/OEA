# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D8): descriptions of `listStarterPackages`, `importStarterPackage` and `NotationElement`; no schema change.

  Verify: Redocly lint, `check_openapi_authorization.py` and `check_openapi_conventions.py` with their tests.

## 2. Backend and package

- [x] 2.1 `backend/src/main/resources/notations/catalog.json` (D2): the 19 BPMN keys.

  Verify: the catalog lists the new keys as `bpmn2` elements and the existing keys unchanged; `NotationCatalogTests` counts 32 `bpmn2` keys; a viewpoint mapping to `bpmn:UserTask` is accepted and one to `bpmn:Unknown` is rejected with 422.
- [x] 2.2 `packages/oea-starter-bpmn.json` (D1), with stable UUIDs and `en`/`de` bundles.

  Verify:
  - `listStarterPackages` lists both packages, BPMN with 31 metatypes and 2 property definitions; `StarterPackagesTests` finds TOGAF by its identifier, not by position (D9);
  - `StarterPackageCheck` accepts the file at startup;
  - the import alone succeeds, with 26 element metatypes, 5 connection types, 2 property definitions and 1 viewpoint, and no `isComposition`;
  - the import after TOGAF succeeds, and the TOGAF content is unchanged;
  - an organization metatype "Task" gives 409 naming it, and nothing is imported;
  - the base types: the 6 task variants have "Task", and the 4 event variants have "Start Event" or "End Event";
  - the endpoints:
    - a sequence flow is accepted between "User Task" and "Exclusive Gateway" and between an organization subtype of "User Task" and "End Event";
    - it is rejected from "User Task" to "Application Component" (422) and from "Pool" to "Task";
    - a message flow is accepted between two pools;
    - an association is accepted from "Text Annotation" to "Task" and rejected from "Task" to "Text Annotation";
  - every viewpoint mapping and size is as in D1;
  - the second import of the same package is 409 or a re-import, as for TOGAF.
- [x] 2.3 The round trip (D9) through `saveDiagramView`:
  - every type of the package;
  - pool, lane, a nested lane and tasks as children;
  - a call activity with "Calls" to a process, and a pool with "References Process";
  - a text annotation with an association whose "Association direction" is `both`;
  - a group with a "Group category" of 255 characters.

  Verify: the reload returns every shape, edge, `parentShapeId`, parent, connection and value as saved. A category of 256 characters is rejected with 422.
- [x] 2.4 Cucumber: the scenarios of the delta specs at API level.
  - The BPMN import runs once and is guarded, and the other features stay independent of it (D9).
  - The starter feature counts only the metatypes of its own package (D9).

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 `bpmnSymbols.ts` and the BPMN rendering of `EntityShape.vue` and `ConnectionEdges.vue` (D3).

  Verify with specs:
  - every key gives its symbol and marker, and unknown and unmapped keys give the box;
  - a non-`bpmn2` notation gives the box;
  - the line styles of the three flow keys, and the default line otherwise;
  - the text annotation shows its description, else its name;
  - every symbol has an `aria-label`.
- [x] 3.2 The grouped palette (D4).

  Verify: the groups and their order, empty groups hidden, a subtype under the group of its mapping, unmapped types under "Other", and the flat palette for `archimate3`.
- [x] 3.3 The container rule (D5).

  Verify:
  - a drop into a lane nests the shape there, and a drop onto a task nests it in the lane below or in none;
  - a drop onto a group or a text annotation does not nest the shape;
  - a move of a task onto another task in a lane keeps it in the lane, and "New parent?" names the lane;
  - "Show children" is offered for a pool with lanes and not for a task with children;
  - `archimate3` keeps any shape as a container and the children question for every shape.
- [x] 3.4 The flow warnings (D6).

  Verify with `it.each`:
  - a sequence flow in the same pool gets no warning, also across lanes and nested lanes;
  - across two pools it is warned, and with one end without a pool it is warned;
  - with both ends without a pool it gets no warning;
  - a message flow is warned within one pool, and not between two pools or between a pool and a task of another pool;
  - the pool itself as an end counts as that pool;
  - the warning goes away after a move;
  - the count in the toolbar;
  - the save is sent with warnings;
  - conflicts and hidden shapes are ignored.
- [x] 3.5 The section "Starter packages" on SCR-030 (D7), with `i18n` in en and de.

  Verify: listed with "Import" and "Imported", the confirmation, the counts after the import, the 409 conflicts listed, and no section without `metamodel:write`.
- [x] 3.6 Stories:
  - SCR-021 with a BPMN process (two pools, lanes, every symbol, a warned flow);
  - SCR-030 with the starter packages.

  Verify: the story tests with a11y pass, and `python3 scripts/check_screens.py` passes, except for the images CI renders.

## 4. Documentation and wrap-up

- [x] 4.1 The manual pages "Diagrams" and "Metamodel" in English and German (D10).
- [x] 4.2 SCREENS.md (SCR-021, SCR-030).
- [x] 4.3 Realign:
  - REQ-076 (0.3.0);
  - US-074 (the implementation note; tick AC1, AC4, AC5, AC6, AC7, AC8 and AC9; AC2 and AC3 stay open, because they include the data objects of change 20, and the stacking of nested lanes follows with change 18b);
  - UC-10;
  - the sprint plan: the status of change 18 and the cut of 18b, `us-074-bpmn-pools-and-lanes`, with lane stacking and the collapsing of sub-processes.
- [x] 4.4 All checks of AGENTS.md "Before Pushing", the frontend unit tests and `vue-tsc`, and the full backend `verify`.
