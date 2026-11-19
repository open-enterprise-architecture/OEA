# Tasks

## 1. Stores

- [x] 1.1 Create or extend the stores of D1 in `io.oea.core.metamodel` and move the SQL of the 20 classes into them (D2–D4). Work class by class, running the class's tests after each one.

  Verify:
  - `DataAccessArchitectureTests` passes;
  - the store file, rewritten by that test run (D5) and committed, holds no line starting with `<kind> <io.oea.app.metamodel.` and 510 lines in total; `git diff --numstat` shows 0 added lines;
  - `LayerArchitectureTests` and `CycleArchitectureTests` pass; no class of `core.metamodel` imports `core.entity` or `core.solution`;
  - the five `@Autowired` setters of D4 are gone, and no store carries `@Transactional` (D3).
- [x] 1.2 Store tests for stores whose queries no service test covers (dynamic SQL, locking).

  Verify: each new store method is exercised by at least one test.

## 2. Regression

- [x] 2.1 The full backend `verify` (unit, integration, Cucumber).

  Verify: the counts are those of main or higher, with 0 failures, and no existing test was changed except where it reached into a moved method.

## 3. Documentation

- [x] 3.1 ADR-051 progress note, review finding ARCH-4, and the S17 status in the sprint plan (D6).

  Verify:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate s17-sql-into-core-metamodel --strict`.
