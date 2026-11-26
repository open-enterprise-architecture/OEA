# Proposal

## Why

Since change 14 (`s17-entity-delete`) entities can be deleted and restored through `restoreDeletedEntity`. No screen lists deleted entities, so a restore needs the entity ID. The owner decided on 2026-11-20 that deleted entities are found on a page "Deleted entities" and through "Show deleted" in the catalog, both for holders of `entity:restore`.

This is S17 change 15 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| ADR | [ADR-019](../../../adrs/ADR-019-soft-delete-strategy.md) (amendment 2026-11-20) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`entity:restore`), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Screens | SCR-020 "Catalog Browser", SCR-068 "Deleted entities" (new) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decision of 2026-11-20:** deleted entities are found on the page "Deleted entities" and through "Show deleted" in the catalog, both for `entity:restore`.

**Decisions and assumptions of this change (open for review):**

- **New list:** `listDeletedEntities` (`GET /entities/deleted`, `entity:restore`).
  - Ordered by the deletion time descending, then by ID.
  - Paging with `PageParam`, `PageSizeParam` and `CursorParam`, and the envelope `items`/`page`/`nextCursor`.
  - Filters `q` (name part, at most 255 characters, wildcards matched literally) and `metatypeId`.
  - The entries carry no property values, so property visibility does not apply.
- **Catalog query:** it gets `includeDeleted` (boolean, `entity:restore` as a handler-enforced condition).
  - Deleted rows are marked with `deleted`, `deletedAt` and their `version` (for the restore).
  - In a connection catalog, a deleted connection shows its endpoints even when they are deleted.
  - Columns, visibility, filters, sorting, paging, joins and the time limit are unchanged.
  - The joined columns still show only active joined entities.
  - The portal query does not get the parameter.
- **SCR-020:** the switch "Show deleted" for `entity:restore`, deleted rows greyed with a marker, and the row action "Restore" (`restoreDeletedEntity`). The switch is not stored in saved views.
- **SCR-068:** the page "Deleted entities" at `/entities/deleted`, with a header entry for `entity:restore` only.
  - The list shows the cascade origin ("Deleted with ERP"), with filters, "Load more" and "Restore" with a confirmation.
  - After a restore, a notice names the restored entities and those that stayed deleted.
- **Restore:** it uses `restoreDeletedEntity` of change 14 with the version of the entry; 412 or 409 reload the list.

**Not in this change:**

- deleting from the catalog or the page;
- hard delete and purge;
- deleted entities in other reads (search, browser, history).

## Capabilities

### New Capabilities

- `repository/deleted-entities`: the list of deleted entities and the page "Deleted entities".

### Modified Capabilities

- `catalog/catalog-query`: showing deleted rows with `includeDeleted`.

## Impact

- **Contract:** `listDeletedEntities`, `includeDeleted` on `queryCatalog`, and the optional `deleted`, `deletedAt` and `version` on `CatalogRow`.
- **Backend:**
  - `core.entity` (the list query);
  - `core.catalog` (the `includeDeleted` filter);
  - `app` services and controllers.
- **Frontend:** `DeletedEntitiesPage.vue` (SCR-068), the catalog switch and row action, the header entry, stories and fakes.
- **Documentation:** the manual "Deleted entities" (new) and "Catalogs" (en/de), SCREENS.md and the sprint plan.
