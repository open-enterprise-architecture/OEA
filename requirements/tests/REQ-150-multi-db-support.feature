# Derived from: requirements/req/req-150-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + demonstration

@constraint @must @UC-02
Feature: REQ-150 – Multi-database support

  The system **MUST** be operable with PostgreSQL 14+, MySQL 8+, MariaDB 10.6+ and
  SQL Server 2019+ without changes to the
  application code; the database type is selected exclusively via
  configuration.

  @AC1
  Scenario: AC1 – OEA Docker image with env variable `SPRING_DATASOURCE_URL=jdbc:mysql://...`
    Given OEA Docker image with env variable `SPRING_DATASOURCE_URL=jdbc:mysql://...`
    When  `docker compose up`
    Then  Flyway migrations run through; API responds correctly; all CRUD operations work

  @AC2
  Scenario: AC2 – Test environment with H2 in-memory (CI)
    Given test environment with H2 in-memory (CI)
    When  unit and integration tests run
    Then  all tests green without a PostgreSQL instance

  @AC3
  Scenario: AC3 – Schema migration V1 in SQL
    Given schema migration V1 in SQL
    When  run Flyway on SQL Server 2019
    Then  no syntax-specific errors; all tables created
