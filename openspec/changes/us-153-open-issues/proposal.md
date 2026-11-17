# Proposal

## Why

The go-live of change 1 (`us-152-solution-go-live`) takes metamodel violations live and records them as open issues, but nothing makes anyone fix them. The owner decided that open issues block their entities until they are resolved (2026-11-16).

[US-153](../../../requirements/user-stories/US-153-resolve-open-issues.md) (new) makes the open issues blocking and resolvable.

A metamodel change already removes the values of a removed property, or of a deleted enumeration value, from the entities in the catalog. Active solutions are not considered, so their deltas keep values that the go-live can no longer write. [US-154](../../../requirements/user-stories/US-154-metamodel-change-in-solutions.md) (new) asks, for every affected active solution, whether the change applies there too, or whether the solution keeps the property as a snapshot until its go-live.

This is S14 change 2.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) (project mode, after the go-live); [UC-04](../../../requirements/use-cases/UC-04-configure-metamodel.md) (metamodel changes) |
| User Story | [US-153: Resolve the Open Issues of a Go-Live](../../../requirements/user-stories/US-153-resolve-open-issues.md) (new, AC1–AC5); [US-154: Decide Whether a Metamodel Change Applies to Active Solutions](../../../requirements/user-stories/US-154-metamodel-change-in-solutions.md) (new, AC1–AC5); [US-152](../../../requirements/user-stories/US-152-solution-go-live.md) AC4 |
| Requirements | [REQ-032](../../../requirements/req/REQ-032-entitytype-gui-configuration.md) AC4, [REQ-037](../../../requirements/req/REQ-037-architecture-metamodel-extension.md), [REQ-040](../../../requirements/req/REQ-040-capture-entity-deltas.md), [REQ-162](../../../requirements/req/REQ-162-property-write-protection-all-paths.md) |
| ADRs | [ADR-022](../../../adrs/ADR-022-structured-property-model.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) (SQL only in `core`) |
| Sprint / Milestone | S14 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-17:**

- **Blocking:** an entity with an open issue cannot be written in the catalog except by resolving its issues, and no solution can capture a new delta `modified` or `retiring` of it, until all its issues are resolved.
- **Resolution:** in a dialog on the page of the solution, like SCR-063, with a valid value per issue. For an invalid value of an optional property, removing the value also resolves the issue. The resolution is stored with person and time and audited.
- **Automatic resolution:** an issue also counts as resolved once its entity is valid in another way, for example when a metamodel change makes the property optional.
- **Who:** holders of `solution:write`, and those who may write the entity in the catalog.
- **Catalog:** the catalog marks entities with open issues, and the entity names its open issues with a link to the solution.
- **Metamodel changes and active solutions:**
  - Removing a mapping, deleting an enumeration value and changing a base type remove values from the catalog, as they do today.
  - For every active solution the change affects, OEA asks whether the change applies there too.
  - "Yes" removes the values from the deltas of the solution, and the metatypes of its extension lose the property.
  - "No" keeps the property in the metamodel of the solution as a snapshot until the go-live.
  - At the go-live, every value of a snapshot opens the merge dialog. A value that is not resolved becomes an open issue that blocks its entity until it is resolved.
  - The plateaus of change 3 get the same question.

**Assumptions recorded here (minor details, open for review):**

- **What "blocked" covers:**
  - updates of the entity by `batchWriteEntities` (item error `OPEN_ISSUES`, following the `atomic` rules of the batch);
  - full and partial restores (409);
  - `createSolutionDelta` of type `modified` or `retiring` (409);
  - the go-live of another solution with a delta `modified` or `retiring` of the entity (409 naming the deltas).

  Editing or removing a delta captured before the go-live stays possible. The go-live is what applies it. Reads are never blocked.
- **Permission paths:**
  - With `solution:write`, values are taken without a check of write roles, as in the go-live.
  - With only `entity:write`, the write roles and scope of a catalog write apply (REQ-162), and a value the person may not write gives 403.
- **Automatic resolution is computed:** the violation of each unresolved issue is evaluated against the current metamodel and values on every read and every blocking check. An issue whose violation no longer holds counts as resolved and is listed with the resolution `automatic`. It is neither stored nor audited.
- **Where the catalog shows the issues:**
  - the change history of the entity (SCR-026) shows the notice with the link, because no other entity page exists yet;
  - the catalog browser (SCR-020) marks the row.

- **Metamodel question:**
  - A solution is affected when it is active (not `implemented` or `archived`) and either has a delta with a value the change removes, or has an extension metatype that inherits the changed metatype.
  - `deletePropertyMapping`, `deleteEnumValue` and `updateMetatype` take a decision per affected solution: `apply` or `keep`.
  - Without a decision for every affected solution, they answer 409 `solution-decision-required` with the list, and nothing changes. The client uses this answer to ask.
  - When no solution is affected, nothing changes in the flow.
- **Snapshot:**
  - A snapshot is stored per solution and names the metatype and the property, or the enumeration value.
  - The checks of the deltas of the solution, and the effective metamodel of its extension, accept a snapshot as if it were still in the metamodel.
  - A property definition with a snapshot cannot be deleted (409 `in-use`).
  - The snapshots of a solution are dropped with its go-live.
- **Snapshot values at the go-live:**
  - They are conflicts of the kind `not-in-metamodel` in the preview and in the merge dialog SCR-063, with the choices "Discard value" and "Resolve later".
  - Unlike field conflicts, they do not stop the go-live.
  - A value that is not discarded becomes an open issue `not-in-metamodel`, which keeps the value and blocks the entity.
  - It is resolved by discarding the value, or, once the property is mapped again (or the enumeration value exists again), by writing the kept or another value.

**Open issues (`solution`):**

- **`listSolutionOpenIssues`** returns every issue with its `status` (`open` or `resolved`) and, for a resolved issue:
  - `resolution` (`value`, `removed` or `automatic`);
  - `resolvedAt` and `resolvedBy`, which are empty for an automatic resolution.

  `openIssueCount` in `Solution` counts the open issues only.
- **`resolveSolutionOpenIssues`** (`POST /solutions/{id}/open-issues/resolution`, `solution:write` or `entity:write`) takes a list of `{issueId, value}` or `{issueId, remove: true}`. In one transaction, it writes the values through `EntityWriter` (new version, `entity.updated`) and marks the issues resolved. It audits `solution.open-issues-resolved` with the issue IDs.
  - A value the current definition rejects, or the removal of a mandatory value, gives 422 naming the issue.
  - An issue that is already resolved, or one of another solution, gives 422 naming the issue.
  - A value the caller may not write on the `entity:write` path gives 403.
- **Blocking:** a write to a blocked entity gives the problem type `entity-blocked` (409), with the entity, its open issues and their solution.

**Catalog and history:**

- `CatalogRow` gets `openIssues: boolean`, and the catalog browser shows a marker in the name column.
- `getEntityHistory` gets `openIssues` (issue, solution ID and name), and SCR-026 shows a notice with a link to the solution.

**Frontend (SCR-030, SCR-061, SCR-063, SCR-064):**

- On the metamodel page (SCR-030), the dialogs for removing a mapping, deleting an enumeration value and changing a base type show the affected solutions after the first 409, each with "Apply" and "Keep as snapshot". The dialogs send the decisions again with the request.
- SCR-063 lists snapshot values with "Discard value" and "Resolve later".

- The list "Open issues" of the page "Solution" shows the status of each issue and, for holders of `solution:write` or `entity:write`, "Resolve".
- This opens the dialog SCR-064 "Resolve open issues" (new), grouped by entity. Per issue it shows the field, the kind and the current value, with an input for a new value and, where allowed, "Remove value".

## Out of Scope

- Blocking other writes than the ones named above. `replaceEntity`, `patchEntity`, `deleteEntity` and `upsertEntityByExternalId` are still `planned`, and they take over the block when they are implemented.
- Plateaus and the plateau go-live (changes 3 and 4). Change 3 extends the metamodel question to plateaus with their own metamodel or changes.
- Metamodel changes other than the three named, such as a changed data type, which is already refused while values exist, or a deleted metatype, which is refused while entities exist.
- A resolution of open issues from the catalog itself.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `solution/solution-go-live`: the open issues get a status, a resolution and the automatic resolution; resolving open issues; blocking writes and deltas of entities with open issues; the dialog SCR-064.
- `solution/solution-deltas`: capturing a delta `modified` or `retiring` of a blocked entity is rejected.
- `repository/entity-batch-write`: the item error `OPEN_ISSUES` for an update of a blocked entity.
- `repository/entity-restore`: the 409 `entity-blocked` for full and partial restores of a blocked entity.
- `catalog/catalog-query`: the flag `openIssues` per row and the marker in the catalog browser.
- `repository/entity-history`: the open issues of the entity and the notice on SCR-026.
- `metamodel/property-mappings`: the question per affected solution when a mapping is removed.
- `metamodel/property-definitions`: the question when an enumeration value is deleted, and the 409 for a definition with a snapshot.
- `metamodel/metatype-editing`: the question when a base type change removes inherited properties.
- `metamodel/solution-extensions`: snapshots in the effective metamodel of an extension.
- `solution/solution-deltas`: the checks accept the snapshots of the solution.

## Impact

- **api/openapi.yaml:**
  - `resolveSolutionOpenIssues`;
  - `SolutionOpenIssue` with `status`, `resolution`, `resolvedAt` and `resolvedBy`;
  - `CatalogRow.openIssues`, and `openIssues` in the history page;
  - the problem types `entity-blocked` and `solution-decision-required`;
  - `solutionDecisions` for `deletePropertyMapping` and `deleteEnumValue` (query) and for `updateMetatype` (body);
  - the conflict kind `not-in-metamodel` in `DeltaConflict`, the resolution choice `discard`, and the issue kind `not-in-metamodel` with its kept value;
  - the item error `OPEN_ISSUES` in the batch description.
- **backend:**
  - migration `V36__open_issue_resolution.sql`:
    - the columns `resolved_at`, `resolved_by`, `resolution` and `kept_value`, with `UPDATE` of the first three for the runtime role;
    - the kind `not-in-metamodel`;
    - the table `solution_metamodel_snapshots`;
  - `core/solution/SnapshotStore` and `core/solution/DeltaValueStore` (ADR-051);
  - `app/metamodel/SolutionImpact`, which finds the affected solutions and applies the decisions, called by the mapping, enumeration and metatype services;
  - `core/solution/OpenIssueStore` (ADR-051);
  - `app/solution/OpenIssues`, with listing, evaluation, resolution and the blocking check;
  - the blocking check in `EntityBatch`, `EntityRestore`, `SolutionDeltas` and `SolutionGoLive`;
  - the catalog query and the history with the flags;
  - the audit event `solution.open-issues-resolved`;
  - the decisions in the details of `metamodel.mapping-deleted`, `metamodel.enum-value-deleted` and `metamodel.metatype-updated`.
- **frontend:**
  - the metamodel dialogs with the decision list, through a shared `SolutionDecisions.vue`;
  - `DeltaMergeDialog.vue` with snapshot values;
  - `SolutionPage.vue` with the status and the action;
  - the new `OpenIssuesDialog.vue` (SCR-064);
  - `CatalogPage` with the marker;
  - `EntityHistoryPage` with the notice;
  - `api/solutions.ts`, `api/catalogs.ts` and `api/history.ts`;
  - the stories of SCR-061, SCR-064, SCR-020 and SCR-026.
- **Documentation:**
  - US-153 and US-154 (new, written with this proposal);
  - `docs/audit-event-catalog.md`;
  - `docs/data-model.puml` and `business-objects/solution.md` (BR-08);
  - `docs/screens/SCREENS.md` (SCR-064; additions to SCR-030 and SCR-063);
  - the user manual for solutions, catalogs, history and the metamodel in English and German;
  - the S14 row of the sprint plan.
