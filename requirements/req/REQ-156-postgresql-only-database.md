---
id: REQ-156
title: PostgreSQL as the only supported database
type: constraint
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: Lukas Mathis
references:
  use_cases:
    - UC-02
  adrs:
    - ADR-030
    - ADR-016
    - ADR-033
    - ADR-036
supersedes:
  - REQ-150
superseded_by: []
---

# REQ-156: PostgreSQL as the Only Supported Database

## Statement

The system **MUST** use PostgreSQL ≥ 15 as its only database for application data and audit data. Database access and migrations **MAY** use PostgreSQL-specific features (JSONB, GIN indexes, arrays, recursive CTEs). Automated integration and system tests **MUST** run against a real PostgreSQL instance of the minimum supported version.

## Rationale

Supporting several databases requires a test matrix and dialect-specific migrations the project cannot maintain. JSONB fits the metamodel-driven configuration documents better than portable TEXT/CLOB columns (ADR-030).

## Context

Replaces REQ-150 (multi-database support). Configuration documents (`metamodel_configurations.config`, `catalogs.config`) are stored as JSONB. Existing normalized structures (`i18n_entries`, constraint columns, join tables) remain.

ADR-036 §3: the repository root `compose.yaml` is the operator file; it defaults no secret, so `docker compose up` succeeds only after all required secrets are supplied (e.g. `.env` filled from `.env.example`) and is not runnable with an empty environment. Development uses `compose.dev.yaml` with the Compose profile `dev`. ADR-033 §2: migrations run in a separate one-shot migration step under the owner role, with one location per schema (`db/migration` for the application schemas, `db/migration-audit` for the audit schema); both are PostgreSQL-only, so the "no dialect-specific paths" rule of ADR-030 is unaffected. The Keycloak container used in development and tests keeps its own internal store, which is not OEA application data.

## Type-Specific Fields

**Type of Constraint**: technical

**Source**: ADR-030, ADR-016

**Binding Strength**: hard

**Impact of Non-Compliance**: untested database paths in production; loss of JSONB querying and indexing for configuration documents

## Acceptance Criteria

**AC1**:

- Given: the operator Compose file with all required secrets supplied explicitly
- When: `docker compose up` is executed
- Then: the migration step applies all migrations from `db/migration` and `db/migration-audit` to PostgreSQL, the application connects to PostgreSQL, and no dialect-specific migration paths exist; with any required secret unset, `docker compose up` fails before any service starts

**AC2**:

- Given: the CI pipeline
- When: integration and Cucumber system tests run
- Then: they use a PostgreSQL Testcontainer of the minimum supported version; no H2 or other in-memory database is used

**AC3**:

- Given: a configured datasource pointing to a PostgreSQL version below 15
- When: the application starts
- Then: startup fails with a clear error message naming the minimum supported version

**AC4**:

- Given: a metamodel configuration stored in `metamodel_configurations.config`
- When: the column type is inspected
- Then: it is `JSONB`

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: Testcontainers with `postgres:15`; startup test against an older PostgreSQL image for AC3
- [ ] Pass criterion: AC1–AC4 green in CI
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: ADR-030, ADR-016, ADR-015 (Flyway), ADR-029 (Testcontainers), ADR-033 (migration step), ADR-036 (operator and development Compose files)
- **Downstream effects**: REQ-152 (dynamic labels; `i18n_entries` table), REQ-075 (deployment via Docker Compose)

## Risks of Non-Compliance

- Risk 1: tests against H2 or another database pass while production on PostgreSQL fails – medium severity

## Implementation Notes

- Hibernate: `@JdbcTypeCode(SqlTypes.JSON)` for JSONB columns
- Version check at startup via `SELECT current_setting('server_version_num')`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Lukas Mathis | Initial draft from ADR-030; supersedes REQ-150 |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-036 CH-3: AC1 requires all secrets explicitly for the operator Compose file; ADR-033 §2: migration step with locations `db/migration` and `db/migration-audit` (AC1, Context) |
