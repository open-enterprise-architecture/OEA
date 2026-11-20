---
title: Diagrams
covers:
  screens: [SCR-021, SCR-067]
  stories: [US-074]
---

# Diagrams

A diagram models a business process or another view of the architecture with the notation of a [viewpoint](../administration/metamodel-editing#viewpoints), for example *Process Model (BPMN 2.0)*. You create, find, rename, delete and restore diagrams as objects, and you place the elements of the viewpoint on the canvas of a diagram.

You need the permission `entity:read` (for example the role `viewer`) to see diagrams. Creating, renaming and deleting needs `entity:write`, restoring `entity:restore` (role `enterprise-architect`). Service accounts with `entity:write` can create and change diagrams through the API like persons.

## Find diagrams

You find diagrams in three places:

- **The page *Diagrams*:** choose *Diagrams* in the header. The page lists the diagrams by name, regardless of upper and lower case, each with its viewpoint, the notation of the viewpoint and the time of the last change. Type a part of a name into *Filter by name* to narrow the list; characters such as `%` and `_` are matched as typed. The list shows 50 diagrams at a time; choose *Load more* for the next ones. Without any diagram the page says *No diagrams yet.*
- **The browser:** the category *Diagrams* of *Current state* in the sidebar lists the diagrams with the notation as a small label (see [Navigation tree](./navigation#browser)).
- **The navigation tree:** diagrams that were placed in a folder (see [Place a diagram](./navigation#place-a-diagram)).

Choose a diagram to open its page.

## Create a diagram

1. Choose *Create diagram* on the page *Diagrams*.
2. Fill in the dialog:

   | Field | Meaning |
   |---|---|
   | Name | required, 1 to 200 characters; unique among the diagrams, regardless of upper and lower case; surrounding blanks are removed |
   | Description | optional, up to 2000 characters |
   | Viewpoint | required; the viewpoint of the diagram and its notation |

3. Choose *Create*. The new, empty diagram opens.

The viewpoint is fixed when you create the diagram and cannot be changed later. If the name is taken, the dialog says *A diagram with this name already exists.*

## The canvas

The page of a diagram shows its name, description and viewpoint, and below them the canvas with the shapes of the diagram. Each shape is a box with the name of the entity, the abbreviation of its type and, if the viewpoint gives one, a border in the color of its type. **A shape is a view of an entity: the entity itself stays in the repository.** The same entity may appear several times in one diagram. A diagram shows only entities of the types its viewpoint allows. Connections are drawn as edges between the shapes (see [Connections](#connections)). Notation-specific shapes, for example those of BPMN, follow in a later version; until then every shape is a box.

Pan the canvas by dragging the background and zoom with the mouse wheel or with the buttons *Zoom in*, *Zoom out* and *Fit view* above the canvas. With the permission `entity:read` you can look at the diagram this way; the canvas is then marked *Read-only* and has no palette, no *Add existing* and no *Save*.

With the permission `entity:write` you also edit the layout:

- **Place a new entity:** the palette above the canvas lists the entity types the viewpoint allows. Click a type to place a new shape at the center of the visible canvas, or drag the type onto the canvas. Type the name into the new shape and press Enter. Without a name the shape disappears again (also with Escape). The shape gets the default size of its type in the viewpoint, in points (pt); without a stored size that is 90 × 30 pt.
- **Place an existing entity:** *Add existing* opens a search. Type a part of the name (regardless of upper and lower case; characters such as `%` and `_` are matched as typed) and choose the entity; *Load more* shows further matches. The search finds only entities of the types the viewpoint allows. The new shape appears at the center of the visible canvas.
- **Move and resize:** drag a shape, and drag the handles of a selected shape to resize it. With the keyboard, select a shape (Tab or click) and move it with the arrow keys by 1 pt, or by 10 pt with Shift. Positions are within ±100 000 pt, sizes between 1 and 2000 pt, with two decimals at most.
- **Remove from diagram:** select a shape and choose *Remove from diagram* above the canvas, press Delete, or open the context menu of the shape with the right mouse button. The shape disappears from the diagram; **the entity stays in the repository.**
- **Details of an entity:** select a shape to open its details next to the canvas (see [Details of an entity](#details-of-an-entity)).
- **Save:** *Save* stores all changes in one step, including the changes of entities made in the details. Until then, the canvas shows *Unsaved changes*, and new or changed shapes carry a small dot. A diagram holds at most 2000 shapes, and one save creates at most 500 new entities; the canvas tells when a limit is reached. Edges are saved in the same step (see [Connections](#connections)).

If you leave the page with unsaved changes, OEA asks whether to discard them. If someone else (or you in another window) changed the diagram since you opened it, *Save* answers *The diagram was changed by someone else*; choose *Reload and discard my changes* to continue with the current state, or *Keep editing* to copy your work elsewhere first. Renaming the diagram counts as a change too.

If a shape cannot be saved, for example because the viewpoint no longer allows its type or its entity was deleted meanwhile, the canvas marks the shape with the reason and keeps all your changes; nothing is saved until every shape is valid.

### Entities with missing values

A new entity created on the canvas has a name and a type only. If its type has a mandatory property without a default value, the entity is still created, and it counts as **incomplete** until the value is written. Every shape of an incomplete entity shows *Incomplete:* with the names of the missing properties. Other changes of the entity, for example through the batch API, still require the mandatory values. You complete the entity in its details (see below), or write the values through the API (see the [API reference](../api/)).

If an entity is deleted, its shapes are hidden from the diagram; they show again when the entity is restored.

## Details of an entity

Select a shape to open the details panel of its entity beside the canvas. It shows the name, the description, the type and the version of the entity and one field for each property of its type, in the order of the type; a required property is marked. Close the panel with the close button or with Escape; Tab leads from the selected shape into the panel.

- **Read:** every holder of `entity:read` sees the values. A value you may not read, for example one that is restricted to a role, shows as *Restricted*. Without `entity:write` the panel is read-only.
- **Change:** with `entity:write` you change the name, the description and the values of the properties you may write. A property that is restricted to a role you do not hold shows its value without an input field. For a reference property you search the entities of the referenced type by name. An empty input removes the value.
- **Complete an incomplete entity:** the panel names the missing required values (*Required values missing: …*). Enter them and choose *Save*; the shape then loses its *Incomplete:* mark. A required property that you may not change stays required, the entity stays incomplete until someone who may writes it.
- **Save:** your changes in the details count as unsaved changes of the diagram, with the *Unsaved changes* mark, and are saved together with the layout by *Save*, in one step: either everything is saved or nothing. A save changes at most 500 entities; the canvas tells when the limit is reached. Changing an entity that is still incomplete requires its required values, so complete it in the same step.
- **A new entity:** the details of a new shape take a description and values as well; they are created with the entity.

If someone else changed an entity since you opened its details, *Save* answers *… was changed by someone else* naming the entity, and saves nothing. Your changes stay on the canvas; choose *Reload … and discard my changes of it* to read the entity again (this discards only your changes of that entity), then repeat your change and save again. An entity with open issues of a solution cannot be changed; the message names the entity and its issues, and your changes stay. A value the server rejects is marked at its field with the reason.

## Connections

A connection is an entity of its own: it joins a source entity with a target entity and has a type, a name and values. A diagram shows a connection as an **edge**, a straight line between two shapes with an arrow at the target, the name of the connection and, if you add them, bend points. The line runs from a shape of the connection's source to a shape of its target, so the direction follows the connection. **An edge is a view of a connection: the connection itself stays in the repository.** The same connection may have several edges in one diagram, and not every connection between two shapes needs to be shown.

- **Draw a connection:** with `entity:write`, drag from a handle at the left or right side of a shape to another shape. OEA offers the connection types that the viewpoint and the metamodel allow between the two types. With one type, it is used; with several, you choose one; with none, the canvas says *No connection is allowed here.* A type that is allowed only from the second shape to the first is drawn from the second shape. The connection is created when you choose *Save*, in the same step as the layout, and it is named after its ends (*Source → Target*). If its type has a mandatory property without a default value, the connection is still created and counts as **incomplete** until the value is written; the edge shows *Incomplete:* with the names. A connection of a type that controls the visibility of properties (a *scoping connection type*) needs the permission `visibility-scope:assign` in addition; without it, *Save* answers that you may not create connections of this type.
- **Show existing connections:** a diagram does not show the existing connections of an entity by itself. When you place an existing entity with *Add existing* and it has connections to entities that are already on the canvas, a notice says *N connections available*. Choose *Show all* to add their edges, *Choose…* to pick some of them, or *Dismiss*; nothing is added without your choice. Only connections of types the viewpoint allows are offered, and deleted connections are not.
- **Add a connected element:** select a shape and choose *Add connected element* (also in the context menu of the shape). Choose the connection type and the type of the new element among those that the viewpoint and the metamodel allow from the selected type. The new shape is placed to the right of the selected one, at the default length of the connection type: the distance between the two borders, 90 pt without a stored length (see [Viewpoints](../administration/metamodel-editing#viewpoints)). Name the new shape as usual.
- **Bend points:** double-click an edge to add a bend point, drag a bend point of the selected edge to move it, and double-click a bend point to remove it. An edge has at most 50 bend points.
- **Select and remove:** reach an edge with Tab or click it; its details open next to the canvas (see [Details of an entity](#details-of-an-entity)). *Remove from diagram*, Delete or the context menu of the edge removes only the edge; **the connection stays in the repository.** Removing a shape removes its edges, and a connection that was only drawn and not yet saved disappears with its last edge.
- **Limits:** a diagram holds at most 4000 edges, and one save creates at most 500 new connections; the canvas tells when a limit is reached.

If a connection is deleted, its edges are hidden; they show again when it is restored. A stored edge stays when the viewpoint no longer allows its connection type, but you cannot draw new ones of that type.

## Rename and delete a diagram

*Rename* changes the name and the description of a diagram, on the page *Diagrams* and on the page of the diagram. If someone else changed the diagram meanwhile, the dialog says so; reload the page and repeat your change.

*Delete* asks for a confirmation. A deleted diagram no longer appears in the list, the browser and the navigation tree, and its name can be used again. **The entities of the repository stay.** The diagram itself can be restored (see below).

## Restore a diagram

With the permission `entity:restore`, the switch *Show deleted* on the page *Diagrams* lists the deleted diagrams with the time and the person of the deletion. Choose *Restore* to bring a diagram back, together with its entries in the navigation tree. If another diagram has taken its name meanwhile, the page says so and the diagram stays deleted; rename the other diagram first.

## Deleting a viewpoint

Diagrams cannot exist without their viewpoint. When you delete a viewpoint that diagrams use, they are deleted with it, **for good** and including the deleted ones; the entities stay. The confirmation names the number of diagrams that will be deleted (see [Viewpoints](../administration/metamodel-editing#viewpoints)). The same happens when a new version of a [metamodel package](../administration/metamodel-packages) no longer carries a system-defined viewpoint.

## Through the API

The diagrams are available through `/diagrams` (see the [API reference](../api/)):

- `GET /diagrams` lists the diagrams by name. `q` is a part of the name (at most 200 characters), `pageSize` is 1 to 200 (default 50), and `page` starts at 0. The answer carries `nextCursor`; send it as `cursor` together with `page` increased by 1 to continue. A cursor of another query is rejected with 400 naming `cursor`.
- `POST /diagrams` creates a diagram from `name`, `description` and `viewpointId`; `GET /diagrams/{id}` reads it; `PUT /diagrams/{id}` changes `name` and `description` (a `viewpointId` is ignored); `DELETE /diagrams/{id}` deletes it. Every change needs the header `If-Match` with the version that was read (`"3"`); without it the answer is 428, with another version 412. The answers carry the new version as `ETag`.
- `GET /diagrams/deleted` lists the deleted diagrams, and `POST /diagrams/{id}/restore` with `If-Match` restores one; both need `entity:restore`. A diagram that is not deleted answers 409 (`urn:oea:problem:diagram-not-deleted`).
- A taken name answers 422 with the problem type `urn:oea:problem:diagram-name-taken`; other invalid input answers 422 and names the field in `invalidFields` (`name`, `description` or `viewpointId`). An unknown or deleted diagram answers 404.
- Every change is recorded in the audit trail as `diagram.created`, `diagram.updated`, `diagram.deleted` or `diagram.restored`.
- `GET /views/diagrams/{id}` returns the view of a diagram: the diagram with its version (`ETag`), its viewpoint with the effective default sizes, and the shapes in drawing order (the last is drawn on top) with the shape ID, the entity, the position and the size in pt, and `missingMandatory`, the names of the mandatory properties without a value. Shapes of a deleted entity are left out.
- `PUT /views/diagrams/{id}` saves the layout with `If-Match`: all `shapes` in drawing order (`id`, exactly one of `entityId` and `newEntityKey`, `x`, `y`, `width`, `height`) and the `newEntities` (`key`, `metatypeId`, `name`) that the shapes with `newEntityKey` refer to. The save is atomic and raises the version. New entities are created like entities of the batch write (`entity.created`), but an entity with a missing mandatory property is created incomplete instead of being rejected. Limits: 2000 shapes, 500 new entities, positions within ±100 000 pt, sizes 1 to 2000 pt, at most two decimals, a body of 4 MB; violations answer 400 or 413. A faulty shape or new entity answers 422 with `invalidFields` and `errors` (for example `shapes[3].entityId` or `newEntities[1].name`); nothing is saved then. A shape ID of another diagram is rejected too.
- `GET /views/diagrams/{id}/entities` searches the entities that the viewpoint allows (`q` up to 255 characters, `metatypeId`, `page`, `pageSize` 1 to 200); a metatype the viewpoint does not allow answers 422 naming `metatypeId`.
- Every saved layout is recorded in the audit trail as `diagram.layout-saved` with the numbers of added, changed and removed shapes and edges and of created entities and connections.
- `GET /views/diagrams/{id}/entities/{entityId}` returns the details of an entity that a shape of the diagram shows: `name`, `description`, `metatype`, `version`, the values in `properties` (a value you may not read has `redacted: true`), in `fields` for each mapped property whether you may read (`readable`) and change (`writable`) it, and `missingMandatory`. An entity without a shape in the diagram, a deleted entity and a deleted diagram answer 404. The data types, enumeration values and limits of the properties come from the metatype (`GET /metamodel/metatypes/{id}`).
- `GET /views/diagrams/{id}/reference-targets?propertyDefId=…` lists the entities a reference property may point to: the entities of exactly the referenced type, by name (`q` up to 255 characters, `page`, `pageSize` 1 to 200). A property that is not a reference property answers 422 naming `propertyDefId`.
- `PUT /views/diagrams/{id}` also carries `entityUpdates`, at most 500: each names an `entityId` that a shape of the request shows (once), the `expectedVersion` read (at least 1) and optionally `name` (not blank, up to 255 characters), `description` (up to 2000) and `properties` (`propertyDefId` and `value`; an empty `value` removes the value). Fields and properties that are not named keep their value. The changes are written in the same transaction as the layout, with the checks and the audit of a batch update (`entity.updated`); a changed entity needs its mandatory values (422 `MANDATORY_MISSING` on `entityUpdates[i].properties`), and a restricted property without write access answers 403 for the whole save. A `newEntities` item can carry `description` and `properties` as well. An entity with another current version answers 409 with the problem type `urn:oea:problem:entity-version-conflict` and `conflicts` (`entityId` and `currentVersion` of every stale entity); an entity with an open issue of a solution answers 409 `urn:oea:problem:entity-blocked`. Nothing is changed then. The batch API (`POST /entities/batch`) keeps writing without a version.
- `GET /views/diagrams/{id}` also returns the `edges`: each with the edge ID, the `connection` (`id`, `name`, `metatypeId`), the `sourceShapeId` and `targetShapeId`, the bend `points` in pt and `missingMandatory`. Edges of a deleted connection or of a shape of a deleted entity are left out.
- `PUT /views/diagrams/{id}` also carries `edges` (at most 4000; the saved set replaces the stored one) and `newConnections` (at most 500). An edge has an ID, exactly one of `connectionId` and `newConnectionKey`, the `sourceShapeId` and `targetShapeId` of shapes of the same request, and `points` (at most 50, each within ±100 000 pt with at most two decimals). The source shape must show the source entity of the connection and the target shape its target entity; a new edge needs a connection type that the viewpoint allows. A `newConnections` item has a `key`, a `metatypeId` and optionally `name`, `description` and `properties`; its ends are the entities of the shapes of its first edge, and it needs at least one edge. New connections are created after the new entities with the checks and the audit of a connection of the batch write (`entity.created`; the codes `SOURCE_NOT_ALLOWED` and `TARGET_NOT_ALLOWED` name `newConnections[i]`), a missing mandatory value does not block them, and a connection of a scoping type needs `visibility-scope:assign` (403 for the whole save, before the version check). A faulty edge or new connection answers 422 naming `edges[i]` or `newConnections[i]`; more than 4000 edges, 50 bend points on an edge or 500 new connections answer 400. `entityUpdates` may name a connection that an edge of the request shows. The body is limited to 4 MB. `diagram.layout-saved` also carries `edgesAdded`, `edgesChanged`, `edgesRemoved` and `connectionsCreated`.
- `GET /views/diagrams/{id}/entities/{entityId}` also answers for a connection that an edge of the diagram shows; a connection without an edge, or with a hidden edge, answers 404.
- `POST /views/diagrams/{id}/connections/query` with `entityIds` (1 to 2000 entities just placed) and `shownEntityIds` (0 to 2000 other shown entities) returns the connections of allowed types that are not deleted and join a placed entity with a placed or shown one, by ID, at most 5000, with `truncated` for more (`entity:read`; it only reads and is not audited).
