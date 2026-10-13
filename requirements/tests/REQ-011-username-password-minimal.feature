# Derived from: requirements/req/req-011-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated) + inspection (default configuration)

@functional @could @UC-01
Feature: REQ-011 – Username/password login without second factor (minimal variant)

  The system MAY allow persons to log in with username/password only,
  without a second factor; this variant MUST be explicitly enabled at
  instance level and MUST NOT be the default state of a newly
  installed OEA instance.

  Background:
    Given an OEA instance with a configured authentication mechanism
    And a person with email "test@example.com" and an active role exists

  @AC1
  Scenario: AC1 – an instance on which this variant was explicitly enabled, and a perso...
    Given an instance on which this variant was explicitly enabled, and a person with a correct password
    When  they log in
    Then  they receive a session according to their active role assignments

  @AC2
  Scenario: AC2 – a newly installed, unconfigured OEA instance
    Given a newly installed, unconfigured OEA instance
    When  its authentication setup is checked
    Then  this variant is disabled by default

  @AC3
  Scenario: AC3 – every successful or failed login via this variant
    Given every successful or failed login via this variant
    When  the audit log entry is created (see REQ-005)
    Then  the entry shows that no second factor was used
