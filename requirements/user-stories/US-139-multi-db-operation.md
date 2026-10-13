# US-139: Operate OEA on an Alternative Database

**ID**: US-139
**Story Points**: 5
**Status**: rejected
**Sprint**: unassigned

> **Rejected** (2026-10-13): OEA supports PostgreSQL only ([ADR-030](../../adrs/ADR-030-postgresql-only.md), [REQ-156](../req/REQ-156-postgresql-only-database.md)).

## Story

As a system administrator, I want to be able to operate OEA on PostgreSQL, MySQL, or SQL Server as desired without having to adapt the application code, so that I can use the database of my existing infrastructure.

## Reference

**Use Case**: [UC-02: System Administration Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: REQ-150

## Acceptance Criteria

**AC1**:

- Given: Docker Compose configuration with MySQL 8 instead of PostgreSQL
- When: `docker compose up`
- Then: Flyway migrations run through; bootstrapping reachable; API responds correctly

**AC2**:

- Given: CI pipeline with H2 in-memory
- When: test suite runs
- Then: all unit and integration tests green without an external DB

**AC3**:

- Given: no JSONB, no arrays in the schema
- When: run Flyway migration on SQL Server
- Then: all tables created without dialect errors

## Technical Notes

- Hibernate dialect configurable via `spring.jpa.database-platform`
- No `JSONB`, no `UUID[]` in the schema — only `TEXT`/`VARCHAR`/`CLOB`
- Flyway migrations: SQL-92 compatible; DB-specific variants in `db/migration/{vendor}/`
- H2 compatibility: `spring.h2.console.enabled=true` only for the dev profile
- Primary key: `BIGSERIAL` (PG) → `BIGINT AUTO_INCREMENT` (MySQL) → Flyway vendor path

## Definition of Done

- [ ] Acceptance criteria fulfilled
- [ ] CI matrix: PostgreSQL + MySQL + H2 green
- [ ] Flyway migrations without PostgreSQL-specific syntax
- [ ] `docker-compose.mysql.yml` present as an example

## Dependencies

- Waits for: US-013 (bootstrapping schema must be portable first)
- Blocks: — (can be implemented in parallel with other features)
