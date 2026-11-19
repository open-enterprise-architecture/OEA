---
title: Diagrams
covers:
  screens: [SCR-067]
  stories: [US-074]
---

# Diagrams

A diagram models a business process or another view of the architecture with the notation of a [viewpoint](../administration/metamodel-editing#viewpoints), for example *Process Model (BPMN 2.0)*. This version of OEA manages diagrams as objects: you create, find, rename, delete and restore them. The canvas on which you place elements follows in a later version; until then, the page of a diagram shows its name, description and viewpoint.

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
