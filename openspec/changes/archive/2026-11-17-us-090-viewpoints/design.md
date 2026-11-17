# Design

## Context

- **Contract:** `listViewpoints`, `getViewpoint` and `updateViewpoint` under `/admin/viewpoints` are `planned`, with `ViewpointConfig` (`id`, `name`, `description`, `allowedMetatypes`, `allowedRelations`). Reading needs `entity:read` or `viewpoint:manage`, and writing `viewpoint:manage`.
- **Permission:** `viewpoint:manage` exists (V3), and `enterprise-architect` holds it. It is used today for the navigation tree.
- **Metatypes (V5):** `metatypes` has `config_id` (instance or solution extension) and `is_connection`. A metatype is deleted only while it has no entities.
- **Metamodel page (SCR-030):** it has sections with the route `/metamodel/:section?/:id?`, and the metatype writes use `version` and `If-Match` (scr-030 D4).

## Goals / Non-Goals

**Goals:** generic viewpoints with create, read, update and delete, the checks, optimistic locking, audit, and the section SCR-031.

**Non-Goals:** notations, notation mapping, system-defined viewpoints, diagram counts, export and import, and viewpoints of extensions.

## Decisions

### D1 – Schema (`V39__viewpoints.sql`)

- **Table `viewpoints`:**
  - `id UUID PRIMARY KEY`;
  - `name TEXT NOT NULL CHECK (length BETWEEN 1 AND 255)`, with a unique index on `lower(name)`;
  - `description TEXT CHECK (length <= 1000)`;
  - `version INTEGER NOT NULL DEFAULT 1`;
  - `created_at`, `created_by`, `updated_at` and `updated_by`.
- **Link tables:** `viewpoint_metatypes` and `viewpoint_connection_types`, each `(viewpoint_id, metatype_id)`.
  - Both are primary keys with `ON DELETE CASCADE` on both foreign keys, so that a deleted metatype leaves the viewpoint.
  - The kind (element or connection) and the configuration are checked by the service, because a check cannot read another table.
- **Grants:** the runtime role gets `SELECT`, `INSERT`, `UPDATE` and `DELETE` on the three tables.

### D2 – Store and service

- `core/metamodel/ViewpointStore`: `list`, `find`, `insert`, `update` (with the expected version, returning whether it matched), `delete` (with the expected version), `currentVersion`, and `kinds(ids)`, which returns per metatype its `is_connection` and whether it belongs to the instance configuration.
- `app/metamodel/Viewpoints`: `create`, `update`, `delete`, `get` and `list`.
  - The checks run in this order: the name (blank or taken), at least one metatype, then the kind and configuration of each ID.
  - The service audits `viewpoint.created`, `viewpoint.updated` (`fields`: `name`, `description`, `metatypeIds`, `connectionTypeIds`) and `viewpoint.deleted`.
  - Errors reuse `MetamodelChangeException.invalid(field, detail)` (422) and the stale-version and precondition handling of the metamodel (412 with `currentVersion`, 428).

### D3 – Contract (delta to `api/openapi.yaml`)

- **`/admin/viewpoints`:**
  - `GET listViewpoints`, without the `planned` marker;
  - `POST createViewpoint`, taking `ViewpointWrite` and answering 201 with `ViewpointConfig`, `Location` and `ETag`.
- **`/admin/viewpoints/{id}`:**
  - `GET` answers with an `ETag`;
  - `PUT` and `DELETE` require `If-Match`, and answer 412 and 428.
  - The `id` is a UUID.
- **`ViewpointConfig`:** `id` (uuid), `name`, `description`, `allowedMetatypes`, `allowedRelations` (the connection types, the name kept from the planned schema), `version`, `createdAt` and `createdBy`.
- **`ViewpointWrite`:** `name`, `description`, `metatypeIds` (1–500) and `connectionTypeIds` (0–500).

### D4 – Frontend

- `ViewpointsSection.vue` in `MetamodelPage.vue` (section `viewpoints`, SCR-031):
  - a list with the name, description and the type badges;
  - for holders of `viewpoint:manage`, "New viewpoint", "Edit" and "Delete".
- `ViewpointDialog.vue`:
  - the name and description;
  - two checkbox groups, one for the element metatypes and one for the connection metatypes of the instance (from `fetchMetatypes`);
  - stale versions use `CollisionWarning`.
- Deletion uses the existing `DeleteDialog`, with a plain confirmation.
- `api/viewpoints.ts`.

### D5 – Documentation and data model

- `business-objects/viewpoint.md` (0.2.0): the generic form; notation, notation mapping and system-defined viewpoints marked as following with the diagrams.
- `docs/data-model.puml`: the three tables.

## Risks / Trade-offs

- **Generic viewpoints without notation:** diagrams will add the notation as a new, optional attribute with a default. No migration of existing viewpoints is needed.
- **A metatype deleted silently leaves viewpoints:** acceptable, because only metatypes without entities can be deleted, and the change is visible in the viewpoint.

## Migration Plan

- V39 adds new tables; there is no data migration.

## Open Questions

- None. The assumptions of the proposal are open for review in the proposal pull request.
