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

The page of a diagram shows its name, description and viewpoint, and below them the canvas with the shapes of the diagram. Each shape is a box with the name of the entity, the abbreviation of its type and, if the viewpoint gives one, a border in the color of its type. **A shape is a view of an entity: the entity itself stays in the repository.** The same entity may appear several times in one diagram. A diagram shows only entities of the types its viewpoint allows. Notation-specific shapes, for example those of BPMN, follow in a later version; until then every shape is a box.

Pan the canvas by dragging the background and zoom with the mouse wheel or with the buttons *Zoom in*, *Zoom out* and *Fit view* above the canvas. With the permission `entity:read` you can look at the diagram this way; the canvas is then marked *Read-only* and has no palette, no *Add existing* and no *Save*.

With the permission `entity:write` you also edit the layout:

- **Place a new entity:** the palette above the canvas lists the entity types the viewpoint allows. Click a type to place a new shape at the center of the visible canvas, or drag the type onto the canvas. Type the name into the new shape and press Enter. Without a name the shape disappears again (also with Escape). The shape gets the default size of its type in the viewpoint, in points (pt); without a stored size that is 90 × 30 pt.
- **Place an existing entity:** *Add existing* opens a search. Type a part of the name (regardless of upper and lower case; characters such as `%` and `_` are matched as typed) and choose the entity; *Load more* shows further matches. The search finds only entities of the types the viewpoint allows. The new shape appears at the center of the visible canvas.
- **Move and resize:** drag a shape, and drag the handles of a selected shape to resize it. With the keyboard, select a shape (Tab or click) and move it with the arrow keys by 1 pt, or by 10 pt with Shift. Positions are within ±100 000 pt, sizes between 1 and 2000 pt, with two decimals at most.
- **Remove from diagram:** select a shape and choose *Remove from diagram* above the canvas, press Delete, or open the context menu of the shape with the right mouse button. The shape disappears from the diagram; **the entity stays in the repository.**
- **Save:** *Save* stores all changes in one step. Until then, the canvas shows *Unsaved changes*, and new or changed shapes carry a small dot. A diagram holds at most 2000 shapes, and one save creates at most 500 new entities; the canvas tells when a limit is reached.

If you leave the page with unsaved changes, OEA asks whether to discard them. If someone else (or you in another window) changed the diagram since you opened it, *Save* answers *The diagram was changed by someone else*; choose *Reload and discard my changes* to continue with the current state, or *Keep editing* to copy your work elsewhere first. Renaming the diagram counts as a change too.

If a shape cannot be saved, for example because the viewpoint no longer allows its type or its entity was deleted meanwhile, the canvas marks the shape with the reason and keeps all your changes; nothing is saved until every shape is valid.

### Entities with missing values

A new entity created on the canvas has a name and a type only. If its type has a mandatory property without a default value, the entity is still created, and it counts as **incomplete** until the value is written. Every shape of an incomplete entity shows *Incomplete:* with the names of the missing properties. Other changes of the entity, for example through the batch API, still require the mandatory values. The values are entered in the details of the entity, which follow in a later version, or written through the API (see the [API reference](../api/)).

If an entity is deleted, its shapes are hidden from the diagram; they show again when the entity is restored.

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
- `PUT /views/diagrams/{id}` saves the layout with `If-Match`: all `shapes` in drawing order (`id`, exactly one of `entityId` and `newEntityKey`, `x`, `y`, `width`, `height`) and the `newEntities` (`key`, `metatypeId`, `name`) that the shapes with `newEntityKey` refer to. The save is atomic and raises the version. New entities are created like entities of the batch write (`entity.created`), but an entity with a missing mandatory property is created incomplete instead of being rejected. Limits: 2000 shapes, 500 new entities, positions within ±100 000 pt, sizes 1 to 2000 pt, at most two decimals, a body of 2 MB; violations answer 400 or 413. A faulty shape or new entity answers 422 with `invalidFields` and `errors` (for example `shapes[3].entityId` or `newEntities[1].name`); nothing is saved then. A shape ID of another diagram is rejected too.
- `GET /views/diagrams/{id}/entities` searches the entities that the viewpoint allows (`q` up to 255 characters, `metatypeId`, `page`, `pageSize` 1 to 200); a metatype the viewpoint does not allow answers 422 naming `metatypeId`.
- Every saved layout is recorded in the audit trail as `diagram.layout-saved` with the numbers of added, changed and removed shapes and of created entities.
