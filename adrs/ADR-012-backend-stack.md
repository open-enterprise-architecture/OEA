# ADR-012: Backend Stack – Java 21 + Spring Boot 3 + Hibernate

**Status**: accepted
**Date**: 2026-06-27
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Business Engineer
**Informed**: ADR-013 (API style), ADR-015 (DB migration), ADR-016 (persistence)
**Supersedes**: –
**Updated**: 2026-10-04 — OpenAPI spec-first instead of SpringDoc code-first (ADR-013); build with Maven instead of Gradle, directory `backend/` (ADR-027)
**Partially superseded**: 2026-10-13 by [ADR-030](./ADR-030-postgresql-only.md) — multi-database parts no longer apply (PostgreSQL only)

## Context and Problem

OEA needs a backend service that:

- provides the REST API (all `/api/v1/...` endpoints)
- executes the metamodel and the EntityType engine
- supports multiple databases (PostgreSQL, MySQL, MariaDB, SQL Server, Oracle)
- can execute CEL rule evaluation (ADR-018) in parallel
- sends WebSocket/SSE events for real-time updates
- can be operated as a Docker container on Linux, macOS, and Windows

**DB neutrality is a tier-1 requirement**: Companies focus on as few technologies as possible and often already use an enterprise database. OEA should fit into existing infrastructure, not force a new DB dependency.

## Decision Drivers

- **DB neutrality**: PostgreSQL, MySQL, MariaDB, SQL Server, Oracle — all with official ORM support
- **Oracle**: Key criterion; only Hibernate and EF Core have official Oracle support
- **OpenJDK**: No Oracle JDK or commercial JVM; exclusively free distributions
- **True multithreading**: parallel processing without event loop restrictions
- **Cross-platform + container**: Standard Docker images, no proprietary base images
- **OpenAPI codegen**: Generation of server interfaces from the spec-first contract `api/openapi.yaml` (ADR-013)
- **OSS compatibility**: Apache 2.0 or GPL-compatible licenses

## Considered Options

### Option 1: TypeScript + NestJS

- **Pro**: One-language stack (TypeScript everywhere); direct DTO types in the frontend
- **Con**: Node.js single-threaded (event loop); no mature ORM with Oracle support; DB neutrality only achievable with considerable own effort; ruled out by the DB neutrality requirement

### Option 2: Go + chi + GORM

- **Pro**: Goroutines (true parallelism); ~20 MB Docker image; `microsoft/go-mssqldb` official
- **Con**: GORM has **no official Oracle support**; `sijms/go-ora` is community-only; Oracle requirement not reliably fulfillable; ruled out

### Option 3: C# / .NET 8 + Entity Framework Core

- **Pro**: EF Core with official Oracle support; .NET 8 cross-platform; good performance
- **Con**: Microsoft ecosystem politically problematic for a neutral OSS project; ruled out

### Option 4: Rust + Axum + sqlx

- **Pro**: Maximum performance; ~15 MB Docker image
- **Con**: No mature multi-DB ORM; Oracle support (`tiberius`) community-only; highest learning curve; ruled out

### Option 5: Java 21 + Spring Boot 3 + Hibernate ✓

- **Pro**:
  - Hibernate 6.x: most mature multi-DB ORM with official support for **all 5 target DBs**
  - Java 21 virtual threads (Project Loom): true parallelism with minimal overhead
  - Eclipse Temurin: fully open source, TCK-certified, no Oracle license costs
  - GraalVM CE (optional): native image → ~100 MB Docker image, ~50 ms startup
  - `openapi-generator-maven-plugin`: generates controller interfaces and DTOs from `api/openapi.yaml` (spec-first, ADR-013-compliant)
  - Flyway: Spring-native DB migration, all 5 target DBs supported (ADR-015)
- **Con**:
  - Second language alongside TypeScript (frontend); no direct DTO sharing — only via OpenAPI codegen
  - JVM without GraalVM: ~250–400 MB memory (acceptable for a long-running server process)
  - GraalVM native image: longer build times; reflection-heavy libraries need configuration

## Decision

**Option 5: Java 21 + Spring Boot 3 + Hibernate.**

Hibernate is the only ORM with official, battle-tested support for all five target databases including Oracle. Java 21 virtual threads solve the multithreading requirement with a single config switch. The multilingualism (Java backend + TypeScript frontend) is justifiable through clear layer separation and OpenAPI type generation.

### Chosen Stack

| Component | Technology | Version | License |
|---|---|---|---|
| JDK | Eclipse Temurin (OpenJDK) | 21 LTS | GPL v2 + CE |
| Framework | Spring Boot | 3.x | Apache 2.0 |
| ORM | Hibernate (via Spring Data JPA) | 6.x | LGPL 2.1 |
| API codegen | OpenAPI Generator (`openapi-generator-maven-plugin`, generator `spring`) | 7.x | Apache 2.0 |
| DB migration | Flyway Community | 10.x | Apache 2.0 |
| Native image (optional) | GraalVM Community Edition | 21 | GPL v2 + CE |
| Build | Maven (wrapper `./mvnw`, ADR-027) | 3.9.x | Apache 2.0 |
| Container base | `eclipse-temurin:21-jre-alpine` | current | GPL v2 + CE |

### OpenJDK: Eclipse Temurin

Eclipse Temurin is the recommended OpenJDK distribution for OSS projects:

- Publisher: Eclipse Adoptium Working Group (Eclipse Foundation)
- Fully open source; no Oracle license costs; TCK-certified
- Docker: `eclipse-temurin:21-jre-alpine` (~170 MB) as standard base image
- GraalVM native image: `ghcr.io/graalvm/native-image:ol9-java21` (GraalVM CE, GPL)

### Virtual Threads (Project Loom)

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

One config switch activates virtual threads for the entire Spring MVC thread pool. Each HTTP request runs in its own virtual thread — no thread pool bottleneck with many parallel requests.

### DB Neutrality via Hibernate Dialects

Hibernate detects the database type from the datasource and automatically selects the appropriate dialect:

| Database | Hibernate dialect | JSON column for properties |
|---|---|---|
| PostgreSQL | `PostgreSQLDialect` | `JSONB` |
| MySQL | `MySQLDialect` | `JSON` |
| MariaDB | `MariaDBDialect` | `JSON` |
| SQL Server | `SQLServerDialect` | `NVARCHAR(MAX)` |
| Oracle | `OracleDialect` | `CLOB` / `JSON` (Oracle 21c+) |

Entity properties (metamodel-driven, no fixed schema) via `@JdbcTypeCode(SqlTypes.JSON)`:

```java
@Entity
@Table(name = "entities")
public class ArchitectureEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type_id", nullable = false, updatable = false)
    private String entityTypeId;

    @Version
    private Integer version;  // Optimistic locking (ADR-016)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", nullable = false)
    private Map<String, Object> properties = new HashMap<>();
}
```

Hibernate serializes/deserializes JSON automatically and uses the dialect-specific column type — no per-database adjustment needed.

### OpenAPI Type Sharing (ADR-013-compliant)

The contract `api/openapi.yaml` is the source (spec-first). The `openapi-generator-maven-plugin` generates controller interfaces and DTOs at build time; the implementation only realizes the generated interface:

```java
@RestController
public class EntityController implements EntitiesApi {   // EntitiesApi: generated from api/openapi.yaml

    @Override
    public ResponseEntity<EntityDto> getEntityById(Long id) { ... }
}
```

`openapi-typescript` in the Vue 3 frontend generates type-safe API clients from the same spec.

## Consequences

### Positive Consequences

- Oracle, SQL Server, MySQL, MariaDB, PostgreSQL: all first-class via Hibernate
- Virtual threads: true parallelism without reactive programming models
- Eclipse Temurin: no vendor lock-in, fully free and TCK-certified
- Flyway: Spring-native migration, all 5 target DBs, plain SQL files in the repository
- Optimistic locking via `@Version` (Hibernate-native, ADR-016-compliant)
- GraalVM CE optional: native binary, fast startup, small image

### Negative Consequences / Trade-offs

- Two languages in the stack: Java (backend) + TypeScript (frontend)
- No direct DTO sharing; only via OpenAPI codegen
- JVM without native image: ~250–400 MB memory vs. ~20 MB (Go/Rust)
- GraalVM native image: more complex build pipeline (optional; fallback to JVM operation possible)

### Follow-up Decisions

- **ADR-015**: Drizzle Kit → Flyway (Spring-native, all 5 DBs)
- **ADR-016**: JSONB-specific GIN indexes are dropped in favor of DB neutrality; `@JdbcTypeCode(SqlTypes.JSON)` as abstraction layer; optimistic locking and EntityVersion mechanism remain valid
- **Monorepo structure**: decided in ADR-027 — `backend/` (Spring Boot / Maven), `frontend/` (Vue 3 / Vite), `api/openapi.yaml` (contract)

## References

**Related ADRs**: [ADR-013](./ADR-013-api-style.md), [ADR-015](./ADR-015-db-migration.md), [ADR-016](./ADR-016-persistence-strategy.md), [ADR-018](./ADR-018-business-rule-engine.md)

**Requirements**: [REQ-075](../requirements/req/REQ-075-platform-independent-deployment.md)

**Concept**: §21 API Architecture, §22 Walking Skeleton
