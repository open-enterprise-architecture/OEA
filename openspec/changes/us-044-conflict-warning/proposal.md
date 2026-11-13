# Proposal

## Why

Several change initiatives usually run at the same time. When two of them modify or retire the same entity, the conflict only surfaces at the go-live, where resolving it is expensive ([UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md)). [US-044](../../../requirements/user-stories/US-044-conflict-warning.md) warns the architect early, while they capture the change, so that they can coordinate with the owner of the other solution. It is S11 change 5, the last change of the sprint.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05: Describe the Architecture Vision of a Change Initiative](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) |
| User Stories | [US-044: Show Conflict Warning for Parallel EntityDeltas](../../../requirements/user-stories/US-044-conflict-warning.md) (AC1–AC5, realigned) |
| Requirements | [REQ-042: Conflict warning for parallel EntityDeltas](../../../requirements/req/REQ-042-conflict-warning-parallel-solutions.md) (AC1–AC6, realigned) |
| ADRs | [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-049: Organization extensions of packages](../../../adrs/ADR-049-organization-extensions-of-packages.md) (settings that a re-import keeps), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S11 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-13:**

- **What conflicts:** a delta `modified` or `retiring` conflicts with a delta `modified` or `retiring` on the same entity (`entityId`) in another active solution. A solution is active unless it is `implemented` or `archived`. A delta `new` has no entity until the go-live and never conflicts.
- **Where the warning shows:**
  - in the response to saving a delta;
  - on each entry of the delta list on SCR-061;
  - as a summary notice in the header of SCR-061;
  - on the cards of the comparison SCR-062.
- **Baseline marking:** the baseline on SCR-061 also marks entities that another active solution changes, before anything is captured. A new setting per metatype, "Show parallel changes in the baseline", controls this marking. Metamodel administrators (`metamodel:write`) maintain it in the metamodel configuration. Its default is off. It affects only the baseline marking; the other warnings always show.

**Conflicts of a delta (computed, not stored):**

- each `SolutionDelta` carries `conflicts`: one entry per other active solution that holds a delta for the same entity. An entry has the solution's ID, name and status, its owner's ID and name, and the type of its delta. Entries are ordered by solution name.
- `createSolutionDelta`, `updateSolutionDelta` and `listSolutionDeltas` return them. The warning never blocks a write.
- the conflicts are read on each request and not stored. They disappear as soon as the other solution becomes `implemented` or `archived`, or removes its delta.
- `SolutionDeltaList` carries `conflicted`: the number of deltas with at least one conflict, over all pages, for the summary notice.
- `SolutionDiffEntry` carries the same `conflicts`.

**Baseline marking:**

- each `SolutionLandscapeEntry` carries `parallelChanges`: the other active solutions that hold a delta for the entity, in the same form. The list is empty when the metatype of the entity does not have the setting on.
- the entry read `getSolutionLandscapeEntity` carries them too.

**Metatype setting (`metamodel`):**

- `MetatypeDetail.showParallelChanges` (read-only, default `false`).
- the new operation `PUT /metamodel/metatypes/{id}/parallel-changes` with `metamodel:write` and `If-Match`. It sets the value for a metatype of any scope (built-in, imported, organization or extension), like the re-import setting.
- it stays possible while the metamodel is locked (`import-only`); a package re-import keeps it. Each change raises the metatype version and is audited as `metamodel.metatype-parallel-changes-changed`; the metamodel changes list it under the kind `metatype`.
- a subtype has its own setting; there is no inheritance along base types.

**Frontend:**

- **SCR-061, delta list:** an entry with conflicts shows a warning icon and, per conflict, "Also changed in '{solution}' (owner: {owner})". The solution name links to that solution.
- **SCR-061, header:** a warning notice "{n} changes also concern other active solutions. Please coordinate."
- **SCR-061, after saving:** a delta that conflicts shows a warning toast with the conflicting solutions; the list keeps the warning afterwards.
- **SCR-061, baseline:** the marker "Changed in {n} other solutions" on an entry, with the solution names, only for metatypes with the setting on.
- **SCR-062:** the conflicts of an entry on its card.
- **SCR-030, metatype detail:** the switch "Show parallel changes in the baseline". It stays changeable while the metamodel is locked, like the re-import setting.
- colors are never the only signal: each warning carries an icon and text.

**Realigned sections:**

- **US-044 AC3 and REQ-042 AC5:** the warning is persistent because it is computed on each read of the delta list, not stored.
- **US-044 AC4 and REQ-042 AC6:** solution status changes and the go-live arrive in S12–S14. Until then, the tests set the status of the other solution directly in the database.
- **REQ-042, Error Cases:** the conflicts are part of the read of a delta and have no failure mode of their own. A write is committed before its response is read.
- **US-044 Technical Notes and REQ-042 Implementation Notes:** the conflicts are an array on the delta, the diff entry and the baseline entry. Besides the owner's name, each entry carries the status and the delta type of the other solution.

## Out of Scope

- **Resolving a conflict:** the warning informs; coordination happens outside the tool, and the review and go-live process follows in S12–S14.
- **Conflicts between `new` deltas** with the same name and metatype (owner decision 2026-11-13).
- **Conflicts on connections and diagrams.**
- **Notifications** to the owner of the other solution.
- **Inheriting the setting** along base types.
- **Package format:** packages do not carry the setting.

## Capabilities

### New Capabilities

- `solution/solution-conflicts`: conflicts of a delta with other active solutions in the API, on SCR-061 and SCR-062, and the baseline marking controlled per metatype.

### Modified Capabilities

- `metamodel/metatype-editing`: the setting "Show parallel changes in the baseline" per metatype, also for read-only metatypes.
- `metamodel/configuration-screen`: the switch on the metatype detail.
- `metamodel/lock-mode`: the setting stays possible in `import-only`.

## Impact

- **`api/openapi.yaml`:**
  - the schema `SolutionDeltaConflict`;
  - `conflicts` on `SolutionDelta` and `SolutionDiffEntry`; `conflicted` on `SolutionDeltaList`; `parallelChanges` on `SolutionLandscapeEntry`;
  - `showParallelChanges` on `MetatypeDetail`; the operation `updateMetatypeParallelChanges` and the schema `MetatypeParallelChanges`.
- **`backend/`:**
  - migration V28: the column `metatypes.show_parallel_changes` (default `false`) and an index `entity_deltas (entity_id)`;
  - a new app service `SolutionConflicts` that reads the conflicts of a set of entities in one query;
  - `SolutionDeltas`, `SolutionDiff` and `SolutionLandscape` attach the conflicts;
  - the setting in `MetatypeCatalog` and a new metamodel service, audited;
  - controllers, integration and Cucumber tests.
- **`frontend/`:**
  - the warnings in `SolutionDeltas.vue`, `SolutionPage.vue`, `SolutionBaseline.vue` and `SolutionDiffPage.vue`;
  - the switch in `MetatypeDetail.vue`;
  - API clients, fake APIs and i18n;
  - the stories of SCR-061, SCR-062 and SCR-030 with their images.
- **`manual/`:** the pages on solutions and the metamodel configuration, in English and German.
- **Documentation:**
  - US-044 and REQ-042;
  - `business-objects/solution.md` (BR-05) and `business-objects/metamodel-configuration.md`;
  - `docs/data-model.puml`;
  - `docs/audit-event-catalog.md`;
  - `docs/screens/SCREENS.md` (SCR-030, SCR-061, SCR-062);
  - the sprint plan.
