# Design

## Context

- **The store** (checked 2026-11-19): 136 lines, all with an origin in the nine classes, none of another origin:
  - `SolutionDeltas` 60, `SolutionDiff` 21, `Solutions` 19, `SolutionLandscape` 15, `SolutionConflicts` 11;
  - `EntityBatch` 5, `EntityRestore` 2, `CatalogQueries` 2, `MetatypeRules` 1.
- **Lines that name other packages:** some of these lines also name `OeaPrincipal` or `LanguagePreferences` in a signature. They are lines of the nine classes and disappear with them.
- **Lines without own SQL:**
  - `EntityRestore` (2) only holds a `JdbcTemplate` to call `MetatypeRules.load(JdbcTemplate, LocalDate)`;
  - `MetatypeRules` (1) is that overload;
  - `EntityBatch` (5) holds the same call plus one query of a metatype's name and abbreviation;
  - `CatalogQueries` (2) only checks `SQLException` state `57014` (query cancelled) in `cancelled(Throwable)`.
- **Existing stores:**
  - `core.entity`: `EntityStore`, `EntityVersionStore`, `EntityHistoryStore`, `EntityHierarchyStore`, `PropertyValueStore` and `MetatypeRuleStore`, with the record `TypedValue`;
  - `core.solution`: `DeltaValueStore` (delta value clean-up after metamodel changes), `GoLiveStore`, `OpenIssueStore`, `PlateauStore`, `PlateauContentStore` (with `Overlay`: property name, data type, `TypedValue`, reference name and abbreviation), `SnapshotStore` and `SolutionBrowseStore`;
  - `core.catalog`: `CatalogStore`, `CatalogQueryStore` and `ScopeStore`;
  - `core.metamodel`: `MetatypeStore` and others.
- **Package dependencies today:** `core.solution` imports `core.entity`, `core.entity` imports `core.metamodel`; `core.metamodel`, `core.access` and `core.catalog` import no other `core` package. `CycleRules` forbids any cycle between packages.
- **#259:** the issue names the records `SolutionDeltas.Value` and `SolutionDiff.Typed`. In the code, the two app records are `SolutionDeltas.Typed` (six components, the reference as `Reference(id, name, metatypeAbbreviation)`) and `SolutionDeltas.Value` (property ID, name, data type, `redacted`, and the six components of `Typed` flattened). `SolutionDiff` has no record of its own; it copies `Value` into `Typed` in two places. The value query with the reference target is written three times:
  - `SolutionLandscape.entry`: `entity_property_values` of one entity;
  - `SolutionDiff.catalog`: `entity_property_values` of several entities;
  - `SolutionDeltas.values` and `changes`: `entity_delta_property_values` of several deltas (after values, and before and after values).
- **Freeze configuration:**
  - `FreezingArchRule` is used once, in `DataAccessArchitectureTests.applicationLayerAddsNoSql`; the rule itself is `DataAccessRules.noSqlInApplicationLayer`;
  - `src/test/resources/archunit.properties` holds only the three freeze keys;
  - `src/test/resources/archunit_store/` holds `stored.rules` and one store file.

## Goals / Non-Goals

**Goals:**

- an empty store, then a plain rule;
- one shared value read API and one value record for the solution feature.

**Non-goals:**

- redesigning solutions;
- changing SQL for performance.

## Decisions

### D1 – Store cut

- **`core.solution`, new `SolutionStore`** (`solutions`): the insert of `Solutions.create` (`ON CONFLICT DO NOTHING`, returns whether a row was inserted), `list`, `find`, and the status read of `SolutionDeltas.status`.
- **`core.solution`, new `DeltaStore`** (`entity_deltas`):
  - the writes of `SolutionDeltas`: insert, the `FOR UPDATE` lock of `update`, the `updated_at` touch, the delete;
  - the reads: counts per type and the delta page of `read`;
  - the two conflict queries of `SolutionConflicts`. `SolutionConflicts` stays as a thin app service that maps the delta type to `SolutionDeltas.Type`, so `SolutionConflictsTests` keeps its API.
- **`core.solution`, `DeltaValueStore` extended** (`entity_delta_property_values`): the value insert, the delete of a delta's values, the recorded values of `update`, and the labeled value reads of D2.
- **`core.entity`:**
  - `PropertyValueStore` gets the labeled value read of D2;
  - `EntityStore` gets the two entity reads of the solution feature: the baseline check of `SolutionDeltas.inBaseline` (element, not deleted, not a structuring aid; this is not `isActiveElement`, which keeps structuring aids) and the name and description of the active elements among several IDs (`SolutionDiff.catalog`).
- **`MetatypeRules`:** the overload `load(JdbcTemplate, LocalDate)` is removed. `EntityBatch`, `EntityRestore` and `SolutionDeltas` inject `MetatypeRuleStore` and call `load(MetatypeRuleStore, LocalDate)`, as `EntityBlocks`, `OpenIssues` and `SolutionGoLive` already do.
- **`EntityBatch.metatypeName`:** the query of name and abbreviation moves into `core.metamodel.MetatypeStore` (existing). An unknown ID still fails as before.
- **`CatalogQueries.cancelled`:** the `SQLException` check moves into `core.catalog.CatalogQueryStore` as a static helper (or the store translates the cancellation into a `core` exception); `CatalogQueries` keeps the metrics, the audit event and the `CatalogQueryTimeoutException`.
- **Reuse:** existing stores are extended, not duplicated. `SolutionBrowseStore` stays as it is.
- **Dependencies:** only the existing direction `core.solution` → `core.entity` → `core.metamodel` is used. No new store in `core.entity`, `core.metamodel` or `core.catalog` imports `core.solution`, and `core.catalog` imports no other `core` package.

### D2 – Shared value read API (#259)

- **`core.entity`:** `PropertyValueStore` gets one read of the values of several entities with their property name, data type and reference target, e.g. `Map<Integer, List<LabeledValue>> labeledValues(Collection<Integer> entityIds)`, ordered by property name (case-insensitive), then ID. `LabeledValue` holds the property ID, name and data type, the `TypedValue`, and the reference name and abbreviation. `SolutionLandscape.entry` calls it with one ID, `SolutionDiff.catalog` with several.
- **`core.solution`:** `DeltaValueStore` gets the counterpart for delta values, with the same value shape: the after values of `new` deltas and the before and after values of `modified` deltas, with the system field and the entity ID of the delta.
- **Why `TypedValue` alone is not enough:** it lacks the reference target's name and abbreviation, the property name, data type and the redaction flag, which the responses need. It stays the raw value inside the store records.
- **One app record:**
  - `SolutionDeltas.Typed` (with `Reference`) is the one value record of the solution feature;
  - `SolutionDeltas.Value` keeps the property ID, name, data type and `redacted`, and holds a `Typed` instead of its six flattened components;
  - the copies from `Value` into `Typed` in `SolutionDiff` disappear, and `SolutionController` maps `Value` through the same `Typed` mapping.
  - The JSON responses stay identical.
- **The three queries:** replaced by these calls; the existing landscape, diff and delta tests prove identical results.
- **Redaction:** stays in `app` with the reader's `PropertyAccess.Snapshot`, as today:
  - `readableFor(property, entityId)` for the landscape, the diff and the changes of `modified` deltas;
  - `readableWithoutEntity(property)` for the values of `new` deltas.

  The stores return unredacted values and are called only from these app services.

### D3 – Signatures, transactions and wiring

As in changes 2 and 3:

- records only, no JDBC types outside `core`;
- `@Transactional` stays on the app services with its attributes; `EntityBatch` and `CatalogQueries` keep their `TransactionTemplate`, and `CatalogQueries` keeps `SET LOCAL statement_timeout` in its transaction;
- no store is transactional, and lock statements stay first:
  - `SolutionDeltas.update` locks the delta (`FOR UPDATE`) before any other read;
  - `computeChanges` keeps `EntityStore.lockForShare`;
  - `Solutions.create` keeps the plateau lock (`PlateauStore.lock`, `FOR SHARE`) before the insert;
- a duplicate delta still surfaces as `DuplicateKeyException` (Spring `dao`, not JDBC) from the insert and becomes the same 422;
- constructor injection.
- **No SQL fragments from `app`:** `SolutionDeltas.read` today passes `where` and `limit` as SQL text. The store method takes typed arguments instead (an optional delta ID, the keyset values of the cursor, the page size and offset), and the constants `RANK` and `SORT_NAME` move into the store.

### D4 – Ending the freeze

1. After the last class, `DataAccessArchitectureTests` runs, and the store shrinks to 0 lines.
2. In `DataAccessArchitectureTests.applicationLayerAddsNoSql`, `FreezingArchRule.freeze(DataAccessRules.noSqlInApplicationLayer("io.oea"))` becomes `DataAccessRules.noSqlInApplicationLayer("io.oea")`. The description and `because` text in `DataAccessRules` stay. The import of `FreezingArchRule` goes, and the class comment no longer mentions the frozen store or #259.
3. The directory `src/test/resources/archunit_store/` is deleted, with `stored.rules` and the store file. `src/test/resources/archunit.properties` holds only the freeze keys and is deleted.
4. The test runs again and passes without a store; a new JDBC dependency in `app` now fails directly.

### D5 – Documentation

- **ADR-051:**
  - the rule section no longer says that the rule is a `FreezingArchRule` with a store; it says that the rule has been plain since this change;
  - the consequences get a progress note that the frozen debt is gone (date, the three PRs), and the store consequence is marked as resolved.
- **The review finding ARCH-4:** resolved.
- **The S17 row of the sprint plan:** the status of this change.
- **The pull request:** it closes #259.

### D6 – Security

- **No behavior change:** authorization stays in `app`, before the store calls.
- **SQL:** moved SQL stays parameterized. Dynamic SQL in `CatalogQueryStore` and the delta page keeps its allowlists and is built only inside the store (D3).
- **Value visibility:** the redaction stays in `app` (D2); the existing visibility tests of landscape, diff and deltas prove it.

## Risks / Trade-offs

- **Value records:** merging the value reads can change subtle behavior, such as null handling, the empty text or the order. The store returns the value as today (`null` for no value), and the app keeps its sorting. The existing diff, landscape and delta tests guard it.
- **`Value` with a nested `Typed`:** the controller mapping changes, but the response must not. The existing controller and Cucumber tests guard it.

## Migration Plan

None.

## Open Questions

None.
