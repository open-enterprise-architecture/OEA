# Design

## Context

- **Open issues (change 1, V35):**
  - `solution_open_issues` holds `solution_id`, `entity_id`, `field` (`name`, `description` or a property definition ID), `property_def_id`, `kind` (`mandatory-missing` or `invalid-value`) and `created_at`.
  - The runtime role has `SELECT` and `INSERT` on it.
  - `GoLiveStore` inserts the rows and lists and counts them. `listSolutionOpenIssues` and `openIssueCount` show them read-only.
- **Entity writes in use:**
  - `batchWriteEntities` (`app/entity/EntityBatch`);
  - full and partial restores (`app/history/EntityRestore`);
  - the go-live (`app/solution/SolutionGoLive`).

  `replaceEntity`, `patchEntity`, `deleteEntity` and `upsertEntityByExternalId` are `planned`. All writes go through `EntityWriter`.
- **Validation:** `MetatypeRules` (loaded through `MetatypeRuleStore`) gives the mappings with their necessity. `EntityValidator.typed` checks a value against its definition. `SolutionGoLive` already derives both kinds of violation from them.
- **Metamodel effects:**
  - `app/metamodel/MetamodelEffects` takes a snapshot of the effective mappings before a change. After the change, it removes the values of lost (metatype, property) pairs from the catalog entities, each with a new version. Removed mappings and base type changes go through it.
  - `PropertyDefinitions` deletes an enumeration value and removes it from the entities.
  - Neither looks at solutions.
- **Mappings** belong to metatypes and have no configuration. An extension of a solution has its own metatypes, which may have an instance metatype as their base type.
- **Package dependencies:**
  - `app/solution` and `app/history` use `app/entity`;
  - `app/entity` uses only `app/metamodel` and `app/security`;
  - `app/catalog` uses neither `app/entity` nor `app/solution`.

## Goals / Non-Goals

**Goals:**

- Open issues with a status, explicit and automatic resolution.
- Blocking of entity writes and deltas, by the owner decisions of 2026-11-17.
- The dialog SCR-064, and the markers on SCR-020 and SCR-026.
- The question per affected solution for metamodel changes, the snapshots, and the snapshot values at the go-live (US-154).

**Non-Goals:**

- Blocking the planned entity operations. They call the same guard when they are implemented.
- Plateaus (changes 3 and 4).

## Architecture Constraints

- **SQL only in `core` (ADR-051):** new SQL goes into `core` stores, and no new class in `app` uses JDBC.
- **No package cycles (`CycleRules`):**
  - `app/entity` cannot use `app/solution`. The blocking check, which `EntityBatch` and `EntityRestore` need, therefore lives in `app/entity`.
  - `app/metamodel` cannot use `app/solution` either. The metamodel question therefore reads the deltas through `core` stores.
- **Contract first (ADR-013):** Redocly lint and both authorization scripts pass.

## Decisions

### D1 – Schema (`V36__open_issue_resolution.sql`)

- `solution_open_issues` gets three columns:
  - `resolution TEXT NULL CHECK (resolution IN ('value', 'removed'))`;
  - `kept_value TEXT NULL`, the value of an issue `not-in-metamodel` in its text form;
  - `resolved_at TIMESTAMPTZ NULL`;
  - `resolved_by BIGINT NULL REFERENCES persons(id)`.
- A check ensures that `resolution`, `resolved_at` and `resolved_by` are either all null or all set.
- The check of `kind` gains `not-in-metamodel`, and `kept_value` is set exactly for that kind. `property_def_id` stays set for it; the column has no foreign key, so a deleted definition keeps the row readable.
- The table `solution_metamodel_snapshots` holds:
  - `id`;
  - `solution_id → solutions`;
  - `metatype_id → metatypes`;
  - `property_def_id → property_definitions` (`ON DELETE RESTRICT`);
  - `enum_value TEXT NULL`: null keeps the whole property, and a value keeps one enumeration value;
  - `created_at` and `created_by`;
  - unique (`solution_id`, `metatype_id`, `property_def_id`, `coalesce(enum_value, '')`).
- The runtime role gets `SELECT`, `INSERT` and `DELETE` on it, and `DELETE` on `entity_delta_property_values` if it does not have it yet.
- `GRANT UPDATE (resolution, resolved_at, resolved_by)` to `oea_app`.
- A partial index on `entity_id` covers the unresolved rows, for the blocking check.
- `automatic` is never stored (D3).

### D2 – Store (`core/solution/OpenIssueStore`)

- `unresolved(entityIds)`: the unresolved rows of the given entities, with their solution ID and name.
- `bySolution(solutionId)`: all rows of a solution, with the name of the resolving person.
- `lock(solutionId, issueIds)`: `SELECT … FOR UPDATE`.
- `resolve(issueId, resolution, personId, now)`.

`GoLiveStore` keeps the insert, and its `openIssues` and `openIssueCount` move to `OpenIssueStore`.

### D3 – Evaluation and guard (`app/entity/EntityBlocks`)

**`EntityBlocks.open(entityIds)`:**

- reads the unresolved rows;
- loads the current values of the entities through `EntityStore`;
- loads `MetatypeRules` per metatype for today;
- keeps a row only while its violation still holds:
  - `mandatory-missing`: the property is still mapped as mandatory, and the entity has no value;
  - `not-in-metamodel`: always holds. It is never resolved automatically (US-154 AC4);
  - `invalid-value`: the entity has a value that `EntityValidator.typed` rejects.
- A violation that no longer holds makes the issue count as resolved `automatic`. Nothing is written, so a metamodel change takes effect on the next read without a hook.

**`EntityBlocks.check(entityIds)`** throws `EntityBlockedException` with the entity IDs, the open issues and their solutions. It is called:

- by `EntityBatch` for every update item, as the item error `OPEN_ISSUES` through the existing per-item error path with its `atomic` rules;
- by `EntityRestore` before the full and the partial restore. The preview is not checked;
- by `SolutionDeltas.create` for `modified` and `retiring`;
- by `SolutionGoLive.goLive` for the entities of its deltas `modified` and `retiring`, before the conflicts are checked. The resulting 409 names the deltas.

The cost is one query for the rows and, only for entities that have some, one value read each. The common case, an entity without rows, costs one indexed query.

### D4 – Resolution (`app/solution/OpenIssues`)

**`list(principal, solutionId)`** returns every row with its status:

- `open`;
- `resolved` with a stored resolution;
- `resolved` with `automatic`, through `EntityBlocks`.

**`resolve(principal, solutionId, List<Resolution>)`** runs in one transaction:

- It locks the rows and checks them. The checks give 422 with `invalidFields` holding the issue IDs:
  - an unknown or resolved issue, or one of another solution;
  - a value that `EntityValidator.typed` rejects;
  - a removal of a mandatory or `mandatory-missing` property, or a removal of `name`.
- **Permission:**
  - `solution:write` writes with a `Checked` built like the go-live, without a write-role check;
  - otherwise `entity:write` needs `PropertyWriteAccess` as in a batch, and gives 403 for a value the person may not write;
  - neither permission gives 403.
- It writes per entity through `EntityWriter` (one version, `entity.updated`), marks the rows resolved, and audits `solution.open-issues-resolved` with `solutionId`, `issueIds` and `entityIds`.
- An issue whose violation already holds no more is still accepted, and the write is idempotent. It keeps the user from a confusing 422 when the metamodel changed while the dialog was open.

**`openIssueCount`** counts only open issues, through `EntityBlocks`.

### D5 – Contract

Snapshot additions:

- **`DeltaConflict.kind`:** `field` (the existing conflicts) or `not-in-metamodel`.
- **`DeltaConflictResolution.choice`:** gains `discard`, which is valid only for `not-in-metamodel`. The existing choices are valid only for `field`, and a mismatch gives 422.
- **The go-live:** `merge-required` counts only conflicts of the kind `field`.
- **Open issues:** `SolutionOpenIssue.kind` gains `not-in-metamodel`, with `keptValue`. A resolution item may be `{ issueId, value }` or `{ issueId, remove: true }`, and for this kind `remove` means "discard".
- **Decisions:**
  - `deletePropertyMapping` and `deleteEnumValue` take a repeated query parameter `decision=<solutionId>:apply|keep`, because a body on `DELETE` is avoided;
  - `updateMetatype` takes `solutionDecisions: [{ solutionId, decision }]` in its body;
  - the 409 `solution-decision-required` carries `solutions: [{ id, name, status, deltaValues, extensionMetatypes, properties: [{ metatypeId, propertyDefId, label, enumValue? }] }]`;
  - a decision for a solution that is not affected gives 422 naming it.
- **`MetatypeSummary`** in the effective metamodel of an extension gives its properties `snapshot: true` where a snapshot provides them.

Open issues and blocking:

- **`SolutionOpenIssue`** gets:
  - `status` (`open` or `resolved`);
  - `resolution` (`value`, `removed`, `automatic` or null);
  - `resolvedAt` and `resolvedBy` (`PersonRef` or null);
  - `currentValue` (text, redacted like delta values).
- **`resolveSolutionOpenIssues`:**
  - `POST /solutions/{id}/open-issues/resolution`;
  - request body `{ resolutions: [{ issueId, value?, remove? }] }`, with 1 to 200 items;
  - answer 200 with the updated list;
  - `x-oea-authorization` with `access: permission`, `permissions: [solution:write, entity:write]` and the note "either".
- **`entity-blocked`** (409):
  - `entities: [{ entityId, entityName, issues: [{ id, field, label, kind, solutionId, solutionName }] }]`;
  - `deltaIds` when the go-live is refused.
- **`CatalogRow.openIssues: boolean`**, required.
- **`EntityHistoryPage.openIssues`**: array of `{ id, field, label, kind, solutionId, solutionName }`.
- The batch description names `OPEN_ISSUES`.

### D6 – Catalog and history

- **`CatalogQueries`** sets `openIssues` for the entity IDs of a page. To keep `app/catalog` free of `app/entity`, the controller asks `EntityBlocks.open` for the page's entity IDs and sets the flag in the mapping in `api/catalog`. The marker is an icon with the tooltip "Open issues" next to the action "History" of the row, since the name column is configurable and may be hidden; the history names the issues.
- **`EntityHistory`** adds the open issues of the entity from `EntityBlocks`. SCR-026 shows a `UAlert` (warning) with the issues and a link to each solution.

### D7 – Frontend (SCR-061, SCR-064)

- The list "Open issues" on SCR-061 gets a status badge and, for `solution:write` or `entity:write`, a button "Resolve" while issues are open.
- `OpenIssuesDialog.vue` (SCR-064) is modelled on `DeltaMergeDialog`:
  - it groups the issues by entity;
  - per issue it shows the label, the kind and the current value, and an input typed by the data type of the property: number, date or text. An enumeration takes its value as text, because the issue does not carry the allowed values; the server checks it (422);
  - it offers "Remove value" for `invalid-value` of an optional property;
  - a 422 shows the error at the named issue.
- `api/solutions.ts` gets `resolveOpenIssues`. `api/catalogs.ts` and `api/history.ts` get the new fields.

### D8 – Audit

- **`solution.open-issues-resolved`:** category `change`, with the details `solutionId`, `issueIds` and `entityIds`.
- The values are audited by `entity.updated`. An automatic resolution writes nothing.

### D9 – Metamodel question (`app/metamodel/SolutionImpact`)

**`affected(lost)`:**

- It takes the lost (metatype, property) pairs, or (property, enumeration value) for an enumeration value.
- It reads, through `core/solution/DeltaValueStore`, the active solutions with delta values of the pairs, including deltas of subtypes of the metatype.
- It reads, through `MetatypeRuleStore`, the solutions whose extension metatypes inherit a lost metatype.

**`apply(decisions, lost, principal)`** runs inside the transaction of the metamodel change:

- **For `apply`:** it deletes the delta values of the pairs. It removes the deltas `modified` that are left without a change, and audits each removal with `solution.delta-removed`.
- **For `keep`:** it inserts the snapshot rows.
- **The audit:** it adds `solutionDecisions` (`[{ solutionId, decision }]`) to the details of the metamodel event.

**Where it is called:**

- `MetamodelEffects.apply` calls it, after the lost pairs are known and before the values in the catalog are removed. A missing or extra decision throws `SolutionDecisionRequiredException`, which rolls the metamodel change back. One code path thus covers removed mappings and base type changes.
- `PropertyDefinitions.deleteEnumValue` calls it with the enumeration value.

`PropertyDefinitions.delete` checks the snapshots for its `in-use` answer.

Only a change of the instance metamodel asks: a mapping removed from, or a base type changed of, a metatype of a solution extension is the extension's own change and passes no decisions. A package import passes none either, and leaves the solutions as they are.

### D10 – Snapshots in checks and effective metamodel

- **`MetatypeRules`:**
  - `MetatypeRules.load(store, date)` gains an overload with a `solutionId`. It adds the snapshot properties as optional mappings and the snapshot enumeration values to the definition.
  - `SolutionDeltas` loads the rules with the solution, so its checks accept the snapshots.
  - Every other caller keeps the organization rules, so a delta of another solution, or a catalog write, handles them as any unmapped property (ignored) or deleted enumeration value (rejected).
- **`MetatypeCatalog`:** it adds the snapshots, marked `snapshot`, for an extension configuration.

### D11 – Snapshot values at the go-live (`SolutionGoLive`)

- **`plan()`:** for every delta value whose pair exists only in a snapshot of the solution, it adds a `DeltaConflict` of the kind `not-in-metamodel`.
- **The resolution choice `discard`:** it deletes that value from the delta, and a delta `modified` left empty is removed.
- **The go-live:**
  - It writes the entity without the remaining snapshot values, and inserts an open issue `not-in-metamodel` with `kept_value` for each.
  - It deletes the snapshots of the solution.
  - The counts in `solution.went-live` gain `notInMetamodel`.
- **`OpenIssues.resolve`** for this kind:
  - `remove` gives the resolution `removed`;
  - a value is accepted only when the organization rules of today have the property, or the enumeration value, and validate it. Otherwise it gives 422.

### D12 – Frontend of the question (SCR-030)

- **`SolutionDecisions.vue`:**
  - It lists the affected solutions with their name, status and what is affected.
  - It offers a radio pair per solution: "Apply to this solution" and "Keep as snapshot".
  - "Confirm" is enabled once every solution has an answer.
- **The dialogs that use it:** the delete dialog of mappings (`DeleteDialog`), the deletion of an enumeration value in `PropertyDialog`, and the base type change in `MetatypeDialog`.
- **The flow:** each dialog sends the request first. On 409 `solution-decision-required`, it shows the list, then sends the request again with the decisions.
- **SCR-063:** it shows conflicts `not-in-metamodel` with the kept value and the choices "Discard value" and "Resolve later". The dialog needs no choice for them to enable "Apply".

## Risks / Trade-offs

- **Computed automatic resolution:** the blocking check depends on the current metamodel and values. A metamodel change can therefore block an entity again, when an issue that counted as automatically resolved holds again. This is accepted: only stored rows are evaluated, and their number is small. The alternative, storing it, would need hooks in every metamodel write.
- **Two permission paths:** the `solution:write` path skips write roles, as the go-live does (owner decision 2026-11-16). The `entity:write` path follows REQ-162. The tests cover both.
- **Snapshots outside the organization rules:** a snapshot is a second source of mappings, and the rules are loaded with or without a solution. Only `SolutionDeltas` passes the solution; the go-live reads the snapshots itself, and a test asserts that a delta of another solution does not take a snapshot value.
- **Unanswered 409 as the question:** the client sends the change twice. Between the two requests, another solution may become affected, and the second request then answers 409 again with the new list. This is accepted, because the list is computed in the transaction of the change.
- **Plateaus:** change 3 extends `SolutionImpact` to plateaus with their own metamodel or changes. The decision values stay the same.
- **Planned entity operations:** they are not blocked until they are implemented. A comment in `EntityWriter` points to `EntityBlocks`.

## Migration Plan

V36 adds nullable columns and the snapshot table. Existing rows stay unresolved and are evaluated as in D3. A rollback drops the columns and the table.

## Testing

- **`OpenIssuesTests`:**
  - list with all statuses;
  - resolve with a value, by removal and automatically;
  - the 422s: rejected value, removal of a mandatory value, resolved issue, issue of another solution;
  - the boundary of `max_value` (1000 accepted, 1001 rejected);
  - both permission paths and the 403s;
  - the audit.
- **`EntityBlocksTests`:** each kind of violation holds or no longer holds, with an entity without rows.
- **Blocking:**
  - `EntityBatchTests` with `atomic` true and false;
  - `EntityRestoreTests` with the full and the partial restore;
  - `SolutionDeltasTests` for `modified` and `retiring`;
  - `SolutionGoLiveTests` for the 409, and the go-live running after the resolution.
- **Resolution list size:** 200 items accepted and 201 rejected; an empty list rejected.
- **`SolutionImpactTests`:**
  - affected through a delta value, a subtype delta, or an extension metatype;
  - not affected: `implemented` and `archived` solutions, deltas without the value;
  - missing and extra decisions;
  - `apply` removes values and empty modifications;
  - `keep` stores the snapshot;
  - the audit details;
  - the enumeration value and base type paths;
  - the 409 `in-use` for a definition with a snapshot.
- **Snapshot checks:**
  - in `SolutionDeltasTests`, the own solution accepts a snapshot property and another solution rejects it;
  - in `EntityBatchTests`, the catalog rejects it;
  - `MetatypeCatalog` marks the snapshot.
- **`SolutionGoLiveTests` for snapshots:**
  - a `not-in-metamodel` conflict does not stop the go-live and becomes an open issue with the kept value;
  - `discard` removes the value;
  - the snapshots are dropped.
- **`OpenIssuesTests` for `not-in-metamodel`:**
  - `remove` resolves it;
  - a value gives 422 while the property is not mapped, and resolves after it is mapped again;
  - it is never resolved automatically.
- **Cucumber:** `features/solution/solution-open-issues.feature` and `features/metamodel/metamodel-solution-impact.feature`, with the scenarios of the delta specs.
- **Frontend:**
  - `OpenIssuesDialog.spec.ts`;
  - `SolutionPage.spec.ts`;
  - `CatalogPage.spec.ts`, for the marker;
  - `EntityHistoryPage.spec.ts`, for the notice;
  - the stories of SCR-064 (new), SCR-061, SCR-020 and SCR-026;
  - `SolutionDecisions.spec.ts`;
  - the dialog specs of the metamodel, with the 409 and the second request;
  - `DeltaMergeDialog.spec.ts` with `not-in-metamodel`;
  - the stories of SCR-030 with the question, and of SCR-063 with a snapshot value.
