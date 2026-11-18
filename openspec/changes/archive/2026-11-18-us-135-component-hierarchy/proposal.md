# Proposal

## Why

Real architectures are hierarchical: an ERP system contains a finance and a logistics module. UC-13 asks to arrange components under each other in the browser, so that the composition becomes visible (US-135, REQ-141, REQ-142). Purely navigational groupings, called structuring aids, must also be possible without adding semantic connections to the model (US-136).

The entity table already carries `parent_entity_id`, `is_structural_grouper` and `auto_created` (V6), but no operation writes them.

This is S16 change 2 ([sprint plan](../../../docs/sprint-plan.md), UC-13 2/2). It builds on change 1 (`us-132-solution-browser`, the browser tab) and takes migration V42.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-13](../../../requirements/use-cases/UC-13-manage-navigation-tree.md) |
| User Story | [US-135](../../../requirements/user-stories/US-135-place-component-hierarchically.md) (AC1–AC3), [US-136](../../../requirements/user-stories/US-136-structuring-aid-without-connection.md) (AC1–AC3) |
| Requirements | [REQ-141](../../../requirements/req/REQ-141-component-hierarchy-explorer.md), [REQ-142](../../../requirements/req/REQ-142-implicit-parent-child-connection.md) |
| ADRs | [ADR-020](../../../adrs/ADR-020-explorer-navigation-model.md), [ADR-021](../../../adrs/ADR-021-implicit-parent-child-connection.md) (implicit connection), [ADR-022](../../../adrs/ADR-022-structured-property-model.md) (versions as deltas), [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S16 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-18:**

- **Where the hierarchy takes effect:** placing a component under another one changes the entity directly in the catalog. The change is versioned like other entity changes, needs the right to write entities, and is blocked by open issues.
- **Role:** the built-in role `solution-architect` gets `entity:write` (not `entity:delete`), so that Solution Architects can arrange components and create structuring aids; only holders of `entity:delete` delete a structuring aid.
- **Composition connection:** the automatic connection uses a connection metatype marked as *composition* in the metamodel. Without such a metatype, only the hierarchy is stored. The starter package gets a "Composition" connection metatype.

**Decisions and assumptions of this change (open for review):**

- **Parent:**
  - The parent of an element entity is another element entity that is not deleted, or none.
  - Connections have no parent and are no parent.
  - A cycle, the entity itself, or a depth of more than 10 levels give 422 (REQ-141).
  - Setting the parent writes a new version of the child, with the field "Parent" in its history; the parent entity is unchanged. A restore does not restore the parent.
- **Structuring aid:**
  - A structuring aid is an entity of the new built-in element metatype "Structuring aid", created by the migration in the instance metamodel. It is read-only and has no properties.
  - Its entities carry `isStructuralGrouper=true`.
  - It is created and deleted with dedicated operations; deleting needs `entity:delete` too, as every soft delete.
  - The batch and solutions cannot create one, and no connection starts or ends at one.
  - Deleting it moves its children to the root level, each with a new version, and soft-deletes it (US-136 AC3).
- **Automatic connection:**
  - It is created when the parent and the child are no structuring aids, and when the composition metatype allows the parent's metatype as source and the child's as target.
  - The connection is marked `autoCreated=true`.
  - Moving the child elsewhere or detaching it soft-deletes the connection if it is still auto-created and not deleted.
  - If the metatype does not allow the pair, only the hierarchy is stored, and the response says why.
- **Composition mark:** `isComposition` on a connection metatype, at most one per instance.
  - It is set with the metatype by holders of `metamodel:write`, or by a package, in the instance configuration only.
  - The starter package 1.3.0 brings "Composition", with no endpoint restriction.
- **Browser:**
  - "Components" of "Current state" shows the root level: elements without a parent, or whose parent was deleted.
  - Each element with children expands to them, by name, in pages.
  - Structuring aids show a folder icon.
  - Drag and drop and "Move to…" set the parent.
  - "New structuring aid" creates one at the root level or under an element.
  - Components of a solution stay a flat list.
- **Concurrency:** the parent change and the deletion of a structuring aid need `If-Match` with the current entity version: 428 without it, 412 when it is stale, as for the restore.

**Contract (spec-first):**

- `setEntityParent` (`PUT /entities/{id}/parent`), `createStructuringAid` (`POST /structuring-aids`) and `deleteStructuringAid` (`DELETE /structuring-aids/{id}`), all with `entity:write`, the deletion also with `entity:delete`.
- `isComposition` on the metatype schemas; `parentEntityId` among the system fields of the history.
- `parentEntityId` on `listBrowserItems`, and `childCount` and `structuringAid` on `BrowserItem`.

**Not in this change:**

- Hierarchy inside solutions (deltas).
- Changing an auto-created connection by hand (REQ-142 step 5) waits for connection editing.

## Capabilities

### New Capabilities

- `repository/entity-hierarchy`: parent of an element entity, structuring aids and the automatic composition connection.

### Modified Capabilities

- `metamodel/connection-metatypes`: the composition mark of a connection metatype.
- `navigation/solution-browser`: the components of "Current state" as a hierarchy, with drag and drop, "Move to…" and structuring aids.

## Impact

- **Contract:** three new operations, plus new fields on existing schemas.
- **Migration:** `V42__entity_hierarchy.sql` adds:
  - the built-in metatype "Structuring aid";
  - `metatypes.is_composition` with its unique partial index;
  - the field name `parent_entity_id` in the check of `entity_system_field_history`;
  - indexes on `entities.parent_entity_id`.
- **Backend:** the hierarchy service in `app/entity` with SQL in `core/entity`, the composition check, the history readers of the parent field, the protection of the built-in metatype, and the package import of `isComposition`.
- **Starter package:** version 1.3.0 with the metatype "Composition".
- **Frontend:**
  - the hierarchy, drag and drop, "Move to…" and structuring aids in `SolutionBrowser.vue`;
  - the composition mark in `MetatypeDialog.vue` of the metamodel page (SCR-030).
- **Documentation:**
  - the manual (navigation, metamodel editing and packages, in English and German);
  - business objects `entity.md` and `metatype`, `data-model.puml`, the audit catalog;
  - UC-13, REQ-141 and REQ-142, US-135 and US-136.
