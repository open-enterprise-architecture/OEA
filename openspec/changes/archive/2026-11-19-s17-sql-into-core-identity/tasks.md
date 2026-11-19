# Tasks

## 1. Stores

- [x] 1.1 Create or extend the stores of D1 in `io.oea.core.access` and move the SQL of the 29 classes into them (D2, D3). Work class by class and run the class's tests after each one.

  Verify:
  - `DataAccessArchitectureTests`, `LayerArchitectureTests` and `CycleArchitectureTests` pass;
  - the frozen store goes from 510 to 136 lines with 0 added, and no line starts with an origin in the four packages;
  - no setter workaround is added, no store carries `@Transactional`, and the `REQUIRES_NEW` and `noRollbackFor` attributes are unchanged (D2);
  - `AdminRecoverCommand` no longer names `JdbcTemplate`, and its tests pass unchanged;
  - `LanguagePreferences` and `OeaPrincipal` keep their names and packages.
- [x] 1.2 Store tests for store methods that no service test covers (locks, the expiry query with `SKIP LOCKED`, throttle windows, the advisory lock).

  Verify: each new store method is exercised by at least one test.

## 2. Regression

- [x] 2.1 The full backend `verify`: unit, integration and Cucumber.

  Verify:
  - the counts are those of main or higher, with 0 failures;
  - no existing test changed, except where it reached into a moved method.

## 3. Documentation

- [x] 3.1 The ADR-051 progress note, the review finding ARCH-4 and the S17 status in the sprint plan (D5).

  Verify:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate s17-sql-into-core-identity --strict`.
