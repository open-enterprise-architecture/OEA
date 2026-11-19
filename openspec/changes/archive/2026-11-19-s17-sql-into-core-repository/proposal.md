# Proposal

## Why

ADR-051 keeps SQL in the stores of `core` and froze the existing violations. The owner decided on 2026-11-18 to remove this debt in S17, so this change empties the store:

- **Change 2** (`s17-sql-into-core-metamodel`, #331) took the store from 860 to 510 lines.
- **Change 3** (`s17-sql-into-core-identity`, #332) took it from 510 to 136.
- **This change** moves the last nine classes: `io.oea.app.solution` (126 lines), `entity` (6), `history` (2) and `catalog` (2).

It also resolves issue [#259](https://github.com/open-enterprise-architecture/OEA/issues/259) (ARCH-4): the solution feature reads entity values through one shared read API of `core.entity` instead of three copies of the same SQL.

With the store empty, the rule `applicationLayerAddsNoSql` stops being frozen and becomes a plain ArchUnit rule. The freeze store and its configuration are removed.

This is S17 change 4 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| ADR | [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Issue | [#259](https://github.com/open-enterprise-architecture/OEA/issues/259) (ARCH-4: shared entity read API) |
| Review | [Architecture review 2026-11](../../../docs/reviews/architecture-2026-11.md), finding ARCH-4 |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decision of 2026-11-18:** the frozen SQL debt of ADR-051 is removed in S17, including #259.

**Scope:**

- The SQL of the following classes moves into stores of `core.solution`, `core.entity`, `core.metamodel` and `core.catalog`:
  - `SolutionDeltas`, `SolutionDiff`, `Solutions`, `SolutionLandscape` and `SolutionConflicts`;
  - `EntityBatch`, `MetatypeRules`, `EntityRestore` and `CatalogQueries`.
- **#259:**
  - a read API in `core.entity` for the property values of several entities (and of deltas), shared by baseline, deltas and diff;
  - one value record for the solution feature: `SolutionDeltas.Value` holds a `SolutionDeltas.Typed` instead of copying its components (the issue's `SolutionDiff.Typed` is `SolutionDeltas.Typed`, used by the diff);
  - the duplicated value queries are removed.
- **Store and rule:**
  - the frozen store reaches 0 lines;
  - `applicationLayerAddsNoSql` becomes a plain rule;
  - the store directory, `stored.rules` and the freeze settings in `archunit.properties` are removed, or the file is removed if nothing else is left in it.

**Not in this change:**

- No behavior change: same responses, errors, SQL semantics and transaction boundaries.
- No contract, migration or frontend change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a refactoring without behavior change, so the change sets `skip_specs: true`.

## Impact

- **Backend:** the nine classes, new or extended stores, the shared value read API, and `DataAccessArchitectureTests`.
- **Tests:** existing tests stay unchanged, and store tests are added for new store methods.
- **Documentation:**
  - ADR-051: the debt is resolved and the rule is no longer frozen;
  - review ARCH-4 is resolved;
  - the sprint plan;
  - #259 is closed by the pull request.
