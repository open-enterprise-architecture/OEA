# Design

## Context

- **Diagrams:** none are implemented. The contract has only the planned `getDiagramView` (for the canvas, change 11) and the planned portal read.
- **Similar features:** dashboards (S16) and solutions are the closest patterns:
  - an object with name, `If-Match`, audit and a list page;
  - a store in `core` and a service in `app` (ADR-051).
- **Viewpoints:** `Viewpoints` and `ViewpointStore` manage them. A package re-import deletes system-defined viewpoints that the package no longer carries (`viewpointsDeleted`).
- **Browser:** `getBrowserView` and `listBrowserItems` serve "Current state" with the categories `components`, `connections` and `catalogs`.
- **Navigation tree:** it holds entries of the types `catalog`, `entity` and `solution`. Missing catalogs are left out when the tree is read.

## Goals / Non-Goals

**Goals:**

- Diagrams as objects with the permissions, audit and lost-update protection of the other objects.
- The three places to find them.
- The viewpoint deletion with diagrams.

**Non-Goals:**

- Shapes and layout (change 11).
- Plateau.
- Portal.
- Collaboration.

## Architecture Constraints

- **ADR-051:** SQL only in `core` stores. A new package `io.oea.core.diagram` holds `DiagramStore`; `io.oea.app.diagram.Diagrams` holds the rules. The ArchUnit rules stay green.
- **ADR-032:** `entity:read` for reads, `entity:write` for writes, declared in `x-oea-authorization`.
- **ADR-033:** an audit event for every write; nothing for a rejected write.
- **ADR-013:** the contract first.

## Decisions

### D1 Table `diagrams`

Migration `V46__diagrams.sql`, modeled on `V43__dashboards.sql`:

```sql
CREATE TABLE diagrams (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT        NOT NULL CHECK (name = btrim(name) AND name <> '' AND length(name) <= 200),
    description  TEXT        CHECK (length(description) <= 2000),
    viewpoint_id UUID        NOT NULL REFERENCES viewpoints (id),
    version      INTEGER     NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT      NOT NULL REFERENCES persons (id),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT      NOT NULL REFERENCES persons (id),
    deleted_at   TIMESTAMPTZ,
    deleted_by   BIGINT      REFERENCES persons (id),
    CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);
CREATE UNIQUE INDEX diagrams_name_unique ON diagrams (lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX diagrams_viewpoint ON diagrams (viewpoint_id);
REVOKE ALL ON diagrams FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON diagrams TO oea_app;
```

- **Actor columns:** a service account is a row of `persons` with `person_type` `service-account` (V30), so `created_by` and `updated_by` reference `persons (id)` as in all other tables. The local system admin account has no person and gets 403 (as for solutions and dashboards).
- Names are stored trimmed, so `lower(name)` is the case-insensitive comparison (as `viewpoints_name_unique`).
- `viewpoint_id` has no cascade: the service deletes and audits the diagrams before it deletes the viewpoint (D4). A diagram inserted for a viewpoint deleted at the same time fails on the foreign key; the service answers it with 422 naming `viewpointId`.
- **Navigation tree:** no migration. `tree_node_items` already allows the item type `diagram` (V40), and `tree_node_items_once_per_folder` (V21) covers it.
- A layout (shapes, edges) follows in change 11.

### D2 Contract

New tag `Diagrams`. The operations follow dashboards and the solution landscape (`getSolutionLandscape`):

| Operation | Path | Permission | Notes |
|---|---|---|---|
| `listDiagrams` | `GET /diagrams?q=&page=&pageSize=&cursor=` | `entity:read` | `PageParam`, `PageSizeParam` (1–200, default 50) and `CursorParam`; `DiagramPage` with `items`, `page` (`PageMeta`) and `nextCursor` |
| `createDiagram` | `POST /diagrams` | `entity:write` | `DiagramCreate` {name, description, viewpointId}; 201 with `ETag` and `Location` |
| `getDiagram` | `GET /diagrams/{id}` | `entity:read` | `Diagram` with `ETag` |
| `updateDiagram` | `PUT /diagrams/{id}` | `entity:write` | `IfMatchRequiredHeader`; `DiagramUpdate` {name, description} replaces both, as `updateDashboard` and `updateViewpoint`; a `viewpointId` in the body is an unknown field and changes nothing |
| `deleteDiagram` | `DELETE /diagrams/{id}` | `entity:write` | `IfMatchRequiredHeader`; 204 |

- `{id}` uses `UuidIdParam` (conventions check, rule 1).
- `Diagram` carries `id`, `name`, `description`, `viewpoint` (`DiagramViewpoint`: `id`, `name`, `notation`), `version`, `createdBy` (`PrincipalRef`), `createdAt` and `updatedAt`. The list items are `Diagram` as well.
- **String limits** (conventions check, rule 2, no allowlist entries):
  - `name` `minLength: 1`, `maxLength: 200`;
  - `description` `maxLength: 2000`;
  - the query parameter `q` `maxLength: 200`.
- **Page envelope** (conventions check, rule 3): `DiagramPage` has `items`, `page` and `nextCursor`.
- **Error answers:**
  - 422 `urn:oea:problem:diagram-name-taken` naming `name` (as `dashboard-name-taken`);
  - 422 `validation-failed` naming `name` for a blank or over-long name, `description` for an over-long description and `viewpointId` for a missing or unknown viewpoint (declared contract violation, as for dashboards);
  - 400 for `pageSize` outside 1–200, `page` below 0 or `q` over 200 characters; 400 naming `cursor` for a cursor of another query;
  - 404 for an unknown diagram; 412 and 428.
- **Other schemas:**
  - `ViewpointConfig` (used by list, get, create and update of viewpoints) gets the required `diagramCount` (integer, minimum 0);
  - `BrowserCurrentState` gets the required `diagrams`; the `category` parameter of `listBrowserItems` gets `diagrams` (only with `current-state`, else 422 naming `category`); `BrowserItem.kind` gets `diagram`, and `BrowserItem` the required, nullable `notation` (`ViewpointNotation`, set for diagrams only);
  - `TreeNodeItemCreate.itemType` and the item type of the tree entries get `diagram` (with `referenceId`);
  - the import result gets `diagramsDeleted` (integer or null, like `viewpointsDeleted`).
- `deleteViewpoint` and `importMetamodelPackage` describe the deletion of the diagrams.
- The planned `getDiagramView` stays planned for change 11.

### D3 Service `Diagrams`

- **Principals:** persons and service accounts with `entity:write` write; the principal's `personId` is the actor. A principal without a person (the local system admin account) gets 403.
- **Creating:** it trims the name, checks it for blanks and uniqueness ignoring case (with the unique index as the final guard, translated to 422 `diagram-name-taken`), checks that the viewpoint exists (and translates a foreign key violation to 422 naming `viewpointId`), and audits.
- **Changing:** it locks the row, checks `If-Match`, applies name and description, raises the version, sets `updated_at` and `updated_by`, and audits the changed fields; a write that changes nothing raises the version and is audited with an empty field list, as for viewpoints.
- **Deleting:** it locks, checks `If-Match`, deletes and audits.
- **Listing:** `lower(name), id` order, with a cursor built from them as in `getSolutionLandscape`, and a name filter with `ILIKE` on an escaped pattern (`%`, `_` and `\` matched literally).
- **Exceptions:** `app.diagram` has its own exception (e.g. `DiagramChangeException` for 422, 412 and 428), mapped in `ApiExceptionHandler`. It does not use the exceptions of `app.history`, because `app.history` depends on `app.metamodel`, which depends on `app.diagram` (D4): that would be a package cycle.
- **Audit details** (catalog entry `diagram.created` / `diagram.updated` / `diagram.deleted`, target `diagram` with its ID):
  - all three: `name` and `viewpointId`;
  - `diagram.updated`: `fields`, the changed fields among `name` and `description`;
  - `diagram.deleted` by a viewpoint deletion or a package re-import: `cause` `viewpoint-deleted`.

### D4 Viewpoint deletion

- `Viewpoints.delete` locks the viewpoint and checks it (system-defined 422, `If-Match`) as before, then deletes the diagrams of the viewpoint through `Diagrams` in the same transaction, each audited as `diagram.deleted` with `cause` `viewpoint-deleted`, and then deletes the viewpoint and audits `viewpoint.deleted`. A rejected deletion deletes no diagram.
- The package import does the same for each viewpoint of the package it removes, after locking that viewpoint row (`ViewpointStore.lockVersion`), and counts `diagramsDeleted` in the result and in the details of `metamodel.package-imported`.
- **Locking:** the viewpoint row is locked before its diagrams are deleted. A concurrent `createDiagram` for that viewpoint waits on the foreign key and then fails with 422 naming `viewpointId`; one that committed before is deleted with the others.
- **Dependency direction:** `app.metamodel` calls `app.diagram`; `app.diagram` depends only on `app.security`, `audit`, `core.diagram` and `core.metamodel.ViewpointStore` (for the viewpoint's existence, name and notation). No class of `app.diagram` imports `app.metamodel`, `app.history` or another feature that depends on `app.metamodel`. `CycleArchitectureTests` stays green.
- `diagramCount` comes from count queries in `DiagramStore` (one grouped query for the list), called by `Viewpoints`; `core.diagram` does not depend on `core.metamodel`'s stores in the other direction.

### D5 Browser and navigation tree

- **Browser** (`app.navigation.Browser`, offset paging as the other categories):
  - `CurrentState` gets `diagrams`, counted by `DiagramStore` (ADR-051: the table belongs to `core.diagram`, other features use its store).
  - The category `diagrams` of `current-state` lists the diagrams by `lower(name)`, then ID, in pages of 1–200 (default 50); `solution` with `diagrams` gives 422 naming `category`.
  - Each item has `kind` `diagram`, `id` the diagram UUID, `notation` the notation of its viewpoint as marker, `metatypeAbbreviation`, `entityId`, `solutionId` and `deltaType` null, `childCount` 0, `structuringAid` false and `version` 0.
  - A click opens `/diagrams/{id}`.
- **Navigation tree** (`app.navigation.NavigationTree`, `core.navigation.NavigationTreeStore`):
  - Entry type `diagram`, with `referenceId` the diagram ID, under the rules of `solution` entries: `viewpoint:manage` and `entity:read` (rule "Who changes the tree"), once per folder, at the end, the folder limit of 200, audit `tree-node.item-added` and `tree-node.item-removed` with `itemType` `diagram` and `referenceId`.
  - The existence check uses `DiagramStore`; the visible-entries query joins `diagrams` as it joins `catalogs` and `solutions`, and leaves out entries whose diagram does not exist. The rows of a deleted diagram stay in `tree_node_items`, as for deleted catalogs.
  - "Place in navigation" on the diagram page for holders of `viewpoint:manage` and `entity:read`, as on the catalog and solution page.

### D6 Frontend

- **`DiagramsPage.vue`** (SCR-067, route `/diagrams`):
  - the list with name, viewpoint, notation and last change, the name filter and "Load more" (cursor);
  - for `entity:write`: "Create diagram" (dialog with name, description and viewpoint chosen from `listViewpoints`), "Rename" and "Delete" with a confirmation;
  - the empty state "No diagrams yet.";
  - a header entry "Diagrams" for readers with `entity:read`.
- **`DiagramPage.vue`** (route `/diagrams/:id`): name, description, viewpoint, "Rename" and "Delete" for `entity:write`, and "Place in navigation" for `viewpoint:manage`. The canvas follows in change 11; until then a short notice says the canvas follows.
- **SCR-031:**
  - the deletion confirmation says "N diagrams will be deleted" when `diagramCount` > 0, and has no such text at 0 (US-093);
  - the edit dialog shows the non-blocking warning "N diagrams affected" when an allowed metatype or connection type is removed from a viewpoint with `diagramCount` N > 0 (US-090 AC2). Assumption: until diagrams have shapes (change 11), every diagram of the viewpoint counts as affected; change 11 narrows N to the diagrams that show an entity of a removed type.
- **Sidebar SCR-055:** the browser category "Diagrams" with the notation as marker, and the tree entry with a diagram icon; both open the diagram page.
- **Stories:** SCR-067 (list, empty, reader, create dialog); SCR-055 and SCR-031 are extended.

### D7 Documentation

- **The manual (en/de):** a new page "Diagrams", and "Viewpoints" and "Navigation" extended.
- **SCREENS.md:** SCR-067 is new and `implemented`.
- **The business object Diagram:**
  - plateau and layout marked as later;
  - `createdBy` a person or service account;
  - the lifecycle: deleted diagrams are restorable (D8);
  - the attribute limits as implemented.
- **`docs/data-model.puml`.**
- **Stories and use cases:**
  - US-090 AC2 and US-093 AC1 done; US-093 AC1 realigned from "will be affected" to "will be deleted" (owner decision 2026-11-19);
  - US-074 with an implementation note (creating a diagram);
  - UC-10, UC-12 and UC-13 realigned.
- **The audit event catalog:** `diagram.*`.
- **The sprint plan:** the status of change 10.

### D8 Restorable deletion (owner decision 2026-11-19)

- The owner decided on 2026-11-19 that deleted diagrams are restorable already now, as the business object foresees (`active → deleted → restored`).
- **Deleting:**
  - `deleteDiagram` sets `deleted_at` and `deleted_by` and raises the version;
  - a deleted diagram is not listed, not counted (browser, `diagramCount`) and answers 404 on `getDiagram`, `updateDiagram` and `deleteDiagram`;
  - its name is free again (partial unique index).
- **Listing deleted diagrams:** `listDeletedDiagrams` (`GET /diagrams/deleted`, `entity:restore`, the same paging and name filter) returns `DeletedDiagramPage` with `items` of `Diagram` plus `deletedAt` and `deletedBy`.
- **Restoring:** `restoreDiagram` (`POST /diagrams/{id}/restore`, `entity:restore`, `If-Match` with the version of the deleted diagram) clears the deletion and raises the version, then audits `diagram.restored`.
  - 404 for an unknown diagram;
  - 409 `diagram-not-deleted` for a diagram that is not deleted;
  - 422 `diagram-name-taken` when an active diagram has the name;
  - 412 and 428.
- **Navigation tree entries** of a deleted diagram stay stored and are left out when the tree is read, so they show again after the restore.
- **Viewpoint deletion and package re-import** remove the diagrams of the viewpoint for good, deleted ones included, since a diagram cannot exist without its viewpoint. The warning counts the diagrams that are not deleted.
- **SCR-067:** holders of `entity:restore` get the switch "Show deleted", which lists the deleted diagrams with deletion time, actor and "Restore".

## Data Model

The new `Diagram` table (D1), without layout. `docs/data-model.puml` and the business object are aligned.

## API

D2. Five operations under the new tag `Diagrams`, and the extended viewpoint, browser, tree and import schemas.

## Risks / Trade-offs

- **[Risk]** Deleting a viewpoint deletes many diagrams at once.
  - **Mitigation:** the client warns with the number (owner decision), and each deletion is audited.
- **[Risk]** A package re-import deletes diagrams without a dialog.
  - **Mitigation:** only system-defined viewpoints that the package drops are affected. The import result names the number, and the audit records each diagram.
- **[Trade-off]** Diagrams deleted with their viewpoint are removed for good (D8), since they cannot exist without it; the warning names their number.
- **[Trade-off]** Tree entries of a deleted diagram stay as hidden rows and count toward the folder limit of 200, as those of deleted catalogs.
