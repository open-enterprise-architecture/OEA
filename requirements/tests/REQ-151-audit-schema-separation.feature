# Derived from: requirements/req/req-151-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + inspection

@compliance @must @UC-14
Feature: REQ-151 – Audit data storage immutable and schema-separated

  The system **MUST** store all audit events in a separate
  database schema (`audit`) for which the application user
  has exclusively INSERT privileges; UPDATE and DELETE on
  audit tables **MUST NOT** be
  executable by the application user.

  @AC1
  Scenario: AC1 – OEA application user `oea_app` active
    Given OEA application user `oea_app` active
    When  attempt to execute `UPDATE audit.events SET event_type='manipulated'`
    Then  database error "permission denied"; no record changed

  @AC2
  Scenario: AC2 – Entity change via UI
    Given entity change via UI
    When  change persisted
    Then  entry in `audit.events` with correct `event_type`, `actor_name`, `actor_email`, `occurred_at`; `old_value`/`new_value` in `audit.event_changes`

  @AC3
  Scenario: AC3 – `oea.datasource.audit.url` set (external DB)
    Given `oea.datasource.audit.url` set (external DB)
    When  application starts
    Then  audit writes go to the external DB; application data remains in the main DB
