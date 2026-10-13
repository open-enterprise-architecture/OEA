---
id: REQ-150
title: Multi-Database Support
type: constraint
priority: must
status: rejected
version: 0.1.0
created: 2026-06-29
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-02
  adrs:
    - ADR-023
supersedes: []
superseded_by:
  - REQ-156
---

# REQ-150: Multi-Database Support

> **Rejected** (2026-10-13): superseded by [REQ-156](REQ-156-postgresql-only-database.md) following [ADR-030](../../adrs/ADR-030-postgresql-only.md).

## Statement

The system **MUST** be operable with PostgreSQL 14+, MySQL 8+, MariaDB 10.6+, and SQL Server 2019+ without changes to the application code; the database type is selected exclusively via configuration.

## Rationale

Companies already operate licensed databases. A PostgreSQL obligation raises the adoption threshold. Only those who do not have to introduce new infrastructure will adopt the tool.

## Context

The database schema uses neither JSONB nor database-specific array types. Configuration blobs are stored as TEXT/CLOB and serialized via a JPA `@Converter`. (ADR-023)

## Type-Specific Fields

**Type of Constraint**: technical

**Source**: ADR-023, market requirement

**Binding Strength**: hard

**Impact of Non-Compliance**: adoption limited to PostgreSQL installations; exclusion of SQL Server and MySQL customers

## Acceptance Criteria

**AC1**:

- Given: OEA Docker image with env variable `SPRING_DATASOURCE_URL=jdbc:mysql://...`
- When: `docker compose up`
- Then: Flyway migrations run through; API responds correctly; all CRUD operations work

**AC2**:

- Given: test environment with H2 in-memory (CI)
- When: unit and integration tests run
- Then: all tests green without a PostgreSQL instance

**AC3**:

- Given: schema migration V1 in SQL
- When: running Flyway on SQL Server 2019
- Then: no syntax-specific errors; all tables created

## Verification Method

- [ ] Method: test (automated) + demonstration
- [ ] Test setup: CI matrix with PostgreSQL, MySQL, H2; manual smoke test SQL Server
- [ ] Pass criterion: CI matrix green; smoke test on SQL Server successful
- [ ] Integrated in CI: yes (PostgreSQL + H2); SQL Server as an optional job

## Dependencies

- **Prerequisites**: ADR-015 (Flyway), ADR-023

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-023 |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Rejected: ADR-023 superseded by ADR-030 (PostgreSQL only); replaced by REQ-156 |
