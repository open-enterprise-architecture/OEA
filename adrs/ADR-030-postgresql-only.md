# ADR-030: PostgreSQL as the Only Supported Database

**Status**: accepted
**Date**: 2026-10-13
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: –
**Informed**: all contributors
**Supersedes**: [ADR-023](./ADR-023-multi-db-strategy.md) (multi-DB strategy)

## Context and Problem

[ADR-023](./ADR-023-multi-db-strategy.md) decided that OEA runs on PostgreSQL, MySQL, MariaDB, SQL Server and H2 via JPA/Hibernate and banned database-specific types. As a consequence, JSONB was replaced by TEXT/CLOB, structured configuration became opaque strings, and the project committed to testing every supported database.

Before the first line of backend code exists, this decision is reconsidered:

- The data model relies on runtime-configurable, metamodel-driven properties and configuration documents (metamodel configuration, catalog configuration). JSONB with GIN indexes is the natural fit; TEXT/CLOB makes these documents unqueryable in the database.
- Supporting five databases means a CI matrix, dialect-specific Flyway paths and per-database bug analysis. The project has one maintainer and no resources to test and support every database type properly.
- [ADR-016](./ADR-016-persistence-strategy.md) already chose PostgreSQL 15 + JSONB as the persistence strategy; ADR-023 diluted it.

## Decision Drivers

- **Maintainability with a small team**: one database to test, operate, tune and debug
- **Fit for the data model**: JSONB for configuration documents and flexible properties, GIN indexes, recursive CTEs (ADR-016)
- **Test realism**: tests must run against the database used in production, not an emulation
- **Operational simplicity**: `docker compose up` (REQ-075) ships with PostgreSQL anyway
- **Adoption**: organizations standardized on SQL Server or MySQL must accept an additional PostgreSQL instance

## Considered Options

### Option 1: Keep multi-DB support (ADR-023)

- **Pro**:
  - Lower adoption threshold for organizations that only run SQL Server or MySQL
  - No vendor lock-in at database level
- **Con**:
  - CI matrix and dialect-specific migrations for five databases; not affordable for the project
  - No JSONB: configuration documents stored as opaque TEXT/CLOB, not queryable or indexable
  - H2 in tests hides PostgreSQL-specific behavior
  - Every feature has to be designed for the lowest common denominator

### Option 2: PostgreSQL only

- **Pro**:
  - Full use of PostgreSQL features: JSONB + GIN indexes, recursive CTEs, partial indexes, arrays where useful
  - One Flyway migration path, one dialect, one set of operational know-how
  - Tests run against real PostgreSQL via Testcontainers
  - Consistent with ADR-016 and the self-hosted Docker Compose deployment
- **Con**:
  - Organizations without PostgreSQL must operate an additional database
  - Returning to multi-DB later would require a migration effort for JSONB-based structures

### Option 3: PostgreSQL first, other databases "community-supported"

- **Pro**:
  - Formally keeps the door open for other databases
- **Con**:
  - Without tests the support is not real; users would hit untested code paths
  - Still forces avoiding PostgreSQL-specific features to keep the door open

## Decision

We choose **Option 2: PostgreSQL only**, because the project cannot test and support several databases with its resources, and because JSONB is a better fit for the metamodel-driven data model than portable TEXT/CLOB columns. ADR-016 (PostgreSQL 15 + JSONB) is the persistence strategy again without restrictions.

Concretely:

1. **Supported database**: PostgreSQL ≥ 15 (minimum version as in ADR-016). No other database is supported or tested.
2. **Types**: JSONB is allowed and preferred for structured documents and flexible properties. PostgreSQL arrays and other native types may be used where they simplify the model. Existing normalizations from ADR-022/ADR-023 (e.g. `i18n_entries`, constraint columns, join tables) stay – they are sound relational design independent of the database.
3. **Configuration documents**: `metamodel_configurations.config` and `catalogs.config` become `JSONB` (instead of TEXT/CLOB).
4. **Migrations**: one Flyway location (`db/migration`); no dialect-specific locations.
5. **Tests**: integration and Cucumber system tests (ADR-029) run against PostgreSQL via Testcontainers, using the minimum supported version. H2 is not used.
6. **Audit database** (ADR-024): the separate audit schema or external audit database is PostgreSQL as well.

## Consequences

### Positive Consequences

- Single CI database, single migration path, single dialect
- Configuration documents are queryable and indexable (JSONB + GIN)
- Tests detect PostgreSQL-specific problems before production
- Simpler onboarding for contributors (one database to understand)

### Negative Consequences / Trade-offs

- Organizations standardized on SQL Server, MySQL or MariaDB need an additional PostgreSQL instance
- Higher effort if multi-DB support is ever reintroduced (JSONB structures would need a portable representation)
- Contributors need Docker for integration tests (Testcontainers)

### Effects on Existing ADRs

These ADRs keep their status; only the listed parts are superseded by this ADR:

| ADR | Superseded part | Still valid |
|---|---|---|
| [ADR-012](./ADR-012-backend-stack.md) | "DB neutrality" as decision driver; Hibernate dialect table for five databases | Java 21 + Spring Boot 3 + Hibernate |
| [ADR-015](./ADR-015-db-migration.md) | Multi-DB support as driver; dialect-specific Flyway locations | Flyway as migration tool |
| [ADR-024](./ADR-024-audit-separation.md) | "DB-agnostic, no JSONB" as argument for TEXT columns | Separate audit schema / external audit DB (PostgreSQL) |
| [ADR-025](./ADR-025-i18n-strategy.md) | Reference to ADR-023 as reason for the `i18n_entries` table | Two-layer i18n with `i18n_entries` |
| [ADR-026](./ADR-026-external-system-integration.md) | "Batch atomicity under multi-DB" as risk | External write API |
| [ADR-027](./ADR-027-project-setup-monorepo.md) | `docker-compose.mysql.yml`, `docker-compose.sqlserver.yml` | Monorepo, Maven structure, Docker Compose with PostgreSQL |

### Effects on Requirements and Data Model

- [REQ-150](../requirements/req/REQ-150-multi-db-support.md) (multi-DB support) is rejected and superseded by [REQ-156](../requirements/req/REQ-156-postgresql-only-database.md)
- [US-139](../requirements/user-stories/US-139-multi-db-operation.md) (multi-DB operation) is rejected
- `docs/data-model.puml`: `config` columns of `metamodel_configurations` and `catalogs` as `JSONB`; type legend updated

### Follow-up Decisions

- Minimum PostgreSQL version: PostgreSQL 15 reaches end of life in November 2027; raise the minimum version before the v1.0 release
- Whether to use PostgreSQL-specific search features (`pg_trgm`, full-text search) is decided with the search requirements

## References

**Concept Chapters**:

- [§6 Core Entity Types](../concept/20-entities/06-core-entity-types.md)
- [§15 Schema Evolution](../concept/40-extensibility/15-schema-evolution.md)
- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-016](./ADR-016-persistence-strategy.md): Persistence strategy – PostgreSQL 15 + JSONB
- [ADR-023](./ADR-023-multi-db-strategy.md): Multi-DB strategy (superseded)
- [ADR-029](./ADR-029-cucumber-test-framework.md): Cucumber + Testcontainers

**Use Cases / Requirements**:

- [REQ-156: PostgreSQL as the only supported database](../requirements/req/REQ-156-postgresql-only-database.md)
- [REQ-075: Platform-independent deployment](../requirements/req/REQ-075-platform-independent-deployment.md)

## Notes

ADR-023 stays in the repository unchanged except for its status, so the reasoning for and against multi-DB support remains traceable.
