# Proposal

## Why

ADR-051 decided on 2026-11-16 that SQL runs only in the stores of `core`, and `app` orchestrates through them. The violations that existed then were frozen in `backend/src/test/resources/archunit_store`: 915 dependencies of `app` on `org.springframework.jdbc` and `java.sql`, 860 of which are left. The frozen list documents the debt but does not remove it.

The owner decided on 2026-11-18 to remove the debt in S17. The first of three changes covers the package with the most violations, `io.oea.app.metamodel`: 20 classes with 350 entries.

This is S17 change 2 ([sprint plan](../../../docs/sprint-plan.md)). Changes 3 (`s17-sql-into-core-identity`) and 4 (`s17-sql-into-core-repository`, including #259) follow. When all three are done, the store is empty and the rule becomes a plain ArchUnit rule.

## Traceability

| Artifact | Reference |
|---|---|
| ADR | [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) (SQL only in `core`, frozen rule) |
| Review | [Architecture review 2026-11](../../../docs/reviews/architecture-2026-11.md), finding ARCH-4 |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decision of 2026-11-18:** the frozen SQL debt of ADR-051 is removed in S17.

**Scope:**

- Every class of `io.oea.app.metamodel` that depends on `JdbcTemplate`, `NamedParameterJdbcTemplate`, `RowMapper`, `ResultSet` or other `java.sql` types gets its SQL moved into stores in `io.oea.core.metamodel`.
- Stores follow the existing ones (`ArchitectureLayerStore`, `ViewpointStore`, `CompositionStore`), which are extended where a query touches their tables.
- The `@Autowired` setters that kept the constructors stable for the frozen rule are replaced by constructor injection.
- Each store is one table or one cohesive read model, and returns records, never `ResultSet` or JDBC types.
- `app` keeps the orchestration: validation, transactions, the audit, and the composition of stores.
- The entries of `io.oea.app.metamodel` are removed from the frozen store. After this change the store holds no entry whose origin is a class of that package (860 → 510 lines), and it gains no entry.

**Not in this change:**

- No behavior change: same responses, same errors, same SQL semantics and the same transaction boundaries.
- No change of the contract, the migrations or the frontend.
- Other packages of `app` (changes 3 and 4).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a refactoring without behavior change, so the change sets `skip_specs: true`.

## Impact

- **Backend:** `io.oea.app.metamodel` (20 classes) and new or extended stores in `io.oea.core.metamodel`.
- **Tests:** the existing tests stay as they are. Store tests are added where a store has no test coverage through the existing service tests. The frozen store shrinks.
- **Documentation:**
  - ADR-051 gets a progress note with the number of entries left;
  - the review finding ARCH-4;
  - the sprint plan.
