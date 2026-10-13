# ADR-023: Multi-DB Strategy — Database Abstraction via JPA/Hibernate

**Status**: accepted
**Date**: 2026-06-29
**Deciders**: [Lukas – Repository Owner](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Context

The previous data model uses `JSONB` in several places — a PostgreSQL-specific type
that is not available on MySQL, MariaDB, SQL Server, and embedded test databases (H2).

OEA targets companies of different sizes and IT landscapes. Many organizations
already operate licensed relational databases (SQL Server, MySQL) and do not want to introduce
a new PostgreSQL instance. Binding to PostgreSQL would raise the adoption threshold.

---

## Decision

OEA uses **JPA/Hibernate** (via Spring Data JPA) as the database abstraction layer and
refrains from using database-specific types in the database schema.

### Supported Databases (v1.0)

| Database | Minimum version | Note |
|---|---|---|
| PostgreSQL | 14+ | Primary development and reference DB |
| MySQL | 8.0+ | `utf8mb4`, `STRICT_TRANS_TABLES` mandatory |
| MariaDB | 10.6+ | MySQL-compatible |
| SQL Server | 2019+ | `READ_COMMITTED_SNAPSHOT` recommended |
| H2 | 2.x | Only for tests and local development |

### Consequences for the Use of Database Types

**JSONB is replaced by `TEXT` (CLOB).** The serialization of structured data to JSON
happens exclusively at application level via JPA `@Converter`.

| Previous | Replacement | Rationale |
|---|---|---|
| `JSONB` (i18n_labels) | `i18n_entries` table | normalizable, no JSON needed |
| `JSONB` (constraints) | Individual columns `max_length`, `min_value`, `max_value`, `pattern` | typeable |
| `JSONB` (metamodel_configurations.config) | `TEXT` (CLOB) | too complex to normalize in v1; no DB query need |
| `JSONB` (catalogs.config) | `TEXT` (CLOB) | view configuration; no DB query need |

**Arrays (`TEXT[]`, `UUID[]`) are forbidden.** Postgres arrays are not portable.
All array fields were already converted into join tables in the normalization round (ADR-022).

**PostgreSQL-specific functions** (e.g. `gen_random_uuid()`, `NOW()`) are not used
directly in migration scripts — instead Hibernate defaults or DB-agnostic
equivalents in Flyway scripts with DB-specific compatibility paths.

### i18n Strategy

**Record fields** (name, description of entities, business objects) are stored in the
corporate language of the respective OEA instance. No multilingualism at record level.

**UI labels, menus, system messages** are managed via `vue-i18n` (static JSON files).
New language = new translation bundle, no database intervention.

**Configurable labels** (meta type names, property labels, enum values, category names, roles)
are stored in a normalized `i18n_entries` table, since these must be adjustable by administrators
at runtime.

---

## Rejected Alternatives

### PostgreSQL-only
Simplest setup, best ecosystem (JSONB, pg_trgm, etc.). But: restricts adoption in
SQL Server- and MySQL-heavy companies. **Rejected**: Market opportunity too high.

### Document database (MongoDB) in parallel
Would solve JSON data elegantly, but increases operational complexity considerably. **Rejected** for v1.

### Abstraction via repository pattern without JPA
More control, but not justifiable effort for open-source v1. **Rejected**.

---

## Consequences

**Positive:**
- OEA runs on all common enterprise databases without adjustment
- Tests can run with H2 in-memory (no PostgreSQL needed)
- No vendor lock-in at database level

**Negative / Compromises:**
- `config` fields (metamodel, catalogs) as TEXT/CLOB: no DB-side JSON query possible
- Flyway migrations may need DB-specific variants (PostgreSQL vs. SQL Server dialect)
- Hibernate dialect must be configured per deployment

---

## Affected Concept Chapters

- §6 (Entity Model), §9 (Metamodel), §21 (Tech Stack)

## Related ADRs

- ADR-006: Backend tech stack (Java/Spring)
- ADR-022: Structured property model
- ADR-024: Audit separation
