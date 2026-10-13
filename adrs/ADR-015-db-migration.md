# ADR-015: Database Migration Tool – Flyway

**Status**: accepted
**Date**: 2026-06-27
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Business Engineer
**Informed**: –

## Context and Problem

The database schema (tables, indexes, constraints) must be versioned and migrated automatically — on every deployment and on `docker compose up`. OEA supports five target databases (PostgreSQL, MySQL, MariaDB, SQL Server, Oracle; ADR-012). The migration tool must fit into the Java/Spring Boot stack (ADR-012) and support all five databases.

## Decision Drivers

- **Multi-DB support**: PostgreSQL, MySQL, MariaDB, SQL Server, Oracle must all be supported
- **Spring Boot integration**: automatic migrations at app start without a separate container
- **SQL transparency**: plain SQL files, reviewable and git-diffable
- **OSS**: no commercial features needed; community edition must suffice
- **REQ-075**: `docker compose up` should work without manual migration steps

## Considered Options

### Option 1: Flyway Community ✓

Flyway is the de facto standard migration tool in the Java/Spring ecosystem.

- **Pro**:
  - Spring Boot has native Flyway support (`spring.flyway.*`): migrations run automatically at app start
  - Supports all 5 target DBs in the community edition: PostgreSQL, MySQL, MariaDB, SQL Server, Oracle
  - Plain SQL migration files (`V1__init.sql`, `V2__add_index.sql`) — readable, reviewable, versioned
  - Battle-tested: in use in thousands of Java enterprise projects
  - Apache 2.0 license (community edition)
- **Con**: No automatic schema diff from Java entities (migrations are written manually); rollback support only in the commercial edition — down migrations must be written manually as a new migration

### Option 2: Liquibase Community

Similar to Flyway, but XML/YAML/SQL formats.

- **Pro**: Rollback support in community edition; multiple formats
- **Con**: XML changeset format for DB-neutral migrations is less readable than plain SQL; higher complexity; Apache 2.0 community, proprietary features
- Ruled out: Flyway with plain SQL is simpler and sufficient

### Option 3: Drizzle Kit (Node.js)

Migration tool of Drizzle ORM; was tied to the TypeScript/NestJS stack.

- **Con**: Node.js-based; no Java/Spring integration; no Oracle support; dropped with the ADR-012 decision for Java

### Option 4: Hibernate `hbm2ddl` / `validate`

Hibernate can generate the schema automatically from entities (`spring.jpa.hibernate.ddl-auto=update`).

- **Pro**: no separate tool; always in sync with entities
- **Con**: **Not suitable for production** — uncontrolled schema changes on code changes; no version tracking; no rollback possibility; ruled out for productive deployments

## Decision

**Option 1: Flyway Community (Apache 2.0).**

Spring Boot integrates Flyway natively — no separate container, no manual step:

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

Migrations run automatically at app start before the first DB access.

### Migration Convention

```
src/main/resources/db/migration/
  V1__init_core_schema.sql        ← entities, entity_versions, audit_events
  V2__init_plateau_schema.sql     ← plateaus, entity_plateau_states
  V3__init_metamodel_schema.sql   ← metamodel_configurations, entity_type_definitions
  V4__add_indexes.sql             ← indexes (DB-neutral: B-tree)
```

Naming scheme: `V{version}__{description}.sql` (Flyway standard).

### DB-Specific Migrations

For DB-specific optimizations (e.g. GIN index on PostgreSQL), separate Flyway locations per dialect are used:

```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration          # DB-neutral (mandatory)
      - classpath:db/migration/${DB_DIALECT:postgresql}  # DB-specific (optional)
```

This way PostgreSQL deployments get GIN indexes without other databases failing.

### Rollback Strategy

Flyway Community does not support automatic rollback. Strategy for v1.0:

- **Forward-only**: Faulty migrations are reverted by a new `V{n+1}__rollback_*.sql` migration
- **Pre-migration backup**: Operators back up the DB before every deployment (recommended in operations documentation)

## Consequences

### Positive Consequences

- `docker compose up` automatically runs all pending migrations — zero-config for operators
- Plain SQL migrations: versioned in the repository, reviewable in the PR, portable for all 5 DBs
- Spring-native start: no separate migration container
- Support for all 5 target databases without license costs

### Negative Consequences / Trade-offs

- Migrations are written manually (no schema diff from Hibernate entities); requires discipline from the developer
- No automatic rollback; write down migrations as a new version
- `baseline-on-migrate: true` required for installations with an existing schema (upgrade scenarios)

## References

**Related ADRs**: [ADR-012](./ADR-012-backend-stack.md) (Java + Spring Boot), [ADR-016](./ADR-016-persistence-strategy.md) (persistence strategy)

**Requirements**: [REQ-075](../requirements/req/REQ-075-platform-independent-deployment.md)
