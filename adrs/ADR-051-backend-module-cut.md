# ADR-051: Backend Module Cut — Feature Packages, Access Between Features, SQL Only in Core

**Status**: accepted
**Date**: 2026-11-16
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Backend Engineer
**Informed**: all contributors
**Supersedes**: the package structure of [ADR-027](./ADR-027-project-setup-monorepo.md) §2 and §3 (examples), and the sections "Layer 2", "Layer 3" and "Package Structure (complete)" of [ADR-028](./ADR-028-backend-layered-architecture.md); the layers and their access rules of ADR-028 (amended 2026-11-13) stay in force

## Context and Problem

ADR-027 and ADR-028 (2026-06-29) were written before the first line of backend code. They prescribe:

- application-service interfaces with implementations (`EntityAppService`, `EntityAppServiceImpl`);
- `command/` and `query/` subpackages;
- `api/v1/controller` with hand-written request and response DTOs;
- repository interfaces implemented with Spring Data JPA in `core`;
- an `integration/BatchWriteService`.

The code that grew over 13 sprints took a different shape. The [architecture review 2026-11](../docs/reviews/architecture-2026-11.md) found:

- **ARCH-3:**
  - `app` has feature packages (`access`, `catalog`, `entity`, `history`, `login`, `metamodel`, `navigation`, `portal`, `recovery`, `security`, `solution`, …) with concrete service classes and no interfaces;
  - the DTOs are generated from `api/openapi.yaml` (ADR-013);
  - `EntityBatch` lives in `app/entity`;
  - `integration` holds only `package-info.java`.
- **ARCH-4:** 57 classes in `app` run SQL through `JdbcTemplate` directly, against 10 in `core`. Features read the tables of other features with their own SQL. The solution module, for example, reads `entities` and `entity_property_values` and duplicates the value query in `SolutionDeltas` and `SolutionDiff`.

The ADRs no longer describe the code, so they guide nobody. Without a rule for SQL, every new feature adds its own queries against foreign tables. Issue [#258](https://github.com/open-enterprise-architecture/OEA/issues/258) asks for an ADR that documents the cut in use and the rules between features.

## Decision Drivers

- **One source of truth:** the ADRs must describe the code that exists, so that agents and contributors follow them.
- **Ownership of tables:** every table has one owner. Changes of its shape stay local, and duplicated queries disappear (ARCH-4).
- **Checkable rules:** a rule that is not enforced decays; ArchUnit already enforces the layers and cycles (ADR-028 amendment).
- **Incremental migration:** the rule must not stop the S14 work or require a big-bang refactoring of 57 classes.
- **Little ceremony:** one developer with agents. Interfaces with a single implementation and command records add files without adding safety.

## Considered Options

### Option 1: Keep ADR-027/028 and refactor the code to them

- **Pro**: no new ADR; the original design is restored.
- **Con**: interfaces with one implementation, command and query records and hand-written DTOs duplicate what the contract already generates. Months of work without a user-visible gain.

### Option 2: Document the code as it is, SQL allowed everywhere

- **Pro**: no refactoring; the ADR matches the code at once.
- **Con**: the duplicated queries of ARCH-4 stay and grow. Table ownership remains unclear, and a schema change touches unknown places.

### Option 3: Document the feature cut, allow reads in `app`, writes only in `core`

- **Pro**: less refactoring; writes, the riskier half, get one owner.
- **Con**: reads of foreign tables stay duplicated, and "read" versus "write" is hard to enforce with ArchUnit.

### Option 4: Document the feature cut, SQL only in `core`, frozen rule

- **Pro**:
  - every table has one owner in `core`, and `app` orchestrates;
  - the rule is simple and checkable: `app` does not depend on `org.springframework.jdbc` or `java.sql`;
  - the freezing rule of ArchUnit lets the existing violations shrink step by step while no new one can be added.
- **Con**:
  - 57 classes have to move their SQL into `core` stores over several changes;
  - until then, the frozen list documents the debt.

## Decision

We choose **Option 4: document the feature cut, SQL only in `core`, enforced by a frozen ArchUnit rule** (owner decision 2026-11-16).

**1. Packages.** The layers of ADR-028 (amended 2026-11-13) stay: `api → config → app → audit → infrastructure`, and `app → core`. Within them:

| Package | Holds |
|---|---|
| `io.oea.api.<feature>` | Controllers implementing the interfaces generated from `api/openapi.yaml` (one interface per tag), and the mapping between generated models and `app` records. No hand-written DTOs |
| `io.oea.app.<feature>` | Concrete `@Service` classes per feature with the transaction boundaries and the audit. Records for inputs and results are nested in or next to their service. No interfaces with a single implementation, no `command/` or `query/` subpackages |
| `io.oea.app.shared` | Types used by several features; it depends on no feature (ADR-028 amendment) |
| `io.oea.core.<feature>` | Stores: the only classes that run SQL, one per table group, with its records. Core knows no layer above it |
| `io.oea.integration` | Reserved for outgoing integrations such as webhooks (ADR-026). Inbound writes of external systems go through the regular API and `app` (e.g. `EntityBatch`) |

**2. Access between features.** A feature in `app` may use the public classes of another feature in `app`, as long as no package cycle arises (`CycleRules`). Helpers that are not meant for other features stay package-private. The same holds in `core`.

**3. SQL only in `core`.**

- Classes in `app` do not use `JdbcTemplate`, `java.sql` or other JDBC types. They read and write through the stores of `core`.
- A table belongs to the `core` package of its feature, and other features use that store. This is the shared read API of [#259](https://github.com/open-enterprise-architecture/OEA/issues/259).
- `audit` and `infrastructure` keep their own SQL for their own tables (audit chain, key ring).

**4. Enforcement.**

- A new ArchUnit rule (`DataAccessRules`) forbids dependencies of `io.oea.app..` on `org.springframework.jdbc..` and `java.sql..`.
- It is a `FreezingArchRule`. The violations that exist on 2026-11-16 are stored in `backend/src/test/resources/archunit_store`, and the store may only shrink: a new violation fails the build, and a removed one is dropped from the store.
- Changes that touch a class with a stored violation move its SQL into `core` where that is in their scope. #259 tracks the rest.

## Consequences

### Positive Consequences

- ADR-027, ADR-028 and the code agree; new contributors and agents find the rules where the code follows them.
- Every table gets one owner in `core`. Duplicated reads (ARCH-4) disappear as the store shrinks.
- New features, starting with the go-live of S14, put their SQL in `core` from the start.

### Negative Consequences / Trade-offs

- Until the frozen store is empty, the rule allows the known exceptions: 915 dependencies in 74 classes of `app` on 2026-11-16 (`JdbcTemplate` in 57 classes, plus the row mappers and nested records that use `java.sql`). The store makes the debt visible but does not remove it.
- Progress note 2026-11-19: S17 removes the debt package by package. After the change `s17-sql-into-core-metamodel`, the 20 classes of `io.oea.app.metamodel` use stores in `io.oea.core.metamodel` and the frozen store lists 510 dependencies (860 before; 350 entries of that package removed); `s17-sql-into-core-identity` and `s17-sql-into-core-repository` follow.
- Moving SQL out of `app` adds a store class per table group and some indirection for simple reads.
- `FreezingArchRule` needs its store in the repository. A refactoring that renames a violating class produces a new violation, and the store must be updated in the same change.

## References

**Related ADRs**: [ADR-027](./ADR-027-project-setup-monorepo.md) (partially superseded), [ADR-028](./ADR-028-backend-layered-architecture.md) (partially superseded; layers stay), [ADR-013](./ADR-013-api-style.md) (generated contract), [ADR-026](./ADR-026-external-system-integration.md) (integration)
**Issues**: [#258](https://github.com/open-enterprise-architecture/OEA/issues/258), [#259](https://github.com/open-enterprise-architecture/OEA/issues/259)
**Review**: [Architecture review 2026-11](../docs/reviews/architecture-2026-11.md), ARCH-3 and ARCH-4
**Concept**: §21 (Tech Stack Backend)
