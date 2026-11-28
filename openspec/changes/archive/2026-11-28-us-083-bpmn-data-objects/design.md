# Design

## Context

The BPMN package 1.0.0 (change 18) brings 26 element types and 5 connection types without data. The notation catalog already has `bpmn:DataObject` and `bpmn:DataStore` as element keys, but no keys for data associations. Pools, lanes and sub-processes are containers: dropping a shape into one makes the container's entity its parent, except in assignment lanes, where the shape is assigned instead (change 19, D8). Connection constraints with an empty list allow every metatype, and an allowed endpoint also allows its subtypes. Catalog joins over connections exist.

Facts this design builds on:

- **Package viewpoints are read-only.** "Process Model (BPMN 2.0)" is system-defined (viewpoints spec, "System-defined viewpoints are read-only"). Mapping the TOGAF "Data Object" to `bpmn:DataObject`, or drawing "Realized By" as an edge to a shape such as "System Software", needs an organization viewpoint with the notation `bpmn2`; the context menu of D8 does not.
- **SCR-030 offers "Import" only for a package not yet imported.** `StarterPackage.imported` is true for any version, so 1.0.0 instances cannot reach 1.1.0 from the screen today (D7).
- **The view carries no property values.** `DiagramShape` holds no values, so the collection marker needs a field of the view (D2).
- **Connections without an edge exist for assignments only.** Change 19 saves belongs-to, performs-role and responsible connections without an edge (`newConnections` with `sourceShapeId` and `targetEntityId`), shows them as badges (`DiagramAssignments`) and removes them through `removedConnections` with `entity:delete`. D8 extends this path to "Realized By".
- **Text properties have no length limit without `maxLength`.** `EntityValidator` checks `maxLength` only when it is set (D1).

## Goals / Non-Goals

**Goals:** the package 1.1.0 with data types and data associations, the symbols and lines, the direction check, the transformation properties, data objects that do not nest, "Realized by" on a data store with a repository search, and the update of the package from SCR-030.

**Non-Goals:** lineage (REQ-081 AC6), the portal canvas (AC8), the `[[` mention (REQ-070), process `dataInput` and `dataOutput`, a multi-value property type, and "Create new" in the "Realized by" search.

## Decisions

### D1 – Package 1.1.0

`packages/oea-starter-bpmn.json` gets the version 1.1.0, with stable UUIDs derived like those of 1.0.0. The IDs of 1.0.0 stay unchanged.

**New element types:**

- "BPMN Data Object" (`BDO`), with the optional boolean property "Collection";
- "BPMN Data Store" (`BDS`).

The abbreviations avoid `DO` of the TOGAF "Data Object".

**New connection types:**

| Type | Abbr. | Source | Target | Properties |
|---|---|---|---|---|
| "Data Input Association" | `DIA` | any element | "Task" and its subtypes | none |
| "Data Output Association" | `DOA` | "Task" and its subtypes | any element | "Transformation type", "Affected attributes", "Condition" |
| "Realized By" | `RB` | any element | any element | none |

"Any element" is an empty list on that side, as for "Belongs To" of the organization package.

**New property definitions** (category "BPMN", all optional):

| Property | Data type | Limit |
|---|---|---|
| "Collection" | `boolean` | – |
| "Transformation type" | `enum`: `create`, `enrich`, `overwrite`, `delete`, `read-only` | – |
| "Affected attributes" | `text`, one attribute per line | `maxLength` 5049 (50 tags of 100 characters and 49 line feeds) |
| "Condition" | `text` | `maxLength` 2000 |

The package then has 36 metatypes and 6 property definitions, with en and de labels for the new types, properties and enum values.

**Viewpoint "Process Model (BPMN 2.0)".** It allows the new types and maps them:

- the data object to `bpmn:DataObject`, 36 × 50 pt;
- the data store to `bpmn:DataStore`, 50 × 50 pt;
- the two data associations to the new keys of D2, with a length of 45 pt;
- "Realized By" to `bpmn:Association`, with a length of 90 pt.

**Names.** The names "BPMN Data Object" and "BPMN Data Store" avoid the 409 against the TOGAF "Data Object". No other package uses the new names.

**Re-import.** The import with strategy `merge` updates the content the same package imported before: the 1.0.0 types keep their IDs, the viewpoint is updated in place with its ID, so its diagrams stay and get no conflicts (types are only added). Entities, connections and layouts are unchanged.

**TOGAF data objects.** The TOGAF "Data Object" reaches the associations through the open side of the constraints. To draw it as a BPMN data object, an organization viewpoint of notation `bpmn2` allows it and maps it to `bpmn:DataObject`; the manual describes this.

*Alternative:* a subtype of the TOGAF "Data Object". This is impossible across packages, and the owner chose the symbol.

### D2 – Notation keys and view values

**Keys.** The catalog gets the relationship keys `bpmn:DataInputAssociation` and `bpmn:DataOutputAssociation` (notation `bpmn2`; the `bpmn2` count of `NotationCatalogTests` goes from 32 to 34). The canvas draws both dashed, with an open arrow at the target.

**View value.** `getDiagramView` gets the read-only field `DiagramShape.collection` (boolean), so that the canvas can draw the marker without loading the details of every shape: true when the shape is drawn as `bpmn:DataObject`, its metatype has the package property "Collection" (by its stable ID) and the value is true; false otherwise. It is read with the caller's property visibility: a value the reader may not read gives false, as the redaction of the other reads. A value changed in the details panel and not yet saved is drawn from the pending edit.

### D3 – Direction check in the save

`saveDiagramView` checks every new connection of the request that an edge shows, with the source and target shapes of its first edge; a new "Realized By" without an edge is checked by D8. Connections that exist already (written by a batch or shown with "Show existing connections") are not checked, so that they can still be shown.

| Connection type | Rule |
|---|---|
| mapped to `bpmn:DataInputAssociation` | The source shape is drawn as `bpmn:DataObject` or `bpmn:DataStore`. |
| mapped to `bpmn:DataOutputAssociation` | The target shape is drawn as `bpmn:DataObject` or `bpmn:DataStore`. |
| "Realized By" of the package, or a subtype | In a viewpoint of notation `bpmn2`, the source shape is drawn as `bpmn:DataStore`. |

**Which types are checked.** The data associations are found by the notation key of their viewpoint mapping, not by UUID: a viewpoint that maps another type to `bpmn:DataInputAssociation` gets the same check. "Realized By" is mapped to `bpmn:Association` like "Association", so the key cannot name it; it is found by the stable ID of the package type and its subtypes, as the assignment rules of change 19 find their types.

**The task side.** The task end is guarded by the endpoint constraint of the type ("Task" and its subtypes), which the connection write checks as for every new connection; D3 checks only the data end.

**Errors.** A violation answers 422 `validation-failed` with the issue code `DATA_ASSOCIATION_DIRECTION`, naming `newConnections[i]`. The check is part of the edge validation (`validateEdges`), so it is reported with the other 422 of the layout, before the 409 `diagram-conflicts` and before any entity write. The endpoint codes of the connection write come later, as before.

**Outside the diagram.** Batch writes are not checked; the endpoint constraints of D1 are their only guard, as for the org unit limit of change 19.

### D4 – No nesting of data shapes

A shape whose notation key is `bpmn:DataObject` or `bpmn:DataStore` (a data shape) lies in containers for the layout only. In the canvas model:

- dropping it into a pool, lane or sub-process sets its `parentShapeId`, and it moves and is removed with the container;
- it creates no `parentChanges` and never asks "New parent?";
- a new data entity is created without a parent, also when dropped into a container;
- it gets no assignment from an assignment lane or pool: dropping it into one or out of one creates and removes no belongs-to or performs-role connection (owner decision 2026-11-28: a data object is no process step but the input or output of one);
- "Show children" does not place children drawn as data shapes, names them in the notice, and adds no hierarchy line or composition edge for them.

**Server side.**

- `saveDiagramView` rejects with 422 `DATA_OBJECT_PARENT` a `parentChanges[i]` entry for an entity that a data shape of the request shows, and a `newEntities[i]` with a parent whose shape is a data shape. This is part of `validateParentChanges`, with the other 422 of the layout.
- `assignmentsOf` does not count a data shape as lying in an assignment lane or pool: a data object shape has `assignments: null`, and a data store shape carries only its `realizedBy` (D8).

An entity parent the data entity has from elsewhere (for example a TOGAF data object in its own hierarchy) is kept.

### D5 – Editor

**Palette.** A new group "Data" ("Daten") holds the types drawn as `bpmn:DataObject` or `bpmn:DataStore`. The order of the groups becomes pools and lanes, activities, events, gateways, data, artifacts, other.

**Drag.** `connectionOptions` (edgeModel) filters the allowed types of a drawn line by the rules of D3:

- a type mapped to `bpmn:DataInputAssociation` is offered only when the dragged-from shape is a data shape, and never reversed;
- a type mapped to `bpmn:DataOutputAssociation` is offered only when the dragged-to shape is a data shape, and never reversed;
- "Realized By" is offered only from a data store shape, never reversed;
- every other type is offered as before.

So a drag from a data object to a task offers the input association, from a task to a data object the output association, and from a gateway to a task no data association.

**Symbols.** The data object is a document with a folded corner and, when `collection` is true, three vertical bars at the bottom. The data store is a cylinder. The name is written below both.

**Details panel.** For the property "Affected attributes" (by its stable ID) the details panel passes a tag mode to `PropertyInput`; on other screens the field stays a text area with one attribute per line. The tag input:

- takes at most 50 tags and refuses the 51st;
- trims each tag and refuses one that is empty or blank, or longer than 100 characters;
- drops a duplicate (exact match after trimming);
- stores the tags joined by line feeds; the server limit of 5049 characters matches 50 tags of 100 characters.

**BR-08 retired.** A task may read and create or change the same data object (owner decision 2026-11-28); there is no warning.

### D6 – Security

**No new operation and no new permission.**

- Drawing and saving need `entity:write`, as for every `saveDiagramView`; the read field of D2 and the `realizedBy` of D8 need `entity:read` with `getDiagramView`.
- "Realized by" (D8): adding needs `entity:write`, removing needs `entity:delete` (403 for the whole save, before 412, 428 and every 422, as in change 19); the search needs `entity:read` and returns only active element entities the caller may see.
- Values are written through `entityUpdates` and the values of new connections, with the existing property checks, the property visibility and the audit `entity.updated`.
- The server limits are those of D1: "Affected attributes" at most 5049 characters, "Condition" at most 2000, "Transformation type" one of its five values; the tag limits (50 tags, 1 to 100 characters) are editor limits on top.
- The search of D8 keeps the limits of `searchDiagramReferenceTargets`: `q` at most 255 characters, `pageSize` 1 to 200, `page` at least 0 (400 otherwise); `newConnections` and `removedConnections` keep their limit of 500 each.
- The update of a starter package (D7) needs `metamodel:write`, as every import.

**Audit.** The audit is that of connections (`entity.created` for new connections, `entity.deleted` for removed ones), of `diagram.layout-saved`, and of `metamodel.package-imported` for the update.

### D7 – Update of the BPMN package from SCR-030

`StarterPackage` gets `importedVersion` (string or null): the version of the last import of the package, from `metamodel_package_imports`. The section "Starter packages" of SCR-030 shows "Update to <version>" for a package whose `importedVersion` differs from the shipped `version`, with the confirmation and the result of an import; it calls `importStarterPackage`, which imports with `merge` as before. This also offers the update of other shipped packages that are newer than their import.

*Alternative:* re-import through the API only. Rejected: the migration plan needs a way for the metamodel administrator without the API.

### D8 – "Realized by" on a data store

The owner decided that "Realized by" is set from the context menu of a data store with a repository search, like "Set participant" on pools in change 19. Drawing "Realized By" as an edge between two shapes stays possible (D3, D5).

**Editor.**

- The context menu of a shape drawn as `bpmn:DataStore` offers "Realized by…" to holders of `entity:write` when the package type "Realized By" exists.
- The search dialog of change 19 searches with `part=realizer`; "Create new" is not offered.
- Choosing an entity adds a pending "Realized By" connection without an edge; "Save" writes it.
- The data store shows each realizing element as a badge with its name; "×" removes it and is offered only with `entity:delete` and not for a connection that an edge of the diagram shows.

**Search.** `searchDiagramReferenceTargets` accepts `part=realizer`: the active element entities of every element metatype, by name, with the paging, the `q` filter and the visibility of the other parts. The viewpoint need not allow their type.

**Save.** The connections-without-edge path of change 19 accepts, besides the organization model types, "Realized By" of the package and its subtypes (by the stable package ID):

- the source shape is a shape of the request drawn as `bpmn:DataStore`, else 422 `DATA_ASSOCIATION_DIRECTION` naming `newConnections[i]`;
- the target is `targetEntityId`, an active element entity; `targetNewEntityKey` is rejected with 422 `ASSIGNMENT_TARGET_INVALID`;
- the connection type need not be allowed in the viewpoint, as for assignments;
- the endpoint checks, the audit and the 409 of the connection write apply as for every new connection.

`removedConnections` accepts a "Realized By" whose source is shown on the diagram, with the checks of change 19 (`SOURCE_NOT_ON_DIAGRAM`, `REMOVED_CONNECTION_SHOWN`, `entity-version-conflict`); every other non-assignment type stays `NOT_AN_ASSIGNMENT`.

**View.** `DiagramAssignments` gets `realizedBy` (array of `DiagramAssignment`): the active "Realized By" connections from the entity of a shape drawn as `bpmn:DataStore` to targets that are not deleted, also those an edge shows. A data store shape carries assignments with empty `orgUnits`, `roles` and `persons`; every other shape has an empty `realizedBy`.

*Alternative:* only an edge between two shapes. Rejected by the owner: the target would need a viewpoint that allows its type.

## Risks / Trade-offs

- **Notation-based rules depend on the viewpoint mapping.** A diagram whose viewpoint does not map the data associations has no direction check, which is acceptable for non-BPMN viewpoints.
- **The data end only.** A viewpoint that maps another connection type to a data association key gets only the data end checked; its own endpoint constraints guard the other end.
- **Text instead of a list for the attributes.** Catalog filters match substrings. A multi-value type can replace it later.
- **A TOGAF data object needs an organization viewpoint.** The package viewpoint is read-only and cannot allow types of another package.

## Migration Plan

There is no database change. Instances with 1.0.0 get the new types with "Update to 1.1.0" on SCR-030 (D7). Instances without the package get 1.1.0 with "Import".

## Open Questions

- none. The owner decided on 2026-11-28: data shapes get no lane assignment (D4), "Realized by" is set by a context menu with a search (D8), and BR-08 is retired (D5).
