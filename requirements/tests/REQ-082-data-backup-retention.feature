# Derived from: requirements/req/req-082-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: inspection (documentation) + test (restore drill)

@non-functional @performance @must @UC-02
Feature: REQ-082 – Data backup and point-in-time recovery

  The system MUST ensure that PostgreSQL data (all
  ArchitectureEntities, metamodel configuration, audit events) is backed
  up daily and can be restored within a recovery time objective of **4
  hours**. The recovery point objective (data loss window) MUST NOT
  exceed **24 hours**.

  @AC1
  Scenario: AC1 – Backup procedure documented – fresh OEA installation via `docker compose up`
    Given fresh OEA installation via `docker compose up`
    When  operator reads the documentation
    Then  step-by-step instructions for `pg_dump`-based backup and restore are available; setup effort < 30 minutes

  @AC2
  Scenario: AC2 – Restore test – backup of a production instance with 10,000 entities
    Given backup of a production instance with 10,000 entities
    When  restore to an empty PostgreSQL instance via the documented procedure
    Then  all entities, metamodel configuration and audit events are completely present; API responds correctly within 4 hours

  @AC3
  Scenario: AC3 – RPO evidence – daily backup at 02:00; database failure at 23:59
    Given daily backup at 02:00; database failure at 23:59
    When  restore from the latest backup
    Then  data loss ≤ 22 hours (< 24h RPO)

  @AC4
  Scenario: AC4 – Retention – daily backups over 31 days
    Given daily backups over 31 days
    When  operator checks the backup archive
    Then  at least 30 backup generations are present; oldest backup is not deleted automatically
