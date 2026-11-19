# Tasks

## 1. Stores and shared value API

- [x] 1.1 The shared value read API and the single value record (D2, #259).

  Verify:
  - the baseline, diff and delta tests are unchanged and pass;
  - no class of `app` names `entity_property_values` or `entity_delta_property_values`;
  - `SolutionDiff` no longer copies `Value` into `Typed`.
- [x] 1.2 Move the SQL of the nine classes into the stores of D1 (D3), class by class, running the class's tests after each one.

  Verify:
  - the frozen store shrinks to 0 lines with 0 added;
  - `LayerArchitectureTests` and `CycleArchitectureTests` pass;
  - `MetatypeRules.load(JdbcTemplate, LocalDate)` is gone, and `SolutionDeltas` passes no SQL text to a store.
- [x] 1.3 Store tests for the new store methods that no service test covers.

  Verify: each new store method is exercised by at least one test.

## 2. End of the freeze

- [x] 2.1 Make `applicationLayerAddsNoSql` a plain rule, and delete `archunit_store/` and `archunit.properties` (D4).

  Verify:
  - `DataAccessArchitectureTests` passes without a store;
  - a temporary `JdbcTemplate` field in an `app` class makes it fail (check locally, do not commit it).

## 3. Regression

- [x] 3.1 The full backend `verify`: unit, integration and Cucumber.

  Verify:
  - the counts are those of main or higher, with 0 failures;
  - no existing test changed, except where it reached into a moved method.

## 4. Documentation

- [x] 4.1 ADR-051, the review finding ARCH-4 and the S17 status in the sprint plan (D5).

  Verify:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate s17-sql-into-core-repository --strict`.
