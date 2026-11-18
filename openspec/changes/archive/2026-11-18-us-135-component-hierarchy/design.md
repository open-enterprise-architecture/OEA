# Design

## Context

- **Entity columns (V6):** `entities` already has `parent_entity_id INTEGER REFERENCES entities (id)`, `is_structural_grouper` and `auto_created`; no code writes them.
- **Connections:** they are entities of a metatype with `is_connection = true`, plus a row in `entity_connections` (V19). They are written through `createConnection` and the batch, with endpoints checked against `metatype_connection_constraints`.
- **Entity changes:** they write versions with deltas (ADR-022) through the existing write path of the batch and the restore. A blocked entity (open issue, US-153) rejects writes with 409.
- **Restore:** it requires `If-Match` with the entity version (428/412).
- **Metatypes:** they belong to a metamodel configuration and have `scope` `built-in`, `imported` or `organization`. No built-in metatype exists yet.
- **Browser:** change 1 (`us-132-solution-browser`) built the browser: `getBrowserView` and `listBrowserItems` in `NavigationTreeController`, `Browser` in `app/navigation`, `EntityStore.pageActive`, and `SolutionBrowser.vue`.

## Goals / Non-Goals

**Goals:** as in `specs/repository/entity-hierarchy`, the composition mark in `specs/metamodel/connection-metatypes`, and the hierarchy in the browser in `specs/navigation/solution-browser`.

**Non-goals:**

- a hierarchy of deltas inside solutions;
- editing auto-created connections;
- a general `deleteEntity` (it stays `planned`);
- renaming a structuring aid (follows with entity editing in the browser);
- capturing structuring aids or the hierarchy in solutions.

## Decisions

### D1 – Migration `V42__entity_hierarchy.sql`

- **Structuring aid metatype:** insert it with a fixed UUID (name-based, like the starter package IDs), `name = 'Structuring aid'`, `is_connection = false`, `scope = 'built-in'`, no abbreviation and no source package (`metatypes_origin` holds), in the instance configuration `00000000-0000-0000-0000-000000000001` (V5). The UUID is a constant in `core/metamodel` (e.g. `BuiltInMetatypes.STRUCTURING_AID`).
  - Assumption: no organization metatype of the instance is named "Structuring aid" (`metatypes_name_per_config`); otherwise the migration fails with a clear message.
  - The name is English only. `i18n_entries` allows only `imported` and `organization` labels (V17), so the migration adds no label; an organization label can be added in "Translations" as for any metatype.
- **Composition mark:** `ALTER TABLE metatypes ADD COLUMN is_composition BOOLEAN NOT NULL DEFAULT false`, with:
  - the check `NOT is_composition OR is_connection`;
  - the unique partial index `metatypes_one_composition ON metatypes ((true)) WHERE is_composition`, so there is at most one per instance; the service allows the mark only in the instance configuration (an extension gives 422 naming `isComposition`).
- **Index:** `entities_parent ON entities (parent_entity_id) WHERE deleted_at IS NULL AND parent_entity_id IS NOT NULL`.
- **No defaults or grants needed:** `is_structural_grouper` and `auto_created` are `NOT NULL DEFAULT false` (V6), and `oea_app` already has `UPDATE` on `entities`.
- **History of the parent field:** the history stores system fields as rows of `entity_system_field_history` with the value before the change (V6, read by `EntityVersionStore`). Its `field_name` check (`entity_system_field_history_field_name_check`) is dropped and re-added with `parent_entity_id`; old and new values are the parent ID as text or null. The version tables of V27, V32 and V33 are not touched. The engineer extends:
  - `EntityVersionStore`: the key constant `PARENT = "parent_entity_id"`, read by `currentSystemFields` for elements (also when null), so `state()` reconstructs it;
  - `EntityVersions.SYSTEM_ORDER` and its name resolution (as for `SOURCE`/`TARGET`), so versions and diffs show the parent's name;
  - `EntityHistory.SYSTEM_FIELDS`: `parent_entity_id` → contract key `parentEntityId`, label "Parent";
  - `FieldDiff` and `EntityVersionChange` in the contract name the key `parentEntityId`.
- **Restore:** "Parent" is not restored, like the metatype and the endpoints of a connection (`EntityRestore` compares name, description, `isLogical` and properties only). The hierarchy is changed with "Move to…"; a restore whose only difference is the parent gives 422 `nothing-to-restore`, as today.

### D2 – Contract (`api/openapi.yaml`)

All three writes have the tag `Entities – Write`, problem types as in `restoreEntity`: 409 `urn:oea:problem:entity-blocked` with the extension member `entities`, 412 `PreconditionFailed`, 428 `PreconditionRequired`, 422 `validation-failed` with `invalidFields`.

**`setEntityParent` – `PUT /entities/{id}/parent` (`EntityIdParam`), `entity:write`:**

- headers: `IfMatchRequiredHeader` (428 without, 412 when stale);
- body `EntityParentUpdate { parentEntityId: integer (minimum 1) | null }`;
- 200 `EntityParentResult { entityId, version, parentEntityId, composition: { created: boolean, connectionId: int | null, reason: 'created' | 'structuring-aid' | 'no-composition-metatype' | 'not-allowed' | 'unchanged' }, removedConnectionId: int | null }`;
- errors 400, 401, 403, 404 (unknown or deleted child), 409 (blocked), 412, 413, 422 (naming `parentEntityId` or `entityId`), 428, 429.

**`createStructuringAid` – `POST /structuring-aids`, `entity:write`:**

- body `StructuringAidCreate { name: 1–255 (trimmed, not blank), description?: ≤ 2000, parentEntityId?: integer (minimum 1) }`;
- 201 with `Location` and `StructuringAid { id, name, description, parentEntityId, version }`;
- errors 400, 401, 403, 413, 422 (naming `name`, `description` or `parentEntityId`), 429; no 409, because the parent itself is not changed.

**`deleteStructuringAid` – `DELETE /structuring-aids/{id}`, `entity:write` and `entity:delete` (soft delete, ADR-019, like `deleteEntity`):**

- `IfMatchRequiredHeader`;
- 204;
- errors 401, 403, 404 (unknown or already deleted), 409 (the aid or a child is blocked), 412, 422 naming `entityId` (not a structuring aid), 428, 429.

The aids get their own path instead of `/entities/structuring-aids`, so that no literal segment competes with `/entities/{id}` and its sub-paths.

**Other schema changes:**

- `isComposition: boolean` on `MetatypeDetail` (read, and the package format) and optional on `MetatypeWrite`.
- `listBrowserItems` gets the optional `parentEntityId` (integer, minimum 1; only with `scope=current-state` and `category=components`, else 422 naming `parentEntityId`; 404 when it is no element entity that is not deleted).
- `BrowserItem` gets `childCount: integer`, `structuringAid: boolean` and `version: integer` (entity items; null or 0 for catalogs and deltas).
- `FieldDiff` and `EntityVersionChange` list `parentEntityId` among the system fields, shown by name.
- The planned `Entities – Write` operations stay `planned`; the three new ones are implemented.

### D3 – Service `app/entity/EntityHierarchy` and store (`core/entity`)

`setParent(entityId, parentId | null, ifMatch, actor)` runs in one transaction and reuses:

1. **Hierarchy lock:** `pg_advisory_xact_lock` on a fixed key, as `AdministrationPaths.requireRemainingPath` does, so concurrent moves cannot create a cycle.
2. **Lock and load the child:** `EntityStore.lock` (`FOR UPDATE`); `StoredEntity` and its `SELECT` get `parentEntityId`. Check the version (412), not deleted (404), element metatype through `MetatypeRules` (422 `entityId`), and not blocked with `EntityBlocks.check` (409, `EntityBlockedException`).
3. **Validate the parent:** `EntityStore.lockForShare(parentId)`; it exists, is not deleted, is an element (else 422 `parentEntityId`), and is not the child itself.
4. **Cycle and depth:** a recursive CTE over `parent_entity_id` from the new parent up to the root, stopping at a deleted entity, gives the level of the parent (root level = 1) and checks that the child is not an ancestor; a second CTE gives the number of levels of the child's subtree, the child included, over children that are not deleted. The rule is `level(parent) + levels(subtree) ≤ 10`; otherwise 422 `parentEntityId`.
5. **Unchanged:** if the parent is the same as before, return `reason=unchanged`, with no version and no audit event.
6. **Remove the old connection:** if there was an old parent, find the connection with `auto_created = true`, source old parent and target child that is not deleted; soft-delete it with `EntityStore.softDelete` and audit `entity.deleted` with the detail `cause` = `parent-changed` instead of `solutionId`. The audit part of `SolutionGoLive.retire` is private: extract it into a shared helper in `app/entity` (e.g. `EntityDeletion`) used by both.
7. **Write the change:** a new `EntityStore.updateParent(entity, parentId, personId, now)` sets `parent_entity_id`, the next version and `updated_*`, and writes the version header and the history row `parent_entity_id`. `EntityWriter.write` is not used: it compares only name, description, `isLogical` and properties.
8. **Create the composition:** if neither entity is a structuring aid, find the metatype with `is_composition`. Check the pair with `MetatypeRules.allows` (subtypes included), as `EntityBatch.prepareConnection` does. If allowed:
   - build the input with the default name "parent → child" (cut to 255, as in `prepareConnection`);
   - check it with `EntityValidator.check(..., asConnection = true, Necessity.WARN)`, so an empty mandatory property of the composition metatype is marked as missing, as after a restore, instead of blocking;
   - write it with `EntityWriter.write(principal, checked, null, Map.of(), context, new Endpoints(parentId, childId))` (version 1, `entity.created`), and set `auto_created = true` with a store method in the same transaction.

   `EntityBatch.createConnection` is not reused: it opens its own transaction, checks property write access and reports errors instead of a reason. Otherwise record the reason.
9. **Audit:** `entity.parent-changed`, named like `person.status-changed`, category `change`, target the child with `entity_id` and `entity_name` (empty for modeled persons, ADR-033 §3), details `version`, `fromParentId`, `toParentId`, `createdConnectionId`, `removedConnectionId`, and one change row `parent_entity_id` with old and new ID.

**Structuring aids:**

- `createStructuringAid` writes an entity of the built-in metatype through `EntityValidator.check` and `EntityWriter.write` (version 1, `entity.created`), and sets `is_structural_grouper = true` and the parent in the same transaction. With a parent, step 1, step 3 and step 4 apply; no composition is created and no history row is written for version 1.
- `deleteStructuringAid` takes the hierarchy lock, locks the aid, checks it (422 if it is not an aid), and then:
  - locks its children in ID order and checks that neither the aid nor any child is blocked (409);
  - clears the parent of each child through the internal path of `setParent` (no `If-Match` per child), each with a new version and `entity.parent-changed`;
  - soft-deletes the aid with `entity.deleted` (`cause` = `structuring-aid-deleted`).

  No limit on the number of children; one transaction.
- **Keeping the metatype to its purpose:**
  - the batch (`EntityBatch.prepare`) and the delta capture of solutions (`SolutionDeltas`) reject the structuring-aid metatype with 422 naming `metatypeId`; the batch keeps rejecting `parentId` (`EntityValidator`, ADR-021 note), as the hierarchy is set only through `setEntityParent`;
  - `MetatypeRules.allows` and `AllowedConnections.between` never allow a structuring aid as source or target, so no connection starts or ends at one (US-136: no semantic connections);
  - changing and deleting it are already refused by `MetatypeEditing` (409 `read-only-metatype`, built-in scope); `PropertyMappings` refuses mappings to it with the same 409, although it allows mappings to other built-in metatypes;
  - `MetatypeEditing` and `PackageImporter` refuse it as base type or as allowed endpoint metatype (422 naming `baseMetatypeId`, or the package field).

### D4 – Composition mark and package

- **Metatype create and update:** `isComposition` is accepted for organization connection metatypes of the instance configuration; it is a changed field of `metamodel.metatype-updated` and raises the metatype version. An element metatype, an extension configuration, or a second mark (the unique index or the check) give 422 naming `isComposition`. Imported metatypes are read-only, as before.
- **Package import:** a metatype in the package may carry `isComposition: true`. Only one per package, and only on connection metatypes (422 in the validator). On import, if the instance already has a different composition metatype, the import is rejected with 422 naming `isComposition`; nothing is stored.
- **Starter package:** `packages/oea-starter-togaf-classic.json` becomes 1.3.0 and gets its first connection metatype, "Composition" (name-based UUID like the others, abbreviation "CO", German label "Komposition" in `i18nBundles`), with `isComposition: true` and no `connectionConstraints`, so it allows every element metatype except the structuring aid. `StarterPackagesTests`, `RepositoryInstance` and the other tests that import it are updated.

### D5 – Browser

**Backend:**

- `EntityStore.pageActive` gets the variant `pageChildren(parentId)`.
- The root level is elements whose `parent_entity_id` is null or points to a deleted entity.
- Each row carries `childCount` (children not deleted) and `structuringAid`.
- The count "Components (N)" of "Current state" stays the number of all elements.
- `parentEntityId` with another scope or category gives 422; one that is no element entity that is not deleted gives 404.

**Frontend (`SolutionBrowser.vue`, `BrowserNodeRow.vue`, `browserNodes.ts`):**

- **Children:** an element with `childCount > 0` is expandable and loads its children lazily, in pages.
- **Structuring aids:** they show `i-lucide-folder` instead of the abbreviation badge.
- **Drag and drop**, for holders of `entity:write`:
  - a component can be dropped onto another component or onto "Components" (root level);
  - invalid targets (itself, a descendant already loaded in the client, a connection, a delta row) refuse the drop;
  - the drop sends `setEntityParent` with `If-Match` of the version, which the item needs, so `BrowserItem` also gets `version`;
  - the server decides cycles and depth beyond the loaded nodes, and a 422 is shown as a toast.
- **"Move to…":** a dialog with a search over the components (through `listBrowserItems`) and "Root level".
- **"New structuring aid":** on "Components" and on any element; it creates the aid there.
- **"Delete":** only on structuring aids and for holders of `entity:write` and `entity:delete`, with the warning "N children move to the root level and are not deleted".
- **Refresh:** after a write, the browser reloads the affected parent nodes and the counts.
- **Composition mark:** `MetatypeDialog.vue` (`src/components/metamodel/`, screen SCR-030) gets the switch "Composition" for organization connection metatypes of the instance.
- **Supporting files:** fakes in `src/test/navigation.ts` and `src/test/metamodel.ts`; stories in `SCR-055-navigation-sidebar.stories.ts` (hierarchy, structuring aid, refused drop target, "Move to…", delete warning) and `SCR-030-metamodel-configuration.stories.ts` (the switch).

### D6 – Documentation and data model

- **Manual (en/de):**
  - "Navigation" gets the sections "Arrange components" and "Structuring aids";
  - "Metamodel editing" gets "Composition";
  - "Metamodel packages" covers `isComposition` and the starter package 1.3.0.
- **Business objects and data model:**
  - `business-objects/entity.md`: the parent, the structuring aid and the auto-created connection;
  - the metatype business object: `isComposition` and the built-in metatype;
  - `docs/data-model.puml`.
- **Audit catalog:** `docs/audit-event-catalog.md` gets `entity.parent-changed`; `entity.deleted` names its new causes, the detail `cause` and the deleting person.
- **Requirements:**
  - UC-13, REQ-141 and REQ-142 are realigned (the connection is a connection entity, not an entity of type `composition`; the composition metatype is configurable);
  - US-135 and US-136 are set to `done`;
  - ADR-021 gets a note on its implementation.

### D7 – Security

- **Owner decision 2026-11-18:** the built-in role `solution-architect` gets `entity:write` (contract `x-oea-builtin-roles` and V42), but not `entity:delete`.
- **Authorization:** all three writes need `entity:write`, like the batch; deleting a structuring aid also needs `entity:delete`, as every soft delete (ADR-019, ADR-032). The automatic removal of an auto-created connection is part of the parent change and needs no `entity:delete`. Changing `isComposition` needs `metamodel:write`, like any metatype change. Reading children through the browser needs `entity:read`.
- **Integrity:**
  - `If-Match` on the parent change and the deletion of an aid prevents lost updates;
  - the advisory lock prevents concurrent moves from creating a cycle;
  - blocked entities give 409.
- **Input limits, with both sides tested:**
  - the aid name 1–255 (255 accepted, 256 and blank rejected);
  - the description ≤ 2000 (2000 accepted, 2001 rejected);
  - depth 10 (level 10 accepted, level 11 rejected, also for a moved subtree);
  - `parentEntityId` ≥ 1 on the writes and on `listBrowserItems` (1 accepted, 0 gives 400);
  - the browser `pageSize` as in change 1.
- **No escalation through the composition:** the auto-created connection is written only for a pair the composition metatype allows, through the same endpoint check as `createConnection`. It needs no permission beyond `entity:write`, because `createConnection` needs `entity:write` too.
- **Audit:**
  - `entity.parent-changed` carries IDs only, no names;
  - the created and removed connections are audited by their write paths;
  - a rejection writes no event.
- **Output encoding:** names in the browser and in "Move to…" are rendered as text.

## Risks / Trade-offs

- **History:** the parent becomes a system field of the history; its check constraint is widened and the readers of D1 extended. Versions before the first parent change reconstruct it as none, through the history row of that change.
- **Many children:** deleting an aid with thousands of children writes thousands of versions in one transaction; accepted for now (structuring aids are navigation aids, typically small).
- **Deleted parents:** they leave children pointing to them. These children show at the root level, and the next parent change clears the pointer.

## Migration Plan

V42 is additive. The starter package 1.3.0 is offered as an update like 1.2.0; instances that do not import it have no composition metatype and store only the hierarchy.

## Open Questions

None.
