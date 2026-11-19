# Design

## Context

- **The rule:** `DataAccessArchitectureTests.applicationLayerAddsNoSql` is a `FreezingArchRule`, and its store `3a8a0f51-…` lists one line per dependency.
- **Classes in `io.oea.app.metamodel` with entries**, by the number of lines:
  - `MetatypeCatalog` 65, `PackageImporter` 35, `PropertyDefinitions` 29, `PropertyVisibility` 26;
  - `MetamodelConfigurations` 24, `MetamodelChanges` 24, `PropertyMappings` 20, `MetatypeEditing` 18;
  - `MetamodelLabels` 17, `MappingEffectiveDates` 17, `HistoryVisibility` 14, `MetamodelEffects` 13;
  - `AllowedConnections` 12, `ConnectionConstraints` 8, `PersonMetatypes` 7;
  - `VisibilityOverride` 5, `ReimportPolicies` 5, `ParallelChangeSettings` 4, `MetamodelVersions` 4, `StarterPackages` 3.
- **Other entries naming the package:** 41 further lines belong to classes of other packages (`app.solution`, `app.history`) and only name `PropertyAccess`, `PropertyAccess$Snapshot` or `MetamodelLabels` in their signatures. They stay, and they match only as long as these types keep their names and the signatures quoted there.
- **Existing stores in `core/metamodel`:** `ArchitectureLayerStore`, `ViewpointStore`, `CompositionStore`, and the constants class `BuiltInMetatypes`. `core.entity.MetatypeRuleStore` reads the same tables for the write rules of entities; it stays where it is.
- **Dependencies inside `core`:** `core.entity` imports `core.metamodel` (`EntityStore` uses `BuiltInMetatypes`), and `core.solution` imports `core.entity`. A Java import from `core.metamodel` to `core.entity` or `core.solution` would therefore close a slice cycle.
- **Other rules:** `LayerArchitectureTests` (only `app` and the root package may use `core`) and `CycleArchitectureTests` (every package is a slice, no cycles) must keep passing.
- **Setter injection:** `MetatypeCatalog.setLayers`, `MetatypeEditing.setComposition` and `setLayers`, `PackageImporter.setComposition` and `setLayerStore` inject stores through `@Autowired` setters, only so that the constructors stay as the frozen lines quote them.
- **Transactions:** the services use only declarative `@Transactional` (partly `readOnly = true`), with no `TransactionTemplate` and no propagation settings. Writes serialize on the metamodel lock `MetamodelEffects.lock()` (`SELECT … FROM metamodel_configurations WHERE id = ? FOR UPDATE`); `HistoryVisibility` and `VisibilityOverride` also lock the rows of `authorization_roles` (`FOR UPDATE`).

## Goals / Non-Goals

**Goals:** zero entries of `io.oea.app.metamodel` in the frozen store, with unchanged behavior.

**Non-goals:**

- other packages;
- redesigning the metamodel services;
- changing SQL for performance.

## Decisions

### D1 – Store cut

One store per table, or per cohesive group of tables that are always read together, all in `io.oea.core.metamodel`:

- `MetatypeStore`: `metatypes`, including the re-import policy, the parallel-change setting and the usage counts that `MetatypeEditing` reads before a delete (`entities`, `catalogs`, `visibility_settings`, as SQL, without importing `core.entity`);
- `ConnectionConstraintStore`: `metatype_connection_constraints` (`ConnectionConstraints`, `AllowedConnections`, the constraint part of `MetatypeEditing`);
- `PropertyDefinitionStore`: `property_definitions`, `property_categories`, `property_enum_values`;
- `PropertyMappingStore`: `metatype_property_mappings` (`PropertyMappings`, `MappingEffectiveDates`, the mapping reads of `MetamodelEffects`);
- `MetamodelConfigurationStore`: `metamodel_configurations`, including the metamodel lock (`lock…`), the solution existence check of `MetamodelConfigurations` and the version reads of `MetamodelVersions` (the table name comes from its `Element` enum, never from input);
- `MetamodelLabelStore`: `i18n_entries`, including the orphan cleanup of `MetamodelEffects` and the element names of `MetamodelLabels`;
- `MetamodelChangeStore`: the change read model over `audit.events` with its joins (`MetamodelChanges`);
- `VisibilityStore`: `property_visibility_roles`, `history_visibility_settings`, `visibility_settings.person_metatype_id` and the visibility columns of `authorization_roles` (`PropertyVisibility`, `HistoryVisibility`, `VisibilityOverride`, `PersonMetatypes`);
- `PackageImportStore`: `metamodel_package_imports` (`PackageImporter`, `StarterPackages`); the content upserts of `PackageImporter` go to the stores of their tables.
- `MetatypeCatalogStore`: the joined reads of `MetatypeCatalog` (65 lines) as one read model, so that its joins are not split across stores.

Rules for the cut:

- `ArchitectureLayerStore`, `ViewpointStore` and `CompositionStore` are extended where a query touches their tables, not duplicated.
- No store of `core.metamodel` imports `core.entity` or `core.solution` (cycle, see Context); a query may still join their tables in SQL.
- The engineer may merge or split stores where the code suggests it, and records it in the tasks.

### D2 – Signatures

- Stores take and return Java records or primitives, never JDBC types.
- Row mapping happens inside the store.
- Locking queries (`FOR UPDATE`, `FOR SHARE`, advisory locks) move with their statements and keep their names (`lock…`).

### D3 – Transactions

- Transactions stay in `app`: the `@Transactional` annotations of the service methods stay where they are, with their `readOnly` flags, and stores join them.
- No store carries `@Transactional` or opens its own transaction.
- The metamodel lock and the `authorization_roles` locks stay the first statement of the same service methods as today; only the statement moves into the store.

### D4 – Wiring and cycles

- Stores are `@Repository` beans with constructor injection of `JdbcTemplate`. Services receive the stores through their constructors.
- The five setters of Context (`MetatypeCatalog.setLayers`, `MetatypeEditing.setComposition` and `setLayers`, `PackageImporter.setComposition` and `setLayerStore`) are removed; their stores become constructor parameters.
- No test constructs these services with `new`, so the constructor changes reach only Spring wiring.
- `PropertyAccess`, `PropertyAccess$Snapshot` and `MetamodelLabels` keep their names, and the members that the frozen lines of other packages quote keep their signatures (see Context); otherwise those lines no longer match and fail as new violations.

### D5 – The frozen store

- **Mechanism:** `src/test/resources/archunit.properties` sets `freeze.refreeze=false` and `freeze.store.default.allowStoreCreation=false`; `freeze.store.default.allowStoreUpdate` keeps its default `true`. A run of `DataAccessArchitectureTests.applicationLayerAddsNoSql` therefore removes the lines of fixed violations and rewrites `src/test/resources/archunit_store/3a8a0f51-…` in the source tree (the path is relative to `backend/`, the working directory of Surefire); a new violation still fails.
- **What the engineer does:** run the test from `backend/` after the last class (e.g. `./mvnw test -Dtest=DataAccessArchitectureTests`), check the diff of the store file and commit it. Neither `refreeze` nor the properties file are changed, and the file is not edited by hand. `stored.rules` stays unchanged.
- **Expected result:** no line starting with `<kind> <io.oea.app.metamodel.` (today 350); the 41 lines of other packages that only name metamodel types stay. The store shrinks from 860 to 510 lines, and only lines are removed (`git diff --numstat` shows 0 added lines).

### D6 – Documentation

- **ADR-051:** a progress note under "Consequences" with the date and the number of entries left.
- **The review finding ARCH-4:** its status is updated.
- **The S17 row of the sprint plan:** the status of this change.

### D7 – Security

- **No behavior change:** the authorization checks stay in `app` and run before the store calls, as before.
- **Parameterized SQL:** moved SQL stays parameterized, with no string concatenation of input. Where existing code builds dynamic SQL (filters, sort, the `VISIBLE` predicates), it moves unchanged with its allowlists.
- **Locking:** lock statements move with their transactions, so concurrency guarantees are unchanged; the existing concurrency tests prove it.

## Risks / Trade-offs

- **Large mechanical diff:** the safety net is the existing suite (about 1900 unit and integration tests and about 850 Cucumber scenarios), which must pass unchanged. A test may change only where it reached into a moved private method.
- **Transaction boundaries:** they move easily by accident. Rule D3 and the concurrency tests guard them.

## Migration Plan

None. This is code only.

## Open Questions

None.
